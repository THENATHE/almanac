package com.thenathe.almanac.client.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;

/** A preset as a tile: its item drawn large, its name under it, and a check while it is in use. */
public final class PresetTile extends AbstractButton {
    public static final int HEIGHT = 4 + 32 + 4 + 12 + 4;

    private final Look look;
    private final Preset preset;
    private final Runnable afterApply;

    public PresetTile(Look look, int w, Preset preset, Runnable afterApply) {
        super(0, 0, w, HEIGHT, preset.label());
        this.look = look;
        this.preset = preset;
        this.afterApply = afterApply;
    }

    public Preset preset() {
        return preset;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (!preset.inUse()) {
            preset.apply();
            afterApply.run();
        }
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean inUse = preset.inUse();
        boolean hot = !inUse && Ink.hot(this);
        Plates.draw(graphics, look, getX(), getY(), width, height, true, hot, inUse, false);

        graphics.pose().pushMatrix();
        graphics.pose().translate(getX() + (width - 32) / 2.0F, getY() + 4);
        graphics.pose().scale(2.0F, 2.0F);
        graphics.fakeItem(preset.icon(), 0, 0);
        graphics.pose().popMatrix();

        Font font = Minecraft.getInstance().font;
        int colour = look.plateText(true, hot, inUse);
        String name = Ink.trim(font, getMessage().getString(), width - 8);
        graphics.text(font, name, getX() + (width - font.width(name)) / 2, getY() + 4 + 32 + 6, colour, look.plateShadow(colour));
        if (inUse) {
            graphics.nextStratum();
            Glyphs.CHECK.drawShadowed(graphics, getX() + width - 14, getY() + 5, Palette.GOOD);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
