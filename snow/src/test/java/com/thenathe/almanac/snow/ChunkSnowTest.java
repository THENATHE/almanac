package com.thenathe.almanac.snow;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

class ChunkSnowTest {
    /** Ice where the climate is cold in mid winter, as a state saved before 0.3.0 reads. */
    private static final Map<Climate, Double> WINTER_ICE = Map.of(Climate.TEMPERATE, 1.0, Climate.COLD, 1.0);
    private static final SnowState WINTER = winter(1.0, 2.0);

    private static SnowState winter(double temperate, double cold) {
        return new SnowState(SubSeason.MID_WINTER, Map.of(Climate.TEMPERATE, temperate, Climate.COLD, cold), WINTER_ICE,
                2, true, 2);
    }

    private static ChunkSnow sample() {
        ChunkSnow record = new ChunkSnow();
        record.markUpdated(WINTER, EnumSet.of(Climate.TEMPERATE, Climate.COLD), 123_456L);
        record.setModSnowLayers(ChunkSnow.pack(new BlockPos(17, 70, -3)), 2);
        record.setModSnowLayers(ChunkSnow.pack(new BlockPos(31, -60, 0)), 1);
        record.setModIce(ChunkSnow.pack(new BlockPos(16, 62, 15)), true);
        record.setPlayerSnow(ChunkSnow.pack(new BlockPos(20, 319, 4)), true);
        return record;
    }

    private static ChunkSnow viaNbt(ChunkSnow record) {
        Tag tag = ChunkSnow.CODEC.encodeStart(NbtOps.INSTANCE, record).getOrThrow();
        return ChunkSnow.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
    }

    private static void assertSame(ChunkSnow expected, ChunkSnow actual) {
        assertEquals(expected.updated(), actual.updated());
        assertEquals(expected.state(), actual.state());
        assertEquals(expected.climates(), actual.climates());
        int[] positions = expected.modSnowPositions();
        Arrays.sort(positions);
        int[] read = actual.modSnowPositions();
        Arrays.sort(read);
        assertArrayEquals(positions, read);
        for (int key : positions) {
            assertEquals(expected.modSnowLayers(key), actual.modSnowLayers(key));
        }
        assertArrayEquals(sorted(expected.modIcePositions()), sorted(actual.modIcePositions()));
        assertArrayEquals(sorted(expected.playerSnowPositions()), sorted(actual.playerSnowPositions()));
    }

    private static int[] sorted(int[] values) {
        int[] copy = values.clone();
        Arrays.sort(copy);
        return copy;
    }

    @Test
    void positionsPackInsideTheChunkWithSignedHeights() {
        int key = ChunkSnow.pack(new BlockPos(-1, -64, 33));
        assertEquals(15, ChunkSnow.localX(key));
        assertEquals(1, ChunkSnow.localZ(key));
        assertEquals(-64, ChunkSnow.y(key));
        assertEquals(2031, ChunkSnow.y(ChunkSnow.pack(0, 2031, 0)));
    }

    @Test
    void roundTripThroughChunkNbt() {
        ChunkSnow record = sample();
        ChunkSnow read = viaNbt(record);
        assertSame(record, read);
        assertTrue(read.isUpTo(WINTER));
        assertSame(read, viaNbt(read));
    }

