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
        record.markUpdated(WINTER, 123_456L);
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
