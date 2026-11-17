package com.thenathe.almanac.snow;

import com.thenathe.almanac.climate.SeasonalClimate;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.season.ServerSeasons;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * Keeps the overworld's snow and ice in step with the season.
 *
 * <ul>
 *   <li>A chunk that loads behind the current {@link SnowState} (new, from before Almanac, or unloaded while the
 *   season moved on) is brought up to date at once, a budgeted number of chunks per tick, at the end of the tick so
 *   most are done before they are sent. In thaw time ({@link SnowRules#isThawTime}) it keeps the snow and ice it
 *   should lose, and thaws like a loaded chunk.</li>
 *   <li>When the state changes, chunks that stay loaded move towards it gradually: a budgeted number of columns per
 *   tick, a layer at a time, in a scattered order. Snow and ice go at the pace of a thaw ({@link ThawPace}), which
 *   starts for every loaded chunk when the change can take snow away, and for a chunk held back on load when it
 *   loads.</li>
 * </ul>
 * A chunk's record says which state it last reached, so an up-to-date chunk costs one lookup.
 */
public final class SnowUpdates {
    private static final long NO_CHUNK = Long.MAX_VALUE;
    /** Visits the 256 columns of a chunk in a scattered order: 167 is odd, so every column comes up once. */
    private static final int[] COLUMN_ORDER = new int[256];

    static {
        for (int index = 0; index < 256; index++) {
            COLUMN_ORDER[index] = index * 167 & 255;
        }
    }

    private static @Nullable SnowUpdates current;

    private final LongLinkedOpenHashSet loaded = new LongLinkedOpenHashSet();
    private final LongLinkedOpenHashSet catchUp = new LongLinkedOpenHashSet();
    private final LongLinkedOpenHashSet gradual = new LongLinkedOpenHashSet();
    /** Thaws under way, by chunk. Kept in memory only: after a restart, chunks that still have snow to lose start again. */
    private final Long2ObjectOpenHashMap<ThawPace.Window> thaws = new Long2ObjectOpenHashMap<>();
    private @Nullable SnowState state;
    private long passChunk = NO_CHUNK;
    private int passColumn;
    private boolean passSettled;
    private long caughtUp;

    private SnowUpdates() {
    }

    /** Called as the server starts, before the spawn chunks load. */
    public static void serverStarting() {
        current = new SnowUpdates();
    }

    public static void serverStopped() {
        current = null;
    }

    public static @Nullable SnowUpdates get() {
        return current;
    }

    public static void chunkLoaded(ServerLevel level, LevelChunk chunk) {
        // Mods that load chunks off the server thread still get their chunks queued, on the server thread.
        if (!level.getServer().isSameThread()) {
            level.getServer().execute(() -> chunkLoaded(level, chunk));
            return;
        }
        SnowUpdates updates = current;
        if (updates != null && level.dimension() == Level.OVERWORLD) {
            long pos = chunk.getPos().pack();
            updates.loaded.add(pos);
            updates.catchUp.add(pos);
        }
    }

    public static void chunkUnloaded(ServerLevel level, LevelChunk chunk) {
        if (!level.getServer().isSameThread()) {
            level.getServer().execute(() -> chunkUnloaded(level, chunk));
            return;
        }
        SnowUpdates updates = current;
        if (updates != null && level.dimension() == Level.OVERWORLD) {
            long pos = chunk.getPos().pack();
            updates.loaded.remove(pos);
            updates.catchUp.remove(pos);
            updates.gradual.remove(pos);
            updates.thaws.remove(pos);
            if (updates.passChunk == pos) {
                updates.passChunk = NO_CHUNK;
            }
        }
    }

    public static void tick(ServerLevel level) {
        SnowUpdates updates = current;
        SeasonalClimate climate = ServerSeasons.climate(level);
        ServerSeasons seasons = ServerSeasons.get();
        if (updates != null && climate != null && seasons != null) {
            updates.tick(level, climate, seasons.snowState(), seasons.thawTicks());
        }
    }

    private void tick(ServerLevel level, SeasonalClimate climate, SnowState now, long thawTicks) {
        ServerConfig config = ServerConfig.get();
        if (!now.equals(state)) {
            if (now.mayThawFrom(state)) {
                startThaws(level.getGameTime(), thawTicks);
            }
            state = now;
            gradual.addAll(loaded);
            // Columns visited before the change were moved towards the old state, so this pass can't settle.
            passSettled = false;
        }
        ChunkSnowUpdater updater = new ChunkSnowUpdater(level, climate, now);
        catchUp(level, updater, now, config, thawTicks);
        gradual(level, updater, now, config.snowColumnsPerTick, thawTicks);
    }

    /**
     * Starts a thaw in every loaded chunk that isn't already thawing, all from the same moment so that neighbouring
     * chunks keep step. A thaw still under way carries on to its own end.
     */
    private void startThaws(long now, long thawTicks) {
        for (LongIterator chunks = loaded.iterator(); chunks.hasNext(); ) {
            startThaw(chunks.nextLong(), now, thawTicks);
        }
    }

    private ThawPace.Window startThaw(long pos, long now, long thawTicks) {
        ThawPace.Window window = thaws.get(pos);
        if (window == null || window.isOver(now)) {
            window = ThawPace.Window.startingAt(now, thawTicks);
            thaws.put(pos, window);
        }
        return window;
    }

    /** Lets snow and ice go from a chunk at the pace of its thaw, starting one if it has none. */
    private Thaw pacedThaw(ServerLevel level, long pos, long thawTicks) {
        long seed = level.getSeed();
        return spot -> {
            long now = level.getGameTime();
            ThawPace.Window window = thaws.get(pos);
            if (window == null) {
                window = startThaw(pos, now, thawTicks);
            }
            return ThawPace.reached(seed, spot.getX(), spot.getZ(), window.progress(now));
        };
    }

    private void catchUp(ServerLevel level, ChunkSnowUpdater updater, SnowState now, ServerConfig config, long thawTicks) {
        int budget = config.catchUpChunksPerTick;
        Thaw onLoad = Thaw.onLoad(now.subSeason());
        while (budget > 0 && !catchUp.isEmpty()) {
            long pos = catchUp.removeFirstLong();
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(pos), ChunkPos.getZ(pos));
            if (chunk == null) {
                continue;
            }
            ChunkSnow existing = SnowTracking.existingRecord(chunk);
            if (existing != null && existing.isUpTo(now)) {
                continue;
            }
            if (!config.snowCatchUp) {
                gradual.add(pos);
                continue;
            }
            boolean settled = updater.catchUp(chunk, SnowTracking.record(chunk), onLoad);
            if (settled) {
                gradual.remove(pos);
                thaws.remove(pos);
                if (passChunk == pos) {
                    passChunk = NO_CHUNK;
                }
            } else {
                // Thaw time: the chunk keeps what it should lose and thaws from now, like the loaded chunks did.
                startThaw(pos, level.getGameTime(), thawTicks);
                gradual.add(pos);
            }
            caughtUp++;
            budget--;
        }
    }

    private void gradual(ServerLevel level, ChunkSnowUpdater updater, SnowState now, int columns, long thawTicks) {
        // Each queued chunk starts at most one pass a tick, so chunks waiting on their thaw aren't scanned over and
        // over within a tick.
        int passesLeft = gradual.size();
        while (columns > 0) {
            if (passChunk == NO_CHUNK) {
                if (gradual.isEmpty() || passesLeft == 0) {
                    return;
                }
                passesLeft--;
                passChunk = gradual.removeFirstLong();
                passColumn = 0;
                passSettled = true;
            }
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(passChunk), ChunkPos.getZ(passChunk));
            if (chunk == null) {
                passChunk = NO_CHUNK;
                continue;
            }
            ChunkSnow record = SnowTracking.record(chunk);
            if (passColumn == 0 && record.isUpTo(now)) {
                thaws.remove(passChunk);
                passChunk = NO_CHUNK;
                continue;
            }
            Thaw thaw = pacedThaw(level, passChunk, thawTicks);
            while (columns > 0 && passColumn < 256) {
                int column = COLUMN_ORDER[passColumn++];
                passSettled &= updater.column(chunk, record, column & 15, column >> 4, true, thaw);
                columns--;
            }
            if (passColumn == 256) {
                passSettled &= updater.records(chunk, record, true, thaw);
                if (passSettled) {
                    record.markUpdated(now, level.getGameTime());
                    chunk.markUnsaved();
                    thaws.remove(passChunk);
                } else {
                    gradual.add(passChunk);
                }
                passChunk = NO_CHUNK;
            }
        }
    }

    /**
     * For {@code /almanac snow}: chunks waiting to be brought up to date, waiting for gradual work, and loaded, and
     * chunks with a thaw under way.
     */
    public String describeQueues() {
        return "catch-up queue " + catchUp.size() + ", gradual queue " + (gradual.size() + (passChunk == NO_CHUNK ? 0 : 1))
                + ", loaded chunks " + loaded.size() + ", brought up to date on load so far " + caughtUp
                + ", chunks thawing " + thaws.size();
    }
}
