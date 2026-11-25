# Almanac — architecture

How Almanac's code is split into modules, what each owns, and the internal API they talk through. What other mods
read is in `docs/INTEGRATION.md`.

## Modules

Almanac is one mod, one jar and one mod id (`almanac`), built from Gradle subprojects whose boundaries the compiler
enforces. A module compiles against the API only, never against another module's classes, so a reach past the API
fails the build. Every module ships in the same jar and release as core, so the internal API changes freely; nothing
outside Almanac uses it. Other mods use the public API, `com.thenathe.almanac.api`, which is unchanged by the split.

| Subproject | Holds | Compiles against |
|---|---|---|
| `api` | The public API (`com.thenathe.almanac.api`: `AlmanacApi`, `AlmanacEvents`, `Climate`, `SubSeason`, `SeasonClimate`, and from 0.3.0 `SnowLayers` and `SnowClocks`) and the internal API (`com.thenathe.almanac.internal`): interfaces and plain data, and the three small pieces both sides use (`WeatherKind`, `SavedFields`, `FarClimateView`) | Minecraft (server classes), Fabric |
| `core`, `src/main` | Everything that isn't a module: the season clock, its saved data and commands; climates, the climate table, the rain shares and event reach, the climate hooks; settings; the payloads and networking, the refusal; the climate map for far terrain; the server mixins; `fabric.mod.json`. Implements the internal API's `Seasons` (`modules`) and builds the jar | `api`, Minecraft (server classes), Fabric |
| `core`, `src/client` | The client half of core: client state and sync, the client's climate map, client colours and the palettes (`colour`), client rain, the settings screen and its kit, the client mixins and their config, Mod Menu | `core`'s `src/main`, `api`, Minecraft, Fabric, Mod Menu's API |
| `snow` | The snow: the weather log and `snow.dat` (`SnowLog`, `SnowData`); each climate's snow depth and ice cover worked out from it every tick (`SeasonSnow`), and under regional weather each snow node's (`SnowNodes`, published as a `SnowField`), with the rain from a `RainSource` ("Snow by area" below); the state a chunk is brought to (`SnowState`) and the rules per spot (`SnowRules`); the column points and freezing points (`ColumnPoints`), and a loaded chunk's sorted points and cached freezing points (`ChunkPoints`); the chunk records (`ChunkSnow`, `SnowTracking`); catch-up on load and the loaded chunks' rounds (`SnowUpdates`), each column to its target (`ChunkSnowUpdater`); the far rule (`FarSnowRule`); a client's own snow by area, from the log the server sends (`ClientSnow`). Implements the internal API's `Snow` (`SnowModule`). The layer rule itself is the public `SnowLayers`, in `api`, since far terrain and other mods use it too | `api`, Minecraft (server classes), Fabric |
| `weather` (0.4.0) | Regional weather: the storm maths, one class per released version (`StormMaths`, `StormMathsV1`: the lattice, each storm's draws, the seasons' `StormCharacter`s and the wind), a storm's arithmetic (`StormShape`: is a column under its rain now, and when over a stretch, in closed form), the weather worked out from a record (`WeatherField`) and the weather clock (`WeatherClock`), the index of storms near the players (`StormIndex`), the record and `weather.dat` (`WeatherData`), the server's and the client's state (`ServerWeather`, `ClientWeather`), the background thread. Implements the internal API's `Weather` (`WeatherModule`) | `api`, Minecraft (server classes), Fabric |

The jar is `core`'s, with `api`'s and `snow`'s classes and resources merged in (a file two of them ship fails the
build). It is written to `build/libs/`. Sources are under `<subproject>/src/`.
Each module keeps its package names, so class and resource paths in the jar are what they were in 0.2.0, but for the
three pieces moved into `internal`. Most tests are in `core`; the snow and weather modules have tests of their own.

**Server classes only.** Every subproject uses Loom's environment source sets: a `main` source set sees only the
Minecraft classes a dedicated server has, and client code sits in `core`'s `src/client`, which sees `main` but not the
other way round. So nothing a dedicated server loads can reach Almanac's client classes or Minecraft's; before the
split that rested on care alone. The jar's manifest says which entries are client only
(`Fabric-Loom-Client-Only-Entries`), for tools; Fabric Loader doesn't read it.

**Starting.** `Almanac.onInitialize` installs core's side of the internal API first thing
(`modules.ModuleHost.installApi`), and starts the modules where the snow's chunk records were always registered,
after the settings and the payload types (`ModuleHost.startModules`): every class listed under the `almanac:module`
entrypoint in `fabric.mod.json` gets `AlmanacModule.initialize()`, on a client as on a dedicated server, and installs
its side. Then core registers its game events, which call the snow through the API from exactly where they called it
before, so the order of everything on a tick is unchanged. Starting fails, naming the module, if the snow module is
missing. Until core's side is installed the public API answers as it always did before a season was known: nothing.

