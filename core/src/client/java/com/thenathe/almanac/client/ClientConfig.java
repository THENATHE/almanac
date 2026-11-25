package com.thenathe.almanac.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.thenathe.almanac.client.settings.Accent;
import com.thenathe.almanac.client.settings.SettingsStyle;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.config.SettingsFile;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * This client's own choices, in {@code config/almanac-client.json}. Field names are part of the file, so they never
 * change; a file from an older version fills its missing fields from the defaults. A file that doesn't read is set
 * aside before anything saves over it ({@link SettingsFile}).
 */
public final class ClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "almanac-client.json";
    private static final SettingsFile<ClientConfig> FILE = new SettingsFile<>(
            () -> FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME), ClientConfig::fromJson, ClientConfig::toJson);

    public static final int MIN_REDRAW_SECTIONS = 1;
    public static final int MAX_REDRAW_SECTIONS = 512;

    private static ClientConfig current;

    public int version = 1;
    public String _credits = ServerConfig.CREDITS;

    // How the settings screen looks. House is the default; the Minecraft look stays one click away.
    public SettingsStyle style = SettingsStyle.HOUSE;
    public boolean lightMode = false;
    public Accent accent = Accent.GREEN;

    /** Colours change a little every day; off, they change in twelve steps, one per sub-season. */
    public boolean colourBlending = true;
    /** Sections with grass or leaves redrawn per tick after a colour change. */
    public int redrawSectionsPerTick = 24;

    public static ClientConfig get() {
        if (current == null) {
            current = load();
        }
        return current;
    }

    public static Path file() {
        return FILE.path();
    }

    private static ClientConfig load() {
        return FILE.readOr(ClientConfig::new);
    }

    public static void save() {
        FILE.save(get());
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    /** Reads settings from the file's text and puts every value in range; returns null for text that isn't JSON. */
    public static ClientConfig fromJson(String json) {
        try {
            ClientConfig read = GSON.fromJson(json, ClientConfig.class);
            return read == null ? null : read.validated();
        } catch (JsonParseException invalid) {
            return null;
        }
    }

    /** Gson leaves fields an older file lacks, and names it doesn't know, as null. */
    ClientConfig validated() {
        ClientConfig defaults = new ClientConfig();
        if (style == null) {
            style = defaults.style;
        }
        if (accent == null) {
            accent = defaults.accent;
        }
        redrawSectionsPerTick = Math.clamp(redrawSectionsPerTick, MIN_REDRAW_SECTIONS, MAX_REDRAW_SECTIONS);
        version = defaults.version;
        _credits = ServerConfig.CREDITS;
        return this;
    }
}
