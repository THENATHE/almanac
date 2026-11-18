package com.thenathe.almanac.climate;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.climatemap.FarClimateView;
import com.thenathe.almanac.climatemap.ServerClimateMap;
import com.thenathe.almanac.network.WorldWeatherPayload;
import com.thenathe.almanac.season.ServerSeasons;
import java.util.OptionalDouble;
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
    private static volatile BiFunction<Level, Climate, OptionalDouble> clientSnowDepth = (level, climate) -> OptionalDouble.empty();
    private static volatile FarClimateView farClimate = FarClimateView.NOTHING_KNOWN;
    private static volatile Function<Level, @Nullable WorldWeatherPayload> clientWorldWeather = level -> null;
    private static volatile Function<Level, OptionalLong> clientThawTicks = level -> OptionalLong.empty();
    private static volatile Function<Level, OptionalLong> clientFarSnowChanged = level -> OptionalLong.empty();
    private static volatile Function<Level, OptionalLong> clientFormingTicks = level -> OptionalLong.empty();

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

    /** The snow depth of a climate in layers; on a client, as last synced (to a sixteenth of a layer). */
    public static OptionalDouble snowDepth(Level level, Climate climate) {
        if (forLevel(level) == null) {
            return OptionalDouble.empty();
        }
        if (level instanceof ServerLevel) {
            ServerSeasons seasons = ServerSeasons.get();
            return seasons == null ? OptionalDouble.empty() : OptionalDouble.of(seasons.snowDepth(climate));
        }
        return clientSnowDepth.apply(level, climate);
    }

    /** Set once by the client entrypoint. */
    public static void setClientClimate(Function<Level, @Nullable SeasonalClimate> source) {
        clientClimate = source;
    }

    /** Set once by the client entrypoint. */
    public static void setClientSnowDepth(BiFunction<Level, Climate, OptionalDouble> source) {
        clientSnowDepth = source;
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
     * The dimension's own rain or thunder level, before players' shares: the level's own on the server, the synced
     * one on a client that has it, and otherwise the level's (a server without Almanac has no shares).
     */
    public static float worldWeather(Level level, boolean thunder) {
        if (level.isClientSide() && level.dimension() == Level.OVERWORLD) {
            WorldWeatherPayload synced = clientWorldWeather.apply(level);
            if (synced != null) {
                return thunder ? synced.thunder() : synced.rain();
            }
        }
        return thunder ? level.getThunderLevel(1.0F) : level.getRainLevel(1.0F);
    }

    /** Set once by the client entrypoint. */
    public static void setClientWorldWeather(Function<Level, @Nullable WorldWeatherPayload> source) {
        clientWorldWeather = source;
    }

    /** How long a thaw in loaded chunks takes now, in game ticks; on a client, as last synced. */
    public static OptionalLong thawTicks(Level level) {
        if (forLevel(level) == null) {
            return OptionalLong.empty();
        }
        if (level instanceof ServerLevel) {
            ServerSeasons seasons = ServerSeasons.get();
            return seasons == null ? OptionalLong.empty() : OptionalLong.of(seasons.thawTicks());
        }
        return clientThawTicks.apply(level);
    }

    /** Set once by the client entrypoint. */
    public static void setClientThawTicks(Function<Level, OptionalLong> source) {
        clientThawTicks = source;
    }

    /** How long falling snow takes to whiten loaded land now, in game ticks; on a client, as last synced. */
    public static OptionalLong formingTicks(Level level) {
        if (forLevel(level) == null) {
            return OptionalLong.empty();
        }
        if (level instanceof ServerLevel) {
            ServerSeasons seasons = ServerSeasons.get();
            return seasons == null ? OptionalLong.empty() : OptionalLong.of(seasons.formingTicks());
        }
        return clientFormingTicks.apply(level);
    }

    /** Set once by the client entrypoint. */
    public static void setClientFormingTicks(Function<Level, OptionalLong> source) {
        clientFormingTicks = source;
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
    public static void setClientFarSnowChanged(Function<Level, OptionalLong> source) {
        clientFarSnowChanged = source;
    }
}
