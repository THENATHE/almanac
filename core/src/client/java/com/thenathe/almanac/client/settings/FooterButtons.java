package com.thenathe.almanac.client.settings;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** The house look's two extra footer buttons: the accent picker and the light/dark switch. */
public final class FooterButtons {
    private FooterButtons() {
    }

    /** "Color", with a small square of the current accent before the word. */
    public static final class AccentButton extends KitButton {
        private static final int SWATCH = 8;

        private final Consumer<AbstractWidget> openPicker;

        /** The picker is anchored to this button, so it receives the button when pressed. */
        public AccentButton(Look look, int x, int y, int w, Consumer<AbstractWidget> openPicker) {
            super(look, x, y, w, 20, Component.translatable("almanac.footer.color"), () -> {});
            this.openPicker = openPicker;
        }

        @Override
        public void onPress(InputWithModifiers input) {
            openPicker.accept(this);
        }

        public static int width(Font font) {
            return Math.max(40, font.width(Component.translatable("almanac.footer.color")) + 16 + SWATCH + 4);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            boolean hot = lit();
            Plates.draw(graphics, look, getX(), getY(), width, height, active, hot, false, false);
            int swatchX = getX() + 6;
            int swatchY = getY() + (height - SWATCH) / 2;
            graphics.fill(swatchX, swatchY, swatchX + SWATCH, swatchY + SWATCH, HouseColors.OUTLINE);
            graphics.fill(swatchX + 1, swatchY + 1, swatchX + SWATCH - 1, swatchY + SWATCH - 1, look.house().accent());
            int colour = look.plateText(active, hot, false);
            Font font = Minecraft.getInstance().font;
            graphics.text(font, getMessage(), swatchX + SWATCH + 4, getY() + (height - 8) / 2, colour, look.plateShadow(colour));
        }
    }

    /** A square button with a sun in light mode and a moon in dark mode. */
    public static final class ModeButton extends KitButton {
        private static final int SUN = 0xFFF5B83D;
        private static final int MOON = 0xFFDDE3F0;

        public ModeButton(Look look, int x, int y, Runnable flip) {
            super(look, x, y, 20, 20, Component.empty(), flip);
            String mode = look.house().light() ? "light" : "dark";
            setTooltip(Tooltip.create(Component.translatable("almanac.footer.mode." + mode)));
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            Plates.draw(graphics, look, getX(), getY(), width, height, active, lit(), false, false);
            Glyphs glyph = look.house().light() ? Glyphs.SUN : Glyphs.MOON;
            int colour = look.house().light() ? SUN : MOON;
            glyph.drawCentred(graphics, getX(), getY(), width, height, colour);
        }
    }
}
