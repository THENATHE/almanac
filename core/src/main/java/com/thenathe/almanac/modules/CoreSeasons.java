package com.thenathe.almanac.modules;

import com.thenathe.almanac.api.WeatherAt;
import com.thenathe.almanac.climate.ClimateHooks;
import com.thenathe.almanac.climate.LocalWeather;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.internal.ClimateNow;
import com.thenathe.almanac.internal.FarClimateView;
import com.thenathe.almanac.internal.Seasons;
import com.thenathe.almanac.internal.SnowReading;
import com.thenathe.almanac.internal.SnowSettings;
import java.util.OptionalLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Core's side of the internal API: the answers {@link ClimateHooks} gives core's own mixins. */
final class CoreSeasons implements Seasons {
    @Override
    public @Nullable ClimateNow climate(Level level) {
        return ClimateHooks.forLevel(level);
    }

    @Override
    public @Nullable SnowReading snow(Level level) {
        return ClimateHooks.snow(level);
    }

    @Override
    public float worldWeather(Level level, boolean thunder) {
        return ClimateHooks.worldWeather(level, thunder);
    }

    @Override
    public FarClimateView farClimate() {
        return ClimateHooks.farClimate();
    }

    @Override
    public OptionalLong farSnowChangedTick(Level level) {
        return ClimateHooks.farSnowChangedTick(level);
    }

    @Override
    public @Nullable WeatherAt weatherAt(Level level, BlockPos pos) {
        return LocalWeather.weatherAt(level, pos);
    }

    @Override
    public SnowSettings snowSettings() {
        ServerConfig config = ServerConfig.get();
        return new SnowSettings(config.snowCatchUp, config.catchUpChunksPerTick, config.snowColumnsPerTick,
                config.nativeSnowMeltsInSummer, config.snowfallTicks, config.thawTicks, config.iceTicks,
                config.maxSnowLayers);
    }
}
