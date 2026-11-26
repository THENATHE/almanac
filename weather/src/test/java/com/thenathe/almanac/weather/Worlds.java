package com.thenathe.almanac.weather;

import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.WeatherRecord;
import java.util.ArrayList;
import java.util.List;

/**
 * Weather records for the tests: a world held in one sub-season, or one going round the year, on the current storm
 * maths unless a test asks for another version.
 */
final class Worlds {
    static final long SEED = Hashes.weatherSeed(-4_172_144_997_902_289_642L);

    private Worlds() {
    }

    /** Regional weather from tick 0 on the current maths, held in one sub-season, with every share at 100. */
    static WeatherRecord heldIn(SubSeason subSeason) {
        return heldIn(subSeason, StormMathsV2.VERSION, StormMathsV2.DEFAULT_RAIN_SHARE);
    }

    /** The same on one version of the maths, under rain {@code rainShare} percent of the time (0: not recorded). */
    static WeatherRecord heldIn(SubSeason subSeason, int version, int rainShare) {
        return new WeatherRecord(SEED, List.of(new WeatherRecord.Era(0, subSeason, version, 100, 100, 100, rainShare)),
                List.of(), List.of());
    }

    /** Regional weather from tick 0 on the current maths going round the year, {@code days} days to a sub-season. */
    static WeatherRecord year(int days, int years) {
        return year(days, years, StormMathsV2.VERSION, StormMathsV2.DEFAULT_RAIN_SHARE);
    }

    /** The same on one version of the maths. */
    static WeatherRecord year(int days, int years, int version, int rainShare) {
        List<WeatherRecord.Era> eras = new ArrayList<>();
        for (int step = 0; step < SubSeason.COUNT * years; step++) {
            eras.add(new WeatherRecord.Era((long) step * days * 24_000, SubSeason.byIndex(step), version,
                    100, 100, 100, rainShare));
        }
        return new WeatherRecord(SEED, eras, List.of(), List.of());
    }

    static WeatherField field(WeatherRecord record) {
        return new WeatherField(record, new CellCache());
    }
}
