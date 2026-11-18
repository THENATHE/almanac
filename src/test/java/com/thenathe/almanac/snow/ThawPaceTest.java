package com.thenathe.almanac.snow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.serialization.JsonOps;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.season.SeasonClock;
import java.util.Arrays;
import java.util.Random;
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

    /**
     * Vanilla's snowfall as {@code ServerLevel#tickChunk} does it, on chunks of open ground: each tick every chunk makes
     * {@code randomTickSpeed} tries, and a try that rolls 0 out of 48 snows on one random column. Returns the share of
     * columns with snow after {@code ticks}.
     */
    private static double vanillaCover(int randomTickSpeed, long ticks) {
        Random random = new Random(26_3L);
        int chunks = 40;
        boolean[] snowy = new boolean[chunks * 256];
        int covered = 0;
        for (long tick = 0; tick < ticks; tick++) {
            for (int chunk = 0; chunk < chunks; chunk++) {
                for (int attempt = 0; attempt < randomTickSpeed; attempt++) {
                    if (random.nextInt(48) == 0) {
                        int column = chunk * 256 + random.nextInt(256);
                        if (!snowy[column]) {
                            snowy[column] = true;
                            covered++;
                        }
                    }
                }
            }
        }
        return covered / (double) snowy.length;
    }

    @Test
    void snowFormsAsFastAsVanillasSnowfallCoversTheGround() {
        for (int speed : new int[] {1, 3, 10}) {
            long forming = ThawPace.formingDuration(speed);
            assertEquals(ThawPace.FORMED_COVER, vanillaCover(speed, forming), 0.01, "speed " + speed);
            // Fast at first and slow at the end: well over half covered a third of the way in.
            assertEquals(0.63, vanillaCover(speed, forming / 3), 0.02, "speed " + speed);
        }
    }

    @Test
    void theFormingTimeFollowsTheRandomTickSpeed() {
        // ln(20) * 12288 / speed, rounded up.
        assertEquals(12_271L, ThawPace.formingDuration(3));
        assertEquals(36_812L, ThawPace.formingDuration(1));
        assertEquals(3_682L, ThawPace.formingDuration(10));
        assertEquals(369L, ThawPace.formingDuration(100));
        long previous = Long.MAX_VALUE;
        for (int speed = 1; speed <= 5_000; speed++) {
            long forming = ThawPace.formingDuration(speed);
            assertTrue(forming <= previous && forming >= 1, "speed " + speed);
            previous = forming;
        }
    }

    @Test
    void noRandomTicksFormsAtTheDefaultPaceAndHugeSpeedsAtOnce() {
        // Vanilla never snows on the ground then, but Almanac's own forming still brings the season's snow.
        assertEquals(ThawPace.formingDuration(ThawPace.DEFAULT_RANDOM_TICK_SPEED), ThawPace.formingDuration(0));
        assertEquals(ThawPace.formingDuration(ThawPace.DEFAULT_RANDOM_TICK_SPEED), ThawPace.formingDuration(-4));
        assertEquals(1L, ThawPace.formingDuration(36_812));
        assertEquals(1L, ThawPace.formingDuration(Integer.MAX_VALUE));
    }

    @Test
    void almanacsFormingCoversTheGroundByTheTimeVanillasDoes() {
        long forming = ThawPace.formingDuration(3);
        ThawPace.Window window = ThawPace.Window.startingAt(50_000L, forming);
        // Patches spread at an almost even pace and the window ends with everything covered, so by the time
        // vanilla's snowfall has covered 95 %, so has Almanac's forming, give or take its slower last few percent.
        assertEquals(ThawPace.FORMED_COVER, reachedShare(window.progress(50_000L + Math.round(forming * 0.95))), 0.07);
        assertEquals(1.0, reachedShare(window.progress(window.end())));
        // Not the default thaw's two days: a quarter of that.
        assertTrue(forming < ThawPace.duration(2.0, 8) / 3);
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
