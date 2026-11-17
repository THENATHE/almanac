package com.thenathe.almanac.client.settings;

import java.util.List;

/**
 * The house look's colours for one mode and accent. Every surface is a flat grey; the
 * accent is the only colour, and the lighter and darker accent tones are worked out from it.
 */
public record HouseColors(
        boolean light, Accent accentChoice,
        int face, int faceHigh, int faceLow, int faceShadow,
        int well, int wellEdge, int group, int groupEdge,
        int text, int textDim, int textOff, int textStrong,
        int rule, int warn, boolean textShadow) {

    public static final int OUTLINE = 0xFF000000;
    public static final int DANGER = 0xFF8C3A3A;

    // Surfaces a value can sit on in light mode; value text must read on all of them.
    private static final List<Integer> LIGHT_SURFACES = List.of(0xFFD0D0D0, 0xFFC2C2C2, 0xFFB4B4B4);

    public static HouseColors of(boolean light, Accent accent) {
        if (light) {
            return new HouseColors(true, accent,
                    0xFFD0D0D0, 0xFFF2F2F2, 0xFFBEBEBE, 0xFF8C8C8C,
                    0xFFB4B4B4, 0xFF959595, 0xFFC2C2C2, 0xFFA6A6A6,
                    0xFF242424, 0xFF505050, 0xFF8A8A8A, 0xFF000000,
                    0xFFB0B0B0, 0xFF5C3C00, false);
        }
        return new HouseColors(false, accent,
                0xFF292929, 0xFF404040, 0xFF383838, 0xFF121212,
                0xFF1A1A1A, 0xFF121212, 0xFF202020, 0xFF151515,
                0xFFE8E8E8, 0xFFA8A8A8, 0xFF6E6E6E, 0xFFFFFFFF,
                0xFF2C2C2C, 0xFFE6C07F, true);
    }

    public int accent() {
        return accentChoice.argb();
    }

    public int accentHot() {
        return Palette.shade(accent(), 0.12F);
    }

    public int sliderFill() {
        return Palette.shade(accent(), light ? 0.55F : -0.2F);
    }

    public int changedMark() {
        return light ? 0xFF9A6A10 : 0xFFE6C07F;
    }

    /** Value text in the accent: lifted toward white on dark, darkened until it is readable on light. */
    public int valueText() {
        if (!light) {
            return Palette.shade(accent(), 0.45F);
        }
        int colour = Palette.shade(accent(), -0.3F);
        for (int step = 0; step < 25 && !readableOnLight(colour); step++) {
            colour = Palette.shade(colour, -0.04F);
        }
        return colour;
    }

    private static boolean readableOnLight(int colour) {
        return LIGHT_SURFACES.stream().allMatch(surface -> Palette.contrast(colour, surface) >= 4.5);
    }
}
