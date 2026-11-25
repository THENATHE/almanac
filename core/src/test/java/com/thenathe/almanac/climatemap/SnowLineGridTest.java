package com.thenathe.almanac.climatemap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.thenathe.almanac.api.AlmanacApi;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

class SnowLineGridTest {
    /** Tiles at (0, 0) and (-1, 0) only; a column's line encodes where it is, so misplaced answers show. */
    private static final class Fake implements SnowLineGrid.Source<Long> {
        final Map<Long, Long> tiles = new HashMap<>();
        int lookups;

        Fake() {
            tiles.put(ClimateTile.key(0, 0), ClimateTile.key(0, 0));
            tiles.put(ClimateTile.key(-1, 0), ClimateTile.key(-1, 0));
        }

        @Override
        public @Nullable Long tile(long key) {
            lookups++;
            return tiles.get(key);
        }

        @Override
        public int line(Long tile, int x, int z) {
            return x * 1000 + z;
        }

        int single(int x, int z) {
            Long tile = tiles.get(ClimateTile.key(ClimateTile.tileOf(x), ClimateTile.tileOf(z)));
            return tile == null ? AlmanacApi.FAR_SNOW_UNKNOWN : line(tile, x, z);
        }
    }

    @Test
    void fillsRowByRowLikeSingleLookups() {
        Fake fake = new Fake();
        int width = 40;
        int height = 30;
        int[] lines = new int[width * height + 5];
        Arrays.fill(lines, 42);
        int known = SnowLineGrid.fill(fake, -300, 400, 16, width, height, lines);
        int expectedKnown = 0;
        for (int row = 0; row < height; row++) {
            for (int column = 0; column < width; column++) {
                int expected = fake.single(-300 + column * 16, 400 + row * 16);
                assertEquals(expected, lines[row * width + column], "column " + column + ", row " + row);
                if (expected != AlmanacApi.FAR_SNOW_UNKNOWN) {
                    expectedKnown++;
                }
            }
        }
        assertEquals(expectedKnown, known);
        // Rows from z = 400 to 864 cross into the unknown tile row z >= 512; columns run from -300 to 324.
        assertEquals(42, lines[width * height], "nothing written past the grid");
        // One lookup per run of columns in a tile, not one per column.
        assertEquals(height * 2, fake.lookups);
    }

    @Test
    void anEmptyGridFillsNothing() {
        assertEquals(0, SnowLineGrid.fill(new Fake(), 0, 0, 1, 0, 10, new int[0]));
    }

    @Test
    void theApiChecksItsArgumentsAndKnowsNothingWithoutAClient() {
        int[] lines = new int[12];
        assertThrows(IllegalArgumentException.class, () -> AlmanacApi.fillFarSnowLines(null, 0, 0, 0, 3, 4, lines));
        assertThrows(IllegalArgumentException.class, () -> AlmanacApi.fillFarSnowLines(null, 0, 0, 16, 4, 4, lines));
        assertEquals(0, AlmanacApi.fillFarSnowLines(null, 0, 0, 16, 3, 4, lines));
        for (int line : lines) {
            assertEquals(AlmanacApi.FAR_SNOW_UNKNOWN, line);
        }
        assertEquals(AlmanacApi.FAR_SNOW_UNKNOWN, AlmanacApi.farSnowLine(null, 5, 5));
    }
}
