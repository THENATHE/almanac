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
    void thunderGoesOutPlainSoTheClientScalesItOnce() {
        // Half way into a storm, for a player whose share is full and for one walking out of it.
        for (float reach : new float[] {1.0F, 0.5F, 0.0F}) {
            float worldRain = 0.5F;
            float plainThunder = 1.0F;
            PlayerWeather.Levels sent = PlayerWeather.levelsFor(worldRain, plainThunder, reach);
            assertEquals(worldRain * reach, sent.rain(), 1e-6F);
            assertEquals(plainThunder, sent.thunder(), 1e-6F, "as vanilla sends it");
            // The client's Level#getThunderLevel multiplies by its own rain level: the world's storm times the share.
            float shownThunder = sent.thunder() * sent.rain();
            assertEquals(worldRain * plainThunder * reach, shownThunder, 1e-6F, "share " + reach);
        }
    }

    @Test
    void worldWeatherComesBackFromTheNetwork() {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        WorldWeatherPayload.CODEC.encode(buffer, new WorldWeatherPayload(0.25F, 1.0F));
        assertEquals(new WorldWeatherPayload(0.25F, 1.0F), WorldWeatherPayload.CODEC.decode(buffer));
    }
}
