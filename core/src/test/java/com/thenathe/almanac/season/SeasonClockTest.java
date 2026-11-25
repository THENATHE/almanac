package com.thenathe.almanac.season;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.thenathe.almanac.api.SubSeason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SeasonClockTest {
    @Test
    void defaultYearHasEightDaysPerSubSeason() {
        assertEquals(96, SeasonClock.yearLength(24));
        assertEquals(SubSeason.EARLY_SPRING, SeasonClock.subSeasonAt(0, 24));
        assertEquals(SubSeason.EARLY_SPRING, SeasonClock.subSeasonAt(7, 24));
        assertEquals(SubSeason.MID_SPRING, SeasonClock.subSeasonAt(8, 24));
        assertEquals(SubSeason.MID_WINTER, SeasonClock.subSeasonAt(80, 24));
        assertEquals(SubSeason.LATE_WINTER, SeasonClock.subSeasonAt(95, 24));
        assertEquals(SubSeason.EARLY_SPRING, SeasonClock.subSeasonAt(96, 24));
        assertEquals(8, SeasonClock.daysLeft(0, 24));
        assertEquals(1, SeasonClock.daysLeft(7, 24));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5, 10, 11, 24, 25, 100, 1200})
    void subSeasonsCoverTheYearWithoutGaps(int daysPerSeason) {
        int day = 0;
        for (SubSeason subSeason : SubSeason.values()) {
            assertEquals(day, SeasonClock.startOf(subSeason, daysPerSeason), subSeason.id());
            int length = SeasonClock.lengthOf(subSeason, daysPerSeason);
            for (int into = 0; into < length; into++) {
                assertEquals(subSeason, SeasonClock.subSeasonAt(day + into, daysPerSeason), "day " + (day + into));
                assertEquals(length - into, SeasonClock.daysLeft(day + into, daysPerSeason));
            }
            day += length;
        }
        assertEquals(SeasonClock.yearLength(daysPerSeason), day);
    }

    @Test
    void earlyPartGetsTheExtraDays() {
        assertEquals(4, SeasonClock.lengthOf(SubSeason.EARLY_SPRING, 10));
        assertEquals(3, SeasonClock.lengthOf(SubSeason.MID_SPRING, 10));
        assertEquals(3, SeasonClock.lengthOf(SubSeason.LATE_SPRING, 10));
    }

    @Test
    void advanceWrapsIntoTheNextYear() {
        assertEquals(1, SeasonClock.advance(95, 2, 24));
        assertEquals(95, SeasonClock.advance(95, 96 * 1000L, 24));
        assertEquals(10, SeasonClock.advance(0, 10, 24));
    }

    private static final long DAY = SeasonClock.TICKS_PER_DAY;

    private static SeasonClock.Reading step(SeasonClock.Reading saved, long clockTime, int daysPerSeason, boolean paused) {
        return SeasonClock.step(saved, clockTime, daysPerSeason, SubSeason.MID_FALL, paused);
    }

    @Test
    void theFirstReadingStartsTheYearWhereTheSettingSays() {
        SeasonClock.Reading fresh = new SeasonClock.Reading(0, 24, SeasonClock.NOT_STARTED);
        SeasonClock.Reading started = step(fresh, 5 * DAY + 100, 24, false);
        assertEquals(new SeasonClock.Reading(SeasonClock.startOf(SubSeason.MID_FALL, 24), 24, 5), started);
        // A pause doesn't stop the year starting, and a world from before Almanac starts the same way.
        assertEquals(started, step(fresh, 5 * DAY, 24, true));
        assertEquals(new SeasonClock.Reading(SeasonClock.startOf(SubSeason.MID_FALL, 3), 3, 5),
                step(new SeasonClock.Reading(40, 24, SeasonClock.NOT_STARTED), 5 * DAY, 3, false));
    }

    @Test
    void eachNewDayMovesTheYearOn() {
        SeasonClock.Reading saved = new SeasonClock.Reading(10, 24, 7);
        assertEquals(saved, step(saved, 7 * DAY + 23_999, 24, false));
        assertEquals(new SeasonClock.Reading(11, 24, 8), step(saved, 8 * DAY, 24, false));
        // Sleeping or /time add skip several days at once, wrapping into the next year.
        assertEquals(new SeasonClock.Reading(SeasonClock.advance(10, 100, 24), 24, 107), step(saved, 107 * DAY, 24, false));
    }

    @Test
    void settingTheTimeBackDoesNotTurnTheYearBack() {
        SeasonClock.Reading saved = new SeasonClock.Reading(30, 24, 50);
        SeasonClock.Reading back = step(saved, 20 * DAY, 24, false);
        assertEquals(new SeasonClock.Reading(30, 24, 20), back);
        // The next day after that counts from where the clock now is.
        assertEquals(new SeasonClock.Reading(31, 24, 21), step(back, 21 * DAY, 24, false));
        // Negative clock times (time set back past zero) work the same way.
        assertEquals(new SeasonClock.Reading(30, 24, -1), step(saved, -1, 24, false));
    }

    @Test
    void aPauseHoldsTheYearButKeepsTrackOfTheClock() {
        SeasonClock.Reading saved = new SeasonClock.Reading(30, 24, 50);
        SeasonClock.Reading paused = step(saved, 55 * DAY, 24, true);
        assertEquals(new SeasonClock.Reading(30, 24, 55), paused);
        // Resuming doesn't catch up on the paused days.
        assertEquals(new SeasonClock.Reading(31, 24, 56), step(paused, 56 * DAY, 24, false));
    }

    @Test
    void aNewSeasonLengthKeepsTheMomentOfTheYear() {
        // Day 4 of mid winter in 24-day seasons.
        SeasonClock.Reading saved = new SeasonClock.Reading(84, 24, 9);
        SeasonClock.Reading shorter = step(saved, 9 * DAY, 12, false);
        assertEquals(new SeasonClock.Reading(SeasonClock.resize(84, 24, 12), 12, 9), shorter);
        assertEquals(SubSeason.MID_WINTER, SeasonClock.subSeasonAt(shorter.dayOfYear(), 12));
        // A new length and a new day in the same step: resized first, then moved on in the new length.
        assertEquals(new SeasonClock.Reading(SeasonClock.advance(SeasonClock.resize(84, 24, 12), 1, 12), 12, 10),
                step(saved, 10 * DAY, 12, false));
    }

    @Test
    void resizeKeepsTheSubSeason() {
        // Day 4 of mid winter (day 84) in a 24-day season.
        int resized = SeasonClock.resize(84, 24, 12);
        assertEquals(SubSeason.MID_WINTER, SeasonClock.subSeasonAt(resized, 12));
        assertEquals(SeasonClock.startOf(SubSeason.MID_WINTER, 12) + 3, resized);
        // Past the end of the shorter sub-season it stops on its last day.
        int shrunk = SeasonClock.resize(87, 24, 3);
        assertEquals(SubSeason.MID_WINTER, SeasonClock.subSeasonAt(shrunk, 3));
        for (int day = 0; day < 96; day++) {
            assertEquals(SeasonClock.subSeasonAt(day, 24), SeasonClock.subSeasonAt(SeasonClock.resize(day, 24, 7), 7));
        }
    }
}
