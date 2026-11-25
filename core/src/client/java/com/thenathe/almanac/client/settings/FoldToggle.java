package com.thenathe.almanac.client.settings;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * The clickable strip over a foldable caption. It draws nothing itself; the screen paints the
 * caption with its chevron, so the strip only gives the caption mouse and keyboard focus.
 */
public final class FoldToggle extends AbstractButton {
    private final Runnable flip;

    public FoldToggle(int w, Component caption, Runnable flip) {
        super(0, 0, w, 12, caption);
        this.flip = flip;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        flip.run();
    }

    public boolean lit() {
        return Ink.hot(this);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
