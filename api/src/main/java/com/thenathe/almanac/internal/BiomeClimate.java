package com.thenathe.almanac.internal;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/** One biome's seasons, from the climate table: core's {@code ClimateTable.Entry}. Safe from any thread. */
public interface BiomeClimate {
    /** Vanilla snows below this temperature. */
    float SNOW_TEMPERATURE = 0.15F;

    Holder<Biome> biome();

    /** The biome's climate in spring and fall. */
    Climate base();

    /** The climate in a sub-season: one step colder in winter and one step warmer in summer. */
    Climate seasonal(SubSeason subSeason);

    /** Its water freezes with the season ({@code #almanac:no_seasonal_ice} doesn't hold it). */
    boolean seasonalIce();

    /** The season never makes it cold enough for snow ({@code #almanac:no_seasonal_snow}). */
    boolean noSeasonalSnow();

    /** Whether rain or snow can fall here in a sub-season, when an event reaches the climate. */
    boolean canFall(SubSeason subSeason);

    /** The biome's temperature in a sub-season, before cooling with height and its temperature modifier. */
    float temperature(SubSeason subSeason);

    /** The season's temperature at a position, cooled with height as vanilla cools it. */
    float temperatureAt(SubSeason subSeason, BlockPos pos, int seaLevel);

    /** Whether the season makes a position cold enough for snow, as vanilla's {@code Biome#coldEnoughToSnow} does. */
    boolean coldEnoughToSnow(SubSeason subSeason, BlockPos pos, int seaLevel);

    /** What falls at a position in a sub-season, before the rain reach. */
    Biome.Precipitation precipitationAt(SubSeason subSeason, BlockPos pos, int seaLevel);
}
