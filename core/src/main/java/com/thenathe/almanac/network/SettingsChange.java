package com.thenathe.almanac.network;

import com.thenathe.almanac.config.ServerConfig;
import org.jspecify.annotations.Nullable;

/**
 * What becomes of the settings changes a player sent from the settings screen. The server decides from its own idea
 * of the player's permission, never from anything the client says, and reads the changes on top of its own current
 * settings, every value checked as for a file.
 *
 * @param settings the settings to apply; null unless {@code outcome} is {@link Outcome#APPLIED}
 */
record SettingsChange(Outcome outcome, @Nullable ServerConfig settings) {
    enum Outcome {
        APPLIED,
        /** The player may not change the server's settings; nothing changes. */
        DENIED,
        /** What was sent isn't a JSON object of settings; nothing changes. */
        UNREADABLE
    }

    static SettingsChange judge(boolean mayEdit, ServerConfig current, String changesJson) {
        if (!mayEdit) {
            return new SettingsChange(Outcome.DENIED, null);
        }
        ServerConfig updated = ServerConfig.withChanges(current, changesJson);
        return updated == null ? new SettingsChange(Outcome.UNREADABLE, null) : new SettingsChange(Outcome.APPLIED, updated);
    }
}
