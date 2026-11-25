package com.thenathe.almanac.mixin.weather;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thenathe.almanac.climate.LocalWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Monster;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Monsters spawn in the darkness of a thunderstorm only under one (regional weather, from 0.4.0). */
@Mixin(Monster.class)
abstract class MonsterMixin {
    @WrapOperation(method = "isDarkEnoughToSpawn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;isThundering()Z"))
    private static boolean almanac$thunderHere(ServerLevel level, Operation<Boolean> original,
                                               @Local(argsOnly = true) BlockPos pos) {
        return LocalWeather.active(level) ? LocalWeather.thunderingAt(level, pos) : original.call(level);
    }
}
