package com.thenathe.almanac.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thenathe.almanac.climate.PlayerWeather;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** A player arriving in the overworld is told their own rain and thunder levels from the start. */
@Mixin(PlayerList.class)
abstract class PlayerListMixin {
    @WrapOperation(method = "sendLevelInfo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getRainLevel(F)F"))
    private float almanac$playerRain(ServerLevel level, float partialTick, Operation<Float> original,
                                     @Local(argsOnly = true) ServerPlayer player) {
        return PlayerWeather.manages(level) ? PlayerWeather.rainFor(player, level) : original.call(level, partialTick);
    }

    @WrapOperation(method = "sendLevelInfo", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;getThunderLevel(F)F"))
    private float almanac$playerThunder(ServerLevel level, float partialTick, Operation<Float> original,
                                        @Local(argsOnly = true) ServerPlayer player) {
        return PlayerWeather.manages(level) ? PlayerWeather.thunderFor(player, level) : original.call(level, partialTick);
    }
}
