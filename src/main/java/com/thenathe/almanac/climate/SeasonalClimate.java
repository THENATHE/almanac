package com.thenathe.almanac.climate;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.biome.SeasonalBiomeIndex;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * The overworld's climate right now: the current sub-season and which climates the current rain event reaches.
 * Every climate check asks the sub-season's biome copy, so the server, modded clients and vanilla clients (which
 * receive the copies themselves) agree on where rain and snow fall. Biomes without copies behave as in vanilla.
 */
public record SeasonalClimate(SeasonalBiomeIndex index, SubSeason subSeason, Set<Climate> reached) {
    public SeasonalClimate {
        reached = Set.copyOf(reached);
    }

    public SeasonalBiomeIndex.@Nullable Entry entry(Biome biome) {
        return index.entry(biome);
    }

    /** The biome as it is this sub-season: its copy, or the biome itself if it has none. */
    public Biome seasonal(Biome biome) {
        SeasonalBiomeIndex.Entry entry = index.entry(biome);
        return entry == null ? biome : entry.copy(subSeason).value();
    }

    /** Whether the current rain event reaches the biome's climate. */
    public boolean reaches(Biome biome) {
        SeasonalBiomeIndex.Entry entry = index.entry(biome);
        return entry == null || reached.contains(entry.base());
    }

    public Biome.Precipitation precipitationAt(Biome biome, BlockPos pos, int seaLevel) {
        SeasonalBiomeIndex.Entry entry = index.entry(biome);
        if (entry == null) {
            return biome.getPrecipitationAt(pos, seaLevel);
        }
        if (!reached.contains(entry.base())) {
            return Biome.Precipitation.NONE;
        }
        return entry.copy(subSeason).value().getPrecipitationAt(pos, seaLevel);
    }

    /** Water freezes by the season's temperature whatever the weather, as it does in vanilla. */
    public boolean shouldFreeze(Biome biome, LevelReader level, BlockPos pos) {
        return seasonal(biome).shouldFreeze(level, pos);
    }

    public boolean shouldSnow(Biome biome, LevelReader level, BlockPos pos) {
        return reaches(biome) && seasonal(biome).shouldSnow(level, pos);
    }

    /** Winter moves a climate one step colder and summer one step warmer. */
    public static Climate shifted(Climate base, SubSeason subSeason) {
        return switch (subSeason.season()) {
            case WINTER -> base.colder();
            case SUMMER -> base.warmer();
            case SPRING, FALL -> base;
        };
    }
}
