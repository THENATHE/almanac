# Changelog

## 1.0.0+26.3 (2026-11-25)

The first public release since 0.1.3.2. It is 0.4.1 under a new number: the code, the protocol, the saved data and the
API are 0.4.1's. In short, for anyone coming from 0.1.3.2 (the entries below have the details, release by release):

- **Almanac is now needed on every player's game, not only on the server.** A server running Almanac refuses a player
  without it while they connect, with a message saying which Almanac to install, and refuses an Almanac that talks
  differently the same way. Vanilla clients can no longer join, and neither can guests of a single-player world opened
  to LAN who don't have it. The seasonal biome copies vanilla clients used are gone, and with them the copy names
  (`almanac:mid_winter/minecraft/plains`) on the F3 screen and the stutter at each season change.
- **The climate table.** Each biome's climate in each sub-season comes from a small table the server sends to every
  player, in place of the 684 biome copies. It answers what the copies did: where rain or snow falls, where it is cold
  enough to snow, where water freezes. Changes to the climate settings and tags apply at once. Jungles stay temperate
  but never get snow or ice; rain falls there in winter (`#almanac:no_seasonal_snow`).
- **Snow on two clocks, in layers.** While it snows, the snow builds one layer per snowfall clock (`snowfallTicks`,
  12,000 ticks by default); while it is warm, it melts one layer per thaw clock (`thawTicks`, 18,000). It lies up to
  three layers deep by default (`maxSnowLayers`, 1 to 8), Almanac's own setting in place of the
  `max_snow_accumulation_height` game rule. It comes on scattered columns, the way vanilla's snowfall lays it, and the
  top layer melts first. Loaded land, land that loads later and far terrain hold the same snow, and in the cold nothing
  is taken away.
- **Ice without streaks.** Ponds in loaded land froze in rows running east. Now ponds freeze from the shore inward in
  ragged rings, by an ice cover that grows while the climate is cold (`iceTicks`; 0 follows the snowfall clock), and
  thaw from the middle back to the shore, the same in loaded land and land that loads later.
- **Regional weather.** Storms are born, grow, drift with the wind for a few hours to two days of game time and die
  away, so it rains or snows in one place and not in another, in place of vanilla's one weather for the whole world.
  Each place is under rain about as often as in vanilla, and under thunder only beneath a thunderstorm. Summer storms
  are small, quick and often thunder; winter's are large, slow snowstorms. The storms are worked out from a weather seed
  and the time, the same on the server and every player's game. Everything the weather touches goes by the storm where
  it is: rain on mobs, farmland, fire, fishing, cauldrons, snow and ice forming, lightning, monsters in a thunderstorm's
  darkness, foxes, pandas, bees, sleeping in a daytime thunderstorm. The snow lies where the storms went. On by default;
  `regionalWeather` off gives one weather for the whole world, as before.
- **Weather commands.** `/weather rain` and `/weather thunder` place a storm over whoever runs them, full grown at once,
  and `/weather clear` clears the sky 2,500 blocks around them; `weatherCommandEverywhere` (or
  `/almanac weather everywhere`) makes them act on the whole world as vanilla's do, and `advance_weather` off stands
  every storm still. `/almanac weather` shows the weather where you stand, `storms [radius]` the storms near you,
  `forecast` when it next rains or snows where you stand, and `status` the weather's record and cost.
- **For other mods.** New calls: `weatherAt`, `stormsNear`, `weatherTick`, `prevailingWind`, `snowLayersAt` and
  `regionalWeather` (with the types `Storm`, `Wind` and `WeatherAt`), so a mod can draw the storms and place them at any
  partial tick; `snowLayers`, `iceCover` and `snowClocks` (`SnowLayers`, `SnowClocks`); `seasonClimateOf` and
  `seasonClimateAt` (`SeasonClimate`); `snowCover` and `fillFarBareSnowLines` for far terrain. Removed: `thawTicks`,
  `formingTicks`, both `originalBiome` calls, `seasonalBiome(Level, BlockPos)` and `SeasonalCopyId`; and
  `seasonalBiome(Level, Holder)` always answers empty. With regional weather the world as a whole reads clear, so a mod
  that asks the world whether it is raining hears no; `weatherAt` answers for a place. `docs/INTEGRATION.md` lists
  every addition and removal with its release, and how to check the installed version.
