package com.thenathe.almanac.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thenathe.almanac.biome.SeasonalBiomeView;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Biome updates (after {@code /fillbiome}, for example) are built per player, so they get copies the same way. */
@Mixin(ChunkMap.class)
abstract class ChunkMapMixin {
    @Shadow
    @Final
    private ServerLevel level;

    @WrapOperation(method = "resendBiomesForChunks", at = @At(value = "INVOKE",
            target = "Ljava/util/Map;forEach(Ljava/util/function/BiConsumer;)V"))
    private void almanac$seasonalBiomeUpdates(Map<ServerPlayer, List<LevelChunk>> chunksForPlayers,
                                              BiConsumer<ServerPlayer, List<LevelChunk>> send, Operation<Void> original) {
        BiConsumer<ServerPlayer, List<LevelChunk>> seasonal = (player, chunks) ->
                SeasonalBiomeView.build(player, level, () -> {
                    send.accept(player, chunks);
                    return null;
                });
        original.call(chunksForPlayers, seasonal);
    }
}
