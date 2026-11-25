package com.thenathe.almanac.internal;

import java.util.OptionalLong;
import net.minecraft.world.level.Level;
import com.thenathe.almanac.api.WeatherAt;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Core's side of the internal API: the season, for the public API and the modules. On the server it is the running
 * season; on a client, whatever the server last synced.
 */
public interface Seasons {
    /** The overworld's climate now; null for any other level and while no season is known. */
    @Nullable ClimateNow climate(Level level);

    /**
     * The season's snow and ice now; on a client, the last sync followed forward to the level's game time. Null for any
     * other level and while no season is known.
     */
    @Nullable SnowReading snow(Level level);

    /** The dimension's own rain or thunder level, before the player's share. */
    float worldWeather(Level level, boolean thunder);

    /** The client's climate map for far terrain; nothing known on the server. */
    FarClimateView farClimate();

    /** The game tick at which what far terrain shows last changed; on a client, as last synced. */
    OptionalLong farSnowChangedTick(Level level);

    SnowSettings snowSettings();

    /**
     * The weather at a position now (from 0.4.0): under regional weather from the storm over it and the season there,
     * otherwise from vanilla's one weather and the season. Null outside the overworld and while no season is known.
     */
    @Nullable WeatherAt weatherAt(Level level, BlockPos pos);
}
