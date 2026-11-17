package com.thenathe.almanac.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.thenathe.almanac.client.settings.Accent;
import com.thenathe.almanac.client.settings.SettingsStyle;
import com.thenathe.almanac.config.ServerConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This client's own choices, in {@code config/almanac-client.json}. Field names are part of the file, so they never
 * change; a file from an older version fills its missing fields from the defaults.
 */
public final class ClientConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("almanac");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "almanac-client.json";

    public static final int MIN_REDRAW_SECTIONS = 1;
    public static final int MAX_REDRAW_SECTIONS = 512;

    private static ClientConfig current;

    public int version = 1;
    public String _credits = ServerConfig.CREDITS;

    // How the settings screen looks. House is the default; the Minecraft look stays one click away.
    public SettingsStyle style = SettingsStyle.HOUSE;
    public boolean lightMode = false;
    public Accent accent = Accent.GREEN;

    /** Colours change a little every day; off, they change in twelve steps, as players without Almanac see them. */
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
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static ClientConfig load() {
        Path file = file();
        if (Files.isRegularFile(file)) {
            try {
                ClientConfig read = fromJson(Files.readString(file));
                if (read != null) {
                    return read;
                }
            } catch (IOException failure) {
                LOGGER.warn("Couldn't read {}; using defaults", file, failure);
            }
            LOGGER.warn("Couldn't understand {}; using defaults for now and leaving the file untouched", file);
        }
        return new ClientConfig();
    }

    public static void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            // Write beside the real file first so a crash mid-write can't leave it half written.
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, get().toJson());
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            LOGGER.warn("Couldn't save {}", file, failure);
        }
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
