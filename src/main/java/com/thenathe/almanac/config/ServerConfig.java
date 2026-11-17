package com.thenathe.almanac.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.climate.RainShares;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server settings. The server owns them: they live in {@code config/almanac-server.json}, operators change them from
 * the settings screen or with {@code /almanac config}, and clients receive a copy.
 *
 * <p>Field names are part of the file and of the settings sync, so they never change; a file written by an older
 * version fills its missing fields from the defaults. The climate of the seasonal biome copies is built once at
 * server start, so {@link #winterTemperature}, {@link #summerTemperature} and {@link #snowLineAboveSea} apply after
 * a restart.
 */
public final class ServerConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger("almanac");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "almanac-server.json";
    public static final String CREDITS = "Almanac is new code. Idea by Fabric Seasons (lucaargolo): biome edits in memory and "
            + "tracking weather-placed snow. Idea by Serene Seasons (Glitchfiend): sub-seasons and rain timing. Idea by "
            + "Immersive Snow Reloaded (Straywave and contributors): snow recalculation on chunk load. No code is copied "
            + "from any of them.";

    public static final int MIN_DAYS_PER_SEASON = 3;
    public static final int MAX_DAYS_PER_SEASON = 1200;
    public static final int MIN_SNOW_LINE = 18;
    public static final int MAX_SNOW_LINE = 400;
    public static final double MIN_WINTER_TEMPERATURE = -1.0;
    public static final double MAX_WINTER_TEMPERATURE = 0.1;
    public static final double MIN_SUMMER_TEMPERATURE = 0.2;
    public static final double MAX_SUMMER_TEMPERATURE = 2.0;
    public static final int MIN_REFRESH_CHUNKS = 1;
    public static final int MAX_REFRESH_CHUNKS = 64;
    public static final int MIN_SNOW_COLUMNS = 1;
    public static final int MAX_SNOW_COLUMNS = 4096;
    public static final int MIN_CATCH_UP_CHUNKS = 1;
    public static final int MAX_CATCH_UP_CHUNKS = 256;
    public static final double MIN_THAW_DAYS = 0.25;
    public static final double MAX_THAW_DAYS = 16.0;
    public static final int MIN_FAR_RADIUS = 512;
    public static final int MAX_FAR_RADIUS = 16384;
    /** Climate map cells, in blocks; each divides a tile evenly. */
    public static final List<Integer> FAR_CELL_SIZES = List.of(16, 32, 64, 128);
    public static final int MIN_FAR_BYTES = 512;
    public static final int MAX_FAR_BYTES = 65536;

    private static ServerConfig current;

    public int version = 1;
    public String _credits = CREDITS;

    // Seasons
    public int daysPerSeason = 24;
    /** Where a new world's year starts. */
    public String startSubSeason = SubSeason.EARLY_SPRING.id();
    public boolean paused = false;
    public boolean announceSubSeasons = true;

    // Weather: the share of rain events, in percent, that reach each climate, by sub-season. Dry cells aren't listed.
    public Map<String, Integer> temperateShare = RainShares.defaultRow(Climate.TEMPERATE);
    public Map<String, Integer> hotShare = RainShares.defaultRow(Climate.HOT);
    public Map<String, Integer> coldShare = RainShares.defaultRow(Climate.COLD);
    /** In COLD biomes' early and late summer, rain falls below this many blocks above sea level and snow above it. */
    public int snowLineAboveSea = 48;
    /** The temperature given to warmer biomes when winter brings them snow (vanilla snows below 0.15). */
    public double winterTemperature = 0.0;
    /** The temperature given to COLD biomes when summer brings them rain. */
    public double summerTemperature = 0.5;

    // Snow
    /** Native snow in COLD biomes melts when summer makes them warm, and comes back in fall. */
    public boolean nativeSnowMeltsInSummer = true;
    /**
     * In-game days the season's snow and ice take to melt in loaded chunks when it turns warm; never more than half
     * the sub-season the thaw starts in.
     */
    public double thawDays = 2.0;
    /** Chunks that load behind the season (new, or unloaded while it changed) are brought up to date at once. */
    public boolean snowCatchUp = true;
    /** Chunks brought up to date on load, at most, per tick; the rest wait for the next tick. */
    public int catchUpChunksPerTick = 16;
    /** Columns of loaded chunks that move one layer of snow towards the season per tick. */
    public int snowColumnsPerTick = 128;

    // Colours
    /** Send players without Almanac the seasonal biome copies, so they see seasonal weather and colours. */
    public boolean vanillaClientCopies = true;
    /** Chunks per tick refreshed for each player without Almanac after a sub-season change. */
    public int refreshChunksPerTick = 8;

    // Far terrain
    /**
     * Send players who have Almanac a coarse map of the biomes around them, so mods that draw far terrain (Distant
     * Horizons and Voxy, through LiteLights) can show the season's snow there. Off, clients drop the map.
     */
    public boolean farClimateMap = true;
    /** How far the climate map reaches around each player, in blocks. */
    public int farClimateRadius = 4096;
    /** The side of one climate map cell, in blocks: one of {@link #FAR_CELL_SIZES}. */
    public int farClimateCellSize = 32;
    /** At most this much climate map is sent to each player per tick, in bytes. */
    public int farClimateBytesPerTick = 4096;

    public static ServerConfig get() {
        if (current == null) {
            current = load();
        }
        return current;
    }

    /** Replaces the live settings, for example after an operator saves the settings screen. */
    public static void replace(ServerConfig replacement) {
        current = replacement.validated();
        save();
    }

    public static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    private static ServerConfig load() {
        Path file = file();
        if (Files.isRegularFile(file)) {
            try {
                ServerConfig read = fromJson(Files.readString(file));
                if (read != null) {
                    return read;
                }
                LOGGER.warn("{} is empty; using the defaults for now and leaving the file untouched", file);
            } catch (IOException | RuntimeException failure) {
                LOGGER.warn("Couldn't read {}; using the defaults for now and leaving the file untouched", file, failure);
            }
            return new ServerConfig();
        }
        ServerConfig defaults = new ServerConfig();
        current = defaults;
        save();
        return defaults;
    }

    public static void save() {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            // Write beside the real file first so a crash mid-write can't leave it half written.
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, GSON.toJson(get()));
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException failure) {
            LOGGER.warn("Couldn't save {}", file, failure);
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    /** Parses settings from a file or a client and clamps every value; returns null for unreadable text. */
    public static ServerConfig fromJson(String json) {
        try {
            ServerConfig read = GSON.fromJson(json, ServerConfig.class);
            return read == null ? null : read.validated();
        } catch (JsonParseException invalid) {
            return null;
        }
    }

    public ServerConfig copy() {
        return fromJson(toJson());
    }

    public SubSeason startSubSeason() {
        return SubSeason.byId(startSubSeason).orElse(SubSeason.EARLY_SPRING);
    }

    /** The share of rain events, in percent, that reach a climate in a sub-season; 0 for dry cells. */
    public int share(Climate climate, SubSeason subSeason) {
        if (!RainShares.hasShare(climate, subSeason)) {
            return 0;
        }
        Integer share = shares(climate).get(subSeason.id());
        return share == null ? RainShares.defaultShare(climate, subSeason) : share;
    }

    public Map<String, Integer> shares(Climate climate) {
        return switch (climate) {
            case HOT -> hotShare;
            case TEMPERATE -> temperateShare;
            case COLD -> coldShare;
        };
    }

    /** Keeps every number inside its range and fills in fields an older file lacked. */
    public ServerConfig validated() {
        ServerConfig defaults = new ServerConfig();
        daysPerSeason = Math.clamp(daysPerSeason, MIN_DAYS_PER_SEASON, MAX_DAYS_PER_SEASON);
        startSubSeason = startSubSeason().id();
        temperateShare = validShares(Climate.TEMPERATE, temperateShare);
        hotShare = validShares(Climate.HOT, hotShare);
        coldShare = validShares(Climate.COLD, coldShare);
        snowLineAboveSea = Math.clamp(snowLineAboveSea, MIN_SNOW_LINE, MAX_SNOW_LINE);
        winterTemperature = clamp(winterTemperature, MIN_WINTER_TEMPERATURE, MAX_WINTER_TEMPERATURE, defaults.winterTemperature);
        summerTemperature = clamp(summerTemperature, MIN_SUMMER_TEMPERATURE, MAX_SUMMER_TEMPERATURE, defaults.summerTemperature);
        thawDays = clamp(thawDays, MIN_THAW_DAYS, MAX_THAW_DAYS, defaults.thawDays);
        refreshChunksPerTick = Math.clamp(refreshChunksPerTick, MIN_REFRESH_CHUNKS, MAX_REFRESH_CHUNKS);
        catchUpChunksPerTick = Math.clamp(catchUpChunksPerTick, MIN_CATCH_UP_CHUNKS, MAX_CATCH_UP_CHUNKS);
        snowColumnsPerTick = Math.clamp(snowColumnsPerTick, MIN_SNOW_COLUMNS, MAX_SNOW_COLUMNS);
        farClimateRadius = Math.clamp(farClimateRadius, MIN_FAR_RADIUS, MAX_FAR_RADIUS);
        farClimateCellSize = nearestCellSize(farClimateCellSize);
        farClimateBytesPerTick = Math.clamp(farClimateBytesPerTick, MIN_FAR_BYTES, MAX_FAR_BYTES);
        _credits = CREDITS;
        version = defaults.version;
        return this;
    }

    /** The allowed cell size closest to {@code size}; the smaller one on a tie. */
    static int nearestCellSize(int size) {
        int nearest = FAR_CELL_SIZES.getFirst();
        for (int allowed : FAR_CELL_SIZES) {
            if (Math.abs((long) allowed - size) < Math.abs((long) nearest - size)) {
                nearest = allowed;
            }
        }
        return nearest;
    }

    /** Keeps the cells that have a share, in sub-season order, each between 0 and 100. */
    private static Map<String, Integer> validShares(Climate climate, Map<String, Integer> read) {
        Map<String, Integer> valid = new LinkedHashMap<>();
        for (SubSeason subSeason : SubSeason.values()) {
            if (RainShares.hasShare(climate, subSeason)) {
                Integer share = read == null ? null : read.get(subSeason.id());
                valid.put(subSeason.id(), share == null ? RainShares.defaultShare(climate, subSeason) : Math.clamp(share, 0, 100));
            }
        }
        return valid;
    }

    private static double clamp(double value, double min, double max, double fallback) {
        return Double.isFinite(value) ? Math.clamp(value, min, max) : fallback;
    }
}
