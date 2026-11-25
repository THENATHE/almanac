package com.thenathe.almanac.climate;

import com.thenathe.almanac.config.ServerConfig;
import java.util.Objects;

/**
 * The three settings the season's temperatures come from: the temperature winter gives warmer biomes when it brings
 * them snow, the one summer gives cold biomes when it brings them rain, and the height of the summer snow line in
 * cold biomes. Until 0.1.7 they were built into the seasonal biome copies when the server started; from 0.2.0 the
 * {@link ClimateTable} is built from them again whenever they change.
 */
public record ClimateSettings(float winterTemperature, float summerTemperature, int snowLineAboveSea) {
    public static final ClimateSettings DEFAULTS = of(new ServerConfig());

    /** Vanilla starts cooling the air 17 blocks above sea level, by 0.05 for every 40 blocks. */
    private static final int VANILLA_SNOW_LEVEL = 17;
    private static final float COOLING_PER_BLOCK = 0.05F / 40.0F;

    public static ClimateSettings of(ServerConfig config) {
        return new ClimateSettings((float) config.winterTemperature, (float) config.summerTemperature,
                config.snowLineAboveSea);
    }

    /**
     * A number that changes when any of the three settings does, saved with each chunk's snow state so chunks are
     * brought up to date again after they change. It is worked out exactly as 0.1.3 to 0.1.7 worked out the copies'
     * number from the same settings, so chunks those versions saved stay up to date.
     */
    public int fingerprint() {
        return Objects.hash(Float.floatToIntBits(winterTemperature), Float.floatToIntBits(summerTemperature),
                snowLineAboveSea);
    }

    /** A base temperature at which vanilla's cooling with height reaches the snow line at the given height. */
    public float snowLineTemperature() {
        return ClimateSorter.SNOW_TEMPERATURE + (snowLineAboveSea - VANILLA_SNOW_LEVEL) * COOLING_PER_BLOCK;
    }
}
