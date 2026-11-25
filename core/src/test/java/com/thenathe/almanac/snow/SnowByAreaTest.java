package com.thenathe.almanac.snow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SnowClocks;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.WeatherRecord;
import com.thenathe.almanac.internal.WeatherView;
import com.thenathe.almanac.internal.WetSpell;
import com.thenathe.almanac.weather.WeatherModule;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Snow by area under regional weather: a node's snow is the time a storm snowed on it, on the two clocks and in layers
 * as before; a world's snow from before regional weather carries over exactly at the switch-over; and nodes far apart
 * get different snow.
 */
class SnowByAreaTest {
    private static final SnowClocks CLOCKS = new SnowClocks(12_000, 18_000, 12_000, 3);
    private static final long DAY = 24_000;
    private static final long SWITCH_OVER = 40 * DAY;
    private static final long SEED = WeatherModule.weatherSeed(5_550_123L);
    private static final double EPSILON = 1e-9;

    /** A record with regional weather from the switch-over, in mid winter, every share at 100. */
    private static WeatherView winterWeather() {
        return WeatherModule.viewOf(new WeatherRecord(SEED,
                List.of(new WeatherRecord.Era(SWITCH_OVER, SubSeason.MID_WINTER, 1, 100, 100, 100)), List.of(), List.of()));
    }

    /**
     * A world that snowed under vanilla's weather for a day and a half of mid winter, stopped, and then switched to
     * regional weather at {@link #SWITCH_OVER}.
     */
    private static SnowLog upgradedLog() {
        SnowLog log = new SnowLog(0);
        log.observe(SWITCH_OVER - 2 * DAY, SubSeason.MID_WINTER, true, Set.of(Climate.TEMPERATE, Climate.COLD), CLOCKS);
        log.observe(SWITCH_OVER - DAY / 2, SubSeason.MID_WINTER, false, Set.of(Climate.TEMPERATE, Climate.COLD), CLOCKS);
        log.observe(SWITCH_OVER, SubSeason.MID_WINTER, false, Set.of(), CLOCKS, 1);
        return log;
    }

    @Test
    void theSnowOfAWorldFromBeforeRegionalWeatherCarriesOverExactly() {
        SnowLog log = upgradedLog();
        RainSource rain = RainSource.regional(winterWeather());
        double global = log.amount(SnowArea.of(Climate.TEMPERATE), SWITCH_OVER, RainSource.GLOBAL).depth();
        assertEquals(1.5 * DAY / 12_000, global, EPSILON, "a day and a half of snow: three layers' worth, capped at 3");
        for (int nodeX = -20; nodeX <= 20; nodeX += 5) {
            for (int nodeZ = -20; nodeZ <= 20; nodeZ += 5) {
                SnowArea node = SnowArea.node(Climate.TEMPERATE, nodeX, nodeZ);
                assertEquals(global, log.amount(node, SWITCH_OVER, rain).depth(), EPSILON, "node " + nodeX + ", " + nodeZ);
            }
        }
    }

    @Test
    void aNodeBuildsByTheTimeAStormSnowedOnIt() {
        SnowLog log = new SnowLog(0);
        log.observe(SWITCH_OVER, SubSeason.MID_WINTER, false, Set.of(), CLOCKS, 1);
        WeatherView weather = winterWeather();
        RainSource rain = RainSource.regional(weather);
        long end = SWITCH_OVER + 6 * DAY;
        int snowed = 0;
        for (int nodeX = 0; nodeX < 40; nodeX++) {
            SnowArea node = SnowArea.node(Climate.COLD, nodeX * 7, -nodeX * 3);
            WetSpell wet = weather.wetSpell(node.x(), node.z(), SWITCH_OVER, end);
            double expected = Math.min(CLOCKS.maxLayers(), wet.ticks(Climate.COLD) / (double) CLOCKS.snowfallTicks());
            assertEquals(expected, log.amount(node, end, rain).depth(), 1e-6);
            snowed += wet.ticks(Climate.COLD) > 0 ? 1 : 0;
        }
        assertTrue(snowed > 5 && snowed < 40, "some nodes were snowed on and some weren't: " + snowed);
    }

