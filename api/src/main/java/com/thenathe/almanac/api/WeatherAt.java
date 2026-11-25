package com.thenathe.almanac.api;

import net.minecraft.world.level.biome.Biome;

/**
 * The weather at one place under Almanac's regional weather (since 0.4.0), from the storms over it.
 *
 * @param storm   how heavy the storm overhead is, 0 to 1, whatever falls from it: 0 under a clear sky. Where nothing
 *                can fall this sub-season (a desert outside winter) the storm still passes over, dark and dry
 * @param rain    the rain or snow level at the place, 0 to 1, as the game's rain level would be there: the storm's,
 *                where something falls from it here (the storm reaches the climate), and 0 under a storm that passes
 *                this climate by. Where nothing can fall this sub-season it is the storm's level, as vanilla darkens
 *                the sky with nothing falling (a sandstorm's cue)
 * @param thunder the thunder level at the place, 0 to 1; never more than {@code rain}
 * @param falling what falls on the place now: {@code RAIN} or {@code SNOW} where it is raining or snowing on it,
 *                with the sky open above it, and {@code NONE} otherwise, the same answer as
 *                {@code Level#precipitationAt}
 * @since 0.4.0
 */
public record WeatherAt(float storm, float rain, float thunder, Biome.Precipitation falling) {
    public static final WeatherAt CLEAR = new WeatherAt(0.0F, 0.0F, 0.0F, Biome.Precipitation.NONE);
}
