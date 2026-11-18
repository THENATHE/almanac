package com.thenathe.almanac.snow;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.biome.SeasonalCopies;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Everything that decides where snow and ice belong in a chunk: the sub-season (which sets the temperature of every
 * biome), the whole layers of snow in each climate, whether native snow melts in summer, the snow height game rule,
 * and a fingerprint of the restart-only climate settings the seasonal biome copies were built with
 * ({@link SeasonalCopies.CopyClimate#fingerprint}). A chunk that was last brought up to an equal state needs no
 * work; {@link #sameFor} narrows that to the climates a chunk holds.
 */
public record SnowState(SubSeason subSeason, Map<Climate, Integer> layers, boolean nativeSnowMelts, int snowHeight,
                        int climateSettings) {
    /** The fingerprint of the default climate settings, which snow states saved before 0.1.3 were reached with. */
    public static final int DEFAULT_CLIMATE_SETTINGS = SeasonalCopies.CopyClimate.DEFAULTS.fingerprint();

    public SnowState {
        Map<Climate, Integer> complete = new EnumMap<>(Climate.class);
        for (Climate climate : Climate.values()) {
            complete.put(climate, Math.clamp(layers.getOrDefault(climate, 0), 0, 8));
        }
        layers = Map.copyOf(complete);
        snowHeight = Math.clamp(snowHeight, 0, 8);
    }

    /** A state with the default climate settings. */
    public SnowState(SubSeason subSeason, Map<Climate, Integer> layers, boolean nativeSnowMelts, int snowHeight) {
        this(subSeason, layers, nativeSnowMelts, snowHeight, DEFAULT_CLIMATE_SETTINGS);
    }

    public int layers(Climate climate) {
        return layers.get(climate);
    }

    /**
     * Whether moving from {@code before} to this state can take snow or ice away anywhere. A new sub-season changes
     * every biome's temperature, so it always can; within one, only fewer layers or a change to the melt settings
     * can. More snow alone can't.
     */
    public boolean mayThawFrom(@Nullable SnowState before) {
        if (before == null || before.subSeason != subSeason || before.snowHeight != snowHeight
                || before.climateSettings != climateSettings || nativeSnowMelts && !before.nativeSnowMelts) {
            return true;
        }
        for (Climate climate : Climate.values()) {
            if (layers(climate) < before.layers(climate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether moving from {@code before} to this state can bring snow anywhere: a new sub-season, snow height or climate
     * settings change where it is cold, and more layers in a climate add snow there. Less snow alone can't.
     */
    public boolean mayFormFrom(@Nullable SnowState before) {
        if (before == null || before.subSeason != subSeason || before.snowHeight != snowHeight
                || before.climateSettings != climateSettings) {
            return true;
        }
        for (Climate climate : Climate.values()) {
            if (layers(climate) > before.layers(climate)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a chunk last brought up to {@code before} needs nothing more for this state, when its surface and
     * recorded spots hold only {@code climates}: other climates' layers don't matter to it, and nor does the native
     * melt setting without COLD land. The sub-season, the snow height and the climate settings always count, since
     * they change temperatures and heights everywhere. With the climates unknown (null), the whole state counts.
     */
    public boolean sameFor(SnowState before, @Nullable Set<Climate> climates) {
        if (climates == null) {
            return equals(before);
        }
        if (before.subSeason != subSeason || before.snowHeight != snowHeight
                || before.climateSettings != climateSettings) {
            return false;
        }
        if (climates.contains(Climate.COLD) && before.nativeSnowMelts != nativeSnowMelts) {
            return false;
        }
        for (Climate climate : climates) {
            if (layers(climate) != before.layers(climate)) {
                return false;
            }
        }
        return true;
    }

    /** How high native snow can be: one layer, or more where the game rule lets snow pile up. */
    public int naturalHeight() {
        return Math.max(1, snowHeight);
    }

    private record Saved(String subSeason, Map<String, Integer> layers, boolean nativeSnowMelts, int snowHeight,
                         int climateSettings) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("sub_season").forGetter(Saved::subSeason),
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("layers", Map.of()).forGetter(Saved::layers),
                Codec.BOOL.optionalFieldOf("native_snow_melts", true).forGetter(Saved::nativeSnowMelts),
                Codec.INT.optionalFieldOf("snow_height", 1).forGetter(Saved::snowHeight),
                // Added in 0.1.3. Older states were reached with whatever the settings were then; they count as the
                // defaults, so a world that never changed them isn't brought up to date again.
                Codec.INT.optionalFieldOf("climate_settings", DEFAULT_CLIMATE_SETTINGS).forGetter(Saved::climateSettings)
        ).apply(instance, Saved::new));
    }

    /**
     * Saved by name. A sub-season this version doesn't know fails to decode, and the chunk record reads that as no
     * state at all, so the chunk is simply brought up to date again.
     */
    public static final Codec<SnowState> CODEC = Saved.CODEC.comapFlatMap(SnowState::fromSaved, SnowState::toSaved);

    private static DataResult<SnowState> fromSaved(Saved saved) {
        Optional<SubSeason> subSeason = SubSeason.byId(saved.subSeason());
        if (subSeason.isEmpty()) {
            return DataResult.error(() -> "unknown sub-season " + saved.subSeason());
        }
        Map<Climate, Integer> layers = new EnumMap<>(Climate.class);
        for (Climate climate : Climate.values()) {
            layers.put(climate, saved.layers().getOrDefault(climate.id(), 0));
        }
        return DataResult.success(new SnowState(subSeason.get(), layers, saved.nativeSnowMelts(), saved.snowHeight(),
                saved.climateSettings()));
    }

    private static Saved toSaved(SnowState state) {
        Map<String, Integer> layers = new LinkedHashMap<>();
        for (Climate climate : Climate.values()) {
            layers.put(climate.id(), state.layers(climate));
        }
        return new Saved(state.subSeason().id(), layers, state.nativeSnowMelts(), state.snowHeight(),
                state.climateSettings());
    }
}
