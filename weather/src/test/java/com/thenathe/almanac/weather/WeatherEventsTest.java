package com.thenathe.almanac.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.Storm;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.WeatherHere;
import com.thenathe.almanac.internal.WeatherRecord;
import com.thenathe.almanac.internal.WetSpell;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Operators' weather and the weather standing still: a local storm rains (and a thunderstorm thunders) at once over
 * the place it was asked for; a clearing clears the lattice's storms and earlier ones, never a storm asked for after
 * it; world-wide weather reaches everywhere; while advance_weather is off every storm stands where it is. And the snow's
 * arithmetic (the wet spell over a stretch) counts exactly the ticks the per-place answer says it rained.
 */
class WeatherEventsTest {
    private static final long START = 30L * StormMathsV1.DAY;
    private static final WeatherRecord.Era SPRING = new WeatherRecord.Era(0, SubSeason.MID_SPRING, StormMathsV2.VERSION, 100, 100,
            100, StormMathsV2.DEFAULT_RAIN_SHARE);

    private static WeatherField field(List<WeatherRecord.Event> events, List<WeatherRecord.Freeze> freezes) {
        return Worlds.field(new WeatherRecord(Worlds.SEED, List.of(SPRING), events, freezes));
    }

    /** A place the lattice's storms rain on at {@link #START}. */
    private static double[] stormy(WeatherField field) {
        for (int step = 0; step < 5_000; step++) {
            if (field.at(step * 53.0, 64, step * 31.0, START).stormy()) {
                return new double[] {step * 53.0, step * 31.0};
            }
        }
        throw new AssertionError("no storm found");
    }

    @Test
    void aStormAskedForRainsAndThundersAtOnceOverThePlace() {
        WeatherField field = field(List.of(
                new WeatherRecord.Event(WeatherRecord.EventKind.THUNDERSTORM, START, START + 6_000, 5_000, -3_000, 1_200)),
                List.of());
        WeatherHere here = field.at(5_000, 64, -3_000, START);
        assertTrue(here.stormy() && here.thunder() > 0 && here.reaches(Climate.TEMPERATE), here.toString());
        assertTrue(field.at(5_900, 64, -3_000, START).stormy(), "rain over most of its 1,200 blocks from the start");
        assertFalse(field.at(5_000, 64, -3_000, START - 1).stormy(), "nothing before it was asked for");
        assertFalse(field.at(5_000, 64, -3_000, START + 6_001).stormy(), "nothing after the time asked");
    }

    @Test
    void aClearingClearsTheStormsBeforeItButNotOneAskedForAfter() {
        WeatherField plain = field(List.of(), List.of());
        double[] place = stormy(plain);
        WeatherRecord.Event clear = new WeatherRecord.Event(WeatherRecord.EventKind.CLEAR_AROUND, START - 10, START + 5_000,
                place[0], place[1], 2_500);
        WeatherRecord.Event storm = new WeatherRecord.Event(WeatherRecord.EventKind.STORM, START + 100, START + 3_000,
                place[0] + 300, place[1], 1_200);
        assertFalse(field(List.of(clear), List.of()).at(place[0], 64, place[1], START).stormy());
        WeatherField both = field(List.of(clear, storm), List.of());
        assertFalse(both.at(place[0], 64, place[1], START + 50).stormy(), "cleared until the storm is asked for");
        assertTrue(both.at(place[0], 64, place[1], START + 200).stormy(), "the later storm wins");
        WetSpell spell = both.wetSpell(place[0], place[1], START, START + 1_000);
        assertEquals(900, spell.ticks(Climate.TEMPERATE), "the snow counts only the later storm's rain");
    }

    @Test
    void weatherEverywhereReachesEveryPlace() {
        WeatherField field = field(List.of(new WeatherRecord.Event(WeatherRecord.EventKind.THUNDER_EVERYWHERE, START,
                START + 1_000, 0, 0, 0)), List.of());
        for (double x : new double[] {0, 50_000, -900_000}) {
            WeatherHere here = field.at(x, 64, x / 3, START + 10);
            assertTrue(here.stormy() && here.thunder() > 0 && here.reaches(Climate.COLD));
        }
        WeatherField clear = field(List.of(new WeatherRecord.Event(WeatherRecord.EventKind.CLEAR_EVERYWHERE, START,
                START + 1_000, 0, 0, 0)), List.of());
        double[] place = stormy(field(List.of(), List.of()));
        assertFalse(clear.at(place[0], 64, place[1], START + 10).stormy());
    }

