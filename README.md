# Almanac

Almanac is a seasons mod for Minecraft 26.3 (Fabric). The year has twelve sub-seasons, and the weather, snow, grass and leaves follow them. It runs on the server, and players don't need it installed to see any of it. If they do install it, they get colours that change a little every day and a settings screen.

![Almanac through the year](docs/preview.jpg)

*A forest in summer and fall, snow over the plains in winter, and rain in a winter desert.*

## What it does

- **Seasons:** early, mid and late spring, summer, fall and winter. Each season lasts 24 in-game days by default, and you can change that.
- **Weather by climate:** every biome is HOT (deserts, badlands), COLD (the snowy biomes) or TEMPERATE (everything else). Winter moves each one a step colder and summer a step warmer.
  - Deserts get rain in winter.
  - Plains and forests get snow in winter.
  - Snowy places get rain in summer.
  - Each storm reaches some climates and not others, and mid summer is dry.
- **Snow that comes and goes:**
  - Snow builds up in patches while it snows, and melts in patches over a couple of in-game days when it warms up. A thaw carries on where it was after a restart or Save and Quit.
  - Chunks you haven't visited in a while catch up the moment they load. In winter they arrive with the snow that's fallen while you were away; from mid spring to late fall they arrive clear.
  - Early spring is the thaw, so land you come back to then still has its snow and melts while you watch. Land next to a thaw already under way joins it.
  - Lakes and rivers freeze in winter, but open oceans stay open, so you can still cross them by boat.
  - Snow you place never melts, and neither does snow on your builds or high up on mountains, or ice under something you've built.
- **Colours:**
  - Grass and leaves change through the year. Each kind of tree has its own fall colours: oak goes orange-brown, birch yellow and dark oak red-brown.
  - Players without Almanac see the colours change twelve times a year. Players with it see them change a little every day.
- **Far terrain:** with Distant Horizons or Voxy, plus Almanac and a LiteLights version that supports it on your game, the far land follows the seasons too. LiteLights paints the snow on it from a map of the biomes around you that the server sends. No LiteLights release supports it yet.
- **Sandstorm-friendly:** in places where nothing can fall, a storm darkens the sky as in plain Minecraft, so mods like LiteLights can turn it into a sandstorm.

## Install

1. Use Minecraft 26.3 with Fabric Loader 0.19.5 or newer, Fabric API 0.165.2 or newer, and Java 25.
2. Drop the jar from the [latest release](../../releases/latest) into the server's `mods` folder. In singleplayer, put it in your game's `mods` folder.
3. Optional: players can add it to their own game for day-by-day colours and the settings screen. Mod Menu is optional too.

New worlds start in early spring. `/season` shows where you are in the year.

## Commands and settings

- `/season` shows the current sub-season and how many days are left. Anyone can use it.
- `/season set`, `/season pause`, `/season resume` and `/season length <days>` are for operators.
- `/almanac config` lists and changes every server setting, and `/almanac config reload` reads the settings file again after you've edited it by hand. `/almanac snow` shows the snow in each climate.
- With Almanac on your game, everything is on one settings screen (through Mod Menu, or a keybind you set). Operators and the singleplayer host can change the server's settings there; everyone else can see them but not change them.

Server settings are saved in `config/almanac-server.json`. If you edit it by hand and make a mistake, Almanac moves it aside as `almanac-server.json.broken` instead of overwriting it, and says so in the log.

## Compatibility

- Tested alongside Sodium, Lithium, C2ME, Krypton, FerriteCore, ScalableLux, Tectonic, Geophilic, Groundwork, LiteLights, Distant Horizons and Voxy.
- Fabric Seasons, Serene Seasons and Immersive Snow Reloaded do the same jobs as Almanac, so the game won't start with any of them installed alongside it.
- **Snow! Real Magic:** at its default settings there's no seasonal snow. Set `debug.forceVanillaIceSnowLogic: true` in `config/snowrealmagic-common.yaml` to get it back.
- Players without Almanac see the seasonal biome names (like `almanac:mid_winter/minecraft/plains`) on the F3 screen. That's how they get the seasonal weather and colours without the mod.
- Swamp grass keeps vanilla's swamp colours all year.
- Overworld only.

If something breaks, please open an issue with your log and mod list and I'll take a look.

## For mod authors

Almanac has a small API for mods that draw weather, place snow or colour terrain. It covers the season, the climate at a spot, what falls there, snow depths, the seasonal biomes, and far-terrain snow. It's in the `com.thenathe.almanac.api` package and documented in [docs/INTEGRATION.md](docs/INTEGRATION.md). It stays stable from 0.1.1 on: new versions only add to it, and the guide shows how to check which version is installed.

## Credits

Almanac is written from scratch, but the ideas behind it came from other seasons mods:

- **[Fabric Seasons](https://github.com/lucaargolo/fabric-seasons)** by lucaargolo: changing biomes in memory, and keeping track of the snow the weather places.
- **[Serene Seasons](https://github.com/Glitchfiend/SereneSeasons)** by Glitchfiend: sub-seasons and rain timing.
- **[Immersive Snow Reloaded](https://github.com/CerealCamera/Immersive-Snow-Reloaded)** by Straywave and contributors: bringing snow up to date when chunks load.

No code was copied from any of them. The full list is in [CREDITS.md](CREDITS.md).

## License

MIT, see [LICENSE](LICENSE).
