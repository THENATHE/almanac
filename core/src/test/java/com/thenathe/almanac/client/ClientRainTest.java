package com.thenathe.almanac.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.WeatherKind;
import org.junit.jupiter.api.Test;

/** The player's share of the world's rain where they stand (user, 2026-11-17, for sandstorms), now on the client. */
class ClientRainTest {
    @Test
    void whereNothingCanFallPlayersGetTheWholeWeather() {
        // A desert in summer: nothing can fall, so the sky darkens as in plain Minecraft, reached or not.
        assertEquals(1.0F, ClientRain.share(false, false));
        assertEquals(1.0F, ClientRain.share(false, true));
        // Rain could fall here: only a reached climate gets it; otherwise the sky stays clear.
        assertEquals(1.0F, ClientRain.share(true, true));
        assertEquals(0.0F, ClientRain.share(true, false));
    }

    @Test
    void theDryPlacesAreTheDryCells() {
        for (SubSeason subSeason : SubSeason.values()) {
            boolean summer = subSeason.season() == SubSeason.Season.SUMMER;
            boolean winter = subSeason.season() == SubSeason.Season.WINTER;
            assertEquals(!winter, WeatherKind.of(Climate.HOT, subSeason) == WeatherKind.DRY, "HOT " + subSeason);
            assertEquals(subSeason == SubSeason.MID_SUMMER, WeatherKind.of(Climate.TEMPERATE, subSeason) == WeatherKind.DRY,
                    "TEMPERATE " + subSeason);
            assertFalse(WeatherKind.of(Climate.COLD, subSeason) == WeatherKind.DRY, "COLD " + subSeason);
            assertTrue(summer || WeatherKind.of(Climate.TEMPERATE, subSeason) != WeatherKind.DRY);
        }
    }
}
