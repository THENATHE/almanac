package com.thenathe.almanac.mixin.weather;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thenathe.almanac.climate.LocalWeather;
import net.minecraft.world.entity.animal.bee.Bee;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Bees stop pollinating in rain only where it rains on them (regional weather, from 0.4.0). */
@Mixin(targets = "net.minecraft.world.entity.animal.bee.Bee$BeePollinateGoal")
abstract class BeePollinateGoalMixin {
    @Shadow
    @Final
    Bee this$0;

    @WrapOperation(method = {"canBeeUse", "canBeeContinueToUse"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;isRaining()Z"))
    private boolean almanac$rainHere(Level level, Operation<Boolean> original) {
        return LocalWeather.active(level) ? LocalWeather.rainingAt(level, this$0.blockPosition()) : original.call(level);
    }
}
