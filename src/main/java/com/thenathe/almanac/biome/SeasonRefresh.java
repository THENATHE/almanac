package com.thenathe.almanac.biome;

import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.network.ServerNetworking;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.game.ClientboundChunksBiomesPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * After a sub-season change (or the copies being switched on or off), sends players without Almanac the new biomes of
 * the chunks they already have, through vanilla's chunk-biomes packet, nearest chunks first and a few per tick.
 * Chunks sent after the change already carry the new copies.
 *
 * <p>Each player gets up to {@code refreshChunksPerTick} chunks a tick, from one budget of
 * {@link #PLAYERS_PER_TICK} players' worth shared round-robin, so the cost stays the same however many players
 * there are; with more players than that, each waits a little longer for their turn.
 */
public final class SeasonRefresh {
    /** How many players' worth of chunks are refreshed per tick in all. */
    static final int PLAYERS_PER_TICK = 4;
    private static final Map<UUID, Deque<ChunkPos>> QUEUES = new HashMap<>();
    /** Players waiting for their turn, next first. */
    private static final Deque<UUID> TURNS = new ArrayDeque<>();

    private SeasonRefresh() {
    }

    public static void refreshAll(MinecraftServer server) {
        QUEUES.clear();
        TURNS.clear();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            queue(player);
        }
    }

    /**
     * Queues every chunk the player has, for players without Almanac in the overworld. That includes them while the
     * copies are switched off, so switching them off sends the real biomes back.
     */
    public static void queue(ServerPlayer player) {
        if (!needsRefresh(player)) {
            return;
        }
        List<ChunkPos> chunks = new ArrayList<>();
        player.getChunkTrackingView().forEach(chunks::add);
        ChunkPos centre = player.chunkPosition();
        chunks.sort(Comparator.comparingInt(centre::distanceSquared));
        if (QUEUES.put(player.getUUID(), new ArrayDeque<>(chunks)) == null) {
            TURNS.add(player.getUUID());
        }
    }

    private static boolean needsRefresh(ServerPlayer player) {
        return player.level().dimension() == Level.OVERWORLD && !ServerNetworking.hasAlmanac(player);
    }

    public static void forget(ServerPlayer player) {
        QUEUES.remove(player.getUUID());
        TURNS.remove(player.getUUID());
    }

    public static void tick(MinecraftServer server) {
        if (QUEUES.isEmpty()) {
            return;
        }
        int perTick = ServerConfig.get().refreshChunksPerTick;
        for (UUID id : nextTurns(TURNS)) {
            if (refresh(server, id, perTick)) {
                TURNS.add(id);
            } else {
                QUEUES.remove(id);
            }
        }
    }

    /**
     * Who is refreshed this tick: up to {@link #PLAYERS_PER_TICK} players from the front of the line, taken off it.
     * The caller puts each back at the end while they still have chunks waiting, which makes it round-robin.
     */
    static <T> List<T> nextTurns(Deque<T> line) {
        List<T> turns = new ArrayList<>(PLAYERS_PER_TICK);
        while (turns.size() < PLAYERS_PER_TICK && !line.isEmpty()) {
            turns.add(line.poll());
        }
        return turns;
    }

    /**
     * Sends one player their next few chunks' biomes.
     *
     * @return whether the player still has chunks waiting
     */
    private static boolean refresh(MinecraftServer server, UUID id, int perTick) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        Deque<ChunkPos> queue = QUEUES.get(id);
        if (player == null || queue == null || !needsRefresh(player)) {
            return false;
        }
        ServerLevel level = player.level();
        List<LevelChunk> batch = new ArrayList<>(perTick);
        while (batch.size() < perTick && !queue.isEmpty()) {
            ChunkPos pos = queue.poll();
            // A chunk still waiting to be sent goes out whole, with the new biomes, so it needs no refresh.
            if (!player.getChunkTrackingView().contains(pos) || player.connection.chunkSender.isPending(pos.pack())) {
                continue;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x(), pos.z());
            if (chunk != null) {
                batch.add(chunk);
            }
        }
        if (!batch.isEmpty()) {
            player.connection.send(SeasonalBiomeView.build(player, level, () -> ClientboundChunksBiomesPacket.forChunks(batch)));
        }
        return !queue.isEmpty();
    }
}
