package com.thenathe.almanac.snow;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SnowLayers;
import com.thenathe.almanac.internal.AlmanacModule;
import com.thenathe.almanac.internal.BiomeClimate;
import com.thenathe.almanac.internal.ClimateNow;
import com.thenathe.almanac.internal.Services;
import com.thenathe.almanac.internal.SnowSettings;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the overworld's snow and ice in step with the season, from two budgets a tick.
 *
 * <ul>
 *   <li>A chunk that loads is brought up to date at once, at most {@code catchUpChunksPerTick} of them a tick, at the
 *   end of the tick so most are done before they are sent. One whose record already has the state now costs one
 *   lookup and nothing of the budget.</li>
 *   <li>Loaded chunks follow the state in rounds: whenever the state has moved (a new sub-season, a setting, or the
 *   shares or the ice cover by {@link #ROUND_STEP}), every loaded chunk is looked at once more, drawing on
 *   {@code snowColumnsPerTick}. Every look comes from that budget, the chunks the shares only moved past included
 *   (before 0.3.0 those were woken outside it).</li>
 * </ul>
 * Either way a chunk is brought from the state its record saved to the state now ({@link #bringUp}): where only the
 * amounts moved, just the columns whose points they moved past; otherwise every column. Each column goes straight to
 * its target ({@link ChunkSnowUpdater}), so a chunk is the same however it got there.
 *
 * <p>Under regional weather (from 0.4.0) the state now is each chunk's own, from the snow nodes around it
 * ({@link SeasonSnow#stateAt}). This asks for the nodes around every loaded chunk; a chunk whose nodes the background
 * thread hasn't worked out yet waits (usually a tick) and is brought up when they are. A round starts when a node's
 * snow has moved by {@link #ROUND_STEP}, and in it only the chunks whose own depths moved have anything to do, so only
 * land under a snowing storm or in a thaw does work.
 */
public final class SnowUpdates {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlmanacModule.MOD_ID);
    /**
     * A new round of the loaded chunks starts once a share or an ice cover has moved this much since the last one
     * began, or has come to an end (none, or all). About every 47 ticks at the default snowfall clock: a look then
     * finds about one column in a chunk to change.
     */
    static final double ROUND_STEP = 1.0 / 256.0;
    /**
     * What work costs, in sixteenths of the column budget: a column that changes is a whole one, a column with nothing
     * to change or a look at a chunk that needs nothing a sixteenth, and working out a chunk's freezing points (a few
     * hundred block reads) four columns.
     */
    static final int CHANGED_COLUMN = 16;
    static final int QUIET_COLUMN = 1;
    static final int LOOK = 1;
    static final int ICE_POINTS = 64;
    private static final int NOT_READY_RETRY_TICKS = 20;

    private static @Nullable SnowUpdates current;

    private final LongLinkedOpenHashSet loaded = new LongLinkedOpenHashSet();
    private final LongLinkedOpenHashSet catchUp = new LongLinkedOpenHashSet();
    private final Long2ObjectOpenHashMap<ChunkPoints> points = new Long2ObjectOpenHashMap<>();
    /**
     * Chunks that were queued but couldn't be read when their turn came (on their way in or out). They are asked for
     * again until they unload, so a chunk that comes back without loading anew isn't left behind.
     */
    private final LongOpenHashSet notReady = new LongOpenHashSet();
    /** Chunks whose snow work failed; they are left alone until the server restarts. */
    private final LongOpenHashSet failed = new LongOpenHashSet();
    /** Chunks waiting for their snow nodes to be worked out; they go back to catch-up when new nodes are in. */
    private final LongOpenHashSet waiting = new LongOpenHashSet();
    /** The snow nodes asked for around loaded land, and whether the loaded land changed since they were. */
    private final LongOpenHashSet wanted = new LongOpenHashSet();
    private boolean loadedChanged = true;
    private @Nullable SnowField seenField;
    /** The loaded chunks this round still has to look at, and the state (and snow nodes) it began with. */
    private long[] round = new long[0];
    private int roundNext;
    private @Nullable SnowState roundState;
    private @Nullable SnowField roundField;
    private long roundTick;
    /** A chunk loaded that catch-up left to the rounds (catch-up is off). */
    private boolean roundWanted;
    /** Budget a long look spent beyond the tick's, taken from the next ticks. */
    private int overspent;
    private long seed;
    private @Nullable SnowState state;
    private long gameTime;
    private @Nullable SeasonSnow season;
    // What the snow work has cost since the server started, for /almanac snow.
    private long caughtUp;
    private long restamped;
    private long rounds;
    private long looks;
    private long columnsVisited;
    private long columnsChanged;
    private long catchUpNanos;
    private long followNanos;

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

    /**
     * Whether vanilla's snowfall may add a layer to a column of a climate: while its point gets more layers than it
     * has. True without a season to go by.
     */
    public static boolean snowMayFall(BlockPos pos, Climate climate, int layersHere) {
        SnowUpdates updates = current;
        SeasonSnow snow = SeasonSnow.get();
        SnowState now = updates == null || updates.state == null || snow == null ? null
                : snow.stateAt(pos.getX() >> 4, pos.getZ() >> 4, updates.gameTime);
        if (now == null) {
            // No season, or the chunk's snow nodes aren't worked out yet: the chunk's own catch-up decides soon.
            return updates == null || updates.state == null;
        }
        SnowLayers shares = now.shares(climate);
        return shares.layersAt(ColumnPoints.scatter(updates.seed, pos.getX(), pos.getZ())) > layersHere;
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
                updates.loadedChanged = true;
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
        ClimateNow climate = Services.seasons().climate(level);
        SeasonSnow snow = SeasonSnow.get();
        if (updates != null && climate != null && snow != null) {
            updates.tick(level, climate, snow);
        }
    }

    private void tick(ServerLevel level, ClimateNow climate, SeasonSnow snow) {
        SnowSettings settings = Services.seasons().snowSettings();
        SnowState now = snow.state();
        seed = level.getSeed();
        state = now;
        gameTime = level.getGameTime();
        season = snow;
        if (snow.regional()) {
            askForNodes(snow);
        }
        // By the server's own ticks, which go on under /tick freeze, when the game time stands still: chunks that load
        // then are still brought up to date, and these are chunks that came back.
        if (!notReady.isEmpty() && level.getServer().getTickCount() % NOT_READY_RETRY_TICKS == 0) {
            catchUp.addAll(notReady);
            notReady.clear();
        }
        ChunkSnowUpdater updater = new ChunkSnowUpdater(level, climate, now);
        long started = System.nanoTime();
        catchUp(level, updater, settings);
        long caughtUpAt = System.nanoTime();
        // While the game is frozen (/tick freeze) loaded chunks wait with it. Chunks that load are still brought up to
        // date, since they are on their way to players.
        if (level.tickRateManager().runsNormally()) {
            follow(level, updater, settings.columnsPerTick() * CHANGED_COLUMN);
        }
        catchUpNanos += caughtUpAt - started;
        followNanos += System.nanoTime() - caughtUpAt;
    }

    /**
     * Asks for the snow nodes around every loaded chunk, and every chunk waiting to be brought up; when new nodes have
     * been worked out, the chunks that waited for them go back to catch-up.
     */
    private void askForNodes(SeasonSnow snow) {
        if (loadedChanged) {
            wanted.clear();
            for (long pos : loaded) {
                SnowField.nodesOf(ChunkPos.getX(pos), ChunkPos.getZ(pos), wanted);
            }
            loadedChanged = false;
            snow.want(wanted);
        }
        SnowField field = snow.field();
        if (field != seenField) {
            seenField = field;
            if (!waiting.isEmpty()) {
                catchUp.addAll(waiting);
                waiting.clear();
            }
        }
    }

    private void catchUp(ServerLevel level, ChunkSnowUpdater updater, SnowSettings settings) {
        int budget = settings.catchUpChunksPerTick();
        while (budget > 0 && !catchUp.isEmpty()) {
            long pos = catchUp.removeFirstLong();
            LevelChunk chunk = loadedChunk(level, pos);
            if (chunk == null) {
                continue;
            }
            if (!settings.catchUp()) {
                // Off: loaded land's rounds bring it up to date, from their budget.
                roundWanted = true;
                continue;
            }
            try {
                if (bringUp(level, updater, pos, chunk) > LOOK) {
                    caughtUp++;
                    budget--;
                }
            } catch (RuntimeException failure) {
                failed(pos, failure);
            }
        }
    }

    /** Looks at loaded chunks, a round at a time, while the tick's budget lasts. */
    private void follow(ServerLevel level, ChunkSnowUpdater updater, int budget) {
        int left = budget - overspent;
        if (roundNext >= round.length) {
            if (!roundDue(updater.state())) {
                overspent = Math.max(0, -left);
                return;
            }
            round = loaded.toLongArray();
            roundNext = 0;
            roundState = updater.state();
            roundField = season == null ? null : season.field();
            roundTick = gameTime;
            roundWanted = false;
            rounds++;
        }
        while (left > 0 && roundNext < round.length) {
            long pos = round[roundNext++];
            LevelChunk chunk = loadedChunk(level, pos);
            if (chunk == null) {
                continue;
            }
            try {
                left -= bringUp(level, updater, pos, chunk);
            } catch (RuntimeException failure) {
                failed(pos, failure);
            }
        }
        overspent = Math.max(0, -left);
    }

    /**
     * Whether loaded land needs another round: the state has moved since the last one began. Under regional weather a
     * climate's depth is the snow nodes', so it is theirs that are compared.
     */
    private boolean roundDue(SnowState now) {
        SnowState before = roundState;
        if (before == null || roundWanted || !now.sameFrame(before, null) || now.maxLayers() != before.maxLayers()) {
            return true;
        }
        SnowField field = season == null ? null : season.field();
        boolean regional = season != null && season.regional();
        if (regional && field != null && roundField != null && field.movedSince(roundField, roundTick, gameTime)) {
            return true;
        }
        if (regional && (field == null) != (roundField == null)) {
            return true;
        }
        for (Climate climate : Climate.values()) {
            if ((!regional && movedEnough(before.depth(climate), now.depth(climate), now.maxLayers()))
                    || movedEnough(before.ice(climate), now.ice(climate), 1.0)) {
                return true;
            }
        }
        return false;
    }

    static boolean movedEnough(double before, double now, double most) {
        double moved = Math.abs(now - before);
        return moved >= ROUND_STEP || (moved > 0.0 && (now == 0.0 || now == most));
    }

    /**
     * Brings one loaded chunk from the state its record saved to the state now.
     *
     * @return what it cost, in sixteenths of a column
     */
    private int bringUp(ServerLevel level, ChunkSnowUpdater worldUpdater, long pos, LevelChunk chunk) {
        looks++;
        SnowState now = season == null ? worldUpdater.state() : season.stateAt(ChunkPos.getX(pos), ChunkPos.getZ(pos), gameTime);
        if (now == null) {
            // Its snow nodes aren't worked out yet: it waits for them, at no cost.
            waiting.add(pos);
            return 0;
        }
        ChunkSnowUpdater updater = now == worldUpdater.state() ? worldUpdater : worldUpdater.withState(now);
        ChunkSnow existing = SnowTracking.existingRecord(chunk);
        SnowState before = existing == null ? null : existing.state();
        Set<Climate> held = existing == null ? null : existing.climates();
        boolean sameFrame = before != null && (now.sameFrame(before, held)
                || (now.sameFrameBesidesNoSeasonalSnow(before, held)
                && !seasonalSnowWhereItNeverSnows(level, updater.climate(), chunk, existing)));
        if (sameFrame && now.sameAmounts(before, held)) {
            if (!now.sameFrame(before, held)) {
                // Only the biomes kept free of the season's snow differ, and none of its snow lies in them: the chunk
                // counts as up to date as it is, once.
                existing.restamp(now);
                chunk.markUnsaved();
                restamped++;
            }
            // Otherwise only what this chunk doesn't hold moved: nothing to do, and nothing to save.
            return LOOK;
        }
        ChunkPoints chunkPoints = points(pos);
        ChunkSnow record = existing == null ? SnowTracking.record(chunk) : existing;
        ChunkPoints.ColumnSet columns = null;
        int cost = LOOK;
        if (sameFrame) {
            Set<Climate> climates = held == null ? EnumSet.allOf(Climate.class) : held;
            columns = new ChunkPoints.ColumnSet();
            for (Climate climate : climates) {
                for (int layer = 1; layer <= Math.max(before.maxLayers(), now.maxLayers()); layer++) {
                    double a = before.share(climate, layer);
                    double b = now.share(climate, layer);
                    chunkPoints.markBetween(Math.min(a, b), Math.max(a, b), columns);
                }
                double iceWas = before.ice(climate);
                double iceIs = now.ice(climate);
                if (iceWas != iceIs) {
                    double[] icePoints = chunkPoints.icePoints(level.getGameTime());
                    if (icePoints == null) {
                        icePoints = updater.icePoints(chunk);
                        chunkPoints.setIcePoints(icePoints, level.getGameTime());
                        cost += ICE_POINTS;
                    }
                    ChunkPoints.markIceBetween(icePoints, Math.min(iceWas, iceIs), Math.max(iceWas, iceIs), columns);
                }
            }
            if (columns.isEmpty()) {
                // The amounts moved, but past none of this chunk's points. The record follows in memory; the chunk
                // isn't saved for it, since a record left behind only means a few more columns looked at next time.
                record.restamp(now);
                restamped++;
                return cost;
            }
        }
        ChunkSnowUpdater.Visit visit = updater.visit(chunk, record, chunkPoints, columns);
        Set<Climate> climates = EnumSet.noneOf(Climate.class);
        climates.addAll(visit.climates);
        if (columns != null && held != null) {
            // Only some columns were looked at; the rest hold what they held.
            climates.addAll(held);
        }
        updater.settle(record, chunk, climates);
        columnsVisited += visit.columns;
        columnsChanged += visit.changedColumns;
        return cost + visit.changedColumns * CHANGED_COLUMN + (visit.columns - visit.changedColumns) * QUIET_COLUMN;
    }

    /** Whether the season's recorded snow or ice lies in a biome the season no longer brings snow to. */
    private static boolean seasonalSnowWhereItNeverSnows(ServerLevel level, ClimateNow climate, LevelChunk chunk,
                                                         ChunkSnow record) {
        if (!record.holdsSeasonalSnowOrIce()) {
            return false;
        }
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int[] keys : new int[][] {record.modSnowPositions(), record.modIcePositions()}) {
            for (int key : keys) {
                pos.set(minX + ChunkSnow.localX(key), ChunkSnow.y(key), minZ + ChunkSnow.localZ(key));
                BiomeClimate entry = climate.entry(level.getBiome(pos).value());
                if (entry != null && entry.noSeasonalSnow()) {
                    return true;
                }
            }
        }
        return false;
    }

    private ChunkPoints points(long pos) {
        ChunkPoints chunkPoints = points.get(pos);
        if (chunkPoints == null) {
            chunkPoints = new ChunkPoints(seed, ChunkPos.getX(pos), ChunkPos.getZ(pos));
            points.put(pos, chunkPoints);
        }
        return chunkPoints;
    }

    /**
     * The chunk at {@code pos} if it can be read now. One that can't is taken out of the queues and asked for again
     * later, until its unloading says it has gone: a chunk on its way out that is kept after all (the player turned
     * back) doesn't load anew, so nothing else would bring it back here.
     */
    private @Nullable LevelChunk loadedChunk(ServerLevel level, long pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(pos), ChunkPos.getZ(pos));
        if (chunk == null && loaded.contains(pos)) {
            catchUp.remove(pos);
            notReady.add(pos);
        }
        return chunk;
    }

    private void forget(long pos) {
        loaded.remove(pos);
        catchUp.remove(pos);
        notReady.remove(pos);
        waiting.remove(pos);
        points.remove(pos);
        loadedChanged = true;
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
     * For {@code /almanac snow}: chunks waiting to be brought up to date, left in the loaded chunks' round, and
     * loaded, and what the work has cost.
     */
    public String describeQueues() {
        return "catch-up queue " + catchUp.size() + (waiting.isEmpty() ? "" : " (" + waiting.size()
                + " waiting for their snow nodes)") + ", gradual queue " + (round.length - roundNext)
                + " (chunks left in this round of the loaded ones), loaded chunks " + loaded.size()
                + ", brought up to date on load so far " + caughtUp + ", counted up to date without work " + restamped
                + (failed.isEmpty() ? "" : ", left alone after an error " + failed.size())
                + String.format(Locale.ROOT, "\nSnow work since the server started: catch-up %.1f ms (%d chunks), loaded"
                        + " land %.1f ms (%d rounds); %d looks at chunks, %d columns looked at, %d changed",
                        catchUpNanos / 1e6, caughtUp, followNanos / 1e6, rounds, looks, columnsVisited, columnsChanged)
                + (season == null ? "" : "\n" + season.describe());
    }
}
