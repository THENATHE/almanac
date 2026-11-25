package com.thenathe.almanac.climate;

import com.thenathe.almanac.climatemap.ServerClimateMap;
import com.thenathe.almanac.internal.FarClimateView;
import com.thenathe.almanac.internal.Services;
import com.thenathe.almanac.internal.SnowReading;
import com.thenathe.almanac.mixin.LevelWeatherAccessor;
import com.thenathe.almanac.season.ServerSeasons;
import java.util.OptionalLong;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * Where the climate mixins get the season from. On the server it is the running {@link ServerSeasons}; on a
 * client with Almanac it is whatever the server last synced. Only the overworld has seasons, and world generation
 * never comes through here, so it keeps the real climate.
 */
public final class ClimateHooks {
    private static volatile Function<Level, @Nullable SeasonalClimate> clientClimate = level -> null;
    private static volatile Function<Level, @Nullable SnowReading> clientSnow = level -> null;
    private static volatile FarClimateView farClimate = FarClimateView.NOTHING_KNOWN;
    private static volatile Function<Level, OptionalLong> clientFarSnowChanged = level -> OptionalLong.empty();
    private static volatile BiFunction<Level, Boolean, @Nullable Float> clientStorm = (level, thunder) -> null;

    private ClimateHooks() {
    }

    public static @Nullable SeasonalClimate forLevel(Level level) {
        if (level.dimension() != Level.OVERWORLD) {
            return null;
        }
        if (level instanceof ServerLevel serverLevel) {
            return ServerSeasons.climate(serverLevel);
        }
        return level.isClientSide() ? clientClimate.apply(level) : null;
    }

    /** The season's snow and ice now; on a client, as last synced and followed forward to the level's game time. */
    public static @Nullable SnowReading snow(Level level) {
        if (forLevel(level) == null) {
            return null;
        }
        if (level instanceof ServerLevel) {
            return ServerSeasons.get() == null ? null : Services.snow().reading();
        }
        return clientSnow.apply(level);
    }

    /** Set once by the client entrypoint. */
    public static void setClientClimate(Function<Level, @Nullable SeasonalClimate> source) {
        clientClimate = source;
    }

    /** Set once by the client entrypoint. */
    public static void setClientSnow(Function<Level, @Nullable SnowReading> source) {
        clientSnow = source;
    }

    /** The client's climate map for far terrain; nothing known on the server or before the client sets it. */
    public static FarClimateView farClimate() {
        return farClimate;
    }

    /** Set once by the client entrypoint. */
    public static void setFarClimate(FarClimateView view) {
        farClimate = view;
    }

    /**
     * The dimension's own rain or thunder level, before the player's share: on a client {@link Level#getRainLevel} is
     * the share where the player stands ({@code ClientRain}), so this reads the levels the server sent as they are.
     *
     * <p>With regional weather (from 0.4.0) there is no one weather for the world: vanilla's levels are held at
     * nothing. On a client this is then the storm over the camera, whatever falls from it, which is what it gave a
     * player standing there before (a sandstorm's strength, snow gathering on leaves). On the server it is the
     * world's levels, which read clear; ask the weather at a place instead.
     */
    public static float worldWeather(Level level, boolean thunder) {
        if (level.isClientSide()) {
            Float storm = clientStorm.apply(level, thunder);
            if (storm != null) {
                return storm;
            }
            LevelWeatherAccessor weather = (LevelWeatherAccessor) level;
            return thunder ? weather.almanac$thunderLevel() * weather.almanac$rainLevel() : weather.almanac$rainLevel();
        }
        return thunder ? level.getThunderLevel(1.0F) : level.getRainLevel(1.0F);
    }

    /** The game tick at which what far terrain shows last changed; on a client, as last synced. */
    public static OptionalLong farSnowChangedTick(Level level) {
        if (forLevel(level) == null) {
            return OptionalLong.empty();
        }
        if (level instanceof ServerLevel) {
            ServerClimateMap map = ServerClimateMap.get();
            long tick = map == null ? -1 : map.snowChangedTick();
            return tick < 0 ? OptionalLong.empty() : OptionalLong.of(tick);
        }
        return clientFarSnowChanged.apply(level);
    }

    /** Set once by the client entrypoint. */
    public static void setClientStorm(BiFunction<Level, Boolean, @Nullable Float> source) {
        clientStorm = source;
    }

    /** Set once by the client entrypoint. */
    public static void setClientFarSnowChanged(Function<Level, OptionalLong> source) {
        clientFarSnowChanged = source;
    }
}
