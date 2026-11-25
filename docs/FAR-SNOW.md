# Almanac far-terrain snow

Status: built in Almanac 0.1.2+26.3, and changed since as the sections below say.

This file is the contract for mods that draw far terrain. `docs/INTEGRATION.md` has the short version for other mods.

## Cover (0.1.4)

From 0.1.4 a far snow line says snow from a snowfall's first flake (any depth counts as the first layer), and how much
of the ground the snow has reached is the climate's **cover**: `AlmanacApi.snowCover(level, climate)`, the first layer
of `snowDepth`, 0 to 1. Loaded chunks and chunks that load lay snow on the columns whose point the cover has passed
(scattered single columns from 0.1.5, as vanilla's snowfall lays them; the thaw's patches in 0.1.4; the client
doesn't know the seed, so it can't reproduce the points). Far terrain should show the
line's new snow on the same share of its columns, by any even spread of its own, and keep it there while the cover
stands still: it comes from the saved weather log, so it is the same after leaving and coming back. LiteLights 1.0.4
does this in place of timing a forming animation, and 1.0.6 scatters it the same way. From 0.1.5 vanilla's snowfall
keeps to the cover too, so the land near the player is no further along than the far land. From 0.3.0 the cover is
the first layer's share (`SnowLayers.cover`), which near a whole first layer is a little less than the depth
("Layers").

## Bare lines and the thaw (0.1.6)

From 0.1.6 the cover paces the thaw too. While a climate's snow melts (it is warm for it and its depth is falling),
far terrain keeps the lines of the last sub-season that climate was cold in, and `snowCover` falls from 1 to 0. Early
spring no longer holds late winter's snow whole ("Early spring and the saved depths" below describes 0.1.2 to 0.1.5).

`AlmanacApi.fillFarBareSnowLines` gives the **bare** lines: where snow lies today whatever the cover, as if no climate
had any depth. Draw far snow like this, with no animation:

- at and above a column's bare line: snow, always;
- from its far snow line up to its bare line: snow on the share of columns the cover says, by any even spread;
- below its far snow line: none.

