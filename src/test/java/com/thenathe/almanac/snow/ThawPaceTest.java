package com.thenathe.almanac.snow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.SubSeason;
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
        assertEquals(2 * ThawPace.TICKS_PER_DAY, ThawPace.duration(2.0, 8));
        assertEquals(6_000L, ThawPace.duration(0.25, 8));
    }

    @Test
    void aThawNeverOutlastsHalfTheSubSeason() {
        // Three-day seasons have one-day sub-seasons: a two-day thaw is cut to half a day.
        assertEquals(ThawPace.TICKS_PER_DAY / 2, ThawPace.duration(2.0, 1));
        assertEquals(ThawPace.TICKS_PER_DAY, ThawPace.duration(16.0, 2));
        assertEquals(4 * ThawPace.TICKS_PER_DAY, ThawPace.duration(16.0, 8));
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
        assertFalse(Thaw.onLoad(SubSeason.EARLY_SPRING).reached(anywhere));
        for (SubSeason subSeason : SubSeason.values()) {
            if (subSeason != SubSeason.EARLY_SPRING) {
                assertTrue(Thaw.onLoad(subSeason).reached(anywhere), subSeason.id());
                assertFalse(SnowRules.isThawTime(subSeason), subSeason.id());
            }
        }
        assertTrue(SnowRules.isThawTime(SubSeason.EARLY_SPRING));
    }
}
