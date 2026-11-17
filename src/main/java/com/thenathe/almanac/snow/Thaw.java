package com.thenathe.almanac.snow;

import com.thenathe.almanac.api.SubSeason;
import net.minecraft.core.BlockPos;

/**
 * Whether snow and ice that should go may go now at a spot. Adding snow and freezing water never wait for it.
 */
@FunctionalInterface
interface Thaw {
    /** Chunks brought up to date on load, outside thaw time: everything that should go goes at once. */
    Thaw AT_ONCE = pos -> true;
    /** Chunks brought up to date on load in thaw time: they keep their snow and ice and thaw like loaded chunks. */
    Thaw HELD = pos -> false;

    boolean reached(BlockPos pos);

    /** How a chunk brought up to date on load in this sub-season thaws. */
    static Thaw onLoad(SubSeason subSeason) {
        return SnowRules.isThawTime(subSeason) ? HELD : AT_ONCE;
    }
}
