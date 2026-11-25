package com.thenathe.almanac.client.settings;

/** Fixed colours for the vanilla look, plus small colour maths shared by both looks. */
public final class Palette {
    // Dark ink for the light grey creative-inventory panel.
    public static final int PANEL_TEXT = 0xFF404040;
    public static final int PANEL_TEXT_OFF = 0xFF8E8E8E;
    public static final int PANEL_DIM = 0xFF707070;
    public static final int PANEL_VALUE = 0xFF244474;
    public static final int PANEL_WARN = 0xFF5C3C00;
    public static final int PANEL_RULE = 0x30000000;

    // Light ink for vanilla buttons and popups.
    public static final int BUTTON_TEXT = 0xFFFFFFFF;
    public static final int BUTTON_TEXT_OFF = 0xFFA0A0A0;
    public static final int POPUP_CHOSEN = 0xFFFFFF55;

    public static final int CHANGED_SQUARE = 0xFF3C8CFF;
    public static final int CHANGED_SQUARE_EDGE = 0xFF1A3F73;
    public static final int TITLE = 0xFFFFFFFF;
    public static final int TITLE_NOTE = 0xFF8A9199;
    public static final int GOOD = 0xFF7FD17F;

    private Palette() {
    }

    /** Moves each channel a fraction of the way toward white (positive) or black (negative). */
    public static int shade(int argb, float amount) {
        int target = amount > 0 ? 255 : 0;
        float k = Math.abs(amount);
        int red = Math.round(channel(argb, 16) + (target - channel(argb, 16)) * k);
        int green = Math.round(channel(argb, 8) + (target - channel(argb, 8)) * k);
        int blue = Math.round(channel(argb, 0) + (target - channel(argb, 0)) * k);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    /** WCAG contrast ratio between two opaque colours. */
    public static double contrast(int first, int second) {
        double a = luminance(first);
        double b = luminance(second);
        return (Math.max(a, b) + 0.05) / (Math.min(a, b) + 0.05);
    }

    private static double luminance(int argb) {
        return 0.2126 * linear(channel(argb, 16)) + 0.7152 * linear(channel(argb, 8)) + 0.0722 * linear(channel(argb, 0));
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static int channel(int argb, int shift) {
        return argb >> shift & 0xFF;
    }
}
