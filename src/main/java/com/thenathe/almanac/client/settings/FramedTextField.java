package com.thenathe.almanac.client.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * A text field drawn in either look. Vanilla's own frame is turned off and replaced: the
 * vanilla text-field sprite, or a house well whose edge shows focus. A search field gets a
 * magnifier glyph in front of the text.
 */
public class FramedTextField extends EditBox {
    private static final int PAD = 5;
    private static final int SEARCH_PAD = 17;
    private static final int CLEAR_ROOM = 18;
    private static final int CLEAR_SIZE = 12;

    private final Look look;
    private final boolean search;
    private int frameX;
    private int frameY;
    private int frameW;
    private int frameH;

    public FramedTextField(Look look, int x, int y, int w, int h, Component hint, boolean search) {
        super(Minecraft.getInstance().font, 0, 0, 0, 0, hint);
        this.look = look;
        this.search = search;
        setBordered(false);
        setMaxLength(256);
        setHint(hint.copy().withColor(look.isHouse() ? look.house().textDim() : 0xFF8A8A8A));
        setTextColor(look.isHouse() ? look.house().text() : 0xFFFFFFFF);
        setFrame(x, y, w, h);
    }

    /** Positions the frame; the editable text area sits inside it. */
    public void setFrame(int x, int y, int w, int h) {
        frameX = x;
        frameY = y;
        frameW = w;
        frameH = h;
        int padLeft = search ? SEARCH_PAD : PAD;
        setX(x + padLeft);
        setY(y + (h - 8) / 2);
        setWidth(w - padLeft - (search ? CLEAR_ROOM : PAD));
        setHeight(10);
    }

    public int frameY() {
        return frameY;
    }

    public int frameWidth() {
        return frameW;
    }

    public int frameHeight() {
        return frameH;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return visible && mouseX >= frameX && mouseX < frameX + frameW && mouseY >= frameY && mouseY < frameY + frameH;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean focused = isFocused();
        if (look.isHouse()) {
            HouseColors house = look.house();
            int edge = focused ? (house.light() ? house.accent() : 0xFFFFFFFF)
                    : isMouseOver(mouseX, mouseY) ? Palette.shade(house.accent(), 0.2F) : HouseColors.OUTLINE;
            Bevels.sunken(graphics, house, frameX, frameY, frameW, frameH, edge);
        } else {
            VanillaFrame.sprite(graphics, focused ? VanillaFrame.TEXT_FIELD_HIGHLIGHTED : VanillaFrame.TEXT_FIELD,
                    frameX, frameY, frameW, frameH);
        }
        if (search) {
            int colour = look.isHouse() ? (focused ? look.house().text() : look.house().textDim()) : 0xFFA0A0A0;
            Glyphs.MAGNIFIER.draw(graphics, frameX + 5, frameY + (frameH - Glyphs.MAGNIFIER.height()) / 2, colour);
        }
        super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
        if (showsClear()) {
            drawClearButton(graphics, mouseX, mouseY);
        }
    }

    /** A search with text in it gets a small cross at the right end that empties it. */
    private boolean showsClear() {
        return search && !getValue().isEmpty();
    }

    private int clearX() {
        return frameX + frameW - 15;
    }

    private int clearY() {
        return frameY + (frameH - CLEAR_SIZE) / 2;
    }

    private boolean overClear(double mouseX, double mouseY) {
        return mouseX >= clearX() - 1 && mouseX < clearX() + CLEAR_SIZE + 1 && mouseY >= clearY() - 1 && mouseY < clearY() + CLEAR_SIZE + 1;
    }

    private void drawClearButton(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        boolean hot = overClear(mouseX, mouseY);
        if (look.isHouse()) {
            HouseColors house = look.house();
            if (hot) {
                Bevels.raised(graphics, clearX(), clearY(), CLEAR_SIZE + 1, CLEAR_SIZE + 1, house.accent());
            } else {
                Bevels.raised(graphics, house, clearX(), clearY(), CLEAR_SIZE + 1, CLEAR_SIZE + 1);
            }
            Glyphs.CROSS.drawCentred(graphics, clearX(), clearY(), CLEAR_SIZE + 1, CLEAR_SIZE + 1, hot ? 0xFFFFFFFF : house.textDim());
        } else {
            VanillaFrame.sprite(graphics, hot ? VanillaFrame.CROSS_BUTTON_HIGHLIGHTED : VanillaFrame.CROSS_BUTTON,
                    clearX() - 1, clearY() - 1, CLEAR_SIZE + 2, CLEAR_SIZE + 2);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (showsClear() && overClear(event.x(), event.y())) {
            setValue("");
            setFocused(true);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
