package com.thenathe.almanac.mixin.weather;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thenathe.almanac.climate.LocalWeather;
import net.minecraft.world.entity.animal.panda.Panda;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A worried panda is scared by thunder only under a thunderstorm (regional weather, from 0.4.0). */
@Mixin(Panda.class)
abstract class PandaMixin {
    @WrapOperation(method = {"tick", "isScared"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/Level;isThundering()Z"))
    private boolean almanac$thunderHere(Level level, Operation<Boolean> original) {
        Panda panda = (Panda) (Object) this;
        return LocalWeather.active(level) ? LocalWeather.thunderingAt(level, panda.blockPosition()) : original.call(level);
    }
}
