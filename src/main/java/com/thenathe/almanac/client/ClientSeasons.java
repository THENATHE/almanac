package com.thenathe.almanac.client;

import com.thenathe.almanac.api.AlmanacEvents;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.biome.SeasonalBiomeIndex;
import com.thenathe.almanac.climate.SeasonalClimate;
import com.thenathe.almanac.network.SeasonSyncPayload;
import com.thenathe.almanac.network.WorldWeatherPayload;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * The season as the server last described it. Empty until an Almanac server sends one; the biome copies come with
 * the server's registries, so the client finds them in its own.
 *
 * <p>Everything here can be read from any thread (chunk building and other mods' colour code ask too). The climate
 * is kept in one {@link Snapshot} that is replaced whole and remembers the sync it was made from, so a reader can
 * never keep an older sub-season's climate once a newer sync has arrived.
 */
public final class ClientSeasons {
    private static volatile @Nullable SeasonSyncPayload lastSync;
    /** The overworld's own rain and thunder levels, as last synced. */
    private static volatile @Nullable WorldWeatherPayload worldWeather;
    private static final AtomicReference<@Nullable Snapshot> SNAPSHOT = new AtomicReference<>();

    private ClientSeasons() {
    }

    /**
     * The climate made from one sync for one biome registry. The index only depends on the registry, so another
     * client level of the same connection (portal and preview mods make them) shares it.
     */
    record Snapshot(Object level, SeasonSyncPayload sync, Registry<Biome> registry, SeasonalBiomeIndex index,
                    SeasonalClimate climate) {
        /**
         * The snapshot for {@code sync} and a level with {@code registry}: {@code current} itself when it already is
         * that, a new one reusing its index when the registry is the same, and otherwise one with a newly built index.
         */
        static Snapshot next(@Nullable Snapshot current, Object level, SeasonSyncPayload sync, Registry<Biome> registry,
                             Function<Registry<Biome>, SeasonalBiomeIndex> buildIndex) {
            if (current != null && current.level == level && current.registry == registry && current.sync == sync) {
                return current;
            }
            SeasonalBiomeIndex index = current != null && current.registry == registry ? current.index : buildIndex.apply(registry);
            return new Snapshot(level, sync, registry, index, new SeasonalClimate(index, sync.subSeason(), sync.reached()));
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

    static void receiveWorldWeather(WorldWeatherPayload weather) {
        worldWeather = weather;
    }

    static @Nullable WorldWeatherPayload worldWeather(Level level) {
        return level instanceof ClientLevel ? worldWeather : null;
    }

    static void disconnected() {
        lastSync = null;
        worldWeather = null;
        SNAPSHOT.set(null);
    }

    /** The last season sync from the server, or null when the server doesn't have Almanac. */
    public static @Nullable SeasonSyncPayload lastSync() {
        return lastSync;
    }

    /** The real biomes of this level's registry that have seasonal copies, with their climates. */
    public static SeasonalBiomeIndex index(ClientLevel level) {
        Snapshot current = SNAPSHOT.get();
        if (current != null && current.level() == level) {
            return current.index();
        }
        Registry<Biome> registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        if (current != null && current.registry() == registry) {
            return current.index();
        }
        SeasonSyncPayload sync = lastSync;
        return sync != null ? snapshot(level, sync).index() : SeasonalBiomeIndex.build(registry);
    }

    static OptionalDouble snowDepth(Level level, Climate climate) {
        SeasonSyncPayload sync = lastSync;
        return sync != null && level instanceof ClientLevel ? OptionalDouble.of(sync.snowDepth(climate)) : OptionalDouble.empty();
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
        if (current != null && current.level() == level && current.sync() == sync) {
            return current;
        }
        Registry<Biome> registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        Snapshot next = Snapshot.next(current, level, sync, registry, SeasonalBiomeIndex::build);
        if (next != current) {
            SNAPSHOT.compareAndSet(current, next);
        }
        return next;
    }
}
