package com.thenathe.almanac.snow;

/**
 * How fast the season's snow and ice go from loaded chunks when it turns warm: over a set stretch of game
 * time, in patches that start small and spread, instead of in one sweep.
 *
 * <p>Every column has a point in the thaw between 0 (among the first to go) and 1 (among the last), taken from smooth
 * noise over the world, so neighbouring columns go at about the same time and the snow breaks up into patches. Once
 * a chunk's thaw has passed a column's point, that column's snow and ice may go. The points are spread almost evenly,
 * so the snow cover falls at a fairly steady rate, and everything may go once the thaw is over.
 *
 * <p>Thaws are counted in game ticks, so they speed up with {@code /tick rate} and {@code /tick sprint} like every
 * other part of the season.
 */
public final class ThawPace {
    public static final long TICKS_PER_DAY = 24_000L;
    /** Broad patches about this many blocks across, broken up by smaller ones and a little grain per column. */
    private static final int PATCH_SIZE = 16;
    private static final int SMALL_PATCH_SIZE = 4;
    private static final double PATCH_WEIGHT = 0.6;
    private static final double SMALL_PATCH_WEIGHT = 0.25;
    private static final double GRAIN_WEIGHT = 0.15;

    private ThawPace() {
    }

    /**
     * How long a thaw takes: the setting, but never more than half the sub-season it starts in, so short test seasons
     * still finish their thaw in time.
     */
    public static long duration(double thawDays, int subSeasonDays) {
        double days = Math.min(thawDays, subSeasonDays / 2.0);
        return Math.max(1L, Math.round(days * TICKS_PER_DAY));
    }

    /** A chunk's thaw, from its start to its end in game time. */
    public record Window(long start, long end) {
        public static Window startingAt(long start, long duration) {
            return new Window(start, start + duration);
        }

        /** How far the thaw has got, from 0 at the start to 1 at the end. */
        public double progress(long now) {
            if (end <= start) {
                return 1.0;
            }
            return Math.clamp((now - start) / (double) (end - start), 0.0, 1.0);
        }

        public boolean isOver(long now) {
            return now >= end;
        }
    }

    /** Whether a thaw that has got this far may take the snow and ice of the column at {@code x}, {@code z}. */
    public static boolean reached(long seed, int x, int z, double progress) {
        return progress >= 1.0 || point(seed, x, z) < progress;
    }

    /** The column's point in the thaw, in [0, 1]. */
    static double point(long seed, int x, int z) {
        double noise = PATCH_WEIGHT * valueNoise(seed, x, z, PATCH_SIZE)
                + SMALL_PATCH_WEIGHT * valueNoise(seed + 1, x, z, SMALL_PATCH_SIZE)
                + GRAIN_WEIGHT * random(seed + 2, x, z);
        // Mixed noise bunches up around the middle; easing it twice spreads the points out almost evenly.
        return smoothstep(smoothstep(noise));
    }

    /** Random values on a grid of {@code cell}-block squares, blended smoothly in between. */
    private static double valueNoise(long seed, int x, int z, int cell) {
        int cellX = Math.floorDiv(x, cell);
        int cellZ = Math.floorDiv(z, cell);
        double alongX = smoothstep((x - cellX * cell + 0.5) / cell);
        double alongZ = smoothstep((z - cellZ * cell + 0.5) / cell);
        double north = lerp(alongX, random(seed, cellX, cellZ), random(seed, cellX + 1, cellZ));
        double south = lerp(alongX, random(seed, cellX, cellZ + 1), random(seed, cellX + 1, cellZ + 1));
        return lerp(alongZ, north, south);
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

    private static double smoothstep(double value) {
        return value * value * (3 - 2 * value);
    }

    private static double lerp(double amount, double from, double to) {
        return from + amount * (to - from);
    }
}
