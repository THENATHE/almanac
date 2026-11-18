package com.thenathe.almanac.biome;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.climate.ClimateSorter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import org.jspecify.annotations.Nullable;

/**
 * Finds a real biome's base climate and its seasonal copies in a finished biome registry. The server and the client
 * each build one from their own registry.
 */
public final class SeasonalBiomeIndex {
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
    public static final SeasonalBiomeIndex EMPTY = new SeasonalBiomeIndex(Map.of());

    private final Map<Biome, Entry> byOriginal;

    private SeasonalBiomeIndex(Map<Biome, Entry> byOriginal) {
        this.byOriginal = byOriginal;
    }

    public static SeasonalBiomeIndex build(Registry<Biome> registry) {
        Map<Identifier, Holder.Reference<Biome>[]> copiesById = new HashMap<>();
        registry.listElements().forEach(holder -> CopyNames.parse(holder.key().identifier()).ifPresent(copy ->
                copiesById.computeIfAbsent(copy.original(), ignored -> newArray())[copy.subSeason().ordinal()] = holder));

        Map<Biome, Entry> byOriginal = new IdentityHashMap<>();
        registry.listElements().forEach(holder -> {
            Holder.Reference<Biome>[] seasonal = copiesById.get(holder.key().identifier());
            if (seasonal == null || Arrays.asList(seasonal).contains(null)) {
                return;
            }
            Set<String> tags = holder.tags().map(tag -> tag.location().toString()).collect(Collectors.toSet());
            Biome biome = holder.value();
            Climate base = ClimateSorter.sort(tags::contains, biome.getBaseTemperature(), biome.hasPrecipitation());
            byOriginal.put(biome, new Entry(holder, base, seasonal, tags.contains(SNOW_SURFACE_TAG),
                    !tags.contains(NO_SEASONAL_ICE_TAG)));
        });
        return new SeasonalBiomeIndex(byOriginal);
    }

    @SuppressWarnings("unchecked")
    private static Holder.Reference<Biome>[] newArray() {
        return new Holder.Reference[SubSeason.COUNT];
    }

    /** The entry for a real biome that has seasonal copies, or null. */
    public @Nullable Entry entry(Biome biome) {
        return byOriginal.get(biome);
    }

    /** Every real biome that has seasonal copies. */
    public Collection<Entry> entries() {
        return Collections.unmodifiableCollection(byOriginal.values());
    }

    public boolean isEmpty() {
        return byOriginal.isEmpty();
    }

    public int size() {
        return byOriginal.size();
    }

    /**
     * @param snowSurface the biome is in {@link #SNOW_SURFACE_TAG}
     * @param seasonalIce the biome's water freezes with the season: it isn't in {@link #NO_SEASONAL_ICE_TAG}
     */
    public record Entry(Holder.Reference<Biome> original, Climate base, Holder.Reference<Biome>[] copies, boolean snowSurface,
                        boolean seasonalIce) {
        public Holder.Reference<Biome> copy(SubSeason subSeason) {
            return copies[subSeason.ordinal()];
        }
    }
}
