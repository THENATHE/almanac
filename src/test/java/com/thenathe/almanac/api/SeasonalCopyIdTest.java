package com.thenathe.almanac.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;

class SeasonalCopyIdTest {
    @Test
    void parsesACopysId() {
        SeasonalCopyId copy = SeasonalCopyId.parse("almanac:mid_winter/minecraft/plains").orElseThrow();
        assertEquals(SubSeason.MID_WINTER, copy.subSeason());
        assertEquals("minecraft:plains", copy.original());
        // Paths can have folders of their own.
        assertEquals("terralith:alpine/high_peaks",
                SeasonalCopyId.parse("almanac:late_fall/terralith/alpine/high_peaks").orElseThrow().original());
    }

    @Test
    void writesTheSameIdItReads() {
        String id = SeasonalCopyId.of(SubSeason.EARLY_SPRING, "terralith:alpine/high_peaks");
        assertEquals("almanac:early_spring/terralith/alpine/high_peaks", id);
        assertEquals(id, SeasonalCopyId.parse(id).orElseThrow().id());
        assertThrows(IllegalArgumentException.class, () -> SeasonalCopyId.of(SubSeason.MID_FALL, "plains"));
    }

    @Test
    void anythingElseIsNotACopy() {
        for (String id : new String[] {"minecraft:plains", "almanac:plains", "almanac:monsoon/minecraft/plains",
                "almanac:mid_winter/minecraft", "almanac:mid_winter//plains", "almanac:mid_winter/Minecraft/plains",
                "other:mid_winter/minecraft/plains", ""}) {
            assertTrue(SeasonalCopyId.parse(id).isEmpty(), id);
        }
    }

    @Test
    void originalBiomeKeyOfACopyAndOfARealBiome() {
        ResourceKey<Biome> copy = ResourceKey.create(Registries.BIOME, Identifier.parse("almanac:mid_summer/minecraft/desert"));
        ResourceKey<Biome> desert = ResourceKey.create(Registries.BIOME, Identifier.parse("minecraft:desert"));
        assertEquals(desert, AlmanacApi.originalBiome(copy));
        assertSame(desert, AlmanacApi.originalBiome(desert));
    }
}
