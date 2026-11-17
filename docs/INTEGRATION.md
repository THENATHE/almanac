# Almanac — integrating with other mods

For authors of mods that draw weather, place snow, colour terrain or otherwise care what season it is. Almanac is a
Fabric mod for Minecraft 26.3, mod ID `almanac`.

**Stable contract from Almanac 0.1.1 on:** everything in the `com.thenathe.almanac.api` package, and the seasonal copy
ID format `almanac:<sub_season>/<namespace>/<path>`. Releases only add to it (0.1.2 added biome-based `seasonalBiome` and `climateOf`,
`worldRainLevel`, `worldThunderLevel` and `thawTicks`, the far-terrain lookups and `AlmanacEvents.FAR_CLIMATE_CHANGED`; nothing
from 0.1.1 changed). Anything outside the `api` package can change in any
release.

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

## The API

Everything is static on `com.thenathe.almanac.api.AlmanacApi`. Each method in this table works on the server (any
`ServerLevel`) and on a client connected to an Almanac server (its `ClientLevel`), and gives the same answers on both.
The far-terrain lookups further down are the exception: they answer only on a client.

| Method | Returns |
|---|---|
| `subSeason(Level)` | `Optional<SubSeason>`: one of twelve, `EARLY_SPRING` to `LATE_WINTER`. |
| `climateAt(Level, BlockPos)` | `Optional<ClimateAt>`: the biome's base climate (`HOT`, `TEMPERATE`, `COLD`), the climate the season gives it now (one step colder in winter, warmer in summer), and whether the current rain event reaches it. |
| `climateOf(Level, Holder<Biome>)` (0.1.2) | The same for a biome you already have, for example from your own sampling grid, so Almanac doesn't look the biome up again (which is slower and can land on a neighbouring biome at borders). A seasonal copy counts as the real biome behind it. `climateAt` is `climateOf` of the biome at the position. |
| `precipitationAt(Level, BlockPos)` | `Optional<Biome.Precipitation>`: what falls there during the current rain event this sub-season (`NONE`, `RAIN` or `SNOW`), with the rain reach applied: `NONE` where the event doesn't reach the biome's climate. It doesn't check whether it is raining or whether the sky is open; `Level#precipitationAt` does both and gives the same seasonal answer. |
| `seasonalBiome(Level, BlockPos)` | `Optional<Holder<Biome>>`: the biome a player without Almanac gets there, the sub-season's copy of the real biome (its temperature, precipitation and colours are already seasonal), or the biome itself if it has no copies. The copy comes from the level's own biome registry. |
| `seasonalBiome(Level, Holder<Biome>)` (0.1.2) | The same for a biome you already have: the current sub-season's copy of it, or the biome itself if it has no copies. A seasonal copy (of any sub-season) is first turned back into its real biome, so it gives the current sub-season's copy. The position-based call is this one with the biome at the position. |
| `originalBiome(Level, Holder<Biome>)` | The real biome behind a copy, from the level's registry; any other biome unchanged. |
| `originalBiome(ResourceKey<Biome>)` | The same, by key, without a registry. |
| `rainReaches(Level, Climate)` | Whether the current (or most recent) rain event reaches a climate this sub-season. |
| `snowDepth(Level, Climate)` | `OptionalDouble`: snow depth in layers, one value per climate for the whole dimension. On a client it is the server's value to a sixteenth of a layer. |
| `thawTicks(Level)` (0.1.2) | `OptionalLong`: how long the season's snow and ice take to thaw in loaded chunks right now, in game ticks (the `thawDays` setting, at most half the current sub-season). On a client with Almanac 0.1.2 or later it comes with the climate state. Read it when a transition starts: LiteLights uses it as the length of its far-terrain melt and snow-forming animation, so far melts last as long as near ones. Empty when there is no season; callers without it use their own default. |
| `worldRainLevel(Level)`, `worldThunderLevel(Level)` (0.1.2) | The dimension's own rain and thunder levels, 0 to 1, before each player's share (below). On the server, and on a client with Almanac 0.1.2 or later on an Almanac server (synced every time they change); elsewhere the level's own levels, which are the same thing there. |

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

Sandstorms only where it is hot right now:

```java
boolean hotNow = AlmanacApi.climateAt(level, pos)
        .map(climate -> climate.seasonal() == Climate.HOT)
        .orElse(isDesertBiome);
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
  change, including the first sync after joining.
- In singleplayer both happen, each with its own level.
- Snow depths change in steps of a sixteenth of a layer, so a long snowfall fires every few hundred ticks; listeners
  should be quick.

## Far terrain (0.1.2)

For mods that draw far terrain (LiteLights' pass over Distant Horizons and Voxy): where the far land should show
snow right now, and its seasonal colours. The full contract, with the rule, the climate map, costs and what isn't
modelled, is `docs/FAR-SNOW.md`.

Unlike the rest of the API these answer **only on a client with Almanac 0.1.2 or later** connected to an Almanac
server, for the client's overworld, from a coarse climate map the server sends (one biome per 32-block cell, out to
4,096 blocks by default). Everywhere else they say unknown or empty. All are safe from any thread.

| Method | Returns |
|---|---|
| `farSnowLine(Level, int x, int z)` | The lowest block Y at which a snow layer lies in the column now (the Y of the top face of the ground under it), or `FAR_SNOW_EVERYWHERE`, `FAR_SNOW_NOWHERE` or `FAR_SNOW_UNKNOWN`. Early spring keeps the snow the land had when it began, so far terrain clears at mid spring. |
| `fillFarSnowLines(Level, originX, originZ, spacing, width, height, int[] lines)` | The same for a grid, row by row (x fastest), into `lines[j * width + i]`; returns how many are known. For filling a texture. |
| `seasonalGrassColour(Level, int x, int z)`, `seasonalFoliageColour(...)` | `OptionalInt`: today's seasonal colour (opaque ARGB) of the column's biome on the map. |
| `farClimate(Level, int x, int z)` | `Optional<FarClimate>`: the column's biome on the map, its base climate, its climate now and whether the current rain event reaches it. |

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
- Copies carry every tag their original has, so tag checks such as `#minecraft:is_forest` or `#c:is_desert` work.
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
- Biome changes that other mods make through Fabric's biome modification API at server start don't reach the copies
  (they are made earlier, from the datapack biomes).
