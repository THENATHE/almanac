package com.thenathe.almanac.client.settings;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The house look's boxes. Each is a black outline around a flat fill, with one-pixel lines
 * inside the outline that catch the light from the top left.
 */
public final class Bevels {
    private Bevels() {
    }

    /** A raised surface in the mode's face colour. */
    public static void raised(GuiGraphicsExtractor graphics, HouseColors colors, int x, int y, int w, int h) {
        raisedWith(graphics, x, y, w, h, colors.face(), colors.faceHigh(), colors.faceLow(), colors.faceShadow());
    }

    /** A raised surface in any colour, such as the accent; its edge tones come from that colour. */
    public static void raised(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int face) {
        raisedWith(graphics, x, y, w, h, face, Palette.shade(face, 0.32F), Palette.shade(face, 0.16F), Palette.shade(face, -0.55F));
    }

    private static void raisedWith(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int face, int lit, int shaded, int shadow) {
        outlineAndFill(graphics, x, y, w, h, face);
        int innerRight = x + w - 1;
        int innerBottom = y + h - 1;
        graphics.fill(x + 1, y + 1, innerRight, y + 2, lit);
        graphics.fill(x + 1, y + 1, x + 2, innerBottom - 1, lit);
        if (w >= 4) {
            graphics.fill(innerRight - 1, y + 2, innerRight, innerBottom - 1, shaded);
        }
        if (h >= 6) {
            graphics.fill(x + 2, innerBottom - 2, innerRight, innerBottom - 1, shaded);
            graphics.fill(x + 1, innerBottom - 1, innerRight, innerBottom, shadow);
        }
    }

    /** A well pressed into the surface: fields, tracks and disabled buttons. */
    public static void sunken(GuiGraphicsExtractor graphics, HouseColors colors, int x, int y, int w, int h) {
        sunken(graphics, colors, x, y, w, h, HouseColors.OUTLINE);
    }

    public static void sunken(GuiGraphicsExtractor graphics, HouseColors colors, int x, int y, int w, int h, int edge) {
        graphics.fill(x, y, x + w, y + h, edge);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, colors.well());
        litCorner(graphics, x, y, w, h, colors.wellEdge());
    }

    public static void panel(GuiGraphicsExtractor graphics, HouseColors colors, int x, int y, int w, int h) {
        outlineAndFill(graphics, x, y, w, h, colors.face());
        litCorner(graphics, x, y, w, h, colors.faceHigh());
    }

    public static void group(GuiGraphicsExtractor graphics, HouseColors colors, int x, int y, int w, int h) {
        outlineAndFill(graphics, x, y, w, h, colors.group());
        litCorner(graphics, x, y, w, h, colors.groupEdge());
    }

    private static void outlineAndFill(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int fill) {
        graphics.fill(x, y, x + w, y + h, HouseColors.OUTLINE);
        graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    private static void litCorner(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x + 1, y + 1, x + w - 1, y + 2, colour);
        graphics.fill(x + 1, y + 1, x + 2, y + h - 1, colour);
    }

    /** A one-pixel ring, for focus and chosen marks. */
    public static void ring(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y + 1, x + 1, y + h - 1, colour);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }
}
