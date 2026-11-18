package com.thenathe.almanac.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thenathe.almanac.climate.ClimateHooks;
import com.thenathe.almanac.climate.PlayerWeather;
import com.thenathe.almanac.climate.SeasonalClimate;
import com.thenathe.almanac.snow.SnowTracking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Snow and ice forming, water freezing and cauldrons filling follow the season, and the snow and ice the season
 * brings is recorded so it can melt again ({@link SnowTracking}). The overworld's rain and thunder levels go to each
 * player separately ({@link PlayerWeather}) instead of to everyone at once.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
    @WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean almanac$seasonalFreeze(Biome biome, LevelReader level, BlockPos pos, Operation<Boolean> original) {
        SeasonalClimate climate = ClimateHooks.forLevel((Level) (Object) this);
        return climate == null ? original.call(biome, level, pos) : climate.shouldFreeze(biome, level, pos);
    }

    @WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean almanac$seasonalSnow(Biome biome, LevelReader level, BlockPos pos, Operation<Boolean> original) {
        SeasonalClimate climate = ClimateHooks.forLevel((Level) (Object) this);
        return climate == null ? original.call(biome, level, pos) : climate.shouldSnow(biome, level, pos);
    }

    @WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/biome/Biome;getPrecipitationAt(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/biome/Biome$Precipitation;"))
    private Biome.Precipitation almanac$seasonalCauldrons(Biome biome, BlockPos pos, int seaLevel,
                                                          Operation<Biome.Precipitation> original) {
        SeasonalClimate climate = ClimateHooks.forLevel((Level) (Object) this);
        return climate == null ? original.call(biome, pos, seaLevel) : climate.precipitationAt(biome, pos, seaLevel);
    }

    /** Covers all three of vanilla's weather placements: ice, a new snow layer, and one more layer on snow. */
    @WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;setBlockAndUpdate(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)Z"),
            require = 3)
    private boolean almanac$recordWeatherSnow(ServerLevel level, BlockPos pos, BlockState state, Operation<Boolean> original) {
        BlockState before = level.getBlockState(pos);
        boolean changed = original.call(level, pos, state);
        if (changed) {
            SnowTracking.weatherPlaced(level, pos, before, state);
        }
        return changed;
    }

    @WrapOperation(method = "advanceWeatherCycle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/resources/ResourceKey;)V"))
    private void almanac$weatherPerPlayer(PlayerList players, Packet<?> packet, ResourceKey<Level> dimension,
                                          Operation<Void> original) {
        if (packet instanceof ClientboundGameEventPacket event && PlayerWeather.isWeatherLevelPacket(event)
                && PlayerWeather.manages((ServerLevel) (Object) this)) {
            return;
        }
        original.call(players, packet, dimension);
    }

    /** Vanilla also sends these to every dimension; players elsewhere still get them as before. */
    @WrapOperation(method = "advanceWeatherCycle", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/players/PlayerList;broadcastAll(Lnet/minecraft/network/protocol/Packet;)V"))
    private void almanac$weatherChangePerPlayer(PlayerList players, Packet<?> packet, Operation<Void> original) {
        ServerLevel self = (ServerLevel) (Object) this;
        if (!(packet instanceof ClientboundGameEventPacket event)) {
            original.call(players, packet);
            return;
        }
        if (!PlayerWeather.manages(self)) {
            original.call(players, packet);
            // Another dimension's start or stop reaches overworld players too and resets their rain level.
            if (PlayerWeather.isRainStartOrStop(event)) {
                PlayerWeather.resendAll();
            }
            return;
        }
        if (PlayerWeather.isWeatherLevelPacket(event)) {
            for (ServerPlayer player : players.getPlayers()) {
                if (player.level() != self) {
                    player.connection.send(packet);
                }
            }
            return;
        }
        original.call(players, packet);
        if (PlayerWeather.isRainStartOrStop(event)) {
            PlayerWeather.resendAll();
        }
    }
}
