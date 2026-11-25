package com.thenathe.almanac.api;

/**
 * How fast the season's snow and ice come and go (since 0.3.0): the clocks in force now. Each clock is
 * the time for one layer of snow, or a pond's whole ice, in game ticks: three layers take three snowfall clocks.
 *
 * @param snowfallTicks how long one layer of snow takes to build while it snows (12,000 by default)
 * @param thawTicks     how long one layer takes to melt, and the ice too, while it is warm (18,000 by default)
 * @param iceTicks      how long still water takes to freeze over while it is cold, shore first (the snowfall clock by
 *                      default; this is the clock in force, never the setting's 0)
 * @param maxLayers     how deep the season's snow may build, in layers: the setting (3 by default), or more in a world
 *                      an older Almanac let build deeper by the snow height game rule
 * @since 0.3.0
 */
public record SnowClocks(int snowfallTicks, int thawTicks, int iceTicks, int maxLayers) {
    public SnowClocks {
        snowfallTicks = Math.max(1, snowfallTicks);
        thawTicks = Math.max(1, thawTicks);
        iceTicks = Math.max(1, iceTicks);
        maxLayers = Math.clamp(maxLayers, 1, SnowLayers.MOST_LAYERS);
    }
}
