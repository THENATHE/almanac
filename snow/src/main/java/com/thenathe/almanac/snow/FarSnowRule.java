package com.thenathe.almanac.snow;

import com.thenathe.almanac.api.AlmanacApi;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.BiomeClimate;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Where far terrain shows snow (docs/FAR-SNOW.md): what a column would look like if its chunk loaded and was brought
 * up to date right now. While a climate's snow is melting (0.1.6) that is the land as it was before the thaw, with the
 * share of its snow the layers still have; the lines say where that snow lies, and
 * {@link com.thenathe.almanac.api.AlmanacApi#snowLayers} how much of it, layer by layer.
 *
 * <p>The answer for a column is one number, the <em>far snow line</em>: the lowest block Y at which a snow layer lies,
 * which is the Y of the top face of the ground under it. Snow lies at that height and above. Whether snow lies at a
 * height comes from {@link SnowRules#snow} itself, applied to a column as world generation left it, so far terrain and
 * chunk catch-up follow one rule. The ground is taken to be natural, except in biomes whose ground is partly snow
 * blocks ({@code #almanac:snow_surface}): there, where the ground decides, the answer is {@link #UNKNOWN}.
 */
public final class FarSnowRule {
    /** Nothing is known about the column (no climate map there yet, or no season): leave it as captured. */
    public static final int UNKNOWN = AlmanacApi.FAR_SNOW_UNKNOWN;
    /** Snow lies at every height. */
    public static final int EVERYWHERE = AlmanacApi.FAR_SNOW_EVERYWHERE;
    /** Snow lies at no height. */
    public static final int NOWHERE = AlmanacApi.FAR_SNOW_NOWHERE;

    /** Vanilla starts cooling the air this many blocks above sea level, by 0.05 for every 40 blocks. */
    private static final int SNOW_LEVEL_ABOVE_SEA = 17;
    private static final double BLOCKS_PER_DEGREE = 40.0 / 0.05;
    /** Far above any build height; a line higher than this is no line. */
    private static final double HIGHEST_LINE = 1 << 20;

    private FarSnowRule() {
    }

    /**
     * What far terrain shows in a sub-season: the sub-season and the layers of snow begun in each climate. Usually
     * that is the present. While a climate that is warm now still has snow melting, it is the last sub-season in
     * which that climate was cold, with the layers there are now: the land keeps its snow lines through its thaw, and
     * the cover says how much of the snow under them is left. (Before 0.1.6 early spring showed late winter's snow
     * whole, and the lines changed at mid spring.)
     */
    public static Shown shown(SubSeason now, Map<Climate, Integer> layersNow) {
        for (Climate climate : Climate.values()) {
            if (layersNow.getOrDefault(climate, 0) > 0 && !SnowLog.holdsSnow(climate, now)) {
                SubSeason before = now;
                for (int step = 0; step < SubSeason.COUNT && !SnowLog.holdsSnow(climate, before); step++) {
                    before = before.previous();
                }
                return new Shown(before, layersNow);
            }
        }
        return new Shown(now, layersNow);
    }

    /** A sub-season and the layers of snow begun in each climate (missing climates have none). */
    public record Shown(SubSeason subSeason, Map<Climate, Integer> layers) {
        public Shown {
            layers = Map.copyOf(layers);
        }

        public int layers(Climate climate) {
            return layers.getOrDefault(climate, 0);
        }
    }

    /**
     * The lowest Y at which a biome is cold enough for snow in one column, by vanilla's own arithmetic: at and below
     * 17 blocks above sea level the biome's temperature counts as it is; above that the air cools by 0.05 for every
     * 40 blocks, shifted up or down by up to 8 blocks of vanilla's temperature noise.
     *
     * <p>A biome already cold at sea level counts as cold {@link #EVERYWHERE}. (Strictly, the noise can warm the
     * first few blocks above the cooling height again for a biome within 0.01 of the snow temperature; those few
     * blocks are ignored.)
     *
     * @param temperature the biome's temperature in this column after its temperature modifier, as at sea level
     * @param noise       vanilla's temperature noise for the column, in blocks ({@code TEMPERATURE_NOISE} at x/8,
     *                    z/8, times 8)
     */
    public static int coldLine(float temperature, float noise, int seaLevel) {
        if (!Float.isFinite(temperature) || !Float.isFinite(noise)) {
            return NOWHERE;
        }
        int snowLevel = seaLevel + SNOW_LEVEL_ABOVE_SEA;
        if (temperature < BiomeClimate.SNOW_TEMPERATURE) {
            return EVERYWHERE;
        }
        double estimate = snowLevel + (temperature - BiomeClimate.SNOW_TEMPERATURE) * BLOCKS_PER_DEGREE - noise;
        if (estimate > HIGHEST_LINE) {
            return NOWHERE;
        }
        // The estimate is within a block of the answer; settle it with the same float arithmetic vanilla uses.
        int line = Math.max(snowLevel + 1, (int) Math.floor(estimate));
        while (line > snowLevel + 1 && isCold(temperature, noise, line - 1, snowLevel)) {
            line--;
        }
        for (int step = 0; step < 8 && !isCold(temperature, noise, line, snowLevel); step++) {
            line++;
        }
        return line;
    }

    /** Vanilla's {@code Biome#coldEnoughToSnow}, for a temperature already through its modifier. */
    static boolean isCold(float temperature, float noise, int y, int snowLevel) {
        if (y <= snowLevel) {
            return temperature < BiomeClimate.SNOW_TEMPERATURE;
        }
        return temperature - (noise + y - snowLevel) * 0.05F / 40.0F < BiomeClimate.SNOW_TEMPERATURE;
    }

    /**
     * Whether a column's surface has snow once it is brought up to date: {@link SnowRules#snow} on a column as world
     * generation left it, with a layer of native snow where the biome's own climate snows, free to hold more and with
     * no player's snow.
     *
     * @param coldNow       the season makes it cold enough for snow here
     * @param realCold      the biome's own climate is cold enough for snow here
     * @param layers        the layers of snow begun in the biome's climate
     * @param naturalGround the ground is natural ({@code #almanac:natural_snow_ground}); false for biomes whose
     *                      ground is snow blocks, where native snow never melts
     */
    public static boolean snowLies(Climate base, boolean coldNow, boolean realCold, int layers, boolean nativeSnowMelts,
                                   int naturalHeight, boolean naturalGround) {
        SnowRules.SnowSpot generated = new SnowRules.SnowSpot(base, coldNow, realCold, realCold ? 1 : 0, 0, false, true,
                naturalGround);
        return SnowRules.snow(generated, layers, true, nativeSnowMelts, naturalHeight).layers() > 0;
    }

    /**
     * A column's far snow line from its two cold lines: where the season makes it cold, and where the biome's own
     * climate does.
     *
     * <p>In biomes whose ground is partly snow blocks ({@code #almanac:snow_surface}: groves, snowy slopes, peaks),
     * snow on the snow blocks never melts while snow on grass, dirt and leaves does. When that makes a difference
     * (their summer melt) one line can't describe the column, so it is {@link #UNKNOWN} and far terrain stays as
     * captured; the rest of the year both kinds of ground agree.
     *
     * @param base             the biome's base climate, or null for a biome without seasons, which keeps its own snow
     * @param seasonalColdLine {@link #coldLine} of the sub-season's copy of the biome
     * @param realColdLine     {@link #coldLine} of the biome itself
     * @param layers           the layers of snow begun in the biome's climate
     * @param snowSurface      the biome's ground is partly snow blocks
     * @return a Y, {@link #EVERYWHERE}, {@link #NOWHERE}, or {@link #UNKNOWN} where the ground decides
     */
    public static int snowLine(@Nullable Climate base, int seasonalColdLine, int realColdLine, int layers,
                               boolean nativeSnowMelts, int naturalHeight, boolean snowSurface) {
        if (base == null) {
            return realColdLine;
        }
        int onNaturalGround = lineOverGround(base, seasonalColdLine, realColdLine, layers, nativeSnowMelts, naturalHeight,
                true);
        if (!snowSurface) {
            return onNaturalGround;
        }
        int onSnowBlocks = lineOverGround(base, seasonalColdLine, realColdLine, layers, nativeSnowMelts, naturalHeight,
                false);
        return onNaturalGround == onSnowBlocks ? onNaturalGround : UNKNOWN;
    }

    /**
     * The line over one kind of ground. Above both cold lines a column is cold both ways and below both it is
     * neither, so only the band between them needs a look.
     */
    private static int lineOverGround(Climate base, int seasonalColdLine, int realColdLine, int layers,
                                      boolean nativeSnowMelts, int naturalHeight, boolean naturalGround) {
        if (!snowLies(base, true, true, layers, nativeSnowMelts, naturalHeight, naturalGround)) {
            return NOWHERE;
        }
        int low = Math.min(seasonalColdLine, realColdLine);
        int high = Math.max(seasonalColdLine, realColdLine);
        if (low == high) {
            return low;
        }
        boolean seasonColdFirst = seasonalColdLine < realColdLine;
        boolean snowBetween = snowLies(base, seasonColdFirst, !seasonColdFirst, layers, nativeSnowMelts, naturalHeight,
                naturalGround);
        return snowBetween ? low : high;
    }

    /** Fits a line to a level's heights: at or below the bottom it is everywhere, above the top nowhere. */
    public static int withinLevel(int line, int minY, int maxY) {
        if (line == UNKNOWN) {
            return UNKNOWN;
        }
        if (line <= minY) {
            return EVERYWHERE;
        }
        return line > maxY ? NOWHERE : line;
    }
}
