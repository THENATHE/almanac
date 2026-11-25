package com.thenathe.almanac.client;

import com.thenathe.almanac.config.ServerConfig;
import org.jspecify.annotations.Nullable;

/** The server's settings as it last sent them, and whether this player may change them. Reset on disconnect. */
public final class ClientState {
    private static @Nullable ServerConfig serverSettings;
    private static boolean mayEditServerSettings;

    private ClientState() {
    }

    static void settings(boolean canEdit, @Nullable ServerConfig settings) {
        mayEditServerSettings = canEdit;
        serverSettings = settings;
    }

    static void reset() {
        serverSettings = null;
        mayEditServerSettings = false;
    }

    /** The server's settings as last sent, or null when the server doesn't have Almanac. */
    public static @Nullable ServerConfig serverSettings() {
        return serverSettings;
    }

    public static boolean mayEditServerSettings() {
        return mayEditServerSettings;
    }
}
