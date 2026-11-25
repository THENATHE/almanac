package com.thenathe.almanac.climatemap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.network.FarClimatePayloads;
import io.netty.buffer.Unpooled;
import java.util.Random;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;

class ClimateTileTest {
    /** A tile like a real one: a few biomes in patches. */
    private static int[] patches(int cellShift, int biomes, long seed) {
        int side = ClimateTile.SIZE >> cellShift;
        int[] ids = new int[side * side];
        Random random = new Random(seed);
        int[] choices = new int[biomes];
        for (int index = 0; index < biomes; index++) {
            choices[index] = 3 + random.nextInt(700);
        }
        for (int index = 0; index < ids.length; index++) {
            int x = index % side;
            int z = index / side;
            ids[index] = choices[Math.floorMod(x / 5 + z / 7 * 3 + (random.nextInt(9) == 0 ? 1 : 0), biomes)];
        }
        return ids;
    }

    private static <T> T throughTheNetwork(T payload, net.minecraft.network.codec.StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        codec.encode(buffer, payload);
        T read = codec.decode(buffer);
        assertEquals(0, buffer.readableBytes(), "every byte read");
        return read;
    }

    @Test
    void tilesComeBackFromTheNetworkUnchanged() {
        for (int cellShift = 4; cellShift <= 7; cellShift++) {
            for (int biomes : new int[] {1, 2, 3, 5, 17, 200}) {
                int[] ids = patches(cellShift, Math.min(biomes, (ClimateTile.SIZE >> cellShift) * (ClimateTile.SIZE >> cellShift)), biomes);
                ClimateTile tile = ClimateTile.ofBiomes(-3, 7, cellShift, ids);
                FarClimatePayloads.Tile payload = throughTheNetwork(FarClimatePayloads.Tile.of(tile), FarClimatePayloads.Tile.CODEC);
                ClimateTile read = payload.unpack();
                assertEquals(tile, read);
                int side = ClimateTile.SIZE >> cellShift;
                for (int index = 0; index < ids.length; index++) {
                    int x = tile.minBlockX() + (index % side << cellShift);
                    int z = tile.minBlockZ() + (index / side << cellShift);
                    assertEquals(ids[index], read.biomeAt(x, z));
                }
            }
        }
    }

    @Test
    void oneBiomeIsJustAPalette() {
        ClimateTile tile = ClimateTile.ofBiomes(0, 0, 5, new int[256]);
        FarClimatePayloads.Tile payload = FarClimatePayloads.Tile.of(tile);
        assertEquals(0, payload.packedCells().length);
        assertTrue(payload.sizeInBytes() < 16, "size " + payload.sizeInBytes());
        assertEquals(tile, payload.unpack());
    }

    @Test
    void packingAndCompressionKeepTilesSmall() {
        ClimateTile tile = ClimateTile.ofBiomes(5, 5, 5, patches(5, 4, 1));
        FarClimatePayloads.Tile payload = FarClimatePayloads.Tile.of(tile);
        // 256 cells at 2 bits are 64 bytes before compression.
        assertTrue(payload.packedCells().length < 64, "packed " + payload.packedCells().length);
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        FarClimatePayloads.Tile.CODEC.encode(buffer, payload);
        assertEquals(buffer.readableBytes(), payload.sizeInBytes());
    }

    @Test
    void cellsAreFoundForNegativeCoordinates() {
        assertEquals(-1, ClimateTile.tileOf(-1));
        assertEquals(-1, ClimateTile.tileOf(-512));
        assertEquals(-2, ClimateTile.tileOf(-513));
        int[] ids = new int[256];
        ids[255] = 9;
        ClimateTile tile = ClimateTile.ofBiomes(-1, -1, 5, ids);
        assertEquals(255, tile.cellIndex(-1, -1));
        assertEquals(9, tile.biomeAt(-1, -1));
        assertEquals(0, tile.cellIndex(-512, -512));
        long key = ClimateTile.key(-70000, 123456);
        assertEquals(-70000, ClimateTile.keyX(key));
        assertEquals(123456, ClimateTile.keyZ(key));
    }

    @Test
    void damagedTilesAreRefused() {
        ClimateTile tile = ClimateTile.ofBiomes(0, 0, 5, patches(5, 6, 2));
        byte[] packed = tile.packCells();
        byte[] cut = java.util.Arrays.copyOf(packed, packed.length / 2);
        assertThrows(IllegalArgumentException.class, () -> ClimateTile.unpack(0, 0, 5, tile.palette(), cut));
        assertThrows(IllegalArgumentException.class, () -> ClimateTile.unpack(0, 0, 5, tile.palette(), new byte[] {1, 2, 3}));
        assertThrows(IllegalArgumentException.class, () -> ClimateTile.unpack(0, 0, 12, tile.palette(), packed));
        assertThrows(IllegalArgumentException.class, () -> ClimateTile.unpack(0, 0, 5, new int[0], packed));
        // A palette of 5 needs 3 bits, so a cell can point past it; that is refused too.
        int[] shortPalette = {1, 2, 3, 4, 5};
        int[] cells = new int[256];
        cells[0] = 7;
        assertThrows(IllegalArgumentException.class, () -> new ClimateTile(0, 0, 5, shortPalette, cells));
    }

    @Test
    void theStateAndForgettingComeBackUnchanged() {
        FarClimatePayloads.State state = new FarClimatePayloads.State(true, SubSeason.LATE_WINTER, new int[] {0, 2, 1}, false, 3);
        assertEquals(state, throughTheNetwork(state, FarClimatePayloads.State.CODEC));
        assertEquals(2, state.layers(Climate.TEMPERATE));
        FarClimatePayloads.State deeper = new FarClimatePayloads.State(true, SubSeason.LATE_WINTER, new int[] {0, 3, 1},
                false, 3);
        assertFalse(deeper.sameFarSnow(state));
        assertTrue(deeper.sameFarRule(state));
        FarClimatePayloads.Forget forget = new FarClimatePayloads.Forget(false, new long[] {ClimateTile.key(-4, 9), 17L});
        FarClimatePayloads.Forget read = throughTheNetwork(forget, FarClimatePayloads.Forget.CODEC);
        assertArrayEquals(forget.tiles(), read.tiles());
    }
}
