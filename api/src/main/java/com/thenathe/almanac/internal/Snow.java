package com.thenathe.almanac.internal;

import com.thenathe.almanac.api.Climate;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * The snow module's side of the internal API: the weather log and the snow depths worked out from it, the snow on
 * loaded and loading chunks, and the far rule. Core calls each of these from where it called the snow before the
 * module split, so the order of everything on a tick is as it was. Server calls belong on the server thread.
 */
public interface Snow {
    /** As the server starts, before the spawn chunks load. */
    void serverStarting();

    /** The season has started, with the world's saved data: reads the weather log. */
    void seasonStarted(MinecraftServer server, SeasonTick start);

    /** Once a server tick, after the season clock: logs the weather and works the snow depths out again. */
    void seasonTicked(ServerLevel overworld, SeasonTick now);

    /** At the end of each level's tick: the loaded and loading chunks' share of the work. */
    void levelTicked(ServerLevel level);

    void chunkLoaded(ServerLevel level, LevelChunk chunk);

    void chunkUnloaded(ServerLevel level, LevelChunk chunk);

    void serverStopped();

    /** Vanilla's weather placed snow or ice. */
    void weatherPlaced(ServerLevel level, BlockPos pos, BlockState before, BlockState placed);

    /** A player placed a block. */
    void playerPlaced(Level level, BlockPos pos, BlockState placed);

    /**
     * Whether vanilla's snowfall may add a layer to a column of a climate that has {@code layersHere} now: only while
     * the column's share of the season's snow has more layers for it. True while no season runs.
     */
    boolean snowMayFall(BlockPos pos, Climate climate, int layersHere);

    /**
     * The overworld's snow and ice now, and how they are moving; null while no season runs. Under regional weather
     * (from 0.4.0) the snow differs from place to place, and this is the snow at the world's spawn.
     */
    @Nullable SnowReading reading();

    /**
     * The snow and ice at a column now and how they move there: under regional weather the snow nodes' around it,
     * otherwise the same as {@link #reading}. Null while no season runs.
     */
    @Nullable SnowReading readingAt(double x, double z);

    /** Where snow lies now; null while no season runs. */
    @Nullable SnowNow now();

    /**
     * The layers of snow begun in each climate as far terrain counts them: {@link SnowNow#layers}, and under regional
     * weather at least one in a climate whose snow may lie anywhere, even where no loaded land has any. Null while no
     * season runs.
     */
    @Nullable Map<Climate, Integer> farLayers();

    /** The work queues, for {@code /almanac snow}; null while no server runs. */
    @Nullable String describeQueues();

    FarSnowLines farRule();

    /**
     * The weather log as the server sends it to clients (from 0.4.0), for their own snow by area; null while no season
     * runs. {@link #logChanges} says when it is worth sending again.
     */
    @Nullable CompoundTag logForClients();

    /** Counts up whenever the weather log changes. */
    long logChanges();

    /** A client received the server's weather log. */
    void clientReceivedLog(CompoundTag log);

    /** Once a client tick, in the overworld: works out the snow of the places asked about, nearest the camera first. */
    void clientTicked(Level level, double cameraX, double cameraZ);

    void clientDisconnected();

    /**
     * Each climate's snow depth at a column, on whichever side asks: on the server for loaded land, on a client from
     * the log the server sent, worked out on its background thread (the first ask starts it, so a place answers soon
     * after). Without regional weather it is the climate's one depth. Null while it isn't known.
     */
    @Nullable Map<Climate, Double> depthsAt(Level level, double x, double z);
}
