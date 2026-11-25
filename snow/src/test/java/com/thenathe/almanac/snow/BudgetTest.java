package com.thenathe.almanac.snow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.SnowLayers;
import org.junit.jupiter.api.Test;

/**
 * Loaded land's snow work comes from the column budget: a round of the loaded chunks starts when the shares have moved
 * {@link SnowUpdates#ROUND_STEP}, and a round in a snowstorm costs little enough to keep up within the default budget.
 */
class BudgetTest {
    private static final int DEFAULT_COLUMNS_PER_TICK = 128;

    @Test
    void aRoundStartsOnceTheSnowHasMovedAStepOrComeToAnEnd() {
        assertFalse(SnowUpdates.movedEnough(0.5, 0.5, 3.0));
        assertFalse(SnowUpdates.movedEnough(0.5, 0.5 + SnowUpdates.ROUND_STEP / 2, 3.0));
        assertTrue(SnowUpdates.movedEnough(0.5, 0.5 + SnowUpdates.ROUND_STEP, 3.0));
        // The last little way to none or to the most layers still counts, so no column is left waiting.
        assertTrue(SnowUpdates.movedEnough(0.0001, 0.0, 3.0));
        assertTrue(SnowUpdates.movedEnough(2.9999, 3.0, 3.0));
    }

    @Test
    void aRoundOf2000ChunksInASnowstormFitsTheDefaultBudget() {
        // Three players' worth of loaded land (the 2026-11-24 measurement had 1,875 to 2,128 chunks), at the default
        // clock: the shares move a step in about 47 ticks, and each chunk's look finds about one column.
        int chunks = 2000;
        double step = SnowUpdates.ROUND_STEP;
        int changed = 0;
        for (int chunk = 0; chunk < chunks; chunk++) {
            ChunkPoints points = new ChunkPoints(42L, chunk % 50, chunk / 50);
            SnowLayers was = SnowLayers.of(0.4, 3);
            SnowLayers is = SnowLayers.of(0.4 + step, 3);
            ChunkPoints.ColumnSet moved = new ChunkPoints.ColumnSet();
            points.markBetween(was.cover(), is.cover(), moved);
            changed += moved.size();
        }
        long cost = (long) chunks * SnowUpdates.LOOK + (long) changed * SnowUpdates.CHANGED_COLUMN;
        long budgetPerTick = (long) DEFAULT_COLUMNS_PER_TICK * SnowUpdates.CHANGED_COLUMN;
        double ticks = cost / (double) budgetPerTick;
        double ticksBetweenRounds = step * 12_000;
        assertTrue(ticks < ticksBetweenRounds, "a round takes " + ticks + " ticks of budget, rounds come every "
                + ticksBetweenRounds);
    }
}
