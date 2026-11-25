package com.thenathe.almanac.client.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** One value of a list row. Clicking anywhere on it removes it. */
public final class Chip extends AbstractButton {
    public static final int HEIGHT = 14;
    private static final int CROSS_ROOM = 14;

    private final Look look;
    private final Runnable remove;

    public Chip(Look look, String text, Runnable remove) {
        super(0, 0, width(text), HEIGHT, Component.literal(text));
        this.look = look;
        this.remove = remove;
    }

    public static int width(String text) {
        return Minecraft.getInstance().font.width(text) + 6 + CROSS_ROOM;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        remove.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean hot = Ink.hot(this);
        Font font = Minecraft.getInstance().font;
        int x = getX();
        int y = getY();
        if (look.isHouse()) {
            HouseColors house = look.house();
            if (hot) {
                Bevels.raised(graphics, x, y, width, height, house.accent());
            } else {
                Bevels.raised(graphics, house, x, y, width, height);
            }
            int colour = hot ? 0xFFFFFFFF : house.text();
            graphics.text(font, getMessage(), x + 4, y + 3, colour, house.textShadow() || hot);
            Glyphs.CROSS.draw(graphics, x + width - 11, y + 4, hot ? 0xFFFFFFFF : house.textDim());
        } else {
            VanillaFrame.sprite(graphics, VanillaFrame.BUTTON, x, y, width, height);
            graphics.text(font, getMessage(), x + 4, y + 3, Palette.BUTTON_TEXT, true);
            VanillaFrame.sprite(graphics, hot ? VanillaFrame.CROSS_BUTTON_HIGHLIGHTED : VanillaFrame.CROSS_BUTTON,
                    x + width - CROSS_ROOM, y, CROSS_ROOM, CROSS_ROOM);
        }
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        return Component.translatable("almanac.chip.remove", getMessage());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
