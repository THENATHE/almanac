package com.thenathe.almanac.client.settings;

import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/**
 * A settings row whose value is one int: a toggle is 0 or 1, a choice an index, an items row
 * a bit per item, a colour row an RGB value. One shape keeps change tracking, reset and presets
 * the same for every kind.
 */
public final class Setting implements PageRow, Described {
    public enum Kind { TOGGLE, CHOICE, SLIDER, NUMBER, ITEMS, COLOUR }

    /** A swatch on a colour row. */
    public record Swatch(int rgb, Component name) {
    }

    private final Kind kind;
    private final String key;
    private final IntSupplier getter;
    private final IntConsumer setter;
    private final int defaultValue;
    private final int min;
    private final int max;
    private final int step;
    private final List<Component> choices;
    private final List<Item> items;
    private final List<Swatch> swatches;
    private final IntFunction<Component> format;

    private Setting(Builder builder) {
        this.kind = builder.kind;
        this.key = builder.key;
        this.getter = builder.getter;
        this.setter = builder.setter;
        this.defaultValue = builder.defaultValue;
        this.min = builder.min;
        this.max = builder.max;
        this.step = builder.step;
        this.choices = builder.choices;
        this.items = builder.items;
        this.swatches = builder.swatches;
        this.format = builder.format;
    }

    public static Setting toggle(String key, boolean defaultValue, IntSupplier getter, IntConsumer setter) {
        return new Builder(Kind.TOGGLE, key, defaultValue ? 1 : 0, getter, setter).range(0, 1, 1)
                .format(value -> value != 0 ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF).build();
    }

    public static Setting choice(String key, int defaultValue, List<Component> choices, IntSupplier getter, IntConsumer setter) {
        Builder builder = new Builder(Kind.CHOICE, key, defaultValue, getter, setter).range(0, choices.size() - 1, 1).format(choices::get);
        builder.choices = choices;
        return builder.build();
    }

    public static Setting slider(String key, int defaultValue, int min, int max, int step, IntFunction<Component> format,
                                 IntSupplier getter, IntConsumer setter) {
        return new Builder(Kind.SLIDER, key, defaultValue, getter, setter).range(min, max, step).format(format).build();
    }

    /** A slider with a box beside it for typing an exact value, for ranges too wide to pick from by dragging. */
    public static Setting number(String key, int defaultValue, int min, int max, IntFunction<Component> format,
                                 IntSupplier getter, IntConsumer setter) {
        return new Builder(Kind.NUMBER, key, defaultValue, getter, setter).range(min, max, 1).format(format).build();
    }

    /** Each item is its own on/off, stored as one bit of the value. */
    public static Setting items(String key, int defaultMask, List<Item> items, IntSupplier getter, IntConsumer setter) {
        Builder builder = new Builder(Kind.ITEMS, key, defaultMask, getter, setter).range(0, (1 << items.size()) - 1, 1)
                .format(mask -> Component.translatable("almanac.items.count", Integer.bitCount(mask), items.size()));
        builder.items = items;
        return builder.build();
    }

    public static Setting colour(String key, int defaultRgb, List<Swatch> swatches, IntSupplier getter, IntConsumer setter) {
        Builder builder = new Builder(Kind.COLOUR, key, defaultRgb, getter, setter).range(0, 0xFFFFFF, 1)
                .format(rgb -> swatches.stream().filter(swatch -> swatch.rgb() == rgb).map(Swatch::name).findFirst()
                        .orElse(Component.literal(String.format(Locale.ROOT, "#%06X", rgb))));
        builder.swatches = swatches;
        return builder.build();
    }

    public Kind kind() {
        return kind;
    }

    /** The translation key of the label; presets name the rows they set with it. */
    public String key() {
        return key;
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
        return format(value());
    }

    public int value() {
        return getter.getAsInt();
    }

    public void set(int value) {
        setter.accept(Math.clamp(value, min, max));
    }

    @Override
    public boolean changed() {
        return value() != defaultValue;
    }

    @Override
    public void reset() {
        set(defaultValue);
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    public int step() {
        return step;
    }

    public List<Component> choices() {
        return choices;
    }

    public List<Item> items() {
        return items;
    }

    public List<Swatch> swatches() {
        return swatches;
    }

    public boolean itemOn(int index) {
        return (value() & 1 << index) != 0;
    }

    public void flipItem(int index) {
        set(value() ^ 1 << index);
    }

    public Component format(int value) {
        return format.apply(value);
    }

    private static final class Builder {
        private final Kind kind;
        private final String key;
        private final int defaultValue;
        private final IntSupplier getter;
        private final IntConsumer setter;
        private int min;
        private int max;
        private int step = 1;
        private List<Component> choices = List.of();
        private List<Item> items = List.of();
        private List<Swatch> swatches = List.of();
        private IntFunction<Component> format = value -> Component.literal(Integer.toString(value));

        private Builder(Kind kind, String key, int defaultValue, IntSupplier getter, IntConsumer setter) {
            this.kind = kind;
            this.key = key;
            this.defaultValue = defaultValue;
            this.getter = getter;
            this.setter = setter;
        }

        private Builder range(int min, int max, int step) {
            this.min = min;
            this.max = max;
            this.step = step;
            return this;
        }

        private Builder format(IntFunction<Component> format) {
            this.format = format;
            return this;
        }

        private Setting build() {
            return new Setting(this);
        }
    }
}
