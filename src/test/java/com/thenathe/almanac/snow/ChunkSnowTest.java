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
    private static final SnowState WINTER = new SnowState(SubSeason.MID_WINTER,
            Map.of(Climate.TEMPERATE, 1, Climate.COLD, 2), true, 2);

    private static ChunkSnow sample() {
        ChunkSnow record = new ChunkSnow();
        record.markUpdated(WINTER, EnumSet.of(Climate.TEMPERATE, Climate.COLD), 123_456L);
        record.setThaw(ThawPace.Window.startingAt(120_000L, 48_000L));
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
        assertEquals(expected.thaw(), actual.thaw());
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
    void theThawWindowAndClimatesSurviveSaving() {
        // A chunk saved mid-thaw (a restart, or Save and Quit) carries on with the same window.
        ChunkSnow read = viaNbt(sample());
        assertEquals(new ThawPace.Window(120_000L, 168_000L), read.thaw());
        assertEquals(EnumSet.of(Climate.TEMPERATE, Climate.COLD), read.climates());
        read.setThaw(null);
        assertNull(viaNbt(read).thaw());
    }

    @Test
    void aRecordFromBefore013HasNoThawAndComparesTheWholeState() {
        // As 0.1.2 writes it: no climates, no thaw.
        var json = JsonParser.parseString("""
                {"format": 1, "updated": 9, "state": {"sub_season": "mid_winter", "layers": {"temperate": 1, "cold": 2},
                 "native_snow_melts": true, "snow_height": 2}, "mod_snow": [1792], "mod_snow_layers": [1]}
                """);
        ChunkSnow read = ChunkSnow.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertNull(read.thaw());
        assertNull(read.climates());
        assertTrue(read.isUpTo(WINTER));
        // Without the climates known, a change in any climate counts, as it did before.
        assertFalse(read.isUpTo(new SnowState(SubSeason.MID_WINTER, Map.of(Climate.TEMPERATE, 1, Climate.COLD, 2,
                Climate.HOT, 1), true, 2)));
    }

    @Test
    void anUnknownClimateMakesTheClimatesUnknown() {
        var json = JsonParser.parseString("""
                {"state": {"sub_season": "mid_winter"}, "climates": ["temperate", "tundra"],
                 "thaw": {"start": "soon"}}
                """);
        ChunkSnow read = ChunkSnow.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
        assertNull(read.climates());
        assertNull(read.thaw());
    }

    @Test
    void aChunkOnlyRechecksForTheClimatesItHolds() {
        ChunkSnow plains = new ChunkSnow();
        plains.markUpdated(WINTER, EnumSet.of(Climate.TEMPERATE), 1L);
        SnowState moreColdSnow = new SnowState(SubSeason.MID_WINTER, Map.of(Climate.TEMPERATE, 1, Climate.COLD, 3), true, 2);
        SnowState moreTemperateSnow = new SnowState(SubSeason.MID_WINTER, Map.of(Climate.TEMPERATE, 2, Climate.COLD, 2), true, 2);
        assertTrue(plains.isUpTo(moreColdSnow));
        assertFalse(plains.isUpTo(moreTemperateSnow));
        ChunkSnow bare = new ChunkSnow();
        bare.markUpdated(WINTER, EnumSet.noneOf(Climate.class), 1L);
        assertTrue(bare.isUpTo(moreTemperateSnow));
        assertTrue(viaNbt(bare).isUpTo(moreTemperateSnow));
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
    }
}
