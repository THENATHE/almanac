package com.thenathe.almanac.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thenathe.almanac.climate.ClimateHooks;
import com.thenathe.almanac.climate.SeasonalClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * What falls at a position, on both sides. {@code isRainingAt} goes through here, so fire, lightning, wet entities,
 * farmland and the rest follow the season.
 */
@Mixin(Level.class)
abstract class LevelMixin {
    @WrapOperation(method = "precipitationAt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation almanac$seasonalPrecipitation(Biome biome, BlockPos pos, int seaLevel,
                                                              Operation<Biome.Precipitation> original) {
        SeasonalClimate climate = ClimateHooks.forLevel((Level) (Object) this);
        return climate == null ? original.call(biome, pos, seaLevel) : climate.precipitationAt(biome, pos, seaLevel);
    }
}
