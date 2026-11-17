package com.thenathe.almanac.snow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.climate.EventReach;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SnowLogTest {
    private static final double EPSILON = 1e-9;

    @Test
    void snowBuildsOnlyWhereTheEventReachesAndItSnows() {
        SnowLog log = new SnowLog(0);
        log.observe(0, SubSeason.MID_WINTER, true, Set.of(Climate.COLD), 8);
        long ticks = 2048;
        assertEquals(ticks * SnowLog.SNOW_PER_TICK, log.depth(Climate.COLD, ticks, 8), EPSILON);
        // The event doesn't reach TEMPERATE, and HOT gets rain in winter.
        assertEquals(0.0, log.depth(Climate.TEMPERATE, ticks, 8), EPSILON);
        assertEquals(0.0, log.depth(Climate.HOT, ticks, 8), EPSILON);
    }

    @Test
    void depthIsCappedByTheGameRule() {
        SnowLog log = new SnowLog(0);
        log.observe(0, SubSeason.MID_WINTER, true, EnumSet.allOf(Climate.class), 1);
        assertEquals(1.0, log.depth(Climate.TEMPERATE, 1_000_000, 1), EPSILON);
        assertEquals(3.0, log.depth(Climate.TEMPERATE, 1_000_000, 3), EPSILON);
        assertEquals(0.0, log.depth(Climate.TEMPERATE, 1_000_000, 0), EPSILON);
    }

    @Test
    void coldAndDryHoldsAndWarmMelts() {
        SnowLog log = new SnowLog(0);
        log.observe(0, SubSeason.MID_WINTER, true, EnumSet.allOf(Climate.class), 8);
        log.observe(4096, SubSeason.MID_WINTER, false, EnumSet.allOf(Climate.class), 8);
        assertEquals(1.0, log.depth(Climate.TEMPERATE, 4096, 8), EPSILON);
        assertEquals(1.0, log.depth(Climate.TEMPERATE, 50_000, 8), EPSILON);

        log.observe(50_000, SubSeason.EARLY_SPRING, false, EnumSet.allOf(Climate.class), 8);
        assertEquals(1.0 - 1200 * SnowLog.MELT_PER_TICK, log.depth(Climate.TEMPERATE, 51_200, 8), EPSILON);
        assertEquals(0.0, log.depth(Climate.TEMPERATE, 60_000, 8), EPSILON);
        // COLD is still cold in spring, so its snow stays.
        assertEquals(1.0, log.depth(Climate.COLD, 60_000, 8), EPSILON);
    }

    @Test
    void theSnowLineCountsAsColdAndRainyHotWinterDoesNot() {
        SnowLog log = new SnowLog(0);
        log.observe(0, SubSeason.EARLY_SUMMER, true, EnumSet.allOf(Climate.class), 8);
        assertEquals(4096 * SnowLog.SNOW_PER_TICK, log.depth(Climate.COLD, 4096, 8), EPSILON);
        assertEquals(0.0, log.depth(Climate.TEMPERATE, 4096, 8), EPSILON);
    }

    @Test
    void sameWeatherAddsNoSegment() {
        SnowLog log = new SnowLog(0);
        assertTrue(log.observe(0, SubSeason.MID_FALL, false, Set.of(), 1));
        assertFalse(log.observe(100, SubSeason.MID_FALL, false, Set.of(), 1));
        assertTrue(log.observe(200, SubSeason.MID_FALL, true, Set.of(Climate.COLD), 1));
        assertEquals(2, log.segments().size());
    }

    @Test
    void foldingOldSegmentsGivesTheSameDepth() {
        SnowLog log = new SnowLog(0);
        Map<Climate, Double> expected = new EnumMap<>(Climate.class);
        for (Climate climate : Climate.values()) {
            expected.put(climate, 0.0);
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
            log.observe(tick, subSeason, raining, reached, 4);
            long duration = 700 + step % 5 * 311;
            SnowLog.Segment segment = new SnowLog.Segment(tick, subSeason, raining, reached);
            expected.replaceAll((climate, depth) -> SnowLog.advance(depth, climate, segment, duration, 4));
            tick += duration;
        }
        assertTrue(log.segments().size() <= SnowLog.MAX_SEGMENTS);
        for (Climate climate : Climate.values()) {
            assertEquals(expected.get(climate), log.depth(climate, tick, 4), EPSILON, climate.id());
        }
    }

    @Test
    void savedLogReadsBackTheSame() {
        SnowLog log = new SnowLog(10);
        log.observe(10, SubSeason.LATE_FALL, true, Set.of(Climate.COLD, Climate.TEMPERATE), 2);
        log.observe(5000, SubSeason.EARLY_WINTER, true, Set.of(Climate.COLD), 2);
        var json = SnowLog.CODEC.encodeStart(JsonOps.INSTANCE, log).getOrThrow();
        SnowLog read = SnowLog.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(log.segments(), read.segments());
        for (Climate climate : Climate.values()) {
            assertEquals(log.depth(climate, 9000, 2), read.depth(climate, 9000, 2), EPSILON);
        }
        // Climates are saved by name.
        assertTrue(json.toString().contains("\"temperate\""), json.toString());
    }

    @Test
    void layersRoundHalfUpWithinTheCap() {
        assertEquals(0, SnowLog.layers(0.49, 1));
        assertEquals(1, SnowLog.layers(0.5, 1));
        assertEquals(1, SnowLog.layers(3.0, 1));
        assertEquals(3, SnowLog.layers(3.2, 8));
        assertEquals(0, SnowLog.layers(5.0, 0));
    }
}
