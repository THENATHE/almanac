package com.thenathe.almanac.api;

import java.util.Arrays;

/**
 * How a climate's snow lies, layer by layer (since 0.3.0): the share of the open ground each layer covers, from the
 * first up to the deepest snow may build. Loaded chunks, chunks that load and far terrain all show these shares.
 *
 * <p>Everything follows from one number, the depth: the snow on the ground in layers, averaged over open ground. It
 * builds by one layer per snowfall clock and melts by one per thaw clock, so it is the same however and whenever it
 * is asked for. A layer has the ground to itself until it is {@value #NEXT_LAYER_AT} down; then the next layer starts,
 * and the two share the snowfall until the lower one is whole. The thaw takes the same steps backwards: the top layer
 * goes first, and the one under it starts to go once the top is down to a tenth. So the shares never jump, and a
 * layer's share is never more than the share of the layer under it.
 *
 * <p>Every column of land has a point between 0 and 1; a column holds as many layers as there are layers whose share
 * is past its point ({@link #layersAt}). Almanac's points are its own, from the world seed; a mod drawing snow of its
 * own can scatter it with points of its own and the shares will hold.
 *
 * @since 0.3.0
 */
public final class SnowLayers {
    /** How far a layer is down when the next one starts, and how far the top one is gone when the next one under it does. */
    public static final double NEXT_LAYER_AT = 0.9;
    /** The deepest snow can ever be, in layers: a full block of snow layers. */
    public static final int MOST_LAYERS = 8;
    private static final double OVERLAP = 1.0 - NEXT_LAYER_AT;

    private final double depth;
    private final int maxLayers;
    private final double[] shares;

    private SnowLayers(double depth, int maxLayers) {
        this.maxLayers = Math.clamp(maxLayers, 1, MOST_LAYERS);
        this.depth = Double.isFinite(depth) ? Math.clamp(depth, 0.0, this.maxLayers) : 0.0;
        this.shares = new double[this.maxLayers];
        for (int layer = 1; layer <= this.maxLayers; layer++) {
            shares[layer - 1] = shareOf(this.depth, layer, this.maxLayers);
        }
    }

    /**
     * The layers of snow {@code depth} layers deep where snow may be at most {@code maxLayers} deep (1 to
     * {@value #MOST_LAYERS}). The depth is held between 0 and the most layers.
     */
    public static SnowLayers of(double depth, int maxLayers) {
        return new SnowLayers(depth, maxLayers);
    }

    /** No snow, with snow allowed to build {@code maxLayers} deep. */
    public static SnowLayers none(int maxLayers) {
        return new SnowLayers(0.0, maxLayers);
    }

    /** The snow on the ground, in layers, averaged over open ground: the sum of the layers' shares. */
    public double depth() {
        return depth;
    }

    /** How deep the snow may build, in layers. */
    public int maxLayers() {
        return maxLayers;
    }

    /**
     * The share of open ground a layer covers, 0 to 1, counting from 1 for the layer on the ground. 0 for layers above
     * {@link #maxLayers} and below 1.
     */
    public double share(int layer) {
        return layer >= 1 && layer <= maxLayers ? shares[layer - 1] : 0.0;
    }

    /** The first layer's share: how much of the open ground is white. */
    public double cover() {
        return shares[0];
    }

    /** How many layers have begun: those with any share at all. */
    public int started() {
        int started = 0;
        while (started < maxLayers && shares[started] > 0.0) {
            started++;
        }
        return started;
    }

    /**
     * How many layers lie on a column whose point is {@code point} (0 to 1): one for each layer whose share is past
     * it. Shares fall layer by layer, so a column with a second layer always has a first.
     */
    public int layersAt(double point) {
        int layers = 0;
        while (layers < maxLayers && point < shares[layers]) {
            layers++;
        }
        return layers;
    }

    /**
     * The share of one layer of snow {@code depth} layers deep, by the rule in the class description. Below 1 and
     * above {@code maxLayers} it is 0.
     */
    static double shareOf(double depth, int layer, int maxLayers) {
        if (layer < 1 || layer > maxLayers) {
            return 0.0;
        }
        double below = layer - 1;
        // The layer starts when the one under it is NEXT_LAYER_AT down, and shares the snowfall with it until that
        // one is whole: half the snow each, for 2 * OVERLAP of depth.
        double starts = layer == 1 ? 0.0 : below - OVERLAP;
        double alone = layer == 1 ? 0.0 : below + OVERLAP;
        if (depth <= starts) {
            return 0.0;
        }
        if (depth < alone) {
            return (depth - starts) / 2.0;
        }
        // The top layer never shares: there is nothing above it to start.
        double sharesWithNext = layer == maxLayers ? Double.MAX_VALUE : layer - OVERLAP;
        if (depth < sharesWithNext) {
            return Math.min(1.0, depth - below);
        }
        return Math.min(1.0, NEXT_LAYER_AT + (depth - sharesWithNext) / 2.0);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SnowLayers layers && layers.maxLayers == maxLayers
                && Double.compare(layers.depth, depth) == 0;
    }

    @Override
    public int hashCode() {
        return 31 * Double.hashCode(depth) + maxLayers;
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();
        for (int layer = 1; layer <= maxLayers; layer++) {
            text.append(layer == 1 ? "" : " ").append(Math.round(share(layer) * 1000) / 10.0).append('%');
        }
        return text.toString();
    }

    /** The shares of every layer, for tests and debug output. */
    double[] shares() {
        return Arrays.copyOf(shares, shares.length);
    }
}
