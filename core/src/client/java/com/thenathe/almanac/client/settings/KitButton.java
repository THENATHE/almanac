package com.thenathe.almanac.client.settings;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * The plate button used for navigation, choice segments and the footer. A "chosen" button
 * stays pressed in: vanilla shows the disabled sprite with white text, the house look fills it
 * with the accent.
 */
public class KitButton extends AbstractButton {
    protected final Look look;
    private final Runnable action;
    private BooleanSupplier chosen = () -> false;

    public KitButton(Look look, int x, int y, int w, int h, Component label, Runnable action) {
        super(x, y, w, h, label);
        this.look = look;
        this.action = action;
    }

    public KitButton chosenWhen(BooleanSupplier chosen) {
        this.chosen = chosen;
        return this;
    }

    protected boolean isChosen() {
        return chosen.getAsBoolean();
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (!isChosen()) {
            action.run();
        }
    }

    protected boolean lit() {
        return active && !isChosen() && Ink.hot(this);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean hot = lit();
        Plates.draw(graphics, look, getX(), getY(), width, height, active, hot, isChosen(), false);
        drawLabel(graphics, look.plateText(active, hot, isChosen()), 0);
    }

    /** Centres the label, leaving {@code reserved} pixels free at the right for a glyph. */
    protected void drawLabel(GuiGraphicsExtractor graphics, int colour, int reserved) {
        Font font = Minecraft.getInstance().font;
        String text = Ink.trim(font, getMessage().getString(), width - 8 - reserved);
        int textX = getX() + (width - reserved - font.width(text)) / 2;
        graphics.text(font, text, textX, getY() + (height - 8) / 2, colour, look.plateShadow(colour));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
