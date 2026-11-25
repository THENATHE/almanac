package com.thenathe.almanac.client.settings;

import net.minecraft.network.chat.Component;

/** A line in a settings page. */
public sealed interface PageRow permits PageRow.Caption, PageRow.Note, Setting, TextListSetting, Preset {
    /** Starts a group. A foldable caption can hide its group; it starts folded unless something in it is changed. */
    record Caption(Component text, boolean foldable) implements PageRow {
    }

    /** A short wrapped paragraph, such as the line that explains presets. */
    record Note(Component text) implements PageRow {
    }
}
