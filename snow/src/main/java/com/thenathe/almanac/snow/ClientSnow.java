package com.thenathe.almanac.snow;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SnowClocks;
import com.thenathe.almanac.internal.AlmanacModule;
import com.thenathe.almanac.internal.Services;
import com.thenathe.almanac.internal.WeatherView;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A client's snow by area under regional weather (from 0.4.0), for far terrain and anything else that wants the snow
 * of land the player hasn't loaded: the server's weather log and the client's own weather view, worked out on the
 * client's background thread the same way the server works out loaded land, so no snow comes over the network but
 * the log. A place is worked out when it is first asked about, and answered from then on; places no longer asked
 * about are let go. When more are asked about than it keeps, it keeps those nearest the camera.
 */
final class ClientSnow {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlmanacModule.MOD_ID);
    /** How many snow nodes a client keeps worked out at most: about 11 km square. */
    static final int MOST_NODES = 2_048;
    /** Client ticks after its last asking that a node is let go. */
    static final int FORGET_AFTER = 600;

    private static volatile @Nullable ClientSnow current;

    private final SnowLog log;
    private final SnowNodes nodes = new SnowNodes();
    /** Each node asked about, and the client tick it was last asked about. Any thread adds to it. */
    private final Map<Long, Long> asked = new ConcurrentHashMap<>();
    /** Set when a node is first asked about or let go, so the nodes worked out are chosen again. */
    private final AtomicBoolean askedChanged = new AtomicBoolean(true);
    /** The nodes worked out: every node asked about, or the {@link #MOST_NODES} of them nearest the camera. */
    private LongSet chosen = new LongOpenHashSet();
    /** The node the camera was at when {@link #chosen} was chosen. */
    private long chosenAround = Long.MIN_VALUE;
    private volatile long tick;

    private ClientSnow(SnowLog log) {
        this.log = log;
    }

    static @Nullable ClientSnow get() {
        return current;
    }

    /** The server sent its weather log (a sub-season, a setting, the weather changed, or the player joined). */
    static void received(CompoundTag saved) {
        SnowLog read = SnowLog.CODEC.parse(NbtOps.INSTANCE, saved.getCompoundOrEmpty("log"))
                .resultOrPartial(problem -> LOGGER.warn("Almanac: the server's weather log didn't read: {}", problem))
                .orElse(null);
        if (read == null) {
            return;
        }
        read.setLegacyMaxLayers(saved.getIntOr("legacy_max_layers", 1));
        current = new ClientSnow(read);
    }

    /** What the server sends: the log, and the snow height an older Almanac read its oldest segments with. */
    static CompoundTag forClients(SnowLog log) {
        CompoundTag saved = new CompoundTag();
        saved.put("log", SnowLog.CODEC.encodeStart(NbtOps.INSTANCE, log).getOrThrow());
        saved.putInt("legacy_max_layers", log.legacyMaxLayers());
        return saved;
    }

    /** The log the server sent. */
    SnowLog log() {
        return log;
    }

    static void disconnected() {
        current = null;
    }

    /** Once a client tick: works out the nodes asked about lately, nearest the camera first, and lets go of the others. */
    void tick(Level level, double cameraX, double cameraZ) {
        tick++;
        WeatherView weather = Services.weather().view(level);
        if (weather == null) {
            return;
        }
        if (asked.values().removeIf(last -> tick - last > FORGET_AFTER)) {
            askedChanged.set(true);
        }
        long around = SnowArea.key(Math.floorDiv((int) Math.floor(cameraX), SnowArea.NODE_SPACING),
                Math.floorDiv((int) Math.floor(cameraZ), SnowArea.NODE_SPACING));
        if (askedChanged.getAndSet(false) || around != chosenAround) {
            chosen = nearest(asked.keySet(), cameraX, cameraZ, MOST_NODES);
            chosenAround = around;
        }
        if (!chosen.isEmpty()) {
            nodes.refresh(log, weather, chosen, level.getGameTime(), maxLayers());
        }
    }

    /**
     * At most {@code most} of the nodes, nearest a place first. Up to 1.1.0 the first {@code most} in the order the
     * map kept them were taken, which beside far terrain asking about more than that (LiteLights asks 16,384) left
     * most of the land round the player unknown, scattered by the keys' hashes.
     */
    static LongSet nearest(Collection<Long> keys, double x, double z, int most) {
        long[] all = new long[keys.size()];
        int count = 0;
        for (Long key : keys) {
            if (count == all.length) {
                break;
            }
            all[count++] = key;
        }
        if (count <= most) {
            LongSet every = new LongOpenHashSet(count);
            for (int index = 0; index < count; index++) {
                every.add(all[index]);
            }
            return every;
        }
        // Each node's squared distance from the place (blocks squared over 256) above its index, so one sort orders them.
        long[] order = new long[count];
        for (int index = 0; index < count; index++) {
            double dx = SnowArea.keyX(all[index]) * (double) SnowArea.NODE_SPACING - x;
            double dz = SnowArea.keyZ(all[index]) * (double) SnowArea.NODE_SPACING - z;
            long distance = (long) Math.min(Integer.MAX_VALUE, (dx * dx + dz * dz) / 256.0);
            order[index] = distance << 32 | index;
        }
        Arrays.sort(order);
        LongSet nearest = new LongOpenHashSet(most);
        for (int index = 0; index < most; index++) {
            nearest.add(all[(int) order[index]]);
        }
        return nearest;
    }

    private int maxLayers() {
        SnowLog.Segment last = log.segments().isEmpty() ? null : log.segments().getLast();
        SnowClocks clocks = last == null ? log.legacyClocks() : log.clocksOf(last);
        return clocks.maxLayers();
    }

    /** Each climate's snow depth at a column now; null until its nodes are worked out, which asking starts. */
    @Nullable Map<Climate, Double> depthsAt(double x, double z, long gameTime) {
        LongSet around = new LongOpenHashSet(4);
        SnowField.nodesOf(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4, around);
        long now = tick;
        for (long key : around) {
            if (asked.put(key, now) == null) {
                askedChanged.set(true);
            }
        }
        return nodes.field().depthsAt(x, z, gameTime);
    }
}
