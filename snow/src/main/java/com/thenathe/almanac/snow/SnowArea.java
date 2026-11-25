package com.thenathe.almanac.snow;

import com.thenathe.almanac.api.Climate;
import java.util.List;

/**
 * Where one set of snow amounts holds: the key every depth is worked out for. Under vanilla's one weather a climate's
 * snow is the same everywhere, so an area is just a climate ({@link #of}). Under regional weather (from 0.4.0) snow
 * builds only where storms went, so an area is also a place: a snow node, one every {@link #NODE_SPACING} blocks
 * ({@link #node}). A node's depth is worked out from the weather log and the storms that crossed it; a chunk's is
 * the four nodes around it, weighed by how near each is ({@code SnowField}), so where a storm's edge went the snow
 * thins out over a few hundred blocks rather than stopping at a line.
 *
 * <p>Nothing is saved by area: amounts always come from the log. A chunk record keeps the amounts its chunk was
 * brought to under the climate's ID ({@link #id}), the same as up to 0.3.0, so the records of a world from before
 * regional weather read as they were.
 *
 * @param node whether this is a snow node, at block {@code (x, z)}; otherwise the climate as a whole
 */
public record SnowArea(Climate climate, boolean node, int x, int z) {
    /** Blocks between snow nodes. */
    public static final int NODE_SPACING = 256;

    private static final List<SnowArea> ALL = List.of(of(Climate.HOT), of(Climate.TEMPERATE), of(Climate.COLD));

    /** Every climate as a whole, in climate order. */
    public static List<SnowArea> all() {
        return ALL;
    }

    /** A climate as a whole: its snow where vanilla's one weather decides it. */
    public static SnowArea of(Climate climate) {
        return new SnowArea(climate, false, 0, 0);
    }

    /** The snow node {@code (nodeX, nodeZ)} on the node grid, at block {@code (nodeX * 256, nodeZ * 256)}. */
    public static SnowArea node(Climate climate, int nodeX, int nodeZ) {
        return new SnowArea(climate, true, nodeX * NODE_SPACING, nodeZ * NODE_SPACING);
    }

    /** The name a chunk record saves this area's amounts under: the climate's ID. */
    public String id() {
        return climate.id();
    }

    /** A node's place on the node grid as one number, for maps of nodes. */
    static long key(int nodeX, int nodeZ) {
        return ((long) nodeX << 32) ^ (nodeZ & 0xFFFF_FFFFL);
    }

    static int keyX(long key) {
        return (int) (key >> 32);
    }

    static int keyZ(long key) {
        return (int) key;
    }
}
