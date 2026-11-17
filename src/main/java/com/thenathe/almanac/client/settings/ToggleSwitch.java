package com.thenathe.almanac.client.settings;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.MutableComponent;

/** A 28x17 on/off switch. Vanilla builds it from the slider sprites; the house look from bevels. */
public final class ToggleSwitch extends AbstractButton {
    public static final int WIDTH = 28;
    public static final int HEIGHT = 17;
    private static final int VANILLA_KNOB_W = 10;
    private static final int HOUSE_KNOB_W = 12;
    private static final int ON_TRACK_FILL = 0xFF1E5A14;
    private static final int ON_KNOB_FILL = 0xFF4FA03C;

    private final Look look;
    private final Setting setting;

    public ToggleSwitch(Look look, Setting setting) {
        super(0, 0, WIDTH, HEIGHT, setting.label());
        this.look = look;
        this.setting = setting;
    }

    private boolean on() {
        return setting.value() != 0;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        setting.set(on() ? 0 : 1);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean hot = active && Ink.hot(this);
        if (look.isHouse()) {
            drawHouse(graphics, hot);
        } else {
            drawVanilla(graphics, hot);
        }
    }

    private void drawVanilla(GuiGraphicsExtractor graphics, boolean hot) {
        int x = getX();
        int y = getY();
        int knobX = on() ? x + WIDTH - VANILLA_KNOB_W : x;
        VanillaFrame.sprite(graphics, hot ? VanillaFrame.SLIDER_HIGHLIGHTED : VanillaFrame.SLIDER, x, y, WIDTH, HEIGHT);
        if (on()) {
            graphics.fill(x + 2, y + 2, knobX, y + HEIGHT - 2, ON_TRACK_FILL);
        }
        VanillaFrame.sprite(graphics, hot ? VanillaFrame.SLIDER_HANDLE_HIGHLIGHTED : VanillaFrame.SLIDER_HANDLE, knobX, y, VANILLA_KNOB_W, HEIGHT);
        if (on()) {
            graphics.fill(knobX + 1, y + 1, knobX + VANILLA_KNOB_W - 1, y + HEIGHT - 1, ON_KNOB_FILL);
        }
        if (!active) {
            graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xAAC6C6C6);
        }
    }

    private void drawHouse(GuiGraphicsExtractor graphics, boolean hot) {
        HouseColors house = look.house();
        int x = getX();
        int y = getY();
        Bevels.sunken(graphics, house, x, y + 2, WIDTH, 13);
        if (on()) {
            int fill = active ? house.accent() : Palette.shade(house.accent(), -0.5F);
            graphics.fill(x + 1, y + 3, x + WIDTH - 1, y + HEIGHT - 3, fill);
        }
        int knobX = on() ? x + WIDTH - HOUSE_KNOB_W : x;
        if (!active) {
            Bevels.sunken(graphics, house, knobX, y, HOUSE_KNOB_W, HEIGHT);
        } else if (hot) {
            Bevels.raised(graphics, knobX, y, HOUSE_KNOB_W, HEIGHT, Palette.shade(house.face(), 0.18F));
        } else {
            Bevels.raised(graphics, house, knobX, y, HOUSE_KNOB_W, HEIGHT);
        }
    }

    @Override
    protected MutableComponent createNarrationMessage() {
        return CommonComponents.optionStatus(getMessage(), on());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
