package com.thenathe.almanac.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thenathe.almanac.biome.ChunkPacketOrigins;
import com.thenathe.almanac.biome.SeasonalBiomeView;
import java.util.BitSet;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.PlayerChunkSender;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Chunks sent to a player without Almanac carry the current sub-season's biome copies. */
@Mixin(PlayerChunkSender.class)
abstract class PlayerChunkSenderMixin {
    @WrapOperation(method = "sendChunk", at = @At(value = "NEW",
            target = "(Lnet/minecraft/world/level/chunk/LevelChunk;Lnet/minecraft/world/level/lighting/LevelLightEngine;Ljava/util/BitSet;Ljava/util/BitSet;)Lnet/minecraft/network/protocol/game/ClientboundLevelChunkWithLightPacket;"))
    private static ClientboundLevelChunkWithLightPacket almanac$seasonalChunk(
            LevelChunk chunk, LevelLightEngine light, BitSet skyChanges, BitSet blockChanges,
            Operation<ClientboundLevelChunkWithLightPacket> original,
            @Local(argsOnly = true) ServerGamePacketListenerImpl connection, @Local(argsOnly = true) ServerLevel level) {
        ClientboundLevelChunkWithLightPacket packet = SeasonalBiomeView.build(connection.player, level,
                () -> original.call(chunk, light, skyChanges, blockChanges));
        ChunkPacketOrigins.built(packet, connection.player, SeasonalBiomeView.getsCopies(connection.player, level));
        return packet;
    }
}
