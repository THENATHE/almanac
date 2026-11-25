package com.thenathe.almanac.api;

import com.thenathe.almanac.internal.BiomeClimate;
import com.thenathe.almanac.internal.ClimateNow;
import com.thenathe.almanac.internal.Services;
import com.thenathe.almanac.internal.SnowReading;
import com.thenathe.almanac.internal.WeatherView;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * Almanac's public view of the season, the same on the server and on a client connected to an Almanac server.
 * Everything is empty outside the overworld, and on a client when the server has no Almanac. Changes are announced
 * by {@link AlmanacEvents#SEASON_CHANGED}; {@code docs/INTEGRATION.md} has examples.
 *
 * <p>The far-terrain lookups ({@link #farSnowLine} and the rest, from 0.1.2) are different: they answer only on a
 * client with Almanac, from the climate map the server sends it, and are documented in {@code docs/FAR-SNOW.md}.
 *
 * <p>Server calls belong on the server thread. On a client every call is safe from any thread (from 0.1.3; before
 * that, the season lookups were meant for the client thread).
 *
 * <p>This package is the only part of Almanac meant for other mods; anything outside it can change in any release.
 * Calls added later say since when. A release may also remove calls (0.1.7 removed {@code thawTicks} and
 * {@code formingTicks}, 0.2.0 {@code originalBiome}, the position form of {@code seasonalBiome} and
 * {@code SeasonalCopyId}); the changelog and {@code docs/INTEGRATION.md} name each one and the release it went in.
 * {@code docs/INTEGRATION.md} shows how to check the installed version.
 */
public final class AlmanacApi {
    /**
     * {@link #farSnowLine}: nothing is known about the column yet; leave far terrain there as it was captured.
     *
     * @since 0.1.2
     */
    public static final int FAR_SNOW_UNKNOWN = Integer.MIN_VALUE;
    /**
     * {@link #farSnowLine}: snow lies at every height in the column.
     *
     * @since 0.1.2
     */
    public static final int FAR_SNOW_EVERYWHERE = Integer.MIN_VALUE + 1;
    /**
     * {@link #farSnowLine}: snow lies at no height in the column.
     *
     * @since 0.1.2
     */
    public static final int FAR_SNOW_NOWHERE = Integer.MAX_VALUE;

    private AlmanacApi() {
    }

    public static Optional<SubSeason> subSeason(Level level) {
        return Optional.ofNullable(Services.seasons().climate(level)).map(ClimateNow::subSeason);
    }

    /** The climate at a position: the biome's base climate, the climate the season gives it now, and the rain reach. */
    public static Optional<ClimateAt> climateAt(Level level, BlockPos pos) {
        return climateOf(level, level.getBiome(pos));
    }

    /**
     * {@link #climateAt} for a biome the caller already has, for example from its own sampling grid, without looking
     * the biome up again. Empty for biomes without seasons.
     *
     * @since 0.1.2
     */
    public static Optional<ClimateAt> climateOf(Level level, Holder<Biome> biome) {
        ClimateNow climate = Services.seasons().climate(level);
        BiomeClimate entry = climate == null ? null : climate.entry(biome.value());
        if (entry == null) {
            return Optional.empty();
        }
        Climate base = entry.base();
        return Optional.of(new ClimateAt(base, entry.seasonal(climate.subSeason()), climate.reached().contains(base)));
    }

    /**
     * The season's climate at a position: how warm the biome there is this sub-season, whether anything can fall,
     * whether the current rain event reaches it and whether it is cold enough for snow at a given height. Empty for
     * biomes without seasons and where there is no season.
     *
     * @since 0.2.0
     */
    public static Optional<SeasonClimate> seasonClimateAt(Level level, BlockPos pos) {
        return seasonClimateOf(level, level.getBiome(pos));
    }

    /**
     * {@link #seasonClimateAt} for a biome the caller already has. It replaces {@code seasonalBiome}: where a mod
     * asked the sub-season's copy of a biome for its temperature ({@code getBaseTemperature}), whether anything can
     * fall ({@code hasPrecipitation}) or whether it is cold enough to snow ({@code coldEnoughToSnow}), it asks this
     * instead ({@link SeasonClimate#temperature}, {@link SeasonClimate#canFall}, {@link SeasonClimate#coldEnoughToSnow}).
     *
     * @since 0.2.0
     */
    public static Optional<SeasonClimate> seasonClimateOf(Level level, Holder<Biome> biome) {
        ClimateNow climate = Services.seasons().climate(level);
        BiomeClimate entry = climate == null ? null : climate.entry(biome.value());
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(new SeasonClimate(entry, climate.subSeason(), climate.reached().contains(entry.base())));
    }

    /**
     * What falls at a position during the current (or most recent) rain event this sub-season: the same answer as
     * the game's own {@link Level#precipitationAt}, which Almanac hooks, before that checks whether it is raining
     * and whether the sky is open there. {@code NONE} where the event doesn't reach the biome's climate.
     *
     * <p>Use this, or {@code Level#precipitationAt}, rather than {@link Biome#getPrecipitationAt}: a real biome only
     * knows its own climate, not the season's.
     */
    public static Optional<Biome.Precipitation> precipitationAt(Level level, BlockPos pos) {
        return precipitationOf(level, level.getBiome(pos), pos);
    }

    /**
     * {@link #precipitationAt} for a biome the caller already has, for example from its own sampling grid, so the
     * two can't drift apart: what falls at {@code pos} in that biome during the current rain event this sub-season,
     * {@code NONE} where the event doesn't reach its climate. The position still counts for the height (snow above
     * the snow line). Empty when there is no season here.
     *
     * @since 0.1.3
     */
    public static Optional<Biome.Precipitation> precipitationOf(Level level, Holder<Biome> biome, BlockPos pos) {
        ClimateNow climate = Services.seasons().climate(level);
        if (climate == null) {
            return Optional.empty();
        }
        return Optional.of(climate.precipitationAt(biome.value(), pos, level.getSeaLevel()));
    }

    /**
     * There only until LiteLights has moved off it: Almanac 0.2.0 and later have no seasonal biome copies, so this
     * always answers empty. LiteLights 1.0.7 and 1.0.8 call it and read an empty answer as Almanac having nothing to
     * say about the biome, so they keep running; without the method they would stop on a missing call. LiteLights 1.0.9
     * asks {@link #seasonClimateOf} instead. The position-based form and {@code originalBiome} went in 0.2.0.
     *
     * @deprecated replaced by {@link #seasonClimateOf}; goes once no LiteLights before 1.0.9 needs to be met
     * @since 0.1.2
     */
    @Deprecated(since = "0.2.0", forRemoval = true)
    public static Optional<Holder<Biome>> seasonalBiome(Level level, Holder<Biome> biome) {
        return Optional.empty();
    }

    /** Whether the current (or most recent) rain event reaches a climate this sub-season. */
    public static boolean rainReaches(Level level, Climate climate) {
        ClimateNow seasonal = Services.seasons().climate(level);
        return seasonal != null && seasonal.reached().contains(climate);
    }

    /**
     * The depth of snow in a climate, in snow layers: the sum of its layers' shares ({@link #snowLayers}), so the
     * average number of layers on open ground where the season makes it cold enough for snow. It is one value per
     * climate for the whole dimension under vanilla's one weather. Under regional weather (from 0.4.0) the snow
     * differs from place to place: on a client this is the snow where the player stands, and on the server the snow at
     * the world's spawn; {@link #snowLayersAt} gives any place. snow builds by one layer per snowfall clock while it
     * snows in that climate, holds while it's cold and melts by one layer per thaw clock while it's warm
     * ({@link #snowClocks}). Between 0 and the most layers (3 by default; until 0.3.0 the
     * {@code max_snow_accumulation_height} game rule).
     *
     * <p>On a client it is the server's value followed forward to the client's game time, so it moves smoothly (from
     * 0.3.0; before that, in sixteenths of a layer). A value between 0 and 0.9 is the first layer's share, as before.
     */
    public static OptionalDouble snowDepth(Level level, Climate climate) {
        SnowReading snow = Services.seasons().snow(level);
        return snow == null ? OptionalDouble.empty() : OptionalDouble.of(snow.depth(climate));
    }

    /**
     * How a climate's snow lies layer by layer: the share of the open ground each layer covers, with the most layers
     * snow may build to. Loaded chunks, chunks that load and far terrain all show these shares, so draw far snow, or
     * snow of your own, as {@link SnowLayers#layersAt} layers deep on a column whose point (your own scatter, 0 to 1)
     * is that. Far terrain shows the same shares between a column's far snow line and its bare line
     * ({@link #fillFarBareSnowLines}). On a client, followed forward to the client's game time like
     * {@link #snowDepth}. Empty where {@link #snowDepth} is.
     *
     * @since 0.3.0
     */
    public static Optional<SnowLayers> snowLayers(Level level, Climate climate) {
        SnowReading snow = Services.seasons().snow(level);
        return snow == null ? Optional.empty() : Optional.of(snow.layers(climate));
    }

    /**
     * How much of a climate's open ground the snow has reached, 0 to 1 (since 0.1.4): the first layer's share
     * ({@link SnowLayers#cover}). A column gains its snow once the share passes that column's own point between 0 and
     * 1, in loaded chunks, in chunks that load and on far terrain alike, and the share comes from the saved weather, so
     * it is the same after leaving an area and coming back. Until 0.3.0 this was {@link #snowDepth} up to 1; from
     * 0.3.0 the second layer starts once the first is 90 % down, so near a full first layer the two differ a little.
     * Empty where {@link #snowDepth} is.
     */
    public static OptionalDouble snowCover(Level level, Climate climate) {
        return snowLayers(level, climate).map(layers -> OptionalDouble.of(layers.cover())).orElse(OptionalDouble.empty());
    }

    /**
     * How far still water in a climate has frozen over, 0 to 1 (since 0.3.0): each pond's water freezes from the shore
     * inward as this passes each column's own freezing point, and thaws the same way back. It builds over the ice clock
     * while the climate is cold, whatever the weather, and goes over the thaw clock while it is warm. Only biomes that
     * get seasonal ice freeze by it (not oceans), and only where the season makes the water cold enough. Empty where
     * {@link #snowDepth} is.
     *
     * @since 0.3.0
     */
    public static OptionalDouble iceCover(Level level, Climate climate) {
        SnowReading snow = Services.seasons().snow(level);
        return snow == null ? OptionalDouble.empty() : OptionalDouble.of(snow.ice(climate));
    }

    /**
     * The snowfall, thaw and ice clocks and the most layers snow may build to, as they are in force now
     * ({@link SnowClocks}). Empty where {@link #snowDepth} is.
     *
     * @since 0.3.0
     */
    public static Optional<SnowClocks> snowClocks(Level level) {
        SnowReading snow = Services.seasons().snow(level);
        return snow == null ? Optional.empty() : Optional.of(snow.clocks());
    }

    /**
     * The game tick ({@link Level#getGameTime} of the overworld) at which what far terrain shows last changed
     * everywhere: a new shown sub-season, snow appearing or going in a climate, the native snow setting, or the climate
     * map turned on or off. {@link AlmanacEvents#FAR_CLIMATE_CHANGED} reports each of those everywhere with only
     * {@code snowLines} set; it also does when only the depth of snow changes (two layers to one), which doesn't move
     * the tick (since 0.1.3.1). It says how long what far terrain shows has stood, for example to resume an animation
     * of your own for a player who joins after the change. Since 0.1.6 the season's snow comes and goes by
     * {@link #snowCover}, so far terrain that follows the cover needs no animation. Tiles arriving or being dropped
     * don't change it, and a server restart doesn't either (it is saved with the season).
     *
     * <p>On the server, and on a client with Almanac 0.1.3 or later connected to an Almanac 0.1.3 server, where it
     * arrives before the change's event fires, so the listener reads the new tick. Empty elsewhere, outside the
     * overworld, and on older servers: start the animation from the moment of the event then.
     *
     * @since 0.1.3
     */
    public static OptionalLong farSnowChangedTick(Level level) {
        return Services.seasons().farSnowChangedTick(level);
    }

    /**
     * The dimension's own rain level, from 0 to 1, before the player's share of it. On a client
     * {@link Level#getRainLevel} is the player's share where they stand: 0 where the current event doesn't reach the
     * climate but rain or snow could fall, the full level where it reaches, and the full level again where nothing can
     * fall this sub-season (deserts and badlands outside winter), where the sky darkens with nothing falling. Use this
     * for how strong the weather is everywhere, for example a sandstorm's strength.
     *
     * <p>On the server it is the level's own. From 0.2.0 the client works the share out itself and the server sends
     * the world's levels as vanilla does; until 0.1.7 the server sent each player their share instead.
     *
     * @since 0.1.2
     */
    public static float worldRainLevel(Level level) {
        return Services.seasons().worldWeather(level, false);
    }

    /**
     * The dimension's own thunder level, from 0 to 1; see {@link #worldRainLevel}. A player's
     * {@link Level#getThunderLevel} on a client is this times their share.
     *
     * @since 0.1.2
     */
    public static float worldThunderLevel(Level level) {
        return Services.seasons().worldWeather(level, true);
    }

    /**
     * Whether Almanac's regional weather decides the weather in a level now (since 0.4.0): storms that form, move
     * across the land and pass, so it rains or snows in one place and not in another. False outside the overworld,
     * when a server has it off ({@code regionalWeather}), and on a client until the server has sent its weather.
     *
     * <p>While it runs, vanilla's one weather for the world is held clear: {@link Level#isRaining} and
     * {@link Level#getRainLevel} on the server read clear. Ask {@link #weatherAt} for a place instead. On a client
     * {@link Level#getRainLevel} and {@link Level#getThunderLevel} are the weather over the camera, as before.
     *
     * @since 0.4.0
     */
    public static boolean regionalWeather(Level level) {
        return Services.hasWeather() && Services.weather().active(level);
    }

    /**
     * The weather at a position now (since 0.4.0): how heavy the storm over it is, the rain (or snow) and thunder
     * levels there, and what falls on it. Under regional weather it is the storm over the place; otherwise vanilla's one
     * weather with the season's share where it is. The real answer to "is it raining here" for a mod that would ask the
     * world as a whole. On the server and on a client; empty outside the overworld and while no season is known.
     *
     * @since 0.4.0
     */
    public static Optional<WeatherAt> weatherAt(Level level, BlockPos pos) {
        return Optional.ofNullable(Services.seasons().weatherAt(level, pos));
    }

    /**
     * The storms whose clouds come within {@code radius} blocks of a column now, nearest first (since 0.4.0). Each is
     * fixed for its whole life, so place it at any moment, partial ticks included, from {@link #weatherTick}:
     * {@link Storm#centreX}, {@link Storm#size}, {@link Storm#covers}. The same storms on the server and every client.
     * A storm an operator placed, or a clearing asked for, while the weather stood still counts from when it was asked,
     * as at the place itself ({@link #weatherAt}; from 0.4.1, before which such a storm was left out until the weather
     * moved again). Empty without regional weather. Safe from any thread on a client.
     *
     * @since 0.4.0
     */
    public static List<Storm> stormsNear(Level level, double x, double z, double radius) {
        WeatherView view = regionalWeather(level) ? Services.weather().view(level) : null;
        long gameTick = level.getGameTime();
        return view == null ? List.of() : view.stormsNear(x, z, radius, view.weatherTick(gameTick), gameTick);
    }

    /**
     * The weather tick now, with a partial tick (since 0.4.0): the game time less any time the weather stood still
     * ({@code advance_weather} off). Storms live and move in weather ticks. While the weather stands still this stays
     * where it is. Empty without regional weather.
     *
     * @since 0.4.0
     */
    public static OptionalDouble weatherTick(Level level, float partialTick) {
        WeatherView view = regionalWeather(level) ? Services.weather().view(level) : null;
        return view == null ? OptionalDouble.empty() : OptionalDouble.of(view.weatherTick(level.getGameTime() + partialTick));
    }

    /**
     * The prevailing wind at a weather tick (since 0.4.0): the way storms are carried, a smooth function of the time
     * that wanders over days. For fair-weather clouds and particles that should drift with the storms. Empty without
     * regional weather.
     *
     * @since 0.4.0
     */
    public static Optional<Wind> prevailingWind(Level level, double weatherTick) {
        WeatherView view = regionalWeather(level) ? Services.weather().view(level) : null;
        return view == null ? Optional.empty() : Optional.of(view.wind(weatherTick));
    }

    /**
     * How a climate's snow lies layer by layer at a column (since 0.4.0): under regional weather the snow differs from
     * place to place, since it builds only where storms went, so far terrain and visual snow should ask by place. Under
     * vanilla's one weather it is {@link #snowLayers}. On a client it is worked out from the weather the server sent,
     * for any column, loaded or not: the first ask about a place starts the work and answers empty, and the place
     * answers from a tick or so later (ask again each frame or refill). On the server it answers for loaded land.
     *
     * @since 0.4.0
     */
    public static Optional<SnowLayers> snowLayersAt(Level level, double x, double z, Climate climate) {
        SnowReading snow = Services.seasons().snow(level);
        if (snow == null) {
            return Optional.empty();
        }
        if (!regionalWeather(level)) {
            return Optional.of(snow.layers(climate));
        }
        Map<Climate, Double> depths = Services.snow().depthsAt(level, x, z);
        return depths == null ? Optional.empty() : Optional.of(SnowLayers.of(depths.get(climate), snow.clocks().maxLayers()));
    }

    /**
     * Where far terrain (Distant Horizons, Voxy) should show snow in a column right now: the lowest block Y at which a
     * snow layer lies, which is the Y of the top face of the ground it lies on. Snow lies at that height and above, so
     * for an upward face at world height {@code faceY} the whole test is {@code faceY >= line}, once
     * {@link #FAR_SNOW_UNKNOWN} is ruled out; {@link #FAR_SNOW_EVERYWHERE} and {@link #FAR_SNOW_NOWHERE} need no
     * special case.
     *
     * <p>It is where the column's snow would lie if its chunk loaded now, with the cover full. While a climate's snow
     * is melting (from 0.1.6) the line stays where it was in the last sub-season the climate was cold, and
     * {@link #snowCover} says how much of the snow above it is left; before 0.1.6, early spring kept the line of the
     * start of early spring until mid spring. Built-up snow and snow players placed aren't modelled.
     *
     * <p>Only a client with Almanac connected to an Almanac server knows; it comes from the climate map the server
     * sends (one biome per cell, 32 blocks by default), so columns near a biome border can be a cell off. Unknown on
     * the server, outside the overworld, beyond the map's radius and until the column's tile arrives. Safe to call
     * from any thread; {@link AlmanacEvents#FAR_CLIMATE_CHANGED} says when answers change.
     *
     * @return a block Y, or {@link #FAR_SNOW_EVERYWHERE}, {@link #FAR_SNOW_NOWHERE} or {@link #FAR_SNOW_UNKNOWN}
     * @since 0.1.2
     */
    public static int farSnowLine(Level level, int x, int z) {
        return Services.seasons().farClimate().snowLine(level, x, z);
    }

    /**
     * {@link #farSnowLine} for a grid of columns, for filling a texture: column {@code (i, j)} is at
     * {@code originX + i * spacing, originZ + j * spacing} and its line goes to {@code lines[j * width + i]}.
     * Much cheaper than a call per column.
     *
     * @param spacing blocks between neighbouring columns, 1 or more
     * @return how many of the lines are known (not {@link #FAR_SNOW_UNKNOWN})
     * @throws IllegalArgumentException if the spacing or a size is out of range, or {@code lines} is too short
     * @since 0.1.2
     */
    public static int fillFarSnowLines(Level level, int originX, int originZ, int spacing, int width, int height,
                                       int[] lines) {
        if (spacing < 1 || width < 0 || height < 0 || (long) width * height > lines.length) {
            throw new IllegalArgumentException("spacing " + spacing + ", " + width + " x " + height + " into "
                    + lines.length + " lines");
        }
        return Services.seasons().farClimate().fillSnowLines(level, originX, originZ, spacing, width, height, lines);
    }

    /**
     * {@link #fillFarSnowLines} for the <em>bare</em> lines: where snow lies today whatever the snow cover, as if no
     * climate had any snow depth (snow the biomes have of their own, and height snow). Between a column's bare line
     * and its far snow line lies the season's snow, which is there on the share of columns {@link #snowCover} says:
     * draw snow at and above the bare line on every column, and from the far snow line up to the bare line on that
     * share. While a snowfall whitens the land and while the snow melts the lines stay put and only the cover
     * moves, so far terrain needs no animation of its own to match loaded land.
     *
     * @return how many of the lines are known (not {@link #FAR_SNOW_UNKNOWN})
     * @throws IllegalArgumentException if the spacing or a size is out of range, or {@code lines} is too short
     * @since 0.1.6
     */
    public static int fillFarBareSnowLines(Level level, int originX, int originZ, int spacing, int width, int height,
                                           int[] lines) {
        if (spacing < 1 || width < 0 || height < 0 || (long) width * height > lines.length) {
            throw new IllegalArgumentException("spacing " + spacing + ", " + width + " x " + height + " into "
                    + lines.length + " lines");
        }
        return Services.seasons().farClimate().fillBareSnowLines(level, originX, originZ, spacing, width, height, lines);
    }

    /**
     * The seasonal grass colour (opaque ARGB) of a column from the climate map, as a client with Almanac colours grass
     * there today: for far terrain whose captured snow should go, or to tint far grass to the season. Empty where the
     * climate map has no tile for the column or the client doesn't know its biome; given even where
     * {@link #farSnowLine} is unknown because the biome's ground decides (snow-block biomes while they melt).
     *
     * @since 0.1.2
     */
    public static OptionalInt seasonalGrassColour(Level level, int x, int z) {
        return Services.seasons().farClimate().grassColour(level, x, z);
    }

    /**
     * The seasonal foliage colour (opaque ARGB) of a column from the climate map; see {@link #seasonalGrassColour}.
     *
     * @since 0.1.2
     */
    public static OptionalInt seasonalFoliageColour(Level level, int x, int z) {
        return Services.seasons().farClimate().foliageColour(level, x, z);
    }

    /**
     * The biome of a column on the climate map and its climate now, for haze, sandstorms and debug views. Empty where
     * the climate map has no tile for the column, for biomes the client doesn't know and for biomes without seasons;
     * given where {@link #farSnowLine} is unknown because the ground decides, as the colours are.
     *
     * @since 0.1.2
     */
    public static Optional<FarClimate> farClimate(Level level, int x, int z) {
        return Services.seasons().farClimate().climate(level, x, z);
    }

    /**
     * A column of far terrain on the climate map.
     *
     * @param biome       the real biome of the cell holding the column
     * @param base        the biome's climate in spring and fall
     * @param seasonal    one step colder in winter and warmer in summer
     * @param rainReaches whether the current rain event reaches this climate
     * @since 0.1.2
     */
    public record FarClimate(Holder<Biome> biome, Climate base, Climate seasonal, boolean rainReaches) {
    }

    /**
     * @param base        the biome's climate in spring and fall
     * @param seasonal    one step colder in winter and warmer in summer
     * @param rainReaches whether the current rain event reaches this climate; when false nothing falls here
     */
    public record ClimateAt(Climate base, Climate seasonal, boolean rainReaches) {
    }
}
