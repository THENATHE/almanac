package com.thenathe.almanac.mixin.weather;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thenathe.almanac.climate.LocalWeather;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Rain puts fire out and keeps it from spreading only where a storm is over it (regional weather, from 0.4.0).
 * Vanilla asks whether it rains anywhere first, then whether rain falls at and beside the fire; the second part goes
 * by the storm already ({@code isRainingAt}), so the first asks the storm over the fire.
 */
@Mixin(FireBlock.class)
abstract class FireBlockMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/level/ServerLevel;isRaining()Z"))
    private boolean almanac$rainNearFire(ServerLevel level, Operation<Boolean> original, @Local(argsOnly = true) BlockPos pos) {
        return LocalWeather.active(level) ? LocalWeather.rainingAt(level, pos) : original.call(level);
    }
}
