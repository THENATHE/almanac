# Almanac — integrating with other mods

For authors of mods that draw weather, place snow, colour terrain or otherwise care what season it is. Almanac is a
Fabric mod for Minecraft 26.3, mod ID `almanac`.

**What other mods can rely on.** The supported way in is the `com.thenathe.almanac.api` package and the biome and block
tags listed under "Tags" below. Anything outside the `api` package can change in any release. (Up to 0.1.7 the seasonal
copy ID format `almanac:<sub_season>/<namespace>/<path>` was part of it too; there are no copies from 0.2.0.)

**From 0.2.0 Almanac is on every client of an Almanac server,** of the same protocol as the server's: the server
refuses anyone else while they connect. A client mod can count on Almanac's answers on such a server.

- **A call keeps its meaning while it exists.** What a call answers, and the values of constants such as the
  `FAR_SNOW_*` sentinels, don't change under the same name; a different answer gets a new call.
- **Calls can be removed.** Up to 0.1.6 this file promised that releases only add to the API. That promise is
  withdrawn from 0.1.7: a call that no longer serves a purpose may go, and so may one that is replaced. Every
  removal is named in the changelog and in "Removed" below, with the release it went in. Check the version before
  using a call (below), so a removed call is a clear message rather than a crash.
- **Saved worlds and settings carry over.** A world or a settings file from an earlier Almanac opens in a later one
  with nothing lost; fields that were retired are dropped. (Going back to an older Almanac isn't supported.)

Each addition is marked with the version it came in:

- **0.1.2** added biome-based `seasonalBiome` and `climateOf`, `worldRainLevel`, `worldThunderLevel` and `thawTicks`,
  the far-terrain lookups and `AlmanacEvents.FAR_CLIMATE_CHANGED`.
- **0.1.3** added `precipitationOf` and `farSnowChangedTick`, and the tag `#almanac:no_seasonal_ice`.
- **0.1.3.1** added nothing; `farSnowChangedTick` moves only when far snow changes, not with every layer of depth.
- **0.1.3.2** added `formingTicks`.
- **0.1.4** added `snowCover`.
- **0.1.6** added `fillFarBareSnowLines`.
- **0.2.0** added `seasonClimateOf` and `seasonClimateAt` (and the type `SeasonClimate`), and the tag
  `#almanac:no_seasonal_snow`.
- **0.3.0** added `snowLayers`, `iceCover` and `snowClocks`, and the types `SnowLayers` and `SnowClocks` ("Added in
  0.3.0" below).
- **0.4.0** added `regionalWeather`, `weatherAt`, `stormsNear`, `weatherTick`, `prevailingWind` and `snowLayersAt`,
  and the types `Storm`, `Wind` and `WeatherAt` ("Added in 0.4.0: regional weather" below).
- **0.4.1** added nothing. `stormsNear` keeps its meaning and now holds to it while the weather stands still: a storm an
  operator placed then is listed from when it was asked for, and storms an operator cleared then are left out, as
  `weatherAt` already had them (in 0.4.0 both waited for the weather to move again).
- **1.0.0** added nothing. It is 0.4.1 under the number of the public release: the same calls with the same answers,
  the same protocol and the same saved data.

### Removed

- **0.1.7** removed `thawTicks` (0.1.2) and `formingTicks` (0.1.3.2). They were lengths for timing far-terrain
  animations; since 0.1.6 the snow forms and thaws by `snowCover` everywhere, so there is nothing to time, and the
  `thawDays` setting they came from paced nothing. Follow `snowCover` and the bare lines instead (`docs/FAR-SNOW.md`).
- **0.2.0** removed `originalBiome(Level, Holder)` and `originalBiome(ResourceKey)` (0.1.1), `seasonalBiome(Level,
  BlockPos)` (0.1.1) and `SeasonalCopyId` (0.1.1), with the seasonal biome copies they described: there are no
  players without Almanac to send copies to. Ask `seasonClimateOf` or `seasonClimateAt` for the season's climate of a
  biome instead (below). `seasonalBiome(Level, Holder)` (0.1.2) is still there but always answers empty, only because
  LiteLights 1.0.7 and 1.0.8 call it and would stop on a missing method; it goes once no LiteLights before 1.0.9
  needs to be met.
- **0.4.0** removed nothing. What changed under the same names with regional weather on is under "Added in 0.4.0".
- **0.3.0** removed nothing from the `api` package. Outside it, two payloads only older Almanacs used went: the
  `almanac:update_server_settings` payload (every setting at once, from 0.1.2 clients) and the last field of
  `almanac:far_climate_state` (the thaw time, always 0 since 0.1.7, for 0.1.6 clients). Neither kind of client can
  join since 0.2.0.
- **0.4.1 and 1.0.0** removed nothing.

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
`precipitationOf` or `farSnowChangedTick`, `<0.1.4` for `snowCover`, `<0.1.6` for `fillFarBareSnowLines`, `<0.2.0`
for `seasonClimateOf`, `<0.3.0` for `snowLayers`, `iceCover` or `snowClocks`, `<0.4.0` for the regional weather
calls), or check
the version at runtime and only use the newer calls when they are there:

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

A call that was removed throws `NoSuchMethodError` in the same way, so a mod that still uses one should name the
release that removed it in its `fabric.mod.json` (`"almanac": ">=0.1.7"` under `breaks` for `thawTicks` or
`formingTicks`, `">=0.2.0"` for `originalBiome` or `SeasonalCopyId`), or check the version as above and stop using the
call from that release.

### When the two mods' versions don't match

For Almanac and the author's other mods (LiteLights today) the rule is: when one is too old for the other, the
features they share switch off with a notice, and neither keeps code for the other's older versions. Each mod reads
the other's version from Fabric Loader, as above.

- **Almanac with a LiteLights older than 1.0.7** (from Almanac 0.1.7): those LiteLights time far snow by `thawTicks`
  and `formingTicks`. Almanac's client then doesn't take the climate map, so far terrain keeps the snow it was
  captured with and LiteLights never reaches the removed calls. The log says so as the game starts, and the chat when
  the player joins a world. Everything else LiteLights reads from Almanac (precipitation, climates, the world's rain
  and thunder) works as before.
- **Almanac 0.2.0 or later with LiteLights 1.0.8 or older:** those LiteLights read the season's climate from Almanac's
  seasonal copies (`seasonalBiome`), which 0.2.0 doesn't have. The call is kept, answering empty, which those
  LiteLights read as Almanac having nothing to say about a biome: their weather looks that depend on how cold or dry a place is this
  sub-season (snow-cold places, sandstorm seasons, the aurora's coldness, the kind of place for particles and glass
  weather) go by each biome's own climate. What falls still follows the season (`precipitationOf`), and from 1.0.7 far
  terrain is shared as before. The log says so as the game starts, and the chat when the player joins a world
  (`LiteLightsCheck`, which from Almanac 0.3.0 names LiteLights 1.0.9 as the first that works with it,
  `LiteLightsCheck.Shared.SEASONAL_CLIMATE`; 0.2.0 named 1.0.8, a guess made before 1.0.9 shipped).
- **LiteLights 1.1.0** is the LiteLights release that goes with Almanac 1.0.0, and names 1.0.0 as its minimum.
- **LiteLights with an Almanac too old for it** is LiteLights' side.

#### What LiteLights had to change for Almanac 0.2.0

LiteLights 1.0.9 asks `seasonClimateOf` instead of `seasonalBiome`. The list as written for it, read in LiteLights
1.0.7's source (`render/feature/climate` and its callers):

1. `AlmanacClimate.placeOf` asks `AlmanacApi.seasonalBiome(level, biome)` for the sub-season's copy. Ask
   `AlmanacApi.seasonClimateOf(level, biome)` instead, and keep the `SeasonClimate` in `SeasonalClimate.Place` in
   place of the copy's holder (`hasClimate`, `baseHot` and `reached` come from it too: `base()`, `rainReaches()`).
2. `SeasonalClimate.coldEnoughToSnow`: `place.seasonal().value().coldEnoughToSnow(pos, seaLevel)` becomes
   `climate.coldEnoughToSnow(pos, seaLevel)`; `dryHeat`: `!place.seasonal().value().hasPrecipitation()` becomes
   `!climate.canFall()`; `fallAt`'s fallback for Almanac 0.1.2 (the copy's `getPrecipitationAt`) goes, since
   `precipitationOf` is always there.
