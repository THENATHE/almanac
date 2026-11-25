package com.thenathe.almanac.mixin.weather;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thenathe.almanac.climate.LocalWeather;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Foxes seek shelter from a thunderstorm only under one (regional weather, from 0.4.0). */
@Mixin(targets = "net.minecraft.world.entity.animal.fox.Fox$SeekShelterGoal")
abstract class FoxShelterMixin {
    @Shadow
    @Final
    Fox this$0;

    @WrapOperation(method = "canUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isThundering()Z"))
    private boolean almanac$thunderHere(Level level, Operation<Boolean> original) {
        return LocalWeather.active(level) ? LocalWeather.thunderingAt(level, this$0.blockPosition()) : original.call(level);
    }
}