- **LiteLights 1.1.0** is the LiteLights release that goes with Almanac 1.0.0.
- **Upgrading.** Replace the jar on the server and on every player's game. A world from 0.1.3.2 keeps its season and
  day, its snow, its frozen ponds, its chunk records and its settings. Snow that fell before is read at the pace it
  fell at. Regional weather starts the first time the world opens, which the log notes, and at that moment every
  place's snow is what the world had. The settings file keeps every value, gains the new settings at their defaults,
  and drops `thawDays`, `vanillaClientCopies` and `refreshChunksPerTick`, which no longer do anything; rain shares still
  at 0.1.3.2's defaults move to 100 once, so every storm reaches wherever the season lets anything fall. New saved data:
  `data/almanac/weather.dat`, the weather's record.

## 0.4.1+26.3 (2026-11-25, local)

- **Fixed: storms placed or cleared while the weather stands still.** With `advance_weather` off, a storm placed by
  `/weather rain` or `thunder` rained at once, but `AlmanacApi.stormsNear` (and `/almanac weather storms`) left it out
  until the weather moved again, and a sky cleared by `/weather clear` still listed the storms it had cleared. Both now
  list the storms as the weather at each place has them. Found by LiteLights 1.0.15, which draws the storms
  `stormsNear` lists: it drew no cloud over such a storm, and kept the rain it shows under it held back.
- Nothing else changed: the storms, the snow, the saved data and the API's calls are 0.4.0's. Worlds and settings from
  0.4.0 open unchanged.

## 0.4.0+26.3 (2026-11-25, local)

- **Regional weather**. Storms form, move across the land and pass, so it rains or
  snows in one place and not in another, in place of vanilla's one weather for the whole world. Each storm is an ellipse
  the size of a circle 300 to 1,800 blocks in radius (some long, thin fronts), born somewhere, growing, drifting with
  the wind for a few hours to two days of game time, and shrinking away; rain falls in the inner part of its cloud and a
  thunderstorm thunders in its heart. Storms are worked out from a weather seed and the time, never saved, the same on
  the server and every client, and nothing depends on where players are. On by default (`regionalWeather`); off, the
  weather is as in 0.3.0.
- **The amount of rain is vanilla's**: every place is under rain 15.8 % of the time on average, as
  vanilla's own timers give, and under thunder about 9 % of that. Measured exactly over a year at twelve places across
  the world: 13.6 % to 17.5 %, 15.5 % on average.
- **Seasons shape the storms, not the amount:** summer's are small, quick and often thunderstorms; winter's large, slow,
  long snowstorms that seldom thunder; spring and fall in between, with more long fronts.
- **Everything vanilla asks of its one weather goes by the storm at the place:** rain on entities, farmland, fire,
  fishing, snow and ice forming, cauldrons, lightning (only under a thunderstorm), monsters in a thunderstorm's
  darkness, foxes, pandas, bees and hives, lightning rods, Channeling's lightning, and sleeping in a daytime
  thunderstorm. The world as a whole reads clear, so another mod that asks "is it raining" of the world hears no;
  `AlmanacApi.weatherAt` has the answer per place. Sleeping through the night changes nothing about the storms.
- **The snow lies where the storms went.** Its depth is worked out for snow nodes every 256 blocks from the time storms
  snowed on them, on the two clocks and in layers as in 0.3.0, in loaded land, on load and for far terrain alike; a
  chunk takes its four nodes' depths, so the snow thins out over a few hundred blocks at a storm's edge.
- **On the client** the rain, snow and thunder at the camera come from the same storms, worked out from the weather
  record the server sends on joining and when it changes; nothing is sent each tick. Rain and snow are drawn only on
  columns under a storm, so a storm's edge is an edge of rain.