    @Test
    void nodesFarApartGetDifferentSnowAndOneNodeHasOneAnswer() {
        SnowLog log = new SnowLog(0);
        log.observe(SWITCH_OVER, SubSeason.MID_WINTER, false, Set.of(), CLOCKS, 1);
        RainSource rain = RainSource.regional(winterWeather());
        long end = SWITCH_OVER + 3 * DAY;
        double lowest = Double.MAX_VALUE;
        double highest = 0.0;
        for (int step = 0; step < 30; step++) {
            double depth = log.amount(SnowArea.node(Climate.TEMPERATE, step * 11, step * 5), end, rain).depth();
            lowest = Math.min(lowest, depth);
            highest = Math.max(highest, depth);
        }
        assertEquals(0.0, lowest, EPSILON, "somewhere no storm went");
        assertTrue(highest > 0.3, "somewhere one did: " + highest);
        SnowArea node = SnowArea.node(Climate.TEMPERATE, 33, 15);
        assertEquals(log.amount(node, end, rain).depth(), log.amount(node, end, RainSource.regional(winterWeather())).depth());
    }

    @Test
    void followingANodeOnGivesWhatWorkingItOutAgainGives() {
        SnowLog log = new SnowLog(0);
        log.observe(SWITCH_OVER, SubSeason.MID_WINTER, false, Set.of(), CLOCKS, 1);
        RainSource rain = RainSource.regional(winterWeather());
        SnowArea node = SnowArea.node(Climate.COLD, 4, -9);
        long tick = SWITCH_OVER;
        SnowLog.Amount followed = log.amount(node, tick, rain);
        for (int step = 0; step < 200; step++) {
            long next = tick + 20 + step * 37 % 900;
            followed = log.trendFrom(node, tick, followed, next, rain).now();
            tick = next;
        }
        assertEquals(log.amount(node, tick, rain).depth(), followed.depth(), 1e-9);
    }

    @Test
    void aNodeFollowedForwardMatchesWorkingItOutUntilItsStableTick() {
        // The server thread follows each node forward from where it was worked out, at its pace, until its stable
        // tick; every tick until then it must give exactly what working the node out again would.
        SnowLog log = new SnowLog(0);
        log.observe(SWITCH_OVER, SubSeason.MID_WINTER, false, Set.of(), CLOCKS, 1);
        RainSource rain = RainSource.regional(winterWeather());
        int checked = 0;
        for (int nodeX = 0; nodeX < 12; nodeX++) {
            SnowArea node = SnowArea.node(Climate.COLD, nodeX * 5, nodeX * -3);
            long tick = SWITCH_OVER + 1_000;
            while (tick < SWITCH_OVER + 3 * DAY) {
                SnowLog.Trend trend = log.trend(node, tick, rain);
                long until = Math.min(trend.stableUntil(), tick + 3_000);
                for (long at = tick; at <= until; at += Math.max(1, (until - tick) / 7)) {
                    double followed = Math.min(CLOCKS.maxLayers(), trend.now().depth() + trend.depthPerTick() * (at - tick));
                    assertEquals(log.amount(node, at, rain).depth(), followed, 1e-9, node + " at " + at + " from " + tick);
                    checked++;
                }
                tick = until + 1;
            }
        }
        assertTrue(checked > 500, "checked " + checked);
    }

