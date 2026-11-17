package com.thenathe.almanac.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.SubSeason;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

class CopyNamesTest {
    @Test
    void namesRoundTrip() {
        Identifier copy = CopyNames.copyOf(Identifier.parse("terralith:alpine/high_peaks"), SubSeason.LATE_FALL);
        assertEquals("almanac:late_fall/terralith/alpine/high_peaks", copy.toString());
        CopyNames.Copy parsed = CopyNames.parse(copy).orElseThrow();
        assertEquals(SubSeason.LATE_FALL, parsed.subSeason());
        assertEquals(Identifier.parse("terralith:alpine/high_peaks"), parsed.original());
    }

    @Test
    void onlyCopiesAreCopies() {
        assertFalse(CopyNames.isCopy(Identifier.parse("minecraft:plains")));
        assertFalse(CopyNames.isCopy(Identifier.parse("almanac:plains")));
        assertFalse(CopyNames.isCopy(Identifier.parse("almanac:monsoon/minecraft/plains")));
        assertTrue(CopyNames.isCopy(Identifier.parse("almanac:mid_winter/minecraft/plains")));
    }
}