    @Test
    void whileTheWeatherStandsStillStormsStayWhereTheyAre() {
        long frozenFrom = START - 5_000;
        WeatherField moving = field(List.of(), List.of());
        WeatherField frozen = field(List.of(), List.of(new WeatherRecord.Freeze(frozenFrom, START + 20_000)));
        assertEquals(frozenFrom, frozen.weatherTick(START), 1e-9);
        List<Storm> then = moving.stormsNear(0, 0, 6_000, frozenFrom);
        List<Storm> standing = frozen.stormsNear(0, 0, 6_000, frozen.weatherTick(START + 10_000));
        assertEquals(then, standing, "the same storms, in the same places");
        assertEquals(frozenFrom + 5_000, frozen.weatherTick(START + 25_000), 1e-9, "moving again from where they stood");
    }

    @Test
    void aStormAskedForWhileTheWeatherStandsStillIsAmongTheStormsNearAtOnce() {
        // The weather stands still from START; at START + 2,000 an operator places a storm and, elsewhere, clears the sky.
        double[] place = stormy(field(List.of(), List.of()));
        WeatherRecord.Freeze stand = new WeatherRecord.Freeze(START, START + 20_000);
        WeatherRecord.Event storm = new WeatherRecord.Event(WeatherRecord.EventKind.STORM, START + 2_000, START + 8_000,
                50_000, 50_000, 1_200);
        WeatherRecord.Event clear = new WeatherRecord.Event(WeatherRecord.EventKind.CLEAR_AROUND, START + 2_000, START + 8_000,
                place[0], place[1], 2_500);
        WeatherField field = field(List.of(storm, clear), List.of(stand));
        long now = START + 3_000;
        double tick = field.weatherTick(now);
        assertTrue(field.at(50_000, 64, 50_000, now).stormy(), "it rains under the placed storm");
        assertTrue(field.stormsNear(50_000, 50_000, 0, tick, now).stream().anyMatch(near -> near.radius() == 1_200),
                "and the placed storm is listed, as the weather at the place has it");
        assertFalse(field.at(place[0], 64, place[1], now).stormy(), "the clearing holds at its place");
        assertTrue(field.stormsNear(place[0], place[1], 0, tick, now).isEmpty(), "and no cleared storm is listed there");
        assertTrue(field.stormsNear(50_000, 50_000, 0, tick, START + 1_000).stream().noneMatch(near -> near.radius() == 1_200),
                "before it was asked for, the placed storm isn't there");
        // By the weather tick alone (0.4.0's stormsNear) the stand's first game tick is taken, before the command.
        assertTrue(field.stormsNear(50_000, 50_000, 0, tick).stream().noneMatch(near -> near.radius() == 1_200));
    }

    @Test
    void theSnowCountsExactlyTheTicksItRainedHere() {
        WeatherField field = field(List.of(
                new WeatherRecord.Event(WeatherRecord.EventKind.CLEAR_AROUND, START + 500, START + 1_500, 0, 0, 3_000),
                new WeatherRecord.Event(WeatherRecord.EventKind.STORM, START + 1_000, START + 2_000, 100, 100, 800)),
                List.of(new WeatherRecord.Freeze(START + 2_500, START + 2_800)));
        double[] place = stormy(field(List.of(), List.of()));
        for (double[] at : new double[][] {place, {0, 0}, {300, 0}}) {
            long counted = 0;
            for (long tick = START; tick < START + 4_000; tick++) {
                if (field.at(at[0], 64, at[1], tick).reaches(Climate.TEMPERATE)) {
                    counted++;
                }
            }
            assertEquals(counted, field.wetSpell(at[0], at[1], START, START + 4_000).ticks(Climate.TEMPERATE),
                    "at " + at[0] + ", " + at[1]);
        }
    }
}
