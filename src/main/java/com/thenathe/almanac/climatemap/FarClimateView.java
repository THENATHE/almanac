package com.thenathe.almanac.climatemap;

import com.thenathe.almanac.api.AlmanacApi;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.world.level.Level;

/**
 * The answers behind {@link AlmanacApi}'s far-terrain lookups. Only a client with Almanac has the climate map; until
 * its entrypoint sets one, and on the server, nothing is known.
 */
public interface FarClimateView {
    FarClimateView NOTHING_KNOWN = new FarClimateView() {
        @Override
        public int snowLine(Level level, int x, int z) {
            return AlmanacApi.FAR_SNOW_UNKNOWN;
        }

        @Override
        public int fillSnowLines(Level level, int originX, int originZ, int spacing, int width, int height, int[] lines) {
            Arrays.fill(lines, 0, width * height, AlmanacApi.FAR_SNOW_UNKNOWN);
            return 0;
        }

        @Override
        public OptionalInt grassColour(Level level, int x, int z) {
            return OptionalInt.empty();
        }

        @Override
        public OptionalInt foliageColour(Level level, int x, int z) {
            return OptionalInt.empty();
        }

        @Override
        public Optional<AlmanacApi.FarClimate> climate(Level level, int x, int z) {
            return Optional.empty();
        }
    };

    int snowLine(Level level, int x, int z);

    /** Fills {@code lines} row by row (x fastest); returns how many are known. Arguments are already checked. */
    int fillSnowLines(Level level, int originX, int originZ, int spacing, int width, int height, int[] lines);

    OptionalInt grassColour(Level level, int x, int z);

    OptionalInt foliageColour(Level level, int x, int z);

    Optional<AlmanacApi.FarClimate> climate(Level level, int x, int z);
}
