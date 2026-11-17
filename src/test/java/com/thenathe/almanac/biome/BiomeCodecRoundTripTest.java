package com.thenathe.almanac.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.thenathe.almanac.climate.WeatherKind;
import com.thenathe.almanac.colour.SeasonalPalette;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The copies go through the game's own biome codec, as the server makes them, and lose nothing on the way. */
class BiomeCodecRoundTripTest {
    private static HolderLookup.Provider registries;
    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = VanillaRegistries.createWorldLookup();
        ops = registries.createSerializationContext(JsonOps.INSTANCE);
    }

    private static Biome biome(net.minecraft.resources.ResourceKey<Biome> key) {
        return registries.lookupOrThrow(Registries.BIOME).getOrThrow(key).value();
    }

    private static JsonObject encode(Biome biome) {
        return Biome.DIRECT_CODEC.encodeStart(ops, biome).getOrThrow().getAsJsonObject();
    }

    @Test
    void copyDecodesAndEncodesBackUnchanged() {
        for (var key : new net.minecraft.resources.ResourceKey[] {Biomes.PLAINS, Biomes.DESERT, Biomes.SNOWY_PLAINS,
                Biomes.DARK_FOREST, Biomes.SWAMP, Biomes.DEEP_FROZEN_OCEAN, Biomes.MANGROVE_SWAMP}) {
            @SuppressWarnings("unchecked")
            Biome original = biome((net.minecraft.resources.ResourceKey<Biome>) key);
            JsonObject encoded = encode(original);
            for (WeatherKind kind : WeatherKind.values()) {
                JsonObject copyJson = SeasonalCopies.copy(encoded, kind, new SeasonalCopies.CopyClimate(0.0F, 0.5F, 48),
                        channel -> SeasonalPalette.Step.multiply(1.2F, 0.9F, 0.7F));
                Biome copy = Biome.DIRECT_CODEC.parse(ops, copyJson).getOrThrow();
                assertEquals(copyJson, encode(copy), key.identifier() + " " + kind);
                // Mob spawn settings have no equals, so attributes are compared as the codec writes them.
                assertEquals(encoded.get("attributes"), encode(copy).get("attributes"), key.identifier() + " attributes");
                // Colours read from text are opaque; the built-in biomes leave the alpha empty. The colour is the same.
                assertEquals(original.getWaterColor() & 0xFFFFFF, copy.getWaterColor() & 0xFFFFFF);
                assertEquals(original.getSpecialEffects().grassColorModifier(), copy.getSpecialEffects().grassColorModifier());
                assertEquals(original.getGenerationSettings().features(), copy.getGenerationSettings().features());
            }
        }
    }

    @Test
    void copiesFollowTheirWeather() {
        Biome desert = biome(Biomes.DESERT);
        Biome plains = biome(Biomes.PLAINS);
        Biome snowyPlains = biome(Biomes.SNOWY_PLAINS);
        SeasonalCopies.CopyClimate climate = new SeasonalCopies.CopyClimate(0.0F, 0.5F, 48);
        Biome winterDesert = copy(desert, WeatherKind.RAIN, climate);
        Biome winterPlains = copy(plains, WeatherKind.SNOW, climate);
        Biome summerPlains = copy(plains, WeatherKind.DRY, climate);
        Biome edgeOfSummerSnow = copy(snowyPlains, WeatherKind.SNOW_LINE, climate);
        BlockPos ground = new BlockPos(0, 70, 0);
        int seaLevel = 63;
        assertEquals(Biome.Precipitation.NONE, desert.getPrecipitationAt(ground, seaLevel));
        assertEquals(Biome.Precipitation.RAIN, winterDesert.getPrecipitationAt(ground, seaLevel));
        assertEquals(Biome.Precipitation.SNOW, winterPlains.getPrecipitationAt(ground, seaLevel));
        assertEquals(Biome.Precipitation.NONE, summerPlains.getPrecipitationAt(ground, seaLevel));
        assertEquals(Biome.Precipitation.RAIN, edgeOfSummerSnow.getPrecipitationAt(ground, seaLevel));
        assertEquals(Biome.Precipitation.SNOW, edgeOfSummerSnow.getPrecipitationAt(new BlockPos(0, 63 + 48 + 12, 0), seaLevel));
        assertTrue(winterPlains.coldEnoughToSnow(ground, seaLevel));
        assertFalse(plains.coldEnoughToSnow(ground, seaLevel));
    }

    private static Biome copy(Biome original, WeatherKind kind, SeasonalCopies.CopyClimate climate) {
        JsonObject json = SeasonalCopies.copy(encode(original), kind, climate, channel -> SeasonalPalette.Step.UNCHANGED);
        return Biome.DIRECT_CODEC.parse(ops, json).getOrThrow();
    }
}
