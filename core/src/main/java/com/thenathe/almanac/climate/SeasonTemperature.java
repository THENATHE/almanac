package com.thenathe.almanac.climate;

import com.thenathe.almanac.mixin.BiomeAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.synth.Noise;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

/**
 * Vanilla's temperature arithmetic, for a temperature the season gives a biome: the same cooling with height, by the
 * same float steps, so a seasonal temperature snows exactly where a biome with that temperature would. Safe from any
 * thread: nothing here keeps a cache.
 */
public final class SeasonTemperature {
    /**
     * The noise that moves vanilla's snow line up and down by up to 8 blocks: built as vanilla builds its own
     * ({@code Biome.TEMPERATURE_NOISE}, seed 1234), so it gives the same values ({@code SeasonTemperatureTest}).
     */
    public static final Noise NOISE = new SimplexNoise(new WorldgenRandom(new LegacyRandomSource(1234L)), true);

    /** Vanilla starts cooling the air this many blocks above sea level. */
    private static final int SNOW_LEVEL_ABOVE_SEA = 17;

    private SeasonTemperature() {
    }

    /**
     * A temperature at a position, cooled with height as vanilla cools a biome's ({@code Biome#getHeightAdjustedTemperature}
     * for a biome without a temperature modifier).
     */
    public static float cooled(float temperature, BlockPos pos, int seaLevel) {
        int snowLevel = seaLevel + SNOW_LEVEL_ABOVE_SEA;
        if (pos.getY() > snowLevel) {
            float noise = NOISE.get(pos.getX() / 8.0F, pos.getZ() / 8.0F) * 8.0F;
            return temperature - (noise + pos.getY() - snowLevel) * 0.05F / 40.0F;
        }
        return temperature;
    }

    /** A biome's own temperature at a position, its temperature modifier included, without vanilla's cache. */
    public static float own(Biome biome, BlockPos pos, int seaLevel) {
        return ((BiomeAccessor) (Object) biome).almanac$heightAdjustedTemperature(pos, seaLevel);
    }

    /** Vanilla's line between rain and snow: snow below 0.15. */
    public static boolean isCold(float temperature) {
        return !(temperature >= ClimateSorter.SNOW_TEMPERATURE);
    }
}
