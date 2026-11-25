package com.thenathe.almanac.climate;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.ClimateNow;
import com.thenathe.almanac.internal.Services;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * The overworld's climate right now: the current sub-season and which climates the current rain event reaches. Every
 * climate check asks the {@link ClimateTable}, which the server and its clients share, so both agree on where rain
 * and snow fall. Biomes without seasons behave as in vanilla.
 */
public record SeasonalClimate(ClimateTable table, SubSeason subSeason, Set<Climate> reached) implements ClimateNow {
    /** Vanilla neither snows nor freezes at block light 10 or more. */
    private static final int MAX_BLOCK_LIGHT = 10;

    public SeasonalClimate {
        reached = Set.copyOf(reached);
    }

    @Override
    public ClimateTable.@Nullable Entry entry(Biome biome) {
        return table.entry(biome);
    }

    /** Whether the current rain event reaches the biome's climate. */
    public boolean reaches(Biome biome) {
        ClimateTable.Entry entry = table.entry(biome);
        return entry == null || reached.contains(entry.base());
    }

    /** Whether rain or snow falls in the biome during the current event: the event reaches it and something can fall. */
    public boolean falls(Biome biome) {
        ClimateTable.Entry entry = table.entry(biome);
        if (entry == null) {
            return biome.hasPrecipitation();
        }
        return reached.contains(entry.base()) && entry.canFall(subSeason);
    }

    @Override
    public Biome.Precipitation precipitationAt(Biome biome, BlockPos pos, int seaLevel) {
        ClimateTable.Entry entry = table.entry(biome);
        if (entry == null) {
            return biome.getPrecipitationAt(pos, seaLevel);
        }
        if (!reached.contains(entry.base())) {
            return Biome.Precipitation.NONE;
        }
        return entry.precipitationAt(subSeason, pos, seaLevel);
    }

    /** Whether the season makes a position cold enough for snow; a biome without seasons answers for itself. */
    public boolean coldEnoughToSnow(Biome biome, BlockPos pos, int seaLevel) {
        ClimateTable.Entry entry = table.entry(biome);
        return entry == null ? biome.coldEnoughToSnow(pos, seaLevel) : entry.coldEnoughToSnow(subSeason, pos, seaLevel);
    }

    /**
     * Whether vanilla's freezing may freeze water here. Biomes that get no seasonal ice (oceans) freeze as their real
     * biome does. In the rest, the season's ice is the snow module's alone (from 0.3.0): it follows its climate's ice
     * cover column by column, from the shore inward, so loaded land freezes as land that loads does. Vanilla only
     * freezes water the biome's own climate freezes, while the season keeps it cold, with the same checks as
     * {@code Biome#shouldFreeze}.
     */
    public boolean shouldFreeze(Biome biome, LevelReader level, BlockPos pos) {
        ClimateTable.Entry entry = table.entry(biome);
        if (entry == null || !entry.seasonalIce()) {
            return biome.shouldFreeze(level, pos);
        }
        if (!entry.coldEnoughToSnow(subSeason, pos, level.getSeaLevel()) || !biome.coldEnoughToSnow(pos, level.getSeaLevel())
                || !insideAndDarkEnough(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!level.getFluidState(pos).is(Fluids.WATER) || !(state.getBlock() instanceof LiquidBlock)) {
            return false;
        }
        boolean surroundedByWater = level.isWaterAt(pos.west()) && level.isWaterAt(pos.east())
                && level.isWaterAt(pos.north()) && level.isWaterAt(pos.south());
        return !surroundedByWater;
    }

    /**
     * Whether vanilla's snowfall may lay snow here. Snow the season brings only lands where the column's share of the
     * climate's layers has more for it (the snow module's {@code snowMayFall}), so land inside simulation distance
     * whitens and deepens no faster than the land around it and far terrain. Where the biome is cold all year (peaks, the frozen north) vanilla's
     * snowfall is left as it is. The same checks as vanilla's {@code Biome#shouldSnow}, with the season's weather.
     */
    public boolean shouldSnow(Biome biome, LevelReader level, BlockPos pos) {
        ClimateTable.Entry entry = table.entry(biome);
        if (entry == null) {
            return biome.shouldSnow(level, pos);
        }
        if (!reached.contains(entry.base())
                || entry.precipitationAt(subSeason, pos, level.getSeaLevel()) != Biome.Precipitation.SNOW
                || !insideAndDarkEnough(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.isAir() || state.is(Blocks.SNOW)) || !Blocks.SNOW.defaultBlockState().canSurvive(level, pos)) {
            return false;
        }
        int layersHere = state.is(Blocks.SNOW) ? state.getValue(SnowLayerBlock.LAYERS) : 0;
        return biome.coldEnoughToSnow(pos, level.getSeaLevel()) || Services.snow().snowMayFall(pos, entry.base(), layersHere);
    }

    /** Vanilla's test before snowing or freezing: inside the world and dark enough. */
    private static boolean insideAndDarkEnough(LevelReader level, BlockPos pos) {
        return level.isInsideBuildHeight(pos.getY()) && level.getBrightness(LightLayer.BLOCK, pos) < MAX_BLOCK_LIGHT;
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
