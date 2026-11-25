package com.thenathe.almanac.snow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Ice by an ice cover (0.3.0, the full fix of the streaks found on 2026-11-24): a pond's columns have freezing points
 * from their distance to the shore with a scatter, and freeze as the climate's ice cover passes them. Which columns are
 * frozen depends on the cover and the points alone, never on the order columns are looked at, so there are no rows,
 * and loaded water and water that loads freeze alike.
 */
class IceCoverTest {
    private static final long SEED = 99L;
    private static final int SIZE = 24;
    /** A 14 by 14 pond in the middle of a 24 by 24 grid of land. */
    private static final int POND_FROM = 5;
    private static final int POND_TO = 18;

    private static boolean[] pond() {
        boolean[] pond = new boolean[SIZE * SIZE];
        for (int z = POND_FROM; z <= POND_TO; z++) {
            for (int x = POND_FROM; x <= POND_TO; x++) {
                pond[z * SIZE + x] = true;
            }
        }
        return pond;
    }

    private static double[] icePoints() {
        int[] distance = ColumnPoints.shoreDistances(pond(), SIZE);
        double[] points = new double[SIZE * SIZE];
        for (int cell = 0; cell < points.length; cell++) {
            points[cell] = distance[cell] == 0 ? Double.NaN : ColumnPoints.ice(SEED, cell % SIZE, cell / SIZE, distance[cell]);
        }
        return points;
    }

    @Test
    void theShoreIsOneStepAwayAndTheMiddleAtTheReach() {
        int[] distance = ColumnPoints.shoreDistances(pond(), SIZE);
        assertEquals(0, distance[0]);
        assertEquals(1, distance[POND_FROM * SIZE + POND_FROM], "a corner of the pond");
        assertEquals(1, distance[10 * SIZE + POND_FROM]);
        assertEquals(2, distance[10 * SIZE + POND_FROM + 1]);
        assertEquals(3, distance[10 * SIZE + POND_FROM + 2]);
        assertEquals(ColumnPoints.SHORE_REACH, distance[11 * SIZE + 11], "the middle");
        // Water to the edge of the grid (a neighbouring chunk that isn't loaded) has no shore there.
        boolean[] open = new boolean[SIZE * SIZE];
        java.util.Arrays.fill(open, true);
        assertEquals(ColumnPoints.SHORE_REACH, ColumnPoints.shoreDistances(open, SIZE)[0]);
    }

    @Test
    void pointsRiseFromTheShoreWithARaggedEdge() {
        double[] byRing = new double[ColumnPoints.SHORE_REACH + 1];
        int[] count = new int[ColumnPoints.SHORE_REACH + 1];
        int[] distance = ColumnPoints.shoreDistances(pond(), SIZE);
        double[] points = icePoints();
        for (int cell = 0; cell < points.length; cell++) {
            if (distance[cell] > 0) {
                assertTrue(points[cell] >= 0.0 && points[cell] < 1.0);
                byRing[distance[cell]] += points[cell];
                count[distance[cell]]++;
            }
        }
        for (int ring = 2; ring <= ColumnPoints.SHORE_REACH; ring++) {
            assertTrue(byRing[ring] / count[ring] > byRing[ring - 1] / count[ring - 1], "ring " + ring + " freezes later");
        }
        // The rings overlap: some of the second ring freezes before some of the shore.
        double latestShore = 0.0;
        double earliestSecond = 1.0;
        for (int cell = 0; cell < points.length; cell++) {
            if (distance[cell] == 1) {
                latestShore = Math.max(latestShore, points[cell]);
            } else if (distance[cell] == 2) {
                earliestSecond = Math.min(earliestSecond, points[cell]);
            }
        }
        assertTrue(earliestSecond < latestShore);
    }

    @Test
    void aPondFreezesFromTheShoreInwardWithNoRowsWhateverTheOrder() {
        double[] points = icePoints();
        int[] distance = ColumnPoints.shoreDistances(pond(), SIZE);
        double cover = 0.35;
        int eastWest = 0;
        int northSouth = 0;
        int shoreFrozen = 0;
        int shore = 0;
        int middleFrozen = 0;
        for (int z = POND_FROM; z <= POND_TO; z++) {
            for (int x = POND_FROM; x <= POND_TO; x++) {
                boolean frozen = SnowRules.freezes(true, points[z * SIZE + x] < cover);
                if (distance[z * SIZE + x] == 1) {
                    shore++;
                    shoreFrozen += frozen ? 1 : 0;
                } else if (distance[z * SIZE + x] == ColumnPoints.SHORE_REACH && frozen) {
                    middleFrozen++;
                }
                if (frozen && x < POND_TO && points[z * SIZE + x + 1] < cover) {
                    eastWest++;
                }
                if (frozen && z < POND_TO && points[(z + 1) * SIZE + x] < cover) {
                    northSouth++;
                }
            }
        }
        assertTrue(shoreFrozen > shore / 2, "most of the shore first: " + shoreFrozen + " of " + shore);
        assertEquals(0, middleFrozen, "the middle last");
        // Before 0.1.7 ice ran east in rows; neighbours frozen side by side are as likely either way now.
        double ratio = eastWest / (double) Math.max(1, northSouth);
        assertTrue(ratio > 0.6 && ratio < 1.6, "east-west " + eastWest + " against north-south " + northSouth);
    }

    @Test
    void theIceCoverFreezesTheWholePondAndThawsItTheOtherWay() {
        double[] points = icePoints();
        int frozenBefore = 0;
        for (int step = 0; step <= 20; step++) {
            double cover = step / 20.0;
            int frozen = 0;
            for (double point : points) {
                if (!Double.isNaN(point) && point < cover) {
                    frozen++;
                }
            }
            assertTrue(frozen >= frozenBefore, "only more as the cover grows");
            frozenBefore = frozen;
        }
        assertEquals(14 * 14, frozenBefore, "all of it at a full cover");
        // In a thaw the cover falls and the season's ice goes where it no longer reaches: the middle first.
        assertTrue(SnowRules.iceMelts(true, true, true, false, false));
    }
}
