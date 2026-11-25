package com.thenathe.almanac.snow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SnowClocks;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.climate.EventReach;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The weather log against core's event reach: the two clocks, the most layers, the ice cover and the saved log. */
class SnowLogTest {
    private static final double EPSILON = 1e-9;
    /** The defaults the user confirmed: 12,000 ticks a layer to build, 18,000 to melt; ice on the snowfall clock. */
    private static final SnowClocks DEFAULTS = new SnowClocks(12_000, 18_000, 12_000, 3);
    private static final Set<Climate> ALL = EnumSet.allOf(Climate.class);

    @Test
    void snowBuildsOneLayerPerSnowfallClockOnlyWhereTheEventReachesAndItSnows() {
        SnowLog log = new SnowLog(0);
        log.observe(0, SubSeason.MID_WINTER, true, Set.of(Climate.COLD), DEFAULTS);
        assertEquals(0.5, log.depth(Climate.COLD, 6_000), EPSILON);
        assertEquals(1.0, log.depth(Climate.COLD, 12_000), EPSILON);
        // The event doesn't reach TEMPERATE, and HOT gets rain in winter.
        assertEquals(0.0, log.depth(Climate.TEMPERATE, 12_000), EPSILON);
        assertEquals(0.0, log.depth(Climate.HOT, 12_000), EPSILON);
    }

    @Test
    void threeLayersTakeThreeSnowfallClocksAndStopAtTheMostLayers() {
        SnowLog log = new SnowLog(0);
        log.observe(0, SubSeason.MID_WINTER, true, ALL, DEFAULTS);
        assertEquals(2.0, log.depth(Climate.TEMPERATE, 24_000), EPSILON);
        assertEquals(3.0, log.depth(Climate.TEMPERATE, 36_000), EPSILON);
        assertEquals(3.0, log.depth(Climate.TEMPERATE, 1_000_000), EPSILON, "no deeper than the most layers");
        SnowLog deep = new SnowLog(0);
        deep.observe(0, SubSeason.MID_WINTER, true, ALL, new SnowClocks(12_000, 18_000, 12_000, 5));
        assertEquals(5.0, deep.depth(Climate.TEMPERATE, 1_000_000), EPSILON);
    }

    @Test
    void theThawTakesItsOwnClockALayerAtATime() {
        SnowLog log = new SnowLog(0);
        log.observe(0, SubSeason.MID_WINTER, true, ALL, DEFAULTS);
        log.observe(24_000, SubSeason.MID_WINTER, false, ALL, DEFAULTS);
        assertEquals(2.0, log.depth(Climate.TEMPERATE, 50_000), EPSILON, "cold and dry holds");
        log.observe(50_000, SubSeason.EARLY_SPRING, false, ALL, DEFAULTS);
        assertEquals(1.5, log.depth(Climate.TEMPERATE, 59_000), EPSILON, "half a thaw clock melts half a layer");
        assertEquals(0.0, log.depth(Climate.TEMPERATE, 50_000 + 2 * 18_000), EPSILON, "two layers, two thaw clocks");
        // COLD is still cold in spring, so its snow stays.
        assertEquals(2.0, log.depth(Climate.COLD, 90_000), EPSILON);
    }

    @Test
    void stillWaterFreezesOverOnTheIceClockWhateverTheWeatherAndThawsOnTheThawClock() {
        SnowLog log = new SnowLog(0);
        log.observe(0, SubSeason.MID_WINTER, false, Set.of(), new SnowClocks(12_000, 18_000, 4_000, 3));
        assertEquals(0.5, log.ice(Climate.TEMPERATE, 2_000), EPSILON, "dry cold freezes too");
        assertEquals(1.0, log.ice(Climate.TEMPERATE, 50_000), EPSILON);
        assertEquals(0.0, log.ice(Climate.HOT, 50_000), EPSILON, "never cold in winter");
        log.observe(50_000, SubSeason.EARLY_SPRING, false, Set.of(), new SnowClocks(12_000, 18_000, 4_000, 3));
        assertEquals(0.5, log.ice(Climate.TEMPERATE, 59_000), EPSILON);
        assertEquals(0.0, log.ice(Climate.TEMPERATE, 68_000), EPSILON);
    }

