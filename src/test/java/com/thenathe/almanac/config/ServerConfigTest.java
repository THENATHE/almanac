package com.thenathe.almanac.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import org.junit.jupiter.api.Test;

class ServerConfigTest {
    @Test
    void valuesAreClamped() {
        ServerConfig config = ServerConfig.fromJson("""
                {"daysPerSeason": 0, "snowLineAboveSea": 9000, "winterTemperature": 3.0, "summerTemperature": -4,
                 "refreshChunksPerTick": 1000, "temperateShare": {"mid_winter": 250, "early_spring": -5}}
                """);
        assertEquals(ServerConfig.MIN_DAYS_PER_SEASON, config.daysPerSeason);
        assertEquals(ServerConfig.MAX_SNOW_LINE, config.snowLineAboveSea);
        assertEquals(ServerConfig.MAX_WINTER_TEMPERATURE, config.winterTemperature);
        assertEquals(ServerConfig.MIN_SUMMER_TEMPERATURE, config.summerTemperature);
        assertEquals(ServerConfig.MAX_REFRESH_CHUNKS, config.refreshChunksPerTick);
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
    void thawTimeDefaultsToTwoDaysAndClamps() {
        assertEquals(2.0, new ServerConfig().thawDays);
        assertEquals(ServerConfig.MIN_THAW_DAYS, ServerConfig.fromJson("{\"thawDays\": 0}").thawDays);
        assertEquals(ServerConfig.MAX_THAW_DAYS, ServerConfig.fromJson("{\"thawDays\": 400}").thawDays);
        // A 0.1.0 file has no thaw time and gets the default.
        assertEquals(2.0, ServerConfig.fromJson("{\"daysPerSeason\": 30}").thawDays);
        ServerConfig config = new ServerConfig();
        ServerSetting.byName("thawDays").orElseThrow().set(config, "0.5");
        assertEquals(0.5, config.validated().thawDays);
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
        // Four season settings, the 26 shares (11 temperate, 3 hot, 12 cold), three copy climate settings, five snow
        // settings, two colour settings and four far terrain settings.
        assertEquals(4 + 26 + 3 + 5 + 2 + 4, ServerSetting.all().size());
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
}
