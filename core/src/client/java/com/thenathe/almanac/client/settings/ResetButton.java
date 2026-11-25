package com.thenathe.almanac.client.settings;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** The 20x20 reset at the end of each row. It only works while the row differs from its default. */
public final class ResetButton extends AbstractButton {
    public static final int SIZE = 20;

    private final Look look;
    private final Described row;
    private final Runnable afterReset;

    public ResetButton(Look look, Described row, Runnable afterReset) {
        super(0, 0, SIZE, SIZE, Component.translatable("almanac.reset"));
        this.look = look;
        this.row = row;
        this.afterReset = afterReset;
        setTooltip(Tooltip.create(Component.translatable("almanac.reset.tooltip")));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        row.reset();
        afterReset.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        active = row.changed();
        boolean hot = active && Ink.hot(this);
        Plates.draw(graphics, look, getX(), getY(), width, height, active, hot, false, false);
        int colour = active ? look.plateText(true, hot, false) : 0x66FFFFFF;
        if (look.isHouse() && !active) {
            colour = look.house().textOff();
        }
        Glyphs.UNDO.drawCentred(graphics, getX(), getY(), width, height, colour);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
