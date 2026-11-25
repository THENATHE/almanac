package com.thenathe.almanac.mixin.weather;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thenathe.almanac.climate.LocalWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Lying down in the day is allowed under a thunderstorm over the bed (regional weather, from 0.4.0). */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerSleepMixin {
    @WrapOperation(method = "startSleepInBed", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/attribute/BedRule;canSleep(Lnet/minecraft/world/level/Level;)Z"))
    private boolean almanac$thunderAtBed(BedRule rule, Level level, Operation<Boolean> original,
                                         @Local(argsOnly = true) BlockPos bed) {
        return original.call(rule, level) || (LocalWeather.active(level) && LocalWeather.thunderingAt(level, bed));
    }
}