    @Test
    void aChangeOfTheClocksStartsASegmentAndDoesNotRewriteThePast() {
        SnowLog log = new SnowLog(0);
        log.observe(0, SubSeason.MID_WINTER, true, ALL, DEFAULTS);
        assertFalse(log.observe(3_000, SubSeason.MID_WINTER, true, ALL, DEFAULTS));
        assertTrue(log.observe(6_000, SubSeason.MID_WINTER, true, ALL, new SnowClocks(3_000, 18_000, 12_000, 3)));
        assertEquals(0.5, log.depth(Climate.TEMPERATE, 6_000), EPSILON, "the first half layer at 12,000 a layer");
        assertEquals(1.5, log.depth(Climate.TEMPERATE, 9_000), EPSILON, "then a layer in 3,000");
        assertEquals(2, log.segments().size());
    }

    @Test
    void segmentsSavedBefore030RunOnTheLegacyClocksAndTheGameRule() {
        // As 0.2.0 saved it: no clocks of its own.
        SnowLog legacy = SnowLog.CODEC.parse(JsonOps.INSTANCE, JsonOps.INSTANCE.createMap(Map.of(
                JsonOps.INSTANCE.createString("segments"), JsonOps.INSTANCE.createList(Stream.of(
                        JsonOps.INSTANCE.createMap(Map.of(
                                JsonOps.INSTANCE.createString("start"), JsonOps.INSTANCE.createLong(0),
                                JsonOps.INSTANCE.createString("sub_season"), JsonOps.INSTANCE.createString("mid_winter"),
                                JsonOps.INSTANCE.createString("raining"), JsonOps.INSTANCE.createBoolean(true),
                                JsonOps.INSTANCE.createString("reached"), JsonOps.INSTANCE.createList(
                                        Stream.of(JsonOps.INSTANCE.createString("temperate"))))))))))
                .getOrThrow();
        legacy.setLegacyMaxLayers(1);
        assertTrue(legacy.hasLegacySegments());
        assertEquals(2048.0 / SnowLog.LEGACY_SNOWFALL_TICKS, legacy.depth(Climate.TEMPERATE, 2_048), EPSILON,
                "a layer per 4,096 ticks, as 0.2.0 counted");
        assertEquals(1.0, legacy.depth(Climate.TEMPERATE, 100_000), EPSILON, "no deeper than the game rule then");
        assertEquals(1.0, legacy.ice(Climate.TEMPERATE, 10), EPSILON, "0.2.0 froze still water at once in the cold");
        // The world opens in 0.3.0 at tick 100,000 and the snow goes on from there on the new clocks.
        legacy.observe(100_000, SubSeason.MID_WINTER, true, Set.of(Climate.TEMPERATE), DEFAULTS);
        assertEquals(1.0, legacy.depth(Climate.TEMPERATE, 100_000), EPSILON, "nothing lost at the upgrade");
        assertEquals(1.5, legacy.depth(Climate.TEMPERATE, 106_000), EPSILON);
    }

    @Test
    void theSnowLineCountsAsColdAndRainyHotWinterDoesNot() {
        SnowLog log = new SnowLog(0);
        log.observe(0, SubSeason.EARLY_SUMMER, true, ALL, DEFAULTS);
        assertEquals(0.25, log.depth(Climate.COLD, 3_000), EPSILON);
        assertEquals(0.0, log.depth(Climate.TEMPERATE, 3_000), EPSILON);
    }

    @Test
    void sameWeatherAddsNoSegment() {
        SnowLog log = new SnowLog(0);
        assertTrue(log.observe(0, SubSeason.MID_FALL, false, Set.of(), DEFAULTS));
        assertFalse(log.observe(100, SubSeason.MID_FALL, false, Set.of(), DEFAULTS));
        assertTrue(log.observe(200, SubSeason.MID_FALL, true, Set.of(Climate.COLD), DEFAULTS));
        assertEquals(2, log.segments().size());
    }

