# Almanac — integrating with other mods

For authors of mods that draw weather, place snow, colour terrain or otherwise care what season it is. Almanac is a
Fabric mod for Minecraft 26.3, mod ID `almanac`.

**Stable contract from Almanac 0.1.1 on:** everything in the `com.thenathe.almanac.api` package, the seasonal copy
ID format `almanac:<sub_season>/<namespace>/<path>`, and the biome and block tags listed under "Tags" below. Releases
only add to it; constants (such as the `FAR_SNOW_*` sentinels) never change. Each addition is marked with the version
it came in:

- **0.1.2** added biome-based `seasonalBiome` and `climateOf`, `worldRainLevel`, `worldThunderLevel` and `thawTicks`,
  the far-terrain lookups and `AlmanacEvents.FAR_CLIMATE_CHANGED`.
- **0.1.3** added `precipitationOf` and `farSnowChangedTick`, and the tag `#almanac:no_seasonal_ice`.
- **0.1.3.1** added nothing; `farSnowChangedTick` moves only when far snow changes, not with every layer of depth.
- **0.1.3.2** added `formingTicks`. Snow forming in loaded chunks takes that long instead of `thawTicks`, and far
  terrain should form its snow over it too (0.1.3's notes said `thawTicks`).

Nothing earlier changed. Anything outside the `api` package can change in any release.

## Detecting Almanac

```java
boolean almanac = FabricLoader.getInstance().isModLoaded("almanac");
```

Keep the calls to Almanac's classes in a class of your own that is only loaded when this is true, so your mod still
loads without Almanac:

```java
if (FabricLoader.getInstance().isModLoaded("almanac")) {
    AlmanacCompat.register(); // the only class that imports com.thenathe.almanac.api
}
```

Being installed is not the same as being active. On a client, Almanac's answers are empty until the server it is
connected to has Almanac too and has sent the season, and they are always empty outside the overworld.

### Checking the version

A call added in a later Almanac doesn't exist in an earlier one, and calling it there throws `NoSuchMethodError`. So
either refuse older versions in your `fabric.mod.json`, which makes Fabric Loader say so at startup instead of a crash
in play:

```json
"breaks": {
    "almanac": "<0.1.3"
}
```

(name the version that added the newest call you use: `<0.1.2` for the far-terrain lookups, `<0.1.3` for
`precipitationOf` or `farSnowChangedTick`, `<0.1.3.2` for `formingTicks`), or check the version at runtime and only use the newer calls when they are
there:

```java
boolean hasPrecipitationOf = FabricLoader.getInstance().getModContainer("almanac")
        .map(almanac -> {
            try {
                return almanac.getMetadata().getVersion().compareTo(Version.parse("0.1.3")) >= 0;
            } catch (VersionParsingException unreadable) {
                return false;
            }
        })
        .orElse(false);
```

Almanac's versions look like `0.1.3+26.3`; the part after `+` is the Minecraft version and doesn't count in the
comparison.

## The API

Everything is static on `com.thenathe.almanac.api.AlmanacApi`. Each method in this table works on the server (any
`ServerLevel`) and on a client connected to an Almanac server (its `ClientLevel`), and gives the same answers on both.
The far-terrain lookups further down are the exception: they answer only on a client. Server calls belong on the
server thread; on a client every call is safe from any thread (the climate is kept as one value replaced whole, from
0.1.3; before that, call from the client thread).

| Method | Returns |
|---|---|
| `subSeason(Level)` | `Optional<SubSeason>`: one of twelve, `EARLY_SPRING` to `LATE_WINTER`. |
| `climateAt(Level, BlockPos)` | `Optional<ClimateAt>`: the biome's base climate (`HOT`, `TEMPERATE`, `COLD`), the climate the season gives it now (one step colder in winter, warmer in summer), and whether the current rain event reaches it. |
| `climateOf(Level, Holder<Biome>)` (since 0.1.2) | The same for a biome you already have, for example from your own sampling grid, so Almanac doesn't look the biome up again (which is slower and can land on a neighbouring biome at borders). A seasonal copy counts as the real biome behind it. `climateAt` is `climateOf` of the biome at the position. |
| `precipitationAt(Level, BlockPos)` | `Optional<Biome.Precipitation>`: what falls there during the current rain event this sub-season (`NONE`, `RAIN` or `SNOW`), with the rain reach applied: `NONE` where the event doesn't reach the biome's climate. It doesn't check whether it is raining or whether the sky is open; `Level#precipitationAt` does both and gives the same seasonal answer. |
| `precipitationOf(Level, Holder<Biome>, BlockPos)` (since 0.1.3) | The same for a biome you already have, by the same rule, so you don't need a copy of Almanac's "what falls here" logic: what falls at the position (it still counts for the height, snow above the snow line) in that biome. A seasonal copy counts as the real biome behind it. `precipitationAt` is `precipitationOf` of the biome at the position. |
| `seasonalBiome(Level, BlockPos)` | `Optional<Holder<Biome>>`: the biome a player without Almanac gets there, the sub-season's copy of the real biome (its temperature, precipitation and colours are already seasonal), or the biome itself if it has no copies. The copy comes from the level's own biome registry. |
| `seasonalBiome(Level, Holder<Biome>)` (since 0.1.2) | The same for a biome you already have: the current sub-season's copy of it, or the biome itself if it has no copies. A seasonal copy (of any sub-season) is first turned back into its real biome, so it gives the current sub-season's copy. The position-based call is this one with the biome at the position. |
| `originalBiome(Level, Holder<Biome>)` | The real biome behind a copy, from the level's registry; any other biome unchanged. |
| `originalBiome(ResourceKey<Biome>)` | The same, by key, without a registry. |
| `rainReaches(Level, Climate)` | Whether the current (or most recent) rain event reaches a climate this sub-season. |
| `snowDepth(Level, Climate)` | `OptionalDouble`: snow depth in layers, one value per climate for the whole dimension. On a client it is the server's value to a sixteenth of a layer. |
| `thawTicks(Level)` (since 0.1.2) | `OptionalLong`: how long the season's snow and ice take to thaw in loaded chunks right now, in game ticks (the `thawDays` setting, at most half the current sub-season). On a client with Almanac 0.1.2 or later it comes with the climate state. Read it when a melt starts: it is the length for a far-terrain melt animation, so far melts last as long as near ones. Snow forming is quicker: see `formingTicks`. Empty when there is no season; callers without it use their own default. |
| `formingTicks(Level)` (since 0.1.3.2) | `OptionalLong`: how long falling snow takes to whiten loaded land right now, in game ticks: the time vanilla's snowfall needs to cover 95 % of the open ground at the `randomTickSpeed` game rule, `ln(20) * 12288 / randomTickSpeed` rounded up (12,271 at the default 3). Almanac's own snow forming in loaded chunks takes as long, including beyond simulation distance where vanilla doesn't snow. Use it as the length of a far-terrain snow-forming animation; read it when the snow starts to form. With `randomTickSpeed` 0 it is the default speed's time (vanilla never snows then, Almanac's forming still runs), and a huge speed gives 1. On a client with Almanac 0.1.3.2 or later on a 0.1.3.2 or later server, which sends it on joining and when the game rule changes; empty on older servers (use 12,000 ticks). |
| `worldRainLevel(Level)`, `worldThunderLevel(Level)` (since 0.1.2) | The dimension's own rain and thunder levels, 0 to 1, before each player's share (below). On the server, and on a client with Almanac 0.1.2 or later on an Almanac 0.1.2 or later server (synced every time they change). Elsewhere the level's own levels: the same thing without Almanac on the server, but **on an Almanac 0.1.1 server they are the player's share**, since that server doesn't send the world's levels and the client can't tell. |

`SeasonalCopyId` reads and writes copy IDs as plain strings, so you can also copy its logic instead of depending on
Almanac:

```java
SeasonalCopyId.parse("almanac:mid_winter/minecraft/plains")
        // Optional[SeasonalCopyId[subSeason=MID_WINTER, original=minecraft:plains]]
SeasonalCopyId.of(SubSeason.LATE_FALL, "terralith:alpine/high_peaks")
        // "almanac:late_fall/terralith/alpine/high_peaks"
```

### Examples

Rain or snow particles that follow the season:

```java
Biome.Precipitation falling = AlmanacApi.precipitationAt(level, pos)
        .orElseGet(() -> level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel()));
```

One code path for every client: read the seasonal biome when Almanac answers, and the biome in the world otherwise
(which, for a client without Almanac on an Almanac server, is already the copy):

```java
Holder<Biome> biome = AlmanacApi.seasonalBiome(level, pos).orElseGet(() -> level.getBiome(pos));
float temperature = biome.value().getBaseTemperature();
boolean snows = biome.value().coldEnoughToSnow(pos, level.getSeaLevel());
```

### Rain levels, dry places and sandstorms

Vanilla has one rain and thunder level per dimension. Almanac gives each overworld player their own share of it,
through vanilla's weather events, so `Level#getRainLevel` and `getThunderLevel` on a client are that player's share:

- **Where rain or snow can fall this sub-season**: the full levels if the current event reaches the climate there,
  0 if it doesn't, so the sky stays clear wherever nothing will fall.
- **Where nothing can fall this sub-season** (the sub-season's copy of the biome has no precipitation): the full
  levels (0.1.2). The sky darkens as in plain Minecraft and no rain is drawn. That is HOT biomes (vanilla's desert,
  badlands, eroded badlands and wooded badlands) from early spring to late fall, and TEMPERATE biomes (vanilla's
  plains, forests, jungles, savannas, swamps, oceans, rivers, beaches, meadow, stony peaks, windswept hills and the
  cave biomes) in mid summer, their dry cell. COLD biomes always have something that can fall.
- The share fades at vanilla's rate (0.01 a tick) as a player walks between places, so the sky changes smoothly.
- The thunder level goes out plain, as vanilla sends it, and the client multiplies it by its rain level, so
  `Level#getThunderLevel` on a client is the world's thunder times the player's share (from 0.1.3; 0.1.2 servers
  scaled it twice, so storm darkness faded early as a storm ended).
- Lightning is unchanged: the server only strikes where it really rains (`isRainingAt` goes through Almanac's
  precipitation), so dry places get none.

For a sandstorm: a darkened sky with nothing falling is the cue, `worldRainLevel` the strength (`worldThunderLevel`
for a stronger storm), and **raise sand only where `climateOf(...)` or `climateAt(...)` has `base() == Climate.HOT`**,
so mid-summer plains and oceans get a dry storm sky but no sand:

```java
boolean sandstorm = level.getRainLevel(partialTick) > 0
        && level.precipitationAt(pos) == Biome.Precipitation.NONE
        && AlmanacApi.climateAt(level, pos).map(climate -> climate.base() == Climate.HOT).orElse(isDesertBiome);
float strength = AlmanacApi.worldRainLevel(level);
```

### Noticing changes

`AlmanacEvents.SEASON_CHANGED` fires when the sub-season, the rain reach or a snow depth changes. Use it to drop
caches you keyed by biome or worked out from the season:

```java
AlmanacEvents.SEASON_CHANGED.register((level, change) -> {
    if (level.isClientSide() && (change.subSeason() || change.rainReach())) {
        precipitationCache.clear();
    }
});
```

- On the server it fires on the server thread with the overworld, at most once a tick, first as the server starts.
- On a client it fires on the client thread with the client's level whenever the server's season sync arrives with a
  change, including the first sync after joining. That level is whichever one the player is in, so in the Nether or
  the End every answer for it is empty: work things out again when the player is back in the overworld rather than in
  the listener.
- In singleplayer both happen, each with its own level.
- Snow depths change in steps of a sixteenth of a layer, so a long snowfall fires every few hundred ticks; listeners
  should be quick.

## Tags

Data packs can change these like any tag. The climate tags and `#almanac:snow_surface` are read when the server
starts, so changes to them apply after a restart (a `/reload` keeps the seasonal copies in their tags, from 0.1.3, but
doesn't sort biomes into climates again).

| Tag | What it does |
|---|---|
| `#almanac:climate/hot`, `#almanac:climate/temperate`, `#almanac:climate/cold` (biome) | Puts a biome in a climate, ahead of the conventional tags and its temperature. |
| `#almanac:snow_surface` (biome, since 0.1.2) | Biomes whose ground world generation makes partly of snow blocks; far terrain leaves them as captured while their snow melts (`docs/FAR-SNOW.md`). |
| `#almanac:no_seasonal_ice` (biome, since 0.1.3) | Biomes whose water never gets the season's ice: oceans (`#minecraft:is_ocean`) by default, so boats cross them in winter. They freeze as their real biome does, so frozen oceans stay as in plain Minecraft. |
| `#almanac:natural_snow_ground` (block) | Ground whose native snow melts in summer with "Native snow melts in summer" on; snow on anything else (snow blocks, builds) stays. |

## Far terrain (since 0.1.2)

For mods that draw far terrain (LiteLights' pass over Distant Horizons and Voxy): where the far land should show
snow right now, and its seasonal colours. The full contract, with the rule, the climate map, costs and what isn't
modelled, is `docs/FAR-SNOW.md`.

Unlike the rest of the API these answer **only on a client with Almanac 0.1.2 or later** connected to an Almanac
server, for the client's overworld, from a coarse climate map the server sends (one biome per 32-block cell, out to
4,096 blocks by default). Everywhere else they say unknown or empty. All are safe from any thread. They answer
for the overworld only: fill with the overworld's level, and expect "unknown" for any other.

| Method | Returns |
|---|---|
| `farSnowLine(Level, int x, int z)` | The lowest block Y at which a snow layer lies in the column now (the Y of the top face of the ground under it), or `FAR_SNOW_EVERYWHERE`, `FAR_SNOW_NOWHERE` or `FAR_SNOW_UNKNOWN`. Early spring keeps the snow the land had when it began, so far terrain clears at mid spring. |
| `fillFarSnowLines(Level, originX, originZ, spacing, width, height, int[] lines)` | The same for a grid, row by row (x fastest), into `lines[j * width + i]`; returns how many are known. For filling a texture. |
| `seasonalGrassColour(Level, int x, int z)`, `seasonalFoliageColour(...)` | `OptionalInt`: today's seasonal colour (opaque ARGB) of the column's biome on the map. Empty where the map has no tile or the client doesn't know the biome; given even where the snow line is unknown (snow-block biomes while they melt). |
| `farClimate(Level, int x, int z)` | `Optional<FarClimate>`: the column's biome on the map, its base climate, its climate now and whether the current rain event reaches it. Empty where the map has no tile, for unknown biomes and for biomes without seasons. |
| `farSnowChangedTick(Level)` (since 0.1.3) | `OptionalLong`: the overworld game tick at which what far terrain shows last changed everywhere (see "Telling a season change from map data" below). With `thawTicks` it says how far through its melt the far land is, and with `formingTicks` how far through its snow forming, so an animation can resume part way, for a player who joins mid-melt for example. It survives server restarts. It moves only when the far rule's answers can change: since 0.1.3.1, snow getting deeper or shallower in a climate that keeps some (two layers to one) still fires the event but leaves the tick where it was. On the server, and on a 0.1.3 client of a 0.1.3 server, where it arrives before the change's event fires; empty on older servers, where you start the animation at the event instead. |

The sentinels are `FAR_SNOW_UNKNOWN = Integer.MIN_VALUE`, `FAR_SNOW_EVERYWHERE = Integer.MIN_VALUE + 1` and
`FAR_SNOW_NOWHERE = Integer.MAX_VALUE`, so once unknown is ruled out the whole test for an upward face is
`faceY >= line`:

```java
int line = AlmanacApi.farSnowLine(level, x, z);
if (line != AlmanacApi.FAR_SNOW_UNKNOWN) {
    boolean snowBelongsHere = topFaceY >= line;
}
```

`AlmanacEvents.FAR_CLIMATE_CHANGED` fires on the client thread, after the change, with the area (block coordinates,
both ends included) and which answers may have changed (`snowLines`, `colours`, `climate`): when a map tile arrives
or is dropped, and everywhere when the season, the snow or the day's colours change:

```java
AlmanacEvents.FAR_CLIMATE_CHANGED.register((level, change) -> {
    if (change.snowLines() && change.intersects(textureMinX, textureMinZ, textureMaxX, textureMaxZ)) {
        snowTextureDirty = true; // fill again with fillFarSnowLines, once per frame at most
    }
});
```

**Telling a season change from map data.** Only a change that is everywhere with `snowLines` alone set
(`change.isEverywhere() && change.snowLines() && !change.colours() && !change.climate()`) is the far land itself
changing: a new sub-season, snow appearing or going, the native snow setting. That is the one to animate: a melt
over `thawTicks`, snow forming over `formingTicks`. It comes when a climate's snow depth reaches a whole layer, half a
layer into a snowfall (about 2,048 ticks), so the land near players, where vanilla's own snowfall has been at work
since the snow started, is already partly white by then. Everything else (a tile arriving as you join or move, tiles dropped, the map switched on or off, which
also sets colours and climate) is the map filling in and should show at once.

Nothing fires on disconnecting, when every answer becomes unknown: clear your far-snow state when the player leaves a
world.

## The two kinds of client

Almanac works on the server alone, so your client mod can meet a season in two ways.

### Almanac on the client too

The client receives the **real** biomes (`minecraft:plains`), the season sync and the seasonal climate hooks.

- Use `AlmanacApi`, or the game's level-based lookups, which Almanac hooks on both sides: `Level#precipitationAt`,
  `Level#isRainingAt`, and the precipitation `ClientLevel` uses for drawing rain and snow.
- Calling `Biome#getPrecipitationAt`, `Biome#coldEnoughToSnow`, `Biome#shouldSnow` or `Biome#getBaseTemperature`
  directly on the real biome gives the **biome's own** climate, not the season's: rain over snowy plains in winter,
  nothing over a desert's winter rain. A biome on its own doesn't know which level or season it is in, so Almanac
  leaves it alone (changing it globally would also reach world generation). Ask the level or the API instead, or
  take `AlmanacApi.seasonalBiome` and ask that.
- Grass, foliage and leaf colours are already seasonal through the game's colour resolvers; nothing to do.

### Almanac only on the server

The client has no Almanac, so the API isn't there. The server sends it **seasonal copies** of the biomes instead, with
IDs like `almanac:mid_winter/minecraft/plains`.

- Each copy's temperature, precipitation and grass, foliage and dry-foliage colours are already the sub-season's, so
  the vanilla lookups on the biome in the world are seasonal as they are.
- Copies carry every tag their original has, so tag checks such as `#minecraft:is_forest` or `#c:is_desert` work,
  also after a `/reload` or a data pack change (from 0.1.3; before that, a reload took the copies out of every tag
  until the server restarted).
- To find the real biome, parse the ID with the format above (or copy `SeasonalCopyId`'s logic).
- Each player gets their own rain and thunder strength from where they stand (see "Rain levels, dry places and
  sandstorms" above), so `Level#getRainLevel` drops to nothing where rain could fall but the current event doesn't
  reach, and stays full where nothing can fall this sub-season. A copy can't say that by itself: check the rain
  level as well as the biome's precipitation.
- The copy changes at each sub-season change, when the server re-sends the biomes of loaded chunks.

## Known gaps

- The far-terrain lookups work only on a client with Almanac 0.1.2 or later; a server alone can't give far terrain
  its snow, since the far mods draw on the client. Their answers are one biome per cell, so columns near a biome
  border can be a cell off.

- `snowDepth` on a client is rounded to sixteenths of a layer.
- Everything is empty outside the overworld, and on a client until the first season sync arrives.
- `seasonalBiome` on a client with Almanac returns copies from the client's registry. They come with the server's
  registries; a test client with Almanac found all 684 copies of vanilla 26.3's 57 overworld biomes there.
- Fabric's biome modification API runs at server start, after the copies are made from the data-pack biomes.
  Modifications that select biomes by key, or by "found in the overworld", don't reach the copies (they aren't in any
  biome source). Modifications that select by tag do reach them, since copies carry their originals' tags: one that
  changes the weather (precipitation, temperature) or the grass, foliage or dry-foliage colour replaces that copy's
  seasonal climate or colours for players without Almanac. The server logs a warning at start naming such copies; a
  mod doing that should leave biomes in the `almanac` namespace alone. Feature and spawn changes are harmless, since
  copies are never generated.
