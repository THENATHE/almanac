package com.thenathe.almanac.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.thenathe.almanac.climate.ClimateHooks;
import com.thenathe.almanac.climate.LocalWeather;
import com.thenathe.almanac.climate.SeasonalClimate;
import com.thenathe.almanac.internal.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.WeatherData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Snow and ice forming, water freezing and cauldrons filling follow the season, and the snow and ice the season
 * brings is recorded so it can melt again (by the snow module). The rain and thunder levels go to players as in
 * vanilla; each client works out its own share of them (from 0.2.0, {@code ClientRain}).
 *
 * <p>With regional weather (from 0.4.0) vanilla's one weather is held clear, and the snowfall, the cauldrons and
 * lightning go by the storm over each column and chunk instead: snow falls and lightning strikes only under a storm.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
    /**
     * Vanilla's weather cycle doesn't run in the overworld while regional weather does: its rain and thunder stay at
     * nothing, so the world as a whole reads clear. The game rule and {@code /weather} act on the storms instead.
     */
    @Inject(method = "advanceWeatherCycle", at = @At("HEAD"), cancellable = true)
    private void almanac$holdClear(CallbackInfo info) {
        ServerLevel level = (ServerLevel) (Object) this;
        if (!LocalWeather.active(level)) {
            return;
        }
        info.cancel();
        WeatherData weather = level.getWeatherData();
        if (weather.isRaining() || weather.isThundering()) {
            weather.setRaining(false);
            weather.setThundering(false);
            weather.setRainTime(0);
            weather.setThunderTime(0);
        }
        boolean wasRaining = level.getRainLevel(1.0F) > 0.0F;
        if (wasRaining || level.getThunderLevel(1.0F) > 0.0F) {
            level.setRainLevel(0.0F);
            level.setThunderLevel(0.0F);
            PlayerList players = level.getServer().getPlayerList();
            players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.STOP_RAINING, 0.0F));
            players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, 0.0F),
                    level.dimension());
            players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, 0.0F),
                    level.dimension());
        }
    }

    /** Snow falls and cauldrons fill only on a column under a storm. */
    @WrapOperation(method = "tickPrecipitation", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;isRaining()Z"))
    private boolean almanac$rainingOnColumn(ServerLevel level, Operation<Boolean> original,
                                            @Local(argsOnly = true) BlockPos pos) {
        if (!LocalWeather.active(level)) {
            return original.call(level);
        }
        return LocalWeather.rainingAt(level, level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos));
    }

    /**
     * Every loaded chunk rolls for lightning each tick as in vanilla; with regional weather the roll counts only
     * under a thunderstorm at the chunk, checked once the roll has come up, so a chunk costs nothing more each tick.
     * The bolt then still needs rain where it lands ({@code isRainingAt}), as in vanilla.
     */
    @WrapOperation(method = "tickThunder", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;isRaining()Z"))
    private boolean almanac$mayThunder(ServerLevel level, Operation<Boolean> original) {
        return LocalWeather.active(level) || original.call(level);
    }

    @WrapOperation(method = "tickThunder", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;isThundering()Z"))
    private boolean almanac$mayThunderToo(ServerLevel level, Operation<Boolean> original) {
        return LocalWeather.active(level) || original.call(level);
    }

    @WrapOperation(method = "tickThunder", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
    private int almanac$thunderAtChunk(RandomSource random, int bound, Operation<Integer> original,
                                       @Local(argsOnly = true) LevelChunk chunk) {
        int roll = original.call(random, bound);
        ServerLevel level = (ServerLevel) (Object) this;
        if (roll != 0 || !LocalWeather.active(level)) {
            return roll;
        }
        int x = chunk.getPos().getMinBlockX() + 8;
        int z = chunk.getPos().getMinBlockZ() + 8;
        BlockPos centre = new BlockPos(x, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, 8, 8) + 1, z);
        return LocalWeather.thunderingAt(level, centre) ? 0 : 1;
    }

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
            Services.snow().weatherPlaced(level, pos, before, state);
        }
        return changed;
    }
}
