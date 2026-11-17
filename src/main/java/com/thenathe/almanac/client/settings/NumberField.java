package com.thenathe.almanac.client.settings;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.network.chat.Component;

/**
 * A small box beside a wide slider for typing an exact value. A number inside the range applies
 * as it is typed; anything else waits until the box loses focus or Enter is pressed, then the
 * value is clamped into range and shown as the setting now has it.
 */
public final class NumberField extends FramedTextField {
    public static final int WIDTH = 46;

    private final Setting setting;

    public NumberField(Look look, Setting setting) {
        super(look, 0, 0, WIDTH, 20, Component.empty(), false);
        this.setting = setting;
        setMaxLength(Integer.toString(setting.max()).length());
        setValue(Integer.toString(setting.value()));
        setResponder(this::typed);
    }

    public Setting setting() {
        return setting;
    }

    private void typed(String text) {
        Integer number = parse(text);
        if (number != null && number >= setting.min() && number <= setting.max()) {
            setting.set(number);
        }
    }

    /** Clamps what was typed into range and shows the setting's value. */
    public void commit() {
        Integer number = parse(getValue());
        if (number != null) {
            setting.set(number);
        }
        showValue();
    }

    private void showValue() {
        String shown = Integer.toString(setting.value());
        if (!shown.equals(getValue())) {
            setValue(shown);
        }
    }

    private static Integer parse(String text) {
        if (text.isEmpty() || !text.chars().allMatch(Character::isDigit)) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException tooLong) {
            return Integer.MAX_VALUE;
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return Character.isDigit(event.codepoint()) && super.charTyped(event);
    }

    @Override
    public void setFocused(boolean focused) {
        boolean leaving = isFocused() && !focused;
        super.setFocused(focused);
        if (leaving) {
            commit();
        }
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        // The slider, a reset or a preset can change the setting too; follow it while nobody is typing.
        if (!isFocused()) {
            showValue();
        }
        super.extractWidgetRenderState(graphics, mouseX, mouseY, delta);
    }
}
