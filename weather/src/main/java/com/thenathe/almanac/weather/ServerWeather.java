package com.thenathe.almanac.weather;

import com.thenathe.almanac.internal.AlmanacModule;
import com.thenathe.almanac.internal.WeatherHere;
import com.thenathe.almanac.internal.WeatherRecord;
import com.thenathe.almanac.internal.WeatherTick;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The server's regional weather while the season runs: the saved record, the field worked out from it, and the index
 * of storms near the players that the server thread reads. Each tick it records what has changed (a new era, the
 * weather standing still or moving again), and every {@link #INDEX_EVERY} ticks it has the background thread build a
 * fresh index; the server thread only ever reads finished ones.
 */
final class ServerWeather {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlmanacModule.MOD_ID);
    /** How often the storm index is built again, in server ticks. */
    static final int INDEX_EVERY = 20;
    /** How far ahead each index reaches, in weather ticks: a few builds, so a late one never leaves a gap. */
    static final int INDEX_AHEAD = 80;
    /** Events kept after they end, for snow worked out for land that was away (about two default years). */
    static final long KEEP_EVENTS = 4_608_000L;
    static final int MOST_EVENTS = 4_096;

    /** At most this many columns are remembered within one tick before the cache starts again. */
    static final int MOST_REMEMBERED = 8_192;
    /** Storm cloud tops are all above this, so below it the height of a question doesn't change the answer. */
    static final int LOWEST_CLOUD_TOP = 200;

    private final MinecraftServer server;
    private final WeatherData data;
    private final long seed;
    /**
     * The weather over each column already asked about this tick, on the server thread: vanilla asks for every entity,
     * twice a tick, and many stand in the same columns. Keyed by column (and by height above the lowest cloud top).
     */
    private final Long2ObjectOpenHashMap<WeatherHere> remembered = new Long2ObjectOpenHashMap<>();
    private long rememberedTick = Long.MIN_VALUE;
    private @Nullable WeatherField rememberedField;
    private final CellCache cache = new CellCache();
    private volatile WeatherField field;
    private volatile @Nullable StormIndex index;
    private final AtomicBoolean building = new AtomicBoolean();
    private long changes;
    private int ticks;
    // What the index has cost, for /almanac weather.
    private long builds;
    private long buildNanos;
    private long indexAnswers;
    private long directAnswers;

    ServerWeather(MinecraftServer server) {
        this.server = server;
        this.data = server.overworld().getDataStorage().computeIfAbsent(WeatherData.TYPE);
        this.seed = Hashes.weatherSeed(server.overworld().getSeed());
        this.field = new WeatherField(data.record(seed), cache);
    }

    WeatherField field() {
        return field;
    }

    long changes() {
        return changes;
    }

    WeatherRecord record() {
        return field.record();
    }

    /** Records this tick's season and shares, and whether the weather moves; builds the index when it is due. */
    void tick(ServerLevel overworld, WeatherTick tick, int mathsVersion) {
        long now = overworld.getGameTime();
        boolean changed = observeEra(now, tick, mathsVersion);
        changed |= observeFreeze(now, tick);
        changed |= prune(now);
        if (changed) {
            field = new WeatherField(data.record(seed), cache);
            index = null;
            changes++;
            data.changed();
        }
        if (tick.regional() && (changed || ticks++ % INDEX_EVERY == 0)) {
            buildIndex(overworld);
        }
    }

    private boolean observeEra(long now, WeatherTick tick, int mathsVersion) {
        List<WeatherRecord.Era> eras = data.eras();
        WeatherRecord.Era era = new WeatherRecord.Era(now, tick.subSeason(), tick.regional() ? mathsVersion : 0,
                tick.hotShare(), tick.temperateShare(), tick.coldShare());
        WeatherRecord.Era last = eras.isEmpty() ? null : eras.getLast();
        if (last != null && last.sameWeather(era)) {
            return false;
        }
        if (era.regional() && (last == null || !last.regional())) {
            LOGGER.info("Almanac: regional weather runs in this world from game tick {}; before it the weather was "
                    + "vanilla's one rain", now);
        }
        if (last != null && last.start() >= now) {
            eras.removeLast();
        }
        eras.add(era);
        return true;
    }

    /** {@code advance_weather} off holds every storm where it is; on again, they move on from there. */
    private boolean observeFreeze(long now, WeatherTick tick) {
        List<WeatherRecord.Freeze> freezes = data.freezes();
        WeatherRecord.Freeze last = freezes.isEmpty() ? null : freezes.getLast();
        boolean open = last != null && last.end() == WeatherRecord.Freeze.OPEN;
        boolean standing = tick.regional() && !tick.advancing();
        if (standing && !open) {
            freezes.add(new WeatherRecord.Freeze(now, WeatherRecord.Freeze.OPEN));
            return true;
        }
        if (!standing && open) {
            freezes.removeLast();
            if (now > last.start()) {
                freezes.add(new WeatherRecord.Freeze(last.start(), now));
            }
            return true;
        }
        return false;
    }

    private boolean prune(long now) {
        List<WeatherRecord.Event> events = data.events();
        int before = events.size();
        events.removeIf(event -> event.end() < now - KEEP_EVENTS);
        while (events.size() > MOST_EVENTS) {
            events.removeFirst();
        }
        return events.size() != before;
    }

    void addEvent(WeatherRecord.Event event) {
        data.events().add(event);
        field = new WeatherField(data.record(seed), cache);
        index = null;
        changes++;
        data.changed();
    }

    /** Has the background thread build an index of the storms around every player in the overworld. */
    private void buildIndex(ServerLevel overworld) {
        if (!building.compareAndSet(false, true)) {
            return;
        }
        WeatherField built = field;
        double from = built.weatherTick(overworld.getGameTime());
        int reach = (overworld.getServer().getPlayerList().getViewDistance() + 3) * 16;
        List<StormIndex.Area> areas = new ArrayList<>();
        for (ServerPlayer player : overworld.players()) {
            areas.add(new StormIndex.Area(player.getX(), player.getZ(), reach));
        }
        try {
            WeatherModule.worker().execute(() -> {
                try {
                    long started = System.nanoTime();
                    StormIndex fresh = StormIndex.build(built, areas, from, from + INDEX_AHEAD);
                    buildNanos += System.nanoTime() - started;
                    builds++;
                    if (field == built) {
                        index = fresh;
                    }
                } catch (RuntimeException failure) {
                    LOGGER.error("Almanac: building the storm index failed; the weather is worked out directly", failure);
                } finally {
                    building.set(false);
                }
            });
        } catch (RuntimeException refused) {
            building.set(false);
        }
    }

    /**
     * The weather over a column now: remembered for the rest of the tick on the server thread, from the index where it
     * covers the column, worked out directly elsewhere.
     */
    WeatherHere at(double x, double y, double z, long gameTick) {
        WeatherField now = field;
        if (!server.isSameThread()) {
            return lookUp(now, x, y, z, gameTick);
        }
        if (gameTick != rememberedTick || now != rememberedField || remembered.size() > MOST_REMEMBERED) {
            remembered.clear();
            rememberedTick = gameTick;
            rememberedField = now;
        }
        int blockY = y < LOWEST_CLOUD_TOP ? Integer.MIN_VALUE : (int) Math.floor(y);
        long key = (((long) (int) Math.floor(x)) * 0x9E37_79B9L) ^ ((long) (int) Math.floor(z) << 32) ^ blockY;
        WeatherHere known = remembered.get(key);
        if (known == null) {
            // The column's centre stands for the whole column, so the answer is the same whoever asks first.
            known = lookUp(now, Math.floor(x) + 0.5, y < LOWEST_CLOUD_TOP ? 0.0 : y, Math.floor(z) + 0.5, gameTick);
            remembered.put(key, known);
        }
        return known;
    }

    private WeatherHere lookUp(WeatherField now, double x, double y, double z, long gameTick) {
        StormIndex current = index;
        if (current != null && current.field() == now) {
            WeatherHere indexed = current.tryAt(x, y, z, gameTick);
            if (indexed != null) {
                indexAnswers++;
                return indexed;
            }
        }
        directAnswers++;
        return now.at(x, y, z, gameTick);
    }

    String describe() {
        StormIndex current = index;
        WeatherRecord record = field.record();
        long switchOver = record.eras().stream().filter(WeatherRecord.Era::regional).mapToLong(WeatherRecord.Era::start)
                .findFirst().orElse(-1);
        return String.format(Locale.ROOT, "record: %d eras, %d events, %d freezes; regional weather since game tick %s%n"
                        + "storm index: %s; built %d times, %.2f ms each on the background thread; answers %d from the "
                        + "index, %d worked out directly; %d lattice cells cached",
                record.eras().size(), record.events().size(), record.freezes().size(),
                switchOver < 0 ? "(never)" : String.valueOf(switchOver),
                current == null ? "none yet" : current.squares() + " squares, " + current.storms() + " storms",
                builds, builds == 0 ? 0.0 : buildNanos / 1e6 / builds, indexAnswers, directAnswers, cache.size());
    }
}
