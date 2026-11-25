package com.thenathe.almanac.client.settings;

/**
 * Everything a widget needs to know to draw itself: the style, and for the house style its
 * colours. Widgets take a Look when built, so switching look rebuilds the screen's widgets.
 */
public record Look(SettingsStyle style, HouseColors house) {

    public boolean isHouse() {
        return style == SettingsStyle.HOUSE;
    }

    public int label(boolean enabled) {
        if (isHouse()) {
            return enabled ? house.text() : house.textOff();
        }
        return enabled ? Palette.PANEL_TEXT : Palette.PANEL_TEXT_OFF;
    }

    public int dim() {
        return isHouse() ? house.textDim() : Palette.PANEL_DIM;
    }

    public int strong() {
        return isHouse() ? house.textStrong() : Palette.PANEL_TEXT;
    }

    public int value() {
        return isHouse() ? house.valueText() : Palette.PANEL_VALUE;
    }

    public int warn() {
        return isHouse() ? house.warn() : Palette.PANEL_WARN;
    }

    public int rule() {
        return isHouse() ? house.rule() : Palette.PANEL_RULE;
    }

    /** Text drawn on the panel: house text follows the mode, vanilla panel text never has a shadow. */
    public boolean panelShadow() {
        return isHouse() && house.textShadow();
    }

    /** Label colour for a plate (button) in the given state. */
    public int plateText(boolean active, boolean hot, boolean chosen) {
        if (isHouse()) {
            if (hot || chosen) {
                return 0xFFFFFFFF;
            }
            return active ? house.text() : house.textOff();
        }
        return active || chosen ? Palette.BUTTON_TEXT : Palette.BUTTON_TEXT_OFF;
    }

    public boolean plateShadow(int colour) {
        return isHouse() ? house.textShadow() || colour == 0xFFFFFFFF : Ink.shadow(colour);
    }
}