- **`/weather`** acts around whoever runs it: `rain` and `thunder` place a storm over them, full grown at once, and
  `clear` clears the sky 2,500 blocks around them. `weatherCommandEverywhere` (or `/almanac weather everywhere`) makes
  it act on the whole world as vanilla's does. `advance_weather` off stands every storm still.
- **`/almanac weather`** shows the weather where you stand; `storms [radius]` the storms near you, how big, which way
  and how far through their lives; `forecast` when it next rains or snows where you stand; `status` the weather's record
  and its cost.
- **Settings:** the Weather page has "Regional weather" and "/weather changes the whole world" at the top, as do
  `/almanac config` and `almanac-server.json`.
- **New API** for LiteLights and other mods: `regionalWeather`, `weatherAt`, `stormsNear` (each `Storm` with its birth,
  life, path, shape, strength, thunder and cloud top, so it can be placed at any partial tick), `weatherTick`,
  `prevailingWind`, `snowLayersAt` (snow by place, for far terrain; a client works it out itself from the weather log),
  and the types `Storm`, `Wind` and `WeatherAt`. With regional weather on, `worldRainLevel` and `worldThunderLevel` on a
  client are the storm over the camera, and `snowDepth` on a client is the snow where the player stands, so LiteLights
  1.0.9 to 1.0.12 show the weather where the player stands with no change, Fancy clouds included.
- **Players need Almanac 0.4.0.** The protocol is 4: an older client can't follow regional weather.
- **Server cost:** the server thread only reads finished work; an index of the storms near the players and the snow of
  the land near them are worked out on one background thread. Measured on four cores with three players, Almanac took
  0.35 ms a tick idle, 0.71 ms travelling and 0.79 ms under a storm snowing on all three (0.3.0: 0.22, 0.45 and 0.59 in
  a snowstorm everywhere); each column's weather is answered once a tick.
- **Credits:** idea credits for the regional weather, in `CREDITS.md`, the README, Mod Menu and the settings screen:
  Create: Sky, Climate: Clouds, Storms, Technology and ProtoManly's Weather for regional, moving weather (with the
  particular ideas each gave), and Simple Clouds for clouds grouped where the weather is. No code, data or wording came
  from any of them.