    @Test
    void foldingOldSegmentsGivesTheSameAmounts() {
        SnowLog log = new SnowLog(0);
        SnowLog replay = new SnowLog(0);
        Map<Climate, SnowLog.Amount> expected = new EnumMap<>(Climate.class);
        for (Climate climate : Climate.values()) {
            expected.put(climate, SnowLog.Amount.NONE);
        }
        SubSeason[] year = SubSeason.values();
        long tick = 0;
        for (int step = 0; step < 300; step++) {
            SubSeason subSeason = year[step / 25 % year.length];
            boolean raining = step % 2 == 0;
            Set<Climate> reached = EnumSet.noneOf(Climate.class);
            double strength = EventReach.strength(1234L, step / 2);
            for (Climate climate : Climate.values()) {
                if (EventReach.reaches(strength, 30 + 30 * climate.ordinal())) {
                    reached.add(climate);
                }
            }
            SnowClocks clocks = new SnowClocks(1_000 + step % 3 * 700, 1_500, 900, 4);
            log.observe(tick, subSeason, raining, reached, clocks);
            long duration = 700 + step % 5 * 311;
            SnowLog.Segment segment = new SnowLog.Segment(tick, subSeason, raining, reached, clocks);
            long from = tick;
            expected.replaceAll((climate, amount) ->
                    replay.advance(amount, SnowArea.of(climate), segment, from, from + duration, RainSource.GLOBAL));
            tick += duration;
        }
        assertTrue(log.segments().size() <= SnowLog.MAX_SEGMENTS);
        for (Climate climate : Climate.values()) {
            assertEquals(expected.get(climate).depth(), log.depth(climate, tick), EPSILON, climate.id());
            assertEquals(expected.get(climate).ice(), log.ice(climate, tick), EPSILON, climate.id());
        }
    }

    @Test
    void theRateIsWhatTheAmountsMoveByUntilTheWeatherChanges() {
        SnowLog log = new SnowLog(0);
        log.observe(0, SubSeason.MID_WINTER, true, ALL, DEFAULTS);
        SnowLog.Trend trend = log.trend(SnowArea.of(Climate.TEMPERATE), 1_000, RainSource.GLOBAL);
        assertEquals(1.0 / 12_000, trend.depthPerTick(), EPSILON);
        assertEquals(1.0 / 12_000, trend.icePerTick(), EPSILON);
        SnowLog.Trend full = log.trend(SnowArea.of(Climate.TEMPERATE), 100_000, RainSource.GLOBAL);
        assertEquals(0.0, full.depthPerTick(), "at the most layers the depth stands still");
        assertEquals(0.0, full.icePerTick());
    }

    @Test
    void savedLogReadsBackTheSame() {
        SnowLog log = new SnowLog(10);
        log.observe(10, SubSeason.LATE_FALL, true, Set.of(Climate.COLD, Climate.TEMPERATE), DEFAULTS);
        log.observe(5000, SubSeason.EARLY_WINTER, true, Set.of(Climate.COLD), new SnowClocks(6_000, 9_000, 3_000, 2));
        var json = SnowLog.CODEC.encodeStart(JsonOps.INSTANCE, log).getOrThrow();
        SnowLog read = SnowLog.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(log.segments(), read.segments());
        for (Climate climate : Climate.values()) {
            assertEquals(log.depth(climate, 9000), read.depth(climate, 9000), EPSILON);
            assertEquals(log.ice(climate, 9000), read.ice(climate, 9000), EPSILON);
        }
        // Climates are saved by name.
        assertTrue(json.toString().contains("\"temperate\""), json.toString());
        assertTrue(json.toString().contains("\"snowfall_ticks\":6000"), json.toString());
    }
}
