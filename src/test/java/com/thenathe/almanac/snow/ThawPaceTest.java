package com.thenathe.almanac.snow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.season.SeasonClock;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class ThawPaceTest {
    private static final long SEED = 8_675_309L;
    private static final int AREA = 160;

    /** The share of columns that a thaw this far along has reached, sampled every other block over 800 x 800. */
    private static double reachedShare(double progress) {
        int reached = 0;
        int columns = 0;
        for (int x = -400; x < 400; x += 2) {
            for (int z = -400; z < 400; z += 2) {
                columns++;
                if (ThawPace.reached(SEED, x, z, progress)) {
                    reached++;
                }
            }
        }
        return reached / (double) columns;
    }

    @Test
    void theSettingSetsTheLengthInGameTicks() {
        // Default 24-day seasons: 8-day sub-seasons, so two days is well within half of one.
        assertEquals(2 * SeasonClock.TICKS_PER_DAY, ThawPace.duration(2.0, 8));
        assertEquals(6_000L, ThawPace.duration(0.25, 8));
    }

    @Test
    void aThawNeverOutlastsHalfTheSubSeason() {
        // Three-day seasons have one-day sub-seasons: a two-day thaw is cut to half a day.
        assertEquals(SeasonClock.TICKS_PER_DAY / 2, ThawPace.duration(2.0, 1));
        assertEquals(SeasonClock.TICKS_PER_DAY, ThawPace.duration(16.0, 2));
        assertEquals(4 * SeasonClock.TICKS_PER_DAY, ThawPace.duration(16.0, 8));
    }

    @Test
    void progressRunsFromStartToEnd() {
        ThawPace.Window window = ThawPace.Window.startingAt(1_000, 2_000);
        assertEquals(0.0, window.progress(500));
        assertEquals(0.0, window.progress(1_000));
        assertEquals(0.5, window.progress(2_000));
        assertEquals(1.0, window.progress(3_000));
        assertEquals(1.0, window.progress(90_000));
        assertFalse(window.isOver(2_999));
        assertTrue(window.isOver(3_000));
    }

    @Test
    void snowCoverFallsSteadilyOverTheThaw() {
        assertEquals(0.0, reachedShare(0.0));
        double previous = 0.0;
        for (int step = 1; step <= 9; step++) {
            double share = reachedShare(step / 10.0);
            // Close to an even pace (a little slower at the very start and end), and never going backwards.
            assertEquals(step / 10.0, share, 0.07, "at " + step * 10 + " %");
            assertTrue(share >= previous);
            previous = share;
        }
        assertEquals(1.0, reachedShare(1.0));
    }

    @Test
    void neighboursThawTogetherSoTheSnowGoesInPatches() {
        double neighbourGap = 0;
        double farGap = 0;
        int pairs = 0;
        for (int x = 0; x < AREA; x += 3) {
            for (int z = 0; z < AREA; z += 3) {
                double here = ThawPace.point(SEED, x, z);
                neighbourGap += Math.abs(here - ThawPace.point(SEED, x + 1, z));
                farGap += Math.abs(here - ThawPace.point(SEED, x + 61, z + 37));
                pairs++;
            }
        }
        // Next-door columns are much closer in the thaw than columns far apart, which are unrelated.
        assertTrue(neighbourGap / pairs < 0.4 * (farGap / pairs),
                "neighbours " + neighbourGap / pairs + ", far apart " + farGap / pairs);
    }

    @Test
    void theSameWorldThawsTheSameWay() {
        assertEquals(ThawPace.point(SEED, 12, -40), ThawPace.point(SEED, 12, -40));
        assertTrue(ThawPace.point(SEED, 12, -40) != ThawPace.point(SEED + 1, 12, -40));
    }

    @Test
    void earlySpringCatchUpKeepsSnowAndEverythingElseIsInstant() {
        BlockPos anywhere = new BlockPos(5, 70, -9);
        assertFalse(Pace.onLoad(SubSeason.EARLY_SPRING).mayMelt(anywhere));
        for (SubSeason subSeason : SubSeason.values()) {
            if (subSeason != SubSeason.EARLY_SPRING) {
                assertTrue(Pace.onLoad(subSeason).mayMelt(anywhere), subSeason.id());
                assertFalse(SnowRules.isThawTime(subSeason), subSeason.id());
            }
        }
        assertTrue(SnowRules.isThawTime(SubSeason.EARLY_SPRING));
        // Snow forming is never held back on load: loading chunks get their snow at once.
        for (SubSeason subSeason : SubSeason.values()) {
            assertTrue(Pace.onLoad(subSeason).mayForm(anywhere), subSeason.id());
        }
    }

    @Test
    void aWindowRoundTripsThroughTheChunkRecordsCodec() {
        ThawPace.Window window = ThawPace.Window.startingAt(-5_000L, 48_000L);
        var json = ThawPace.Window.CODEC.encodeStart(JsonOps.INSTANCE, window).getOrThrow();
        assertEquals(window, ThawPace.Window.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void aChunkWakesExactlyWhenItsNextPatchIsDue() {
        ThawPace.Window window = ThawPace.Window.startingAt(10_000L, 12_000L);
        for (int x = -200; x < 200; x += 7) {
            for (int z = -200; z < 200; z += 11) {
                double point = ThawPace.point(SEED, x, z);
                long due = window.tickReaching(point);
                assertTrue(ThawPace.reached(SEED, x, z, window.progress(due)), x + ", " + z);
                assertFalse(ThawPace.reached(SEED, x, z, window.progress(due - 1)), x + ", " + z);
                assertTrue(due > window.start() && due <= window.end());
            }
        }
        assertEquals(window.end(), window.tickReaching(1.0));
    }

    @Test
    void aWaitingChunkIsLookedAtAtMostThirtyTwoTimesAWindow() {
        ThawPace.Window window = ThawPace.Window.startingAt(0L, 32_000L);
        // A point due in a few ticks still waits a thirty-second of the window.
        assertEquals(6_000L, window.nextVisit(0.1, 5_000L));
        // A point further off is waited for.
        assertEquals(window.tickReaching(0.9), window.nextVisit(0.9, 5_000L));
        // Very short windows still move on.
        assertEquals(11L, ThawPace.Window.startingAt(0L, 5L).nextVisit(0.0, 10L));
    }

    @Test
    void aSavedThawCarriesOnAfterARestart() {
        ThawPace.Window saved = ThawPace.Window.startingAt(1_000L, 12_000L);
        ThawPace.Window startedAtThisStart = ThawPace.Window.startingAt(8_000L, 12_000L);
        // Half way through when the server stopped: it carries on, rather than starting again at the restart.
        assertEquals(saved, ThawPace.join(saved, startedAtThisStart, null, 8_000L, 12_000L));
        // A saved thaw that has ended (the chunk was away for longer) counts for nothing.
        assertEquals(startedAtThisStart, ThawPace.join(saved, startedAtThisStart, null, 14_000L, 12_000L));
    }

    @Test
    void aChunkLoadingBesideAThawJoinsIt() {
        ThawPace.Window neighbours = ThawPace.Window.startingAt(1_000L, 12_000L);
        assertEquals(neighbours, ThawPace.join(null, null, neighbours, 7_000L, 12_000L));
        // Far from anything thawing, it thaws from the moment it loads.
        assertEquals(ThawPace.Window.startingAt(7_000L, 12_000L), ThawPace.join(null, null, null, 7_000L, 12_000L));
        // Of several thaws around, the earliest still under way.
        ThawPace.Window later = ThawPace.Window.startingAt(4_000L, 12_000L);
        ThawPace.Window ended = ThawPace.Window.startingAt(-20_000L, 12_000L);
        assertEquals(neighbours, ThawPace.earliestUnderWay(Arrays.asList(later, null, ended, neighbours), 7_000L));
        assertEquals(later, ThawPace.earliestUnderWay(Arrays.asList(later, null, ended, neighbours), 13_500L));
        assertNull(ThawPace.earliestUnderWay(Arrays.asList(null, ended), 7_000L));
    }
}
