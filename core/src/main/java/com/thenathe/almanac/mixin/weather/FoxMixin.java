package com.thenathe.almanac.mixin.weather;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thenathe.almanac.climate.LocalWeather;
import net.minecraft.world.entity.animal.fox.Fox;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A sleeping fox wakes in a thunderstorm only under one (regional weather, from 0.4.0). */
@Mixin(Fox.class)
abstract class FoxMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;isThundering()Z"))
    private boolean almanac$thunderHere(Level level, Operation<Boolean> original) {
        Fox fox = (Fox) (Object) this;
        return LocalWeather.active(level) ? LocalWeather.thunderingAt(level, fox.blockPosition()) : original.call(level);
    }
}
