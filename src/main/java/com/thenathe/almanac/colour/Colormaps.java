package com.thenathe.almanac.colour;

/**
 * The colour a biome without a colour override gets from vanilla's grass, foliage and dry-foliage colormaps.
 *
 * <p>The colormaps are client textures, so a dedicated server doesn't have them. These are smooth fits of the three
 * 26.3 colormaps (within 3 of 255 on every channel), good enough to start a seasonal colour from. Coordinates are
 * vanilla's: {@code x = 1 - temperature} and {@code y = 1 - downfall * temperature}, both clamped to [0, 1].
 */
public final class Colormaps {
    // Per channel: constant, y, y², x, x·y, x².
    private static final double[][] GRASS = {
            {70.846, 119.541, 0.183, 35.053, -97.600, 0.070},
            {205.034, -21.653, 0.156, -19.943, 17.068, -0.051},
            {50.555, 34.446, 0.108, 93.633, -28.311, 0.133}};
    private static final double[][] FOLIAGE = {
            {25.695, 149.346, -0.487, 42.450, -121.501, 0.652},
            {191.412, -27.011, -0.051, -25.709, 22.410, 0.387},
            {-0.229, 42.891, -0.020, 115.848, -34.781, 0.191}};
    private static final double[][] DRY_FOLIAGE = {
            {161.164, 3.348, -1.360, 18.454, -17.386, -22.317},
            {92.473, 45.030, -7.887, 4.676, -35.045, 21.760},
            {71.835, -3.347, 1.360, -18.441, 17.377, 22.302}};

    private Colormaps() {
    }

    public static int grass(float temperature, float downfall) {
        return sample(GRASS, temperature, downfall);
    }

    public static int foliage(float temperature, float downfall) {
        return sample(FOLIAGE, temperature, downfall);
    }

    public static int dryFoliage(float temperature, float downfall) {
        return sample(DRY_FOLIAGE, temperature, downfall);
    }

    public static int sample(ColourChannel channel, float temperature, float downfall) {
        return switch (channel) {
            case GRASS -> grass(temperature, downfall);
            case FOLIAGE -> foliage(temperature, downfall);
            case DRY_FOLIAGE -> dryFoliage(temperature, downfall);
        };
    }

    private static int sample(double[][] fit, float temperature, float downfall) {
        double clampedTemperature = Math.clamp(temperature, 0.0, 1.0);
        double clampedDownfall = Math.clamp(downfall, 0.0, 1.0);
        double x = 1.0 - clampedTemperature;
        double y = 1.0 - clampedDownfall * clampedTemperature;
        int rgb = 0;
        for (double[] channel : fit) {
            double value = channel[0] + channel[1] * y + channel[2] * y * y + channel[3] * x + channel[4] * x * y
                    + channel[5] * x * x;
            rgb = rgb << 8 | (int) Math.clamp(Math.round(value), 0, 255);
        }
        return rgb;
    }
}