    @Test
    void aWarmSpellLongEnoughMeltsEverythingAndTheLogFoldsThroughIt() {
        SnowLog log = new SnowLog(0);
        log.observe(SWITCH_OVER, SubSeason.MID_WINTER, false, Set.of(), CLOCKS, 1);
        log.observe(SWITCH_OVER + 8 * DAY, SubSeason.MID_SUMMER, false, Set.of(), CLOCKS, 1);
        log.observe(SWITCH_OVER + 16 * DAY, SubSeason.EARLY_WINTER, false, Set.of(), CLOCKS, 1);
        assertEquals(SWITCH_OVER + 16 * DAY, log.meltedBy(Climate.COLD, SWITCH_OVER + 20 * DAY));
        assertEquals(Long.MIN_VALUE, log.meltedBy(Climate.COLD, SWITCH_OVER + 10 * DAY));
        // Working a node out from the melt gives what working it out from the start gives.
        RainSource rain = RainSource.regional(winterWeather());
        SnowArea node = SnowArea.node(Climate.COLD, 12, 3);
        long now = SWITCH_OVER + 22 * DAY;
        double fromStart = log.amount(node, now, rain).depth();
        double fromMelt = log.trendFrom(node, SWITCH_OVER + 16 * DAY, SnowLog.Amount.NONE, now, rain).now().depth();
        assertEquals(fromStart, fromMelt, EPSILON);
        // Past the most segments, a regional log folds through the newest melt, and only there: snow that may lie
        // after it differs from node to node, so the cold segments since wait for the next melt.
        int before = log.segments().size();
        for (int step = 1; step <= SnowLog.MAX_SEGMENTS + 4; step++) {
            log.observe(SWITCH_OVER + 22 * DAY + step * 100L, SubSeason.EARLY_WINTER, false, Set.of(),
                    new SnowClocks(12_000 + step, 18_000, 12_000, 3), 1);
        }
        assertEquals(before + SnowLog.MAX_SEGMENTS + 4 - 2, log.segments().size(), "the winter and the summer folded");
        assertEquals(SWITCH_OVER + 16 * DAY, log.checkpointTick());
        assertEquals(0, log.approximateFolds());
        long later = SWITCH_OVER + 22 * DAY + (SnowLog.MAX_SEGMENTS + 10) * 100L;
        assertEquals(log.trendFrom(node, SWITCH_OVER + 16 * DAY, SnowLog.Amount.NONE, later, rain).now().depth(),
                log.amount(node, later, rain).depth(), EPSILON, "the node's snow is the same after the fold");
    }

    @Test
    void aChunkTakesTheDepthsOfItsFourNodesByHowNearItIs() {
        Long2ObjectOpenHashMap<SnowField.Node> nodes = new Long2ObjectOpenHashMap<>();
        double[][] depths = {{0.0, 0.0, 0.0}, {0.0, 1.0, 0.0}, {0.0, 2.0, 0.0}, {0.0, 3.0, 0.0}};
        int[][] places = {{0, 0}, {1, 0}, {0, 1}, {1, 1}};
        for (int index = 0; index < 4; index++) {
            nodes.put(SnowArea.key(places[index][0], places[index][1]),
                    new SnowField.Node(100, depths[index], new double[3], Long.MAX_VALUE));
        }
        SnowField field = new SnowField(nodes, 3);
        Map<Climate, Double> corner = field.depthsAt(0, 0, 100);
        Map<Climate, Double> middle = field.depthsAt(128, 128, 100);
        assertNotNull(corner);
        assertEquals(0.0, corner.get(Climate.TEMPERATE), EPSILON);
        assertEquals(1.5, middle.get(Climate.TEMPERATE), EPSILON);
        assertEquals(1.0, field.depthsAt(255.999, 0, 100).get(Climate.TEMPERATE), 1e-3);
        assertEquals(null, field.depthsAt(300, 0, 100), "a column whose nodes aren't known waits");
    }

    @Test
    void aSegmentKeepsItsWeatherWhenSaved() {
        SnowLog log = upgradedLog();
        var saved = SnowLog.CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, log).getOrThrow();
        SnowLog read = SnowLog.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, saved).getOrThrow();
        assertEquals(log.segments(), read.segments());
        assertEquals(0, read.segments().getFirst().weatherVersion());
        assertEquals(1, read.segments().getLast().weatherVersion());
        // Segments of vanilla's weather save as they did in 0.3.0, without the new field.
        assertTrue(!saved.toString().contains("weather_version:0"));
    }
}
