package com.thenathe.almanac.api;

import com.thenathe.almanac.biome.SeasonalBiomeIndex;
import com.thenathe.almanac.climate.ClimateHooks;
import com.thenathe.almanac.climate.SeasonalClimate;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
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
 * <p>This package is a stable contract from Almanac 0.1.1 on.
 */
public final class AlmanacApi {
    /** {@link #farSnowLine}: nothing is known about the column yet; leave far terrain there as it was captured. */
    public static final int FAR_SNOW_UNKNOWN = Integer.MIN_VALUE;
    /** {@link #farSnowLine}: snow lies at every height in the column. */
    public static final int FAR_SNOW_EVERYWHERE = Integer.MIN_VALUE + 1;
    /** {@link #farSnowLine}: snow lies at no height in the column. */
    public static final int FAR_SNOW_NOWHERE = Integer.MAX_VALUE;

    private AlmanacApi() {
    }

    public static Optional<SubSeason> subSeason(Level level) {
        return Optional.ofNullable(ClimateHooks.forLevel(level)).map(SeasonalClimate::subSeason);
    }

    /** The climate at a position: the biome's base climate, the climate the season gives it now, and the rain reach. */
    public static Optional<ClimateAt> climateAt(Level level, BlockPos pos) {
        return climateOf(level, level.getBiome(pos));
    }

    /**
     * {@link #climateAt} for a biome the caller already has, for example from its own sampling grid, without looking
     * the biome up again. A seasonal copy counts as the real biome behind it. Empty for biomes without seasons.
     */
    public static Optional<ClimateAt> climateOf(Level level, Holder<Biome> biome) {
        SeasonalClimate climate = ClimateHooks.forLevel(level);
        if (climate == null) {
            return Optional.empty();
        }
        SeasonalBiomeIndex.Entry entry = climate.entry(originalBiome(level, biome).value());
        if (entry == null) {
            return Optional.empty();
        }
        Climate base = entry.base();
        return Optional.of(new ClimateAt(base, SeasonalClimate.shifted(base, climate.subSeason()),
                climate.reached().contains(base)));
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
        SeasonalClimate climate = ClimateHooks.forLevel(level);
        if (climate == null) {
            return Optional.empty();
        }
        return Optional.of(climate.precipitationAt(level.getBiome(pos).value(), pos, level.getSeaLevel()));
    }

    /**
     * The biome a player without Almanac would get at a position: the sub-season's copy of the biome there, whose
     * temperature, precipitation and colours are already seasonal, or the biome itself if it has no copies. The
     * copies are in the level's own biome registry on both sides, so the holder can be used like any other.
     */
    public static Optional<Holder<Biome>> seasonalBiome(Level level, BlockPos pos) {
        return seasonalBiome(level, level.getBiome(pos));
    }

    /**
     * {@link #seasonalBiome(Level, BlockPos)} for a biome the caller already has: the current sub-season's copy of
     * it, or the biome itself if it has no copies. A seasonal copy is first turned back into its real biome, so any
     * copy gives the current sub-season's. Empty only when there is no season here.
     */
    public static Optional<Holder<Biome>> seasonalBiome(Level level, Holder<Biome> biome) {
        SeasonalClimate climate = ClimateHooks.forLevel(level);
        if (climate == null) {
            return Optional.empty();
        }
        Holder<Biome> original = originalBiome(level, biome);
        SeasonalBiomeIndex.Entry entry = climate.entry(original.value());
        return Optional.of(entry == null ? original : entry.copy(climate.subSeason()));
    }

    /**
     * The real biome behind a seasonal copy, looked up in the level's biome registry; any other biome is returned as
     * it is. Works whether or not a season is running, since copies keep their names.
     */
    public static Holder<Biome> originalBiome(Level level, Holder<Biome> biome) {
        Optional<ResourceKey<Biome>> key = biome.unwrapKey();
        if (key.isEmpty()) {
            return biome;
        }
        ResourceKey<Biome> original = originalBiome(key.get());
        if (original == key.get()) {
            return biome;
        }
        return level.registryAccess().lookupOrThrow(Registries.BIOME).get(original).<Holder<Biome>>map(holder -> holder)
                .orElse(biome);
    }

    /** The real biome's key behind a seasonal copy's key; any other key is returned as it is. */
    public static ResourceKey<Biome> originalBiome(ResourceKey<Biome> biome) {
        return SeasonalCopyId.parse(biome.identifier().toString())
                .map(copy -> ResourceKey.create(Registries.BIOME, Identifier.parse(copy.original())))
                .orElse(biome);
    }

    /** Whether the current (or most recent) rain event reaches a climate this sub-season. */
    public static boolean rainReaches(Level level, Climate climate) {
        SeasonalClimate seasonal = ClimateHooks.forLevel(level);
        return seasonal != null && seasonal.reached().contains(climate);
    }

    /**
     * The depth of snow in a climate, in snow layers between 0 and the {@code max_snow_accumulation_height} game rule.
     * It is one value per climate for the whole dimension, since weather is global: snow builds while it snows in
     * that climate, holds while it's cold and melts while it's warm. Where the season makes a spot cold enough for
     * snow, this much lies there (rounded to whole layers). On a client it is the server's value to a sixteenth of a
     * layer.
     */
    public static OptionalDouble snowDepth(Level level, Climate climate) {
        return ClimateHooks.snowDepth(level, climate);
    }

