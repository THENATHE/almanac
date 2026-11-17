# Changelog

## 0.1.2+26.3 (2026-11-17)

### Far terrain

- Far terrain (Distant Horizons, Voxy) can now follow the seasons, drawn by LiteLights on clients that have Almanac
  too. The server sends those players a coarse map of the surface biomes around them, worked out from the world
  generator on a background thread without loading or generating any chunk, nearest first within a byte budget per
  tick. Players without Almanac, or with an older one, are sent nothing new.
- Far land shows what it would if it loaded now: temperate land white only once it has snowed this winter, altitude
  snow always, cold land melting in summer with native melting on. Early spring keeps the snow the land had when it
  began, so far terrain clears at mid spring.
- The snow depths at the start of early spring are saved with the season data. Worlds from 0.1.0 and 0.1.1 work
  them out from the weather log when opened in early spring.
- New server settings on the Snow page, under "Far terrain": the climate map's off switch (`farClimateMap`), its
  reach (`farClimateRadius`, 4,096 blocks), its cell size (`farClimateCellSize`, 32 blocks) and the data sent to each
  player per tick (`farClimateBytesPerTick`, 4,096 bytes). `/almanac snow` shows the map's cost and what each player
  has been sent.

### Weather

- Where nothing can fall this sub-season (deserts and badlands outside winter, temperate land in mid summer), players
  now get the world's full rain and thunder levels instead of none. The sky darkens in a storm as in plain Minecraft,
  nothing is drawn falling, and mods such as LiteLights can raise a sandstorm or a haboob there. Where rain could
  fall but the event doesn't reach, the sky stays clear as before; lightning still strikes only where it rains.

### API

- New: `AlmanacApi.seasonalBiome(Level, Holder<Biome>)` and `climateOf(Level, Holder<Biome>)`, for a biome the caller
  already has (the position-based calls now use them); `worldRainLevel` and `worldThunderLevel`, the dimension's
  levels before each player's share; `thawTicks`, how long the loaded chunks' thaw takes now, in game ticks.
- New, for clients: `AlmanacApi.farSnowLine` and `fillFarSnowLines` (where far terrain shows snow, per column or for
  a grid), `seasonalGrassColour`, `seasonalFoliageColour` and `farClimate`, and `AlmanacEvents.FAR_CLIMATE_CHANGED`
  with the area and kind of change. Nothing from 0.1.1 changed. `docs/FAR-SNOW.md` is the full contract.

## 0.1.1+26.3 (2026-11-17)

### Snow

- Loaded chunks now thaw like real snow when it turns warm: patches open up and spread over the new **thaw time**
  setting (`thawDays`, 2 in-game days by default), instead of the whole area clearing in one sweep. A thaw never
  takes more than half the sub-season, so short seasons still finish in time, and it counts game ticks, so it speeds
  up with `/tick rate` and `/tick sprint`. Snowfall and freezing are unchanged.
- In early spring, chunks that load behind the season keep their snow and the season's ice, and thaw at the same
  pace from the moment they load. In every other sub-season, catch-up on load stays instant.
- `/almanac snow` also shows how many chunks are thawing.

### API

- New: `AlmanacApi.precipitationAt`, `seasonalBiome` and `originalBiome`; `SeasonalCopyId` for reading and writing
  copy IDs as plain strings; `AlmanacEvents.SEASON_CHANGED`, fired on the server and on clients when the sub-season,
  the rain reach or a snow depth changes. The `api` package and the copy ID format are a stable contract from this
  version on; `docs/INTEGRATION.md` explains them for other mods.

### Other

- A mod icon: a tree with one season in each quarter of its canopy.

## 0.1.0+26.3 (2026-11-17)

First release, for Minecraft 26.3 on Fabric.

### Seasons

- Twelve sub-seasons (early, mid and late spring, summer, fall and winter), 24 in-game days per season by default,
  kept in the world's save. The clock follows the overworld's days; setting the time back doesn't turn the year back.
- `/season`, `/season set|pause|resume|length` and `/almanac config [setting] [value]`, with sub-season announcements
  on the action bar.

### Weather

- Biomes are sorted into HOT, TEMPERATE and COLD climates from data (`#almanac:climate/*`, then the conventional tags,
  then temperature and precipitation). Winter moves each climate one step colder and summer one step warmer.
- Each rain event reaches a share of each climate, set per sub-season, so dry spells and wet spells differ by climate.
- Players without Almanac see the seasonal weather too: each chunk they receive uses a seasonal copy of its biome,
  made in memory and never saved.

### Snow

- Snow depth per climate from a saved weather log; snow builds while it snows, holds in the cold and melts in the warm.
- Each chunk records the snow and ice the season placed and the snow players placed. Season's snow and ice melt;
  player snow, snow blocks, deeper snow, snow on built blocks and mountain snow stay. Native snow in snowy biomes melts
  in summer and comes back in fall (optional).
- Chunks that load behind the season are brought up to date at once; loaded chunks change gradually, within per-tick
  budgets.

### Colours

- Seasonal grass, foliage and dry-foliage colours for every player (twelve steps a year without Almanac, day by day
  with it), from `assets/almanac/palette.json`, which resource packs can replace on clients with Almanac.
- On clients with Almanac, per-tree leaf palettes (birch clear yellow and dark oak deep red-brown in fall, spruce
  cooler in winter) and batched redraws, nearest first, on vanilla's renderer and Sodium alike.

### Settings

- One settings screen (Mod Menu or an unbound key): Seasons, Weather, Snow, Colours, On this computer and Help, in the
  House or Minecraft look. Operators and the singleplayer host change the server's settings; on the title screen the
  pages edit the file the next singleplayer world uses.
- "Days per season" has a box for typing an exact number (3 to 1200) beside its slider.

### API

- `com.thenathe.almanac.api.AlmanacApi`: the sub-season, the climate at a position (base, seasonal, rain reach), rain
  reach per climate and snow depth per climate, on servers and on clients with Almanac.
