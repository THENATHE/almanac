package com.thenathe.almanac.snow;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.internal.AlmanacModule;
import com.thenathe.almanac.internal.Services;
import com.thenathe.almanac.internal.WeatherView;
import com.thenathe.almanac.internal.WetSpell;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Works out the snow of the snow nodes near loaded land under regional weather, on Almanac's background thread, and
 * publishes it as a {@link SnowField} for the server thread to read. Pure arithmetic on a copy of the weather log and
 * the weather module's view, so the server thread never waits for it and nothing it reads ever changes under it.
 *
 * <p>A node is worked out once from scratch (from the last time its climate's snow was surely gone, or the log's
 * checkpoint) and then followed forward from where it was, a stretch at a time, so keeping fifty nodes current costs
 * the storms of the last second, not of the season. A node no longer wanted is forgotten.
 */
final class SnowNodes {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlmanacModule.MOD_ID);
    /** How often the nodes are worked out again while nothing new is wanted, in server ticks. */
    static final int REFRESH_TICKS = 20;

    /** Where a node's snow was last worked out to. Background thread only. */
    private record Known(long tick, double[] depths) {
    }

    private final Long2ObjectOpenHashMap<Known> known = new Long2ObjectOpenHashMap<>();
    private final AtomicBoolean working = new AtomicBoolean();
    private volatile SnowField field = SnowField.EMPTY;
    private volatile long fieldTick;
    /** Bumped when the log's past changed (it was moved back or folded roughly): what is known is then forgotten. */
    private volatile int epoch;
    private int knownEpoch;
    private long lastStart = Long.MIN_VALUE;
    // What the work has cost, for /almanac snow.
    private volatile long runs;
    private volatile long nodesWorked;
    private volatile long nanos;

    SnowField field() {
        return field;
    }

    long fieldTick() {
        return fieldTick;
    }

    void forgetPast() {
        epoch++;
    }

    /**
     * Has the background thread work out {@code wanted} at {@code now}, if it isn't already working and some of them
     * aren't in the field yet, or one has come to the tick its snowfall may start or stop (from then on the field can't
     * follow it forward, so it is worked out again at once), or {@link #REFRESH_TICKS} have passed.
     */
    void refresh(SnowLog log, WeatherView weather, LongSet wanted, long now, int maxLayers) {
        SnowField current = field;
        boolean due = now - lastStart >= REFRESH_TICKS || now < lastStart;
        for (long key : wanted) {
            SnowField.Node node = current.node(key);
            if (node == null || (node.stableUntil() <= now && node.tick() < now)) {
                due = true;
                break;
            }
        }
        if (!due) {
            return;
        }
        if (!working.compareAndSet(false, true)) {
            return;
        }
        lastStart = now;
        SnowLog copy = log.copy();
        LongSet nodes = new LongOpenHashSet(wanted);
        int startEpoch = epoch;
        try {
            Services.weather().background().execute(() -> {
                try {
                    long started = System.nanoTime();
                    field = work(copy, weather, nodes, now, maxLayers, startEpoch);
                    fieldTick = now;
                    nanos += System.nanoTime() - started;
                    runs++;
                } catch (RuntimeException failure) {
                    LOGGER.error("Almanac: working out the snow by area failed; loaded land keeps its snow until the "
                            + "next try", failure);
                } finally {
                    working.set(false);
                }
            });
        } catch (RuntimeException refused) {
            working.set(false);
        }
    }

    private SnowField work(SnowLog log, WeatherView weather, LongSet nodes, long now, int maxLayers, int startEpoch) {
        if (knownEpoch != startEpoch) {
            known.clear();
            knownEpoch = startEpoch;
        }
        known.keySet().retainAll(nodes);
        Long2ObjectOpenHashMap<SnowField.Node> worked = new Long2ObjectOpenHashMap<>(nodes.size());
        for (long key : nodes) {
            SnowField.Node node = node(log, weather, key, now);
            worked.put(key, node);
            known.put(key, new Known(now, node.depths().clone()));
            nodesWorked++;
        }
        return new SnowField(worked, maxLayers);
    }

    /** One node's snow at {@code now}, followed on from where it was known, or worked out from scratch. */
    private SnowField.Node node(SnowLog log, WeatherView weather, long key, long now) {
        int nodeX = SnowArea.keyX(key);
        int nodeZ = SnowArea.keyZ(key);
        RainSource rain = RainSource.regional(remembering(weather));
        Known was = known.get(key);
        double[] depths = new double[Climate.values().length];
        double[] paces = new double[depths.length];
        long stableUntil = Long.MAX_VALUE;
        for (Climate climate : Climate.values()) {
            SnowArea area = SnowArea.node(climate, nodeX, nodeZ);
            SnowLog.Trend trend;
            if (was != null && was.tick() <= now) {
                trend = log.trendFrom(area, was.tick(), new SnowLog.Amount(was.depths()[climate.ordinal()], 0.0), now, rain);
            } else {
                long melted = log.meltedBy(climate, now);
                trend = melted == Long.MIN_VALUE
                        ? log.trendFrom(area, log.checkpointTick(), log.checkpoint(climate), now, rain)
                        : log.trendFrom(area, melted, SnowLog.Amount.NONE, now, rain);
            }
            depths[climate.ordinal()] = trend.now().depth();
            paces[climate.ordinal()] = trend.depthPerTick();
            stableUntil = Math.min(stableUntil, trend.stableUntil());
        }
        return new SnowField.Node(now, depths, paces, stableUntil);
    }

    /**
     * Asks the weather about each stretch of a node once for all three climates: the storms over a place are the same
     * whichever climate is asking, and one answer holds all three.
     */
    private static RainSource.WetSpells remembering(WeatherView weather) {
        record Stretch(double x, double z, long from, long to) {
        }
        Map<Stretch, WetSpell> asked = new HashMap<>();
        return (x, z, from, to) -> asked.computeIfAbsent(new Stretch(x, z, from, to),
                stretch -> weather.wetSpell(x, z, from, to));
    }

    String describe() {
        long done = runs;
        return String.format(Locale.ROOT, "snow nodes: %d worked out at tick %d; %d runs, %d nodes, %.2f ms a "
                + "run on the background thread", field.size(), fieldTick, done, nodesWorked,
                done == 0 ? 0.0 : nanos / 1e6 / done);
    }
}
