package com.thenathe.almanac.snow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SnowClocks;
import com.thenathe.almanac.api.SnowLayers;
import com.thenathe.almanac.api.SubSeason;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The snow a column gets follows from the weather log and its point alone, so land gets the same snow however it
 * arrives: loaded throughout a snowstorm and its thaw, loaded part way through, or loaded after. The chunk here is a
 * plains chunk at the default clocks: a snowstorm of two and a half layers' worth, a dry spell, then the thaw.
 */
class SnowDerivationTest {
    private static final long SEED = 20261125L;
    private static final SnowClocks CLOCKS = new SnowClocks(12_000, 18_000, 12_000, 3);
    private static final SnowArea PLAINS = SnowArea.of(Climate.TEMPERATE);
    private static final long STORM_ENDS = 30_000;
    private static final long THAW_STARTS = 40_000;
    private static final long END = 100_000;

    private static SnowLog log() {
        SnowLog log = new SnowLog(0);
        log.observe(0, SubSeason.MID_WINTER, true, Set.of(Climate.TEMPERATE), CLOCKS);
        log.observe(STORM_ENDS, SubSeason.MID_WINTER, false, Set.of(Climate.TEMPERATE), CLOCKS);
        log.observe(THAW_STARTS, SubSeason.EARLY_SPRING, false, Set.of(), CLOCKS);
        return log;
    }

    /** One chunk's columns: the layers on each, the season's all of them. */
    private static final class Chunk {
        final int[] layers = new int[256];
        final ChunkPoints points = new ChunkPoints(SEED, 3, -2);
        double depthReached = -1.0;

        /** Brings the columns to the snow at {@code tick}, every one of them, as catch-up on load does. */
        void catchUp(SnowLog log, long tick) {
            bringUp(log, tick, null);
        }

        /** Brings them to {@code tick} looking only at the columns the shares moved past, as a loaded chunk does. */
        void follow(SnowLog log, long tick) {
            if (depthReached < 0.0) {
                catchUp(log, tick);
                return;
            }
            SnowLayers was = SnowLayers.of(depthReached, CLOCKS.maxLayers());
            SnowLayers is = shares(log, tick);
            ChunkPoints.ColumnSet moved = new ChunkPoints.ColumnSet();
            for (int layer = 1; layer <= CLOCKS.maxLayers(); layer++) {
                points.markBetween(Math.min(was.share(layer), is.share(layer)),
                        Math.max(was.share(layer), is.share(layer)), moved);
            }
            bringUp(log, tick, moved);
        }

        private void bringUp(SnowLog log, long tick, ChunkPoints.ColumnSet columns) {
            SnowLayers shares = shares(log, tick);
            boolean holding = SnowLog.holdsSnow(Climate.TEMPERATE, tick < THAW_STARTS ? SubSeason.MID_WINTER : SubSeason.EARLY_SPRING);
            for (int column = 0; column < 256; column++) {
                if (columns != null && !columns.contains(column)) {
                    continue;
                }
                // A plains column, cold enough through the winter and held so through the thaw while the shares last.
                SnowRules.SnowSpot spot = new SnowRules.SnowSpot(Climate.TEMPERATE, true, false, layers[column],
                        layers[column], false, true, true);
                layers[column] = SnowRules.snow(spot, shares.layersAt(points.point(column)), holding, true, 1).layers();
            }
            depthReached = shares.depth();
        }

        private static SnowLayers shares(SnowLog log, long tick) {
            return SnowLayers.of(log.amount(PLAINS, tick, RainSource.GLOBAL).depth(), CLOCKS.maxLayers());
        }
    }

    @Test
    void landGetsTheSameSnowLoadedThroughoutPartWayOrAfter() {
        SnowLog log = log();
        Chunk throughout = new Chunk();
        Chunk partWay = new Chunk();
        long[] looks = {6_000, 11_000, 13_000, 20_000, STORM_ENDS, 50_000, 61_000, 75_000, END};
        int look = 0;
        // A loaded chunk is looked at every 47 ticks or so (a round of the loaded chunks); here every 47 ticks exactly.
        for (long tick = 0; tick <= END; tick += 47) {
            throughout.follow(log, tick);
            if (tick >= 17_000) {
                partWay.follow(log, tick);
            }
            if (look < looks.length && tick + 47 > looks[look]) {
                long at = looks[look++];
                throughout.follow(log, at);
                if (at >= 17_000) {
                    partWay.follow(log, at);
                }
                Chunk after = new Chunk();
                after.catchUp(log, at);
                assertArrayEquals(after.layers, throughout.layers, "loaded throughout, at " + at);
                if (at >= 17_000) {
                    assertArrayEquals(after.layers, partWay.layers, "loaded part way through the storm, at " + at);
                }
            }
        }
        assertEquals(looks.length, look);
    }

    @Test
    void theStormLaysTheLayersTheSharesSayAndTheThawTakesThemAway() {
        SnowLog log = log();
        Chunk chunk = new Chunk();
        chunk.catchUp(log, 12_000);
        int[] count = new int[4];
        for (int layers : chunk.layers) {
            count[layers]++;
        }
        // One layer's worth of snow: 95 % of the ground white, 5 % already two deep.
        assertEquals(256 * 0.95, count[1] + count[2], 12.0);
        assertEquals(256 * 0.05, count[2], 6.0);
        chunk.catchUp(log, STORM_ENDS);
        int deepest = 0;
        long total = 0;
        for (int layers : chunk.layers) {
            deepest = Math.max(deepest, layers);
            total += layers;
        }
        assertEquals(3, deepest, "two and a half layers' worth reaches the third layer on some columns");
        assertEquals(2.5, total / 256.0, 0.15);
        chunk.catchUp(log, THAW_STARTS + 18_000);
        long afterOneThawClock = 0;
        for (int layers : chunk.layers) {
            afterOneThawClock += layers;
        }
        assertEquals(1.5, afterOneThawClock / 256.0, 0.15, "one thaw clock melts one layer");
        chunk.catchUp(log, END);
        for (int layers : chunk.layers) {
            assertEquals(0, layers);
        }
    }

    @Test
    void aSmallMoveOfTheSharesLooksAtAFewColumns() {
        // At the default snowfall clock a round of the loaded chunks every 47 ticks finds about one column a chunk.
        ChunkPoints points = new ChunkPoints(SEED, 0, 0);
        int found = 0;
        int rounds = 0;
        for (double depth = 0.0; depth < 2.0; depth += 47.0 / 12_000) {
            SnowLayers was = SnowLayers.of(depth, 3);
            SnowLayers is = SnowLayers.of(depth + 47.0 / 12_000, 3);
            ChunkPoints.ColumnSet moved = new ChunkPoints.ColumnSet();
            for (int layer = 1; layer <= 3; layer++) {
                points.markBetween(Math.min(was.share(layer), is.share(layer)), Math.max(was.share(layer), is.share(layer)), moved);
            }
            found += moved.size();
            rounds++;
        }
        double perRound = found / (double) rounds;
        assertTrue(perRound > 0.5 && perRound < 1.5, "about one column a chunk a round: " + perRound);
    }
}
