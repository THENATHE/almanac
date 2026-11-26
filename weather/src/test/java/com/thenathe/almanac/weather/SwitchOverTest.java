package com.thenathe.almanac.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.Storm;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.api.Wind;
import com.thenathe.almanac.internal.WeatherHere;
import com.thenathe.almanac.internal.WeatherRecord;
import com.thenathe.almanac.internal.WetSpell;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * A world last saved by Almanac 1.0.0 opened by 1.1.0: its record gains an era on version 2 of the storm maths at the
 * tick it opens, and everything about the weather before that tick is what 1.0.0 answered, to the bit. The snow that
 * lies in land nobody visited is caught up from the same answers ({@code wetSpell}), so it is kept too.
 */
class SwitchOverTest {
    private static final long DAY = StormMathsV1.DAY;
    /** The tick the world is opened by 1.1.0. */
    private static final long UPGRADE = 100 * DAY + 5_432;

    /**
     * What Almanac 1.0.0 ({@code 79d42e6}) answered for {@link #oldRecord}, worked out by this test's
     * {@link #fingerprint} run against 1.0.0's own classes (the record built with 1.0.0's six-field era).
     */
    private static final long FINGERPRINT_1_0_0 = -4_492_855_645_231_246_185L;

    /** A 1.0.0 record: a year of sub-seasons on version 1, with an operator's storm and clearing and a stand. */
    private static WeatherRecord oldRecord() {
        List<WeatherRecord.Era> eras = new ArrayList<>();
        for (int step = 0; step * 8 * DAY < UPGRADE; step++) {
            eras.add(new WeatherRecord.Era(step * 8 * DAY, SubSeason.byIndex(step), StormMathsV1.VERSION, 100, 70, 100, 0));
        }
        List<WeatherRecord.Event> events = List.of(
                new WeatherRecord.Event(WeatherRecord.EventKind.STORM, 31 * DAY, 31 * DAY + 12_000, 500.0, -800.0, 0.0),
                new WeatherRecord.Event(WeatherRecord.EventKind.CLEAR_AROUND, 52 * DAY, 53 * DAY, -3_000.0, 2_000.0, 2_500.0));
        List<WeatherRecord.Freeze> freezes = List.of(new WeatherRecord.Freeze(70 * DAY, 70 * DAY + 9_000));
        return new WeatherRecord(Worlds.SEED, eras, events, freezes);
    }

    /** The same world after 1.1.0 opened it: one more era, on version 2, from the tick it opened. */
    private static WeatherRecord upgradedRecord() {
        WeatherRecord old = oldRecord();
        List<WeatherRecord.Era> eras = new ArrayList<>(old.eras());
        WeatherRecord.Era last = eras.getLast();
        eras.add(new WeatherRecord.Era(UPGRADE, last.subSeason(), StormMathsV2.VERSION, last.hotShare(),
                last.temperateShare(), last.coldShare(), StormMathsV2.DEFAULT_RAIN_SHARE));
        return new WeatherRecord(old.seed(), eras, old.events(), old.freezes());
    }

    /**
     * Everything the weather answers before {@code before}, folded into one number: the weather over columns below
     * and above the cloud tops, each climate's wet ticks and when what falls next changes, the storms near a place
     * and the wind, at 40 places and 30 times.
     */
    private static long fingerprint(WeatherField field, long before) {
        Random random = new Random(20261125L);
        long hash = 17;
        int stormy = 0;
        for (int sample = 0; sample < 1_200; sample++) {
            double x = (random.nextDouble() * 2.0 - 1.0) * 40_000.0;
            double z = (random.nextDouble() * 2.0 - 1.0) * 40_000.0;
            long tick = 2 * DAY + (long) (random.nextDouble() * (before - 2 * DAY - 3 * WeatherField.HORIZON - 6_000));
            for (double y : new double[] {64.0, 300.0}) {
                WeatherHere here = field.at(x, y, z, tick);
                stormy += here.stormy() ? 1 : 0;
                hash = mix(hash, Float.floatToIntBits(here.storm()));
                hash = mix(hash, Float.floatToIntBits(here.thunder()));
                for (Climate climate : Climate.values()) {
                    hash = mix(hash, Float.floatToIntBits(here.rain(climate)));
                    hash = mix(hash, Float.floatToIntBits(here.thunder(climate)));
                }
            }
            WetSpell spell = field.wetSpell(x, z, tick, tick + 6_000);
            for (Climate climate : Climate.values()) {
                hash = mix(hash, spell.ticks(climate));
                hash = mix(hash, spell.wetAtEnd(climate) ? 1 : 0);
            }
            hash = mix(hash, spell.stableUntil());
            double weatherTick = field.weatherTick(tick);
            for (Storm storm : field.stormsNear(x, z, 3_000.0, weatherTick, tick)) {
                hash = mix(hash, storm.id());
                hash = mix(hash, Double.doubleToLongBits(storm.centreX(weatherTick)));
                hash = mix(hash, Double.doubleToLongBits(storm.radius()));
            }
            Wind wind = field.wind(weatherTick);
            hash = mix(hash, Double.doubleToLongBits(wind.direction()));
            hash = mix(hash, Double.doubleToLongBits(wind.speed()));
        }
        assertTrue(stormy > 100, "enough of the samples are under a storm to say something: " + stormy);
        return hash;
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 0x100_0000_01B3L + 0x9E37_79B9_7F4A_7C15L;
    }

