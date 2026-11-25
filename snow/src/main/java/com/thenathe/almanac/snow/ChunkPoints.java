package com.thenathe.almanac.snow;

import it.unimi.dsi.fastutil.ints.IntArrays;
import org.jspecify.annotations.Nullable;

/**
 * One loaded chunk's column points, kept while it is loaded so the work after a small move of the shares costs a few
 * columns, not a look at all 256 of them.
 *
 * <p>The snow points ({@link ColumnPoints#scatter}) never change, so they are sorted once: the columns whose point a
 * share moved past are a run of that order, found by a binary search ({@link #markBetween}). The freezing points of
 * still water ({@link ColumnPoints#ice}) depend on where the shore is, so the chunk's work sets them
 * ({@link #setIcePoints}) and they are set again after {@link #ICE_POINTS_LAST} ticks, in case something was built
 * or dug since.
 *
 * <p>Columns are numbered {@code x | z << 4} inside the chunk; a set of them is four longs ({@link ColumnSet}).
 */
final class ChunkPoints {
    /** How long a chunk's freezing points stand before they are worked out again. */
    static final long ICE_POINTS_LAST = 1200;
    /** No freezing point: the column's top isn't still water or the season's ice. */
    static final double NOT_WATER = Double.NaN;

    private final double[] points = new double[256];
    /** The columns in order of their points, lowest first. */
    private final int[] order = new int[256];
    private final double[] sorted = new double[256];
    private double @Nullable [] icePoints;
    private long iceSetAt;

    ChunkPoints(long seed, int chunkX, int chunkZ) {
        int minX = chunkX << 4;
        int minZ = chunkZ << 4;
        for (int column = 0; column < 256; column++) {
            points[column] = ColumnPoints.scatter(seed, minX + (column & 15), minZ + (column >> 4));
            order[column] = column;
        }
        IntArrays.quickSort(order, (a, b) -> Double.compare(points[a], points[b]));
        for (int index = 0; index < 256; index++) {
            sorted[index] = points[order[index]];
        }
    }

    double point(int column) {
        return points[column];
    }

    /** Adds the columns whose snow point is in {@code [low, high)} to {@code into}. */
    void markBetween(double low, double high, ColumnSet into) {
        if (!(high > low)) {
            return;
        }
        int index = firstAtLeast(low);
        while (index < 256 && sorted[index] < high) {
            into.add(order[index]);
            index++;
        }
    }

    private int firstAtLeast(double value) {
        int low = 0;
        int high = 256;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (sorted[middle] < value) {
                low = middle + 1;
            } else {
                high = middle;
            }
        }
        return low;
    }

    /** The freezing points if they were set within {@link #ICE_POINTS_LAST} ticks of {@code gameTime}, else null. */
    double @Nullable [] icePoints(long gameTime) {
        return icePoints != null && gameTime - iceSetAt < ICE_POINTS_LAST && gameTime >= iceSetAt ? icePoints : null;
    }

    void setIcePoints(double[] points, long gameTime) {
        icePoints = points;
        iceSetAt = gameTime;
    }

    /** Adds the columns whose freezing point is in {@code [low, high)} to {@code into}. */
    static void markIceBetween(double[] icePoints, double low, double high, ColumnSet into) {
        if (!(high > low)) {
            return;
        }
        for (int column = 0; column < 256; column++) {
            double point = icePoints[column];
            if (point >= low && point < high) {
                into.add(column);
            }
        }
    }

    /** A set of a chunk's columns. */
    static final class ColumnSet {
        private final long[] bits = new long[4];

        void add(int column) {
            bits[column >> 6] |= 1L << (column & 63);
        }

        boolean contains(int column) {
            return (bits[column >> 6] & 1L << (column & 63)) != 0;
        }

        boolean isEmpty() {
            return (bits[0] | bits[1] | bits[2] | bits[3]) == 0;
        }

        int size() {
            return Long.bitCount(bits[0]) + Long.bitCount(bits[1]) + Long.bitCount(bits[2]) + Long.bitCount(bits[3]);
        }
    }
}
