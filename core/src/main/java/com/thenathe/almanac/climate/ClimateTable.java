package com.thenathe.almanac.climate;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.BiomeClimate;
import com.thenathe.almanac.internal.SeasonTick;
import com.thenathe.almanac.internal.WeatherKind;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * The season's climate for every biome with seasons, sub-season by sub-season: whether rain or snow can fall and how
 * warm the biome is. Every climate check asks it, on the server and on clients, which get the server's table
 * ({@code ClimateTablePayload}).
 *
 * <p>Until 0.1.7 this was carried by 684 seasonal copies of the biomes in the biome registry, made for vanilla
 * clients. A cell holds what a copy's climate was, by the copies' own rule ({@link Cell#of}), and its checks use
 * vanilla's arithmetic on that temperature, so the table answers what the copies answered
 * ({@code ClimateTableTest}, against what 0.1.7's copies gave). It is built from the biomes, their tags and the
 * three {@link ClimateSettings}, and built again when those change, so the settings apply at once.
 */
public final class ClimateTable {
    /**
     * Biomes whose ground world generation makes of snow blocks, so the snow lying on it isn't on natural ground and
     * stays all year. Loaded chunks need no tag for that (they see the blocks); far terrain does.
     */
    public static final String SNOW_SURFACE_TAG = "almanac:snow_surface";
    /**
     * Biomes whose water never gets the season's ice: oceans (user, 2026-11-17, via the audit), so boats can still
     * cross them in winter. They freeze as their real biome does, so frozen oceans stay as in vanilla. Data packs
     * can extend it.
     */
    public static final String NO_SEASONAL_ICE_TAG = "almanac:no_seasonal_ice";
    /**
     * Biomes the season never brings snow or ice to: jungles by default (user, 2026-11-20), which stay TEMPERATE and
     * get rain in winter where their climate would give snow. Data packs can extend it.
     */
    public static final String NO_SEASONAL_SNOW_TAG = "almanac:no_seasonal_snow";
    public static final ClimateTable EMPTY = new ClimateTable(List.of());
    /** What chunk records saved before 0.2.0 hold for {@link #noSeasonalSnowFingerprint}: no rule at all. */
    public static final int NO_SEASONAL_SNOW_UNKNOWN = SeasonTick.NO_SEASONAL_SNOW_UNKNOWN;

    /** Biomes of the Nether and the End have no seasons. */
    private static final Set<String> OTHER_DIMENSIONS = Set.of("minecraft:is_nether", "minecraft:is_end", "c:is_nether", "c:is_end");

    private final List<Entry> entries;
    private final Map<Biome, Entry> byBiome = new IdentityHashMap<>();

    private ClimateTable(List<Entry> entries) {
        this.entries = List.copyOf(entries);
        for (Entry entry : this.entries) {
            byBiome.put(entry.biome().value(), entry);
        }
    }

    /** The table for every biome of a registry whose tags are bound, outside the Nether and the End. */
    public static ClimateTable build(Registry<Biome> registry, ClimateSettings settings) {
        List<Entry> entries = new ArrayList<>();
        registry.listElements().forEach(holder -> {
            Set<String> tags = holder.tags().map(tag -> tag.location().toString()).collect(Collectors.toSet());
            if (tags.stream().noneMatch(OTHER_DIMENSIONS::contains)) {
                entries.add(Entry.of(holder, tags::contains, settings));
            }
        });
        entries.sort(Comparator.comparing(entry -> entry.biome().unwrapKey().map(key -> key.identifier().toString()).orElse("")));
        return new ClimateTable(entries);
    }

    /** A table of entries already worked out, as a client receives them. */
    public static ClimateTable of(Collection<Entry> entries) {
        return new ClimateTable(new ArrayList<>(entries));
    }

    /** The entry for a biome with seasons, or null. */
    public @Nullable Entry entry(Biome biome) {
        return byBiome.get(biome);
    }

    /** Every biome with seasons, by ID. */
    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }

    /**
     * A number that changes when the biomes in {@link #NO_SEASONAL_SNOW_TAG} do, saved with each chunk's snow state
     * ({@code SnowState#noSeasonalSnow}) so a chunk holding the season's snow in a biome newly in the tag is gone
     * over once.
     */
    public int noSeasonalSnowFingerprint() {
        List<String> ids = new ArrayList<>();
        for (Entry entry : entries) {
            if (entry.noSeasonalSnow()) {
                ids.add(entry.biome().unwrapKey().map(key -> key.identifier().toString()).orElse("?"));
            }
        }
        ids.sort(Comparator.naturalOrder());
        // Never the value saved states read as "before 0.2.0".
        int fingerprint = ids.hashCode();
        return fingerprint == NO_SEASONAL_SNOW_UNKNOWN ? fingerprint + 1 : fingerprint;
    }

    /**
     * What falls in a biome in one sub-season, before the rain reach: the season's kind of weather for its climate,
     * with rain instead of snow for biomes in {@link #NO_SEASONAL_SNOW_TAG}.
     */
    public static WeatherKind weatherKind(Climate base, SubSeason subSeason, boolean noSeasonalSnow) {
        WeatherKind kind = WeatherKind.of(base, subSeason);
        if (noSeasonalSnow && (kind == WeatherKind.SNOW || kind == WeatherKind.SNOW_LINE)) {
            return WeatherKind.RAIN;
        }
        return kind;
    }

    /**
     * A biome's climate in one sub-season.
     *
     * @param canFall        rain or snow can fall this sub-season (when an event reaches the climate)
     * @param temperature    the biome's temperature this sub-season, before cooling with height
     * @param ownTemperature the season leaves the biome's temperature as it is, so its temperature modifier (frozen
     *                       oceans' warm patches) still counts; otherwise the season decides and the modifier doesn't
     */
    public record Cell(boolean canFall, float temperature, boolean ownTemperature) {
        /**
         * The cell for a biome of this temperature and kind of weather: the rule the seasonal copies were made by.
         * Winter's snow cools a biome that doesn't snow at ground level to the winter temperature, summer's rain warms
         * one that does to the summer temperature, and a snow line gives the temperature whose cooling with height
         * crosses 0.15 at the snow line. Nothing else changes the temperature.
         */
        public static Cell of(float temperature, WeatherKind kind, ClimateSettings settings) {
            float seasonal = temperature;
            boolean snowsAtGround = temperature < ClimateSorter.SNOW_TEMPERATURE;
            switch (kind) {
                case DRY -> {
                }
                case RAIN -> {
                    if (snowsAtGround) {
                        seasonal = Math.max(temperature, settings.summerTemperature());
                    }
                }
                case SNOW -> {
                    if (!snowsAtGround) {
                        seasonal = Math.min(temperature, settings.winterTemperature());
                    }
                }
                case SNOW_LINE -> seasonal = settings.snowLineTemperature();
            }
            return new Cell(kind != WeatherKind.DRY, seasonal, seasonal == temperature);
        }
    }

    /**
     * One biome's seasons.
     *
     * @param base           its climate in spring and fall ({@link ClimateSorter})
     * @param snowSurface    it is in {@link #SNOW_SURFACE_TAG}
     * @param seasonalIce    its water freezes with the season: it isn't in {@link #NO_SEASONAL_ICE_TAG}
     * @param noSeasonalSnow it is in {@link #NO_SEASONAL_SNOW_TAG}: the season never makes it cold enough for snow
     * @param cells          one per sub-season, in sub-season order
     */
    public record Entry(Holder<Biome> biome, Climate base, boolean snowSurface, boolean seasonalIce, boolean noSeasonalSnow,
                        List<Cell> cells) implements BiomeClimate {
        public Entry {
            if (cells.size() != SubSeason.COUNT) {
                throw new IllegalArgumentException(cells.size() + " cells, not " + SubSeason.COUNT);
            }
            cells = List.copyOf(cells);
        }

        /** The entry for a biome, with its tags given by ID ({@code namespace:path}). */
        public static Entry of(Holder<Biome> biome, Predicate<String> hasTag, ClimateSettings settings) {
            Biome value = biome.value();
            Climate base = ClimateSorter.sort(hasTag, value.getBaseTemperature(), value.hasPrecipitation());
            boolean noSeasonalSnow = hasTag.test(NO_SEASONAL_SNOW_TAG);
            List<Cell> cells = new ArrayList<>(SubSeason.COUNT);
            for (SubSeason subSeason : SubSeason.values()) {
                cells.add(Cell.of(value.getBaseTemperature(), weatherKind(base, subSeason, noSeasonalSnow), settings));
            }
            return new Entry(biome, base, hasTag.test(SNOW_SURFACE_TAG), !hasTag.test(NO_SEASONAL_ICE_TAG),
                    noSeasonalSnow, cells);
        }

        public Cell cell(SubSeason subSeason) {
            return cells.get(subSeason.ordinal());
        }

        @Override
        public Climate seasonal(SubSeason subSeason) {
            return SeasonalClimate.shifted(base, subSeason);
        }

        @Override
        public float temperature(SubSeason subSeason) {
            return cell(subSeason).temperature();
        }

        /** Whether rain or snow can fall here this sub-season, when an event reaches the climate. */
        @Override
        public boolean canFall(SubSeason subSeason) {
            return cell(subSeason).canFall();
        }

        /**
         * Whether the season makes a position cold enough for snow, as vanilla's {@code Biome#coldEnoughToSnow}
         * would answer for a biome of the cell's temperature. Never in {@link #NO_SEASONAL_SNOW_TAG}.
         */
        @Override
        public boolean coldEnoughToSnow(SubSeason subSeason, BlockPos pos, int seaLevel) {
            if (noSeasonalSnow) {
                return false;
            }
            Cell cell = cell(subSeason);
            if (cell.ownTemperature()) {
                // The biome's own answer, through vanilla's cache: the same temperature, modifier and all.
                return biome.value().coldEnoughToSnow(pos, seaLevel);
            }
            return SeasonTemperature.isCold(SeasonTemperature.cooled(cell.temperature(), pos, seaLevel));
        }

        /**
         * The season's temperature at a position, cooled with height as vanilla cools it, the biome's temperature
         * modifier included while the season leaves its temperature alone. Safe from any thread.
         */
        @Override
        public float temperatureAt(SubSeason subSeason, BlockPos pos, int seaLevel) {
            Cell cell = cell(subSeason);
            return cell.ownTemperature() ? SeasonTemperature.own(biome.value(), pos, seaLevel)
                    : SeasonTemperature.cooled(cell.temperature(), pos, seaLevel);
        }

        /** What falls at a position this sub-season, before the rain reach: nothing, rain, or snow where it is cold. */
        @Override
        public Biome.Precipitation precipitationAt(SubSeason subSeason, BlockPos pos, int seaLevel) {
            if (!canFall(subSeason)) {
                return Biome.Precipitation.NONE;
            }
            return coldEnoughToSnow(subSeason, pos, seaLevel) ? Biome.Precipitation.SNOW : Biome.Precipitation.RAIN;
        }
    }
}
