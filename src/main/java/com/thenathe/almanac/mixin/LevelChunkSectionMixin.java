package com.thenathe.almanac.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thenathe.almanac.biome.SeasonalBiomeView;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Where packet building reads a section's biomes. Outside a {@link SeasonalBiomeView} this hands back the
 * section's own biomes; nothing that saves or generates chunks is affected.
 */
@Mixin(LevelChunkSection.class)
abstract class LevelChunkSectionMixin {
    @WrapOperation(method = {"write", "getSerializedSize", "getBiomes"}, at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/level/chunk/LevelChunkSection;biomes:Lnet/minecraft/world/level/chunk/PalettedContainerRO;",
            opcode = Opcodes.GETFIELD))
    private PalettedContainerRO<Holder<Biome>> almanac$biomesToSend(LevelChunkSection section,
                                                                   Operation<PalettedContainerRO<Holder<Biome>>> original) {
        return SeasonalBiomeView.biomesToSend(original.call(section));
    }
}
