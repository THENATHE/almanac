package com.thenathe.almanac.client.settings;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.item.ItemStack;

/** A row of item slots, each an on/off. On slots get a green check in the corner. */
public final class ItemSlots extends AbstractWidget {
    private static final int SLOT = 18;
    private static final int PITCH = 24;

    private final Look look;
    private final Setting setting;
    private int keyboardSlot;

    public ItemSlots(Look look, Setting setting) {
        super(0, 0, setting.items().size() * PITCH - (PITCH - SLOT), SLOT, setting.label());
        this.look = look;
        this.setting = setting;
    }

    public static int width(Setting setting) {
        return setting.items().size() * PITCH - (PITCH - SLOT);
    }

    private int slotAt(double mouseX) {
        int offset = (int) mouseX - getX();
        int slot = offset / PITCH;
        return offset >= 0 && offset % PITCH < SLOT && slot < setting.items().size() ? slot : -1;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int hovered = isHovered() ? slotAt(mouseX) : -1;
        boolean keyboard = isFocused() && Minecraft.getInstance().getLastInputType().isKeyboard();
        for (int i = 0; i < setting.items().size(); i++) {
            int x = getX() + i * PITCH;
            int y = getY();
            boolean on = setting.itemOn(i);
            boolean lit = i == hovered || keyboard && i == keyboardSlot;
            drawSlot(graphics, x, y, on);
            if (lit) {
                VanillaFrame.sprite(graphics, VanillaFrame.SLOT_HIGHLIGHT_BACK, x - 3, y - 3, 24, 24);
            }
            graphics.fakeItem(new ItemStack(setting.items().get(i)), x + 1, y + 1);
            if (lit) {
                VanillaFrame.sprite(graphics, VanillaFrame.SLOT_HIGHLIGHT_FRONT, x - 3, y - 3, 24, 24);
            }
            if (on) {
                graphics.nextStratum();
                graphics.fill(x + SLOT - 9, y + SLOT - 8, x + SLOT, y + SLOT, 0xE0202020);
                Glyphs.CHECK.draw(graphics, x + SLOT - 9, y + SLOT - 7, Palette.GOOD);
            }
        }
        if (hovered >= 0) {
            graphics.setTooltipForNextFrame(new ItemStack(setting.items().get(hovered)).getHoverName(), mouseX, mouseY);
        }
    }

    private void drawSlot(GuiGraphicsExtractor graphics, int x, int y, boolean on) {
        if (look.isHouse()) {
            HouseColors house = look.house();
            Bevels.sunken(graphics, house, x, y, SLOT, SLOT, on ? Palette.shade(house.accent(), 0.35F) : HouseColors.OUTLINE);
            if (on) {
                Bevels.ring(graphics, x - 1, y - 1, SLOT + 2, SLOT + 2, house.accent());
            }
        } else {
            VanillaFrame.sprite(graphics, VanillaFrame.SLOT, x, y, SLOT, SLOT);
            if (on) {
                VanillaFrame.sprite(graphics, VanillaFrame.HOTBAR_SELECTION, x - 3, y - 3, 24, 23);
            }
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int slot = slotAt(event.x());
        if (slot >= 0) {
            setting.flipItem(slot);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int count = setting.items().size();
        switch (event.key()) {
            case InputConstants.KEY_LEFT -> keyboardSlot = Math.floorMod(keyboardSlot - 1, count);
            case InputConstants.KEY_RIGHT -> keyboardSlot = Math.floorMod(keyboardSlot + 1, count);
            case InputConstants.KEY_RETURN, InputConstants.KEY_SPACE -> setting.flipItem(keyboardSlot);
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage().copy().append(": ").append(setting.valueText()));
    }
}
