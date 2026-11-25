package com.thenathe.almanac.internal;

import com.thenathe.almanac.api.Climate;
import java.util.Arrays;
import java.util.Locale;

/**
 * The weather over one column at one moment, from the storms: how heavy the storm overhead is whatever it drops, and
 * how much rain (or snow) and thunder each climate gets from it. A storm brings rain only to the climates it reaches
 * (the rain shares), so the three can differ; where several storms overlap the heaviest counts.
 */
public final class WeatherHere {
    public static final WeatherHere CLEAR = new WeatherHere(0.0F, 0.0F, new float[3], new float[3]);

    private final float storm;
    private final float thunder;
    private final float[] rain;
    private final float[] thunderReached;

    /**
     * @param storm          the heaviest storm core over the column, 0 to 1, reaching any climate or none
     * @param thunder        the thunder over the column, 0 or 1, whatever the storm reaches
     * @param rain           per climate (in {@link Climate} order), the heaviest core over the column that reaches it
     * @param thunderReached per climate, the thunder of storms that reach it
     */
    public WeatherHere(float storm, float thunder, float[] rain, float[] thunderReached) {
        this.storm = storm;
        this.thunder = thunder;
        this.rain = rain.clone();
        this.thunderReached = thunderReached.clone();
    }

    /** Whether a storm's rain core is over the column at all. */
    public boolean stormy() {
        return storm > 0.0F;
    }

    public float storm() {
        return storm;
    }

    public float thunder() {
        return thunder;
    }

    /** The rain or snow a climate gets here: the heaviest core over the column that reaches it, or 0. */
    public float rain(Climate climate) {
        return rain[climate.ordinal()];
    }

    public boolean reaches(Climate climate) {
        return rain[climate.ordinal()] > 0.0F;
    }

    /** The thunder over the column from storms that reach a climate. */
    public float thunder(Climate climate) {
        return thunderReached[climate.ordinal()];
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof WeatherHere here && here.storm == storm && here.thunder == thunder
                && Arrays.equals(here.rain, rain) && Arrays.equals(here.thunderReached, thunderReached);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Float.hashCode(storm) + Float.hashCode(thunder)) + Arrays.hashCode(rain);
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "storm %.2f thunder %.0f rain hot %.2f temperate %.2f cold %.2f",
                storm, thunder, rain[0], rain[1], rain[2]);
    }
}
