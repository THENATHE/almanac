package com.thenathe.almanac.client;

import com.thenathe.almanac.api.AlmanacEvents;
import com.thenathe.almanac.client.colour.ClientColours;
import com.thenathe.almanac.climate.ClimateTable;
import com.thenathe.almanac.climate.SeasonalClimate;
import com.thenathe.almanac.internal.SnowReading;
import com.thenathe.almanac.network.ClimateTablePayload;
import com.thenathe.almanac.network.SeasonSyncPayload;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * The season as the server last described it: its climate table and its season sync. Empty until an Almanac server
 * sends them.
 *
 * <p>Everything here can be read from any thread (chunk building and other mods' colour code ask too). The climate
 * is kept in one {@link Snapshot} that is replaced whole and remembers the sync and the table it was made from, so a
 * reader can never keep an older sub-season's climate once a newer sync has arrived.
 */
public final class ClientSeasons {
    private static volatile @Nullable SeasonSyncPayload lastSync;
    private static volatile @Nullable ClimateTablePayload lastTable;
    private static final AtomicReference<@Nullable Snapshot> SNAPSHOT = new AtomicReference<>();

    private ClientSeasons() {
    }

    /**
     * The climate made from one sync and one table for one biome registry. The table only depends on the registry and
     * the table the server sent, so another client level of the same connection (portal and preview mods make them)
     * shares it.
     */
    record Snapshot(Object level, SeasonSyncPayload sync, Registry<Biome> registry, @Nullable ClimateTablePayload sent,
                    ClimateTable table, SeasonalClimate climate) {
        /**
         * The snapshot for {@code sync}, the table {@code sent} and a level with {@code registry}: {@code current}
         * itself when it already is that, a new one reusing its table when the registry and the table sent are the
         * same, and otherwise one with the table looked up in the registry again.
         */
        static Snapshot next(@Nullable Snapshot current, Object level, SeasonSyncPayload sync, Registry<Biome> registry,
                             @Nullable ClimateTablePayload sent,
                             BiFunction<@Nullable ClimateTablePayload, Registry<Biome>, ClimateTable> resolve) {
            if (current != null && current.level == level && current.registry == registry && current.sync == sync
                    && current.sent == sent) {
                return current;
            }
            ClimateTable table = current != null && current.registry == registry && current.sent == sent
                    ? current.table : resolve.apply(sent, registry);
            return new Snapshot(level, sync, registry, sent, table, new SeasonalClimate(table, sync.subSeason(), sync.reached()));
        }
    }

    static ClimateTable resolve(@Nullable ClimateTablePayload sent, Registry<Biome> registry) {
        return sent == null ? ClimateTable.EMPTY : sent.resolve(registry);
    }

    static void receiveTable(ClimateTablePayload table) {
        lastTable = table;
        ClientColours.tableChanged();
        ClientLevel level = Minecraft.getInstance().level;
        SeasonSyncPayload sync = lastSync;
        if (level != null && sync != null) {
            snapshot(level, sync);
            // Settings or tags changed on the server: every answer may have, as at a new sub-season.
            AlmanacEvents.SEASON_CHANGED.invoker().onSeasonChanged(level, new AlmanacEvents.Change(true, false, false));
            ClientClimateMap.climateChanged();
        }
    }

    static void receive(SeasonSyncPayload sync) {
        SeasonSyncPayload previous = lastSync;
        lastSync = sync;
        AlmanacEvents.Change change = sync.changeFrom(previous);
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            // Made now, so listeners and every thread see the new climate at once.
            snapshot(level, sync);
        }
        if (change.any() && level != null) {
            AlmanacEvents.SEASON_CHANGED.invoker().onSeasonChanged(level, change);
        }
        if (change.subSeason() || change.rainReach()) {
            ClientClimateMap.climateChanged();
        }
    }

    static void disconnected() {
        lastSync = null;
        lastTable = null;
        SNAPSHOT.set(null);
    }

    /** The last season sync from the server, or null when the server doesn't have Almanac. */
    public static @Nullable SeasonSyncPayload lastSync() {
        return lastSync;
    }

    /** The server's climate table, for this level's biome registry; empty until the server has sent it. */
    public static ClimateTable table(ClientLevel level) {
        SeasonSyncPayload sync = lastSync;
        if (sync != null) {
            return snapshot(level, sync).table();
        }
        return resolve(lastTable, level.registryAccess().lookupOrThrow(Registries.BIOME));
    }

    /** The server's snow and ice, followed forward from the last sync to the level's game time. */
    static @Nullable SnowReading snow(Level level) {
        SeasonSyncPayload sync = lastSync;
        return sync != null && level instanceof ClientLevel ? sync.snow(level.getGameTime()) : null;
    }

    static @Nullable SeasonalClimate climate(Level level) {
        SeasonSyncPayload sync = lastSync;
        if (sync == null || !(level instanceof ClientLevel clientLevel)) {
            return null;
        }
        return snapshot(clientLevel, sync).climate();
    }

    /**
     * The snapshot for this level and sync, made and published if the current one is for another. A thread that loses
     * the race to publish still answers from its own, which is right for the sync it read.
     */
    private static Snapshot snapshot(ClientLevel level, SeasonSyncPayload sync) {
        Snapshot current = SNAPSHOT.get();
        ClimateTablePayload sent = lastTable;
        if (current != null && current.level() == level && current.sync() == sync && current.sent() == sent) {
            return current;
        }
        Registry<Biome> registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        Snapshot next = Snapshot.next(current, level, sync, registry, sent, ClientSeasons::resolve);
        if (next != current) {
            SNAPSHOT.compareAndSet(current, next);
        }
        return next;
    }
}
