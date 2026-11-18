# Changelog

## 0.1.3+26.3 (2026-11-17)

### Snow and ice

- A thaw under way now carries on after a server restart or Save and Quit instead of starting again, so thaws
  finish on time however short the sessions are. Each chunk with snow or ice waiting for its thaw keeps the thaw in
  its record.
- New snow in loaded chunks builds up in patches that start small and spread, over the same thaw time as the melt,
  instead of filling the land chunk by chunk. Chunks that load still get their snow at once.
- A chunk that loads in early spring next to land that is already thawing joins that thaw, so there are no
  chunk-shaped seams beside half-melted ground. Land far from anywhere loaded still keeps its snow until it thaws.
- Oceans no longer freeze over in winter, so boats can cross them; lakes and rivers still freeze, and frozen oceans
  behave as in plain Minecraft. Ice an older version put on an ocean melts at the next thaw. The biome tag
  `#almanac:no_seasonal_ice` (oceans by default) says where.
- Water ringed by kelp, seagrass, waterlogged blocks or a dock now freezes, and such water no longer keeps its chunk
  busy all winter.
- Ice under a block a player places stays frozen in spring, so an ice-fishing hut keeps its floor.
- With deeper snow (`max_snow_accumulation_height` above 1), the top of a column now loses one layer a step, not two.
- Changing the winter temperature, summer temperature or snow line settings now brings chunks up to date with the
  new climate after the restart.
- `/tick freeze` now holds the snow work in loaded chunks.

### Weather

- Storm darkness now fades in and out as in plain Minecraft. Players were sent a thunder level already scaled by the
  rain, which their game scaled again, so at half rain a storm was a quarter as dark as it should be and skies
  brightened early as storms ended.
- A rain starting or stopping in another dimension with weather no longer leaves overworld players with that
  dimension's rain level.

### Settings

- A settings file with a mistake in it is moved aside as `almanac-server.json.broken` (or `almanac-client.json.broken`)
  and the log says so, instead of being overwritten with the defaults at the next save.
- The server settings file is read again each time a world loads, so hand edits count from the next world, and
  `/almanac config reload` reads it on a running server.
- Saving the settings screen only changes the settings you changed, so it no longer undoes what another operator or
  the console changed while it was open. A player made operator, or no longer one, gets the right to change the
  settings at once, without reconnecting.
- Players who aren't operators see the server's settings on the settings screen, greyed out.

### Biomes and other mods

- `/reload` and data pack changes no longer take the seasonal biomes out of their tags, so mods that check biome tags
  keep recognising deserts, oceans and forests for players without Almanac. Changes to the climate tags still apply
  after a restart.
- The server warns at start if another mod changed the weather or colours of the seasonal biomes (Fabric's biome
  modification API selecting biomes by tag reaches them), and once if a mod sends one player's chunk packet to
  another who should get different biomes.

### Far terrain

- The climate map asks the world's biome source through the same entry point world generation uses, so biome mods
  that hook it can show on the far map. Vanilla worlds get the same map as before.
- Players share the map's sampling in turns, so after a restart nobody waits for everyone listed before them, and a tile
  that can't be sampled is tried again, then left out so the rest of the map can finish.
- Changing the map's cell size tells players to drop their old tiles, so none is left answering for its area.

### API

- New: `AlmanacApi.precipitationOf(Level, Holder<Biome>, BlockPos)`, what falls in a biome you already have, by the
  same rule as `precipitationAt`; `farSnowChangedTick(Level)`, the game tick at which what far terrain shows last
  changed, so a far-terrain melt can resume part way through for a player who joins mid-melt. It survives restarts.
- Client calls are now safe from any thread. Nothing from 0.1.2 changed; `docs/INTEGRATION.md` now shows how to check
  the installed version and lists Almanac's tags.

### Network

- Payloads now skip fields a newer Almanac adds at their end, so later versions can add to them without disconnecting
  0.1.3 clients. 0.1.2 and 0.1.3 clients and servers work with each other in either direction: new data travels in new
  payloads (`almanac:change_server_settings`, `almanac:far_snow_changed`), sent only to clients that take them.
- A server sending 0 days per season no longer crashes Almanac clients.

### Reliability

- A damaged field in `season.dat` or `snow.dat` now costs only that field (logged), instead of resetting the year
  and the snow history. A weather log copied from another world, ahead of its game time, is moved back.
- An error in one chunk's snow work is logged with the chunk's position and that chunk is left alone until the
  server restarts, instead of stopping the server.
- Chunks that unload while a mod is still loading them off the server thread no longer stay in the snow queues.

### Performance

- Columns that can't change (no snow and none due) skip the costly checks, chunks only recheck when the snow of a
  climate they contain changes, and chunks waiting for their thaw or snow forming sleep until their next patch is
  due instead of being scanned again and again. `/almanac snow` shows what the snow work has cost.
- Chunks rewritten for players without Almanac are shared by everyone sent them in the same tick, and the colour
  refresh after a sub-season change takes up to four players a tick in turn, so its cost no longer grows with the
  number of players (it takes longer on busy servers).

### Other

- New worlds no longer log that they have no saved snow.
- Logging a seasonal biome ID that isn't a valid one no longer throws.

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
