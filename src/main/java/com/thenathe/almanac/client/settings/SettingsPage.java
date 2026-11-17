package com.thenathe.almanac.client.settings;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;

/** One tab of the settings screen, declared top to bottom. */
public final class SettingsPage {
    private final Component name;
    private final List<PageRow> rows = new ArrayList<>();

    public SettingsPage(Component name) {
        this.name = name;
    }

    public SettingsPage caption(String key) {
        rows.add(new PageRow.Caption(Component.translatable("almanac.caption." + key), false));
        return this;
    }

    /** A caption whose group can be folded away; use it for settings most players never touch. */
    public SettingsPage foldedCaption(String key) {
        rows.add(new PageRow.Caption(Component.translatable("almanac.caption." + key), true));
        return this;
    }

    public SettingsPage note(String key) {
        rows.add(new PageRow.Note(Component.translatable("almanac.note." + key)));
        return this;
    }

    public SettingsPage add(PageRow row) {
        rows.add(row);
        return this;
    }

    public Component name() {
        return name;
    }

    public List<PageRow> rows() {
        return rows;
    }

    public boolean changed() {
        return rows.stream().anyMatch(row -> row instanceof Described described && described.changed());
    }
}
