package com.thenathe.almanac.client.colour;

import com.thenathe.almanac.colour.SeasonBlend;
import com.thenathe.almanac.colour.SeasonalPalette;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import org.jspecify.annotations.Nullable;

/**
 * The colours in use for one day: each known biome's grass, foliage and dry-foliage colour, worked out once, and
 * each seasonal leaf block's steps. A table never changes after it is made, so the chunk-building threads can read
 * it while the client thread swaps in the next one.
 */
public final class ColourTable {
    public static final int GRASS = 0;
    public static final int FOLIAGE = 1;
    public static final int DRY_FOLIAGE = 2;

    static final ColourTable EMPTY = new ColourTable(null, Map.of(), Map.of());

    private final @Nullable SeasonBlend blend;
    private final Map<Biome, int[]> biomes;
    private final Map<Block, SeasonalPalette.LeafSteps> leaves;

    ColourTable(@Nullable SeasonBlend blend, Map<Biome, int[]> biomes, Map<Block, SeasonalPalette.LeafSteps> leaves) {
        this.blend = blend;
        this.biomes = new IdentityHashMap<>(biomes);
        this.leaves = new IdentityHashMap<>(leaves);
    }

    /** The day's blend, or null while no Almanac server has said what day it is. */
    public @Nullable SeasonBlend blend() {
        return blend;
    }

    public boolean isEmpty() {
        return blend == null;
    }

    /** Grass, foliage and dry foliage (opaque ARGB), or null for a biome without seasons. */
    public int @Nullable [] colours(Biome biome) {
        return biomes.get(biome);
    }

    /** A leaf block's colour on this day, from the colour its own tint gives it. */
    public int leaf(Block block, int tint) {
        SeasonalPalette.LeafSteps steps = leaves.get(block);
        if (steps == null || blend == null) {
            return tint;
        }
        return tint & 0xFF000000 | blend.apply(tint & 0xFFFFFF, steps::step);
    }

    /**
     * The biggest change in any colour channel between this table and another, in steps of 1/255. Leaves are
     * compared on a mid grey, which shows both multiplied and mixed steps.
     */
    int largestDifference(ColourTable other) {
        if (biomes.size() != other.biomes.size() || !leaves.keySet().equals(other.leaves.keySet())) {
            return 255;
        }
        int largest = 0;
        for (Map.Entry<Biome, int[]> entry : biomes.entrySet()) {
            int[] theirs = other.biomes.get(entry.getKey());
            if (theirs == null) {
                return 255;
            }
            for (int channel = 0; channel < 3; channel++) {
                largest = Math.max(largest, SeasonBlend.difference(entry.getValue()[channel], theirs[channel]));
            }
        }
        for (Block block : leaves.keySet()) {
            largest = Math.max(largest, SeasonBlend.difference(leaf(block, 0xFF808080), other.leaf(block, 0xFF808080)));
        }
        return largest;
    }
}
