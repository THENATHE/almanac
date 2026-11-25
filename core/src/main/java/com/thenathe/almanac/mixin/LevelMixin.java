package com.thenathe.almanac.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thenathe.almanac.climate.ClimateHooks;
import com.thenathe.almanac.climate.LocalWeather;
import com.thenathe.almanac.climate.SeasonalClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * What falls at a position, on both sides. {@code isRainingAt} goes through here, so fire, lightning, wet entities,
 * farmland, fishing, leaves dripping, the happy ghast's healing and the rest follow the season, and with regional
 * weather (from 0.4.0) the storm over the position instead of vanilla's one weather.
 */
@Mixin(Level.class)
abstract class LevelMixin {
    @WrapOperation(method = "precipitationAt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;isRaining()Z"))
    private boolean almanac$rainingHere(Level level, Operation<Boolean> original, @Local(argsOnly = true) BlockPos pos) {
        // Only whether a storm's rain is over the column (cheap, and remembered for the tick); whether it reaches the
        // climate there is asked below, with the biome vanilla looks up anyway.
        return LocalWeather.active(level) ? LocalWeather.storms(level, pos).stormy() : original.call(level);
    }

    @WrapOperation(method = "precipitationAt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation almanac$seasonalPrecipitation(Biome biome, BlockPos pos, int seaLevel,
                                                              Operation<Biome.Precipitation> original) {
        Level level = (Level) (Object) this;
        SeasonalClimate climate = ClimateHooks.forLevel(level);
        if (climate == null) {
            return original.call(biome, pos, seaLevel);
        }
        if (LocalWeather.active(level) && !LocalWeather.reaches(level, pos, biome)) {
            return Biome.Precipitation.NONE;
        }
        return climate.precipitationAt(biome, pos, seaLevel);
    }
}
