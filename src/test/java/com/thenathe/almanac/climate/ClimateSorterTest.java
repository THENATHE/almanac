package com.thenathe.almanac.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.thenathe.almanac.api.Climate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClimateSorterTest {
    private static Climate sort(Set<String> tags, float temperature, boolean precipitation) {
        return ClimateSorter.sort(tags::contains, temperature, precipitation);
    }

    @Test
    void almanacTagsComeFirst() {
        assertEquals(Climate.TEMPERATE, sort(Set.of("almanac:climate/temperate", "c:is_desert"), 2.0F, false));
        assertEquals(Climate.COLD, sort(Set.of("almanac:climate/cold"), 0.5F, true));
        assertEquals(Climate.HOT, sort(Set.of("almanac:climate/hot", "c:is_snowy"), 0.0F, true));
    }

    @Test
    void conventionalTagsComeNext() {
        assertEquals(Climate.HOT, sort(Set.of("c:is_desert"), 2.0F, false));
        assertEquals(Climate.HOT, sort(Set.of("minecraft:is_badlands"), 2.0F, false));
        assertEquals(Climate.HOT, sort(Set.of("c:is_badlands"), 2.0F, false));
        assertEquals(Climate.COLD, sort(Set.of("c:is_snowy"), 0.5F, true));
        assertEquals(Climate.COLD, sort(Set.of("c:is_icy"), 0.5F, true));
    }

    @Test
    void climateDecidesTheRest() {
        assertEquals(Climate.TEMPERATE, sort(Set.of(), 0.8F, true));
        assertEquals(Climate.COLD, sort(Set.of(), 0.0F, true));
        assertEquals(Climate.COLD, sort(Set.of(), -0.7F, true));
        assertEquals(Climate.HOT, sort(Set.of(), 1.2F, false));
        assertEquals(Climate.TEMPERATE, sort(Set.of(), 0.15F, true));
    }

    @Test
    void vanillaBiomesSortAsThePlanSays() {
        // Plains, Dappled Forest and a cave biome: temperate.
        assertEquals(Climate.TEMPERATE, sort(Set.of("minecraft:is_overworld"), 0.8F, true));
        assertEquals(Climate.TEMPERATE, sort(Set.of("minecraft:is_overworld", "minecraft:is_forest"), 0.6F, true));
        assertEquals(Climate.TEMPERATE, sort(Set.of("minecraft:is_overworld"), 0.5F, true));
        // Savanna is dry and hot but listed temperate by Almanac's own tag.
        assertEquals(Climate.TEMPERATE, sort(Set.of("minecraft:is_savanna", "almanac:climate/temperate"), 2.0F, false));
        // Deep frozen ocean is 0.5 but listed cold.
        assertEquals(Climate.COLD, sort(Set.of("almanac:climate/cold", "minecraft:is_deep_ocean"), 0.5F, true));
        assertEquals(Climate.COLD, sort(Set.of("c:is_snowy"), -0.3F, true));
    }
}
