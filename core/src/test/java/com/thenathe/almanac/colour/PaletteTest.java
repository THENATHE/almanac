package com.thenathe.almanac.colour;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.thenathe.almanac.api.SubSeason;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PaletteTest {
    private final SeasonalPalette palette = SeasonalPalette.bundled();

    @Test
    void midSummerIsVanillasLookByDefault() {
        for (ColourChannel channel : ColourChannel.values()) {
            assertTrue(palette.step("minecraft:plains", tag -> false, channel, SubSeason.MID_SUMMER).isUnchanged());
        }
    }

    @Test
    void dappledForestIsAnchoredToItsVanillaColours() {
        assertEquals(0xdf6827, palette.step("minecraft:dappled_forest", tag -> false, ColourChannel.GRASS, SubSeason.MID_FALL).apply(0));
        assertEquals(0xe68e30, palette.step("minecraft:dappled_forest", tag -> false, ColourChannel.FOLIAGE, SubSeason.MID_FALL).apply(0));
        assertEquals(0x8c3a04, palette.step("minecraft:dappled_forest", tag -> false, ColourChannel.DRY_FOLIAGE, SubSeason.MID_FALL).apply(0));
    }

    @Test
    void entriesMatchByTagAndFallBackToTheDefault() {
        Set<String> jungleTags = Set.of("minecraft:is_jungle");
        // Jungles stay green in fall; their entry has no dry-foliage channel, so that comes from the default.
        assertTrue(palette.step("minecraft:jungle", jungleTags::contains, ColourChannel.FOLIAGE, SubSeason.MID_FALL).isUnchanged());
        assertEquals(palette.step("minecraft:plains", tag -> false, ColourChannel.DRY_FOLIAGE, SubSeason.MID_FALL),
                palette.step("minecraft:jungle", jungleTags::contains, ColourChannel.DRY_FOLIAGE, SubSeason.MID_FALL));
        int oakFall = palette.step("minecraft:forest", tag -> false, ColourChannel.FOLIAGE, SubSeason.MID_FALL).apply(0x77ab2f);
        assertTrue((oakFall >> 16 & 0xFF) > (oakFall >> 8 & 0xFF), "fall oak foliage turns orange: " + Integer.toHexString(oakFall));
    }

    @Test
    void stepsMultiplyAndClamp() {
        SeasonalPalette.Step step = SeasonalPalette.Step.multiply(2.0F, 0.5F, 1.0F);
        assertEquals(0xff4080, step.apply(0x9080_80));
        assertThrows(IllegalArgumentException.class, () -> SeasonalPalette.parse(JsonParser.parseString(
                "{\"default\": {\"grass\": {\"monsoon\": {\"multiply\": [1, 1, 1]}}}}").getAsJsonObject()));
        assertThrows(IllegalArgumentException.class, () -> SeasonalPalette.parse(JsonParser.parseString(
                "{\"default\": {\"grass\": {\"mid_fall\": {\"color\": \"orange\"}}}}").getAsJsonObject()));
    }

    @Test
    void colormapFitMatchesKnownVanillaColours() {
        // Plains (0.8, 0.4) and forest (0.7, 0.8), as the 26.3 colormaps give them.
        assertClose(0x91bd59, Colormaps.grass(0.8F, 0.4F));
        assertClose(0x77ab2f, Colormaps.foliage(0.8F, 0.4F));
        assertClose(0x79c05a, Colormaps.grass(0.7F, 0.8F));
        assertClose(0x59ae30, Colormaps.foliage(0.7F, 0.8F));
    }

    private static void assertClose(int expected, int actual) {
        for (int shift = 0; shift <= 16; shift += 8) {
            int difference = Math.abs((expected >> shift & 0xFF) - (actual >> shift & 0xFF));
            assertTrue(difference <= 4, Integer.toHexString(expected) + " vs " + Integer.toHexString(actual));
        }
    }

    @Test
    void mixMovesPartWayTowardsAColour() {
        SeasonalPalette.Step half = SeasonalPalette.Step.mix(0xff0000, 0.5F);
        assertEquals(0x804020, half.apply(0x008040));
        assertEquals(0xff0000, SeasonalPalette.Step.color(0xff0000).apply(0x123456));
        SeasonalPalette.Step parsed = parseStep("{\"multiply\": [2, 1, 1], \"mix\": \"#000000\", \"amount\": 0.5}");
        assertEquals(0x401010, parsed.apply(0x402020), "multiplied first, then mixed");
        assertThrows(IllegalArgumentException.class, () -> parseStep("{\"mix\": \"#000000\", \"amount\": 1.5}"));
        assertTrue(SeasonalPalette.Step.mix(0xff0000, 0.0F).isUnchanged());
    }

    @Test
    void fallReadsAsNaturalColoursNotOlive() {
        int forestFoliage = 0x59ae30;
        int oak = palette.step("minecraft:forest", tag -> false, ColourChannel.FOLIAGE, SubSeason.MID_FALL).apply(forestFoliage);
        assertTrue(red(oak) > green(oak) + 40 && green(oak) > blue(oak) + 40, "oak is orange-brown: " + Integer.toHexString(oak));

        int birch = leafColour("minecraft:birch_leaves", SeasonalPalette.LeafTint.FIXED, SubSeason.MID_FALL, 0x80a755);
        assertTrue(red(birch) > 200 && green(birch) > 170 && blue(birch) < 80, "birch is clear yellow: " + Integer.toHexString(birch));

        int darkOak = leafColour("minecraft:dark_oak_leaves", SeasonalPalette.LeafTint.BIOME, SubSeason.MID_FALL, oak);
        assertTrue(red(darkOak) < red(oak) && red(darkOak) > green(darkOak) * 1.6, "dark oak is deep red-brown: " + Integer.toHexString(darkOak));

        for (int colour : new int[] {oak, birch, darkOak}) {
            int max = Math.max(red(colour), Math.max(green(colour), blue(colour)));
            int min = Math.min(red(colour), Math.min(green(colour), blue(colour)));
            assertTrue(max <= 245 && max - min < 200, "not garish: " + Integer.toHexString(colour));
        }
    }

    @Test
    void leavesMatchByIdThenTagThenTint() {
        // By ID: birch has its own steps, cherry is kept as it is.
        assertTrue(!palette.leafSteps("minecraft:birch_leaves", tag -> false, SeasonalPalette.LeafTint.FIXED).isUnchanged());
        assertTrue(palette.leafSteps("minecraft:cherry_leaves", tag -> true, SeasonalPalette.LeafTint.FIXED).isUnchanged());
        // Oak reads the biome's foliage colour, which already follows the season.
        assertTrue(palette.leafSteps("minecraft:oak_leaves", tag -> true, SeasonalPalette.LeafTint.BIOME).isUnchanged());

        // An unknown leaf in #minecraft:leaves: a fixed tint follows the default foliage palette ...
        SeasonalPalette.LeafSteps futureFixed = palette.leafSteps("othermod:maple_leaves", "minecraft:leaves"::equals,
                SeasonalPalette.LeafTint.FIXED);
        assertEquals(palette.step("minecraft:plains", tag -> false, ColourChannel.FOLIAGE, SubSeason.MID_FALL),
                futureFixed.step(SubSeason.MID_FALL));
        // ... a biome tint already does, and an untinted leaf never changes.
        assertTrue(palette.leafSteps("othermod:maple_leaves", "minecraft:leaves"::equals, SeasonalPalette.LeafTint.BIOME).isUnchanged());
        assertTrue(palette.leafSteps("othermod:maple_leaves", "minecraft:leaves"::equals, SeasonalPalette.LeafTint.UNTINTED).isUnchanged());
        assertTrue(palette.leafSteps("minecraft:birch_leaves", tag -> true, SeasonalPalette.LeafTint.UNTINTED).isUnchanged());
        // A block in no matching tag is left alone.
        assertTrue(palette.leafSteps("othermod:hedge", tag -> false, SeasonalPalette.LeafTint.FIXED).isUnchanged());
    }

    @Test
    void anIdEntryWinsOverAnEarlierTagEntry() {
        SeasonalPalette custom = SeasonalPalette.parse(JsonParser.parseString("""
                {"default": {"foliage": {"mid_fall": {"color": "#aa0000"}}},
                 "leaves": [
                   {"match": ["#minecraft:leaves"], "follow": "foliage"},
                   {"match": ["minecraft:birch_leaves"], "steps": {"mid_fall": {"color": "#00aa00"}}}
                 ]}""").getAsJsonObject());
        assertEquals(0x00aa00, custom.leafSteps("minecraft:birch_leaves", tag -> true, SeasonalPalette.LeafTint.FIXED)
                .step(SubSeason.MID_FALL).apply(0));
        assertEquals(0xaa0000, custom.leafSteps("othermod:maple_leaves", tag -> true, SeasonalPalette.LeafTint.FIXED)
                .step(SubSeason.MID_FALL).apply(0));
        assertThrows(IllegalArgumentException.class, () -> SeasonalPalette.parse(JsonParser.parseString(
                "{\"default\": {}, \"leaves\": [{\"match\": [\"minecraft:oak_leaves\"]}]}").getAsJsonObject()));
    }

    private int leafColour(String block, SeasonalPalette.LeafTint tint, SubSeason subSeason, int own) {
        return palette.leafSteps(block, "minecraft:leaves"::equals, tint).step(subSeason).apply(own);
    }

    private static SeasonalPalette.Step parseStep(String json) {
        return SeasonalPalette.parse(JsonParser.parseString("{\"default\": {\"grass\": {\"mid_fall\": " + json + "}}}")
                .getAsJsonObject()).step("minecraft:plains", tag -> false, ColourChannel.GRASS, SubSeason.MID_FALL);
    }

    private static int red(int rgb) {
        return rgb >> 16 & 0xFF;
    }

    private static int green(int rgb) {
        return rgb >> 8 & 0xFF;
    }

    private static int blue(int rgb) {
        return rgb & 0xFF;
    }
}