- **Nothing is lost on upgrading.** A world from 0.3.0 or earlier keeps its season, snow, ponds, chunk records and
  settings. Regional weather starts the first time it opens (the switch-over, logged), and at that tick every place's
  snow is its climate's 0.3.0 depth exactly; the past stays vanilla's one weather. Settings files gain the two new
  settings at their defaults. New saved data: `data/almanac/weather.dat` (the weather's record), and one optional field
  on the weather log's newer segments in `snow.dat`.

## 0.3.0+26.3 (2026-11-25, local)

- **Snow builds and melts on two clocks**. While it snows, a climate's snow grows
  one layer per snowfall clock (`snowfallTicks`, 12,000 ticks by default), so three layers take three clocks ("3
  times as long, yes"); while it is warm it melts one layer per thaw clock (`thawTicks`, 18,000). Until 0.2.0 a layer
  took 4,096 ticks to build and 2,400 to melt, fixed. Loaded land, land that loads and far terrain all go at that pace.
- **Snow lies in layers, up to 3 by default.** How deep the season's snow may build is Almanac's own setting
  (`maxSnowLayers`, 1 to 8), no longer the `max_snow_accumulation_height` game rule. A layer has the ground to itself
  until it is 90 % down; then the next layer starts, on columns that already have snow ("every column can as long as
  it already has snow"), and the two share the snowfall until the lower one is whole. The thaw is the same backwards:
  the top layer goes first, and the one under it starts to go once the top is down to a tenth. Each layer covers a
  share of the open ground, the same everywhere in a climate, and a column holds a layer for every share past its
  point, so the same columns whiten first and deepen first, as in 0.1.5. At a depth of 1.0 the first layer covers 95 %
  of the ground and the second 5 %.
- **Ponds freeze from the shore inward by an ice cover,** the full fix for the streaky ice of 0.1.7. Each climate has
  an ice cover, 0 to 1, from the same weather log: it grows over the ice clock (`iceTicks`; 0, the default, follows
  the snowfall clock) while the climate is cold, whatever the weather, and falls over the thaw clock while it is warm.
  Each still-water column has a freezing point from its distance to the shore with a scatter on top, so the ice comes
  in overlapping rings with a ragged edge and no rows, and thaws from the middle back to the shore. Loaded land, land
  that loads part way through and land that loads after freeze alike; until 0.2.0 loaded ponds froze a ring a pass and
  ponds that loaded froze at once. Vanilla's own freezing now only freezes water the biome's own climate freezes.
- **In the cold nothing is taken away.** While a climate is cold the season's snow and ice only come; snow ahead of the
  shares (vanilla's, or an older version's) waits for the thaw. In a thaw, or where a spot is too warm, they go down to
  what the shares and the ice cover say. Layers the season adds above the natural height in a snowy biome are the
  season's, so they melt.
- **Vanilla's snowfall** adds a layer only where the column's share has a layer more for it, so land near players
  deepens no faster than the land around it.
- **Loaded chunks go straight to the season's snow,** the same rule as chunks that load, so a chunk is the same however
  it got there. They follow in rounds, which start when a share or an ice cover has moved 1/256 or reached an end, and
  only the columns a move passed are looked at. All of it comes from `snowColumnsPerTick` (128): a column that changes
  costs one, a column with nothing to change a sixteenth. The work for chunks waiting on the cover, which ran outside
  the budget since 0.1.5 (0.60 ms a tick in a snowstorm measured on 2026-11-24), is under it now. With three players
  in a snowstorm Almanac took 0.59 ms a tick in all, against 1.19 ms for 0.1.6.
- **Settings:** the Snow page has the four new settings under "Snowfall and thaw" (clocks 200 to 480,000 ticks, layers
  1 to 8), as do `/almanac config` and `almanac-server.json`. They apply at once, and a change doesn't rewrite the
  snow that has already fallen: the clocks are saved with each stretch of the weather log.
- **`/almanac snow`** shows each climate's depth, every layer's share and the ice cover, with the clocks and the most
  layers.
- **Fixed:** a chunk on its way out that a player came back to was asked for again every 20 ticks of game time, which
  stands still under `/tick freeze`, so it stayed behind until the game moved. It is asked for by the server's ticks
  now.
- **Players need Almanac 0.3.0.** The protocol is 3, so a 0.2.0 client is refused while connecting with the usual
  message, and a 0.2.0 server refuses a 0.3.0 client.
- **New API:** `SnowLayers` (each layer's share of a depth, `layersAt` for a column's point), `SnowClocks`,
  `AlmanacApi.snowLayers`, `iceCover` and `snowClocks`. `snowDepth` can now be up to the most layers, and on a client
  it is followed forward from the last sync, so it moves smoothly instead of in sixteenths. `snowCover` is the first
  layer's share, which near a whole first layer is a little less than the depth (95 % at 1.0). No call was removed;
  `seasonalBiome` stays, answering empty, since LiteLights 1.0.7 and 1.0.8 call it.
- **LiteLights 1.0.9** works with this Almanac unchanged, and the notice beside an older one now names 1.0.9 as the
  first that works with the seasonal climate (it said 1.0.8).
- **Removed, for old clients only:** the `almanac:update_server_settings` payload (0.1.2 clients) and the far state's
  last field, always 0 since 0.1.7 (0.1.6 clients). Neither kind of client can join since 0.2.0.
- Nothing is lost on upgrading. Snow that fell before 0.3.0 is read at 0.2.0's paces, up to the snow height game rule
  as it was when 0.3.0 first opened the world (saved once in `snow.dat`), and a world whose rule was raised keeps its
  depth. Frozen ponds stay frozen, chunk records keep their whole layers, and settings files gain the four new
  settings at their defaults. A world opened with a whole first layer gets the start of a second layer on about 5 %
  of its columns at once, as the new layer rule gives it; nothing is taken away.

## 0.2.0+26.3 (2026-11-25, local)

- **Almanac is now needed on every player's game, not only on the server**. A server running 0.2.0 refuses a player without
  Almanac while they connect, with a message saying which Almanac to install; a player with an Almanac that doesn't
  work with the server's (0.1.7 and earlier, or a later one that talks differently) is refused the same way, and told
  which version is needed. Guests of a single-player world opened to LAN are refused too. Up to 0.1.7 players without
  Almanac could join and got the season from seasonal copies of the biomes; that is gone.
- **The season's climate comes from a small table** of every biome's climate per sub-season, on the server and sent to
  every client, in place of the 684 seasonal biome copies that used to carry it. It answers what the copies answered:
  where rain or snow falls, where it is cold enough to snow, where water freezes, the far snow line, the rain fog.
  Checked for every vanilla overworld biome and sub-season against what 0.1.7's copies gave.
- **Jungles stay temperate but never get snow or ice**. They keep rain in spring, summer and fall,
  and in winter get rain where their climate used to give snow. The biomes are the tag `#almanac:no_seasonal_snow`
  (`#minecraft:is_jungle` and `#c:is_jungle`), which datapacks can add to. Snow and ice the season laid in a jungle
  under 0.1.7 go the first time its chunk loads in 0.2.0.
- **The player's share of the rain** (none where the current rain event doesn't reach, all of it where nothing can
  fall, faded as you walk) is worked out by the client where the camera is, instead of being sent to each player
  through vanilla's weather events. It looks as before.
- **The snow line and the winter and summer temperatures apply at once,** like every other setting, instead of after a
  restart. So do the climate tags after a `/reload`.
- **No more biome refresh at each sub-season change,** and nothing touches chunk packets, the biome registry, tags or
  command arguments any more. With the copies went their quirks: copy names on the F3 screen and in other mods' biome
  lists, the stutter on vanilla clients at each season change, and the extra data when joining.
- **Removed:** the settings `vanillaClientCopies` and `refreshChunksPerTick` (and the Colours page rows for them);
  `AlmanacApi.originalBiome` (both forms), `AlmanacApi.seasonalBiome(Level, BlockPos)` and `SeasonalCopyId`; the
  `almanac:world_weather` channel. Settings files that still hold the two settings load as before and leave them out
  the next time they are saved.
- **New API:** `AlmanacApi.seasonClimateOf(level, biome)` and `seasonClimateAt(level, pos)`: a biome's climate this
  sub-season (its temperature, whether anything can fall, whether a position is cold enough to snow), in place of
  asking a seasonal copy. `seasonalBiome(level, biome)` is kept for now, always answering empty, because LiteLights up
  to 1.0.7 calls it; it goes once LiteLights has moved to `seasonClimateOf`.
- **Beside LiteLights 1.0.7 or older,** which still asks for seasonal copies, LiteLights' weather looks (how cold or
  dry a place is) go by each biome's own climate, and the chat and log say so; rain and snow still follow the season,
  and far terrain is shared as before with 1.0.7. With LiteLights before 1.0.7 far terrain's seasonal snow stays off
  too, and `/almanac snow` now says that the player's LiteLights is the reason (it said "no Almanac 0.1.2 on the
  client").
- Nothing is lost on upgrading: worlds from 0.1.7 and earlier keep their season, snow depths, chunk records and
  settings, and chunks are only gone over where the season's snow lies in a jungle.

## 0.1.7+26.3 (2026-11-25, local)

- **Ice no longer forms in streaks in loaded chunks**. Water there froze from the shore inward,
  but each column froze as soon as the chunk's pass came to it, and the next column then counted the new ice as
  shore; the columns come in an order where the east neighbour is nearly always next, so ice ran east along rows
  with open water between. Now a pass freezes nothing until it has looked at the whole chunk, so the shore is the
  water as it found it, and then only about a quarter of the shore, chosen at random each time, so the ice edge is
  ragged and grows inward unevenly, as vanilla's freezing does. A pond ends as fully frozen as one in a chunk that
  loads, which still freezes at once. Ice in loaded land takes more passes than before (about 20 for a 14 by 14
  pond instead of 2).
- **Removed, unused since 0.1.6**:
  - the `thawDays` setting (its row on the Snow page, `/almanac config thawDays`, the field in
    `almanac-server.json`);
  - `AlmanacApi.thawTicks` and `AlmanacApi.formingTicks`, the `almanac:snow_forming` channel, and the thaw time at
    the end of the far-terrain state (still written as 0, so clients up to 0.1.6 keep reading it);
  - the snow depths saved at the start of early spring (`thaw_start_snow` in `data/almanac/season.dat`) and the thaw
    windows saved in chunk records by 0.1.3 to 0.1.5;
  - the forming and thaw lines of `/almanac snow`.
  Nothing is lost on upgrading: settings files, `season.dat` and chunk records that still hold these load as before,
  and the fields are left out the next time each is saved.
- **The API may lose calls.** `docs/INTEGRATION.md` no longer promises that the `api` package only gains calls; it says what other mods can rely on instead, and lists every removed call with the release it went in.
- **Far terrain beside an older LiteLights.** LiteLights before 1.0.7 times far snow by the two removed calls. With
  one installed, Almanac's client no longer takes the far-terrain climate map, so far terrain keeps the snow it was
  captured with instead of the game stopping on a missing call, and the log and the chat say which LiteLights is
  needed. LiteLights 1.0.7 works as with 0.1.6.

## 0.1.6+26.3 (2026-11-20, local)

- **The thaw goes by the stored snow cover, like the snowfall.** A climate's snow depth melts while it is warm for
  that climate (a layer in 2,400 ticks, as before), and the cover is the first layer of that depth. A column now
  keeps the season's snow, and the season's ice, for as long as the cover reaches it, and loses them when the cover
  falls past its point: column by column, scattered, the last columns to whiten going first. That one saved value
  decides it everywhere:
  - loaded chunks, inside simulation distance or not, thaw together;
  - a chunk that loads part way through the thaw is brought to what has thawed by then: it loses the snow the cover
    no longer reaches and no more, and a chunk generated then gets the snow the land around it still has;
  - far terrain shows the same share (below).
  Before, each loaded chunk ran a thaw of its own over `thawDays`, in spreading patches, starting when the sub-season
  changed or when the chunk loaded, and chunks that loaded outside early spring lost all their snow at once. So one
  chunk cleared instantly beside another still white, and none kept pace with far terrain.
- `thawDays` no longer paces anything in Almanac. It is still saved, shown and sent to clients, for far-terrain mods
  that time a thaw of their own (LiteLights before 1.0.7). Thaws saved in chunk records by older versions are ignored
  and cleared as each chunk is next brought up to date; no snow is lost by that.
- Snow that should go for another reason (a spot too warm in a climate that is still cold, fewer layers than before)
  has no thaw to wait for and goes as the chunk is next looked at.
- **Far terrain:** while a climate's snow melts, far terrain keeps the snow lines of the last sub-season that
  climate was cold in, instead of holding late winter's snow whole through early spring and switching at mid spring.
  New API call `AlmanacApi.fillFarBareSnowLines`: the lines of the snow that lies whatever the cover. Between a
  column's bare line and its far snow line lies the season's snow, on the share of columns `snowCover` gives, while
  it forms and while it melts. LiteLights 1.0.7 draws far snow that way and animates nothing itself.

## 0.1.5+26.3 (2026-11-19, local)

- **Snow comes on scattered columns, the way vanilla's snowfall lays it,** instead of in spreading patches. A column's point in a snowfall is now a random value of its own, evenly spread, so a cover of 0.4 is
  snow on 40 % of the open ground. The thaw still goes in patches.
- **Land near players no longer whitens ahead of the cover.** Vanilla's snowfall only lays the season's snow on
  columns the cover has reached, so land inside simulation distance, the loaded land around it, chunks that load and
  far terrain all hold the same share. Before, vanilla's snowfall added to the cover near players (0.51 against a
  cover of 0.31 in 0.1.4's check). Biomes that are cold all year are snowed on as in vanilla.
- **Loaded land keeps up with the cover at any view distance.** A chunk waiting on the snowfall is now looked at
  only where the snow has reached since its last look, and that work isn't held to `snowColumnsPerTick`. In 0.1.4
  the loaded chunks vanilla doesn't snow on fell further behind the more of them there were (at view distance 12
  they gained snow at about half the cover's pace), so they looked unsnowed beside chunks that had just loaded.
- The last columns of a snowfall are no longer left bare: chunks waiting on the cover are woken when it reaches all
  of a climate's ground, however small that last step is.
- A queued chunk that can't be read when its turn comes (on its way in or out) is asked for again until it unloads,
  instead of being dropped. A chunk the game kept after all would otherwise never be looked at again.

## 0.1.4+26.3 (2026-11-19, local)

- **Every rain event now reaches wherever the season lets anything fall.** The default share of rain events is 100
  in every cell that has one; before, a share of the game's weather did nothing in some climates and sub-seasons
  (60 % of early winter's snowfalls in TEMPERATE land, for example). Dry cells stay dry (deserts outside winter,
  TEMPERATE land in mid summer). The shares are still settings. A settings file from an earlier version has each
  cell that is still at its old default moved to 100 once, when it loads; cells set to anything else are kept.
- **Snow now forms by one stored cover per climate.** How much of a climate's ground the snow has reached is the
  first layer of its snow depth, 0 to 1, which comes from the saved weather log and grows a layer per 4,096 ticks
  of snowfall. Every column has its own point between 0 and 1 (the thaw's patch points) and holds snow once the
  cover has passed it. The same rule now applies everywhere:
  - land that stays loaded gains its snow in spreading patches as the cover grows, from a snowfall's first flake
    (before, nothing came until half a layer had fallen, then it ran on a clock of its own);
  - a chunk that loads or is generated part way through a snowfall holds that share of its snow, in the same
    patches, instead of all of it at once;
  - a chunk left part way and come back to has the snow the snowfall has got to: none of it is missing.
  Snow that vanilla's own snowfall lays ahead of the cover stays. When the snowfall stops the cover stays where it
  is, and the next snowfall carries on from there.
- New API call `AlmanacApi.snowCover(Level, Climate)`: that cover, on the server and on clients. Far terrain should
  show a far snow line's snow on that share of its columns; LiteLights 1.0.4 does.
- Loaded chunks following a snowfall are looked over much faster (a column with nothing to change counts as a
  sixteenth of one against `snowColumnsPerTick`), so loaded land stays within a few percent of the cover.
- The thaw is unchanged: it still goes by the thaw time, in the same patches.

## 0.1.3.2+26.3 (2026-11-18)

- New snow in loaded chunks now forms as fast as vanilla's own snowfall whitens the ground: about 12,300 ticks at
  the default `randomTickSpeed` (half an in-game day), faster or slower with the game rule. 0.1.3 and 0.1.3.1 formed
  it over the thaw time (two in-game days by default), so loaded land beyond simulation distance, where vanilla
  doesn't snow, stayed patchy long after the land near players was white. The melt still takes the thaw time.
- New API call `AlmanacApi.formingTicks(Level)`: how long falling snow takes to whiten loaded land, in game ticks, on
  the server and on clients with Almanac 0.1.3.2 or later (the server sends it, since the game rule isn't synced).
  Far terrain should form its snow over this and melt it over `thawTicks`; 0.1.3's notes said both take
  `thawTicks`, which is only true of the melt. Earlier clients and servers keep working with this one both ways.
- `/almanac snow` shows how long new snow takes to form and a thaw takes.
- A chunk left part way through a thaw (unloaded, or still thawing when the season moved on) no longer counts as up
  to date when the same season and snow depth come round again, such as the same sub-season a year later; before, it
  kept its half-melted snow then instead of getting the season's snow.

## 0.1.3.1+26.3 (2026-11-18)

- `AlmanacApi.farSnowChangedTick` now moves only when what far terrain shows changes. 0.1.3 also moved it whenever a
  climate's snow depth changed by a whole layer, even though far snow only depends on whether a climate has any, so
  during a melt a player who rejoined resumed the far-terrain animation late and it finished late. A tick saved by
  0.1.3 is kept. Events, payloads and every other API answer are unchanged.

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