Fill both again when `FAR_CLIMATE_CHANGED` reports new snow lines **or** a climate change (the bare lines are
today's, so they move with the sub-season). LiteLights 1.0.7 does this; with an older Almanac it keeps its timed
changes.

From 0.1.7 the client doesn't take the climate map at all beside a LiteLights older than 1.0.7 (`LiteLightsCheck`):
those time far snow by `thawTicks` and `formingTicks`, which 0.1.7 removed, so far snow there is left as captured,
and the log and the chat say which LiteLights is needed. See `docs/INTEGRATION.md`, "When the two mods' versions
don't match".

## Layers (0.3.0)

From 0.3.0 the season's snow builds in layers, up to `maxSnowLayers` (3 by default), and every kind of land shows the
same share of each layer: loaded chunks, chunks that load and far terrain. A climate's layers
are `AlmanacApi.snowLayers(level, climate)`: `share(layer)` for each layer, and `layersAt(point)` for a column whose
point (0 to 1) is that. Draw far snow like this:

- at and above a column's bare line: snow, always, as before;
- from its far snow line up to its bare line: `layersAt(point)` layers, by any even spread of points of your own
  (none where it gives 0);
- below its far snow line: none.

The lines don't change with depth: they say where the season's snow lies at all, the shares how deep. A layer starts
when the one under it is 90 % down and the thaw takes the top first, so a column's layers never jump, and a column
with a second layer always has a first, whatever spread it is drawn with. `cover()` is the first layer's share, what
`snowCover` gives.

On a client the shares are followed forward from the last season sync to the client's game time, so they move
smoothly between syncs; there is still nothing to animate. The far state (Network, below) carries how many layers
have begun in each climate, not the shares.

A far mod that reads only `snowDepth` and draws a depth between 0 and 1 as the cover (LiteLights 1.0.9) keeps
working, and shows far snow full a little early: at a depth of 1.0 the first layer is at 95 %, whole at 1.1.

## Snow by place (0.4.0)

With regional weather (from 0.4.0) the snow lies where storms went, so how deep it is differs from place to place:
each snow node (one every 256 blocks) has its own depth from the storms that crossed it, and a chunk takes its four
nodes' depths weighed by nearness. The far rule and the climate map are unchanged: the lines still say where snow can
lie in a column this sub-season, and the shares say how much of it does.

- **`snowLayersAt(level, x, z, climate)`** gives the shares at a place, for far terrain that wants the snow of each
  area. The client works it out itself, from the weather log the server sends and the storms, on its background
  thread: the first ask about a place answers empty and starts the work, and the place answers from a tick or so later.
  So ask per far tile and fill again when the answers arrive. It keeps about 11 km square of nodes worked out and lets
  go of places not asked about for 600 ticks.
- **`snowDepth` and `snowLayers` per climate** are, on a client, the snow where the player stands (each player's season
  sync carries their own). A far terrain renderer that reads only those (LiteLights 1.0.9 to 1.0.12) shows the player's
  own snow over all of the far land: right near the player and over land the same storms crossed, a little off
  elsewhere.
- **`/almanac snow`** and the climate map's "which climates have snow" (its far state) go by each climate's deepest snow
  on loaded land, so the far lines show wherever any loaded land has the season's snow.

## The problem

Distant Horizons and Voxy draw far terrain from block data they captured whenever each chunk was last loaded.
Almanac only changes snow in chunks that are loaded, so the far land keeps whatever snow it had when it was captured:
a winter capture stays white all summer, and a summer capture stays green all winter. Unloaded chunks can't be edited
cheaply, so the far land is corrected where it is drawn, in LiteLights' far-terrain pass. That pass already shades
every far-terrain pixel in screen space for both mods, with the world position from depth.

The client can't work out the climate out there on its own: it has no biomes for unloaded land and doesn't know the
seed. So the server sends a coarse climate map, and Almanac's client API turns it into a per-column answer to "should
the far land show snow here right now, and from what height?".

## Who gets it

- Far snow needs Almanac 0.1.2 or later on the client as well as a LiteLights version that draws it (LiteLights 1.1.0 is the one that goes with Almanac 1.0.0). In singleplayer both are on the same
  machine.
- A client with LiteLights but no Almanac gets nothing (from 0.2.0 it can't join an Almanac server at all). The
  server sends the map's payloads only to clients that registered them (Fabric's `canSend`); Almanac's client registers
  them unless a LiteLights older than 1.0.7 is installed beside it (0.1.7).
- Without Almanac on the client, LiteLights leaves far terrain as the far mod captured it.

## The far rule

Far terrain shows what a column would look like if its chunk loaded and was brought up to date right now, **except
in early spring, which holds the snow the land had when early spring began**. Loaded chunks keep
their snow in early spring and thaw while loaded, and chunks that load then keep theirs too, so far terrain switches
from snowy to clear at the start of mid spring. (That was 0.1.2 to 0.1.5. From 0.1.6 a thawing climate keeps the
lines of its last cold sub-season while the cover falls, "Bare lines and the thaw"; from 0.3.0 the shares fall layer
by layer, "Layers".)

The rule is in `snow/FarSnowRule.java`, beside `SnowRules`, and unit-tested in `FarSnowRuleTest`.

### The far snow line

Per column the answer is one number, the **far snow line**: the lowest block Y at which a snow layer lies. That is
the Y of the snow layer's own block, which is the Y of the **top face** of the ground block under it (a grass block
at Y 70 has its top face at world Y 71, where the snow layer sits). Snow lies at the line and everywhere above it.

So, for an upward-facing surface whose top face is at world height `faceY`:

```
line == FAR_SNOW_UNKNOWN    leave the pixel as captured
otherwise                   snow belongs there exactly when faceY >= line
```

`FAR_SNOW_EVERYWHERE` (`Integer.MIN_VALUE + 1`) and `FAR_SNOW_NOWHERE` (`Integer.MAX_VALUE`) satisfy that comparison
on their own, so only `FAR_SNOW_UNKNOWN` (`Integer.MIN_VALUE`) needs a check. Unknown means "leave as captured": no
tile there yet, no season, or a biome whose ground decides (below). Every other value is a block Y within
the level's height (a line at or below the bottom of the world becomes "everywhere", above the top "nowhere").

