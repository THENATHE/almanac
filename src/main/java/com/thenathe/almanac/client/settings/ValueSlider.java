package com.thenathe.almanac.client.settings;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;

/** A stepped slider. Vanilla draws it as usual; the house look uses a well, an accent fill and an 8 px handle. */
public final class ValueSlider extends AbstractSliderButton {
    private static final int HANDLE_W = 8;

    private final Look look;
    private final Setting setting;

    public ValueSlider(Look look, int w, Setting setting) {
        super(0, 0, w, 20, Component.empty(), fraction(setting, setting.value()));
        this.look = look;
        this.setting = setting;
        updateMessage();
    }

    private static double fraction(Setting setting, int value) {
        return (double) (value - setting.min()) / (setting.max() - setting.min());
    }

    private int steppedValue() {
        double raw = setting.min() + value * (setting.max() - setting.min());
        int steps = (int) Math.round((raw - setting.min()) / setting.step());
        return Math.min(setting.max(), setting.min() + steps * setting.step());
    }

    @Override
    protected void updateMessage() {
        setMessage(setting.format(steppedValue()));
    }

    @Override
    protected void applyValue() {
        setting.set(steppedValue());
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // A reset or preset elsewhere changes the setting; follow it so the handle never shows a stale value.
        double current = fraction(setting, setting.value());
        if (Math.abs(current - fraction(setting, steppedValue())) > 1.0E-9) {
            value = current;
            updateMessage();
        }
        if (!look.isHouse()) {
            super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
            return;
        }
        HouseColors house = look.house();
        int x = getX();
        int y = getY();
        Bevels.sunken(graphics, house, x, y, width, height);
        int handleX = x + (int) Math.round(value * (width - HANDLE_W));
        if (active && handleX > x + 1) {
            graphics.fill(x + 1, y + 1, handleX, y + height - 1, house.sliderFill());
        }
        boolean hot = active && Ink.hot(this);
        Bevels.raised(graphics, handleX, y, HANDLE_W, height, hot ? house.accent() : house.faceHigh());
        Font font = Minecraft.getInstance().font;
        String text = getMessage().getString();
        int colour = active ? house.textStrong() : house.textOff();
        graphics.text(font, text, x + (width - font.width(text)) / 2, y + (height - 8) / 2, colour, house.textShadow());
    }
}
