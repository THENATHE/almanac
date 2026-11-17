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
