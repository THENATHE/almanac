package com.thenathe.almanac.snow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.season.SeasonData;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@code season.dat} and {@code snow.dat} are read field by field: a damaged field costs only itself, never the year
 * or the snow history.
 */
class SavedDataReadingTest {
    private static SeasonData season(String json) {
        return SeasonData.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static SnowData snow(Tag tag) {
        return SnowData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    @Test
    void aWrongTypedSeasonFieldCostsOnlyItself() {
        SeasonData data = season("""
                {"format": 1, "day_of_year": "seventy", "days_per_season": 30, "last_clock_day": 77,
                 "rain_event": [1, 2], "was_raining": true,
                 "thaw_start_snow": {"hot": 0.0, "temperate": 1.5, "cold": 2.0}, "from_the_future": {"x": 1}}
                """);
        assertEquals(0, data.dayOfYear());
        assertEquals(30, data.daysPerSeason());
        assertEquals(77L, data.lastClockDay());
        assertEquals(0L, data.rainEvent());
        assertTrue(data.wasRaining());
        assertEquals(Map.of(Climate.HOT, 0.0, Climate.TEMPERATE, 1.5, Climate.COLD, 2.0), data.thawStartSnow().orElseThrow());
    }

    @Test
    void aSeasonFileOfNothingButDamageStillReads() {
        SeasonData data = season("""
                {"format": "x", "day_of_year": {}, "days_per_season": "long", "last_clock_day": false,
                 "rain_event": "many", "was_raining": "maybe", "thaw_start_snow": 4}
                """);
        assertEquals(0, data.dayOfYear());
        assertEquals(24, data.daysPerSeason());
        assertEquals(SeasonData.NOT_STARTED, data.lastClockDay());
        assertTrue(data.thawStartSnow().isEmpty());
    }

    @Test
    void aDamagedDepthOnlyLosesTheSavedEarlySpringDepths() {
        // One climate unreadable: the set isn't whole, so the depths count as not saved (and are worked out again).
        SeasonData data = season("{\"day_of_year\": 12, \"thaw_start_snow\": {\"hot\": 0.0, \"temperate\": \"deep\", \"cold\": 2.0}}");
        assertEquals(12, data.dayOfYear());
        assertTrue(data.thawStartSnow().isEmpty());
    }

    private static CompoundTag segment(long start, String subSeason, boolean raining, String... reached) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("start", start);
        tag.putString("sub_season", subSeason);
        tag.putBoolean("raining", raining);
        ListTag climates = new ListTag();
        for (String climate : reached) {
            climates.add(StringTag.valueOf(climate));
        }
        tag.put("reached", climates);
        return tag;
    }

    private static CompoundTag snowFile(ListTag segments, Tag depths) {
        CompoundTag log = new CompoundTag();
        log.putLong("checkpoint_tick", 1_000L);
        log.put("checkpoint_depth", depths);
        log.put("segments", segments);
        CompoundTag file = new CompoundTag();
        file.putInt("format", 1);
        file.putLong("started", 1_000L);
        file.put("log", log);
        return file;
    }

    @Test
    void aDamagedSegmentIsLeftOutAndTheRestOfTheLogKept() {
        ListTag segments = new ListTag();
        segments.add(segment(2_000L, "mid_winter", true, "temperate", "cold"));
        // Cut short: no sub-season, so it can't be placed in the log.
        CompoundTag truncated = new CompoundTag();
        truncated.putLong("start", 3_000L);
        segments.add(truncated);
        // A wrong-typed field in a segment costs only that field.
        CompoundTag oddRain = segment(4_000L, "late_winter", false);
        oddRain.putString("raining", "heavily");
        segments.add(oddRain);
        CompoundTag depths = new CompoundTag();
        depths.putDouble("temperate", 0.5);
        depths.putString("cold", "lots");
        SnowLog log = snow(snowFile(segments, depths)).log(10_000L);

        List<SnowLog.Segment> read = log.segments();
        assertEquals(2, read.size());
        assertEquals(new SnowLog.Segment(2_000L, SubSeason.MID_WINTER, true, EnumSet.of(Climate.TEMPERATE, Climate.COLD)),
                read.get(0));
        assertEquals(new SnowLog.Segment(4_000L, SubSeason.LATE_WINTER, false, EnumSet.noneOf(Climate.class)), read.get(1));
        // The readable checkpoint depth is kept: half a layer, plus the snowfall from 2,000 to 4,000.
        assertEquals(0.5 + 2_000 * SnowLog.SNOW_PER_TICK, log.depth(Climate.TEMPERATE, 4_000L, 8), 1e-9);
        assertEquals(2_000 * SnowLog.SNOW_PER_TICK, log.depth(Climate.COLD, 4_000L, 8), 1e-9);
    }

    @Test
    void aLogThatCantBeReadAtAllStartsAfreshWithoutFailingTheFile() {
        CompoundTag file = new CompoundTag();
        file.putLong("started", 1_000L);
        file.putString("log", "gone");
        SnowLog log = snow(file).log(5_000L);
        assertTrue(log.segments().isEmpty());
        assertEquals(0.0, log.depth(Climate.TEMPERATE, 5_000L, 8));
    }

    @Test
    void aLostStartFieldDoesNotThrowTheWeatherAway() {
        ListTag segments = new ListTag();
        segments.add(segment(2_000L, "mid_winter", true, "temperate"));
        CompoundTag file = snowFile(segments, new CompoundTag());
        file.putString("started", "when?");
        SnowLog log = snow(file).log(6_096L);
        assertEquals(1, log.segments().size());
        assertEquals(1.0, log.depth(Climate.TEMPERATE, 6_096L, 8), 1e-9);
    }

    @Test
    void aLogAheadOfTheGameTimeIsMovedBackOnRead() {
        // A snow.dat copied from a world 100,000 ticks further on: the depths would stand still until then.
        ListTag segments = new ListTag();
        segments.add(segment(100_000L, "mid_winter", true, "temperate"));
        segments.add(segment(104_096L, "mid_winter", false));
        CompoundTag file = snowFile(segments, new CompoundTag());
        file.getCompound("log").orElseThrow().putLong("checkpoint_tick", 99_000L);
        SnowData data = snow(file);
        SnowLog log = data.log(5_000L);
        assertEquals(5_000L, log.latestTick());
        assertEquals(List.of(904L, 5_000L), log.segments().stream().map(SnowLog.Segment::start).toList());
        assertEquals(1.0, log.depth(Climate.TEMPERATE, 5_000L, 8), 1e-9);
        assertTrue(data.isDirty());
        // Only the first look moves it; later game times are this world's own.
        assertEquals(5_000L, data.log(9_000L).latestTick());
    }

    @Test
    void aLogInStepWithTheGameTimeIsLeftAlone() {
        ListTag segments = new ListTag();
        segments.add(segment(2_000L, "mid_winter", true, "temperate"));
        SnowData data = snow(snowFile(segments, new CompoundTag()));
        assertEquals(2_000L, data.log(3_000L).latestTick());
        assertFalse(data.isDirty());
    }

    @Test
    void theFarSnowChangeSurvivesARestartButNotAChange() {
        // A world from 0.1.2 has no such fields: its first far state counts as new.
        SeasonData data = season("{\"day_of_year\": 12}");
        assertEquals(5000L, data.farSnowChanged(77, 1077, 5000L));
        // The same state later, or after the file is read again, keeps its tick.
        assertEquals(5000L, data.farSnowChanged(77, 1077, 9000L));
        SeasonData reread = season(SeasonData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().toString());
        assertEquals(5000L, reread.farSnowChanged(77, 1077, 12000L));
        // Something else shown: a new change.
        assertEquals(12500L, reread.farSnowChanged(78, 1078, 12500L));
        // A saved tick ahead of the game time (a season.dat from another world) doesn't count.
        SeasonData copied = season("{\"far_snow_shown\": 5, \"far_snow_by_rule\": true, \"far_snow_changed\": 900000}");
        assertEquals(100L, copied.farSnowChanged(5, 1005, 100L));
    }

    @Test
    void aFarSnowTickSavedBy013IsKept() {
        // 0.1.3 saved its own fingerprint of the state, without far_snow_by_rule.
        SeasonData data = season("{\"far_snow_shown\": 1005, \"far_snow_changed\": 4000}");
        assertEquals(4000L, data.farSnowChanged(5, 1005, 9000L));
        assertTrue(data.isDirty(), "the rule's fingerprint replaces 0.1.3's");
        SeasonData reread = season(SeasonData.CODEC.encodeStart(JsonOps.INSTANCE, data).getOrThrow().toString());
        assertEquals(4000L, reread.farSnowChanged(5, 2005, 12000L));
        // A 0.1.3 save of another state is a change, as it was in 0.1.3.
        SeasonData other = season("{\"far_snow_shown\": 1006, \"far_snow_changed\": 4000}");
        assertEquals(9000L, other.farSnowChanged(5, 1005, 9000L));
    }
}
