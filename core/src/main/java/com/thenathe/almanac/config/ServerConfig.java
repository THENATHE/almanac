package com.thenathe.almanac.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.climate.RainShares;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

/**
 * Server settings. The server owns them: they live in {@code config/almanac-server.json}, operators change them from
 * the settings screen or with {@code /almanac config}, and clients receive a copy.
 *
 * <p>Field names are part of the file and of the settings sync, so they never change; a file written by an older
 * version fills its missing fields from the defaults. A field that is no longer a setting is ignored when read and
 * left out the next time the file is written: {@code thawDays} (0.1.2 to 0.1.6), which has paced nothing since
 * 0.1.6, and {@code vanillaClientCopies} and {@code refreshChunksPerTick} (0.1.0 to 0.1.7), which served players
 * without Almanac. Every setting applies at once; until 0.1.7 {@link #winterTemperature}, {@link #summerTemperature}
 * and {@link #snowLineAboveSea} were built into the seasonal biome copies and applied after a restart.
 *
 * <p>The file is read when the game starts and again each time a server loads its world, so hand edits count from
 * the next world (or {@code /almanac config reload}). A file that doesn't read is set aside ({@link SettingsFile}).
 */
public final class ServerConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILE_NAME = "almanac-server.json";
    /** Written by the server itself; never part of a change. */
    private static final Set<String> NOT_SETTINGS = Set.of("version", "_credits");
    private static final SettingsFile<ServerConfig> FILE = new SettingsFile<>(
            () -> FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME), ServerConfig::fromJson, ServerConfig::toJson);
    public static final String CREDITS = "Almanac is new code. Idea by Fabric Seasons (lucaargolo): biome edits in memory and "
            + "tracking weather-placed snow. Idea by Serene Seasons (Glitchfiend): sub-seasons and rain timing. Idea by "
            + "Immersive Snow Reloaded (Straywave and contributors): snow recalculation on chunk load. Idea by Create: Sky: "
            + "regional weather with travelling storms, nothing falling above a storm's cloud top, a local /weather. Idea "
            + "by Climate: Clouds, Storms, Technology: regional, moving weather. Idea by ProtoManly's Weather: regional, "
            + "moving weather; storms worked out alike on the server and every client, from forming to fading; snow "
            + "caught up from the storms that passed. Idea by Simple Clouds: clouds grouped where the weather is. No "
            + "code, data or wording is copied from any of them.";

    public static final int MIN_DAYS_PER_SEASON = 3;
    public static final int MAX_DAYS_PER_SEASON = 1200;
    public static final int MIN_SNOW_LINE = 18;
    public static final int MAX_SNOW_LINE = 400;
    // Winter stays below vanilla's snow line (0.15) and summer above it, which the snow depth relies on
    // (ColdRulesAgreeTest).
    public static final double MIN_WINTER_TEMPERATURE = -1.0;
    public static final double MAX_WINTER_TEMPERATURE = 0.1;
    public static final double MIN_SUMMER_TEMPERATURE = 0.2;
    public static final double MAX_SUMMER_TEMPERATURE = 2.0;
    public static final int MIN_SNOW_COLUMNS = 1;
    public static final int MAX_SNOW_COLUMNS = 4096;
    public static final int MIN_CATCH_UP_CHUNKS = 1;
    public static final int MAX_CATCH_UP_CHUNKS = 256;
    public static final int MIN_CLOCK_TICKS = 200;
    public static final int MAX_CLOCK_TICKS = 480_000;
    public static final int MIN_SNOW_LAYERS = 1;
    public static final int MAX_SNOW_LAYERS = 8;
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

    // Weather
    /**
     * Regional weather (from 0.4.0): storms that form, move across the land and pass, so it rains or snows in one
     * place and not in another, each place under rain about as often as in vanilla. Off, vanilla's one weather for the
     * whole world, as up to 0.3.0.
     */
    public boolean regionalWeather = true;
    /**
     * With regional weather, {@code /weather} acts on the whole world instead of around whoever runs it (a storm over
     * them, or a clear sky for some way around).
     */
    public boolean weatherCommandEverywhere = false;
    // The share of rain events (with regional weather, of storms), in percent, that reach each climate, by sub-season.
    // Dry cells aren't listed.
    public Map<String, Integer> temperateShare = RainShares.defaultRow(Climate.TEMPERATE);
    public Map<String, Integer> hotShare = RainShares.defaultRow(Climate.HOT);
    public Map<String, Integer> coldShare = RainShares.defaultRow(Climate.COLD);
    /**
     * Which default rain shares this file was last brought to: 0 before 0.1.4 (the field is missing), 1 from 0.1.4,
     * where every share defaults to 100. A file at 0 has each cell still at its old default moved to 100 once; cells
     * a player set to anything else keep their value.
     */
    public int rainSharesRevision;
    static final int RAIN_SHARES_REVISION = 1;
    /** In COLD biomes' early and late summer, rain falls below this many blocks above sea level and snow above it. */
    public int snowLineAboveSea = 48;
    /** The temperature given to warmer biomes when winter brings them snow (vanilla snows below 0.15). */
    public double winterTemperature = 0.0;
    /** The temperature given to COLD biomes when summer brings them rain. */
    public double summerTemperature = 0.5;

    // Snow
    /** The snowfall clock: ticks for one layer of snow to build while it snows (from 0.3.0). */
    public int snowfallTicks = 12_000;
    /** The thaw clock: ticks for one layer of snow, or a pond's ice, to melt while it is warm. */
    public int thawTicks = 18_000;
    /** The ice clock: ticks for still water to freeze over while it is cold; 0 follows the snowfall clock. */
    public int iceTicks = 0;
    /** How deep the season's snow may build, in layers; a world an older Almanac let build deeper keeps its depth. */
    public int maxSnowLayers = 3;
    /** Native snow in COLD biomes melts when summer makes them warm, and comes back in fall. */
    public boolean nativeSnowMeltsInSummer = true;
    /** Chunks that load behind the season (new, or unloaded while it changed) are brought up to date at once. */
    public boolean snowCatchUp = true;
    /** Chunks brought up to date on load, at most, per tick; the rest wait for the next tick. */
    public int catchUpChunksPerTick = 16;
    /** Columns of loaded chunks brought to the season's snow per tick; columns with nothing to change count a sixteenth. */
    public int snowColumnsPerTick = 128;

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
        // A replacement built from a client's changes may not carry the revision; it never goes back.
        replacement.rainSharesRevision = Math.max(replacement.rainSharesRevision, current == null ? RAIN_SHARES_REVISION : current.rainSharesRevision);
        current = replacement.validated();
        save();
    }

    /** Reads the file again, as a server loading its world does; a missing or unreadable one gives the defaults. */
    public static void reload() {
        current = load();
    }

    /**
     * Reads the file again for {@code /almanac config reload} without touching the live settings: the caller applies
     * them. A file that doesn't read has been set aside by then.
     */
    public static SettingsFile.Reading<ServerConfig> readFile() {
        return FILE.read();
    }

    public static Path file() {
        return FILE.path();
    }

    private static ServerConfig load() {
        if (FILE.read() instanceof SettingsFile.Reading.Read<ServerConfig> read) {
            ServerConfig settings = read.settings();
            if (settings.moveRainSharesTo014()) {
                current = settings;
                save();
            }
            return settings;
        }
        // No file, or it was set aside: start one with the defaults, as a fresh install does.
        ServerConfig defaults = new ServerConfig();
        defaults.rainSharesRevision = RAIN_SHARES_REVISION;
        current = defaults;
        save();
        return defaults;
    }

    public static void save() {
        FILE.save(get());
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

    /**
     * The settings that differ between {@code before} and {@code after}, as a JSON object of just those fields; a rain
     * share row holds just the sub-seasons that differ. What the settings screen sends, so a save only touches what
     * the player changed.
     */
    public static JsonObject changes(ServerConfig before, ServerConfig after) {
        JsonObject old = GSON.toJsonTree(before).getAsJsonObject();
        JsonObject changes = new JsonObject();
        for (Map.Entry<String, JsonElement> field : GSON.toJsonTree(after).getAsJsonObject().entrySet()) {
            String name = field.getKey();
            JsonElement value = field.getValue();
            JsonElement was = old.get(name);
            if (NOT_SETTINGS.contains(name) || value.equals(was)) {
                continue;
            }
            if (value.isJsonObject() && was != null && was.isJsonObject()) {
                JsonObject changedEntries = new JsonObject();
                for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                    if (!entry.getValue().equals(was.getAsJsonObject().get(entry.getKey()))) {
                        changedEntries.add(entry.getKey(), entry.getValue());
                    }
                }
                changes.add(name, changedEntries);
            } else {
                changes.add(name, value);
            }
        }
        return changes;
    }

    /**
     * {@code base} with {@code changesJson} (from {@link #changes}) applied on top, every value checked as for a file;
     * fields the changes don't name keep {@code base}'s values. Null if the changes aren't a JSON object.
     */
    public static @Nullable ServerConfig withChanges(ServerConfig base, String changesJson) {
        try {
            JsonElement parsed = JsonParser.parseString(changesJson);
            if (!parsed.isJsonObject()) {
                return null;
            }
            JsonObject merged = GSON.toJsonTree(base).getAsJsonObject();
            for (Map.Entry<String, JsonElement> change : parsed.getAsJsonObject().entrySet()) {
                String name = change.getKey();
                JsonElement value = change.getValue();
                JsonElement was = merged.get(name);
                if (NOT_SETTINGS.contains(name)) {
                    continue;
                }
                if (value.isJsonObject() && was != null && was.isJsonObject()) {
                    value.getAsJsonObject().entrySet().forEach(entry -> was.getAsJsonObject().add(entry.getKey(), entry.getValue()));
                } else {
                    merged.add(name, value);
                }
            }
            ServerConfig read = GSON.fromJson(merged, ServerConfig.class);
            return read == null ? null : read.validated();
        } catch (JsonParseException | IllegalStateException invalid) {
            return null;
        }
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

    /**
     * Brings a file from before 0.1.4 to its rain shares: each cell still at its old default goes to 100, the new
     * default; cells a player set to anything else keep their value. Done once, on load. Returns whether anything
     * changed, so the file is written back.
     */
    boolean moveRainSharesTo014() {
        if (rainSharesRevision >= RAIN_SHARES_REVISION) {
            return false;
        }
        for (Climate climate : Climate.values()) {
            Map<String, Integer> shares = shares(climate);
            for (SubSeason subSeason : SubSeason.values()) {
                Integer share = shares.get(subSeason.id());
                if (share != null && share == RainShares.before014(climate, subSeason)) {
                    shares.put(subSeason.id(), RainShares.defaultShare(climate, subSeason));
                }
            }
        }
        rainSharesRevision = RAIN_SHARES_REVISION;
        return true;
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
        snowfallTicks = Math.clamp(snowfallTicks, MIN_CLOCK_TICKS, MAX_CLOCK_TICKS);
        thawTicks = Math.clamp(thawTicks, MIN_CLOCK_TICKS, MAX_CLOCK_TICKS);
        iceTicks = iceTicks <= 0 ? 0 : Math.clamp(iceTicks, MIN_CLOCK_TICKS, MAX_CLOCK_TICKS);
        maxSnowLayers = Math.clamp(maxSnowLayers, MIN_SNOW_LAYERS, MAX_SNOW_LAYERS);
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
