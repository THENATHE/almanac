package com.thenathe.almanac.colour;

import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.season.SeasonClock;
import java.util.function.Function;

/**
 * Where a day sits between two sub-seasons' colours. The palette gives each sub-season its colour at its middle
 * day; the days in between blend from one middle to the next, so colours change a little every day instead of in
 * twelve jumps.
 *
 * <p>{@code weight} is how far the day has moved from {@code from}'s colour towards {@code to}'s, from 0 up to (but
 * not including) 1. The middle day of a sub-season is exactly its own colour, which is also what players without
 * Almanac see for the whole sub-season.
 */
public record SeasonBlend(SubSeason from, SubSeason to, float weight) {
    public SeasonBlend {
        if (weight == 0.0F) {
            to = from;
        }
    }

    /** One sub-season's colour, unblended. */
    public static SeasonBlend of(SubSeason subSeason) {
        return new SeasonBlend(subSeason, subSeason, 0.0F);
    }

    /**
     * The blend for a day of the year. The middle day of a sub-season of {@code n} days is day {@code n / 2} of it,
     * and each day before or after it moves {@code 1 / n} of the way towards the neighbouring sub-season's middle.
     */
    public static SeasonBlend at(int dayOfYear, int daysPerSeason) {
        int day = Math.floorMod(dayOfYear, SeasonClock.yearLength(daysPerSeason));
        SubSeason subSeason = SeasonClock.subSeasonAt(day, daysPerSeason);
        int length = SeasonClock.lengthOf(subSeason, daysPerSeason);
        int intoSubSeason = day - SeasonClock.startOf(subSeason, daysPerSeason);
        float offset = (intoSubSeason - length / 2) / (float) length;
        if (offset < 0.0F) {
            return new SeasonBlend(subSeason.previous(), subSeason, 1.0F + offset);
        }
        return new SeasonBlend(subSeason, subSeason.next(), offset);
    }

    public boolean isBlended() {
        return from != to;
    }

    /** The blended colour of something whose own colour is {@code base}, given each sub-season's step for it. */
    public int apply(int base, Function<SubSeason, SeasonalPalette.Step> steps) {
        int start = steps.apply(from).apply(base);
        if (!isBlended()) {
            return start;
        }
        return lerp(start, steps.apply(to).apply(base), weight);
    }

    /** Mixes two RGB colours channel by channel; {@code amount} 0 gives {@code start}, 1 gives {@code end}. */
    public static int lerp(int start, int end, float amount) {
        int rgb = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            int a = start >> shift & 0xFF;
            int b = end >> shift & 0xFF;
            rgb |= Math.round(a + (b - a) * amount) << shift;
        }
        return rgb;
    }

    /** The largest difference in any channel between two RGB colours. */
    public static int difference(int first, int second) {
        int largest = 0;
        for (int shift = 16; shift >= 0; shift -= 8) {
            largest = Math.max(largest, Math.abs((first >> shift & 0xFF) - (second >> shift & 0xFF)));
        }
        return largest;
    }
}
