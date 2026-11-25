package com.thenathe.almanac.snow;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.AlmanacModule;
import com.thenathe.almanac.internal.FarSnowLines;
import com.thenathe.almanac.internal.SeasonTick;
import com.thenathe.almanac.internal.Services;
import com.thenathe.almanac.internal.Snow;
import com.thenathe.almanac.internal.SnowNow;
import com.thenathe.almanac.internal.SnowReading;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/** The snow module: its entrypoint, and its side of the internal API, answered by the classes that do the work. */
public final class SnowModule implements AlmanacModule, Snow {
    private static final FarSnowLines FAR_RULE = new FarSnowLines() {
        @Override
        public SubSeason shownSubSeason(SubSeason now, Map<Climate, Integer> layers) {
            return FarSnowRule.shown(now, layers).subSeason();
        }

        @Override
        public int coldLine(float temperature, float noise, int seaLevel) {
            return FarSnowRule.coldLine(temperature, noise, seaLevel);
        }

        @Override
        public int snowLine(@Nullable Climate base, int seasonalColdLine, int realColdLine, int layers,
                            boolean nativeSnowMelts, int naturalHeight, boolean snowSurface) {
            return FarSnowRule.snowLine(base, seasonalColdLine, realColdLine, layers, nativeSnowMelts, naturalHeight,
                    snowSurface);
        }

        @Override
        public int withinLevel(int line, int minY, int maxY) {
            return FarSnowRule.withinLevel(line, minY, maxY);
        }
    };

    @Override
    public void initialize() {
        Services.installSnow(this);
        SnowTracking.initialize();
    }

    @Override
    public void serverStarting() {
        SnowUpdates.serverStarting();
    }

    @Override
    public void seasonStarted(MinecraftServer server, SeasonTick start) {
        SeasonSnow.seasonStarted(server, start);
    }

    @Override
    public void seasonTicked(ServerLevel overworld, SeasonTick now) {
        SeasonSnow snow = SeasonSnow.get();
        if (snow != null) {
            snow.update(overworld, now);
        }
    }

    @Override
    public void levelTicked(ServerLevel level) {
        SnowUpdates.tick(level);
    }

    @Override
    public void chunkLoaded(ServerLevel level, LevelChunk chunk) {
        SnowUpdates.chunkLoaded(level, chunk);
    }

    @Override
    public void chunkUnloaded(ServerLevel level, LevelChunk chunk) {
        SnowUpdates.chunkUnloaded(level, chunk);
    }

    @Override
    public void serverStopped() {
        SeasonSnow.serverStopped();
        SnowUpdates.serverStopped();
    }

    @Override
    public void weatherPlaced(ServerLevel level, BlockPos pos, BlockState before, BlockState placed) {
        SnowTracking.weatherPlaced(level, pos, before, placed);
    }

    @Override
    public void playerPlaced(Level level, BlockPos pos, BlockState placed) {
        SnowTracking.playerPlaced(level, pos, placed);
    }

    @Override
    public boolean snowMayFall(BlockPos pos, Climate climate, int layersHere) {
        return SnowUpdates.snowMayFall(pos, climate, layersHere);
    }

    @Override
    public @Nullable SnowReading reading() {
        SeasonSnow snow = SeasonSnow.get();
        return snow == null ? null : snow.reading();
    }

    @Override
    public @Nullable SnowReading readingAt(double x, double z) {
        SeasonSnow snow = SeasonSnow.get();
        return snow == null ? null : snow.readingAt(x, z);
    }

    @Override
    public @Nullable SnowNow now() {
        SeasonSnow snow = SeasonSnow.get();
        return snow == null ? null : snow.state();
    }

    @Override
    public @Nullable String describeQueues() {
        SnowUpdates updates = SnowUpdates.get();
        return updates == null ? null : updates.describeQueues();
    }

    @Override
    public FarSnowLines farRule() {
        return FAR_RULE;
    }

    @Override
    public @Nullable CompoundTag logForClients() {
        SeasonSnow snow = SeasonSnow.get();
        return snow == null ? null : snow.logForClients();
    }

    @Override
    public long logChanges() {
        SeasonSnow snow = SeasonSnow.get();
        return snow == null ? -1 : snow.logChanges();
    }

    @Override
    public void clientReceivedLog(CompoundTag log) {
        ClientSnow.received(log);
    }

    @Override
    public void clientTicked(Level level) {
        ClientSnow snow = ClientSnow.get();
        if (snow != null && level.dimension() == Level.OVERWORLD) {
            snow.tick(level);
        }
    }

    @Override
    public void clientDisconnected() {
        ClientSnow.disconnected();
    }

    @Override
    public @Nullable Map<Climate, Double> depthsAt(Level level, double x, double z) {
        if (level.dimension() != Level.OVERWORLD) {
            return null;
        }
        if (level.isClientSide()) {
            ClientSnow snow = ClientSnow.get();
            return snow == null || !Services.weather().active(level) ? null : snow.depthsAt(x, z, level.getGameTime());
        }
        SeasonSnow snow = SeasonSnow.get();
        return snow == null ? null : snow.depthsAt(x, z);
    }
}
