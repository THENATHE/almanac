package com.thenathe.almanac.client.settings;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

/**
 * A row of colour swatches. Vanilla sets them in a cut-down hotbar with the hotbar's selection
 * frame on the chosen one; the house look in a well with rings for chosen and hovered.
 */
public final class ColourSwatches extends AbstractWidget {
    private static final int PITCH = 20;
    private static final int SWATCH = 14;

    private final Look look;
    private final Setting setting;
    private final List<Setting.Swatch> swatches;

    public ColourSwatches(Look look, Setting setting, int count) {
        super(0, 0, VanillaFrame.hotbarStripWidth(count), 22, setting.label());
        this.look = look;
        this.setting = setting;
        this.swatches = setting.swatches().subList(0, count);
    }

    /** How many swatches fit in a control column: between three and seven. */
    public static int countFor(int controlW, Setting setting) {
        return Math.clamp((controlW - 3) / PITCH, 3, Math.min(7, setting.swatches().size()));
    }

    private int chosen() {
        for (int i = 0; i < swatches.size(); i++) {
            if (swatches.get(i).rgb() == setting.value()) {
                return i;
            }
        }
        return -1;
    }

    private int swatchAt(double mouseX) {
        int index = (int) ((mouseX - getX() - 1) / PITCH);
        return index >= 0 && index < swatches.size() ? index : -1;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int chosen = chosen();
        int hovered = isHovered() ? swatchAt(mouseX) : -1;
        if (look.isHouse()) {
            Bevels.sunken(graphics, look.house(), getX(), getY(), width, height);
        } else {
            VanillaFrame.hotbarStrip(graphics, getX(), getY(), swatches.size());
            if (chosen >= 0) {
                VanillaFrame.sprite(graphics, VanillaFrame.HOTBAR_SELECTION, getX() - 1 + chosen * PITCH, getY() - 1, 24, 23);
            }
        }
        for (int i = 0; i < swatches.size(); i++) {
            int x = getX() + 4 + i * PITCH;
            int y = getY() + 4;
            graphics.fill(x, y, x + SWATCH, y + SWATCH, 0xFF000000 | swatches.get(i).rgb());
            if (look.isHouse()) {
                Bevels.ring(graphics, x, y, SWATCH, SWATCH, HouseColors.OUTLINE);
                if (i == chosen) {
                    Bevels.ring(graphics, x - 2, y - 2, SWATCH + 4, SWATCH + 4, look.house().textStrong());
                } else if (i == hovered) {
                    Bevels.ring(graphics, x - 2, y - 2, SWATCH + 4, SWATCH + 4, look.house().accent());
                }
            } else if (i == hovered) {
                VanillaFrame.sprite(graphics, VanillaFrame.SLOT_HIGHLIGHT_FRONT, x - 5, y - 5, 24, 24);
            }
        }
        if (hovered >= 0) {
            graphics.setTooltipForNextFrame(Minecraft.getInstance().font, swatches.get(hovered).name(), mouseX, mouseY);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int index = swatchAt(event.x());
        if (index >= 0) {
            setting.set(swatches.get(index).rgb());
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int step = event.key() == InputConstants.KEY_LEFT ? -1 : event.key() == InputConstants.KEY_RIGHT ? 1 : 0;
        if (step == 0) {
            return false;
        }
        int next = Math.floorMod(Math.max(0, chosen()) + step, swatches.size());
        setting.set(swatches.get(next).rgb());
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage().copy().append(": ").append(setting.valueText()));
    }
}
