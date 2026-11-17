package com.thenathe.almanac.climate;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;

/**
 * What falls in a climate during one sub-season, when a rain event reaches it.
 *
 * <p>Winter moves each climate one step colder and summer one step warmer; spring and fall keep the base climate.
 * The early and late parts of summer only shift part of the way: TEMPERATE keeps its rain but fewer events reach it,
 * and COLD gets a snow line, rain low and snow high.
 */
public enum WeatherKind {
    /** Nothing falls. */
    DRY,
    RAIN,
    SNOW,
    /** Rain below the snow line and snow above it. */
    SNOW_LINE;

    public static WeatherKind of(Climate base, SubSeason subSeason) {
        boolean partOfTheWay = subSeason != SubSeason.MID_SUMMER;
        return switch (subSeason.season()) {
            case SPRING, FALL -> of(base);
            case WINTER -> of(base.colder());
            case SUMMER -> switch (base) {
                case COLD -> partOfTheWay ? SNOW_LINE : RAIN;
                case TEMPERATE -> partOfTheWay ? RAIN : DRY;
                case HOT -> DRY;
            };
        };
    }

    private static WeatherKind of(Climate climate) {
        return switch (climate) {
            case HOT -> DRY;
            case TEMPERATE -> RAIN;
            case COLD -> SNOW;
        };
    }
}
