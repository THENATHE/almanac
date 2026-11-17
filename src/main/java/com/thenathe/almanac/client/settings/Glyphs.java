package com.thenathe.almanac.client.settings;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Small pixel pictures drawn with fills, so the mod ships no icon textures.
 * Each is a grid of rows where '#' is a lit pixel.
 */
public enum Glyphs {
    UNDO(
            "..#......",
            ".##......",
            "######...",
            ".##...#..",
            "..#....#.",
            ".......#.",
            "......#..",
            "..####..."),
    CHEVRON_RIGHT(
            "#....",
            ".#...",
            "..#..",
            "...#.",
            "..#..",
            ".#...",
            "#...."),
    CHEVRON_DOWN(
            "#.....#",
            ".#...#.",
            "..#.#..",
            "...#..."),
    CHECK(
            "........#",
            ".......##",
            "#.....##.",
            "##...##..",
            ".##.##...",
            "..###....",
            "...#....."),
    CROSS(
            "##...##",
            ".##.##.",
            "..###..",
            ".##.##.",
            "##...##"),
    MAGNIFIER(
            ".###...",
            "#...#..",
            "#...#..",
            "#...#..",
            ".####..",
            "....##.",
            ".....##"),
    SUN(
            "....#....",
            ".#.....#.",
            "...###...",
            "..#####..",
            "#.#####.#",
            "..#####..",
            "...###...",
            ".#.....#.",
            "....#...."),
    MOON(
            "...####..",
            "..###....",
            ".###.....",
            ".###.....",
            ".###.....",
            ".###.....",
            "..###....",
            "...####..");

    private final String[] rows;

    Glyphs(String... rows) {
        this.rows = rows;
    }

    public int width() {
        return rows[0].length();
    }

    public int height() {
        return rows.length;
    }

    public void draw(GuiGraphicsExtractor graphics, int x, int y, int colour) {
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length(); column++) {
                if (rows[row].charAt(column) == '#') {
                    graphics.fill(x + column, y + row, x + column + 1, y + row + 1, colour);
                }
            }
        }
    }

    /** Draws with a vanilla-style drop shadow one pixel down and right. */
    public void drawShadowed(GuiGraphicsExtractor graphics, int x, int y, int colour) {
        int shadow = 0xFF000000 | (colour >> 2 & 0x3F3F3F);
        draw(graphics, x + 1, y + 1, shadow);
        draw(graphics, x, y, colour);
    }

    public void drawCentred(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        draw(graphics, x + (w - width()) / 2, y + (h - height()) / 2, colour);
    }
}