    @Test
    void roundTripThroughJson() {
        ChunkSnow record = sample();
        var json = ChunkSnow.CODEC.encodeStart(JsonOps.INSTANCE, record).getOrThrow();
        assertSame(record, ChunkSnow.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }

    @Test
    void anEmptyOrOlderRecordReadsWithDefaults() {
        ChunkSnow empty = ChunkSnow.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow();
        assertEquals(ChunkSnow.NEVER, empty.updated());
        assertNull(empty.state());
        assertFalse(empty.isUpTo(WINTER));
        assertEquals(0, empty.modSnowPositions().length);
    }

    @Test
    void unknownAndDamagedFieldsDoNotLoseTheRest() {
        // A future version's sub-season, an unreadable field and an extra one: the snow records survive.
        var json = JsonParser.parseString("""
                {"format": 7, "updated": 5, "state": {"sub_season": "monsoon"}, "mod_snow": [1792, 2048],
                 "mod_snow_layers": [3], "mod_ice": "broken", "player_snow": [256], "future": {"a": 1}}
                """);
        ChunkSnow read = ChunkSnow.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertNull(read.state());
        assertEquals(5, read.updated());
        assertEquals(3, read.modSnowLayers(1792));
        // A missing layer count is read as the least that can melt.
        assertEquals(1, read.modSnowLayers(2048));
        assertEquals(0, read.modIcePositions().length);
        assertTrue(read.isPlayerSnow(256));
    }

    @Test
    void theClimatesSurviveSaving() {
        assertEquals(EnumSet.of(Climate.TEMPERATE, Climate.COLD), viaNbt(sample()).climates());
    }

    @Test
    void aRecordSavedWithAThawWindowLoadsWholeAndDropsTheWindow() {
        // 0.1.3 to 0.1.5 saved a chunk's own thaw, and 0.1.6 still read it; the rest of the record is as 0.1.7 writes it.
        CompoundTag saved = (CompoundTag) ChunkSnow.CODEC.encodeStart(NbtOps.INSTANCE, sample()).getOrThrow();
        CompoundTag thaw = new CompoundTag();
        thaw.putLong("start", 120_000L);
        thaw.putLong("end", 168_000L);
        saved.put("thaw", thaw);
        ChunkSnow read = ChunkSnow.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow();
        assertSame(sample(), read);
        assertTrue(read.isUpTo(WINTER));
        CompoundTag again = (CompoundTag) ChunkSnow.CODEC.encodeStart(NbtOps.INSTANCE, read).getOrThrow();
        assertFalse(again.contains("thaw"));
        assertTrue(again.contains("player_snow"));
    }

    @Test
    void aRecordFromBefore013ComparesTheWholeState() {
        // As 0.1.2 writes it: no climates.
        var json = JsonParser.parseString("""
                {"format": 1, "updated": 9, "state": {"sub_season": "mid_winter", "layers": {"temperate": 2, "cold": 2},
                 "native_snow_melts": true, "snow_height": 2}, "mod_snow": [1792], "mod_snow_layers": [1]}
                """);
        ChunkSnow read = ChunkSnow.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertNull(read.climates());
        // Two whole layers, as deep as snow may lie: the same shares as two layers' depth now.
        assertTrue(read.isUpTo(winter(2.0, 2.0)));
        // Without the climates known, a change in any climate counts, as it did before.
        assertFalse(read.isUpTo(new SnowState(SubSeason.MID_WINTER, Map.of(Climate.TEMPERATE, 2.0, Climate.COLD, 2.0,
                Climate.HOT, 1.0), WINTER_ICE, 2, true, 2)));
    }

    @Test
    void anUnknownClimateMakesTheClimatesUnknown() {
        var json = JsonParser.parseString("""
                {"state": {"sub_season": "mid_winter"}, "climates": ["temperate", "tundra"],
                 "thaw": {"start": "soon"}}
                """);
        ChunkSnow read = ChunkSnow.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertNull(read.climates());
    }

    @Test
    void aChunkOnlyRechecksForTheClimatesItHolds() {
        ChunkSnow plains = new ChunkSnow();
        plains.markUpdated(WINTER, EnumSet.of(Climate.TEMPERATE), 1L);
        SnowState moreColdSnow = winter(1.0, 1.5);
        SnowState moreTemperateSnow = winter(1.5, 2.0);
        assertTrue(plains.isUpTo(moreColdSnow));
        assertFalse(plains.isUpTo(moreTemperateSnow));
        ChunkSnow bare = new ChunkSnow();
        bare.markUpdated(WINTER, EnumSet.noneOf(Climate.class), 1L);
        assertTrue(bare.isUpTo(moreTemperateSnow));
        assertTrue(viaNbt(bare).isUpTo(moreTemperateSnow));
    }

    @Test
    void aChunkChangedOnItsWayElsewhereIsNoLongerUpToDateWithItsLastState() {
        ChunkSnow record = sample();
        assertTrue(record.isUpTo(WINTER));
        // Part way through a change: its snow already differs from mid winter's, so a later mid winter must redo it.
        record.leftLastState();
        assertFalse(record.isUpTo(WINTER));
        ChunkSnow read = viaNbt(record);
        assertFalse(read.isUpTo(WINTER), "also after saving and loading");
        assertNull(read.state());
        assertEquals(2, read.modSnowLayers(ChunkSnow.pack(new BlockPos(17, 70, -3))), "and so is the season's snow");
        read.markUpdated(WINTER, EnumSet.of(Climate.TEMPERATE), 200_000L);
        assertTrue(read.isUpTo(WINTER));
    }

    @Test
    void forgettingAPositionClearsEveryRecordThere() {
        ChunkSnow record = sample();
        int key = ChunkSnow.pack(new BlockPos(17, 70, -3));
        record.setPlayerSnow(key, true);
        record.forget(key);
        assertEquals(0, record.modSnowLayers(key));
        assertFalse(record.isPlayerSnow(key));
    }

    @Test
    void snowStateRoundTripsByName() {
        var json = SnowState.CODEC.encodeStart(JsonOps.INSTANCE, WINTER).getOrThrow();
        assertEquals(WINTER, SnowState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        assertTrue(json.toString().contains("mid_winter"), json.toString());
        assertEquals(0, WINTER.layers(Climate.HOT));
        assertEquals(2, WINTER.naturalHeight());
        assertTrue(json.toString().contains("\"depth\""), json.toString());
    }

    @Test
    void aStateSavedWithWholeLayersReadsAsTheAmountsItStoodFor() {
        // As 0.2.0 wrote it: whole layers, no amounts; the snow height game rule was how deep snow could lie.
        var json = JsonParser.parseString("""
                {"sub_season": "mid_winter", "layers": {"hot": 0, "temperate": 1, "cold": 2}, "native_snow_melts": true,
                 "snow_height": 2, "climate_settings": -1593805681, "no_seasonal_snow": 12345}
                """);
        SnowState read = SnowState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(1.0, read.depth(Climate.TEMPERATE));
        assertEquals(2.0, read.depth(Climate.COLD));
        assertEquals(1.0, read.shares(Climate.COLD).cover(), "whole layers up to the game rule: the cover was full");
        assertEquals(1.0, read.shares(Climate.COLD).share(2));
        assertEquals(1.0, read.ice(Climate.TEMPERATE), "water was frozen wherever it was cold");
        assertEquals(0.0, read.ice(Climate.HOT));
        assertEquals(2, read.maxLayers());
        assertEquals(12345, read.noSeasonalSnow());
    }

    @Test
    void wholeLayersUnderARaisedGameRuleClaimNoPartOfTheLayerAbove() {
        // 0.2.0 with the snow height game rule at 5 and four whole layers lying: no column had a fifth.
        var json = JsonParser.parseString("""
                {"sub_season": "mid_winter", "layers": {"temperate": 4}, "snow_height": 5}
                """);
        SnowState read = SnowState.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertEquals(1.0, read.share(Climate.TEMPERATE, 4));
        assertEquals(0.0, read.share(Climate.TEMPERATE, 5));
        // Saved again by this version, it is still whole layers.
        var again = SnowState.CODEC.encodeStart(JsonOps.INSTANCE, read).getOrThrow();
        SnowState reread = SnowState.CODEC.parse(JsonOps.INSTANCE, again).getOrThrow();
        assertEquals(read, reread);
        assertEquals(0.0, reread.share(Climate.TEMPERATE, 5));
        // A chunk brought to it is behind a log at 4.1 layers on the fifth layer's tenth, and on nothing else.
        SnowState now = new SnowState(SubSeason.MID_WINTER, Map.of(Climate.TEMPERATE, 4.1), Map.of(Climate.TEMPERATE, read.ice(Climate.TEMPERATE)), 5, true, 5);
        assertEquals(1.0, now.share(Climate.TEMPERATE, 4), 1e-12);
        assertEquals(0.1, now.share(Climate.TEMPERATE, 5), 1e-12);
    }
}
