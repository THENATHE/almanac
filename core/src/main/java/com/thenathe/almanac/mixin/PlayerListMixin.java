package com.thenathe.almanac.mixin;

import com.thenathe.almanac.network.ServerNetworking;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A player whose operator status changes is told whether they may now change Almanac's settings. */
@Mixin(PlayerList.class)
abstract class PlayerListMixin {
    @Inject(method = "sendPlayerPermissionLevel(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/server/permissions/LevelBasedPermissionSet;)V",
            at = @At("TAIL"))
    private void almanac$settingsRights(ServerPlayer player, LevelBasedPermissionSet permissions, CallbackInfo callback) {
        ServerNetworking.permissionsSent(player);
    }
}
