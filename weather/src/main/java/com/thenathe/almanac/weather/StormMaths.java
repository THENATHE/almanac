package com.thenathe.almanac.weather;

import com.thenathe.almanac.api.Storm;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.api.Wind;

/**
 * One version of the storm maths: the lattice storms are born in, what each is like, and the wind. A released
 * version never changes, so a world's past weather (and the snow it left) stays what it was; a later Almanac that
 * wants other storms adds a version, and the weather record says which version each stretch of time ran on
 * ({@code WeatherRecord.Era#version}).
 */
interface StormMaths {
    int version();

    /** The side of a lattice cell, in blocks. */
    int cellSize();

    /** The length of a lattice cell, in weather ticks. */
    int dayTicks();

    /** The furthest any storm's cloud reaches from the place it was born, in blocks. */
    double reach();

    /** The longest any storm lives, in ticks. */
    int longestLife();

    /** What storms are like in a season. */
    StormCharacter character(SubSeason.Season season);

    /** The storms born in lattice cell {@code (i, j)} on lattice day {@code day}, when its season is {@code season}. */
    Storm[] cell(long seed, int i, int j, long day, SubSeason.Season season);

    Wind wind(long seed, double weatherTick);

    static StormMaths of(int version) {
        if (version == StormMathsV1.VERSION) {
            return StormMathsV1.INSTANCE;
        }
        throw new IllegalArgumentException("no storm maths version " + version);
    }

    /** Whether this Almanac has the storm maths a record names. */
    static boolean known(int version) {
        return version == StormMathsV1.VERSION;
    }
}
