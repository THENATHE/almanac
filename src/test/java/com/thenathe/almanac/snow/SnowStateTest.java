package com.thenathe.almanac.snow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.biome.SeasonalCopies;
import com.thenathe.almanac.config.ServerConfig;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Which state changes a chunk has to follow, and the climate settings in the state. */
class SnowStateTest {
    private static final Map<Climate, Integer> SNOWY = Map.of(Climate.TEMPERATE, 1, Climate.COLD, 1);
    private static final SnowState WINTER = new SnowState(SubSeason.MID_WINTER, SNOWY, true, 1);

    @Test
    void onlyTheClimatesAChunkHoldsCount() {
        SnowState deeperCold = new SnowState(SubSeason.MID_WINTER, Map.of(Climate.TEMPERATE, 1, Climate.COLD, 2), true, 2);
        SnowState sameHeightDeeperCold = new SnowState(SubSeason.MID_WINTER, Map.of(Climate.TEMPERATE, 1, Climate.COLD, 1,
                Climate.HOT, 1), true, 1);
        // A pure desert doesn't care about COLD or TEMPERATE snow.
        assertTrue(sameHeightDeeperCold.sameFor(WINTER, EnumSet.of(Climate.TEMPERATE, Climate.COLD)));
        assertFalse(sameHeightDeeperCold.sameFor(WINTER, EnumSet.of(Climate.HOT)));
        // The snow height always counts: it moves the natural height everywhere.
        assertFalse(deeperCold.sameFor(WINTER, EnumSet.of(Climate.TEMPERATE)));
        // Unknown climates (a record from before 0.1.3): the whole state counts.
        assertFalse(sameHeightDeeperCold.sameFor(WINTER, null));
        assertTrue(WINTER.sameFor(WINTER, null));
    }

    @Test
    void theSubSeasonAndTheClimateSettingsAlwaysCount() {
        SnowState spring = new SnowState(SubSeason.LATE_WINTER, SNOWY, true, 1);
        assertFalse(spring.sameFor(WINTER, Set.of()));
        SnowState otherSettings = new SnowState(SubSeason.MID_WINTER, SNOWY, true, 1, WINTER.climateSettings() + 1);
        assertFalse(otherSettings.sameFor(WINTER, Set.of()));
        assertTrue(otherSettings.mayThawFrom(WINTER));
        assertTrue(otherSettings.mayFormFrom(WINTER));
    }

    @Test
    void theNativeMeltSettingOnlyCountsWithColdLand() {
        SnowState meltOff = new SnowState(SubSeason.MID_WINTER, SNOWY, false, 1);
        assertTrue(meltOff.sameFor(WINTER, EnumSet.of(Climate.TEMPERATE)));
        assertFalse(meltOff.sameFor(WINTER, EnumSet.of(Climate.TEMPERATE, Climate.COLD)));
    }

    @Test
    void onlyChangesThatCanBringSnowStartItForming() {
        SnowState moreSnow = new SnowState(SubSeason.MID_WINTER, Map.of(Climate.TEMPERATE, 2, Climate.COLD, 1), true, 2);
        SnowState lessSnow = new SnowState(SubSeason.MID_WINTER, Map.of(Climate.COLD, 1), true, 1);
        SnowState meltOff = new SnowState(SubSeason.MID_WINTER, SNOWY, false, 1);
        assertTrue(moreSnow.mayFormFrom(WINTER));
        assertTrue(WINTER.mayFormFrom(null));
        assertTrue(new SnowState(SubSeason.LATE_WINTER, SNOWY, true, 1).mayFormFrom(WINTER));
        assertFalse(lessSnow.mayFormFrom(WINTER));
        assertFalse(meltOff.mayFormFrom(WINTER));
        assertFalse(WINTER.mayFormFrom(WINTER));
    }

    @Test
    void aStateFromBefore013CountsAsTheDefaultClimateSettings() {
        var json = JsonParser.parseString("{\"sub_season\": \"mid_winter\", \"layers\": {\"temperate\": 1, \"cold\": 1}}");
        SnowState read = SnowState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(WINTER, read);
        assertEquals(SnowState.DEFAULT_CLIMATE_SETTINGS, read.climateSettings());
        SnowState changed = new SnowState(SubSeason.MID_WINTER, SNOWY, true, 1, 12345);
        var saved = SnowState.CODEC.encodeStart(JsonOps.INSTANCE, changed).getOrThrow();
        assertEquals(changed, SnowState.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());
    }

    @Test
    void theFingerprintFollowsEachClimateSetting() {
        SeasonalCopies.CopyClimate defaults = SeasonalCopies.CopyClimate.of(new ServerConfig());
        assertEquals(new SeasonalCopies.CopyClimate(0.0F, 0.5F, 48), defaults);
        assertEquals(SeasonalCopies.CopyClimate.DEFAULTS, defaults);
        int fingerprint = defaults.fingerprint();
        assertNotEquals(fingerprint, new SeasonalCopies.CopyClimate(-0.05F, 0.5F, 48).fingerprint());
        assertNotEquals(fingerprint, new SeasonalCopies.CopyClimate(0.0F, 0.55F, 48).fingerprint());
        assertNotEquals(fingerprint, new SeasonalCopies.CopyClimate(0.0F, 0.5F, 49).fingerprint());
        // Saved in every chunk record: if this number changes, every chunk in every world is brought up to date again.
        assertEquals(-1_593_805_681, fingerprint);
    }
}
