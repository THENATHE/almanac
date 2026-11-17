package com.thenathe.almanac.climatemap;

import com.thenathe.almanac.api.AlmanacApi;
import org.jspecify.annotations.Nullable;

/**
 * Fills a grid of far snow lines ({@link AlmanacApi#fillFarSnowLines}): column {@code (i, j)} is at
 * {@code originX + i * spacing, originZ + j * spacing} and goes to {@code lines[j * width + i]}. Each tile is looked
 * up once per run of columns that fall in it, rather than once a column.
 */
public final class SnowLineGrid {
    private SnowLineGrid() {
    }

    /** Where the lines come from: tiles by {@link ClimateTile#key}, and a column's line within its tile. */
    public interface Source<T> {
        @Nullable T tile(long key);

        int line(T tile, int x, int z);
    }

    /** @return how many lines are known */
    public static <T> int fill(Source<T> source, int originX, int originZ, int spacing, int width, int height, int[] lines) {
        int known = 0;
        long lastKey = 0;
        T tile = null;
        boolean looked = false;
        for (int row = 0; row < height; row++) {
            int z = originZ + row * spacing;
            for (int column = 0; column < width; column++) {
                int x = originX + column * spacing;
                long key = ClimateTile.key(ClimateTile.tileOf(x), ClimateTile.tileOf(z));
                if (!looked || key != lastKey) {
                    tile = source.tile(key);
                    lastKey = key;
                    looked = true;
                }
                int line = tile == null ? AlmanacApi.FAR_SNOW_UNKNOWN : source.line(tile, x, z);
                if (line != AlmanacApi.FAR_SNOW_UNKNOWN) {
                    known++;
                }
                lines[row * width + column] = line;
            }
        }
        return known;
    }
}
