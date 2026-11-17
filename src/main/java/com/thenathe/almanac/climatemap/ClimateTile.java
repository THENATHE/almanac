package com.thenathe.almanac.climatemap;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * One square of the climate map, {@link #SIZE} blocks on a side and aligned to multiples of it: the surface biome of
 * each cell, as an index into a palette of biome network IDs. Cells are {@code 1 << cellShift} blocks on a side and
 * listed row by row (x fastest), each describing the column at its centre.
 *
 * <p>On the network the cell indices are packed into as few bits as the palette needs and compressed with deflate,
 * so a tile of one biome is just its palette.
 */
public record ClimateTile(int tileX, int tileZ, int cellShift, int[] palette, int[] cells) {
    public static final int SIZE_SHIFT = 9;
    public static final int SIZE = 1 << SIZE_SHIFT;
    /** Cells from 4 to 512 blocks. The settings offer 16 to 128. */
    public static final int MIN_CELL_SHIFT = 2;
    public static final int MAX_CELL_SHIFT = SIZE_SHIFT;
    /** More biomes than any tile can hold cells for at the smallest cell size. */
    public static final int MAX_PALETTE = (SIZE >> MIN_CELL_SHIFT) * (SIZE >> MIN_CELL_SHIFT);

    public ClimateTile {
        if (cellShift < MIN_CELL_SHIFT || cellShift > MAX_CELL_SHIFT) {
            throw new IllegalArgumentException("cell size out of range: " + cellShift);
        }
        if (palette.length == 0 || palette.length > MAX_PALETTE) {
            throw new IllegalArgumentException("palette size out of range: " + palette.length);
        }
        int side = SIZE >> cellShift;
        if (cells.length != side * side) {
            throw new IllegalArgumentException("expected " + side * side + " cells, got " + cells.length);
        }
        for (int cell : cells) {
            if (cell < 0 || cell >= palette.length) {
                throw new IllegalArgumentException("cell outside the palette: " + cell);
            }
        }
    }

    /** Builds a tile from one biome ID per cell, making the palette in order of first appearance. */
    public static ClimateTile ofBiomes(int tileX, int tileZ, int cellShift, int[] biomeIds) {
        int[] palette = new int[Math.min(biomeIds.length, MAX_PALETTE)];
        int paletteSize = 0;
        int[] cells = new int[biomeIds.length];
        for (int index = 0; index < biomeIds.length; index++) {
            int found = indexOf(palette, paletteSize, biomeIds[index]);
            if (found < 0) {
                found = paletteSize;
                palette[paletteSize++] = biomeIds[index];
            }
            cells[index] = found;
        }
        return new ClimateTile(tileX, tileZ, cellShift, Arrays.copyOf(palette, paletteSize), cells);
    }

    private static int indexOf(int[] palette, int size, int id) {
        for (int index = 0; index < size; index++) {
            if (palette[index] == id) {
                return index;
            }
        }
        return -1;
    }

    /** A tile's place in a map of tiles. */
    public static long key(int tileX, int tileZ) {
        return (long) tileX & 0xFFFFFFFFL | (long) tileZ << 32;
    }

    public static int keyX(long key) {
        return (int) key;
    }

    public static int keyZ(long key) {
        return (int) (key >> 32);
    }

    /** The tile holding a block coordinate. */
    public static int tileOf(int block) {
        return block >> SIZE_SHIFT;
    }

    public long key() {
        return key(tileX, tileZ);
    }

    public int cellsPerSide() {
        return SIZE >> cellShift;
    }

    public int minBlockX() {
        return tileX << SIZE_SHIFT;
    }

    public int minBlockZ() {
        return tileZ << SIZE_SHIFT;
    }

    /** The biome ID of the cell holding a block in this tile. */
    public int biomeAt(int blockX, int blockZ) {
        return palette[cells[cellIndex(blockX, blockZ)]];
    }

    /** The index into {@link #cells} of the cell holding a block in this tile. */
    public int cellIndex(int blockX, int blockZ) {
        int cellX = (blockX & SIZE - 1) >> cellShift;
        int cellZ = (blockZ & SIZE - 1) >> cellShift;
        return cellZ * cellsPerSide() + cellX;
    }

    /** Bits per packed cell: none for one biome, otherwise enough for the palette. */
    static int bitsPerCell(int paletteSize) {
        return paletteSize <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(paletteSize - 1);
    }

    /** The cells packed into {@link #bitsPerCell} bits each, lowest bit first, then deflated. */
    public byte[] packCells() {
        int bits = bitsPerCell(palette.length);
        if (bits == 0) {
            return new byte[0];
        }
        byte[] packed = new byte[(cells.length * bits + 7) / 8];
        int bit = 0;
        for (int cell : cells) {
            for (int part = 0; part < bits; part++, bit++) {
                if ((cell >> part & 1) != 0) {
                    packed[bit >> 3] |= (byte) (1 << (bit & 7));
                }
            }
        }
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setInput(packed);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(packed.length / 2 + 16);
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                out.write(buffer, 0, deflater.deflate(buffer));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    /**
     * The reverse of {@link #packCells}, checking everything: the bytes come from the network.
     *
     * @throws IllegalArgumentException if they don't make a tile
     */
    public static ClimateTile unpack(int tileX, int tileZ, int cellShift, int[] palette, byte[] packedCells) {
        if (cellShift < MIN_CELL_SHIFT || cellShift > MAX_CELL_SHIFT) {
            throw new IllegalArgumentException("cell size out of range: " + cellShift);
        }
        int side = SIZE >> cellShift;
        int[] cells = new int[side * side];
        int bits = bitsPerCell(palette.length);
        if (bits > 0) {
            byte[] packed = new byte[(cells.length * bits + 7) / 8];
            Inflater inflater = new Inflater(true);
            try {
                inflater.setInput(packedCells);
                int read = 0;
                while (read < packed.length && !inflater.finished()) {
                    int count = inflater.inflate(packed, read, packed.length - read);
                    if (count == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                        break;
                    }
                    read += count;
                }
                if (read != packed.length) {
                    throw new IllegalArgumentException("packed cells too short: " + read + " of " + packed.length + " bytes");
                }
            } catch (DataFormatException damaged) {
                throw new IllegalArgumentException("packed cells damaged", damaged);
            } finally {
                inflater.end();
            }
            int bit = 0;
            for (int index = 0; index < cells.length; index++) {
                int cell = 0;
                for (int part = 0; part < bits; part++, bit++) {
                    cell |= (packed[bit >> 3] >> (bit & 7) & 1) << part;
                }
                cells[index] = cell;
            }
        }
        return new ClimateTile(tileX, tileZ, cellShift, palette, cells);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ClimateTile tile && tileX == tile.tileX && tileZ == tile.tileZ
                && cellShift == tile.cellShift && Arrays.equals(palette, tile.palette) && Arrays.equals(cells, tile.cells);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * (31 * tileX + tileZ) + cellShift) + Arrays.hashCode(cells);
    }

    @Override
    public String toString() {
        return "ClimateTile[" + tileX + ", " + tileZ + ", cells of " + (1 << cellShift) + ", " + palette.length + " biomes]";
    }
}
