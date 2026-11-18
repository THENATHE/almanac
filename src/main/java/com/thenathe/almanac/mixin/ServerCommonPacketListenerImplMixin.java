package com.thenathe.almanac.mixin;

import com.thenathe.almanac.biome.ChunkPacketOrigins;
import com.thenathe.almanac.biome.SeasonalBiomeView;
import io.netty.channel.ChannelFutureListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Checks each chunk packet goes to the player it was built for, or one who gets the same biomes. */
@Mixin(ServerCommonPacketListenerImpl.class)
abstract class ServerCommonPacketListenerImplMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;)V", at = @At("HEAD"))
    private void almanac$checkChunkPacket(Packet<?> packet, @Nullable ChannelFutureListener listener, CallbackInfo callback) {
        if (packet instanceof ClientboundLevelChunkWithLightPacket && (Object) this instanceof ServerGamePacketListenerImpl game) {
            ServerPlayer player = game.player;
            ChunkPacketOrigins.sending(packet, player, SeasonalBiomeView.getsCopies(player, player.level()));
        }
    }
}
