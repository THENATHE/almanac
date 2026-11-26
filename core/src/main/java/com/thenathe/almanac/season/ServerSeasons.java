package com.thenathe.almanac.season;

import com.thenathe.almanac.Almanac;
import com.thenathe.almanac.api.AlmanacEvents;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.climate.ClimateSettings;
import com.thenathe.almanac.climate.ClimateTable;
import com.thenathe.almanac.climate.EventReach;
import com.thenathe.almanac.climate.SeasonalClimate;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.internal.SeasonTick;
import com.thenathe.almanac.internal.Services;
import com.thenathe.almanac.internal.SnowReading;
import com.thenathe.almanac.internal.WeatherTick;
import com.thenathe.almanac.network.ServerNetworking;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The running server's season: the clock, the rain-event count, the {@link ClimateTable} and the overworld's
 * {@link SeasonalClimate}. There is one per server, created when it starts and dropped when it stops. Each tick it
 * hands the weather module the season and the rain shares ({@link WeatherTick}), then the snow module the season and
 * the weather ({@link SeasonTick}).
 */
public final class ServerSeasons {
    private static final Logger LOGGER = LoggerFactory.getLogger(Almanac.MOD_ID);
    private static @Nullable ServerSeasons current;

    private final MinecraftServer server;
    private final SeasonData data;
    /** What the table was built from: changes to either build it again, and go to every player. */
    private ClimateSettings settings;
    private ClimateTable table;
    private SeasonalClimate climate;
    private boolean announcedStart;

    private ServerSeasons(MinecraftServer server, SeasonData data) {
        this.server = server;
        this.data = data;
        this.settings = ClimateSettings.of(ServerConfig.get());
        this.table = buildTable(server, settings);
        this.climate = new SeasonalClimate(table, subSeason(), Set.of());
    }

    public static void serverStarted(MinecraftServer server) {
        SeasonData data = server.overworld().getDataStorage().computeIfAbsent(SeasonData.TYPE);
        ServerSeasons seasons = new ServerSeasons(server, data);
        Services.weather().serverStarted(server);
        // The weather's record knows the season before the snow first reads it.
        Services.weather().serverTicked(server.overworld(), seasons.weatherTick(ServerConfig.get(), seasons.subSeason()));
        Services.snow().seasonStarted(server, seasons.seasonTick(false));
        current = seasons;
        seasons.tick();
        LOGGER.info("Almanac: {} biomes have seasons; it is {}", seasons.table.size(), seasons.subSeason().id());
    }

    private static ClimateTable buildTable(MinecraftServer server, ClimateSettings settings) {
        Registry<Biome> biomes = server.registryAccess().lookupOrThrow(Registries.BIOME);
        return ClimateTable.build(biomes, settings);
    }

    /**
     * The server's tags were loaded again ({@code /reload}, a data pack switched on or off): the climate tags and
     * {@code #almanac:no_seasonal_snow} apply at once.
     */
    public static void tagsReloaded() {
        ServerSeasons seasons = current;
        if (seasons != null) {
            seasons.rebuildTable(seasons.settings);
            seasons.tick();
        }
    }

    private void rebuildTable(ClimateSettings newSettings) {
        settings = newSettings;
        table = buildTable(server, newSettings);
        climate = new SeasonalClimate(table, climate.subSeason(), climate.reached());
        ServerNetworking.sendClimateTable(server, table);
        // Every climate answer may have changed, as at a new sub-season; clients say the same when the table arrives.
        AlmanacEvents.SEASON_CHANGED.invoker().onSeasonChanged(server.overworld(), new AlmanacEvents.Change(true, false, false));
    }

    public static void serverStopped() {
        current = null;
    }

    public static @Nullable ServerSeasons get() {
        return current;
    }

    /** The overworld's seasonal climate, or null for any other level or when no season is running. */
    public static @Nullable SeasonalClimate climate(ServerLevel level) {
        ServerSeasons seasons = current;
        return seasons != null && level == seasons.server.overworld() ? seasons.climate : null;
    }

    public MinecraftServer server() {
        return server;
    }

    public ClimateTable table() {
        return table;
    }

    public SeasonalClimate climate() {
        return climate;
    }

    public SubSeason subSeason() {
        return SeasonClock.subSeasonAt(data.dayOfYear(), data.daysPerSeason());
    }

    public int dayOfYear() {
        return data.dayOfYear();
    }

    public int daysPerSeason() {
        return data.daysPerSeason();
    }

    public int daysLeft() {
        return SeasonClock.daysLeft(data.dayOfYear(), data.daysPerSeason());
    }

    public long rainEvent() {
        return data.rainEvent();
    }

