package com.thenathe.almanac.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Biome.class)
public interface BiomeAccessor {
    /** A biome's grass colour before its grass modifier (dark forest, swamp) is applied. */
    @Invoker("getBaseGrassColor")
    int almanac$baseGrassColor();

    /**
     * The biome's temperature at a position: its temperature modifier, then cooling with height. Unlike
     * {@code getTemperature} it keeps no cache, so it is safe from any thread.
     */
    @Invoker("getHeightAdjustedTemperature")
    float almanac$heightAdjustedTemperature(BlockPos pos, int seaLevel);
}
