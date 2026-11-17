package com.thenathe.almanac.snow;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Snow is there when you arrive in winter, but only if it has snowed since winter started (user, 2026-11-17): a
 * chunk that loads takes its layers from the climate's snow depth, which only builds while it snows there.
 */
class WinterArrivalTest {
    private static final int SNOW_HEIGHT = 1;
    private static final int SUB_SEASON = 192_000;

    /** A plains column that loads now: cold in winter, warm in its real climate, free to hold snow. */
    private static int layersOnArrival(SnowLog log, long now) {
        int depthLayers = SnowLog.layers(log.depth(Climate.TEMPERATE, now, SNOW_HEIGHT), SNOW_HEIGHT);
        SnowRules.SnowSpot plains = new SnowRules.SnowSpot(Climate.TEMPERATE, true, false, 0, 0, false, true, true);
        return SnowRules.snow(plains, depthLayers, true, SNOW_HEIGHT, false).layers();
    }

    /** Last winter snowed a full layer, then spring, summer and fall went by, partly in the rain. */
    private static SnowLog aYearOn() {
        SnowLog log = new SnowLog(0);
        log.observe(0, SubSeason.MID_WINTER, true, EnumSet.allOf(Climate.class), SNOW_HEIGHT);
        long tick = SUB_SEASON;
        for (SubSeason subSeason = SubSeason.LATE_WINTER; subSeason != SubSeason.EARLY_WINTER; subSeason = subSeason.next()) {
            log.observe(tick, subSeason, false, Set.of(), SNOW_HEIGHT);
            log.observe(tick + SUB_SEASON / 2, subSeason, true, EnumSet.allOf(Climate.class), SNOW_HEIGHT);
            tick += SUB_SEASON;
        }
        // Early winter has begun, and it hasn't snowed yet.
        log.observe(tick, SubSeason.EARLY_WINTER, false, Set.of(), SNOW_HEIGHT);
        return log;
    }

    @Test
    void noSnowOnArrivalUntilItHasSnowedThisWinter() {
        SnowLog log = aYearOn();
        long winterStart = 11L * SUB_SEASON;
        assertEquals(0.0, log.depth(Climate.TEMPERATE, winterStart, SNOW_HEIGHT));
        assertEquals(0, layersOnArrival(log, winterStart + 10_000));
    }

    @Test
    void snowOnArrivalOnceItHasSnowed() {
        SnowLog log = aYearOn();
        long snowStarts = 11L * SUB_SEASON + 10_000;
        log.observe(snowStarts, SubSeason.EARLY_WINTER, true, EnumSet.allOf(Climate.class), SNOW_HEIGHT);
        // Half a layer of snowfall (about 2,048 ticks) puts a layer on the ground.
        assertEquals(0, layersOnArrival(log, snowStarts + 1_000));
        assertEquals(1, layersOnArrival(log, snowStarts + 2_100));
        log.observe(snowStarts + 3_000, SubSeason.EARLY_WINTER, false, Set.of(), SNOW_HEIGHT);
        assertEquals(1, layersOnArrival(log, snowStarts + 50_000));
    }

    @Test
    void anEventThatMissesTemperateBringsItNoSnow() {
        SnowLog log = aYearOn();
        long snowStarts = 11L * SUB_SEASON + 10_000;
        log.observe(snowStarts, SubSeason.EARLY_WINTER, true, Set.of(Climate.COLD), SNOW_HEIGHT);
        assertEquals(0, layersOnArrival(log, snowStarts + 20_000));
    }
}
