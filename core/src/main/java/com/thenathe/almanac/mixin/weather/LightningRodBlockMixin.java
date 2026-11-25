package com.thenathe.almanac.mixin.weather;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thenathe.almanac.climate.LocalWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LightningRodBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A lightning rod sparks only under a thunderstorm (regional weather, from 0.4.0). */
@Mixin(LightningRodBlock.class)
abstract class LightningRodBlockMixin {
    @WrapOperation(method = "animateTick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;isThundering()Z"))
    private boolean almanac$thunderHere(Level level, Operation<Boolean> original, @Local(argsOnly = true) BlockPos pos) {
        return LocalWeather.active(level) ? LocalWeather.thunderingAt(level, pos) : original.call(level);
    }
}
