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

    /**
     * The furthest the cloud of a storm of {@code season} born at most {@code age} weather ticks ago reaches from the
     * place it was born, in blocks: what a question about a place need look through, for a lattice day that began
     * {@code age} ticks before it. Never more than {@link #reach}; only which cells are looked at depends on it.
     */
    double reach(SubSeason.Season season, double age);

    /** The longest any storm lives, in ticks. */
    int longestLife();

    /** What storms are like in a season. */
    StormCharacter character(SubSeason.Season season);

    /**
     * Whether the storm count follows the share of time under rain an era records ({@code WeatherRecord.Era#rainShare});
     * version 1 has vanilla's share built in.
     */
    boolean followsRainShare();

    /**
     * The storms born in lattice cell {@code (i, j)} on lattice day {@code day}, when its season is {@code season} and
     * every place is to be under rain {@code rainShare} percent of the time (ignored where {@link #followsRainShare} is
     * false).
     */
    Storm[] cell(long seed, int i, int j, long day, SubSeason.Season season, int rainShare);

    Wind wind(long seed, double weatherTick);

    static StormMaths of(int version) {
        if (version == StormMathsV1.VERSION) {
            return StormMathsV1.INSTANCE;
        }
        if (version == StormMathsV2.VERSION) {
            return StormMathsV2.INSTANCE;
        }
        throw new IllegalArgumentException("no storm maths version " + version);
    }

    /** Whether this Almanac has the storm maths a record names. */
    static boolean known(int version) {
        return version == StormMathsV1.VERSION || version == StormMathsV2.VERSION;
    }
}
