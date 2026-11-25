package com.thenathe.almanac.weather;

import com.thenathe.almanac.api.Storm;

/**
 * What a season's storms are like: how big, how stretched, how long they live, how fast they move and how often they
 * thunder. The amount of rain doesn't change with the season (user, 2026-11-22: area rain "roughly keeps the amount
 * of rain an area would get in vanilla"), so each character's storm count is solved from its shapes to give every
 * place vanilla's share of rain ({@link #density}), and its share of thunderstorms likewise ({@link #thunderChance}).
 *
 * <p>Radii are spread evenly on a log scale, so small and large storms are both common. A storm's area is that of a
 * circle of its radius whatever its stretch.
 *
 * @param minRadius          the smallest radius, in blocks
 * @param maxRadius          the largest radius
 * @param maxStretch         the most a storm is longer than it is wide; most are rounder than this
 * @param minLife            the shortest life, in ticks
 * @param maxLife            the longest life
 * @param minSpeed           the slowest drift, in blocks a tick, before the wind's own strength
 * @param maxSpeed           the fastest drift
 * @param thunderShareOfRain the share of the time under rain that is also under thunder
 */
record StormCharacter(String name, double minRadius, double maxRadius, double maxStretch, int minLife, int maxLife,
                      double minSpeed, double maxSpeed, double thunderShareOfRain) {
    /**
     * Vanilla 26.3 rains for 12,000 to 24,000 ticks and stays clear for 12,000 to 180,000 ({@code RAIN_DURATION},
     * {@code RAIN_DELAY}): 18,000 in every 114,000 on average.
     */
    static final double VANILLA_RAIN_SHARE = 18_000.0 / 114_000.0;
    /**
     * Thunder in vanilla: 3,600 to 15,600 ticks on, 12,000 to 180,000 off, and heard only while it rains; about 9 % of
     * the time under rain.
     */
    static final double VANILLA_THUNDER_SHARE_OF_RAIN = 0.09;

    /** The mean of the radius squared, for a radius spread evenly on a log scale. */
    double meanSquaredRadius() {
        if (maxRadius <= minRadius) {
            return minRadius * minRadius;
        }
        return (maxRadius * maxRadius - minRadius * minRadius) / (2.0 * StrictMath.log(maxRadius / minRadius));
    }

    double meanLife() {
        return (minLife + maxLife) / 2.0;
    }

    /**
     * The part of its full-grown area-time a storm's rain core covers over its life: it grows over the first
     * {@link Storm#GROWTH} and shrinks over the last, so its area goes with the square of its size.
     */
    static double lifeAreaFactor() {
        return 1.0 - 4.0 * Storm.GROWTH / 3.0;
    }

    /**
     * How many storms are born in one lattice cell (a square of {@code cellSize} blocks over {@code dayTicks} ticks),
     * on average, for every place to be under a rain core {@link #VANILLA_RAIN_SHARE} of the time.
     *
     * <p>Storms are spread evenly and independently over the world and the time, so how many rain cores cover a place
     * at a moment is a Poisson number with a mean of the cores' area-time per cell's area-time, and the share of time
     * under at least one is {@code 1 - exp(-mean)}. Solving that for the mean gives the count.
     */
    double density(int cellSize, int dayTicks) {
        double cover = -StrictMath.log(1.0 - VANILLA_RAIN_SHARE);
        double coreAreaTime = Math.PI * Storm.RAIN_CORE * Storm.RAIN_CORE * meanSquaredRadius() * lifeAreaFactor()
                * meanLife();
        return cover * (double) cellSize * cellSize * dayTicks / coreAreaTime;
    }

    /**
     * The share of storms that are thunderstorms, for the time under thunder to be {@link #thunderShareOfRain} of the
     * time under rain. A thunderstorm thunders in its inner {@link Storm#THUNDER_CORE} while full grown, which is a
     * fixed part of its rain core's area-time; thunderstorms are as evenly spread as storms, so the same Poisson
     * argument solves it.
     */
    double thunderChance() {
        double rainCover = -StrictMath.log(1.0 - VANILLA_RAIN_SHARE);
        double thunderCover = -StrictMath.log(1.0 - thunderShareOfRain * VANILLA_RAIN_SHARE);
        double coreRatio = Storm.THUNDER_CORE / Storm.RAIN_CORE;
        double thunderPart = coreRatio * coreRatio * (1.0 - 2.0 * Storm.GROWTH) / lifeAreaFactor();
        return Math.min(1.0, thunderCover / (rainCover * thunderPart));
    }
}
