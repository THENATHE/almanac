package com.thenathe.almanac.api;

import java.util.Locale;
import java.util.Optional;

/** The twelve parts of the Almanac year, in order. Each season has an early, mid and late part. */
public enum SubSeason {
    EARLY_SPRING(Season.SPRING),
    MID_SPRING(Season.SPRING),
    LATE_SPRING(Season.SPRING),
    EARLY_SUMMER(Season.SUMMER),
    MID_SUMMER(Season.SUMMER),
    LATE_SUMMER(Season.SUMMER),
    EARLY_FALL(Season.FALL),
    MID_FALL(Season.FALL),
    LATE_FALL(Season.FALL),
    EARLY_WINTER(Season.WINTER),
    MID_WINTER(Season.WINTER),
    LATE_WINTER(Season.WINTER);

    public static final int COUNT = 12;
    private static final SubSeason[] ORDER = values();

    private final Season season;

    SubSeason(Season season) {
        this.season = season;
    }

    public Season season() {
        return season;
    }

    /** The lower-case name used in commands, settings, palettes and biome copy IDs, such as {@code mid_winter}. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public SubSeason next() {
        return byIndex(ordinal() + 1);
    }

    public SubSeason previous() {
        return byIndex(ordinal() - 1);
    }

    /** Any index, wrapped into the year. */
    public static SubSeason byIndex(int index) {
        return ORDER[Math.floorMod(index, COUNT)];
    }

    public static Optional<SubSeason> byId(String id) {
        for (SubSeason subSeason : ORDER) {
            if (subSeason.id().equals(id)) {
                return Optional.of(subSeason);
            }
        }
        return Optional.empty();
    }

    public enum Season {
        SPRING, SUMMER, FALL, WINTER
    }
}
