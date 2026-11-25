package com.thenathe.almanac.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.WeatherKind;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The climate table against what Almanac 0.1.7's seasonal biome copies answered, read on a 0.1.7 server for every
 * overworld biome of vanilla 26.3 ({@code src/test/resources/climate-017}, from the QA command {@code climatedump}):
 * the same base climate and ice, whether anything falls, the temperature, and at 280 positions in each sub-season
 * what falls and whether it is cold enough to snow. Once with the default settings and once with all three changed.
 * Jungles alone differ, by the rule of 2026-11-20: rain where the copies snowed, and never cold enough for snow.
 */
class ClimateTableTest {
    private static HolderLookup.RegistryLookup<Biome> vanilla;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        vanilla = VanillaRegistries.createWorldLookup().lookupOrThrow(Registries.BIOME);
    }

    static JsonObject answersOf017(String name) throws IOException {
        try (InputStream stream = Objects.requireNonNull(ClimateTableTest.class.getResourceAsStream("/climate-017/" + name + ".json.gz"));
             InputStreamReader reader = new InputStreamReader(new GZIPInputStream(stream), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Set<String> tags(JsonObject biome) {
        Set<String> tags = new HashSet<>();
        biome.getAsJsonArray("tags").forEach(tag -> tags.add(tag.getAsString()));
        // The tag 0.2.0 adds, as its file fills it: the jungle tags.
        if (tags.contains("minecraft:is_jungle") || tags.contains("c:is_jungle")) {
            tags.add(ClimateTable.NO_SEASONAL_SNOW_TAG);
        }
        return tags;
    }

    private static ClimateSettings settings(JsonObject dump) {
        return new ClimateSettings(dump.get("winterTemperature").getAsFloat(), dump.get("summerTemperature").getAsFloat(),
                dump.get("snowLineAboveSea").getAsInt());
    }

    private static int compare(String name) throws IOException {
        JsonObject dump = answersOf017(name);
        ClimateSettings settings = settings(dump);
        int seaLevel = dump.get("seaLevel").getAsInt();
        JsonArray columns = dump.getAsJsonArray("columns");
        JsonArray heights = dump.getAsJsonArray("heights");
        int compared = 0;
        int jungles = 0;
        for (JsonElement element : dump.getAsJsonArray("biomes")) {
            JsonObject biome = element.getAsJsonObject();
            String id = biome.get("id").getAsString();
            Holder<Biome> holder = vanilla.getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse(id)));
            Set<String> tags = tags(biome);
            ClimateTable.Entry entry = ClimateTable.Entry.of(holder, tags::contains, settings);
            boolean jungle = tags.contains(ClimateTable.NO_SEASONAL_SNOW_TAG);
            jungles += jungle ? 1 : 0;
            assertEquals(biome.get("base").getAsString(), entry.base().id(), id);
            assertEquals(biome.get("seasonalIce").getAsBoolean(), entry.seasonalIce(), id);
            assertEquals(biome.get("snowSurface").getAsBoolean(), entry.snowSurface(), id);
            assertEquals(jungle, entry.noSeasonalSnow(), id);
            for (SubSeason subSeason : SubSeason.values()) {
                JsonObject copy = biome.getAsJsonObject("subSeasons").getAsJsonObject(subSeason.id());
                String where = id + " " + subSeason.id() + " (" + name + ")";
                ClimateTable.Cell cell = entry.cell(subSeason);
                assertEquals(copy.get("hasPrecipitation").getAsBoolean(), cell.canFall(), where);
                if (!jungle) {
                    assertEquals(copy.get("temperature").getAsFloat(), cell.temperature(), where);
                }
                String answers = copy.get("answers").getAsString();
                int index = 0;
                for (JsonElement column : columns) {
                    int x = column.getAsJsonArray().get(0).getAsInt();
                    int z = column.getAsJsonArray().get(1).getAsInt();
                    for (JsonElement height : heights) {
                        BlockPos pos = new BlockPos(x, height.getAsInt(), z);
                        char falls = answers.charAt(index++);
                        boolean cold = answers.charAt(index++) == 'c';
                        String at = where + " at " + pos.toShortString();
                        if (jungle) {
                            assertFalse(entry.coldEnoughToSnow(subSeason, pos, seaLevel), at);
                            assertEquals(falls == 'n' ? Biome.Precipitation.NONE : Biome.Precipitation.RAIN,
                                    entry.precipitationAt(subSeason, pos, seaLevel), at);
                        } else {
                            assertEquals(cold, entry.coldEnoughToSnow(subSeason, pos, seaLevel), at);
                            if (!cell.ownTemperature()) {
                                // The temperature far terrain and the API read gives the same answer. (A cell with the
                                // biome's own temperature reads it through a mixin, which unit tests don't apply.)
                                assertEquals(cold, SeasonTemperature.isCold(entry.temperatureAt(subSeason, pos, seaLevel)),
                                        at + ": the temperature agrees");
                            }
                            assertEquals(switch (falls) {
                                case 'n' -> Biome.Precipitation.NONE;
                                case 'r' -> Biome.Precipitation.RAIN;
                                default -> Biome.Precipitation.SNOW;
                            }, entry.precipitationAt(subSeason, pos, seaLevel), at);
                        }
                        compared++;
                    }
                }
            }
        }
        assertEquals(3, jungles, "jungle, sparse jungle and bamboo jungle");
        return compared;
    }

    @Test
    void theTableAnswersAsTheCopiesDidWithTheDefaultSettings() throws IOException {
        assertEquals(57, answersOf017("default").getAsJsonArray("biomes").size());
        assertEquals(57 * 12 * 8 * 35, compare("default"));
    }

    @Test
    void theTableAnswersAsTheCopiesDidWithEverySettingChanged() throws IOException {
        JsonObject dump = answersOf017("changed");
        assertEquals(new ClimateSettings(-0.45F, 1.1F, 90), settings(dump));
        assertEquals(57 * 12 * 8 * 35, compare("changed"));
    }

    @Test
    void junglesStayTemperateWithRainInWinter() throws IOException {
        JsonObject dump = answersOf017("default");
        for (JsonElement element : dump.getAsJsonArray("biomes")) {
            JsonObject biome = element.getAsJsonObject();
            if (!biome.get("id").getAsString().equals("minecraft:jungle")) {
                continue;
            }
            Holder<Biome> jungle = vanilla.getOrThrow(ResourceKey.create(Registries.BIOME, Identifier.parse("minecraft:jungle")));
            Set<String> tags = tags(biome);
            ClimateTable.Entry entry = ClimateTable.Entry.of(jungle, tags::contains, ClimateSettings.DEFAULTS);
            assertEquals(Climate.TEMPERATE, entry.base());
            for (SubSeason subSeason : SubSeason.values()) {
                ClimateTable.Cell cell = entry.cell(subSeason);
                // Rain in spring, summer (but mid summer's dry spell, as every TEMPERATE biome) and fall, and in winter.
                assertEquals(subSeason != SubSeason.MID_SUMMER, cell.canFall(), subSeason.id());
                assertTrue(cell.ownTemperature(), subSeason.id() + ": winter doesn't cool it");
                assertEquals(Biome.Precipitation.RAIN.equals(entry.precipitationAt(subSeason, new BlockPos(0, 64, 0), 63)),
                        cell.canFall(), subSeason.id());
            }
            // The copies snowed in jungles in winter; that is what the rule changes.
            JsonObject midWinter = biome.getAsJsonObject("subSeasons").getAsJsonObject("mid_winter");
            assertTrue(midWinter.get("answers").getAsString().startsWith("sc"));
            return;
        }
        throw new AssertionError("no jungle in the answers");
    }

    @Test
    void theWeatherKindGivesRainForSnowInTaggedBiomes() {
        for (Climate base : Climate.values()) {
            for (SubSeason subSeason : SubSeason.values()) {
                WeatherKind kind = WeatherKind.of(base, subSeason);
                WeatherKind tagged = ClimateTable.weatherKind(base, subSeason, true);
                assertEquals(kind, ClimateTable.weatherKind(base, subSeason, false));
                assertEquals(kind == WeatherKind.SNOW || kind == WeatherKind.SNOW_LINE ? WeatherKind.RAIN : kind, tagged);
            }
        }
    }
}
