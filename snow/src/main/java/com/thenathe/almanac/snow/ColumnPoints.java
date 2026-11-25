package com.thenathe.almanac.snow;

/**
 * The random points that scatter the season's snow and ice over the land, one column at a time.
 *
 * <p>Every column has a point between 0 and 1 in a snowfall ({@link #scatter}), a random value of its own, evenly
 * spread: a column holds as many layers as there are layers whose share is past its point ({@code SnowLayers}). So
 * snow comes on scattered single columns as vanilla's snowfall lays it, deepens on the columns that whitened first,
 * and goes the same way in a thaw, the last to come first to go. The points are 0.1.5's, so land keeps the same snowy
 * columns.
 *
 * <p>Still water has a freezing point of its own ({@link #ice}, from 0.3.0): mostly how far the column is from the
 * shore, with a scatter of its own on top, so ponds freeze from the edge inward with a ragged edge and thaw the other
 * way, the same in loaded chunks, in chunks that load and on far terrain.
 */
public final class ColumnPoints {
    /** How far from the shore, in blocks, still counts towards a later freezing point; further out is all the same. */
    static final int SHORE_REACH = 4;
    /** How far the scatter spreads a freezing point, in rings: more than one, so the rings blend into each other. */
    private static final double ICE_SCATTER = 1.5;

    private ColumnPoints() {
    }

    /**
     * The column's point in a snowfall, in [0, 1): evenly spread and unrelated to its neighbours', so snow comes on
     * scattered single columns as vanilla's does, and the share of columns below a cover is that cover.
     */
    static double scatter(long seed, int x, int z) {
        return random(seed + 3, x, z);
    }

    /**
     * A still-water column's freezing point, in [0, 1): it freezes once its climate's ice cover is past it.
     *
     * @param shoreDistance how many steps the column is from the nearest block that isn't the pond's surface: 1 beside
     *                      the shore; counted up to {@link #SHORE_REACH}
     */
    static double ice(long seed, int x, int z, int shoreDistance) {
        int rings = Math.clamp(shoreDistance, 1, SHORE_REACH) - 1;
        double point = (rings + ICE_SCATTER * random(seed + 5, x, z)) / (SHORE_REACH - 1 + ICE_SCATTER);
        return Math.min(point, Math.nextDown(1.0));
    }

    /**
     * How many steps each cell of a square grid is from the nearest cell that isn't pond, sideways, counted up to
     * {@link #SHORE_REACH}: 0 for shore, 1 for pond beside it, and so on; a cell further out than the reach is at the
     * reach. Beyond the grid's edge counts as pond, so only the shore inside the grid counts.
     *
     * @param pond whether each cell (row by row, {@code size} a row) is part of a pond
     */
    static int[] shoreDistances(boolean[] pond, int size) {
        int[] distance = new int[size * size];
        for (int cell = 0; cell < distance.length; cell++) {
            distance[cell] = pond[cell] ? SHORE_REACH : 0;
        }
        for (int step = 1; step < SHORE_REACH; step++) {
            for (int z = 0; z < size; z++) {
                for (int x = 0; x < size; x++) {
                    int cell = z * size + x;
                    if (distance[cell] == SHORE_REACH && besideDistance(distance, size, x, z, step - 1)) {
                        distance[cell] = step;
                    }
                }
            }
        }
        return distance;
    }

    private static boolean besideDistance(int[] distance, int size, int x, int z, int value) {
        return (x > 0 && distance[z * size + x - 1] == value) || (x < size - 1 && distance[z * size + x + 1] == value)
                || (z > 0 && distance[(z - 1) * size + x] == value) || (z < size - 1 && distance[(z + 1) * size + x] == value);
    }

    /** A random value in [0, 1) for a grid point, the same every time for the same seed. */
    private static double random(long seed, int x, int z) {
        long mixed = mix(seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL);
        return (mixed >>> 11) * 0x1.0p-53;
    }

    /** SplitMix64's finaliser: every input bit affects every output bit. */
    private static long mix(long value) {
        value += 0x9E3779B97F4A7C15L;
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }
}
