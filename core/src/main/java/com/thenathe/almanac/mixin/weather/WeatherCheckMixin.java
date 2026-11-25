package com.thenathe.almanac.mixin.weather;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thenathe.almanac.climate.LocalWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.WeatherCheck;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The {@code weather_check} loot condition (Channeling's lightning among others) asks the storm where it is checked
 * (regional weather, from 0.4.0). Without a place in the context it reads the world's weather, which is clear.
 */
@Mixin(WeatherCheck.class)
abstract class WeatherCheckMixin {
    @WrapOperation(method = "test", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isRaining()Z"))
    private boolean almanac$rainHere(ServerLevel level, Operation<Boolean> original, @Local(argsOnly = true) LootContext context) {
        BlockPos pos = origin(context);
        return LocalWeather.active(level) && pos != null ? LocalWeather.rainingAt(level, pos) : original.call(level);
    }

    @WrapOperation(method = "test", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isThundering()Z"))
    private boolean almanac$thunderHere(ServerLevel level, Operation<Boolean> original, @Local(argsOnly = true) LootContext context) {
        BlockPos pos = origin(context);
        return LocalWeather.active(level) && pos != null ? LocalWeather.thunderingAt(level, pos) : original.call(level);
    }

    private static @Nullable BlockPos origin(LootContext context) {
        Vec3 origin = context.getOptional(LootContextParams.ORIGIN);
        return origin == null ? null : BlockPos.containing(origin);
    }
}
