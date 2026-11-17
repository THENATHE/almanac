package com.thenathe.almanac.snow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.season.SeasonData;
import java.util.EnumSet;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/**
 * The snow depths saved when early spring begins, which far terrain shows until mid spring, and how a world from
 * 0.1.0 or 0.1.1 (which never saved them) gets them back.
 */
class ThawStartSnowTest {
    private static final int SNOW_HEIGHT = 1;

    private static SeasonData read(Tag tag) {
        return SeasonData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    @Test
    void seasonDataFromOlderVersionsStillReadsAndHasNoThawStartSnow() {
        // season.dat as 0.1.0 and 0.1.1 write it.
        CompoundTag older = new CompoundTag();
        older.putInt("format", 1);
        older.putInt("day_of_year", 101);
        older.putInt("days_per_season", 30);
        older.putLong("last_clock_day", 77L);
        older.putLong("rain_event", 12L);
        older.putBoolean("was_raining", true);
        SeasonData data = read(older);
        assertEquals(101, data.dayOfYear());
        assertEquals(30, data.daysPerSeason());
        assertEquals(77L, data.lastClockDay());
        assertEquals(12L, data.rainEvent());
        assertTrue(data.wasRaining());
        assertTrue(data.thawStartSnow().isEmpty());
        // Saving it again keeps every older field and adds no thaw start snow that isn't known.
        Tag saved = SeasonData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
        SeasonData again = read(saved);
        assertEquals(101, again.dayOfYear());
        assertEquals(30, again.daysPerSeason());
        assertEquals(77L, again.lastClockDay());
        assertEquals(12L, again.rainEvent());
        assertTrue(again.wasRaining());
        assertTrue(again.thawStartSnow().isEmpty());
    }

    @Test
    void thawStartSnowIsSavedAndReadBack() {
        SeasonData data = new SeasonData();
        data.setThawStartSnow(Map.of(Climate.HOT, 0.0, Climate.TEMPERATE, 1.25, Climate.COLD, 3.0));
        SeasonData read = read(SeasonData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow());
        assertEquals(Map.of(Climate.HOT, 0.0, Climate.TEMPERATE, 1.25, Climate.COLD, 3.0), read.thawStartSnow().orElseThrow());
    }

    @Test
    void anIncompleteOrDamagedRecordCountsAsNotSaved() {
        JsonElement partial = JsonParser.parseString("{\"day_of_year\": 5, \"thaw_start_snow\": {\"temperate\": 1.0}}");
        SeasonData data = SeasonData.CODEC.parse(JsonOps.INSTANCE, partial).getOrThrow();
        assertEquals(5, data.dayOfYear());
        assertTrue(data.thawStartSnow().isEmpty());
    }

    /** Late winter with snow, then early spring melting it. */
    private static SnowLog winterIntoSpring() {
        SnowLog log = new SnowLog(0);
        log.observe(0, SubSeason.MID_WINTER, true, EnumSet.allOf(Climate.class), SNOW_HEIGHT);
        log.observe(5_000, SubSeason.LATE_WINTER, false, Set.of(), SNOW_HEIGHT);
        log.observe(20_000, SubSeason.EARLY_SPRING, false, Set.of(), SNOW_HEIGHT);
        log.observe(22_000, SubSeason.EARLY_SPRING, true, EnumSet.of(Climate.TEMPERATE), SNOW_HEIGHT);
        return log;
    }

    @Test
    void theLogFindsWhereEarlySpringBeganAndTheDepthThen() {
        SnowLog log = winterIntoSpring();
        OptionalLong began = log.startOfLatestRun(SnowRules::isThawTime);
        assertEquals(OptionalLong.of(20_000), began);
        // A full layer at the end of winter, melted away a little after early spring began.
        assertEquals(1.0, log.depth(Climate.TEMPERATE, 20_000, SNOW_HEIGHT));
        assertEquals(0.0, log.depth(Climate.TEMPERATE, 30_000, SNOW_HEIGHT));
        assertEquals(1, SnowLog.layers(log.depth(Climate.TEMPERATE, began.getAsLong(), SNOW_HEIGHT), SNOW_HEIGHT));
    }

    @Test
    void theStartIsUnknownWhenTheLogDoesntReachBackToIt() {
        SnowLog springOnly = new SnowLog(0);
        springOnly.observe(0, SubSeason.EARLY_SPRING, false, Set.of(), SNOW_HEIGHT);
        springOnly.observe(4_000, SubSeason.EARLY_SPRING, true, EnumSet.of(Climate.COLD), SNOW_HEIGHT);
        assertTrue(springOnly.startOfLatestRun(SnowRules::isThawTime).isEmpty());
        SnowLog summer = winterIntoSpring();
        summer.observe(40_000, SubSeason.MID_SPRING, false, Set.of(), SNOW_HEIGHT);
        assertTrue(summer.startOfLatestRun(SnowRules::isThawTime).isEmpty());
    }

    @Test
    void depthsInThePastLeaveOutLaterSegments() {
        SnowLog log = winterIntoSpring();
        double before = log.depth(Climate.TEMPERATE, 4_000, SNOW_HEIGHT);
        assertEquals(4_000 * SnowLog.SNOW_PER_TICK, before, 1e-9);
    }
}
