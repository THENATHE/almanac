package com.thenathe.almanac.internal;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;

/**
 * What core hands the weather module once a server tick: the sub-season, the rain shares for it, the share of time
 * every place is under rain (percent), whether regional weather is on, and whether the weather may move
 * ({@code advance_weather}).
 */
public record WeatherTick(SubSeason subSeason, int hotShare, int temperateShare, int coldShare, int rainShare,
                          boolean regional, boolean advancing) {
    public int share(Climate climate) {
        return switch (climate) {
            case HOT -> hotShare;
            case TEMPERATE -> temperateShare;
            case COLD -> coldShare;
        };
    }
}
