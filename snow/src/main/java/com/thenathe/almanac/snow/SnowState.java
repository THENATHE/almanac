package com.thenathe.almanac.snow;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SnowLayers;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.SeasonTick;
import com.thenathe.almanac.internal.SnowNow;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Everything that decides where snow and ice belong in a chunk: the sub-season (which sets the temperature of every
 * biome), the snow depth and ice cover of each area and how deep snow may build (which give every column its layers
 * and whether its water is frozen), whether native snow melts in summer, the snow height game rule, a fingerprint of
 * the climate settings ({@link SeasonTick#climateSettings}) and one of the biomes the season never brings snow to
 * ({@link SeasonTick#noSeasonalSnow}).
 *
 * <p>The amounts are kept per climate. Under vanilla's one weather they are the climate's, the same everywhere; under
 * regional weather (from 0.4.0) each chunk has depths of its own from the snow nodes around it ({@link #withDepths},
 * {@code SnowField}), and the rest of the state is the world's. A chunk's record saves the state it was last brought
 * to. From that and the state now, {@link #sameFrame} says whether only the amounts moved; then only the columns
 * whose points the shares or the ice moved past need a look ({@code SnowUpdates}).
 */
public final class SnowState implements SnowNow {
    /** The fingerprint of the default climate settings, which snow states saved before 0.1.3 were reached with. */
    public static final int DEFAULT_CLIMATE_SETTINGS = SeasonTick.DEFAULT_CLIMATE_SETTINGS;

    private final SubSeason subSeason;
    private final Map<Climate, Double> depths;
    private final Map<Climate, Double> ice;
    private final int maxLayers;
    private final boolean nativeSnowMelts;
    private final int snowHeight;
    private final int climateSettings;
    private final int noSeasonalSnow;
    private final Map<Climate, SnowLayers> shares = new EnumMap<>(Climate.class);
    /**
     * A state saved before 0.3.0, when a chunk was brought to whole layers: its depths are whole numbers of layers
     * lying on every cold column, not a depth split by the 90 % rule (which would claim part of a layer above them).
     */
    private final boolean wholeLayers;

    public SnowState(SubSeason subSeason, Map<Climate, Double> depths, Map<Climate, Double> ice, int maxLayers,
                     boolean nativeSnowMelts, int snowHeight, int climateSettings, int noSeasonalSnow) {
        this(subSeason, depths, ice, maxLayers, nativeSnowMelts, snowHeight, climateSettings, noSeasonalSnow, false);
    }

    private SnowState(SubSeason subSeason, Map<Climate, Double> depths, Map<Climate, Double> ice, int maxLayers,
                      boolean nativeSnowMelts, int snowHeight, int climateSettings, int noSeasonalSnow,
                      boolean wholeLayers) {
        this.wholeLayers = wholeLayers;
        this.subSeason = subSeason;
        this.maxLayers = Math.clamp(maxLayers, 1, SnowLayers.MOST_LAYERS);
        Map<Climate, Double> allDepths = new EnumMap<>(Climate.class);
        Map<Climate, Double> allIce = new EnumMap<>(Climate.class);
        for (Climate climate : Climate.values()) {
            SnowLayers layers = SnowLayers.of(depths.getOrDefault(climate, 0.0), this.maxLayers);
            shares.put(climate, layers);
            allDepths.put(climate, layers.depth());
            double frozen = ice.getOrDefault(climate, 0.0);
            allIce.put(climate, Double.isFinite(frozen) ? Math.clamp(frozen, 0.0, 1.0) : 0.0);
        }
        this.depths = Map.copyOf(allDepths);
        this.ice = Map.copyOf(allIce);
        this.nativeSnowMelts = nativeSnowMelts;
        this.snowHeight = Math.clamp(snowHeight, 0, 8);
        this.climateSettings = climateSettings;
        this.noSeasonalSnow = noSeasonalSnow;
    }

    /** A state with the default climate settings, and no biome kept free of the season's snow. */
    public SnowState(SubSeason subSeason, Map<Climate, Double> depths, Map<Climate, Double> ice, int maxLayers,
                     boolean nativeSnowMelts, int snowHeight) {
        this(subSeason, depths, ice, maxLayers, nativeSnowMelts, snowHeight, DEFAULT_CLIMATE_SETTINGS,
                SeasonTick.NO_SEASONAL_SNOW_UNKNOWN);
    }

    public SubSeason subSeason() {
        return subSeason;
    }

    public double depth(Climate climate) {
        return depths.get(climate);
    }

    @Override
    public double ice(Climate climate) {
        return ice.get(climate);
    }

    @Override
    public SnowLayers shares(Climate climate) {
        return shares.get(climate);
    }

    /**
     * The share of the open ground one layer of a climate covers in this state: {@link #shares}, but for a state saved
     * with whole layers, every layer up to its depth whole and none above.
     */
    public double share(Climate climate, int layer) {
        if (wholeLayers) {
            return layer >= 1 && layer <= Math.round(depth(climate)) ? 1.0 : 0.0;
        }
        return shares(climate).share(layer);
    }

    @Override
    public Map<Climate, Integer> layers() {
        Map<Climate, Integer> layers = new EnumMap<>(Climate.class);
        for (Climate climate : Climate.values()) {
            layers.put(climate, layers(climate));
        }
        return layers;
    }

    @Override
    public int layers(Climate climate) {
        return shares(climate).started();
    }

    @Override
    public int maxLayers() {
        return maxLayers;
    }

    @Override
    public boolean nativeSnowMelts() {
        return nativeSnowMelts;
    }

    @Override
    public int snowHeight() {
        return snowHeight;
    }

    /** How high native snow can be: one layer, or more where the game rule lets snow pile up. */
    @Override
    public int naturalHeight() {
        return Math.max(1, snowHeight);
    }

    public int climateSettings() {
        return climateSettings;
    }

    public int noSeasonalSnow() {
        return noSeasonalSnow;
    }

    /** Whether the climate's snow and ice are melting: it is warm for it this sub-season. */
    public boolean melting(Climate climate) {
        return !SnowLog.holdsSnow(climate, subSeason);
    }

    /**
     * Whether a chunk last brought to {@code before} needs looking at only where the amounts moved, when its surface
     * and recorded spots hold only {@code climates}: everything else that decides its snow is the same. The
     * sub-season, the snow height, the climate settings and the biomes kept free of the season's snow always count,
     * since they change temperatures and heights everywhere (or might, for all a fingerprint says); the native melt
     * setting counts only with COLD land. With the climates unknown (null), the native setting counts too.
     */
    public boolean sameFrame(SnowState before, @Nullable Set<Climate> climates) {
        if (before.subSeason != subSeason || before.snowHeight != snowHeight
                || before.climateSettings != climateSettings || before.noSeasonalSnow != noSeasonalSnow) {
            return false;
        }
        return (climates != null && !climates.contains(Climate.COLD)) || before.nativeSnowMelts == nativeSnowMelts;
    }

    /** Whether {@link #sameFrame} holds but for the biomes kept free of the season's snow. */
    public boolean sameFrameBesidesNoSeasonalSnow(SnowState before, @Nullable Set<Climate> climates) {
        return sameFrame(before.withNoSeasonalSnow(noSeasonalSnow), climates);
    }

    /** Whether the amounts of {@code climates} (every climate, when null) are exactly those of {@code before}. */
    public boolean sameAmounts(SnowState before, @Nullable Set<Climate> climates) {
        for (Climate climate : climates == null ? Set.of(Climate.values()) : climates) {
            if (ice(climate) != before.ice(climate)) {
                return false;
            }
            for (int layer = 1; layer <= Math.max(maxLayers, before.maxLayers); layer++) {
                if (share(climate, layer) != before.share(climate, layer)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * This state with other snow depths and the same everything else: the state one chunk is brought to under
     * regional weather, where each chunk has its own depths ({@code SnowField}) and the rest is the world's.
     */
    public SnowState withDepths(Map<Climate, Double> chunkDepths) {
        return new SnowState(subSeason, chunkDepths, ice, maxLayers, nativeSnowMelts, snowHeight, climateSettings,
                noSeasonalSnow, wholeLayers);
    }

    public SnowState withNoSeasonalSnow(int fingerprint) {
        return new SnowState(subSeason, depths, ice, maxLayers, nativeSnowMelts, snowHeight, climateSettings, fingerprint,
                wholeLayers);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SnowState state && subSeason == state.subSeason && depths.equals(state.depths)
                && ice.equals(state.ice) && maxLayers == state.maxLayers && nativeSnowMelts == state.nativeSnowMelts
                && snowHeight == state.snowHeight && climateSettings == state.climateSettings
                && noSeasonalSnow == state.noSeasonalSnow && wholeLayers == state.wholeLayers;
    }

    @Override
    public int hashCode() {
        return Objects.hash(subSeason, depths, ice, maxLayers, nativeSnowMelts, snowHeight, climateSettings, noSeasonalSnow);
    }

    @Override
    public String toString() {
        return "SnowState[" + subSeason.id() + ", depths " + depths + ", ice " + ice + ", up to " + maxLayers + " layers]";
    }

    private record Saved(String subSeason, Map<String, Integer> layers, Optional<Map<String, Double>> depths,
                         Map<String, Double> ice, int maxLayers, boolean wholeLayers, boolean nativeSnowMelts,
                         int snowHeight, int climateSettings, int noSeasonalSnow) {
        private static final Codec<Map<String, Double>> AMOUNTS = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE);

        static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("sub_season").forGetter(Saved::subSeason),
                // The whole layers of each climate, which states saved before 0.3.0 hold instead of the amounts.
                Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("layers", Map.of()).forGetter(Saved::layers),
                // Added in 0.3.0, by area: today the climate's ID.
                AMOUNTS.optionalFieldOf("depth").forGetter(Saved::depths),
                AMOUNTS.optionalFieldOf("ice", Map.of()).forGetter(Saved::ice),
                Codec.INT.optionalFieldOf("max_layers", 0).forGetter(Saved::maxLayers),
                // A state saved before 0.3.0 and kept since: its depths are whole layers.
                Codec.BOOL.optionalFieldOf("whole_layers", false).forGetter(Saved::wholeLayers),
                Codec.BOOL.optionalFieldOf("native_snow_melts", true).forGetter(Saved::nativeSnowMelts),
                Codec.INT.optionalFieldOf("snow_height", 1).forGetter(Saved::snowHeight),
                // Added in 0.1.3. Older states were reached with whatever the settings were then; they count as the
                // defaults, so a world that never changed them isn't brought up to date again.
                Codec.INT.optionalFieldOf("climate_settings", DEFAULT_CLIMATE_SETTINGS).forGetter(Saved::climateSettings),
                // Added in 0.2.0. Older states were reached before any biome was kept free of the season's snow.
                Codec.INT.optionalFieldOf("no_seasonal_snow", SeasonTick.NO_SEASONAL_SNOW_UNKNOWN)
                        .forGetter(Saved::noSeasonalSnow)
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
        Map<Climate, Double> depths = new EnumMap<>(Climate.class);
        Map<Climate, Double> ice = new EnumMap<>(Climate.class);
        int maxLayers;
        if (saved.depths().isPresent()) {
            for (SnowArea area : SnowArea.all()) {
                depths.put(area.climate(), saved.depths().get().getOrDefault(area.id(), 0.0));
                ice.put(area.climate(), saved.ice().getOrDefault(area.id(), 0.0));
            }
            maxLayers = saved.maxLayers() > 0 ? saved.maxLayers() : Math.max(1, saved.snowHeight());
            return DataResult.success(new SnowState(subSeason.get(), depths, ice, maxLayers, saved.nativeSnowMelts(),
                    saved.snowHeight(), saved.climateSettings(), saved.noSeasonalSnow(), saved.wholeLayers()));
        } else {
            // Saved before 0.3.0. Such a chunk counted as up to date only once the snow cover had decided all of it:
            // where a climate had snow its cover was whole, with that many layers on every cold column, as deep as
            // the game rule let it build; its water was frozen wherever the climate was cold.
            for (Climate climate : Climate.values()) {
                depths.put(climate, (double) Math.max(0, saved.layers().getOrDefault(climate.id(), 0)));
                ice.put(climate, SnowLog.holdsSnow(climate, subSeason.get()) ? 1.0 : 0.0);
            }
            maxLayers = Math.max(1, saved.snowHeight());
        }
        return DataResult.success(new SnowState(subSeason.get(), depths, ice, maxLayers, saved.nativeSnowMelts(),
                saved.snowHeight(), saved.climateSettings(), saved.noSeasonalSnow(), true));
    }

    private static Saved toSaved(SnowState state) {
        Map<String, Double> depths = new LinkedHashMap<>();
        Map<String, Double> ice = new LinkedHashMap<>();
        for (SnowArea area : SnowArea.all()) {
            depths.put(area.id(), state.depth(area.climate()));
            ice.put(area.id(), state.ice(area.climate()));
        }
        return new Saved(state.subSeason.id(), Map.of(), Optional.of(depths), ice, state.maxLayers, state.wholeLayers,
                state.nativeSnowMelts, state.snowHeight, state.climateSettings, state.noSeasonalSnow);
    }
}
