package com.thenathe.almanac.mixin.weather;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thenathe.almanac.climate.LocalWeather;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A player may sleep in the day under a thunderstorm, as vanilla allows, where the storm is over the bed (regional
 * weather, from 0.4.0): vanilla's "dark enough" reads the world's one sky, which stays clear. This is the check that
 * keeps a sleeper in bed each tick; {@code ServerPlayerSleepMixin} is the one that lets them lie down.
 */
@Mixin(Player.class)
abstract class PlayerSleepMixin {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/attribute/BedRule;canSleep(Lnet/minecraft/world/level/Level;)Z"))
    private boolean almanac$thunderAtBed(BedRule rule, Level level, Operation<Boolean> original) {
        if (original.call(rule, level)) {
            return true;
        }
        Player player = (Player) (Object) this;
        return LocalWeather.active(level)
                && LocalWeather.thunderingAt(level, player.getSleepingPos().orElse(player.blockPosition()));
    }
}
