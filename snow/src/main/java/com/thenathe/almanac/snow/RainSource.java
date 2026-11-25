package com.thenathe.almanac.snow;

import com.thenathe.almanac.internal.WeatherView;
import com.thenathe.almanac.internal.WetSpell;

/**
 * How long snow fell on an area through a stretch of one segment of the weather log: the one thing the snow takes
 * from the weather. The log's arithmetic (how deep that makes the snow) is the same whatever the source.
 *
 * <ul>
 *   <li>{@link #GLOBAL}: vanilla's one rain for the whole overworld, reaching the climates the rain shares picked for
 *   the event. Every segment logged before regional weather, and every segment while it is off.</li>
 *   <li>{@link #regional}: from 0.4.0, for segments logged with regional weather on, the time a storm core reaching
 *   the area's climate was over the area's node, from the weather module. Older segments stay global.</li>
 * </ul>
 */
@FunctionalInterface
public interface RainSource {
    /**
     * How long it snowed through game ticks {@code [from, to)} of a segment.
     *
     * @param ticks       the ticks of the stretch with snow falling (where it is cold enough)
     * @param fallingAtEnd whether it is still falling at {@code to}
     * @param stableUntil  the game tick until which that stays so, as far as the source can tell
     */
    record Spell(long ticks, boolean fallingAtEnd, long stableUntil) {
        static final Spell NONE = new Spell(0, false, Long.MAX_VALUE);
    }

    /** Vanilla's rain, logged with the climates the event reaches. */
    RainSource GLOBAL = (segment, area, from, to) -> segment.raining() && segment.reached().contains(area.climate())
            ? new Spell(Math.max(0, to - from), true, Long.MAX_VALUE) : Spell.NONE;

    Spell spell(SnowLog.Segment segment, SnowArea area, long from, long to);

    /**
     * The storms of a weather view for the nodes in segments with regional weather, and {@link #GLOBAL} for the rest:
     * every segment before the switch-over, and a climate as a whole, which no storm falls on.
     */
    static RainSource regional(WeatherView weather) {
        return regional(weather::wetSpell);
    }

    /** {@link #regional(WeatherView)} from any source of wet spells, such as one that remembers what it was asked. */
    static RainSource regional(WetSpells weather) {
        return (segment, area, from, to) -> {
            if (segment.weatherVersion() == 0 || !area.node()) {
                return GLOBAL.spell(segment, area, from, to);
            }
            WetSpell wet = weather.wetSpell(area.x(), area.z(), from, to);
            return new Spell(wet.ticks(area.climate()), wet.wetAtEnd(area.climate()), wet.stableUntil());
        };
    }

    /** How long rain or snow fell on a column through a stretch, per climate ({@link WeatherView#wetSpell}). */
    @FunctionalInterface
    interface WetSpells {
        WetSpell wetSpell(double x, double z, long from, long to);
    }
}
