package com.thenathe.almanac.weather;

import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.WeatherRecord;
import java.util.ArrayList;
import java.util.List;

/** Weather records for the tests: a world held in one sub-season, or one going round the year. */
final class Worlds {
    static final long SEED = Hashes.weatherSeed(-4_172_144_997_902_289_642L);

    private Worlds() {
    }

    /** Regional weather from tick 0, held in one sub-season, with every share at 100. */
    static WeatherRecord heldIn(SubSeason subSeason) {
        return new WeatherRecord(SEED, List.of(new WeatherRecord.Era(0, subSeason, StormMathsV1.VERSION, 100, 100, 100)),
                List.of(), List.of());
    }

    /** Regional weather from tick 0 going round the year, {@code days} days to a sub-season. */
    static WeatherRecord year(int days, int years) {
        List<WeatherRecord.Era> eras = new ArrayList<>();
        for (int step = 0; step < SubSeason.COUNT * years; step++) {
            eras.add(new WeatherRecord.Era((long) step * days * 24_000, SubSeason.byIndex(step), StormMathsV1.VERSION,
                    100, 100, 100));
        }
        return new WeatherRecord(SEED, eras, List.of(), List.of());
    }

    static WeatherField field(WeatherRecord record) {
        return new WeatherField(record, new CellCache());
    }
}
