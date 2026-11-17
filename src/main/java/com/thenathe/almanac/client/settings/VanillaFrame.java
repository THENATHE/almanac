package com.thenathe.almanac.client.settings;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * The vanilla look's pieces, all cut from Minecraft's own sprites and textures so that
 * resource packs restyle them. Only vanilla ids are used here.
 */
public final class VanillaFrame {
    public static final Identifier BUTTON = vanilla("widget/button");
    public static final Identifier BUTTON_HIGHLIGHTED = vanilla("widget/button_highlighted");
    public static final Identifier BUTTON_DISABLED = vanilla("widget/button_disabled");
    public static final Identifier SLIDER = vanilla("widget/slider");
    public static final Identifier SLIDER_HIGHLIGHTED = vanilla("widget/slider_highlighted");
    public static final Identifier SLIDER_HANDLE = vanilla("widget/slider_handle");
    public static final Identifier SLIDER_HANDLE_HIGHLIGHTED = vanilla("widget/slider_handle_highlighted");
    public static final Identifier TEXT_FIELD = vanilla("widget/text_field");
    public static final Identifier TEXT_FIELD_HIGHLIGHTED = vanilla("widget/text_field_highlighted");
    public static final Identifier CROSS_BUTTON = vanilla("widget/cross_button");
    public static final Identifier CROSS_BUTTON_HIGHLIGHTED = vanilla("widget/cross_button_highlighted");
    public static final Identifier POPUP = vanilla("popup/background");
    public static final Identifier SLOT = vanilla("container/slot");
    public static final Identifier SLOT_HIGHLIGHT_BACK = vanilla("container/slot_highlight_back");
    public static final Identifier SLOT_HIGHLIGHT_FRONT = vanilla("container/slot_highlight_front");
    public static final Identifier HOTBAR = vanilla("hud/hotbar");
    public static final Identifier HOTBAR_SELECTION = vanilla("hud/hotbar_selection");
    private static final Identifier SIDE_TAB = vanilla("recipe_book/tab");
    private static final Identifier SIDE_TAB_SELECTED = vanilla("recipe_book/tab_selected");
    private static final Identifier SCROLLER = vanilla("container/creative_inventory/scroller");
    private static final Identifier SCROLLER_DISABLED = vanilla("container/creative_inventory/scroller_disabled");
    private static final Identifier CREATIVE_TEXTURE = vanilla("textures/gui/container/creative_inventory/tab_items.png");

    public static final int SIDE_TAB_W = 35;
    public static final int SIDE_TAB_H = 27;
    public static final int SCROLLBAR_W = 14;
    private static final int HOTBAR_W = 182;
    private static final int HOTBAR_H = 22;

    private VanillaFrame() {
    }

    private static Identifier vanilla(String path) {
        return Identifier.withDefaultNamespace(path);
    }

    public static Identifier button(boolean active, boolean hot) {
        if (!active) {
            return BUTTON_DISABLED;
        }
        return hot ? BUTTON_HIGHLIGHTED : BUTTON;
    }

    public static void sprite(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int w, int h) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, w, h);
    }

    /**
     * The creative inventory's grey panel stretched to any size: the four corners as they are,
     * the edges and the middle tiled from plain stretches of the texture.
     */
    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int w, int h) {
        int right = x + w - 7;
        int bottom = y + h - 7;
        creative(graphics, x, y, 0, 0, 7, 8);
        creative(graphics, right, y, 188, 0, 7, 8);
        creative(graphics, x, bottom, 0, 129, 7, 7);
        creative(graphics, right, bottom, 188, 129, 7, 7);
        for (int tileX = x + 7; tileX < right; tileX += 60) {
            int tileW = Math.min(60, right - tileX);
            creative(graphics, tileX, y, 100, 0, tileW, 8);
            creative(graphics, tileX, bottom, 100, 129, tileW, 7);
            for (int tileY = y + 8; tileY < bottom; tileY += 8) {
                creative(graphics, tileX, tileY, 100, 8, tileW, Math.min(8, bottom - tileY));
            }
        }
        for (int tileY = y + 8; tileY < bottom; tileY += 100) {
            int tileH = Math.min(100, bottom - tileY);
            creative(graphics, x, tileY, 0, 20, 7, tileH);
            creative(graphics, right, tileY, 188, 20, 7, tileH);
        }
    }

    private static void creative(GuiGraphicsExtractor graphics, int x, int y, int u, int v, int w, int h) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, CREATIVE_TEXTURE, x, y, u, v, w, h, 256, 256);
    }

    /**
     * The creative inventory's scrollbar: its sunken track (two-pixel caps, the middle stretched)
     * and the metal scroller. When nothing scrolls the scroller is greyed and stays at the top.
     */
    public static void creativeScrollbar(GuiGraphicsExtractor graphics, int x, int y, int h, float position, boolean scrolls) {
        int trackU = 174;
        int trackV = 17;
        int trackH = 112;
        int cap = 2;
        creative(graphics, x, y, trackU, trackV, SCROLLBAR_W, cap);
        graphics.blit(RenderPipelines.GUI_TEXTURED, CREATIVE_TEXTURE, x, y + cap, trackU, trackV + cap,
                SCROLLBAR_W, h - 2 * cap, SCROLLBAR_W, trackH - 2 * cap, 256, 256);
        creative(graphics, x, y + h - cap, trackU, trackV + trackH - cap, SCROLLBAR_W, cap);
        int thumbY = y + 1 + Math.round((h - 17) * position);
        sprite(graphics, scrolls ? SCROLLER : SCROLLER_DISABLED, x + 1, thumbY, 12, 15);
    }

    /** A recipe-book tab stretched sideways to hold a label; the selected one reaches into the panel. */
    public static void sideTab(GuiGraphicsExtractor graphics, int x, int right, int y, boolean selected) {
        Identifier sprite = selected ? SIDE_TAB_SELECTED : SIDE_TAB;
        int tail = (selected ? SIDE_TAB_W : 30) - 16;
        int end = selected ? right + 3 : right;
        tabSlice(graphics, sprite, 0, x, 16, y);
        for (int middle = x + 16; middle < end - tail; middle++) {
            tabSlice(graphics, sprite, 16, middle, 1, y);
        }
        tabSlice(graphics, sprite, 16, end - tail, tail, y);
    }

    private static void tabSlice(GuiGraphicsExtractor graphics, Identifier sprite, int u, int x, int w, int y) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, SIDE_TAB_W, SIDE_TAB_H, u, 0, x, y, w, SIDE_TAB_H);
    }

    /** The hotbar, cut to hold a given number of 20 px slots. */
    public static void hotbarStrip(GuiGraphicsExtractor graphics, int x, int y, int slots) {
        int body = slots * 20 + 1;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, HOTBAR_W, HOTBAR_H, 0, 0, x, y, body, HOTBAR_H);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, HOTBAR, HOTBAR_W, HOTBAR_H, HOTBAR_W - 2, 0, x + body, y, 2, HOTBAR_H);
    }

    public static int hotbarStripWidth(int slots) {
        return slots * 20 + 3;
    }

    /** The blue "changed" square, drawn with fills. */
    public static void changedMark(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x, y, x + 5, y + 5, Palette.CHANGED_SQUARE_EDGE);
        graphics.fill(x, y, x + 4, y + 4, Palette.CHANGED_SQUARE);
    }
}
