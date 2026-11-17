package com.thenathe.almanac.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The shift table (section 4.2) and the default rain dials (section 4.3). */
class WeatherTableTest {
    @Test
    void springAndFallKeepTheBaseClimate() {
        for (SubSeason subSeason : new SubSeason[] {SubSeason.EARLY_SPRING, SubSeason.MID_FALL, SubSeason.LATE_FALL}) {
            assertEquals(WeatherKind.DRY, WeatherKind.of(Climate.HOT, subSeason));
            assertEquals(WeatherKind.RAIN, WeatherKind.of(Climate.TEMPERATE, subSeason));
            assertEquals(WeatherKind.SNOW, WeatherKind.of(Climate.COLD, subSeason));
        }
    }

    @Test
    void winterIsOneStepColder() {
        for (SubSeason subSeason : new SubSeason[] {SubSeason.EARLY_WINTER, SubSeason.MID_WINTER, SubSeason.LATE_WINTER}) {
            assertEquals(WeatherKind.RAIN, WeatherKind.of(Climate.HOT, subSeason));
            assertEquals(WeatherKind.SNOW, WeatherKind.of(Climate.TEMPERATE, subSeason));
            assertEquals(WeatherKind.SNOW, WeatherKind.of(Climate.COLD, subSeason));
        }
    }

    @Test
    void summerIsOneStepWarmerPartWayAtItsEdges() {
        assertEquals(WeatherKind.SNOW_LINE, WeatherKind.of(Climate.COLD, SubSeason.EARLY_SUMMER));
        assertEquals(WeatherKind.RAIN, WeatherKind.of(Climate.COLD, SubSeason.MID_SUMMER));
        assertEquals(WeatherKind.SNOW_LINE, WeatherKind.of(Climate.COLD, SubSeason.LATE_SUMMER));
        assertEquals(WeatherKind.RAIN, WeatherKind.of(Climate.TEMPERATE, SubSeason.EARLY_SUMMER));
        assertEquals(WeatherKind.DRY, WeatherKind.of(Climate.TEMPERATE, SubSeason.MID_SUMMER));
        assertEquals(WeatherKind.DRY, WeatherKind.of(Climate.HOT, SubSeason.MID_SUMMER));
    }

    @Test
    void defaultSharesMatchTheApprovedTable() {
        int[] temperate = {80, 100, 100, 60, 0, 60, 100, 100, 80, 60, 50, 60};
        int[] hot = {0, 0, 0, 0, 0, 0, 0, 0, 0, 50, 100, 50};
        int[] cold = {100, 100, 100, 50, 100, 50, 100, 100, 100, 100, 100, 100};
        for (SubSeason subSeason : SubSeason.values()) {
            assertEquals(temperate[subSeason.ordinal()], RainShares.defaultShare(Climate.TEMPERATE, subSeason), subSeason.id());
            assertEquals(hot[subSeason.ordinal()], RainShares.defaultShare(Climate.HOT, subSeason), subSeason.id());
            assertEquals(cold[subSeason.ordinal()], RainShares.defaultShare(Climate.COLD, subSeason), subSeason.id());
        }
    }

    @Test
    void dryCellsHaveNoSetting() {
        assertFalse(RainShares.hasShare(Climate.TEMPERATE, SubSeason.MID_SUMMER));
        assertFalse(RainShares.hasShare(Climate.HOT, SubSeason.MID_SPRING));
        assertTrue(RainShares.hasShare(Climate.HOT, SubSeason.MID_WINTER));
        Map<String, Integer> hotRow = RainShares.defaultRow(Climate.HOT);
        assertEquals(Map.of("early_winter", 50, "mid_winter", 100, "late_winter", 50), hotRow);
        assertEquals(11, RainShares.defaultRow(Climate.TEMPERATE).size());
        assertEquals(12, RainShares.defaultRow(Climate.COLD).size());
    }

    @Test
    void shiftedClimateFollowsTheSeason() {
        assertEquals(Climate.COLD, SeasonalClimate.shifted(Climate.TEMPERATE, SubSeason.MID_WINTER));
        assertEquals(Climate.TEMPERATE, SeasonalClimate.shifted(Climate.HOT, SubSeason.EARLY_WINTER));
        assertEquals(Climate.HOT, SeasonalClimate.shifted(Climate.TEMPERATE, SubSeason.LATE_SUMMER));
        assertEquals(Climate.TEMPERATE, SeasonalClimate.shifted(Climate.COLD, SubSeason.MID_SUMMER));
        assertEquals(Climate.COLD, SeasonalClimate.shifted(Climate.COLD, SubSeason.MID_FALL));
    }
}
