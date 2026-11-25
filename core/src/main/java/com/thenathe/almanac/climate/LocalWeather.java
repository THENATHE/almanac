package com.thenathe.almanac.climate;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.WeatherAt;
import com.thenathe.almanac.internal.Services;
import com.thenathe.almanac.internal.WeatherHere;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The weather at a place under regional weather (from 0.4.0), where vanilla asks its one weather for the whole world:
 * the storms over the place (the weather module's) with what the season lets fall there (the climate table's).
 *
 * <ul>
 *   <li>Where rain or snow can fall this sub-season, it rains (or snows) under a storm that reaches the climate, and
 *   the sky is clear under one that doesn't.</li>
 *   <li>Where nothing can fall this sub-season (deserts outside winter, temperate land in mid summer), every storm
 *   passes over dark and dry, as vanilla darkens the sky with nothing falling: a sandstorm's cue.</li>
 * </ul>
 * Thunder is heard only under a thunderstorm's core, and only where its rain counts by the same rule. Nothing falls
 * and no thunder is heard above a storm's cloud top. Vanilla's own global weather is held clear meanwhile, so other
 * mods asking "is it raining" of the whole world hear no; {@code AlmanacApi.weatherAt} has the real answer.
 */
public final class LocalWeather {
    private LocalWeather() {
    }

    /** Whether regional weather decides the rain in a level now. */
    public static boolean active(Level level) {
        return level.dimension() == Level.OVERWORLD && Services.hasWeather() && Services.weather().active(level);
    }

    /** The storms over a position (its column, at its height). */
    public static WeatherHere storms(Level level, BlockPos pos) {
        return Services.weather().at(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
    }

    public static WeatherHere storms(Level level, Vec3 pos) {
        return Services.weather().at(level, pos.x, pos.y, pos.z);
    }

    /**
     * The rain level a storm gives a position, 0 to 1: the storm's where its rain falls on the climate there, or where
     * nothing can fall at all this sub-season; 0 under clear sky and under a storm that passes the climate by.
     */
    public static float rainLevel(Level level, BlockPos pos, WeatherHere here) {
        if (!here.stormy()) {
            return 0.0F;
        }
        ClimateTable.Entry entry = entry(level, pos);
        if (entry == null) {
            return here.storm();
        }
        SeasonalClimate climate = ClimateHooks.forLevel(level);
        boolean canFall = climate == null || entry.canFall(climate.subSeason());
        return canFall ? here.rain(entry.base()) : here.storm();
    }

    /** The thunder level a storm gives a position, by the same rule as {@link #rainLevel}. */
    public static float thunderLevel(Level level, BlockPos pos, WeatherHere here) {
        if (here.thunder() <= 0.0F) {
            return 0.0F;
        }
        ClimateTable.Entry entry = entry(level, pos);
        if (entry == null) {
            return here.thunder();
        }
        SeasonalClimate climate = ClimateHooks.forLevel(level);
        boolean canFall = climate == null || entry.canFall(climate.subSeason());
        return canFall ? here.thunder(entry.base()) : here.thunder();
    }

    /** In place of vanilla's {@code isRaining()} for one position: a storm's rain counts here. */
    public static boolean rainingAt(Level level, BlockPos pos) {
        return rainLevel(level, pos, storms(level, pos)) > 0.0F;
    }

    /**
     * Whether the storm over a position brings rain or snow to the climate of a biome there: where something can fall
     * this sub-season, a storm that reaches the climate; where nothing can fall, whatever passes over (nothing falls).
     */
    public static boolean reaches(Level level, BlockPos pos, Biome biome) {
        WeatherHere here = storms(level, pos);
        if (!here.stormy()) {
            return false;
        }
        SeasonalClimate climate = ClimateHooks.forLevel(level);
        ClimateTable.Entry entry = climate == null ? null : climate.table().entry(biome);
        return entry == null || !entry.canFall(climate.subSeason()) || here.reaches(entry.base());
    }

    /** In place of vanilla's {@code isThundering()} for one position. */
    public static boolean thunderingAt(Level level, BlockPos pos) {
        return thunderLevel(level, pos, storms(level, pos)) > 0.0F;
    }

    /**
     * The weather at a position for the public API: under regional weather the storm over it, otherwise vanilla's
     * one weather with the season's share where it is. Null outside the overworld and while no season is known.
     */
    public static @Nullable WeatherAt weatherAt(Level level, BlockPos pos) {
        SeasonalClimate climate = ClimateHooks.forLevel(level);
        if (climate == null) {
            return null;
        }
        if (active(level)) {
            WeatherHere here = storms(level, pos);
            float rain = rainLevel(level, pos, here);
            return new WeatherAt(here.storm(), rain, Math.min(rain, thunderLevel(level, pos, here)),
                    level.precipitationAt(pos));
        }
        float world = ClimateHooks.worldWeather(level, false);
        float thunder = ClimateHooks.worldWeather(level, true);
        ClimateTable.Entry entry = entry(level, pos);
        boolean shows = entry == null || !entry.canFall(climate.subSeason()) || climate.reached().contains(entry.base());
        float rain = shows ? world : 0.0F;
        return new WeatherAt(world, rain, shows ? thunder : 0.0F, level.precipitationAt(pos));
    }

    /** The base climate at a position, for the snow and the forecast; TEMPERATE for a biome without seasons. */
    public static Climate climateAt(Level level, BlockPos pos) {
        ClimateTable.Entry entry = entry(level, pos);
        return entry == null ? Climate.TEMPERATE : entry.base();
    }

    private static ClimateTable.@Nullable Entry entry(Level level, BlockPos pos) {
        ClimateTable table = table(level);
        return table == null ? null : table.entry(level.getBiome(pos).value());
    }

    private static @Nullable ClimateTable table(Level level) {
        SeasonalClimate climate = ClimateHooks.forLevel(level);
        return climate == null ? null : climate.table();
    }
}
