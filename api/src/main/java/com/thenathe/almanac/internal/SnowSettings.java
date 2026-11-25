package com.thenathe.almanac.internal;

/**
 * The server settings the snow module reads, as they are when asked.
 *
 * @param catchUp              chunks that load are brought up to date at once ({@code snowCatchUp})
 * @param catchUpChunksPerTick how many of them a tick ({@code catchUpChunksPerTick})
 * @param columnsPerTick       the loaded chunks' budget ({@code snowColumnsPerTick})
 * @param nativeSnowMelts      native snow in COLD biomes melts in summer ({@code nativeSnowMeltsInSummer})
 * @param snowfallTicks        the snowfall clock: ticks for one layer to build ({@code snowfallTicks}, from 0.3.0)
 * @param thawTicks            the thaw clock: ticks for one layer, or the ice, to melt ({@code thawTicks})
 * @param iceTicks             the ice clock: ticks for still water to freeze over ({@code iceTicks}; the snowfall
 *                             clock when that is 0)
 * @param maxLayers            how deep the season's snow may build ({@code maxSnowLayers})
 */
public record SnowSettings(boolean catchUp, int catchUpChunksPerTick, int columnsPerTick, boolean nativeSnowMelts,
                           int snowfallTicks, int thawTicks, int iceTicks, int maxLayers) {
}
