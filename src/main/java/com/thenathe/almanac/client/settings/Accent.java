package com.thenathe.almanac.client.settings;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** The eight accent colours the house look offers. Muted, so white text stays readable on them. */
public enum Accent {
    BLUE(0xFF3F5F8C),
    CYAN(0xFF2F7F7A),
    GREEN(0xFF4A7F35),
    PURPLE(0xFF6B4A9A),
    MAGENTA(0xFF9A4A6E),
    RED(0xFF9A4A3A),
    ORANGE(0xFF9A7A2A),
    GRAY(0xFF5A5A5A);

    private final int argb;

    Accent(int argb) {
        this.argb = argb;
    }

    public int argb() {
        return argb;
    }

    public Component displayName() {
        return Component.translatable("almanac.accent." + name().toLowerCase(Locale.ROOT));
    }
}
