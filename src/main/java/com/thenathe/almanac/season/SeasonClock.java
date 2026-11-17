package com.thenathe.almanac.season;

import com.thenathe.almanac.api.SubSeason;

/**
 * Calendar arithmetic for the Almanac year. A year is four seasons of {@code daysPerSeason} days, and each season
 * splits into three sub-seasons; when the length doesn't divide by three, the early part gets the extra days.
 * Days are counted from the start of the year: day 0 is the first day of early spring.
 */
public final class SeasonClock {
    private SeasonClock() {
    }

    public static int yearLength(int daysPerSeason) {
        return 4 * daysPerSeason;
    }

    public static SubSeason subSeasonAt(int dayOfYear, int daysPerSeason) {
        int day = Math.floorMod(dayOfYear, yearLength(daysPerSeason));
        return SubSeason.byIndex((int) ((long) day * 3 / daysPerSeason));
    }

    /** The first day of a sub-season. */
    public static int startOf(SubSeason subSeason, int daysPerSeason) {
        return ceilDiv(subSeason.ordinal() * daysPerSeason, 3);
    }

    public static int lengthOf(SubSeason subSeason, int daysPerSeason) {
        int end = subSeason.ordinal() + 1 == SubSeason.COUNT ? yearLength(daysPerSeason) : startOf(subSeason.next(), daysPerSeason);
        return end - startOf(subSeason, daysPerSeason);
    }

    /** Days left in the current sub-season, counting today. */
    public static int daysLeft(int dayOfYear, int daysPerSeason) {
        SubSeason subSeason = subSeasonAt(dayOfYear, daysPerSeason);
        int day = Math.floorMod(dayOfYear, yearLength(daysPerSeason));
        return startOf(subSeason, daysPerSeason) + lengthOf(subSeason, daysPerSeason) - day;
    }

    /** Moves a day of the year forward by whole days, wrapping into the next year. */
    public static int advance(int dayOfYear, long days, int daysPerSeason) {
        return (int) Math.floorMod(dayOfYear + days, (long) yearLength(daysPerSeason));
    }

    /**
     * The same moment in a year of a different length: the same sub-season, and as far into it as the new length
     * allows.
     */
    public static int resize(int dayOfYear, int oldDaysPerSeason, int newDaysPerSeason) {
        SubSeason subSeason = subSeasonAt(dayOfYear, oldDaysPerSeason);
        int into = Math.floorMod(dayOfYear, yearLength(oldDaysPerSeason)) - startOf(subSeason, oldDaysPerSeason);
        return startOf(subSeason, newDaysPerSeason) + Math.min(into, lengthOf(subSeason, newDaysPerSeason) - 1);
    }

    private static int ceilDiv(int dividend, int divisor) {
        return -Math.floorDiv(-dividend, divisor);
    }
}
