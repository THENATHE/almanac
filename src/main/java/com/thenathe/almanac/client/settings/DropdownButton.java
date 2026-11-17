package com.thenathe.almanac.client.settings;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** A button showing the current choice with a down chevron; pressing it opens the screen's popup list. */
public final class DropdownButton extends KitButton {
    private static final int CHEVRON_ROOM = 13;

    private final Setting setting;
    private final PopupList popup;

    public DropdownButton(Look look, int w, Setting setting, PopupList popup) {
        super(look, 0, 0, w, 20, Component.empty(), () -> {});
        this.setting = setting;
        this.popup = popup;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        if (popup.isOpenFor(this)) {
            popup.close();
            return;
        }
        List<PopupList.Option> options = setting.choices().stream().map(PopupList.Option::of).toList();
        popup.open(this, options, setting.value(), setting::set);
    }

    @Override
    public Component getMessage() {
        return setting.format(setting.value());
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        boolean hot = lit();
        Plates.draw(graphics, look, getX(), getY(), width, height, active, hot, false, false);
        int colour = look.plateText(active, hot, false);
        drawLabel(graphics, colour, CHEVRON_ROOM);
        int chevronX = getX() + width - CHEVRON_ROOM;
        int chevronY = getY() + (height - Glyphs.CHEVRON_DOWN.height()) / 2;
        if (look.plateShadow(colour)) {
            Glyphs.CHEVRON_DOWN.drawShadowed(graphics, chevronX, chevronY, colour);
        } else {
            Glyphs.CHEVRON_DOWN.draw(graphics, chevronX, chevronY, colour);
        }
    }
}