    /**
     * How long the season's snow and ice take to thaw in loaded chunks right now, in game ticks: the {@code thawDays}
     * setting, at most half the current sub-season. Read it when a transition starts, for example to make a melt or
     * snow-forming animation on far terrain last as long as the thaw near the player. It counts game ticks, so
     * {@code /tick rate} and {@code /tick sprint} speed it up with the thaw itself.
     *
     * <p>On the server, and on a client with Almanac 0.1.2 or later connected to an Almanac server (it comes with the
     * far-terrain state, on joining and whenever it changes). Empty when there is no season, as for the other calls.
     */
    public static OptionalLong thawTicks(Level level) {
        return ClimateHooks.thawTicks(level);
    }

    /**
     * The dimension's own rain level, from 0 to 1, before Almanac gives each player their share of it. A player's
     * {@link Level#getRainLevel} is their share: 0 where the current event doesn't reach the climate but rain or snow
     * could fall, the full level where it reaches, and the full level again where nothing can fall this sub-season
     * (deserts and badlands outside winter), where the sky darkens with nothing falling. Use this for how strong the
     * weather is everywhere, for example a sandstorm's strength.
     *
     * <p>On the server and on a client with Almanac 0.1.2 or later connected to an Almanac server; elsewhere it is the
     * level's own rain level, which is the same thing there.
     */
    public static float worldRainLevel(Level level) {
        return ClimateHooks.worldWeather(level, false);
    }

    /** The dimension's own thunder level, from 0 to 1; see {@link #worldRainLevel}. */
    public static float worldThunderLevel(Level level) {
        return ClimateHooks.worldWeather(level, true);
    }

    /**
     * Where far terrain (Distant Horizons, Voxy) should show snow in a column right now: the lowest block Y at which a
     * snow layer lies, which is the Y of the top face of the ground it lies on. Snow lies at that height and above, so
     * for an upward face at world height {@code faceY} the whole test is {@code faceY >= line}, once
     * {@link #FAR_SNOW_UNKNOWN} is ruled out; {@link #FAR_SNOW_EVERYWHERE} and {@link #FAR_SNOW_NOWHERE} need no
     * special case.
     *
     * <p>It is what the column would look like if its chunk loaded now, except in early spring, which keeps the snow
     * the land had when early spring began, so far terrain turns from snowy to clear at mid spring. Built-up snow and
     * snow players placed aren't modelled.
     *
     * <p>Only a client with Almanac connected to an Almanac server knows; it comes from the climate map the server
     * sends (one biome per cell, 32 blocks by default), so columns near a biome border can be a cell off. Unknown on
     * the server, outside the overworld, beyond the map's radius and until the column's tile arrives. Safe to call
     * from any thread; {@link AlmanacEvents#FAR_CLIMATE_CHANGED} says when answers change.
     *
     * @return a block Y, or {@link #FAR_SNOW_EVERYWHERE}, {@link #FAR_SNOW_NOWHERE} or {@link #FAR_SNOW_UNKNOWN}
     */
    public static int farSnowLine(Level level, int x, int z) {
        return ClimateHooks.farClimate().snowLine(level, x, z);
    }

    /**
     * {@link #farSnowLine} for a grid of columns, for filling a texture: column {@code (i, j)} is at
     * {@code originX + i * spacing, originZ + j * spacing} and its line goes to {@code lines[j * width + i]}.
     * Much cheaper than a call per column.
     *
     * @param spacing blocks between neighbouring columns, 1 or more
     * @return how many of the lines are known (not {@link #FAR_SNOW_UNKNOWN})
     * @throws IllegalArgumentException if the spacing or a size is out of range, or {@code lines} is too short
     */
    public static int fillFarSnowLines(Level level, int originX, int originZ, int spacing, int width, int height,
                                       int[] lines) {
        if (spacing < 1 || width < 0 || height < 0 || (long) width * height > lines.length) {
            throw new IllegalArgumentException("spacing " + spacing + ", " + width + " x " + height + " into "
                    + lines.length + " lines");
        }
        return ClimateHooks.farClimate().fillSnowLines(level, originX, originZ, spacing, width, height, lines);
    }

    /**
     * The seasonal grass colour (opaque ARGB) of a column from the climate map, as a client with Almanac colours grass
     * there today: for far terrain whose captured snow should go, or to tint far grass to the season. Empty where
     * {@link #farSnowLine} is unknown.
     */
    public static OptionalInt seasonalGrassColour(Level level, int x, int z) {
        return ClimateHooks.farClimate().grassColour(level, x, z);
    }

    /** The seasonal foliage colour (opaque ARGB) of a column from the climate map; see {@link #seasonalGrassColour}. */
    public static OptionalInt seasonalFoliageColour(Level level, int x, int z) {
        return ClimateHooks.farClimate().foliageColour(level, x, z);
    }

    /**
     * The biome of a column on the climate map and its climate now, for haze, sandstorms and debug views. Empty where
     * {@link #farSnowLine} is unknown, and for biomes without seasons.
     */
    public static Optional<FarClimate> farClimate(Level level, int x, int z) {
        return ClimateHooks.farClimate().climate(level, x, z);
    }

    /**
     * A column of far terrain on the climate map.
     *
     * @param biome       the real biome of the cell holding the column
     * @param base        the biome's climate in spring and fall
     * @param seasonal    one step colder in winter and warmer in summer
     * @param rainReaches whether the current rain event reaches this climate
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
