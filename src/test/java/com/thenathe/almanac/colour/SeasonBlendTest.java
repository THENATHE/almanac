package com.thenathe.almanac.colour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.season.SeasonClock;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class SeasonBlendTest {
    @Test
    void theMiddleDayOfEachSubSeasonIsExactlyItsOwnColour() {
        for (int daysPerSeason : new int[] {3, 9, 10, 24, 25, 100}) {
            for (SubSeason subSeason : SubSeason.values()) {
                int middle = SeasonClock.startOf(subSeason, daysPerSeason) + SeasonClock.lengthOf(subSeason, daysPerSeason) / 2;
                SeasonBlend blend = SeasonBlend.at(middle, daysPerSeason);
                assertEquals(SeasonBlend.of(subSeason), blend, daysPerSeason + " days, " + subSeason);
                assertFalse(blend.isBlended());
            }
        }
    }

    @Test
    void daysBetweenMiddlesMoveEvenlyFromOneToTheNext() {
        // 24 days per season: 8 per sub-season, the middle on its fifth day (day 4).
        assertEquals(new SeasonBlend(SubSeason.LATE_WINTER, SubSeason.EARLY_SPRING, 0.5F), SeasonBlend.at(0, 24));
        assertEquals(new SeasonBlend(SubSeason.LATE_WINTER, SubSeason.EARLY_SPRING, 0.875F), SeasonBlend.at(3, 24));
        assertEquals(SeasonBlend.of(SubSeason.EARLY_SPRING), SeasonBlend.at(4, 24));
        assertEquals(new SeasonBlend(SubSeason.EARLY_SPRING, SubSeason.MID_SPRING, 0.125F), SeasonBlend.at(5, 24));
        assertEquals(new SeasonBlend(SubSeason.EARLY_SPRING, SubSeason.MID_SPRING, 0.375F), SeasonBlend.at(7, 24));
        assertEquals(new SeasonBlend(SubSeason.EARLY_SPRING, SubSeason.MID_SPRING, 0.5F), SeasonBlend.at(8, 24));
        // The year wraps: the last day of late winter leads into early spring.
        assertEquals(new SeasonBlend(SubSeason.LATE_WINTER, SubSeason.EARLY_SPRING, 0.375F), SeasonBlend.at(95, 24));
        assertEquals(SeasonBlend.at(0, 24), SeasonBlend.at(96, 24));
        assertEquals(SeasonBlend.at(95, 24), SeasonBlend.at(-1, 24));
    }

    @Test
    void colourChangesALittleEachDayAndNeverJumps() {
        Function<SubSeason, SeasonalPalette.Step> steps = subSeason -> subSeason == SubSeason.MID_FALL
                ? SeasonalPalette.Step.color(0xc06020) : SeasonalPalette.Step.UNCHANGED;
        int base = 0x59ae30;
        int previous = SeasonBlend.at(0, 24).apply(base, steps);
        int largestDailyStep = 0;
        for (int day = 1; day <= 96; day++) {
            int colour = SeasonBlend.at(day, 24).apply(base, steps);
            largestDailyStep = Math.max(largestDailyStep, SeasonBlend.difference(previous, colour));
            previous = colour;
        }
        // From green to orange-brown over the 8 days either side of mid fall: about 1/8 of 103 a day.
        assertTrue(largestDailyStep <= 14, "largest daily step " + largestDailyStep);
        assertEquals(0xc06020, SeasonBlend.at(SeasonClock.startOf(SubSeason.MID_FALL, 24) + 4, 24).apply(base, steps));
    }

    @Test
    void blendingMatchesTheVanillaCopiesAtEveryMiddleDay() {
        // Players without Almanac get the sub-season's step applied to the biome's colour all sub-season long; a
        // client with Almanac must show that same colour on the middle day.
        SeasonalPalette palette = SeasonalPalette.bundled();
        Set<String> savannaTags = Set.of("minecraft:is_savanna");
        for (SubSeason subSeason : SubSeason.values()) {
            int middle = SeasonClock.startOf(subSeason, 24) + 4;
            for (ColourChannel channel : ColourChannel.values()) {
                for (int base : new int[] {0x59ae30, 0x91bd59, 0x6a7039, 0xbfb755}) {
                    int copy = palette.step("minecraft:savanna", savannaTags::contains, channel, subSeason).apply(base);
                    int blended = SeasonBlend.at(middle, 24).apply(base,
                            each -> palette.step("minecraft:savanna", savannaTags::contains, channel, each));
                    assertEquals(copy, blended, subSeason + " " + channel);
                }
            }
        }
    }

    @Test
    void lerpAndDifferenceWorkPerChannel() {
        assertEquals(0x808080, SeasonBlend.lerp(0x000000, 0xffffff, 0.5F));
        assertEquals(0x102030, SeasonBlend.lerp(0x102030, 0xffffff, 0.0F));
        assertEquals(40, SeasonBlend.difference(0x102030, 0x103858));
    }
}
