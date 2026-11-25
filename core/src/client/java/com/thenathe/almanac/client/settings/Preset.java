package com.thenathe.almanac.client.settings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** A named set of values for several settings, applied together from a tile. */
public final class Preset implements PageRow, Described {
    private final String key;
    private final Supplier<ItemStack> icon;
    private final Map<Setting, Integer> values = new LinkedHashMap<>();

    public Preset(String key, Supplier<ItemStack> icon) {
        this.key = key;
        this.icon = icon;
    }

    public Preset set(Setting setting, int value) {
        values.put(setting, value);
        return this;
    }

    public Preset set(Setting setting, boolean on) {
        return set(setting, on ? 1 : 0);
    }

    public ItemStack icon() {
        return icon.get();
    }

    public boolean inUse() {
        return values.entrySet().stream().allMatch(entry -> entry.getKey().value() == entry.getValue());
    }

    public void apply() {
        values.forEach(Setting::set);
    }

    @Override
    public Component label() {
        return Component.translatable("almanac.preset." + key);
    }

    @Override
    public Component description() {
        return Component.translatable("almanac.preset." + key + ".description");
    }

    @Override
    public Component valueText() {
        return null;
    }

    /** A preset is never "changed"; it has no reset. */
    @Override
    public boolean changed() {
        return false;
    }

    @Override
    public void reset() {
    }

    /** "Sets" and one line per value, for the description box. */
    @Override
    public List<Component> details() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("almanac.preset.sets").withStyle(ChatFormatting.BOLD));
        values.forEach((setting, value) -> lines.add(Component.literal("· ").append(setting.label()).append(": ")
                .append(setting.format(value).copy().withStyle(ChatFormatting.BOLD))));
        return lines;
    }
}
