package com.thenathe.almanac.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.thenathe.almanac.client.ClientRain;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The client's rain level is the player's own where they stand ({@link ClientRain}): with regional weather the
 * storm's over the camera, otherwise the world's times the player's share. The thunder level is multiplied by it, as
 * vanilla does, so storm darkness fades with the rain; with regional weather it is the thunder over the camera too.
 * Levels on the server keep the world's levels.
 */
@Mixin(Level.class)
abstract class LevelRainMixin {
    @ModifyReturnValue(method = "getRainLevel", at = @At("RETURN"))
    private float almanac$playerShare(float worldRain, @Local(argsOnly = true) float partialTick) {
        return (Object) this instanceof ClientLevel level ? ClientRain.rainLevel(level, worldRain, partialTick) : worldRain;
    }

    @ModifyReturnValue(method = "getThunderLevel", at = @At("RETURN"))
    private float almanac$thunderHere(float worldThunder, @Local(argsOnly = true) float partialTick) {
        if ((Object) this instanceof ClientLevel level) {
            Float thunder = ClientRain.thunderLevel(level, partialTick);
            return thunder == null ? worldThunder : thunder;
        }
        return worldThunder;
    }
}
