package com.thenathe.almanac.season;

import com.thenathe.almanac.Almanac;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.biome.SeasonRefresh;
import com.thenathe.almanac.biome.SeasonalBiomeIndex;
import com.thenathe.almanac.climate.EventReach;
import com.thenathe.almanac.climate.SeasonalClimate;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.network.ServerNetworking;
import com.thenathe.almanac.snow.SnowData;
import com.thenathe.almanac.snow.SnowLog;
import com.thenathe.almanac.snow.SnowRules;
import com.thenathe.almanac.snow.SnowState;
import com.thenathe.almanac.snow.ThawPace;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The running server's season: the clock, the rain-event count and the overworld's {@link SeasonalClimate}.
 * There is one per server, created when it starts and dropped when it stops.
 */
public final class ServerSeasons {
    private static final Logger LOGGER = LoggerFactory.getLogger(Almanac.MOD_ID);
    private static final long TICKS_PER_DAY = 24000L;
    private static @Nullable ServerSeasons current;

    private final MinecraftServer server;
    private final SeasonData data;
    private final SeasonalBiomeIndex index;
    private final SnowData snow;
    private SeasonalClimate climate;
    private SnowState snowState;
    private final Map<Climate, Double> snowDepths = new EnumMap<>(Climate.class);
    private boolean announcedStart;

    private ServerSeasons(MinecraftServer server, SeasonData data, SeasonalBiomeIndex index, SnowData snow) {
        this.server = server;
        this.data = data;
        this.index = index;
        this.snow = snow;
        this.climate = new SeasonalClimate(index, subSeason(), Set.of());
        for (Climate each : Climate.values()) {
            snowDepths.put(each, 0.0);
        }
        this.snowState = new SnowState(subSeason(), Map.of(), ServerConfig.get().nativeSnowMeltsInSummer, 1);
    }

