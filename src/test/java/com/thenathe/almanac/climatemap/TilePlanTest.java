package com.thenathe.almanac.climatemap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The order a player's map arrives in, and how the sampler is shared between players (B9, R5). */
class TilePlanTest {
    private static long distanceSquared(long key, int tileX, int tileZ) {
        long dx = ClimateTile.keyX(key) - tileX;
        long dz = ClimateTile.keyZ(key) - tileZ;
        return dx * dx + dz * dz;
    }

    @Test
    void theDefaultMapIsTheNearestTilesFirst() {
        for (int[] at : new int[][] {{0, 0}, {-7, 12}, {40_000, -40_000}}) {
            long[] plan = TilePlan.nearestFirst(at[0], at[1], 4096);
            assertEquals(241, plan.length, "the documented size at the default radius");
            assertEquals(ClimateTile.key(at[0], at[1]), plan[0], "the player's own tile comes first");
            Set<Long> distinct = new HashSet<>();
            for (int index = 0; index < plan.length; index++) {
                assertTrue(distinct.add(plan[index]), "no tile twice");
                assertTrue(TilePlan.within(ClimateTile.keyX(plan[index]) - at[0], ClimateTile.keyZ(plan[index]) - at[1], 4096));
                if (index > 0) {
                    assertTrue(distanceSquared(plan[index - 1], at[0], at[1]) <= distanceSquared(plan[index], at[0], at[1]),
                            "nearest first at " + index);
                }
            }
        }
    }

    @Test
    void theOrderIsTheSameEveryTime() {
        assertArrayEquals(TilePlan.nearestFirst(3, 4, 2048), TilePlan.nearestFirst(3, 4, 2048));
        // Ties keep row order: the four tiles next to the player's come north, west, east, south.
        long[] plan = TilePlan.nearestFirst(0, 0, 2048);
        assertArrayEquals(new long[] {ClimateTile.key(0, -1), ClimateTile.key(-1, 0), ClimateTile.key(1, 0), ClimateTile.key(0, 1)},
                new long[] {plan[1], plan[2], plan[3], plan[4]});
    }

    @Test
    void tilesAreDroppedOnlyOnceATileBeyondTheRadius() {
        int radius = 4096;
        long edge = ClimateTile.key(8, 0);
        assertTrue(TilePlan.within(8, 0, radius));
        assertFalse(TilePlan.dropped(edge, 0, 0, radius));
        // Two tiles beyond the edge: gone. One tile beyond: kept, so walking back and forth doesn't resend it.
        assertFalse(TilePlan.dropped(ClimateTile.key(9, 0), 0, 0, radius));
        assertTrue(TilePlan.dropped(ClimateTile.key(10, 0), 0, 0, radius));
    }

    @Test
    void everyPlayerGetsTheSamplerInTurn() {
        long[] first = {1, 2, 3, 4};
        long[] second = {11, 12};
        long[] third = {21, 22, 23};
        List<long[]> missing = List.of(first, second, third);
        assertArrayEquals(new long[] {1, 11, 21, 2}, TilePlan.fairOrder(missing, 0, 4));
        // Next tick the second player goes first.
        assertArrayEquals(new long[] {11, 21, 1, 12}, TilePlan.fairOrder(missing, 1, 4));
        assertArrayEquals(new long[] {1, 11, 21, 2, 12, 22, 3, 23, 4}, TilePlan.fairOrder(missing, 0, 100));
        // A tile two players both miss is sampled once.
        assertArrayEquals(new long[] {5, 6, 7}, TilePlan.fairOrder(List.of(new long[] {5, 6}, new long[] {5, 7}), 0, 4));
        assertEquals(0, TilePlan.fairOrder(missing, 0, 0).length);
        assertEquals(0, TilePlan.fairOrder(List.of(), 3, 4).length);
    }
}
