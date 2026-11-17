package com.thenathe.almanac.client.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;

/** Shared rules for highlight and text shadow. */
public final class Ink {
    private Ink() {
    }

    /**
     * A widget lights up when hovered, or when focused from the keyboard. A mouse click also
     * focuses a widget, but that alone shouldn't leave it highlighted.
     */
    public static boolean hot(AbstractWidget widget) {
        return widget.isHovered() || widget.isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard();
    }

    /** Shortens text to fit a width, ending it with an ellipsis when anything was cut. */
    public static String trim(Font font, String text, int width) {
        if (font.width(text) <= width) {
            return text;
        }
        String ellipsis = "…";
        return font.plainSubstrByWidth(text, Math.max(0, width - font.width(ellipsis))).stripTrailing() + ellipsis;
    }

    /** Light text gets a shadow, dark text doesn't, as in vanilla. */
    public static boolean shadow(int argb) {
        int r = argb >> 16 & 0xFF;
        int g = argb >> 8 & 0xFF;
        int b = argb & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 >= 128;
    }
}
