package com.thenathe.almanac.client.settings;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.function.IntConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The list a dropdown or the Color button opens. The screen owns one and gives it every click,
 * scroll and key first while it is open. It opens under its button, or above it when there is
 * more room there, and scrolls when it is taller than the room it has.
 */
public final class PopupList {
    private static final int ROW_H = 16;
    private static final int INSET = 5;
    private static final int CHECK_COLUMN = 12;
    private static final int SWATCH = 10;

    /** One row. A swatch of -1 means the row has no colour square. */
    public record Option(Component label, int swatch) {
        public static Option of(Component label) {
            return new Option(label, -1);
        }
    }

    private Look look;
    private AbstractWidget anchor;
    private List<Option> options = List.of();
    private int chosen;
    private IntConsumer onPick;
    private int x;
    private int y;
    private int w;
    private int h;
    private int scroll;
    private int keyboardRow = -1;

    public void setLook(Look look) {
        this.look = look;
    }

    public boolean isOpen() {
        return anchor != null;
    }

    public boolean isOpenFor(AbstractWidget widget) {
        return anchor == widget;
    }

    public void close() {
        anchor = null;
    }

    public void open(AbstractWidget anchor, List<Option> options, int chosen, IntConsumer onPick) {
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        int screenW = minecraft.getWindow().getGuiScaledWidth();
        int screenH = minecraft.getWindow().getGuiScaledHeight();
        this.anchor = anchor;
        this.options = options;
        this.chosen = chosen;
        this.onPick = onPick;
        this.keyboardRow = -1;

        int widest = 0;
        for (Option option : options) {
            widest = Math.max(widest, CHECK_COLUMN + font.width(option.label()) + (option.swatch() >= 0 ? SWATCH + 4 : 0));
        }
        w = Math.min(screenW - 8, Math.max(anchor.getWidth(), widest + 18));
        x = Math.clamp(anchor.getX(), 4, screenW - 4 - w);

        int below = screenH - (anchor.getY() + anchor.getHeight()) - 4;
        int above = anchor.getY() - 4;
        int contentH = options.size() * ROW_H;
        int room = Math.max(below, above) - 2 * INSET;
        h = Math.max(ROW_H, Math.min(contentH, room)) + 2 * INSET;
        boolean opensUp = below < h && above > below;
        y = opensUp ? anchor.getY() - h : anchor.getY() + anchor.getHeight();

        // Open scrolled so the current choice is in the middle.
        int visible = h - 2 * INSET;
        scroll = Math.clamp(chosen * ROW_H - (visible - ROW_H) / 2, 0, maxScroll());
    }

    private int maxScroll() {
        return Math.max(0, options.size() * ROW_H - (h - 2 * INSET));
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + w || mouseY < y + INSET || mouseY >= y + h - INSET) {
            return -1;
        }
        int row = (int) ((mouseY - y - INSET + scroll) / ROW_H);
        return row < options.size() ? row : -1;
    }

    public void extract(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (!isOpen()) {
            return;
        }
        graphics.nextStratum();
        Font font = Minecraft.getInstance().font;
        if (look.isHouse()) {
            Bevels.panel(graphics, look.house(), x, y, w, h);
        } else {
            VanillaFrame.sprite(graphics, VanillaFrame.POPUP, x, y, w, h);
        }
        int hovered = rowAt(mouseX, mouseY);
        int lit = hovered >= 0 ? hovered : keyboardRow;
        graphics.enableScissor(x + 2, y + INSET, x + w - 2, y + h - INSET);
        for (int i = 0; i < options.size(); i++) {
            int rowY = y + INSET + i * ROW_H - scroll;
            if (i > 0) {
                graphics.fill(x + 2, rowY, x + w - 2, rowY + 1, look.isHouse() ? 0x30FFFFFF : 0x28FFFFFF);
            }
            if (i == lit) {
                graphics.fill(x + 2, rowY + 1, x + w - 2, rowY + ROW_H, look.isHouse() ? look.house().accent() : 0x30FFFFFF);
            }
            drawRow(graphics, font, options.get(i), i == chosen, i == lit, rowY);
        }
        graphics.disableScissor();
    }

    private void drawRow(GuiGraphicsExtractor graphics, Font font, Option option, boolean isChosen, boolean lit, int rowY) {
        int colour;
        if (look.isHouse()) {
            colour = isChosen || lit ? 0xFFFFFFFF : look.house().text();
        } else {
            colour = isChosen ? Palette.POPUP_CHOSEN : Palette.BUTTON_TEXT;
        }
        int textX = x + INSET + 4;
        int textY = rowY + (ROW_H - 8) / 2 + 1;
        if (isChosen) {
            Glyphs.CHECK.draw(graphics, textX - 2, textY, colour);
        }
        textX += CHECK_COLUMN;
        if (option.swatch() >= 0) {
            graphics.fill(textX, textY - 1, textX + SWATCH, textY - 1 + SWATCH, HouseColors.OUTLINE);
            graphics.fill(textX + 1, textY, textX + SWATCH - 1, textY + SWATCH - 2, 0xFF000000 | option.swatch());
            textX += SWATCH + 4;
        }
        graphics.text(font, option.label(), textX, textY, colour, Ink.shadow(colour));
    }

    /** Returns true when the popup used the click; a click outside closes it and is used too. */
    public boolean mouseClicked(MouseButtonEvent event) {
        if (!isOpen()) {
            return false;
        }
        int row = rowAt(event.x(), event.y());
        if (row >= 0) {
            pick(row);
        } else {
            close();
        }
        return true;
    }

    private void pick(int row) {
        IntConsumer callback = onPick;
        close();
        callback.accept(row);
    }

    public boolean mouseScrolled(double vertical) {
        if (!isOpen()) {
            return false;
        }
        scroll = Math.clamp(scroll - (int) Math.round(vertical * ROW_H), 0, maxScroll());
        return true;
    }

    /** Up, Down, Enter and Escape work in the list; other keys are held back while it is open. */
    public boolean keyPressed(KeyEvent event) {
        if (!isOpen()) {
            return false;
        }
        switch (event.key()) {
            case InputConstants.KEY_ESCAPE -> close();
            case InputConstants.KEY_DOWN -> moveKeyboardRow(1);
            case InputConstants.KEY_UP -> moveKeyboardRow(-1);
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER, InputConstants.KEY_SPACE -> pick(keyboardRow >= 0 ? keyboardRow : chosen);
            default -> {
            }
        }
        return true;
    }

    private void moveKeyboardRow(int step) {
        int from = keyboardRow >= 0 ? keyboardRow : chosen;
        keyboardRow = Math.floorMod(from + step, options.size());
        int top = keyboardRow * ROW_H;
        int visible = h - 2 * INSET;
        if (top < scroll) {
            scroll = top;
        } else if (top + ROW_H > scroll + visible) {
            scroll = top + ROW_H - visible;
        }
    }
}