    @Test
    void theWeatherBeforeTheUpgradeIsWhat100Answered() {
        long upgraded = fingerprint(Worlds.field(upgradedRecord()), UPGRADE);
        System.out.printf("fingerprint of the weather before the upgrade: %d%n", upgraded);
        assertEquals(fingerprint(Worlds.field(oldRecord()), UPGRADE), upgraded, "the new era changes nothing before it");
        assertEquals(FINGERPRINT_1_0_0, upgraded, "1.1.0 answers as 1.0.0 did");
    }

    @Test
    void theStormsInTheSkyAtTheUpgradeLiveTheirLivesOut() {
        WeatherField old = Worlds.field(oldRecord());
        WeatherField upgraded = Worlds.field(upgradedRecord());
        double weatherTick = upgraded.weatherTick(UPGRADE);
        Set<Long> inTheSky = old.stormsNear(0.0, 0.0, 30_000.0, weatherTick - 1, UPGRADE - 1).stream()
                .map(Storm::id).collect(Collectors.toSet());
        assertTrue(inTheSky.size() > 5, "storms about at the upgrade: " + inTheSky.size());
        // An hour of play later, every one of them that is still alive is still there, unchanged.
        long later = UPGRADE + 3 * 60 * 20;
        List<Storm> before = old.stormsNear(0.0, 0.0, 30_000.0, weatherTick + 3_600, later);
        List<Storm> after = upgraded.stormsNear(0.0, 0.0, 30_000.0, weatherTick + 3_600, later);
        for (Storm storm : before) {
            if (inTheSky.contains(storm.id())) {
                assertTrue(after.contains(storm), "storm " + storm.id() + " lives on");
            }
        }
        // Version 2's storms are born from the next lattice day on (lattice days are in weather time).
        double nextDay = Math.floor(weatherTick / DAY + 1) * DAY;
        assertEquals(stormIds(old, nextDay - 1), stormIds(upgraded, nextDay - 1), "the day of the upgrade is 1.0.0's");
        double twoDaysOn = nextDay + 3 * DAY;
        assertNotEquals(stormIds(old, twoDaysOn), stormIds(upgraded, twoDaysOn), "later days are version 2's");
        // The wind is the same either side.
        assertEquals(old.wind(twoDaysOn), upgraded.wind(twoDaysOn));
        assertEquals(old.wind(weatherTick), upgraded.wind(weatherTick));
    }

    @Test
    void aChangedShareOfTimeUnderRainCountsFromTheNextDay() {
        WeatherRecord upgraded = upgradedRecord();
        List<WeatherRecord.Era> eras = new ArrayList<>(upgraded.eras());
        WeatherRecord.Era last = eras.getLast();
        long change = UPGRADE + 3 * DAY + 777;
        eras.add(new WeatherRecord.Era(change, last.subSeason(), StormMathsV2.VERSION, last.hotShare(),
                last.temperateShare(), last.coldShare(), 45));
        WeatherField before = Worlds.field(upgraded);
        WeatherField after = Worlds.field(new WeatherRecord(upgraded.seed(), eras, upgraded.events(), upgraded.freezes()));
        double dayStart = Math.floor(after.weatherTick(change) / DAY) * DAY;
        assertEquals(stormIds(before, dayStart + DAY - 1), stormIds(after, dayStart + DAY - 1),
                "the day the share changed keeps its storms");
        assertTrue(stormIds(after, dayStart + 4 * DAY).size() > stormIds(before, dayStart + 4 * DAY).size(),
                "a larger share brings more storms from the next day on");
    }

    /** The storms within 30,000 blocks of the origin at a weather tick, after the stand (none is placed then). */
    private static List<Long> stormIds(WeatherField field, double weatherTick) {
        long gameTick = (long) weatherTick + 9_000;
        return field.stormsNear(0.0, 0.0, 30_000.0, weatherTick, gameTick).stream().map(Storm::id).toList();
    }
}
