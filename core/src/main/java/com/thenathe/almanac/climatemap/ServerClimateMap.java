package com.thenathe.almanac.climatemap;

import com.thenathe.almanac.Almanac;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.internal.Services;
import com.thenathe.almanac.internal.SnowNow;
import com.thenathe.almanac.network.ClientCheck;
import com.thenathe.almanac.network.FarClimatePayloads;
import com.thenathe.almanac.season.ServerSeasons;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The climate map (docs/FAR-SNOW.md): the surface biomes around each player whose client has Almanac, in tiles of
 * {@link ClimateTile#SIZE} blocks, so the client can tell where far terrain should show snow.
 *
 * <ul>
 *   <li>Tiles are sampled from the generator on a thread of their own ({@link TileSampler}), a few at a time, nearest
 *   first. Cells inside chunks that are loaded when a tile is finished take the chunk's real surface biome instead.
 *   Nothing is loaded, generated or saved: biome sources are deterministic, so tiles live in memory only.</li>
 *   <li>Each player is sent the tiles within the radius, nearest first, within a byte budget per tick, and told to
 *   drop tiles once they are more than a tile beyond it.</li>
 *   <li>Season changes send no tiles: a small {@link FarClimatePayloads.State} says what far terrain shows now, and
 *   clients with Almanac 0.1.3 or later are told the game tick it last changed at
 *   ({@link FarClimatePayloads.SnowChanged}).</li>
 *   <li>The sampler is shared round-robin: each tick a different player's nearest missing tile goes first. A tile that
 *   fails to sample is tried again a few times, then left unknown, so the player's map can still finish.</li>
 * </ul>
 * Everything here runs on the server thread except the sampling.
 */
public final class ServerClimateMap {
    private static final Logger LOGGER = LoggerFactory.getLogger(Almanac.MOD_ID);
    /** Tiles sampled at once. Few, so a player who moves gets their new nearest tiles first. */
    private static final int MAX_PENDING = 4;
    /** Finished tiles packed on the server thread per tick, at most. */
    private static final int MAX_FINISHED_PER_TICK = 4;
    /** Tiles kept beyond those some player wants, for players coming back. */
    private static final int SPARE_TILES = 512;
    /** Times a tile is sampled before it is left out. */
    private static final int MAX_TRIES = 3;
    private static final long[] NOTHING = new long[0];
    private static @Nullable ServerClimateMap current;

    private final MinecraftServer server;
    private final ServerLevel overworld;
    private final Registry<Biome> biomes;
    private final TileSampler sampler;
    private final ExecutorService worker;
    /** Packed tiles ready to send, least recently used first. */
    private final Long2ObjectLinkedOpenHashMap<FarClimatePayloads.Tile> tiles = new Long2ObjectLinkedOpenHashMap<>();
    private final Long2ObjectOpenHashMap<CompletableFuture<int[]>> pending = new Long2ObjectOpenHashMap<>();
    /** Failed tries per tile; at {@link #MAX_TRIES} the tile is left out. */
    private final Long2IntOpenHashMap failures = new Long2IntOpenHashMap();
    private final Map<UUID, PlayerMap> players = new HashMap<>();
    /** Which player's missing tiles are sampled first this tick. */
    private int firstPlayer;
    /** The overworld game tick at which what far terrain shows last changed; -1 before the first state. */
    private long snowChangedTick = -1;
    /** The cell size of the tiles in use, as a shift; -1 while the map is off. */
    private int cellShift = -1;
    private FarClimatePayloads.@Nullable State lastState;

    private final AtomicLong sampledTiles = new AtomicLong();
    private final AtomicLong sampledCells = new AtomicLong();
    private final AtomicLong samplingNanos = new AtomicLong();
    private long ticks;
    private long tickNanos;
    private long slowestTickNanos;
    private long ticksOverOneMilli;
    private long ticksOverFiveMillis;

    private ServerClimateMap(MinecraftServer server) {
        this.server = server;
        this.overworld = server.overworld();
        this.biomes = overworld.registryAccess().lookupOrThrow(Registries.BIOME);
        this.sampler = new TileSampler(overworld);
        this.worker = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Almanac climate map");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        });
    }

    /** Called after the season has started. */
    public static void serverStarted(MinecraftServer server) {
        current = new ServerClimateMap(server);
        LOGGER.info("Almanac: the climate map samples the overworld's surface biomes by {}",
                current.sampler.method().name().toLowerCase(Locale.ROOT));
    }

    public static void serverStopped() {
        ServerClimateMap map = current;
        current = null;
        if (map != null) {
            map.worker.shutdownNow();
        }
    }

    public static @Nullable ServerClimateMap get() {
        return current;
    }

    /** Whether the player's client takes the climate map: not beside a LiteLights too old for it. */
    public static boolean takesClimateMap(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, FarClimatePayloads.State.TYPE)
                && ServerPlayNetworking.canSend(player, FarClimatePayloads.Tile.TYPE)
                && ServerPlayNetworking.canSend(player, FarClimatePayloads.Forget.TYPE);
    }

    public static void playerJoined(ServerPlayer player) {
        ServerClimateMap map = current;
        if (map != null && map.lastState != null && takesClimateMap(player)) {
            map.sendSnowChanged(player);
            ServerPlayNetworking.send(player, map.lastState);
        }
    }

    /** The overworld game tick at which what far terrain shows last changed, or -1 before it is known. */
    public long snowChangedTick() {
        return snowChangedTick;
    }

    public static void playerLeft(ServerPlayer player) {
        ServerClimateMap map = current;
        if (map != null) {
            map.players.remove(player.getUUID());
        }
    }

    public static void tick(MinecraftServer server) {
        ServerClimateMap map = current;
        if (map != null && map.server == server) {
            map.tick();
        }
    }

    private void tick() {
        long started = System.nanoTime();
        ServerConfig config = ServerConfig.get();
        sendState(config);
        if (!config.farClimateMap) {
            if (cellShift != -1) {
                stop();
            }
        } else {
            int shift = Integer.numberOfTrailingZeros(config.farClimateCellSize);
            if (shift != cellShift) {
                start(shift);
            }
            collectFinished();
            List<long[]> missing = new ArrayList<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                long[] wanted = serve(player, config);
                if (wanted.length > 0) {
                    missing.add(wanted);
                }
            }
            if (!missing.isEmpty()) {
                for (long key : TilePlan.fairOrder(missing, firstPlayer++, MAX_PENDING - pending.size())) {
                    request(key);
                }
            }
            if (server.getTickCount() % 100 == 0) {
                evict();
            }
        }
        long spent = System.nanoTime() - started;
        ticks++;
        tickNanos += spent;
        slowestTickNanos = Math.max(slowestTickNanos, spent);
        if (spent > 1_000_000L) {
            ticksOverOneMilli++;
        }
        if (spent > 5_000_000L) {
            ticksOverFiveMillis++;
        }
    }

    /** Sends what far terrain shows to every client that takes the map, when it changed. */
    private void sendState(ServerConfig config) {
        ServerSeasons seasons = ServerSeasons.get();
        if (seasons == null) {
            return;
        }
        SnowNow snow = Services.snow().now();
        Map<Climate, Integer> begun = Services.snow().farLayers();
        if (snow == null || begun == null) {
            return;
        }
        SubSeason shown = Services.snow().farRule().shownSubSeason(seasons.subSeason(), begun);
        int[] layers = new int[Climate.values().length];
        for (Climate climate : Climate.values()) {
            layers[climate.ordinal()] = begun.getOrDefault(climate, 0);
        }
        FarClimatePayloads.State state = new FarClimatePayloads.State(config.farClimateMap, shown, layers,
                snow.nativeSnowMelts(), snow.naturalHeight());
        if (state.equals(lastState)) {
            return;
        }
        // Snow depth can change without changing what far terrain shows (a melt from two layers to one): the state is
        // still sent and its event still fires, but the tick only moves when the far rule's answers do.
        boolean farSnowChanged = !state.sameFarRule(lastState);
        if (farSnowChanged) {
            // Saved with the season, so a restart doesn't count as a change.
            snowChangedTick = seasons.farSnowChanged(state.farRuleFingerprint(), state.farSnowFingerprint(),
                    overworld.getGameTime());
        }
        lastState = state;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (takesClimateMap(player)) {
                // Before the state, so listeners to the change it brings already read the new tick.
                if (farSnowChanged) {
                    sendSnowChanged(player);
                }
                ServerPlayNetworking.send(player, state);
            }
        }
    }

    private void sendSnowChanged(ServerPlayer player) {
        if (snowChangedTick >= 0 && ServerPlayNetworking.canSend(player, FarClimatePayloads.SnowChanged.TYPE)) {
            ServerPlayNetworking.send(player, new FarClimatePayloads.SnowChanged(snowChangedTick));
        }
    }

    /**
     * Starts the map, or starts it again with another cell size: every tile is sampled and sent again, and players
     * who had tiles at the old size are told to drop them first, so none is left answering for its area.
     */
    private void start(int shift) {
        boolean restart = cellShift != -1;
        clearTiles();
        cellShift = shift;
        for (Map.Entry<UUID, PlayerMap> entry : players.entrySet()) {
            PlayerMap map = entry.getValue();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (restart && player != null && !map.sent.isEmpty()) {
                ServerPlayNetworking.send(player, new FarClimatePayloads.Forget(true, NOTHING));
            }
            map.sent.clear();
            map.complete = false;
        }
    }

    /** Turned off: the state already told clients to drop their tiles. */
    private void stop() {
        clearTiles();
        cellShift = -1;
        players.clear();
    }

    private void clearTiles() {
        tiles.clear();
        pending.values().forEach(future -> future.cancel(false));
        pending.clear();
        failures.clear();
    }

    private boolean gaveUpOn(long key) {
        return failures.get(key) >= MAX_TRIES;
    }

    /**
     * Sends the player their ready tiles, nearest first, within the byte budget.
     *
     * @return the player's nearest tiles that still need sampling, at most {@link #MAX_PENDING}
     */
    private long[] serve(ServerPlayer player, ServerConfig config) {
        if (!takesClimateMap(player)) {
            return NOTHING;
        }
        PlayerMap map = players.computeIfAbsent(player.getUUID(), id -> new PlayerMap());
        if (player.level() != overworld) {
            // Only the overworld has a climate map; the client drops its tiles and gets them again on coming back.
            if (map.tileX != Integer.MIN_VALUE) {
                ServerPlayNetworking.send(player, new FarClimatePayloads.Forget(true, NOTHING));
                map.reset();
            }
            return NOTHING;
        }
        int tileX = ClimateTile.tileOf(player.getBlockX());
        int tileZ = ClimateTile.tileOf(player.getBlockZ());
        if (map.planned(tileX, tileZ, config.farClimateRadius)) {
            long[] forgotten = map.plan(tileX, tileZ, config.farClimateRadius);
            if (forgotten.length > 0) {
                ServerPlayNetworking.send(player, new FarClimatePayloads.Forget(false, forgotten));
            }
        }
        map.credit = Math.min(map.credit + config.farClimateBytesPerTick, config.farClimateBytesPerTick);
        if (map.complete) {
            return NOTHING;
        }
        boolean unsent = false;
        LongArrayList missing = new LongArrayList();
        for (long key : map.wanted) {
            if (map.sent.contains(key) || gaveUpOn(key)) {
                continue;
            }
            unsent = true;
            if (tiles.containsKey(key)) {
                if (map.credit > 0) {
                    FarClimatePayloads.Tile tile = tiles.getAndMoveToLast(key);
                    ServerPlayNetworking.send(player, tile);
                    int size = tile.sizeInBytes();
                    map.credit -= size;
                    map.bytesSent += size;
                    map.tilesSent++;
                    map.sent.add(key);
                }
            } else if (!pending.containsKey(key) && missing.size() < MAX_PENDING) {
                missing.add(key);
            }
            if (map.credit <= 0 && missing.size() >= MAX_PENDING) {
                break;
            }
        }
        map.complete = !unsent;
        return missing.toLongArray();
    }

    private void request(long key) {
        if (pending.size() >= MAX_PENDING || pending.containsKey(key) || gaveUpOn(key)) {
            return;
        }
        int shift = cellShift;
        pending.put(key, CompletableFuture.supplyAsync(() -> {
            long started = System.nanoTime();
            int[] ids = sampler.sample(ClimateTile.keyX(key), ClimateTile.keyZ(key), shift);
            samplingNanos.addAndGet(System.nanoTime() - started);
            sampledTiles.incrementAndGet();
            sampledCells.addAndGet(ids.length);
            return ids;
        }, worker));
    }

    /** Packs tiles the sampler has finished, a few a tick. */
    private void collectFinished() {
        int finished = 0;
        for (ObjectIterator<Long2ObjectMap.Entry<CompletableFuture<int[]>>> entries = pending.long2ObjectEntrySet().fastIterator();
             entries.hasNext() && finished < MAX_FINISHED_PER_TICK; ) {
            Long2ObjectMap.Entry<CompletableFuture<int[]>> entry = entries.next();
            // The fast iterator reuses its entry, so read it before removing it.
            long key = entry.getLongKey();
            CompletableFuture<int[]> sampling = entry.getValue();
            if (!sampling.isDone()) {
                continue;
            }
            entries.remove();
            finished++;
            try {
                int[] ids = sampling.join();
                useLoadedChunks(key, ids);
                ClimateTile tile = ClimateTile.ofBiomes(ClimateTile.keyX(key), ClimateTile.keyZ(key), cellShift, ids);
                tiles.put(key, FarClimatePayloads.Tile.of(tile));
            } catch (RuntimeException failure) {
                boolean firstFailure = failures.isEmpty();
                int tries = failures.addTo(key, 1) + 1;
                if (firstFailure) {
                    LOGGER.warn("Almanac: couldn't sample climate map tile {}, {}; trying it again", ClimateTile.keyX(key),
                            ClimateTile.keyZ(key), failure);
                }
                if (tries == MAX_TRIES && givenUp() == 1) {
                    LOGGER.warn("Almanac: gave up on climate map tile {}, {} after {} tries; far terrain there stays as "
                            + "captured (/almanac snow counts tiles given up on)", ClimateTile.keyX(key), ClimateTile.keyZ(key), tries);
                }
            }
        }
    }

    /** Cells inside loaded chunks take the chunk's own biome at its surface, which is what the world really has. */
    private void useLoadedChunks(long key, int[] ids) {
        int cellSize = 1 << cellShift;
        int side = ClimateTile.SIZE >> cellShift;
        int minX = ClimateTile.keyX(key) << ClimateTile.SIZE_SHIFT;
        int minZ = ClimateTile.keyZ(key) << ClimateTile.SIZE_SHIFT;
        for (int cellZ = 0; cellZ < side; cellZ++) {
            for (int cellX = 0; cellX < side; cellX++) {
                int x = minX + cellX * cellSize + cellSize / 2;
                int z = minZ + cellZ * cellSize + cellSize / 2;
                LevelChunk chunk = overworld.getChunkSource().getChunkNow(x >> 4, z >> 4);
                if (chunk != null) {
                    int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15) + 1;
                    Holder<Biome> biome = chunk.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(surface),
                            QuartPos.fromBlock(z));
                    ids[cellZ * side + cellX] = biomes.getId(biome.value());
                }
            }
        }
    }

    /** Drops tiles no player wants, least recently sent first, once there are more than the players want and a few. */
    private void evict() {
        int keep = SPARE_TILES;
        LongOpenHashSet wanted = new LongOpenHashSet();
        for (PlayerMap map : players.values()) {
            keep += map.wanted.length;
            for (long key : map.wanted) {
                wanted.add(key);
            }
        }
        for (LongIterator keys = tiles.keySet().iterator(); keys.hasNext() && tiles.size() > keep; ) {
            if (!wanted.contains(keys.nextLong())) {
                keys.remove();
            }
        }
    }

    /** For {@code /almanac snow}: the map's size, its cost and what each player has been sent. */
    public List<String> describe() {
        List<String> lines = new ArrayList<>();
        ServerConfig config = ServerConfig.get();
        if (cellShift == -1) {
            lines.add("Climate map: off");
            return lines;
        }
        long sampled = sampledTiles.get();
        double samplingMillis = samplingNanos.get() / 1e6;
        lines.add(String.format(Locale.ROOT, "Climate map: cells of %d blocks out to %d blocks, by %s; %d tiles ready, "
                        + "%d sampling, %d given up on", 1 << cellShift, config.farClimateRadius,
                sampler.method().name().toLowerCase(Locale.ROOT), tiles.size(), pending.size(), givenUp()));
        lines.add(String.format(Locale.ROOT, "  sampled %d tiles in %.1f ms (%.2f ms a tile, %.2f us a cell); "
                        + "server thread %.1f ms over %d ticks (%.4f ms a tick), slowest tick %.2f ms, %d ticks over 1 ms, "
                        + "%d over 5 ms", sampled, samplingMillis, sampled == 0 ? 0.0 : samplingMillis / sampled,
                sampledCells.get() == 0 ? 0.0 : samplingNanos.get() / 1e3 / sampledCells.get(),
                tickNanos / 1e6, ticks, ticks == 0 ? 0.0 : tickNanos / 1e6 / ticks, slowestTickNanos / 1e6,
                ticksOverOneMilli, ticksOverFiveMillis));
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlayerMap map = players.get(player.getUUID());
            if (!takesClimateMap(player)) {
                lines.add("  " + player.getName().getString() + ": no climate map (" + whyNoClimateMap(player) + ")");
            } else if (map != null) {
                lines.add(String.format(Locale.ROOT, "  %s: %d of %d tiles sent, %d tiles and %d bytes in all",
                        player.getName().getString(), countSent(map), map.wanted.length, map.tilesSent, map.bytesSent));
            }
        }
        return lines;
    }

    /**
     * Why a player's client doesn't take the map. Every client has this Almanac (from 0.2.0), so it is the client
     * turning it down beside a LiteLights too old for it, which the client says while connecting.
     */
    private static String whyNoClimateMap(ServerPlayer player) {
        return ClientCheck.oldLiteLights(player)
                .map(version -> "the client's LiteLights " + version + " is too old to share far terrain with this Almanac")
                .orElse("the client doesn't take it");
    }

    private long givenUp() {
        return failures.values().intStream().filter(tries -> tries >= MAX_TRIES).count();
    }

    private static int countSent(PlayerMap map) {
        int count = 0;
        for (long key : map.wanted) {
            if (map.sent.contains(key)) {
                count++;
            }
        }
        return count;
    }

    /** One player's share of the map: the tiles they should have, nearest first, and the ones they have. */
    private static final class PlayerMap {
        final LongOpenHashSet sent = new LongOpenHashSet();
        long[] wanted = new long[0];
        int tileX = Integer.MIN_VALUE;
        int tileZ = Integer.MIN_VALUE;
        int radius;
        int credit;
        boolean complete;
        long bytesSent;
        long tilesSent;

        void reset() {
            sent.clear();
            wanted = new long[0];
            tileX = Integer.MIN_VALUE;
            complete = false;
        }

        /** Whether the player has moved to another tile, or the radius changed, since the last plan. */
        boolean planned(int tileX, int tileZ, int radius) {
            return tileX != this.tileX || tileZ != this.tileZ || radius != this.radius || tileX == Integer.MIN_VALUE;
        }

        /**
         * Works out the tiles within the radius of the player's tile, nearest first, and forgets the sent tiles that
         * are now more than a tile beyond it.
         *
         * @return the forgotten tiles
         */
        long[] plan(int tileX, int tileZ, int radius) {
            this.tileX = tileX;
            this.tileZ = tileZ;
            this.radius = radius;
            wanted = TilePlan.nearestFirst(tileX, tileZ, radius);
            complete = false;

            LongArrayList forgotten = new LongArrayList();
            for (LongIterator keys = sent.iterator(); keys.hasNext(); ) {
                long key = keys.nextLong();
                if (TilePlan.dropped(key, tileX, tileZ, radius)) {
                    forgotten.add(key);
                    keys.remove();
                }
            }
            return forgotten.toLongArray();
        }
    }
}
