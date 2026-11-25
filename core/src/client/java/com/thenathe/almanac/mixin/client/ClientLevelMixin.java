package com.thenathe.almanac.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thenathe.almanac.climate.ClimateHooks;
import com.thenathe.almanac.climate.LocalWeather;
import com.thenathe.almanac.climate.SeasonalClimate;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Rain and snow drawn around the camera, splashes and rain sounds follow the season on a client with Almanac, and with
 * regional weather (from 0.4.0) fall only on columns under a storm, so the edge of a storm shows as an edge of rain.
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @WrapOperation(method = "getPrecipitationAt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation almanac$seasonalPrecipitation(Biome biome, BlockPos pos, int seaLevel,
                                                              Operation<Biome.Precipitation> original) {
        ClientLevel level = (ClientLevel) (Object) this;
        SeasonalClimate climate = ClimateHooks.forLevel(level);
        if (climate == null) {
            return original.call(biome, pos, seaLevel);
        }
        if (LocalWeather.active(level) && !LocalWeather.rainingAt(level, pos)) {
            return Biome.Precipitation.NONE;
        }
        return climate.precipitationAt(biome, pos, seaLevel);
    }
}
