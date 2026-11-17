package com.thenathe.almanac.client.colour;

import java.util.Set;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * A leaf block's own tint source with its seasonal palette on top. The original still decides the colour (a fixed
 * birch green, or the biome's seasonal foliage); the current {@link ColourTable} then moves it along the leaf's
 * steps. Items in hand keep the original colour.
 */
final class SeasonalLeafTint implements BlockTintSource {
    private final Block block;
    private final BlockTintSource original;

    SeasonalLeafTint(Block block, BlockTintSource original) {
        this.block = block;
        this.original = original;
    }

    @Override
    public int color(BlockState state) {
        return original.color(state);
    }

    @Override
    public int colorInWorld(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        return ClientColours.table().leaf(block, original.colorInWorld(state, level, pos));
    }

    @Override
    public int colorAsTerrainParticle(BlockState state, BlockAndTintGetter level, BlockPos pos) {
        return ClientColours.table().leaf(block, original.colorAsTerrainParticle(state, level, pos));
    }

    @Override
    public Set<Property<?>> relevantProperties() {
        return original.relevantProperties();
    }

    @Override
    public String toString() {
        return "Almanac seasonal " + original;
    }
}
