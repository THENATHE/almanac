package com.thenathe.almanac.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thenathe.almanac.climate.ClimateHooks;
import com.thenathe.almanac.climate.SeasonalClimate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.environment.AtmosphericFogEnvironment;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Rain fog thickens where something is falling this season, as vanilla clients get it from the biome copies. */
@Mixin(AtmosphericFogEnvironment.class)
abstract class AtmosphericFogEnvironmentMixin {
    @WrapOperation(method = "updateRainFogState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;hasPrecipitation()Z"))
    private boolean almanac$seasonalRainFog(Biome biome, Operation<Boolean> original, @Local(argsOnly = true) ClientLevel level) {
        SeasonalClimate climate = ClimateHooks.forLevel(level);
        return climate == null ? original.call(biome) : climate.reaches(biome) && climate.seasonal(biome).hasPrecipitation();
    }
}
