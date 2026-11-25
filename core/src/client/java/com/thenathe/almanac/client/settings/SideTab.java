package com.thenathe.almanac.client.settings;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** A vanilla-look navigation tab: a recipe-book tab stretched to hold the page name. */
public final class SideTab extends AbstractButton {
    private final int panelX;
    private final BooleanSupplier selected;
    private final BooleanSupplier changed;
    private final Runnable choose;

    public SideTab(int x, int y, int panelX, Component name, BooleanSupplier selected, BooleanSupplier changed, Runnable choose) {
        super(x, y, panelX - x + 3, VanillaFrame.SIDE_TAB_H, name);
        this.panelX = panelX;
        this.selected = selected;
        this.changed = changed;
        this.choose = choose;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (!selected.getAsBoolean()) {
            choose.run();
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean on = selected.getAsBoolean();
        // Unselected tabs sit 2 px further out, so the selected one looks pulled into the panel.
        int x = on ? getX() : getX() + 2;
        VanillaFrame.sideTab(graphics, x, panelX, getY(), on);
        if (!on && Ink.hot(this)) {
            graphics.fill(x + 3, getY() + 3, panelX, getY() + VanillaFrame.SIDE_TAB_H - 3, 0x30FFFFFF);
        }
        Font font = Minecraft.getInstance().font;
        String text = Ink.trim(font, getMessage().getString(), panelX - x - 12);
        int colour = on ? Palette.PANEL_TEXT : Palette.BUTTON_TEXT;
        graphics.text(font, text, x + 8, getY() + 9, colour, Ink.shadow(colour));
        if (changed.getAsBoolean()) {
            VanillaFrame.changedMark(graphics, x + 3, getY() + 3);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
