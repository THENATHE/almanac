package com.thenathe.almanac.internal;

import java.util.Objects;
import java.util.OptionalLong;
import net.minecraft.world.level.Level;
import com.thenathe.almanac.api.WeatherAt;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Where each side of the internal API is found. Each is installed once as Almanac starts ({@link AlmanacModule}), and
 * read from any thread after that. Until core's is installed the public API answers as it always has before a season
 * is known: nothing.
 */
public final class Services {
    private static final Seasons BEFORE_ALMANAC_STARTS = new Seasons() {
        @Override
        public @Nullable ClimateNow climate(Level level) {
            return null;
        }

        @Override
        public @Nullable SnowReading snow(Level level) {
            return null;
        }

        @Override
        public float worldWeather(Level level, boolean thunder) {
            return thunder ? level.getThunderLevel(1.0F) : level.getRainLevel(1.0F);
        }

        @Override
        public FarClimateView farClimate() {
            return FarClimateView.NOTHING_KNOWN;
        }

        @Override
        public OptionalLong farSnowChangedTick(Level level) {
            return OptionalLong.empty();
        }

        @Override
        public SnowSettings snowSettings() {
            throw new IllegalStateException("Almanac's settings aren't read yet: core installs them as Almanac starts");
        }

        @Override
        public @Nullable WeatherAt weatherAt(Level level, BlockPos pos) {
            return null;
        }
    };

    private static volatile Seasons seasons = BEFORE_ALMANAC_STARTS;
    private static volatile @Nullable Snow snow;
    private static volatile @Nullable Weather weather;

    private Services() {
    }

    public static Seasons seasons() {
        return seasons;
    }

    /** The snow module's side; it fails if the module isn't in the jar, which core checks as Almanac starts. */
    public static Snow snow() {
        Snow installed = snow;
        if (installed == null) {
            throw new IllegalStateException("Almanac's snow module isn't installed: it starts with Almanac");
        }
        return installed;
    }

    /** The weather module's side; it fails if the module isn't in the jar, which core checks as Almanac starts. */
    public static Weather weather() {
        Weather installed = weather;
        if (installed == null) {
            throw new IllegalStateException("Almanac's weather module isn't installed: it starts with Almanac");
        }
        return installed;
    }

    /** Whether the weather module has started, for answers asked before Almanac has. */
    public static boolean hasWeather() {
        return weather != null;
    }

    /** Called by core, first thing as Almanac starts. */
    public static void installSeasons(Seasons provided) {
        seasons = Objects.requireNonNull(provided);
    }

    /** Called by the snow module as it starts. */
    public static void installSnow(Snow provided) {
        snow = Objects.requireNonNull(provided);
    }

    /** Called by the weather module as it starts. */
    public static void installWeather(Weather provided) {
        weather = Objects.requireNonNull(provided);
    }
}
