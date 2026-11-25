package com.thenathe.almanac.client.settings;

import net.minecraft.network.chat.Component;

/**
 * The two looks. HOUSE is a flat, bevelled panel style with an accent colour and a light or dark
 * mode; VANILLA is built from Minecraft's own sprites so resource packs restyle it.
 */
public enum SettingsStyle {
    VANILLA("almanac.style.vanilla"),
    HOUSE("almanac.style.house");

    private final String translationKey;

    SettingsStyle(String translationKey) {
        this.translationKey = translationKey;
    }

    public Component displayName() {
        return Component.translatable(translationKey);
    }

    public SettingsStyle next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
