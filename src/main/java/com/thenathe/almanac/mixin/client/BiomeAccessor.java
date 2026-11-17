package com.thenathe.almanac.mixin.client;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.synth.Noise;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
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

    /** The noise that moves vanilla's snow line up and down by up to 8 blocks. */
    @Accessor("TEMPERATURE_NOISE")
    static Noise almanac$temperatureNoise() {
        throw new AssertionError();
    }
}
