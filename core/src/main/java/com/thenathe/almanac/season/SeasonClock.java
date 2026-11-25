package com.thenathe.almanac.season;

import com.thenathe.almanac.api.SubSeason;

/**
 * Calendar arithmetic for the Almanac year. A year is four seasons of {@code daysPerSeason} days, and each season
 * splits into three sub-seasons; when the length doesn't divide by three, the early part gets the extra days.
 * Days are counted from the start of the year: day 0 is the first day of early spring.
 */
public final class SeasonClock {
    /** One in-game day, in game ticks. */
    public static final long TICKS_PER_DAY = 24_000L;
    /** The clock hasn't seen a day yet: a new world, or a world from before Almanac was installed. */
    public static final long NOT_STARTED = Long.MIN_VALUE;

    private SeasonClock() {
    }

    /**
     * Where the year stands: the day of the year, the season length it is counted in, and the last day of the world's
     * clock the year was moved on for ({@link #NOT_STARTED} before the first).
     */
    public record Reading(int dayOfYear, int daysPerSeason, long lastClockDay) {
        public boolean started() {
            return lastClockDay != NOT_STARTED;
        }
    }

    /**
     * Moves the year on to the world's clock at {@code clockTime}, in ticks. The year follows the clock's day count, so
     * sleeping moves it forward:
     * <ul>
     *   <li>a new season length keeps the same moment of the year ({@link #resize});</li>
     *   <li>the first reading starts the year at {@code start};</li>
     *   <li>each new day moves the year on a day, unless the seasons are paused;</li>
     *   <li>setting the time back doesn't turn the year back; it only moves where the next day starts.</li>
     * </ul>
     */
    public static Reading step(Reading saved, long clockTime, int daysPerSeason, SubSeason start, boolean paused) {
        long clockDay = Math.floorDiv(clockTime, TICKS_PER_DAY);
        if (!saved.started()) {
            return new Reading(startOf(start, daysPerSeason), daysPerSeason, clockDay);
        }
        int day = saved.dayOfYear();
        if (saved.daysPerSeason() != daysPerSeason) {
            day = resize(day, saved.daysPerSeason(), daysPerSeason);
        }
        if (clockDay > saved.lastClockDay() && !paused) {
            day = advance(day, clockDay - saved.lastClockDay(), daysPerSeason);
        }
        return new Reading(day, daysPerSeason, clockDay);
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
