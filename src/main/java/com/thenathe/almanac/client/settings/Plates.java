package com.thenathe.almanac.client.settings;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** The button surface in both looks, shared by buttons, dropdowns, tiles, chips and the reset button. */
public final class Plates {
    private Plates() {
    }

    /**
     * @param chosen a pressed-in state: a chosen segment, the open tab, a preset in use
     * @param armed  waiting for a second click to confirm something destructive
     */
    public static void draw(GuiGraphicsExtractor graphics, Look look, int x, int y, int w, int h,
                            boolean active, boolean hot, boolean chosen, boolean armed) {
        if (!look.isHouse()) {
            VanillaFrame.sprite(graphics, VanillaFrame.button(active && !chosen, hot), x, y, w, h);
            return;
        }
        HouseColors house = look.house();
        if (armed) {
            Bevels.raised(graphics, x, y, w, h, hot ? Palette.shade(HouseColors.DANGER, 0.12F) : HouseColors.DANGER);
        } else if (chosen) {
            Bevels.raised(graphics, x, y, w, h, hot ? house.accentHot() : house.accent());
        } else if (!active) {
            Bevels.sunken(graphics, house, x, y, w, h);
        } else if (hot) {
            Bevels.raised(graphics, x, y, w, h, house.accent());
        } else {
            Bevels.raised(graphics, house, x, y, w, h);
        }
    }
}
