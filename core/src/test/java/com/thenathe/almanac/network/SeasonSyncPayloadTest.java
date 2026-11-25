package com.thenathe.almanac.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.AlmanacEvents.Change;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SnowClocks;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.SnowReading;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SeasonSyncPayloadTest {
    private static final SnowClocks CLOCKS = new SnowClocks(12_000, 18_000, 12_000, 3);

    /** Temperate snow of {@code depth} layers moving by {@code perTick}, and its ice. */
    public static SnowReading snow(double depth, double perTick, double ice) {
        return new SnowReading(Map.of(Climate.TEMPERATE, depth), Map.of(Climate.TEMPERATE, ice),
                Map.of(Climate.TEMPERATE, perTick), Map.of(), CLOCKS);
    }

    private static SeasonSyncPayload sync(SubSeason subSeason, int day, Set<Climate> reached, double temperateSnow) {
        return new SeasonSyncPayload(subSeason, day, 24, false, reached, 7, 1_000L, snow(temperateSnow, 0.0, 0.0));
    }

    @Test
    void theSeasonChangedEventSaysWhatChanged() {
        SeasonSyncPayload winter = sync(SubSeason.MID_WINTER, 40, Set.of(Climate.COLD), 0.25);
        assertEquals(new Change(true, true, true), winter.changeFrom(null));
        assertEquals(new Change(true, false, false), sync(SubSeason.LATE_WINTER, 40, Set.of(Climate.COLD), 0.25).changeFrom(winter));
        assertEquals(new Change(false, true, false), sync(SubSeason.MID_WINTER, 40, Set.of(), 0.25).changeFrom(winter));
        assertEquals(new Change(false, false, true), sync(SubSeason.MID_WINTER, 40, Set.of(Climate.COLD), 0.3125).changeFrom(winter));
        // A new day inside the same sub-season isn't a change listeners need to hear about.
        assertFalse(sync(SubSeason.MID_WINTER, 41, Set.of(Climate.COLD), 0.25).changeFrom(winter).any());
    }

    @Test
    void aClientFollowsTheSnowForwardFromTheSync() {
        SeasonSyncPayload sync = new SeasonSyncPayload(SubSeason.MID_WINTER, 40, 24, false, Set.of(Climate.TEMPERATE), 7,
                10_000L, snow(0.5, 1.0 / 12_000, 0.25));
        assertEquals(0.5, sync.snow(10_000L).depth(Climate.TEMPERATE), 1e-12);
        assertEquals(0.75, sync.snow(13_000L).depth(Climate.TEMPERATE), 1e-12);
        assertEquals(3.0, sync.snow(10_000_000L).depth(Climate.TEMPERATE), 1e-12, "never past the most layers");
        assertEquals(0.5, sync.snow(9_000L).depth(Climate.TEMPERATE), 1e-12, "a clock behind the sync reads the sync");
    }

    @Test
    void theServerSendsAgainForANewPaceOrASixteenthMoreButNotEachTick() {
        SeasonSyncPayload first = new SeasonSyncPayload(SubSeason.MID_WINTER, 40, 24, false, Set.of(), 7, 10_000L,
                snow(0.50, 1.0 / 12_000, 0.0));
        SeasonSyncPayload aTickLater = new SeasonSyncPayload(SubSeason.MID_WINTER, 40, 24, false, Set.of(), 7, 10_001L,
                snow(0.50 + 1.0 / 12_000, 1.0 / 12_000, 0.0));
        assertTrue(aTickLater.sameAs(first));
        SeasonSyncPayload stopped = new SeasonSyncPayload(SubSeason.MID_WINTER, 40, 24, false, Set.of(), 7, 10_001L,
                snow(0.50 + 1.0 / 12_000, 0.0, 0.0));
        assertFalse(stopped.sameAs(first), "the snowfall stopped");
        SeasonSyncPayload further = new SeasonSyncPayload(SubSeason.MID_WINTER, 40, 24, false, Set.of(), 7, 10_750L,
                snow(0.5625, 1.0 / 12_000, 0.0));
        assertFalse(further.sameAs(first), "a sixteenth further on");
    }
}
