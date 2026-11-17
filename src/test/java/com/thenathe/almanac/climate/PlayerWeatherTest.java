package com.thenathe.almanac.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.network.WorldWeatherPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** Each player's share of the dimension's rain and thunder (user, 2026-11-17, for sandstorms). */
class PlayerWeatherTest {
    @Test
    void whereNothingCanFallPlayersGetTheWholeWeather() {
        // A desert in summer: nothing can fall, so the sky darkens as in plain Minecraft, reached or not.
        assertEquals(1.0F, PlayerWeather.share(false, false));
        assertEquals(1.0F, PlayerWeather.share(false, true));
        // Rain could fall here: only a reached climate gets it; otherwise the sky stays clear.
        assertEquals(1.0F, PlayerWeather.share(true, true));
        assertEquals(0.0F, PlayerWeather.share(true, false));
    }

    @Test
    void theDryPlacesAreTheDryCells() {
        // The copies have precipitation exactly where the weather table isn't DRY.
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

    @Test
    void worldWeatherComesBackFromTheNetwork() {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        WorldWeatherPayload.CODEC.encode(buffer, new WorldWeatherPayload(0.25F, 1.0F));
        assertEquals(new WorldWeatherPayload(0.25F, 1.0F), WorldWeatherPayload.CODEC.decode(buffer));
    }
}
