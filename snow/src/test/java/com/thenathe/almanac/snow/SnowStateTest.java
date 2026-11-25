package com.thenathe.almanac.snow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.SeasonTick;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Which state changes a chunk has to follow, and what a state saves. */
class SnowStateTest {
    private static final Map<Climate, Double> SNOWY = Map.of(Climate.TEMPERATE, 1.0, Climate.COLD, 1.0);
    private static final Map<Climate, Double> FROZEN = Map.of(Climate.TEMPERATE, 1.0, Climate.COLD, 1.0);
    private static final SnowState WINTER = winter(SNOWY, 1);

    private static SnowState winter(Map<Climate, Double> depths, int snowHeight) {
        return new SnowState(SubSeason.MID_WINTER, depths, FROZEN, 1, true, snowHeight);
    }

    @Test
    void onlyTheAmountsOfTheClimatesAChunkHoldsCount() {
        SnowState hotSnow = winter(Map.of(Climate.TEMPERATE, 1.0, Climate.COLD, 1.0, Climate.HOT, 0.5), 1);
        // A chunk of TEMPERATE and COLD land doesn't care about HOT snow; a desert does.
        assertTrue(hotSnow.sameFrame(WINTER, EnumSet.of(Climate.TEMPERATE, Climate.COLD)));
        assertTrue(hotSnow.sameAmounts(WINTER, EnumSet.of(Climate.TEMPERATE, Climate.COLD)));
        assertFalse(hotSnow.sameAmounts(WINTER, EnumSet.of(Climate.HOT)));
        // Unknown climates (a record from before 0.1.3): every climate's amounts count.
        assertFalse(hotSnow.sameAmounts(WINTER, null));
        assertTrue(WINTER.sameAmounts(WINTER, null));
    }

    @Test
    void theSubSeasonTheSnowHeightAndTheClimateSettingsAlwaysCount() {
        SnowState late = new SnowState(SubSeason.LATE_WINTER, SNOWY, FROZEN, 1, true, 1);
        assertFalse(late.sameFrame(WINTER, Set.of()));
        assertFalse(winter(SNOWY, 2).sameFrame(WINTER, EnumSet.of(Climate.TEMPERATE)), "the natural height moves everywhere");
        SnowState otherSettings = new SnowState(SubSeason.MID_WINTER, SNOWY, FROZEN, 1, true, 1,
                WINTER.climateSettings() + 1, WINTER.noSeasonalSnow());
        assertFalse(otherSettings.sameFrame(WINTER, Set.of()));
    }

    @Test
    void theMostLayersIsNotPartOfTheFrameButMovesTheShares() {
        // A full first layer with snow allowed one layer deep, then three: the first layer's share is no longer whole,
        // so the columns between are looked at, not every column.
        SnowState upTo3 = new SnowState(SubSeason.MID_WINTER, SNOWY, FROZEN, 3, true, 1);
        assertTrue(upTo3.sameFrame(WINTER, EnumSet.of(Climate.TEMPERATE)));
        assertFalse(upTo3.sameAmounts(WINTER, EnumSet.of(Climate.TEMPERATE)));
        assertEquals(1.0, WINTER.shares(Climate.TEMPERATE).cover());
        assertEquals(0.95, upTo3.shares(Climate.TEMPERATE).cover(), 1e-12);
    }

    @Test
    void theBiomesWithoutSeasonalSnowCountButCanBeSetApart() {
        SnowState jungles = WINTER.withNoSeasonalSnow(12345);
        assertFalse(jungles.sameFrame(WINTER, EnumSet.of(Climate.TEMPERATE)));
        assertTrue(jungles.sameFrameBesidesNoSeasonalSnow(WINTER, EnumSet.of(Climate.TEMPERATE)));
        SnowState lateJungles = new SnowState(SubSeason.LATE_WINTER, SNOWY, FROZEN, 1, true, 1).withNoSeasonalSnow(12345);
        assertFalse(lateJungles.sameFrameBesidesNoSeasonalSnow(WINTER, EnumSet.of(Climate.TEMPERATE)));
    }

    @Test
    void aStateFrom017HasNoBiomesWithoutSeasonalSnow() {
        var json = JsonParser.parseString("{\"sub_season\": \"mid_winter\", \"layers\": {\"temperate\": 1, \"cold\": 1}, "
                + "\"native_snow_melts\": true, \"snow_height\": 1, \"climate_settings\": " + SnowState.DEFAULT_CLIMATE_SETTINGS + "}");
        SnowState read = SnowState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(SeasonTick.NO_SEASONAL_SNOW_UNKNOWN, read.noSeasonalSnow());
        assertTrue(WINTER.withNoSeasonalSnow(42).sameFrameBesidesNoSeasonalSnow(read, null));
        assertTrue(WINTER.sameAmounts(read, null), "whole layers read as a whole cover, ice where it was cold");
    }

    @Test
    void theNativeMeltSettingOnlyCountsWithColdLand() {
        SnowState meltOff = new SnowState(SubSeason.MID_WINTER, SNOWY, FROZEN, 1, false, 1);
        assertTrue(meltOff.sameFrame(WINTER, EnumSet.of(Climate.TEMPERATE)));
        assertFalse(meltOff.sameFrame(WINTER, EnumSet.of(Climate.TEMPERATE, Climate.COLD)));
        assertFalse(meltOff.sameFrame(WINTER, null));
    }

    @Test
    void aStateFromBefore013CountsAsTheDefaultClimateSettings() {
        var json = JsonParser.parseString("{\"sub_season\": \"mid_winter\", \"layers\": {\"temperate\": 1, \"cold\": 1}}");
        SnowState read = SnowState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertTrue(WINTER.sameFrame(read, null) && WINTER.sameAmounts(read, null));
        assertEquals(SnowState.DEFAULT_CLIMATE_SETTINGS, read.climateSettings());
        SnowState changed = new SnowState(SubSeason.MID_WINTER, Map.of(Climate.COLD, 2.37), Map.of(Climate.COLD, 0.4), 3,
                true, 1, 12345, 678);
        var saved = SnowState.CODEC.encodeStart(JsonOps.INSTANCE, changed).getOrThrow();
        assertEquals(changed, SnowState.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow());
    }
}