    /** The overworld's snow and ice now, from the snow module; null before its first tick. */
    public @Nullable SnowReading snow() {
        return Services.snow().reading();
    }

    /** The game tick at which far terrain began to show what it shows now; see {@link SeasonData#farSnowChanged}. */
    public long farSnowChanged(int shown, int shownBy013, long gameTime) {
        return data.farSnowChanged(shown, shownBy013, gameTime);
    }

    public void tick() {
        ServerLevel overworld = server.overworld();
        ServerConfig config = ServerConfig.get();
        SubSeason before = climate.subSeason();
        ClimateSettings configured = ClimateSettings.of(config);
        if (!configured.equals(settings)) {
            // From 0.2.0 the temperatures and the snow line apply at once: nothing is built into the biomes.
            rebuildTable(configured);
        }

        data.setClock(SeasonClock.step(data.clock(), overworld.getOverworldClockTime(), config.daysPerSeason,
                config.startSubSeason(), config.paused));
        boolean regional = config.regionalWeather;
        // With regional weather vanilla's own weather is held clear (ServerLevelMixin), so the rain-event count stays.
        boolean raining = !regional && overworld.getWeatherData().isRaining();
        data.setRaining(raining);

        SubSeason now = subSeason();
        Services.weather().serverTicked(overworld, weatherTick(config, now));
        Set<Climate> reached = regional ? climatesStormsReach(config, now) : reachedClimates(config, now);
        if (now != before || !reached.equals(climate.reached())) {
            climate = new SeasonalClimate(table, now, reached);
        }
        Services.snow().seasonTicked(overworld, seasonTick(raining));
        if (now != before && announcedStart) {
            subSeasonChanged(now, config);
        }
        announcedStart = true;
        ServerNetworking.syncWeatherIfChanged(server);
        ServerNetworking.syncSeasonIfChanged(server);
    }

    /** Jumps to the first day of a sub-season. */
    public void setSubSeason(SubSeason subSeason) {
        data.setDay(SeasonClock.startOf(subSeason, data.daysPerSeason()), data.daysPerSeason());
        tick();
    }

    /**
     * What the snow follows this tick: the climate now, whether it is raining, which weather decides the rain, and
     * what the climate table was built from. With regional weather off the rain is vanilla's one weather for the whole
     * overworld; with it on the storms decide, which the snow asks the weather module about.
     */
    private SeasonTick seasonTick(boolean raining) {
        int weatherVersion = ServerConfig.get().regionalWeather ? Services.weather().mathsVersion() : 0;
        return new SeasonTick(climate, raining, weatherVersion, settings.fingerprint(), table.noSeasonalSnowFingerprint());
    }

    /**
     * What the weather module records this tick: the sub-season, its rain shares, the share of time under rain, and
     * whether the weather moves.
     */
    private WeatherTick weatherTick(ServerConfig config, SubSeason subSeason) {
        boolean advancing = server.overworld().getGameRules().get(GameRules.ADVANCE_WEATHER);
        return new WeatherTick(subSeason, config.share(Climate.HOT, subSeason), config.share(Climate.TEMPERATE, subSeason),
                config.share(Climate.COLD, subSeason), config.timeUnderRain, config.regionalWeather, advancing);
    }

    /**
     * With regional weather, the climates storms may reach this sub-season: those with a share above 0. Which storm
     * reaches which climate is the storm's own draw against the share (the weather module), so this is what the
     * climate's {@code reached} means then: whether rain or snow can come at all.
     */
    private static Set<Climate> climatesStormsReach(ServerConfig config, SubSeason subSeason) {
        Set<Climate> reached = EnumSet.noneOf(Climate.class);
        for (Climate climate : Climate.values()) {
            if (config.share(climate, subSeason) > 0) {
                reached.add(climate);
            }
        }
        return reached;
    }

    private Set<Climate> reachedClimates(ServerConfig config, SubSeason subSeason) {
        double strength = EventReach.strength(server.overworld().getSeed(), data.rainEvent());
        Set<Climate> reached = EnumSet.noneOf(Climate.class);
        for (Climate climate : Climate.values()) {
            if (EventReach.reaches(strength, config.share(climate, subSeason))) {
                reached.add(climate);
            }
        }
        return reached;
    }

    private void subSeasonChanged(SubSeason now, ServerConfig config) {
        LOGGER.info("Almanac: {} has begun", now.id());
        if (config.announceSubSeasons) {
            Component message = Component.translatableWithFallback("almanac.announce.sub_season",
                    "%s has begun", SubSeasonNames.name(now));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                player.sendOverlayMessage(message);
            }
        }
    }
}
