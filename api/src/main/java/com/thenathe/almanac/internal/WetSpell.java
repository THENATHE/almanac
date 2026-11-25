package com.thenathe.almanac.internal;

import com.thenathe.almanac.api.Climate;

/**
 * How long a column was under rain or snow through a stretch of game ticks, for each climate: the time a storm core
 * reaching that climate was over it, with operators' {@code /weather} counted in. The snow builds by it.
 *
 * @param ticks       per climate (in {@link Climate} order), the ticks of the stretch with something falling
 * @param wetAtEnd    per climate, whether something is falling at the end of the stretch
 * @param stableUntil the first game tick after the end of the stretch at which what falls may differ from the end's:
 *                    the first whole tick at or after a storm core's edge or an operator's weather crossing the column,
 *                    or later; every tick before it is as wet as the end
 */
public record WetSpell(long[] ticks, boolean[] wetAtEnd, long stableUntil) {
    public long ticks(Climate climate) {
        return ticks[climate.ordinal()];
    }

    public boolean wetAtEnd(Climate climate) {
        return wetAtEnd[climate.ordinal()];
    }
}
