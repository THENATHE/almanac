package com.thenathe.almanac.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServerConfigTest {
    @TempDir
    Path folder;

    @Test
    void valuesAreClamped() {
        ServerConfig config = ServerConfig.fromJson("""
                {"daysPerSeason": 0, "snowLineAboveSea": 9000, "winterTemperature": 3.0, "summerTemperature": -4,
                 "catchUpChunksPerTick": 1000, "temperateShare": {"mid_winter": 250, "early_spring": -5}}
                """);
        assertEquals(ServerConfig.MIN_DAYS_PER_SEASON, config.daysPerSeason);
        assertEquals(ServerConfig.MAX_SNOW_LINE, config.snowLineAboveSea);
        assertEquals(ServerConfig.MAX_WINTER_TEMPERATURE, config.winterTemperature);
        assertEquals(ServerConfig.MIN_SUMMER_TEMPERATURE, config.summerTemperature);
        assertEquals(ServerConfig.MAX_CATCH_UP_CHUNKS, config.catchUpChunksPerTick);
        assertEquals(100, config.share(Climate.TEMPERATE, SubSeason.MID_WINTER));
        assertEquals(0, config.share(Climate.TEMPERATE, SubSeason.EARLY_SPRING));
    }

    @Test
    void missingAndUnknownFieldsFallBackToDefaults() {
        ServerConfig config = ServerConfig.fromJson("""
                {"startSubSeason": "monsoon", "hotShare": {"mid_summer": 90, "bogus": 3}, "futureField": true}
                """);
        ServerConfig defaults = new ServerConfig();
        assertEquals(SubSeason.EARLY_SPRING, config.startSubSeason());
        assertEquals(defaults.daysPerSeason, config.daysPerSeason);
        // Dry cells and unknown keys are dropped; the real cells are filled in.
        assertFalse(config.hotShare.containsKey("mid_summer"));
        assertFalse(config.hotShare.containsKey("bogus"));
        assertEquals(defaults.hotShare, config.hotShare);
        assertEquals(defaults.coldShare, config.coldShare);
        assertEquals(0, config.share(Climate.HOT, SubSeason.MID_SUMMER));
    }

    @Test
    void notANumberFallsBackToTheDefault() {
        ServerConfig config = new ServerConfig();
        config.winterTemperature = Double.NaN;
        config.validated();
        assertEquals(new ServerConfig().winterTemperature, config.winterTemperature);
    }

    @Test
    void unreadableTextIsRejected() {
        assertNull(ServerConfig.fromJson("{not json"));
        assertNull(ServerConfig.fromJson(""));
        assertNull(ServerConfig.fromJson("{\"temperateShare\": {\"mid_winter\": \"lots\"}}"));
    }

    @Test
    void roundTripKeepsEverything() {
        ServerConfig config = new ServerConfig();
        config.daysPerSeason = 30;
        config.paused = true;
        config.coldShare.put("early_summer", 25);
        ServerConfig copy = ServerConfig.fromJson(config.toJson());
        assertEquals(config.toJson(), copy.toJson());
        assertTrue(copy.toJson().contains("\"_credits\""));
    }

    @Test
    void snowSettingsDefaultAndClamp() {
        ServerConfig defaults = new ServerConfig();
        assertTrue(defaults.nativeSnowMeltsInSummer);
        assertTrue(defaults.snowCatchUp);
        ServerConfig config = ServerConfig.fromJson("""
                {"snowColumnsPerTick": 0, "catchUpChunksPerTick": 100000}
                """);
        assertEquals(ServerConfig.MIN_SNOW_COLUMNS, config.snowColumnsPerTick);
        assertEquals(ServerConfig.MAX_CATCH_UP_CHUNKS, config.catchUpChunksPerTick);
        // A file from before the snow settings existed gets their defaults.
        ServerConfig older = ServerConfig.fromJson("{\"daysPerSeason\": 30}");
        assertTrue(older.nativeSnowMeltsInSummer);
        assertEquals(defaults.snowColumnsPerTick, older.snowColumnsPerTick);
        ServerSetting.byName("nativeSnowMeltsInSummer").orElseThrow().set(config, "off");
        assertFalse(config.nativeSnowMeltsInSummer);
    }

    @Test
    void theClocksAndTheMostLayersDefaultAsConfirmedAndClamp() {
        ServerConfig defaults = new ServerConfig();
        assertEquals(12_000, defaults.snowfallTicks);
        assertEquals(18_000, defaults.thawTicks, "the thaw takes 1.5 times as long");
        assertEquals(0, defaults.iceTicks, "ice on the snowfall clock");
        assertEquals(3, defaults.maxSnowLayers);
        ServerConfig clamped = ServerConfig.fromJson("""
                {"snowfallTicks": 1, "thawTicks": 999999999, "iceTicks": -5, "maxSnowLayers": 40}
                """);
        assertEquals(ServerConfig.MIN_CLOCK_TICKS, clamped.snowfallTicks);
        assertEquals(ServerConfig.MAX_CLOCK_TICKS, clamped.thawTicks);
        assertEquals(0, clamped.iceTicks);
        assertEquals(8, clamped.maxSnowLayers);
        ServerSetting.byName("iceTicks").orElseThrow().set(clamped, "6000");
        assertEquals(6000, clamped.validated().iceTicks);
    }

    @Test
    void aSettingsFileFrom016KeepsEveryValue() throws IOException {
        // As 0.1.6 writes it, with every setting moved off its default and the retired thawDays, vanillaClientCopies and
        // refreshChunksPerTick among them.
        Path path = folder.resolve("almanac-server.json");
        try (InputStream from016 = ServerConfigTest.class.getResourceAsStream("/saves-016/almanac-server.json")) {
            Files.copy(from016, path);
        }
        JsonObject written = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        SettingsFile<ServerConfig> file = new SettingsFile<>(() -> path, ServerConfig::fromJson, ServerConfig::toJson);

        ServerConfig read = file.readOr(ServerConfig::new);
        assertEquals(30, read.daysPerSeason);
        assertEquals(SubSeason.MID_FALL, read.startSubSeason());
        assertTrue(read.paused);
        assertFalse(read.nativeSnowMeltsInSummer);
        assertFalse(read.snowCatchUp);
        assertEquals(512, read.snowColumnsPerTick);
        assertEquals(64, read.farClimateCellSize);
        assertEquals(64, read.snowLineAboveSea);
        assertEquals(-0.25, read.winterTemperature);
        assertEquals(0.8, read.summerTemperature);
        assertEquals(40, read.share(Climate.TEMPERATE, SubSeason.MID_WINTER));
        assertEquals(70, read.share(Climate.COLD, SubSeason.LATE_FALL));
        assertEquals(0, read.share(Climate.HOT, SubSeason.EARLY_WINTER));

        // Saved again, the file holds every value it had, less the setting that is gone.
        file.save(read);
        JsonObject saved = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        for (String retired : new String[] {"thawDays", "vanillaClientCopies", "refreshChunksPerTick"}) {
            written.remove(retired);
            assertTrue(ServerSetting.byName(retired).isEmpty(), retired);
        }
        written.remove("_credits");
        assertEquals(written, withoutNewSettings(saved));
    }

    @Test
    void aSettingsFileFrom017LosesOnlyTheCopiesSettings() throws IOException {
        // As a 0.1.7 server wrote it in an upgrade test, with the two settings only the seasonal copies used.
        Path path = folder.resolve("almanac-server.json");
        try (InputStream from017 = ServerConfigTest.class.getResourceAsStream("/saves-017/almanac-server.json")) {
            Files.copy(from017, path);
        }
        JsonObject written = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        assertTrue(written.has("vanillaClientCopies") && written.has("refreshChunksPerTick"));
        SettingsFile<ServerConfig> file = new SettingsFile<>(() -> path, ServerConfig::fromJson, ServerConfig::toJson);
        ServerConfig read = file.readOr(ServerConfig::new);
        assertEquals(-0.25, read.winterTemperature);
        assertEquals(30, read.daysPerSeason);
        file.save(read);
        written.remove("vanillaClientCopies");
        written.remove("refreshChunksPerTick");
        written.remove("_credits");
        assertEquals(written, withoutNewSettings(JsonParser.parseString(Files.readString(path)).getAsJsonObject()));
    }

    /**
     * A file saved by this version with 0.3.0's, 0.4.0's and 1.1.0's settings checked at their defaults and taken out, and its
     * credits note checked to be this version's (the note is rewritten on every save, so it follows the credits).
     */
    private static JsonObject withoutNewSettings(JsonObject saved) {
        JsonObject older = saved.deepCopy();
        assertEquals(ServerConfig.CREDITS, older.remove("_credits").getAsString());
        assertTrue(older.remove("regionalWeather").getAsBoolean());
        assertFalse(older.remove("weatherCommandEverywhere").getAsBoolean());
        assertEquals(25, older.remove("timeUnderRain").getAsInt());
        assertEquals(12_000, older.remove("snowfallTicks").getAsInt());
        assertEquals(18_000, older.remove("thawTicks").getAsInt());
        assertEquals(0, older.remove("iceTicks").getAsInt());
        assertEquals(3, older.remove("maxSnowLayers").getAsInt());
        return older;
    }

    @Test
    void aChangeToARetiredSettingIsIgnored() {
        // A 0.1.6 client's settings screen still sends thawDays to a newer server.
        ServerConfig base = new ServerConfig();
        base.daysPerSeason = 30;
        ServerConfig changed = ServerConfig.withChanges(base, "{\"thawDays\": 3.0, \"paused\": true}");
        assertTrue(changed.paused);
        assertEquals(30, changed.daysPerSeason);
        assertFalse(changed.toJson().contains("thawDays"));
    }

    @Test
    void everySettingCanBeSetByName() {
        ServerConfig config = new ServerConfig();
        ServerSetting.byName("temperateShare.mid_winter").orElseThrow().set(config, "35");
        ServerSetting.byName("startSubSeason").orElseThrow().set(config, "MID_FALL");
        ServerSetting.byName("paused").orElseThrow().set(config, "on");
        ServerSetting.byName("winterTemperature").orElseThrow().set(config, "-0.25");
        config.validated();
        assertEquals(35, config.share(Climate.TEMPERATE, SubSeason.MID_WINTER));
        assertEquals(SubSeason.MID_FALL, config.startSubSeason());
        assertTrue(config.paused);
        assertEquals(-0.25, config.winterTemperature);
        assertFalse(ServerSetting.byName("temperateShare.mid_summer").isPresent());
        assertThrows(IllegalArgumentException.class, () -> ServerSetting.byName("daysPerSeason").orElseThrow().set(config, "many"));
        assertThrows(IllegalArgumentException.class, () -> ServerSetting.byName("paused").orElseThrow().set(config, "maybe"));
        // Four season settings, three weather settings (two from 0.4.0, the time under rain from 1.1.0), the 26 shares (11 temperate, 3 hot, 12 cold), three
        // climate settings, eight snow settings (the three clocks and the most layers from 0.3.0) and four far terrain
        // settings. The two colour settings for players without Almanac went in 0.2.0.
        assertEquals(4 + 3 + 26 + 3 + 8 + 4, ServerSetting.all().size());
    }

    @Test
    void farTerrainSettingsStayInRange() {
        ServerConfig config = ServerConfig.fromJson("""
                {"farClimateRadius": 99999, "farClimateCellSize": 40, "farClimateBytesPerTick": 3}
                """);
        assertEquals(ServerConfig.MAX_FAR_RADIUS, config.farClimateRadius);
        assertEquals(32, config.farClimateCellSize);
        assertEquals(ServerConfig.MIN_FAR_BYTES, config.farClimateBytesPerTick);
        assertEquals(16, ServerConfig.fromJson("{\"farClimateCellSize\": -5}").farClimateCellSize);
        assertEquals(128, ServerConfig.fromJson("{\"farClimateCellSize\": 5000}").farClimateCellSize);
        // A file from before 0.1.2 gets the far terrain defaults.
        ServerConfig older = ServerConfig.fromJson("{\"daysPerSeason\": 30}");
        ServerConfig defaults = new ServerConfig();
        assertTrue(older.farClimateMap);
        assertEquals(defaults.farClimateRadius, older.farClimateRadius);
        assertEquals(defaults.farClimateCellSize, older.farClimateCellSize);
        ServerSetting.byName("farClimateCellSize").orElseThrow().set(config, "64");
        assertEquals(64, config.farClimateCellSize);
        assertThrows(IllegalArgumentException.class,
                () -> ServerSetting.byName("farClimateCellSize").orElseThrow().set(config, "48"));
    }

    @Test
    void theScreenSendsOnlyWhatChanged() {
        ServerConfig opened = new ServerConfig();
        ServerConfig edited = opened.copy();
        edited.paused = true;
        edited.temperateShare.put("mid_winter", 35);
        JsonObject changes = ServerConfig.changes(opened, edited);
        assertEquals(JsonParser.parseString("{\"paused\": true, \"temperateShare\": {\"mid_winter\": 35}}"), changes);
        assertTrue(ServerConfig.changes(opened, opened.copy()).isEmpty());
    }

    @Test
    void changesGoOnTopOfTheLatestSettings() {
        ServerConfig opened = new ServerConfig();
        ServerConfig edited = opened.copy();
        edited.paused = true;
        edited.temperateShare.put("mid_winter", 35);
        // Meanwhile the console made the seasons longer and changed another share.
        ServerConfig latest = opened.copy();
        latest.daysPerSeason = 48;
        latest.temperateShare.put("late_winter", 10);

        ServerConfig saved = ServerConfig.withChanges(latest, ServerConfig.changes(opened, edited).toString());
        assertEquals(48, saved.daysPerSeason, "the console's change stays");
        assertEquals(10, saved.share(Climate.TEMPERATE, SubSeason.LATE_WINTER));
        assertTrue(saved.paused);
        assertEquals(35, saved.share(Climate.TEMPERATE, SubSeason.MID_WINTER));
    }

    @Test
    void changesAreCheckedLikeAFile() {
        ServerConfig base = new ServerConfig();
        ServerConfig saved = ServerConfig.withChanges(base, "{\"daysPerSeason\": 0, \"version\": 99, \"unknown\": 1}");
        assertEquals(ServerConfig.MIN_DAYS_PER_SEASON, saved.daysPerSeason);
        assertEquals(1, saved.version);
        assertNull(ServerConfig.withChanges(base, "[1, 2]"));
        assertNull(ServerConfig.withChanges(base, "{not json"));
        assertNull(ServerConfig.withChanges(base, "{\"daysPerSeason\": \"many\"}"));
    }
}
