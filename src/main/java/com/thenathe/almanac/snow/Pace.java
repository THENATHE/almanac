package com.thenathe.almanac.snow;

import com.thenathe.almanac.api.SubSeason;
import net.minecraft.core.BlockPos;

/**
 * Whether a change to the snow and ice at a spot may happen yet: snow and ice going, or snow forming. Loaded chunks
 * pace both in patches ({@link ThawPace}); freezing water never waits.
 */
interface Pace {
    /** Chunks brought up to date on load, outside thaw time: everything changes at once. */
    Pace AT_ONCE = new Pace() {
        @Override
        public boolean mayMelt(BlockPos pos) {
            return true;
        }

        @Override
        public boolean mayForm(BlockPos pos) {
            return true;
        }
    };
    /**
     * Chunks brought up to date on load in thaw time: they gain snow and ice at once, but keep what they should lose
     * and thaw like loaded chunks.
     */
    Pace HELD = new Pace() {
        @Override
        public boolean mayMelt(BlockPos pos) {
            return false;
        }

        @Override
        public boolean mayForm(BlockPos pos) {
            return true;
        }
    };

    /** Whether snow or ice that should go may go now. */
    boolean mayMelt(BlockPos pos);

    /** Whether snow that should come may come now. */
    boolean mayForm(BlockPos pos);

    /** How a chunk brought up to date on load in this sub-season changes. */
    static Pace onLoad(SubSeason subSeason) {
        return SnowRules.isThawTime(subSeason) ? HELD : AT_ONCE;
    }
}