    public static void serverStarted(MinecraftServer server) {
        SeasonalBiomeIndex index = SeasonalBiomeIndex.build(server.registryAccess().lookupOrThrow(Registries.BIOME));
        SeasonData data = server.overworld().getDataStorage().computeIfAbsent(SeasonData.TYPE);
        SnowData snow = server.overworld().getDataStorage().computeIfAbsent(SnowData.TYPE);
        ServerSeasons seasons = new ServerSeasons(server, data, index, snow);
        current = seasons;
        seasons.tick();
        LOGGER.info("Almanac: {} biomes have seasonal copies; it is {}", index.size(), seasons.subSeason().id());
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

    public SeasonalBiomeIndex index() {
        return index;
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

    /** Where snow and ice belong right now; see {@link SnowState}. */
    public SnowState snowState() {
        return snowState;
    }

    /** The overworld's snow depth in a climate, in layers. */
    public double snowDepth(Climate climate) {
        return snowDepths.get(climate);
    }

    /**
     * How long a thaw in loaded chunks takes right now, in game ticks: the {@code thawDays} setting, at most half of
     * the current sub-season.
     */
    public long thawTicks() {
        return ThawPace.duration(ServerConfig.get().thawDays, SeasonClock.lengthOf(subSeason(), data.daysPerSeason()));
    }

    /**
     * The whole layers of snow in each climate when the current or latest thaw time began. Far terrain shows them
     * through thaw time ({@link com.thenathe.almanac.snow.FarSnowRule#shown}).
     */
    public Map<Climate, Integer> layersAtThawStart() {
        Map<Climate, Double> depths = data.thawStartSnow().orElse(snowDepths);
        Map<Climate, Integer> layers = new EnumMap<>(Climate.class);
        for (Climate each : Climate.values()) {
            layers.put(each, SnowLog.layers(depths.get(each), snowState.snowHeight()));
        }
        return layers;
    }

    public void tick() {
        ServerLevel overworld = server.overworld();
        ServerConfig config = ServerConfig.get();
        SubSeason before = climate.subSeason();

        if (data.daysPerSeason() != config.daysPerSeason) {
            data.setDay(SeasonClock.resize(data.dayOfYear(), data.daysPerSeason(), config.daysPerSeason), config.daysPerSeason);
        }
        long clockDay = Math.floorDiv(overworld.getOverworldClockTime(), TICKS_PER_DAY);
        if (data.lastClockDay() == SeasonData.NOT_STARTED) {
            data.setDay(SeasonClock.startOf(config.startSubSeason(), config.daysPerSeason), config.daysPerSeason);
            data.setLastClockDay(clockDay);
        } else if (clockDay != data.lastClockDay()) {
            // Setting the time back doesn't turn the year back; it only moves where the next day starts.
            if (clockDay > data.lastClockDay() && !config.paused) {
                data.setDay(SeasonClock.advance(data.dayOfYear(), clockDay - data.lastClockDay(), data.daysPerSeason()),
                        data.daysPerSeason());
            }
            data.setLastClockDay(clockDay);
        }
        data.setRaining(overworld.getWeatherData().isRaining());

        SubSeason now = subSeason();
        Set<Climate> reached = reachedClimates(config, now);
        if (now != before || !reached.equals(climate.reached())) {
            climate = new SeasonalClimate(index, now, reached);
        }
        updateSnow(overworld, config, now, reached);
        if (SnowRules.isThawTime(now) && (!SnowRules.isThawTime(before) || data.thawStartSnow().isEmpty())) {
            data.setThawStartSnow(thawStartDepths(overworld, !SnowRules.isThawTime(before)));
        }
        if (now != before && announcedStart) {
            subSeasonChanged(now, config);
        }
        announcedStart = true;
        ServerNetworking.syncSeasonIfChanged(server);
    }

    /** Jumps to the first day of a sub-season. */
    public void setSubSeason(SubSeason subSeason) {
        data.setDay(SeasonClock.startOf(subSeason, data.daysPerSeason()), data.daysPerSeason());
        tick();
    }

    /** Logs the weather and works out the snow depths and the {@link SnowState} for this tick. */
    private void updateSnow(ServerLevel overworld, ServerConfig config, SubSeason subSeason, Set<Climate> reached) {
        long gameTime = overworld.getGameTime();
        int snowHeight = overworld.getGameRules().get(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT);
        SnowLog log = snow.log(gameTime);
        if (log.observe(gameTime, subSeason, overworld.getWeatherData().isRaining(), reached, snowHeight)) {
            snow.changed();
        }
        Map<Climate, Integer> layers = new EnumMap<>(Climate.class);
        for (Climate each : Climate.values()) {
            double depth = log.depth(each, gameTime, snowHeight);
            snowDepths.put(each, depth);
            layers.put(each, SnowLog.layers(depth, snowHeight));
        }
        snowState = new SnowState(subSeason, layers, config.nativeSnowMeltsInSummer, snowHeight);
    }

    /**
     * The snow depths when thaw time began. When it began this tick they are the depths now. A world from before
     * 0.1.2 that is already in thaw time never saved them, so they come from the weather log when it still reaches
     * back to the start of thaw time, or else from the depths now, which can only have melted since.
     */
    private Map<Climate, Double> thawStartDepths(ServerLevel overworld, boolean beganNow) {
        if (beganNow) {
            return Map.copyOf(snowDepths);
        }
        int snowHeight = overworld.getGameRules().get(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT);
        SnowLog log = snow.log(overworld.getGameTime());
        OptionalLong began = log.startOfLatestRun(SnowRules::isThawTime);
        Map<Climate, Double> depths = new EnumMap<>(Climate.class);
        for (Climate each : Climate.values()) {
            depths.put(each, began.isPresent() ? log.depth(each, began.getAsLong(), snowHeight) : snowDepths.get(each));
        }
        LOGGER.info("Almanac: this world has no saved snow from the start of {}; using {} ({})", subSeason().id(), depths,
                began.isPresent() ? "from the weather log" : "the snow now");
        return depths;
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
        SeasonRefresh.refreshAll(server);
    }
}
