package com.thenathe.almanac.api;

import java.util.Locale;

/**
 * The three climates. HOT means nothing falls, TEMPERATE means rain and COLD means snow. Every biome has a base
 * climate; winter moves it one step colder and summer one step warmer.
 */
public enum Climate {
    HOT, TEMPERATE, COLD;

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Climate colder() {
        return this == HOT ? TEMPERATE : COLD;
    }

    public Climate warmer() {
        return this == COLD ? TEMPERATE : HOT;
    }
}
