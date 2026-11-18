package com.thenathe.almanac.snow;

import com.thenathe.almanac.Almanac;
import com.thenathe.almanac.climate.SeasonalClimate;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.season.ServerSeasons;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.PriorityQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the overworld's snow and ice in step with the season.
 *
 * <ul>
 *   <li>A chunk that loads behind the current {@link SnowState} (new, from before Almanac, or unloaded while the
 *   season moved on) is brought up to date at once, a budgeted number of chunks per tick, at the end of the tick so
 *   most are done before they are sent. In thaw time ({@link SnowRules#isThawTime}) it keeps the snow and ice it
 *   should lose, and thaws like a loaded chunk: with a thaw it was saved with, with its neighbours' thaw, or from
 *   the moment it loads.</li>
 *   <li>When the state changes, chunks that stay loaded move towards it gradually: a budgeted number of columns per
 *   tick, a layer at a time, in a scattered order. Snow and ice go at the pace of a thaw, and snow comes at the pace
 *   of a forming window of the same length ({@link ThawPace}); both start for every loaded chunk when the change can
 *   take snow away or bring it.</li>
 *   <li>A chunk with nothing to do but wait for its thaw or forming sleeps until the next patch is due.</li>
 * </ul>
 * A chunk's record says which state it last reached and which climates it holds, so an up-to-date chunk costs one
 * lookup.
 */
public final class SnowUpdates {
    private static final Logger LOGGER = LoggerFactory.getLogger(Almanac.MOD_ID);
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
    /**
     * Thaws under way in loaded chunks, by chunk. A chunk with snow or ice waiting for its thaw also keeps it in its
     * record, so the thaw carries on after a restart or a reload.
     */
    private final Long2ObjectOpenHashMap<ThawPace.Window> thaws = new Long2ObjectOpenHashMap<>();
    /** Snow forming in loaded chunks, by chunk. In memory only: a chunk that loads gets its snow at once. */
    private final Long2ObjectOpenHashMap<ThawPace.Window> formings = new Long2ObjectOpenHashMap<>();
    /** Chunks waiting for their next patch, with the game tick it is due. */
    private final Long2LongOpenHashMap sleeping = new Long2LongOpenHashMap();
    private final PriorityQueue<Wake> wakes = new PriorityQueue<>(Comparator.comparingLong(Wake::tick));
    /** Chunks whose snow work failed; they are left alone until the server restarts. */
    private final LongOpenHashSet failed = new LongOpenHashSet();
    private @Nullable SnowState state;
    private ChunkSnowUpdater.@Nullable Pass pass;
    private @Nullable ChunkPace passPace;
    private long passChunk = NO_CHUNK;
    /**
     * How many passes have changed a block, and the count each queued chunk saw at its last pass: a chunk whose pass
     * changes nothing while nothing anywhere has changed since its last one can't get any further.
     */
    private long changingPasses;
    private final Long2LongOpenHashMap changingPassesSeen = new Long2LongOpenHashMap();
    private long caughtUp;
    // What the snow work has cost since the server started, for /almanac snow.
    private long catchUpNanos;
    private long gradualNanos;
    private long columnsVisited;
    private long passesFinished;

    private record Wake(long tick, long chunk) {
    }

    private SnowUpdates() {
        changingPassesSeen.defaultReturnValue(-1L);
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
        // Mods that load chunks off the server thread still get their chunks queued, on the server thread, as long
        // as the chunk is still loaded by then.
        if (!level.getServer().isSameThread()) {
            level.getServer().execute(() -> {
                if (level.getChunkSource().getChunkNow(chunk.getPos().x(), chunk.getPos().z()) == chunk) {
                    chunkLoaded(level, chunk);
                }
            });
            return;
        }
        SnowUpdates updates = current;
        if (updates != null && level.dimension() == Level.OVERWORLD) {
            long pos = chunk.getPos().pack();
            if (!updates.failed.contains(pos)) {
                updates.loaded.add(pos);
                updates.catchUp.add(pos);
            }
        }
    }

    public static void chunkUnloaded(ServerLevel level, LevelChunk chunk) {
        if (!level.getServer().isSameThread()) {
            level.getServer().execute(() -> chunkUnloaded(level, chunk));
            return;
        }
        SnowUpdates updates = current;
        if (updates != null && level.dimension() == Level.OVERWORLD) {
            updates.forget(chunk.getPos().pack());
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
        long gameTime = level.getGameTime();
        if (!now.equals(state)) {
            if (now.mayThawFrom(state)) {
                startThaws(gameTime, thawTicks);
            }
            if (now.mayFormFrom(state)) {
                startForming(gameTime, thawTicks);
            }
            state = now;
            gradual.addAll(loaded);
            sleeping.clear();
            wakes.clear();
            changingPassesSeen.clear();
            if (pass != null) {
                // Columns visited before the change were moved towards the old state, so this pass can't settle.
                pass.moreToDo = true;
            }
        }
        ChunkSnowUpdater updater = new ChunkSnowUpdater(level, climate, now);
        long started = System.nanoTime();
        catchUp(level, updater, now, config, thawTicks);
        long caughtUpAt = System.nanoTime();
        // While the game is frozen (/tick freeze) loaded chunks wait with it. Chunks that load are still brought up to
        // date, since they are on their way to players.
        if (level.tickRateManager().runsNormally()) {
            wake(gameTime);
            gradual(level, updater, now, config.snowColumnsPerTick, thawTicks);
        }
        catchUpNanos += caughtUpAt - started;
        gradualNanos += System.nanoTime() - caughtUpAt;
    }

    /**
     * Starts a thaw in every loaded chunk that isn't already thawing, all from the same moment so that neighbouring
     * chunks keep step. A thaw still under way carries on to its own end.
     */
    private void startThaws(long now, long thawTicks) {
        for (LongIterator chunks = loaded.iterator(); chunks.hasNext(); ) {
            long pos = chunks.nextLong();
            ThawPace.Window window = thaws.get(pos);
            if (window == null || window.isOver(now)) {
                thaws.put(pos, ThawPace.Window.startingAt(now, thawTicks));
            }
        }
    }

    /**
     * Starts the snow forming in every loaded chunk from now, replacing any under way: each new snowfall builds up from
     * small patches again.
     */
    private void startForming(long now, long thawTicks) {
        ThawPace.Window window = ThawPace.Window.startingAt(now, thawTicks);
        for (LongIterator chunks = loaded.iterator(); chunks.hasNext(); ) {
            formings.put(chunks.nextLong(), window);
        }
    }

    /**
     * The thaw for a chunk that has snow or ice to lose and no thaw of its own here: see {@link ThawPace#join}. Its
     * neighbours' thaw is the user's choice (2026-11-17, via the audit); chunks far from anything loaded still start
     * their own from the moment they load.
     */
    private ThawPace.Window joinThaw(long pos, ChunkSnow record, long now, long thawTicks) {
        ThawPace.Window window = ThawPace.join(record.thaw(), thaws.get(pos), neighbourThaw(pos, now), now, thawTicks);
        thaws.put(pos, window);
        return window;
    }

    private ThawPace.@Nullable Window neighbourThaw(long pos, long now) {
        int x = ChunkPos.getX(pos);
        int z = ChunkPos.getZ(pos);
        List<ThawPace.Window> around = new ArrayList<>(8);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    around.add(thaws.get(ChunkPos.pack(x + dx, z + dz)));
                }
            }
        }
        return ThawPace.earliestUnderWay(around, now);
    }

    /** Keeps a chunk's thaw in its record while it has snow or ice waiting for it. */
    private static void saveThaw(LevelChunk chunk, ChunkSnow record, ThawPace.Window window) {
        if (!window.equals(record.thaw())) {
            record.setThaw(window);
            chunk.markUnsaved();
        }
    }

    private void catchUp(ServerLevel level, ChunkSnowUpdater updater, SnowState now, ServerConfig config, long thawTicks) {
        int budget = config.catchUpChunksPerTick;
        Pace onLoad = Pace.onLoad(now.subSeason());
        while (budget > 0 && !catchUp.isEmpty()) {
            long pos = catchUp.removeFirstLong();
            LevelChunk chunk = loadedChunk(level, pos);
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
            ChunkSnow record = SnowTracking.record(chunk);
            ChunkSnowUpdater.Pass done;
            try {
                done = updater.catchUp(chunk, record, onLoad);
            } catch (RuntimeException failure) {
                failed(pos, failure);
                continue;
            }
            if (done.settled()) {
                finished(pos, chunk, record);
            } else {
                // Thaw time: the chunk keeps what it should lose and thaws like the loaded chunks.
                saveThaw(chunk, record, joinThaw(pos, record, level.getGameTime(), thawTicks));
                gradual.add(pos);
            }
            caughtUp++;
            budget--;
        }
    }

    private void gradual(ServerLevel level, ChunkSnowUpdater updater, SnowState now, int columns, long thawTicks) {
        // Each queued chunk starts at most one pass a tick, so short queues aren't scanned over and over within a tick.
        int passesLeft = gradual.size();
        while (columns > 0) {
            if (pass == null) {
                if (gradual.isEmpty() || passesLeft == 0) {
                    return;
                }
                passesLeft--;
                long pos = gradual.removeFirstLong();
                LevelChunk chunk = loadedChunk(level, pos);
                if (chunk == null) {
                    continue;
                }
                ChunkSnow record = SnowTracking.record(chunk);
                if (record.isUpTo(now)) {
                    finished(pos, chunk, record);
                    continue;
                }
                passPace = new ChunkPace(level, pos, chunk, record, thawTicks);
                pass = new ChunkSnowUpdater.Pass(chunk, record, true, passPace);
                passChunk = pos;
            } else {
                // Read before the lookup: a chunk that has gone is forgotten there, which ends the pass.
                LevelChunk passing = pass.chunk;
                if (loadedChunk(level, passChunk) != passing) {
                    endPass();
                    continue;
                }
            }
            try {
                while (columns > 0 && pass.nextColumn < 256) {
                    int column = COLUMN_ORDER[pass.nextColumn++];
                    updater.column(pass, column & 15, column >> 4);
                    columns--;
                    columnsVisited++;
                }
                if (pass.nextColumn == 256) {
                    updater.records(pass);
                    passesFinished++;
                    passDone(updater);
                }
            } catch (RuntimeException failure) {
                failed(passChunk, failure);
            }
        }
    }

    /** Decides what comes next for the chunk whose pass just finished. */
    private void passDone(ChunkSnowUpdater updater) {
        ChunkSnowUpdater.Pass done = pass;
        ChunkPace pace = passPace;
        long pos = passChunk;
        endPass();
        if (done.changed) {
            changingPasses++;
        }
        boolean quietSinceLastPass = changingPassesSeen.put(pos, changingPasses) == changingPasses;
        switch (next(done.moreToDo, done.waiting, done.changed, quietSinceLastPass)) {
            case AGAIN -> gradual.add(pos);
            case SLEEP -> sleep(pos, pace.wakeAt);
            case DONE -> {
                if (done.moreToDo) {
                    LOGGER.debug("Almanac: the chunk at {} can't get any further with its snow work; counting it as "
                            + "up to date", ChunkPos.unpack(pos));
                }
                updater.settle(done);
                finished(pos, done.chunk, done.record);
            }
        }
    }

    enum Next {
        /** Back in the queue for another pass. */
        AGAIN,
        /** Asleep until the next patch of its thaw or snow forming is due. */
        SLEEP,
        /** Up to date until the state changes again. */
        DONE
    }

    /**
     * What comes after a chunk's pass. A pass that changed nothing, with nothing changed anywhere else since the
     * chunk's last pass, can't get any further: whatever it still waits for (water that never meets the ice, say)
     * won't come, so the chunk counts as done rather than being scanned again until spring. While other chunks still
     * change (ice spreading across a lake from the next chunk), it keeps going.
     */
    static Next next(boolean moreToDo, boolean waiting, boolean changed, boolean quietSinceLastPass) {
        boolean stuck = !changed && quietSinceLastPass;
        if (moreToDo && !stuck) {
            return Next.AGAIN;
        }
        return waiting ? Next.SLEEP : Next.DONE;
    }

    private void endPass() {
        pass = null;
        passPace = null;
        passChunk = NO_CHUNK;
    }

    private void sleep(long pos, long tick) {
        sleeping.put(pos, tick);
        wakes.add(new Wake(tick, pos));
    }

    /** Puts chunks whose next patch is due back in the queue. */
    private void wake(long now) {
        while (!wakes.isEmpty() && wakes.peek().tick() <= now) {
            Wake wake = wakes.poll();
            if (sleeping.containsKey(wake.chunk()) && sleeping.get(wake.chunk()) == wake.tick()) {
                sleeping.remove(wake.chunk());
                gradual.add(wake.chunk());
            }
        }
    }

    /** A chunk reached the state: it needs no thaw, forming or visits until the next change. */
    private void finished(long pos, LevelChunk chunk, ChunkSnow record) {
        gradual.remove(pos);
        changingPassesSeen.remove(pos);
        thaws.remove(pos);
        formings.remove(pos);
        sleeping.remove(pos);
        if (passChunk == pos) {
            endPass();
        }
        if (record.thaw() != null) {
            record.setThaw(null);
            chunk.markUnsaved();
        }
    }

    /** The chunk at {@code pos} if it is still loaded; a position whose chunk has gone is forgotten. */
    private @Nullable LevelChunk loadedChunk(ServerLevel level, long pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(pos), ChunkPos.getZ(pos));
        if (chunk == null) {
            forget(pos);
        }
        return chunk;
    }

    private void forget(long pos) {
        loaded.remove(pos);
        changingPassesSeen.remove(pos);
        catchUp.remove(pos);
        gradual.remove(pos);
        thaws.remove(pos);
        formings.remove(pos);
        sleeping.remove(pos);
        if (passChunk == pos) {
            endPass();
        }
    }

    /** One chunk's snow work failed: say where, loudly, and leave that chunk alone for the rest of the session. */
    private void failed(long pos, RuntimeException failure) {
        ChunkPos chunk = ChunkPos.unpack(pos);
        LOGGER.error("Almanac: the snow work failed in the chunk at {} (blocks {} {} to {} {}); its snow and ice are left "
                        + "as they are until the server restarts", chunk, chunk.getMinBlockX(), chunk.getMinBlockZ(),
                chunk.getMaxBlockX(), chunk.getMaxBlockZ(), failure);
        failed.add(pos);
        forget(pos);
    }

    /**
     * For {@code /almanac snow}: chunks waiting to be brought up to date, waiting for gradual work (some of them
     * asleep until their next patch), and loaded, and chunks with a thaw under way.
     */
    public String describeQueues() {
        int queued = gradual.size() + (pass == null ? 0 : 1) + sleeping.size();
        return "catch-up queue " + catchUp.size() + ", gradual queue " + queued + " (" + sleeping.size()
                + " waiting for their next patch), loaded chunks " + loaded.size() + ", brought up to date on load so far "
                + caughtUp + ", chunks thawing " + thaws.size() + ", snow forming " + formings.size()
                + (failed.isEmpty() ? "" : ", left alone after an error " + failed.size())
                + String.format(Locale.ROOT, "\nSnow work since the server started: catch-up %.1f ms (%d chunks), gradual %.1f ms"
                        + " (%d columns, %d passes)", catchUpNanos / 1e6, caughtUp, gradualNanos / 1e6, columnsVisited,
                        passesFinished);
    }

    /**
     * Paces a loaded chunk's changes by its thaw and its snow forming, and notes when the next spot that had to wait
     * is due.
     */
    private final class ChunkPace implements Pace {
        private final ServerLevel level;
        private final long pos;
        private final LevelChunk chunk;
        private final ChunkSnow record;
        private final long thawTicks;
        private long wakeAt = Long.MAX_VALUE;

        ChunkPace(ServerLevel level, long pos, LevelChunk chunk, ChunkSnow record, long thawTicks) {
            this.level = level;
            this.pos = pos;
            this.chunk = chunk;
            this.record = record;
            this.thawTicks = thawTicks;
        }

        @Override
        public boolean mayMelt(BlockPos spot) {
            long now = level.getGameTime();
            ThawPace.Window window = record.thaw();
            if (window == null || window.isOver(now)) {
                // A thaw that has ended here stays, so the chunk finishes instead of starting again.
                window = thaws.get(pos);
            }
            if (window == null) {
                // Snow to lose without a thaw started for it (catch-up turned off, say).
                window = joinThaw(pos, record, now, thawTicks);
            }
            if (reached(window, spot, now)) {
                return true;
            }
            saveThaw(chunk, record, window);
            return false;
        }

        @Override
        public boolean mayForm(BlockPos spot) {
            long now = level.getGameTime();
            ThawPace.Window window = formings.get(pos);
            if (window == null) {
                window = ThawPace.Window.startingAt(now, thawTicks);
                formings.put(pos, window);
            }
            return reached(window, spot, now);
        }

        private boolean reached(ThawPace.Window window, BlockPos spot, long now) {
            long seed = level.getSeed();
            if (ThawPace.reached(seed, spot.getX(), spot.getZ(), window.progress(now))) {
                return true;
            }
            wakeAt = Math.min(wakeAt, window.nextVisit(ThawPace.point(seed, spot.getX(), spot.getZ()), now));
            return false;
        }
    }
}
