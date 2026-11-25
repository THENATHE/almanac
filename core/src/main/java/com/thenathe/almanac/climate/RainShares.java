package com.thenathe.almanac.climate;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.WeatherKind;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The default share of rain events that reach each climate in each sub-season. Dry cells have no share: nothing
 * falls there however many events there are.
 *
 * <p>From 0.1.4 every cell that has a share defaults to 100 (user, 2026-11-19: "I don't like the % chance for weather
 * to not do anything"): when the game's weather turns, it rains or snows wherever the season lets anything fall. The
 * shares are still settings. {@link #before014} keeps the first table (user, 2026-11-17), so a settings file written
 * with it can be told from one a player changed.
 */
public final class RainShares {
    private static final Map<Climate, Map<SubSeason, Integer>> DEFAULTS = new EnumMap<>(Climate.class);
    private static final Map<Climate, Map<SubSeason, Integer>> BEFORE_014 = new EnumMap<>(Climate.class);

    static {
        DEFAULTS.put(Climate.TEMPERATE, row(100, 100, 100, 100, 0, 100, 100, 100, 100, 100, 100, 100));
        DEFAULTS.put(Climate.HOT, row(0, 0, 0, 0, 0, 0, 0, 0, 0, 100, 100, 100));
        DEFAULTS.put(Climate.COLD, row(100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100, 100));
        BEFORE_014.put(Climate.TEMPERATE, row(80, 100, 100, 60, 0, 60, 100, 100, 80, 60, 50, 60));
        BEFORE_014.put(Climate.HOT, row(0, 0, 0, 0, 0, 0, 0, 0, 0, 50, 100, 50));
        BEFORE_014.put(Climate.COLD, row(100, 100, 100, 50, 100, 50, 100, 100, 100, 100, 100, 100));
    }

    private RainShares() {
    }

    public static boolean hasShare(Climate climate, SubSeason subSeason) {
        return WeatherKind.of(climate, subSeason) != WeatherKind.DRY;
    }

    public static int defaultShare(Climate climate, SubSeason subSeason) {
        return hasShare(climate, subSeason) ? DEFAULTS.get(climate).get(subSeason) : 0;
    }

    /** The share a cell had by default before 0.1.4. */
    public static int before014(Climate climate, SubSeason subSeason) {
        return hasShare(climate, subSeason) ? BEFORE_014.get(climate).get(subSeason) : 0;
    }

    /** The defaults for one climate, keyed by sub-season ID, without its dry cells. */
    public static Map<String, Integer> defaultRow(Climate climate) {
        Map<String, Integer> row = new LinkedHashMap<>();
        for (SubSeason subSeason : SubSeason.values()) {
            if (hasShare(climate, subSeason)) {
                row.put(subSeason.id(), defaultShare(climate, subSeason));
            }
        }
        return row;
    }

    private static Map<SubSeason, Integer> row(int... shares) {
        Map<SubSeason, Integer> row = new EnumMap<>(SubSeason.class);
        for (SubSeason subSeason : SubSeason.values()) {
            row.put(subSeason, shares[subSeason.ordinal()]);
        }
        return row;
    }
}