3. `ClimateSource.seasonalBiome` and `LocalClimate.seasonalBiome` hand out a copy for its temperature. Their callers
   want the season's temperature or kind of place: `AuroraColdness.of` (`getBaseTemperature` of the copy) takes
   `SeasonClimate.temperature()`, and `BiomeClimate.of` (from `HeatSources`, `ColumnGrid`, `Surroundings` and
   `GlassWeather`) takes the biome with the season's temperature beside it instead of a copy.
4. `SeasonalCopies` (LiteLights' own parser of copy IDs, for a LiteLights player without Almanac on an Almanac server)
   goes with every use of it: `ClimateWatch` (its sub-season from the copy's name), `LocalClimate.idOf`,
   `BiomeClimate.of`, `BiomeAtmospheres` (sky-fog's table looked up by the copy's original) and `VanillaClimate.dryHeat`
   (`!SeasonalCopies.isCopy(biome)`). No such player can join an Almanac 0.2.0 server, and under the rule above no code
   is kept for Almanac 0.1.x servers.
5. `AlmanacVersion` gains 0.2.0 as the minimum for the season's climate; with an older Almanac LiteLights switches
   that part off with a notice, as the rule above asks, instead of reading copies.
6. Build against the 0.2.0 API, and ship as 1.0.8 or later (or tell Almanac's `LiteLightsCheck` the version). Its
   tests `SeasonalCopiesTest` and the copy cases of `SeasonalClimateTest` go or move to `SeasonClimate`.

LiteLights 1.0.9 is out with them, but 1.0.7 and 1.0.8 still call `seasonalBiome(Level, Holder)`, so Almanac keeps it
until no LiteLights before 1.0.9 needs to be met.

#### What LiteLights reads from Almanac 0.3.0, and what it needs for layers

LiteLights 1.0.9 reads `snowDepth` (taking a depth between 0 and 1 as the cover), `fillFarSnowLines`,
`fillFarBareSnowLines`, `seasonClimateOf`, `precipitationOf`, `worldRainLevel` and `worldThunderLevel`, the seasonal
colours, `farClimate`, `SEASON_CHANGED` and `FAR_CLIMATE_CHANGED`. Every one is in 0.3.0 with its meaning kept, so
1.0.9 works with Almanac 0.3.0 unchanged (checked in game, 10 of 10). One thing shows: near a whole first layer its
far snow looks full a little early, since a depth of 1.0 is a first layer of 95 %.

To draw layers near and far it should:

1. Read `snowLayers(level, climate)` in place of `snowDepth`: `share(layer)` for each layer, and `layersAt(point)`
   for a column, with points of its own scatter (0 to 1, evenly spread). A far column between its far snow line and
   its bare line then holds `layersAt` layers; at and above the bare line it keeps the snow that lies whatever the
   season, as now. `cover()` is what it reads from `snowDepth` today, without the 95 % difference.
2. Read `iceCover(level, climate)` for still water, if it is to show seasonal ice: how far ponds of that climate have
   frozen, shore first. Almanac's far rule gives no ice lines (`docs/FAR-SNOW.md`, "What the rule doesn't model"), so
   far ponds stay as captured unless LiteLights draws ice of its own from the cover.
3. Refill or redraw as the shares move. On a client they are followed forward each time they are asked
   (`snowLayers` and `iceCover` move smoothly between syncs), and `SEASON_CHANGED` with `snowDepth` set still fires
   when a depth or ice cover passes a sixteenth step, which is the cue for work that shouldn't run every frame.
4. Build against the 0.3.0 API and name it in `AlmanacVersion` as the minimum for layers, switching that part off with
   a notice beside an older Almanac, as the rule above asks.

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
| `climateOf(Level, Holder<Biome>)` (since 0.1.2) | The same for a biome you already have, for example from your own sampling grid, so Almanac doesn't look the biome up again (which is slower and can land on a neighbouring biome at borders). `climateAt` is `climateOf` of the biome at the position. |
| `seasonClimateAt(Level, BlockPos)`, `seasonClimateOf(Level, Holder<Biome>)` (since 0.2.0) | `Optional<SeasonClimate>`: a biome's climate this sub-season, what the seasonal copies of 0.1.x carried: `temperature()` (what `getBaseTemperature` would say with the season's climate), `canFall()` (whether rain or snow can fall when an event reaches it), `coldEnoughToSnow(pos, seaLevel)` and `temperatureAt(pos, seaLevel)` (with vanilla's cooling with height), `precipitationAt(pos, seaLevel)` (what falls when an event reaches it), `base()`, `seasonal()`, `rainReaches()` and `noSeasonalSnow()`. Empty for biomes without seasons. Safe from any thread on a client. |
| `precipitationAt(Level, BlockPos)` | `Optional<Biome.Precipitation>`: what falls there during the current rain event this sub-season (`NONE`, `RAIN` or `SNOW`), with the rain reach applied: `NONE` where the event doesn't reach the biome's climate. It doesn't check whether it is raining or whether the sky is open; `Level#precipitationAt` does both and gives the same seasonal answer. |
| `precipitationOf(Level, Holder<Biome>, BlockPos)` (since 0.1.3) | The same for a biome you already have, by the same rule, so you don't need a copy of Almanac's "what falls here" logic: what falls at the position (it still counts for the height, snow above the snow line) in that biome. `precipitationAt` is `precipitationOf` of the biome at the position. |
| `seasonalBiome(Level, Holder<Biome>)` (since 0.1.2, deprecated in 0.2.0) | Always empty from 0.2.0, which has no seasonal copies; there only until LiteLights stops calling it ("Removed" above). |
| `rainReaches(Level, Climate)` | Whether the current (or most recent) rain event reaches a climate this sub-season. |
| `snowDepth(Level, Climate)` | `OptionalDouble`: snow depth in layers, one value per climate for the whole dimension: the sum of its layers' shares, so the average number of layers on open ground where the season makes it cold enough. Between 0 and the most layers (3 by default; until 0.2.0 up to the `max_snow_accumulation_height` game rule). On a client it is the server's value followed forward to the client's game time, so it moves smoothly (until 0.2.0, to a sixteenth of a layer as last synced). A value between 0 and 0.9 is the first layer's share, as before. |
| `snowCover(Level, Climate)` (since 0.1.4) | `OptionalDouble`: the first layer's share, 0 to 1 (`SnowLayers.cover`): the share of a climate's open ground the season's snow lies on. Each column holds the snow once the share passes a point of its own, in loaded chunks, in chunks that load and on far terrain alike, forming and (from 0.1.6) melting. Until 0.2.0 it was `snowDepth` up to 1; from 0.3.0 the second layer starts at 0.9, so near a whole first layer the cover is a little less than the depth (0.95 at 1.0, whole at 1.1, wherever more than one layer is allowed). Empty where `snowDepth` is. |
| `snowLayers(Level, Climate)` (since 0.3.0) | `Optional<SnowLayers>`: how the climate's snow lies layer by layer, with the most layers it may build to ("Added in 0.3.0"). On a client, followed forward like `snowDepth`. Empty where `snowDepth` is. |
| `iceCover(Level, Climate)` (since 0.3.0) | `OptionalDouble`: how far still water in the climate has frozen over, 0 to 1. Each pond column freezes once this passes its own freezing point, which rises from the shore inward, and thaws the same way back. It builds over the ice clock while the climate is cold, whatever the weather, and goes over the thaw clock while it is warm. Only biomes that get seasonal ice freeze by it (not `#almanac:no_seasonal_ice`), and only where the season makes the water cold enough. Empty where `snowDepth` is. |
| `snowClocks(Level)` (since 0.3.0) | `Optional<SnowClocks>`: the snowfall, thaw and ice clocks in ticks (the ice clock as in force, so the snowfall clock's value when `iceTicks` is 0) and the most layers in force: the `maxSnowLayers` setting, or more in a world an older Almanac let build deeper under a raised game rule. Empty where `snowDepth` is. |
| `worldRainLevel(Level)`, `worldThunderLevel(Level)` (since 0.1.2) | The dimension's own rain and thunder levels, 0 to 1, before each player's share (below). On the server the level's own; on a client the levels the server sent, as vanilla sends them (from 0.2.0; up to 0.1.7 the server sent them on a channel of their own). |

### Examples

Rain or snow particles that follow the season:

```java
Biome.Precipitation falling = AlmanacApi.precipitationAt(level, pos)
        .orElseGet(() -> level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel()));
```

The season's temperature and cold where Almanac answers, and the biome's own otherwise (another dimension, or a
server without Almanac):

```java
Holder<Biome> biome = level.getBiome(pos);
Optional<SeasonClimate> season = AlmanacApi.seasonClimateOf(level, biome);
float temperature = season.map(SeasonClimate::temperature).orElse(biome.value().getBaseTemperature());
boolean snows = season.map(climate -> climate.coldEnoughToSnow(pos, level.getSeaLevel()))
        .orElse(biome.value().coldEnoughToSnow(pos, level.getSeaLevel()));
```

### Added in 0.3.0

The snow builds and melts layer by layer on two clocks, and ponds freeze by an ice cover.

- **`SnowLayers`** (class, `com.thenathe.almanac.api`): how a depth lies layer by layer.
  - `SnowLayers.of(depth, maxLayers)`, `SnowLayers.none(maxLayers)`: the layers of a depth where snow may build
    `maxLayers` deep (1 to 8); the depth is held between 0 and the most layers.
  - `depth()`, `maxLayers()`.
  - `share(layer)`: the share of open ground a layer covers, 0 to 1, counting from 1 for the layer on the ground; 0
    outside 1 to `maxLayers`.
  - `cover()`: the first layer's share. `started()`: how many layers have any share at all.
  - `layersAt(point)`: how many layers lie on a column whose point is `point` (0 to 1): one for each share past it.
    Shares fall layer by layer, so a column with a second layer always has a first.
  - `NEXT_LAYER_AT = 0.9`: how far a layer is down when the next one starts, and how far the top one is gone when the
    one under it starts to go. `MOST_LAYERS = 8`.
  - The rule: a layer has the ground alone until it is 90 % down; then the next one starts and the two share the
    snowfall, half each, for 0.2 of depth, until the lower one is whole. The top layer never shares. The thaw is the
    same function backwards. Shares never jump, never exceed the share of the layer under, and add up to the depth.
    At a depth of 1.0 with 3 layers allowed the shares are 95 %, 5 % and 0.
- **`SnowClocks`** (record): `snowfallTicks`, `thawTicks`, `iceTicks`, `maxLayers`.
- **`AlmanacApi.snowLayers(Level, Climate)`** (`Optional<SnowLayers>`), **`iceCover(Level, Climate)`**
  (`OptionalDouble`) and **`snowClocks(Level)`** (`Optional<SnowClocks>`), in the table above.

What changed under the same names. Both still answer what they always said they did (the depth in layers, and the
share of open ground the snow lies on); the rule underneath changed, so their numbers differ near a whole layer:

- **`snowDepth`** keeps its signature. It now goes up to the most layers (3 by default), and on a client it is
  followed forward from the last sync to the client's game time, so it is smooth rather than in sixteenths. From 0 to
  0.9 it is still the first layer's share.
- **`snowCover`** is the first layer's share, no longer `snowDepth` up to 1. The two agree up to 0.9; at 1.0 the cover
  is 0.95.
- **How the client gets it:** the season sync (format 3) carries, per climate, the depth and the ice cover as doubles,
  how fast each moves a tick, the tick they were read at, the clocks and the most layers. A client follows them
  forward to its own game time. The server sends a sync again when a pace or a clock changes, or when a depth or an
  ice cover has moved a sixteenth, so the answers between two syncs are exact while the weather holds.

### Added in 0.4.0: regional weather

Almanac's weather is regional from 0.4.0 (the server setting `regionalWeather`, on by default): storms form, move
across the land and pass, so it rains or snows in one place and not in another, and every place is under rain about as
often as in vanilla (15.8 % of the time). Storms are worked out from a weather seed and the time, the same on the
server and every client; nothing about them is sent each tick. The changelog's 0.4.0 entry has how they behave.

**What a mod that reads the weather at the player needs to do: nothing.** On a client `Level#getRainLevel` and
`getThunderLevel` are the weather over the camera, faded at vanilla's 0.01 a tick, and `worldRainLevel` and
`worldThunderLevel` are the storm over the camera whatever falls from it (where up to 0.3.0 they were the world's
levels before the player's share; there is no rain for the world as a whole now). `precipitationAt`/`Of` and the
climate answers are as before, and `snowDepth`, `snowLayers` and `iceCover` on a client are the snow where the player
stands (each player's season sync carries their own). LiteLights 1.0.9 to 1.0.12 read exactly these and show the weather
where the player stands (checked with 1.0.12 and Fancy clouds; the Dynamic clouds read the same levels and weren't run).

**LiteLights draws the storms** with its Dynamic clouds, from 1.0.15 (1.1.0 is the release that goes with Almanac
1.0.0): it asks `stormsNear` once a second out to its cloud reach, places each storm from `weatherTick` with the
`Storm` record's own arithmetic, rides the clouds on `prevailingWind` (summed into how far it has carried them), and
shows the rain and thunder at the camera as above, under cloud that is really there. LiteLights 1.0.15 names 0.4.0
as its one minimum; with 0.4.0 a storm placed while the weather stands still gets no cloud until the weather moves
(fixed in 0.4.1, above).

What changed under the same names, with regional weather on:

- **On the server** the world's own weather reads clear: `Level#isRaining`, `getRainLevel` and `worldRainLevel` say no
  rain, and vanilla's weather cycle doesn't run in the overworld. Every check vanilla makes per place goes by the storm
  there (`isRainingAt`, rain on entities, farmland, fire, fishing, snowfall and freezing, cauldrons, lightning,
  monsters in thunder, foxes, pandas, bees, lightning rods, Channeling, sleeping in a daytime thunderstorm). Ask
  `weatherAt` for a place.
- **`rainReaches` and `climateAt(...).rainReaches()`** say whether storms may reach the climate this sub-season (its
  share is above 0). Whether the storm over a place reaches its climate is `weatherAt(...).rain() > 0`.
- **`snowDepth` on the server** is the snow at the world's spawn; on a client, where the player stands. Use
  `snowLayersAt` for any place.

New calls (all on `AlmanacApi`; on a client every one is safe from any thread):

| Call | Returns |
|---|---|
| `regionalWeather(Level)` | Whether regional weather decides the weather in the level now. False outside the overworld, with the setting off, and on a client before the server's weather has arrived. |
| `weatherAt(Level, BlockPos)` | `Optional<WeatherAt>`: `storm` (how heavy the storm overhead is, 0 to 1, whatever falls), `rain` (the rain or snow level there: the storm's where it reaches the climate, 0 where it passes the climate by, the storm's again where nothing can fall this sub-season, the sandstorm cue), `thunder`, and `falling` (what falls on the place now, as `Level#precipitationAt`). Without regional weather, vanilla's one weather with the season's share. |
| `stormsNear(Level, x, z, radius)` | `List<Storm>`, nearest first: every storm whose cloud comes within `radius` blocks now. Empty without regional weather. |
| `weatherTick(Level, partialTick)` | `OptionalDouble`: the weather tick now, the game time less any time the weather stood still (`advance_weather` off). Storms live in weather ticks. |
| `prevailingWind(Level, weatherTick)` | `Optional<Wind>`: the wind storms are carried by, `direction` (radians from east towards south) and `speed` (blocks a tick), with `velocityX`/`Z`. A smooth function of the weather tick. |
| `snowLayersAt(Level, x, z, Climate)` | `Optional<SnowLayers>`: the snow at a place, which differs from place to place since it builds only where storms went. On a client, for any place, loaded or not, worked out from the weather the server sent: the first ask about a place starts the work and answers empty, and it answers a tick or so later. On the server, for loaded land. Without regional weather it is `snowLayers`. |

**`Storm`** (a record; fixed for its whole life, so it is placed at any moment from these numbers alone):

- `id` (the same on every side), `birthX`, `birthZ`, `birthTick` (a weather tick), `lifeTicks`, `endTick()`,
  `velocityX`, `velocityZ` (blocks a tick), `radius` (of a circle of its area), `stretch` (1 or more), `angle` (of its
  long axis), `strength` (0 to 1), `thunder`, `cloudTop` (block height; nothing falls above it), `reach` (its draw
  against the rain shares).
- `centreX(tick)`, `centreZ(tick)`, `phase(tick)` (0 to 1 through its life), `size(tick)` (0 to 1: it grows over the
  first `GROWTH` = 29 % of its life and shrinks over the last), `fullGrown(tick)`, `majorAxis()`, `minorAxis()`,
  `distance(x, z, tick)` (0 at the centre, 1 at the full-grown cloud's edge), `covers`, `rainsOn` (inside
  `RAIN_CORE` = 0.8 of its size), `thundersOn` (inside `THUNDER_CORE` = 0.5 while full grown).

To draw storm clouds that move smoothly, get the storms near the camera every few seconds and place each one every
frame from `weatherTick(level, partialTick)`; a storm placed by an operator's `/weather` is one of them too.

**Mixed versions.** A LiteLights that doesn't know these calls is unaffected: it reads the weather at the player as
before. A LiteLights that uses them should name 0.4.0 as the minimum for them (`AlmanacVersion`) and switch that part
off with a notice beside an older Almanac, as the rule above asks.

### Rain levels, dry places and sandstorms

With regional weather (0.4.0, above) the levels on a client are the storm's over the camera, by the same rules for dry
places. Without it, as up to 0.3.0: vanilla has one rain and thunder level per dimension, and Almanac gives each
overworld player their own share of it, so `Level#getRainLevel` and `getThunderLevel` on a client are that player's
share where the camera is. From 0.2.0 the client works the share out itself, from the season and the climate table the
server sends; up to 0.1.7 the server sent each player their share through vanilla's weather events, so on the client
the result is the same:

- **Where rain or snow can fall this sub-season**: the full levels if the current event reaches the climate there,
  0 if it doesn't, so the sky stays clear wherever nothing will fall.
- **Where nothing can fall this sub-season** (`SeasonClimate.canFall()` is false): the full levels (0.1.2). The sky darkens as in plain Minecraft and no rain is drawn. That is HOT biomes (vanilla's desert,
  badlands, eroded badlands and wooded badlands) from early spring to late fall, and TEMPERATE biomes (vanilla's
  plains, forests, jungles, savannas, swamps, oceans, rivers, beaches, meadow, stony peaks, windswept hills and the
  cave biomes) in mid summer, their dry cell. COLD biomes always have something that can fall.
- The share fades at vanilla's rate (0.01 a tick) as a player walks between places, so the sky changes smoothly.
- `Level#getThunderLevel` on a client is the world's thunder times the player's share, as vanilla multiplies thunder
  by the rain level.
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
  change, including the first sync after joining, and (from 0.2.0, as a sub-season change) when a new climate table
  arrives: an operator changed a climate setting, or the server's tags were reloaded. The server fires it then too. That level is whichever one the player is in, so in the Nether or
  the End every answer for it is empty: work things out again when the player is back in the overworld rather than in
  the listener.
- In singleplayer both happen, each with its own level.
- `snowDepth` is set when a depth or an ice cover passes a step of a sixteenth, or the most layers change (from 0.3.0;
  until 0.2.0, depths alone). At the default snowfall clock a sixteenth of a layer is 750 ticks, so a long snowfall
  fires about that often; listeners should be quick. Between events the answers still move (they are followed
  forward on a client).

## Tags

Data packs can change these like any tag. From 0.2.0 every one of them applies as soon as the server's tags are
loaded again (a `/reload` or a data pack switched on or off), and clients get the new climate table; up to 0.1.7 the
climate tags and `#almanac:snow_surface` applied after a restart.

| Tag | What it does |
|---|---|
| `#almanac:climate/hot`, `#almanac:climate/temperate`, `#almanac:climate/cold` (biome) | Puts a biome in a climate, ahead of the conventional tags and its temperature. |
| `#almanac:snow_surface` (biome, since 0.1.2) | Biomes whose ground world generation makes partly of snow blocks; far terrain leaves them as captured while their snow melts (`docs/FAR-SNOW.md`). |
| `#almanac:no_seasonal_ice` (biome, since 0.1.3) | Biomes whose water never gets the season's ice: oceans (`#minecraft:is_ocean`) by default, so boats cross them in winter. They freeze as their real biome does, so frozen oceans stay as in plain Minecraft. |
| `#almanac:no_seasonal_snow` (biome, since 0.2.0) | Biomes the season never brings snow or ice to: the jungles (`#minecraft:is_jungle`, `#c:is_jungle`) by default. They keep their climate (jungles stay TEMPERATE: rain in spring, summer and fall) and get rain wherever it would give snow, so rain falls there in winter. `climateOf` still reports the climate's winter shift (TEMPERATE now COLD); `seasonClimateOf` says `noSeasonalSnow()` and is never cold enough to snow. |
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
| `fillFarBareSnowLines(...)` (0.1.6) | The same grid for the bare lines: snow that lies whatever the season. The season's snow lies between a column's bare line and its far snow line, on the share of columns `snowCover` gives (from 0.3.0, layer by layer by `snowLayers`), forming and melting (docs/FAR-SNOW.md, "Bare lines and the thaw", "Layers"). |
| `seasonalGrassColour(Level, int x, int z)`, `seasonalFoliageColour(...)` | `OptionalInt`: today's seasonal colour (opaque ARGB) of the column's biome on the map. Empty where the map has no tile or the client doesn't know the biome; given even where the snow line is unknown (snow-block biomes while they melt). |
| `farClimate(Level, int x, int z)` | `Optional<FarClimate>`: the column's biome on the map, its base climate, its climate now and whether the current rain event reaches it. Empty where the map has no tile, for unknown biomes and for biomes without seasons. |
| `farSnowChangedTick(Level)` (since 0.1.3) | `OptionalLong`: the overworld game tick at which what far terrain shows last changed everywhere (see "Telling a season change from map data" below): how long the far land has looked as it does. It survives server restarts. It moves only when the far rule's answers can change: since 0.1.3.1, snow getting deeper or shallower in a climate that keeps some (two layers to one) still fires the event but leaves the tick where it was. On the server, and on a 0.1.3 or later client of a 0.1.3 or later server, where it arrives before the change's event fires; empty on older servers. |

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
changing: a new sub-season, snow appearing or going, the native snow setting. Since 0.1.6 the season's snow comes and
goes by `snowCover` between the bare lines and the far snow lines (from 0.3.0 by the shares of `snowLayers`), so fill
again and let the shares say how much lies; there is nothing to animate. Everything else (a tile arriving as you join
or move, tiles dropped, the map switched on or off, which also sets colours and climate) is the map filling in and
should show at once.

Nothing fires on disconnecting, when every answer becomes unknown: clear your far-snow state when the player leaves a
world.

## The client

From 0.2.0 every player on an Almanac server has Almanac too, and gets the **real** biomes (`minecraft:plains`), the
climate table, the season sync and the seasonal climate hooks.

- Use `AlmanacApi`, or the game's level-based lookups, which Almanac hooks on both sides: `Level#precipitationAt`,
  `Level#isRainingAt`, and the precipitation `ClientLevel` uses for drawing rain and snow.
- Calling `Biome#getPrecipitationAt`, `Biome#coldEnoughToSnow`, `Biome#shouldSnow` or `Biome#getBaseTemperature`
  directly on the biome gives the **biome's own** climate, not the season's: rain over snowy plains in winter,
  nothing over a desert's winter rain. A biome on its own doesn't know which level or season it is in, so Almanac
  leaves it alone (changing it globally would also reach world generation). Ask the level or the API instead
  (`seasonClimateOf` for a biome's temperature and cold).
- Grass, foliage and leaf colours are already seasonal through the game's colour resolvers; nothing to do.

Up to 0.1.7 a client without Almanac could join too and got seasonal copies of the biomes with IDs like
`almanac:mid_winter/minecraft/plains`, whose climate and colours were the sub-season's; client mods parsed those IDs to
find the real biome. No such client can join a 0.2.0 server.

## Known gaps

- The far-terrain lookups work only on a client with Almanac 0.1.2 or later; a server alone can't give far terrain
  its snow, since the far mods draw on the client. Their answers are one biome per cell, so columns near a biome
  border can be a cell off.

- On a client `snowDepth`, `snowLayers` and `iceCover` are the last sync followed forward. When the weather changes
  they keep the old pace until the new sync arrives (the server sends one at once).
- The far rule gives no ice: far ponds are left as captured, whatever `iceCover` says.
- Everything is empty outside the overworld, and on a client until the first season sync arrives.
- The climate table is built from the biomes as the server has them once Fabric's biome modifications are done, so a
  modification that changes a biome's temperature or precipitation changes its seasons too (up to 0.1.7 such changes
  could miss the copies).
