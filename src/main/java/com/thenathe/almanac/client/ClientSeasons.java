package com.thenathe.almanac.client;

import com.thenathe.almanac.api.AlmanacEvents;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.biome.SeasonalBiomeIndex;
import com.thenathe.almanac.climate.SeasonalClimate;
import com.thenathe.almanac.network.SeasonSyncPayload;
import com.thenathe.almanac.network.WorldWeatherPayload;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The season as the server last described it. Empty until an Almanac server sends one; the biome copies come with
 * the server's registries, so the client finds them in its own.
 */
public final class ClientSeasons {
    /** Read from any thread by the far-terrain lookups. */
    private static volatile @Nullable SeasonSyncPayload lastSync;
    private static @Nullable SeasonalClimate climate;
    /** The overworld's own rain and thunder levels, as last synced. Read from any thread. */
    private static volatile @Nullable WorldWeatherPayload worldWeather;
    private static @Nullable ClientLevel indexedLevel;
    private static SeasonalBiomeIndex index = SeasonalBiomeIndex.EMPTY;

    private ClientSeasons() {
    }

    static void receive(SeasonSyncPayload sync) {
        SeasonSyncPayload previous = lastSync;
        lastSync = sync;
        AlmanacEvents.Change change = sync.changeFrom(previous);
        // A new snow depth alone doesn't change where rain and snow fall.
        if (change.subSeason() || change.rainReach()) {
            climate = null;
        }
        ClientLevel level = Minecraft.getInstance().level;
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
        climate = null;
        indexedLevel = null;
        index = SeasonalBiomeIndex.EMPTY;
    }

    /** The last season sync from the server, or null when the server doesn't have Almanac. */
    public static @Nullable SeasonSyncPayload lastSync() {
        return lastSync;
    }

    /** The real biomes of this level's registry that have seasonal copies, with their climates. */
    public static SeasonalBiomeIndex index(ClientLevel level) {
        if (indexedLevel != level) {
            index = SeasonalBiomeIndex.build(level.registryAccess().lookupOrThrow(Registries.BIOME));
            indexedLevel = level;
            climate = null;
        }
        return index;
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
        SeasonalBiomeIndex levelIndex = index(clientLevel);
        SeasonalClimate current = climate;
        if (current == null) {
            current = new SeasonalClimate(levelIndex, sync.subSeason(), sync.reached());
            climate = current;
        }
        return current;
    }
}
