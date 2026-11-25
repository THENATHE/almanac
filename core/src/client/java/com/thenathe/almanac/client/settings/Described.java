package com.thenathe.almanac.client.settings;

import java.util.List;
import net.minecraft.network.chat.Component;

/** Anything the description box can explain and the reset button can put back. */
public interface Described {
    Component label();

    Component description();

    /** The current value as shown after the name, or null for rows without one. */
    Component valueText();

    boolean changed();

    void reset();

    /** Extra lines under the description, such as what a preset sets. */
    default List<Component> details() {
        return List.of();
    }
}