**What the build refuses** (each tried by planting the import and building):

| From | To | Result |
|---|---|---|
| `core` (`src/main`) | `snow` | `package com.thenathe.almanac.snow does not exist` |
| `snow` | `core` | `package com.thenathe.almanac.season does not exist` |
| `api` | `core` | `package com.thenathe.almanac.climate does not exist` |
| `core` (`src/main`) | `core` (`src/client`) | `package com.thenathe.almanac.client does not exist` |
| `core` (`src/main`), `snow`, `api` | Minecraft's client classes | `package net.minecraft.client does not exist` |

Tests are not shipped and may look across: `core`'s tests see `snow` (the three that check the snow against core:
`FarSnowRuleTest` against the climate table, `SavedDataReadingTest` reading `season.dat` and `snow.dat` together,
`SnowLogTest` against the event reach). `SnowLayersTest`, for the public
`SnowLayers`, runs with the snow's tests.

## The internal API (`com.thenathe.almanac.internal`)

Static access through `Services`. Each piece is something today's code calls across a boundary; nothing was added
for later rounds.

| Piece | What it carries | Implemented by | Read by |
|---|---|---|---|
| `Services` | Where each side is found: `seasons()`, `snow()`, `weather()` (0.4.0); installed once as Almanac starts | — | everything below |
| `AlmanacModule` | A module's entrypoint (`almanac:module`) and the one mod id | `SnowModule`, `WeatherModule` | `ModuleHost` |
| `Seasons` | The season for the public API and the modules: the climate now for a level, the season's snow and ice (`snow`, a `SnowReading`; on a client the last sync followed forward to the level's game time; until 0.3.0 `snowDepth`, one depth as synced), the world's own rain and thunder, the client's climate map, the far-snow change tick, and the snow's settings | core's `modules.CoreSeasons`, over `ClimateHooks` | `AlmanacApi`, `snow` |
| `ClimateNow` | The overworld's climate now: the sub-season, the climates the rain event reaches, each biome's entry, what falls where | core's `SeasonalClimate` | `AlmanacApi`, `snow` |
| `BiomeClimate` | One biome's seasons from the climate table: base and seasonal climate, the ice and jungle flags, can anything fall, the season's temperature, cold enough to snow, what falls; vanilla's snow temperature | core's `ClimateTable.Entry` | `AlmanacApi`, `SeasonClimate`, `snow` |
| `SnowSettings` | The eight settings the snow reads, as they are when asked: catch-up on and its budget, the loaded chunks' budget, native snow melting, and from 0.3.0 the snowfall, thaw and ice clocks and the most layers (`snowfallTicks`, `thawTicks`, `iceTicks`, `maxLayers`) | `CoreSeasons`, from `ServerConfig` | `snow` |
| `SeasonTick` | What the snow follows each server tick: the climate now, whether it is raining, which weather decides the rain (`weatherVersion`, 0.4.0: 0 for vanilla's, else the storm maths), and the fingerprints of the climate settings and the no-snow biomes that every chunk record saves (with their two fixed values) | built by `ServerSeasons.tick` | `snow` |
| `Snow` | The snow module's side: its lifecycle (server starting, season started and ticked, each level's tick, chunks loading and unloading, server stopped), the two placement hooks (weather snow and ice, a player's block), whether vanilla's snowfall may add a layer to a column that has so many (`snowMayFall`; until 0.3.0 `snowHasReached`), the snow and ice now and how they move (`reading`; until 0.3.0 `depth`, per climate; from 0.4.0 `readingAt` a column and `depthsAt` on either side), the snow now, the queues for `/almanac snow`, the far rule, and from 0.4.0 the log for clients and a client's own snow by area | `SnowModule` | `Almanac`, `ServerSeasons`, `SeasonalClimate`, the two mixins, `SeasonCommands`, `ServerClimateMap`, `ClientClimateMap`, `ServerNetworking`, `AlmanacClient`, `AlmanacApi` |
| `SnowReading` (0.3.0) | The season's snow and ice in each climate at one moment: the depths, the ice covers, how fast each moves a tick, and the clocks and most layers (`SnowClocks`); `layers(climate)` gives the `SnowLayers`, and `after(ticks)` the same snow later, if the weather holds | built by the snow's `SeasonSnow` each tick; on a client by core's `SeasonSyncPayload` from the sync | `AlmanacApi`, `ServerSeasons` and `ServerNetworking` (the season sync), `SeasonCommands` |
| `SnowNow` | Where snow lies now: the layers begun per climate (any share at all; until 0.3.0 whole layers), each climate's layer shares (`shares`) and ice cover (`ice`), how deep the season's snow may build (`maxLayers`), native melting, the snow height and natural height | the snow's `SnowState` | `ServerClimateMap`, `SeasonCommands` |
| `FarSnowLines` | The far rule: the sub-season far terrain shows, a column's cold line, its far snow line, a line fitted to a level | `SnowModule`, over `FarSnowRule` | `ServerClimateMap`, `ClientClimateMap` |
| `FarClimateView` | The answers behind `AlmanacApi`'s far lookups (moved from `climatemap`) | `ClientClimateMap` | `AlmanacApi` |
| `WeatherKind` | What falls in a climate in a sub-season (moved from `climate`): the seasonal shift table | — | core's climate table and rain shares, the snow's weather log |
| `SavedFields` | Reading saved fields one by one (moved from `season`) | — | `season.dat` (core), `snow.dat` (snow) and `weather.dat` (weather) |
| `Weather` (0.4.0) | The weather module's side: its lifecycle, the view of the weather on either side, the weather over a column now (from the storm index or worked out), operators' events, the record for the sync, the client's record and ticks, the background thread | `WeatherModule` | `Almanac`, `ServerSeasons`, `LocalWeather` (and through it the weather mixins), `WeatherCommands`, `ServerNetworking`, `AlmanacClient`, `AlmanacApi`, `snow` |
| `WeatherView` (0.4.0) | The weather worked out from one record, immutable, the same on the server and every client: is it regional at a tick, weather ticks, the weather over a column, the storms near a place, the wind, and how long rain fell on a column through a stretch (`wetSpell`) | `WeatherField` | `snow` (`RainSource.regional`, `SnowNodes`, `ClientSnow`), `AlmanacApi`, `WeatherCommands` |
| `WeatherRecord`, `WeatherTick`, `WeatherHere`, `WetSpell` (0.4.0) | Plain data: the record (seed, eras, events, freezes) as saved and sent; what core hands the weather each tick (the sub-season, its shares, regional on, `advance_weather`); the weather over a column per climate; the ticks of rain per climate through a stretch | — | as above |

`AlmanacApi` and `SeasonClimate` answer through `Seasons`, `ClimateNow` and `BiomeClimate`. Every call 0.2.0 had keeps
its signature; 0.3.0 adds `snowLayers`, `iceCover` and `snowClocks`, all three read from `Seasons.snow`, and
`snowDepth` and `snowCover` answer from it too (INTEGRATION, "Added in 0.3.0").

### Where the weather comes in

Up to 0.3.0 core was the source of rain: `ServerSeasons.tick` took whether it was raining from vanilla's one weather
for the overworld, and which climates the event reached from the rain shares (`EventReach`), and handed both to the
snow in the `SeasonTick`. With regional weather off it still is.

**With regional weather (0.4.0)** the `weather` module is the source of rain:

- **Each tick** `ServerSeasons.tick` hands the weather module the sub-season, its rain shares, whether regional weather
  is on and whether `advance_weather` lets the weather move (`WeatherTick`); the module records a new era or freeze
  when one starts (`weather.dat`) and has its background thread build an index of the storms around the players every
  20 ticks. Then `ServerSeasons` hands the snow a `SeasonTick` with `raining` false (vanilla's weather is held clear,
  `ServerLevelMixin.almanac$holdClear`) and `weatherVersion` the storm maths.
- **The per-place answers** (is it raining here, thundering here) are core's `LocalWeather`: the storms over the
  column (`Services.weather().at`, from the index, or worked out directly where the index doesn't reach) with what the
  climate table lets fall there. Vanilla's checks that asked its one weather ask it instead, through the mixins in
  `mixin` and `mixin.weather`. On a client `ClientRain` asks it at the camera,
  and the weather mixins ask it per place, from the client's own index.
- **Clients** get the weather record (`almanac:weather`, `WeatherSyncPayload`) and the snow log (`almanac:snow_log`,
  `SnowLogPayload`) on joining and when either changes, never per tick, and work out the same storms and snow.
- **The snow** logs regional segments and works out each snow node from the log and the storms
  (`RainSource.regional`, below). It never asks vanilla's weather.

### Snow by area (0.3.0 seam, built in 0.4.0)

The seam built in 0.3.0 and used by regional weather in 0.4.0.

- **The amounts are kept by area.** A depth is worked out for a `SnowArea`: a climate as a whole (`SnowArea.of`), or
  under regional weather a snow node (`SnowArea.node`), one every 256 blocks. Before the switch-over every node of a
  climate has the climate's depth, since the log's older segments are vanilla's one weather.
- **The rain comes through a `RainSource`:** how many ticks of a stretch of a segment snow fell on an area.
  `RainSource.GLOBAL` is vanilla's one rain with the climates the event reached; `RainSource.regional(view)` answers
  regional segments of a node from the weather module's `wetSpell` (the whole ticks a storm core reaching the node's
  climate was over it) and every other segment as `GLOBAL`. The log's arithmetic (`SnowLog.trendFrom`, `advance`), the
  layer rule (`SnowLayers`), the per-column rule and the rounds of loaded land are the same for both.
- **The nodes near loaded land** are worked out on the background thread (`SnowNodes`): from scratch the first time
  (from the last time the climate's snow was surely all gone, `SnowLog.meltedBy`, or the checkpoint), then followed on
  from where they were. The server thread reads the newest `SnowField`: each node's depth at a tick, its pace and until
  when the pace holds. `SnowUpdates` asks for the four nodes around every loaded chunk; a chunk whose nodes aren't in
  yet waits (`waiting`) and is brought up when they are.
- **A chunk's depth** is its four nodes' weighed by nearness at the chunk's centre (`SnowField.chunkDepths`), and the
  chunk is brought to the world's state with those depths (`SnowState.withDepths`). Its record saves them under the
  climate's ID, as 0.3.0's did, so a 0.3.0 record reads unchanged and matches at the switch-over.
- **The ice cover doesn't depend on the rain**, so it stays one per climate, worked out under `GLOBAL`.
- **Folding.** Vanilla's segments fold into the per-climate checkpoint as before. Regional segments fold only through
  a melt that leaves no snow anywhere (`SnowLog.foldThroughMelt`); past 1,024 segments the oldest is folded as if
  global (`approximateFolds`), and the nodes are worked out again.
- **The public API** keeps its per-climate calls (`snowDepth`: the player's own on a client, the spawn's on the server)
  and gains `snowLayersAt` by place. A client works out far snow itself from the log (`ClientSnow`).

## What stayed in core

The plan had six modules. Three of them are not modules yet:

- **`farclimate`** (`climatemap` and its payloads, the client's climate map). Its server half reads the season, the
  snow's far state, four settings, the client's handshake (for an old LiteLights) and saves its change tick in
  `season.dat`; core calls it from start-up, the server tick, `/almanac snow` and the far lookups; its client half
  reads the client's season, its climate table and its colours, and is registered only when LiteLights allows. Fenced
  off, that is about ten more API pieces, half of them client colours and sync, for a part LiteLights' own far terrain
  renderer is about to reshape (its distance, the seed, "leave as captured"). It goes into a module with that work.
  It already reaches the snow only through the API (`FarSnowLines`, `SnowNow`).
- **`colour`** and **`client`**. The client reads core deeply: the synced climate table and `SeasonalClimate`, the
  payload records it decodes, the whole `ServerConfig` the settings screen edits. Behind an API they would carry the
  wire format and the settings model, not "interfaces and plain data". What a separate client module would have
  enforced, that the server never reaches client code, the environment split enforces instead, Minecraft's client
  classes included. `colour` is client only and lives in core's `src/client`.

Also left where they were, though they touch the snow: the two mixins that record weather and player snow
(`ServerLevelMixin`, `BlockItemMixin`) stay in core's mixin config and call `Snow`, since `ServerLevelMixin` is
mostly the climate hooks and moving half of it would need a mixin config of the snow's own. The snow round (0.3.0)
added no mixins, so the snow still has no mixin config of its own.

## Adding a module

1. A subproject: `include` it in `settings.gradle`, a `build.gradle` with `implementation project(':api')` and its own
   optional mod APIs (never `:core`), sources under `<name>/src/main/java` in packages of its own. It gets the
   environment split from the root build file.
2. In `core/build.gradle`: `evaluationDependsOn`, `runtimeOnly project(':<name>')` (and `testImplementation` if
   core's tests check it), `from(project(':<name>').sourceSets.main.output)` in `jar` and its sources in
   `sourcesJar`, and its source set in the dev-run mod in `loom { mods { ... } }`.
3. Its entrypoint, an `AlmanacModule`, under `almanac:module` in core's `fabric.mod.json`; it installs its side into
   `Services`. Mixins of its own go in a mixin config in its resources, listed under `mixins`.
4. What it needs from core goes into the internal API as interfaces and plain data, implemented in core's `modules`;
   nothing more than the module uses. Where core calls it, core calls it through the API from the same spot.
5. The module table above.
