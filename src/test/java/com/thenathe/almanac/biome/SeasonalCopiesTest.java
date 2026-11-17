package com.thenathe.almanac.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.climate.WeatherKind;
import com.thenathe.almanac.colour.ColourChannel;
import com.thenathe.almanac.colour.Colormaps;
import com.thenathe.almanac.colour.SeasonalPalette;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/** Copy generation at the JSON level, on the game's own biome files. */
class SeasonalCopiesTest {
    private static final SeasonalCopies.CopyClimate CLIMATE = new SeasonalCopies.CopyClimate(0.0F, 0.5F, 48);
    private static final Function<ColourChannel, SeasonalPalette.Step> UNCHANGED = channel -> SeasonalPalette.Step.UNCHANGED;
    private static final Set<String> CHANGEABLE = Set.of("has_precipitation", "temperature", "temperature_modifier", "effects");

    static JsonObject vanilla(String name) {
        var stream = Objects.requireNonNull(SeasonalCopiesTest.class.getResourceAsStream("/data/minecraft/worldgen/biome/" + name + ".json"),
                name);
        return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void everythingButClimateAndColoursIsCarriedOver() {
        JsonObject plains = vanilla("plains");
        plains.addProperty("a_field_from_the_future", "kept");
        JsonObject copy = SeasonalCopies.copy(plains, WeatherKind.SNOW, CLIMATE, channel -> SeasonalPalette.Step.multiply(1.1F, 0.9F, 0.8F));
        for (String key : plains.keySet()) {
            if (!CHANGEABLE.contains(key)) {
                assertEquals(plains.get(key), copy.get(key), key);
            }
        }
        JsonObject effects = plains.getAsJsonObject("effects");
        for (String key : effects.keySet()) {
            if (!key.endsWith("_color") || key.equals("water_color")) {
                assertEquals(effects.get(key), copy.getAsJsonObject("effects").get(key), key);
            }
        }
        assertEquals("kept", copy.get("a_field_from_the_future").getAsString());
        assertEquals(plains.get("attributes"), copy.get("attributes"));
        assertEquals(plains.get("features"), copy.get("features"));
        // The original is left alone.
        assertEquals(vanilla("plains"), withoutFuture(plains));
    }

    private static JsonObject withoutFuture(JsonObject json) {
        JsonObject copy = json.deepCopy();
        copy.remove("a_field_from_the_future");
        return copy;
    }

    @Test
    void winterSnowCoolsTemperateBiomes() {
        JsonObject copy = SeasonalCopies.copy(vanilla("plains"), WeatherKind.SNOW, CLIMATE, UNCHANGED);
        assertTrue(copy.get("has_precipitation").getAsBoolean());
        assertEquals(0.0F, copy.get("temperature").getAsFloat());
        // Plains has no colour overrides, so a changed temperature writes out the colours it had.
        JsonObject effects = copy.getAsJsonObject("effects");
        assertEquals(SeasonalCopies.writeColour(Colormaps.grass(0.8F, 0.4F)), effects.get("grass_color").getAsString());
        assertEquals(SeasonalCopies.writeColour(Colormaps.foliage(0.8F, 0.4F)), effects.get("foliage_color").getAsString());
    }

    @Test
    void nativeSnowKeepsItsClimate() {
        JsonObject snowyPlains = vanilla("snowy_plains");
        JsonObject copy = SeasonalCopies.copy(snowyPlains, WeatherKind.SNOW, CLIMATE, UNCHANGED);
        assertEquals(snowyPlains, copy);
    }

    @Test
    void winterRainReachesTheDesert() {
        JsonObject desert = vanilla("desert");
        JsonObject copy = SeasonalCopies.copy(desert, WeatherKind.RAIN, CLIMATE, UNCHANGED);
        assertTrue(copy.get("has_precipitation").getAsBoolean());
        assertEquals(desert.get("temperature"), copy.get("temperature"));
        assertFalse(copy.getAsJsonObject("effects").has("grass_color") && !desert.getAsJsonObject("effects").has("grass_color"));
    }

    @Test
    void midSummerDriesTemperateBiomes() {
        JsonObject copy = SeasonalCopies.copy(vanilla("plains"), WeatherKind.DRY, CLIMATE, UNCHANGED);
        assertFalse(copy.get("has_precipitation").getAsBoolean());
        assertEquals(vanilla("plains").get("temperature"), copy.get("temperature"));
    }

    @Test
    void summerWarmsColdBiomes() {
        JsonObject rain = SeasonalCopies.copy(vanilla("snowy_plains"), WeatherKind.RAIN, CLIMATE, UNCHANGED);
        assertEquals(0.5F, rain.get("temperature").getAsFloat());
        JsonObject snowLine = SeasonalCopies.copy(vanilla("snowy_plains"), WeatherKind.SNOW_LINE, CLIMATE, UNCHANGED);
        assertEquals(CLIMATE.snowLineTemperature(), snowLine.get("temperature").getAsFloat());
        // 48 blocks above sea level: vanilla's 0.05 per 40 blocks above sea level + 17 lands on 0.15 there.
        assertEquals(0.15F, CLIMATE.snowLineTemperature() - (48 - 17) * 0.05F / 40.0F, 1e-6);
    }

    @Test
    void frozenOceanLosesItsWarmPatchesWhenTheSeasonDecides() {
        JsonObject deepFrozen = vanilla("deep_frozen_ocean");
        assertEquals("frozen", deepFrozen.get("temperature_modifier").getAsString());
        JsonObject copy = SeasonalCopies.copy(deepFrozen, WeatherKind.SNOW, CLIMATE, UNCHANGED);
        assertFalse(copy.has("temperature_modifier"));
        assertEquals(0.0F, copy.get("temperature").getAsFloat());
    }

    @Test
    void overridesAreKeptUnlessThePaletteChangesThem() {
        JsonObject swamp = vanilla("swamp");
        JsonObject unchanged = SeasonalCopies.copy(swamp, WeatherKind.SNOW, CLIMATE, UNCHANGED);
        assertEquals(swamp.getAsJsonObject("effects").get("foliage_color"), unchanged.getAsJsonObject("effects").get("foliage_color"));
        JsonObject tinted = SeasonalCopies.copy(swamp, WeatherKind.RAIN, CLIMATE,
                channel -> channel == ColourChannel.FOLIAGE ? SeasonalPalette.Step.color(0x123456) : SeasonalPalette.Step.UNCHANGED);
        assertEquals("#123456", tinted.getAsJsonObject("effects").get("foliage_color").getAsString());
    }

    @Test
    void dappledForestMidFallIsVanillasLook() {
        JsonObject dappled = vanilla("dappled_forest");
        SeasonalPalette palette = SeasonalPalette.bundled();
        JsonObject copy = SeasonalCopies.copy(dappled, WeatherKind.RAIN, CLIMATE,
                channel -> palette.step("minecraft:dappled_forest", tag -> false, channel, SubSeason.MID_FALL));
        assertEquals(dappled, copy);
        JsonObject spring = SeasonalCopies.copy(dappled, WeatherKind.RAIN, CLIMATE,
                channel -> palette.step("minecraft:dappled_forest", tag -> false, channel, SubSeason.MID_SPRING));
        assertFalse(dappled.getAsJsonObject("effects").equals(spring.getAsJsonObject("effects")));
    }
}
