package com.thenathe.almanac.client.settings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.network.chat.Component;

/** A row holding a list of short strings, such as item ids, shown as removable chips. */
public final class TextListSetting implements PageRow, Described {
    private final String key;
    private final List<String> defaults;
    private final Supplier<List<String>> getter;
    private final Consumer<List<String>> setter;

    public TextListSetting(String key, List<String> defaults, Supplier<List<String>> getter, Consumer<List<String>> setter) {
        this.key = key;
        this.defaults = List.copyOf(defaults);
        this.getter = getter;
        this.setter = setter;
    }

    public List<String> values() {
        return getter.get();
    }

    /** Adds a trimmed, non-empty value once; returns whether anything was added. */
    public boolean add(String value) {
        String trimmed = value.trim();
        if (trimmed.isEmpty() || values().contains(trimmed)) {
            return false;
        }
        List<String> next = new ArrayList<>(values());
        next.add(trimmed);
        setter.accept(next);
        return true;
    }

    public void remove(String value) {
        List<String> next = new ArrayList<>(values());
        next.remove(value);
        setter.accept(next);
    }

    @Override
    public Component label() {
        return Component.translatable("almanac.setting." + key);
    }

    @Override
    public Component description() {
        return Component.translatable("almanac.setting." + key + ".description");
    }

    @Override
    public Component valueText() {
        return Component.translatable("almanac.list.count", values().size());
    }

    @Override
    public boolean changed() {
        return !values().equals(defaults);
    }

    @Override
    public void reset() {
        setter.accept(new ArrayList<>(defaults));
    }

    public Component placeholder() {
        return Component.translatable("almanac.setting." + key + ".placeholder");
    }
}
