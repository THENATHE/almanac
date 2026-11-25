package com.thenathe.almanac.internal;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * The overworld's climate right now: the sub-season, which climates the current rain event reaches, and each biome's
 * seasons from the climate table. The same on the server and on its clients. Core's {@code SeasonalClimate}.
 */
public interface ClimateNow {
    SubSeason subSeason();

    /** The climates the current (or most recent) rain event reaches. */
    Set<Climate> reached();

    /** The biome's seasons, or null for a biome without them (outside the overworld's biomes). */
    @Nullable BiomeClimate entry(Biome biome);

    /** What falls at a position during the current event: {@code NONE} where the event doesn't reach the climate. */
    Biome.Precipitation precipitationAt(Biome biome, BlockPos pos, int seaLevel);
}