### How a column's line is worked out

1. **The shown state.** The current sub-season and the layers of snow begun in each climate (any share at all; until
   0.2.0 whole layers), from the same state chunk catch-up uses. While a climate that is warm now still has snow
   melting, the last sub-season it was cold in, with the layers there are now (from 0.1.6). Up to 0.1.5, in early
   spring (the sub-seasons in `SnowRules.THAW_TIME`), the sub-season before it (late winter) and the layers saved when
   early spring began (below). This is `FarSnowRule.shown`.
2. **Two cold lines**, both by vanilla's own arithmetic (`Biome#coldEnoughToSnow`): the temperature counts as it is
   up to 17 blocks above sea level, and above that it drops by 0.05 every 40 blocks, moved up or down by up to 8 blocks
   by vanilla's temperature noise at that column. Snow can fall below 0.15.
   - the **seasonal cold line**: of the column's biome in the shown sub-season, from the climate table (0.2.0; until
     0.1.7 of the sub-season's copy of the biome, which had the same temperature): 0.0 in winter for warmer biomes,
     the snow line temperature in COLD biomes' early and late summer, the summer temperature in COLD biomes' mid
     summer. Biomes in `#almanac:no_seasonal_snow` (jungles) have none: the season never makes them cold;
   - the **real cold line**: of the biome itself, which is where world generation put native snow.
   Biome temperature modifiers (frozen oceans' warmer patches) are included.
3. **The line.** Whether snow lies at a height comes from `SnowRules.snow` itself, applied to a column as world
   generation left it (one layer of native snow where the biome's own climate snows, no player's snow, and natural
   ground, except in the biomes of `#almanac:snow_surface`, whose ground is snow blocks). Snow lies above both lines, never below both, and between them depending on the case, so the line is one
   of the two. In practice:
   - **Altitude snow always stays**: the real cold line always counts.
   - **HOT**: only altitude snow. Winter makes HOT biomes rainy, never cold.
   - **TEMPERATE**: in winter, snow everywhere the season makes it cold, but only once it has snowed in that climate
     since winter began (a layer has begun in its climate); before that, only altitude snow.
   - **COLD**: native snow, except where the season makes it warm and the "Native snow melts in summer" setting is
     on: mid summer melts it below about 360 blocks (above any normal build height), and early and late summer split
     rain below the snow line setting (`snowLineAboveSea`, 48: rain up to 48 above sea level, snow from 49) from
     snow above. With the setting off, COLD land stays white.
   - **Biomes with snow-block ground are left as captured while their snow melts.** Loaded chunks only melt native
     snow on natural ground (`#almanac:natural_snow_ground`), and world generation makes part of the ground of some
     cold biomes out of snow blocks, so there the snow on the snow blocks stays all year while the snow on grass,
     dirt and leaves melts. One line can't say both, and far away the ground can't be seen, so in the biomes of
     `#almanac:snow_surface` (vanilla's frozen peaks, grove, ice spikes, jagged peaks and snowy slopes: the five whose
     surface rules place snow blocks) the line is `FAR_SNOW_UNKNOWN` whenever the two kinds of ground would differ
     (their summer melt), and far terrain stays as captured. The rest of the year both agree and the line is given.
     Data packs and mods add their own snow-surfaced biomes to the tag.
   - **Biomes without seasons** (not in the climate table: the Nether's and the End's) keep their own (real) snow.

### Early spring and the saved depths (0.1.2 to 0.1.5)

Up to 0.1.5 far terrain showed the snow early spring began with until mid spring. The snow depth of each climate was
saved with the season data (`data/almanac/season.dat`, field `thaw_start_snow`) at the tick early spring began,
including when `/season set` jumped into it, and far terrain used those depths, rounded to layers. 0.1.6 still saved
them but nothing read them; 0.1.7 no longer reads or writes them, and drops the field from `season.dat` the next time
the file is saved. The rest of this section describes 0.1.2 to 0.1.5.

- Worlds from 0.1.0 and 0.1.1 have no such field; nothing else in the file changes. If such a world is opened
  outside early spring, the field is written the next time early spring begins.
- If it is opened **in** early spring, the depths are worked out once from the weather log (`snow.dat`), which
  records every sub-season change: the depth at the start of the current early spring. If the log no longer reaches
  back that far, the depths now are used (they can only have melted since). Either way the result is saved, so it
  isn't worked out again.
- A new world starts in early spring by default, before any snow; its saved depths are 0.

### What the rule doesn't model

- Snow players placed, snow on built blocks, and deeper snow from other mods: the far land shows only the season's
  and native snow.
- In summer with native melting turned **off**, a COLD column whose native snow melted earlier (while its chunk was
  loaded) shows as snowy far away; the rule can't know a column's history.
- A biome within 0.01 of 0.15 can have a gap of up to 8 blocks just above the cooling height where the noise warms it
  again; those blocks count as snowy.
- One biome per cell (see the map below): columns near a biome border can take the neighbouring cell's answer.
- Seasonal ice. The far rule gives snow lines only; frozen lakes and rivers far away are left as captured. Oceans never
  get seasonal ice (0.1.3), so far oceans and near ones agree. From 0.3.0 `AlmanacApi.iceCover` says how far still
  water in each climate has frozen over, shore first, but the far rule has no shores, so it gives no ice lines.
## The climate map (server)

`climatemap/ServerClimateMap.java`, `climatemap/TileSampler.java`.

- **Tiles** are 512 x 512 blocks, aligned to multiples of 512. Each holds one surface biome per **cell**, sampled at
  the cell's centre. Cells are 32 blocks by default (16, 32, 64 or 128).
- **Who gets it**: every player whose client registered the payloads, while they are in the overworld. Tiles within
  the radius (4,096 blocks by default) of the middle of the player's tile go out nearest first. Tiles more than one
  tile beyond the radius are dropped (the client is told to forget them).
- **Sampling** comes from the overworld's generator and biome source; no chunk is loaded or generated, and nothing is
  saved (biome sources are deterministic, so tiles live in memory). One background thread ("Almanac climate map",
  lowest priority) samples a tile at a time, at most 4 queued; biome sources are built for world generation's worker
  threads, so this is safe.
  - For multi-noise biome sources (vanilla's overworld, and data packs and mods built on it) the surface biome comes
    from one climate sample with the depth parameter set to the surface's (0): biome sources put surface biomes at
    depth 0 and cave biomes below. From 0.1.3 the sample goes through the source's own `createResolver`, the entry
    point world generation and the game's biome lookups use, so biome libraries that hook it (or the parameter list
    behind it) show on the map; for vanilla the answer is the same as before. This is unverified against TerraBlender
    and Biolith for 26.3 (no 26.3 build of either was at hand); Biolith 3.5 for 1.21.11 hooks the parameter list and
    the per-position lookup, which in 26.3 is the resolver. If the resolver throws, the map falls back to looking the
    sample up in the parameter list, and logs it once.
  - For any other biome source, the generator's height estimate (`getBaseHeight`) and the biome there.
  - Cells inside chunks that are loaded when a tile is finished take the chunk's real biome at its surface.
- **Sharing** (0.1.3): each tick a different player's nearest missing tile is sampled first, then the next player's,
  round-robin, so after a restart nobody waits for everyone before them in the list. A tile that fails to sample is
  tried three times, then left out (unknown on the client), so the player's map can still finish; `/almanac snow`
  counts tiles given up on.
- **Server thread**: at most 4 finished tiles are packed per tick, and each player gets at most
  `farClimateBytesPerTick` of tiles per tick (4,096 bytes by default; the tile that crosses the budget still goes, and
  the next tick's budget is short by as much).
- **Memory**: packed tiles are kept while some player wants them, plus 512 spares; at the defaults a tile is about
  92 bytes packed (241 tiles in 22 KB), so the whole cache is well under a megabyte.
- **Turning it off** (`farClimateMap false`) tells every client to drop its tiles; their lookups answer "unknown".
  Changing the cell size tells every client to drop its tiles (0.1.3; before, old tiles beyond the new ones stayed until
  the player left the overworld), then samples and sends everything again, so far terrain refills once.

### Measurements (0.1.2)

From the test run on a dedicated server (seed `almanac`, view distance 6, one player):

- **Ways of finding the surface biome**, against the real surface biome of loaded chunks at 676 points in each of
  plains, windswept hills, snowy plains and a desert:

  | Way | Match | Cost a sample |
  |---|---|---|
  | Climate sample with depth 0 (chosen) | 100 % everywhere | 4.8 to 6.5 us |
  | Generator height estimate, then the biome there | 100 % everywhere | 450 to 530 us |
  | A fixed height (64, 103 or 163) | 100 % at these four places | 4.3 to 6.1 us |

  The height estimate is a hundred times the cost for the same answers. A fixed height costs the same as the climate
  sample, but under a mountain or over a deep valley it can land in a cave biome; the climate sample asks for the
  surface by definition, so it was chosen, with the height estimate kept for biome sources that aren't multi-noise.
- **Cell size** at the default radius (241 tiles), the share of columns whose biome on the map is the biome really
  there (2,481 known columns around a plains spawn), sampling time on the background thread and bytes sent:

  | Cell | Same biome | Sampling | Bytes |
  |---|---|---|---|
  | 16 | 91.3 % | 1,191 ms | 45,704 |
  | **32 (default)** | 87.6 % | 358 ms | 22,147 |
  | 64 | 74.9 % | 105 ms | 10,368 |

  16-block cells cost three times the sampling and twice the data for under four points more; 64 loses thirteen. 32
  is the default; 16 is cheap enough for servers that want it.
- **Joining** at the defaults: 241 tiles, 22,147 bytes, every tile there within 3.5 s of the join (the script polls
  once a second); 1.68 ms a tile, 6.6 us a cell on the background thread.
- **Moving**: a jump of 12,000 blocks into new land sent 241 tiles, 23,230 bytes, in 2 s; each step of one tile (512
  blocks) sent 17 tiles, 1.5 to 1.7 KB, within half a second.
- **Server thread** (packing finished tiles and sending): 0.05 to 0.13 ms a tick on average, the slowest tick 5.0 ms,
  8 ticks over 1 ms and none over 5 ms in 2,586 ticks of joining, changing cell sizes and moving. Whole-server tick
  times (`/tick query`) were set by chunk generation, not the map: about 8.5 ms average while the player joined.
- **Agreement with loaded chunks** after catch-up: 100 % of the columns whose map biome is
  the real one, in mid winter (plains, snowy plains, windswept hills, desert), mid spring (plains, windswept hills
  with altitude snow) and mid summer (snowy plains; a grove's columns unknown). Over all columns, 92.6 % to 100 %:
  the misses are columns near a biome border under a neighbouring cell (a desert's coast under a warm ocean cell,
  windswept forest under a taiga cell).

### Settings

All apply at once (no restart). On the settings screen they are on the Snow page under "Far terrain"; by command,
`/almanac config <name> <value>`.

| Setting | Default | Range | |
|---|---|---|---|
| `farClimateMap` | `true` | | The off switch. |
| `farClimateRadius` | 4096 | 512 to 16384 blocks | Match it to the far terrain's render distance. |
| `farClimateCellSize` | 32 | 16, 32, 64 or 128 blocks | Smaller follows biome borders more closely and costs more. |
| `farClimateBytesPerTick` | 4096 | 512 to 65536 bytes | Per player per tick. |

`/almanac snow` shows the map's state: tiles ready, sampling cost, server-thread time, and per player the tiles and
bytes sent (or that the player's client takes no map).

## Network

Three server-to-client payloads (`network/FarClimatePayloads.java`), each starting with a format number (1):

- `almanac:far_climate_state`: whether the map is on, the shown sub-season, the layers of snow per climate, whether
  native snow melts and the natural snow height. From 0.3.0 the layers are the number of layers begun (any share at
  all; until 0.2.0 whole layers). Up to 0.1.6 the state ended with the thaw time in loaded chunks (`thawTicks`); 0.1.7
  and 0.2.0 wrote it as 0 for clients up to 0.1.6, and 0.3.0 dropped it: those clients can't join since 0.2.0, and
  the change is part of protocol 3. Sent on joining and whenever it changes (a new sub-season, layers beginning or
  going, the setting, the map turned on or off); a few bytes.
- `almanac:far_climate_tile`: a tile's position, its cell size, its palette of biome network IDs (the registry IDs
  the server's and the client's biome registries share, as in chunk packets), and its cells as palette indices packed
  into as few bits as the palette needs and compressed with deflate. A tile of one biome is just its palette.
- `almanac:far_climate_forget`: tiles to drop, or all of them (on leaving the overworld).

A season change sends no tiles: the state payload says what far terrain shows, and the season sync still carries the
sub-season, the snow and the rain reach. From 0.3.0 the sync carries each climate's depth and ice cover with how fast
each is moving, the tick they were read at and the clocks, and the client follows them forward to its own game time
between syncs (`docs/INTEGRATION.md`, "Added in 0.3.0").

From 0.1.3 a fourth payload, `almanac:far_snow_changed`, carries `farSnowChangedTick`, just before the state that
changes it. From 0.1.3.2 to 0.1.6 a fifth, `almanac:snow_forming`, carried `formingTicks` on a channel of its own; 0.1.7
neither registers nor sends it, so a 0.1.7 server sends it to nobody and a 0.1.7 client isn't sent it.

## The client API

Everything is static on `com.thenathe.almanac.api.AlmanacApi` (`docs/INTEGRATION.md` says what other mods can rely
on; these were added in 0.1.2, `farSnowChangedTick` in 0.1.3, `fillFarBareSnowLines` in 0.1.6). They answer only on a
client with Almanac connected to an Almanac server, for its overworld `ClientLevel`, so fill with the overworld's
level. On the server (except `farSnowChangedTick`), in other dimensions and before anything has arrived, they say
unknown (or empty). `snowDepth`, `snowCover`, and from 0.3.0 `snowLayers` and `iceCover`, are in the main table of
`docs/INTEGRATION.md`.

| Method | Returns |
|---|---|
| `farSnowLine(Level, int x, int z)` | The far snow line: a block Y, `FAR_SNOW_EVERYWHERE`, `FAR_SNOW_NOWHERE` or `FAR_SNOW_UNKNOWN`. |
| `fillFarSnowLines(Level, int originX, int originZ, int spacing, int width, int height, int[] lines)` | Fills `lines[j * width + i]` with `farSnowLine(originX + i * spacing, originZ + j * spacing)`; returns how many are known. Throws `IllegalArgumentException` if `spacing < 1`, a size is negative or `lines` is shorter than `width * height`. |
| `seasonalGrassColour(Level, int x, int z)` | `OptionalInt`: today's seasonal grass colour (opaque ARGB, `0xFFrrggbb`) of the column's biome on the map, with the biome's grass modifier (swamp, dark forest) applied, as the client colours grass there. Empty where the map has no tile there or the client doesn't know the biome. It is given even where the line is unknown (snow-block biomes while they melt). |
| `seasonalFoliageColour(Level, int x, int z)` | `OptionalInt`: the same for foliage. |
| `farClimate(Level, int x, int z)` | `Optional<FarClimate>`: the column's biome on the map (`Holder<Biome>`, the real biome), its base climate, its climate now (one step colder in winter, warmer in summer) and whether the current rain event reaches it. Empty where the map has no tile there, for biomes the client doesn't know and for biomes without seasons; given where the line is unknown, as the colours are. |
| `fillFarBareSnowLines(...)` (0.1.6) | The same grid of bare lines: where snow lies whatever the cover ("Bare lines and the thaw"). |
| `farSnowChangedTick(Level)` (0.1.3) | `OptionalLong`: the overworld game tick (`Level#getGameTime`) at which what far terrain shows last changed everywhere; see "When far terrain last changed". |

`FAR_SNOW_UNKNOWN = Integer.MIN_VALUE`, `FAR_SNOW_EVERYWHERE = Integer.MIN_VALUE + 1`,
`FAR_SNOW_NOWHERE = Integer.MAX_VALUE`. Coordinates are block coordinates; a column's answer comes from the cell
holding it, plus that exact column's temperature noise (so the line is ragged by a few blocks, as vanilla's is).

**Threading**: every lookup is safe from any thread. Tiles and the state are published as values that never change
after publishing; no locks are taken.

**Cost** (measured on the client): `fillFarSnowLines` of 256 x 256 columns, 16 blocks apart, 6.5 ms (100 ns a column);
512 x 512 columns, 4 blocks apart, 12.5 ms (48 ns a column, more columns share a tile). A single `farSnowLine` is the
same work plus a map lookup. Fill only what changed, off the render thread if the budget is tight.

### When far terrain last changed

Since 0.1.6 the cover says how much of the season's snow lies, forming and melting, so far terrain that follows it
needs no animation and no lengths. Up to 0.1.6 the API also gave two lengths to animate with: `thawTicks`, the
`thawDays` setting in ticks, and from 0.1.3.2 `formingTicks`, the time vanilla's snowfall takes to whiten the ground
at the current `randomTickSpeed` (`ln(20) * 12288 / randomTickSpeed`, 12,271 ticks at the default). 0.1.7 removed both.

`AlmanacApi.farSnowChangedTick(Level)` (0.1.3, `OptionalLong`) is the overworld game tick at which what far terrain
shows last changed everywhere: a new shown sub-season, snow appearing or going in a climate, the native snow setting,
or the map turned on or off. It is saved with the season, so a server restart isn't a change. It comes from the server
in its own payload, sent on joining and just before the state that changes it, so it is already new inside the
`FAR_CLIMATE_CHANGED` listener for that change. Tiles arriving or being dropped don't change it, and since 0.1.3.1
neither does snow getting deeper or shallower in a climate that keeps some, since the far rule only asks whether a
climate has snow; that still fires the event, with the same far snow lines as before. Every LiteLights before 1.0.7
asks for it each frame until it answers, so it stays while those are about (`docs/INTEGRATION.md`).

### Knowing when to fill again

`AlmanacEvents.FAR_CLIMATE_CHANGED` gives `(Level level, FarChange change)`. `FarChange` has the area in block
coordinates, both ends included (`minX, minZ, maxX, maxZ`), and which kinds of answer may have changed:
`snowLines`, `colours`, `climate`. `isEverywhere()` is true for the whole map; `intersects(...)` tests a rectangle.

| When | Area | snowLines | colours | climate |
|---|---|---|---|---|
| A tile arrives | the tile (512 x 512) | yes | yes | yes |
| A tile is dropped (player moved away) | the tile | yes | yes | yes |
| All tiles dropped (left the overworld, or the cell size changed) | everywhere | yes | yes | yes |
| The shown state changes (new sub-season, snow appearing or going in a climate, the native snow setting) | everywhere | yes | no | no |
| The map is turned on or off | everywhere | yes | yes | yes |
| The day's colours change visibly | everywhere | no | yes | no |
| The sub-season or the rain reach changes | everywhere | no | no | yes |

- Always on the client thread, with the client's level, after the change is applied: lookups inside the listener
  already give the new answers.
- Never on the server; nothing fires on disconnecting (every answer becomes unknown then), so clear far-snow state
  on leaving a world.
- Joining fires one event per tile as the map arrives (241 at the defaults, over the first few seconds),
  so coalesce: mark the changed area and fill once per frame.
- **Telling a season change from map data arriving**: only the row "the shown state changes" (everywhere, `snowLines`
  alone) is the far land itself changing. Up to 0.1.5 that was the change to animate; since 0.1.6 fill again and let
  the cover say how much snow lies. Every other kind (tiles arriving or dropped, the map switched on or off, colours,
  climate) is the map filling in or a separate kind of answer, and should show at once.
- The level passed is the client's current one, which can be the Nether or the End, where every answer is unknown:
  look up with the overworld's level when the player is back.
- `AlmanacEvents.SEASON_CHANGED` still fires as before; far-terrain users need only `FAR_CLIMATE_CHANGED`.
