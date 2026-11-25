package com.thenathe.almanac.climate;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.internal.BiomeClimate;
import java.util.List;
import java.util.function.Predicate;

/**
 * Sorts a biome into its base climate from data alone, never from a list of biome IDs, so new vanilla and modded
 * biomes sort themselves:
 * <ol>
 *   <li>Almanac's own tags, {@code #almanac:climate/hot}, {@code /temperate} and {@code /cold}, which datapacks can
 *   extend;</li>
 *   <li>then the conventional tags for deserts, badlands and snowy or icy biomes;</li>
 *   <li>then the biome's own climate: below 0.15 it snows at ground level (COLD), without precipitation it is dry
 *   (HOT), and anything else is TEMPERATE.</li>
 * </ol>
 * Tags are passed in by ID ({@code namespace:path}) so the same rules work on the registry being loaded, on a frozen
 * registry and on the client.
 */
public final class ClimateSorter {
    public static final String HOT_TAG = "almanac:climate/hot";
    public static final String TEMPERATE_TAG = "almanac:climate/temperate";
    public static final String COLD_TAG = "almanac:climate/cold";

    /** Vanilla's line between rain and snow. */
    public static final float SNOW_TEMPERATURE = BiomeClimate.SNOW_TEMPERATURE;

    private static final List<String> HOT_CONVENTIONAL = List.of("c:is_desert", "c:is_badlands", "minecraft:is_badlands");
    private static final List<String> COLD_CONVENTIONAL = List.of("c:is_snowy", "c:is_icy");

    private ClimateSorter() {
    }

    public static Climate sort(Predicate<String> hasTag, float temperature, boolean hasPrecipitation) {
        if (hasTag.test(HOT_TAG)) {
            return Climate.HOT;
        }
        if (hasTag.test(TEMPERATE_TAG)) {
            return Climate.TEMPERATE;
        }
        if (hasTag.test(COLD_TAG)) {
            return Climate.COLD;
        }
        if (HOT_CONVENTIONAL.stream().anyMatch(hasTag)) {
            return Climate.HOT;
        }
        if (COLD_CONVENTIONAL.stream().anyMatch(hasTag)) {
            return Climate.COLD;
        }
        if (temperature < SNOW_TEMPERATURE) {
            return Climate.COLD;
        }
        return hasPrecipitation ? Climate.TEMPERATE : Climate.HOT;
    }
}
