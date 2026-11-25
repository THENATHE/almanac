package com.thenathe.almanac.climate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.internal.WeatherKind;
import org.junit.jupiter.api.Test;

/**
 * Which climates count as cold is worked out in two places: the snow log builds a climate's snow depth while its
 * {@link WeatherKind} is snow or a snow line ({@code SnowLog.advance}), and the climate table gives the temperature
 * that makes vanilla snow or rain there ({@link ClimateTable.Cell#of}). The two only agree because the winter
 * temperature setting stays below vanilla's snow line (0.15) and the summer temperature setting stays above it; this
 * test holds them together across the whole range of both settings.
 */
class ColdRulesAgreeTest {
    private static final float[] BIOME_TEMPERATURES = {-0.7F, 0.0F, 0.1F, 0.149F, 0.15F, 0.2F, 0.5F, 0.8F, 2.0F};

    /** Whether the snow log counts the climate as cold, as {@code SnowLog.advance} does. */
    private static boolean snowLogCold(WeatherKind kind) {
        return kind == WeatherKind.SNOW || kind == WeatherKind.SNOW_LINE;
    }

    private static boolean agree(ClimateSettings settings) {
        for (Climate base : Climate.values()) {
            for (SubSeason subSeason : SubSeason.values()) {
                WeatherKind kind = WeatherKind.of(base, subSeason);
                for (float biomeTemperature : BIOME_TEMPERATURES) {
                    float seasonal = ClimateTable.Cell.of(biomeTemperature, kind, settings).temperature();
                    boolean coldAtGround = seasonal < ClimateSorter.SNOW_TEMPERATURE;
                    boolean agrees = switch (kind) {
                        // Snow builds, and the table snows down to the ground.
                        case SNOW -> snowLogCold(kind) && coldAtGround;
                        // Rain low and snow high: the depth builds for the snow above the line, the ground rains.
                        case SNOW_LINE -> snowLogCold(kind) && !coldAtGround;
                        // The depth melts, and the table rains.
                        case RAIN -> !snowLogCold(kind) && !coldAtGround;
                        // The depth melts; nothing falls, so the biome keeps its own temperature.
                        case DRY -> !snowLogCold(kind) && seasonal == biomeTemperature;
                    };
                    if (!agrees) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    @Test
    void theRulesAgreeAcrossEveryAllowedSetting() {
        float[] winters = {(float) ServerConfig.MIN_WINTER_TEMPERATURE, 0.0F, (float) ServerConfig.MAX_WINTER_TEMPERATURE};
        float[] summers = {(float) ServerConfig.MIN_SUMMER_TEMPERATURE, 0.5F, (float) ServerConfig.MAX_SUMMER_TEMPERATURE};
        int[] snowLines = {ServerConfig.MIN_SNOW_LINE, 48, ServerConfig.MAX_SNOW_LINE};
        for (float winter : winters) {
            for (float summer : summers) {
                for (int snowLine : snowLines) {
                    ClimateSettings settings = new ClimateSettings(winter, summer, snowLine);
                    assertTrue(agree(settings), settings.toString());
                }
            }
        }
    }

    @Test
    void theyOnlyAgreeBecauseOfTheSettingLimits() {
        // A winter temperature at or above 0.15 would build snow depth where the table rains.
        assertFalse(agree(new ClimateSettings(0.2F, 0.5F, 48)));
        // A summer temperature below 0.15 would melt the depth where the table still snows.
        assertFalse(agree(new ClimateSettings(0.0F, 0.1F, 48)));
        assertTrue(ServerConfig.MAX_WINTER_TEMPERATURE < ClimateSorter.SNOW_TEMPERATURE);
        assertTrue(ServerConfig.MIN_SUMMER_TEMPERATURE >= ClimateSorter.SNOW_TEMPERATURE);
    }
}
