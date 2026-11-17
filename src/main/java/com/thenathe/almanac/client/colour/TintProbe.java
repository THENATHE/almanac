package com.thenathe.almanac.client.colour;

import com.thenathe.almanac.colour.SeasonalPalette.LeafTint;
import java.util.List;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import org.jspecify.annotations.Nullable;

/**
 * Finds out whether a block's tint follows the biome's grass, foliage or dry-foliage colour, by asking its tint
 * sources for a colour in an empty world that notes which colours they look up. That works for vanilla's sources
 * and for other mods' alike, without knowing how they are written.
 */
final class TintProbe implements BlockAndTintGetter {
    private boolean readSeasonalColour;

    private TintProbe() {
    }

    static LeafTint classify(BlockState state, List<BlockTintSource> sources) {
        if (sources.isEmpty()) {
            return LeafTint.UNTINTED;
        }
        TintProbe probe = new TintProbe();
        for (BlockTintSource source : sources) {
            try {
                source.colorInWorld(state, probe, BlockPos.ZERO);
            } catch (RuntimeException unusual) {
                // A source that needs a real world: treat it as following the biome, so Almanac leaves it alone.
                return LeafTint.BIOME;
            }
        }
        return probe.readSeasonalColour ? LeafTint.BIOME : LeafTint.FIXED;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        if (resolver == BiomeColors.GRASS_COLOR_RESOLVER || resolver == BiomeColors.FOLIAGE_COLOR_RESOLVER
                || resolver == BiomeColors.DRY_FOLIAGE_COLOR_RESOLVER) {
            readSeasonalColour = true;
        }
        return 0xFF808080;
    }

    @Override
    public CardinalLighting cardinalLighting() {
        return CardinalLighting.DEFAULT;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return LevelLightEngine.EMPTY;
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public int getHeight() {
        return 0;
    }

    @Override
    public int getMinY() {
        return 0;
    }
}
