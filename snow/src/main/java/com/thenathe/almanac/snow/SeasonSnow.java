package com.thenathe.almanac.snow;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SnowClocks;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.SeasonTick;
import com.thenathe.almanac.internal.Services;
import com.thenathe.almanac.internal.SnowReading;
import com.thenathe.almanac.internal.SnowSettings;
import com.thenathe.almanac.internal.WeatherView;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.Nullable;

/**
 * The running season's snow: the overworld's weather log ({@link SnowData}), each climate's snow depth and ice cover
 * worked out from it every tick, and where snow and ice belong ({@link SnowState}). There is one while the season
 * runs, started with it and dropped when the server stops.
 *
 * <p>Under regional weather (from 0.4.0) the depth differs from place to place: the snow nodes near loaded land are
 * worked out on the background thread ({@link SnowNodes}) and each chunk is brought to the depths around it
 * ({@link #stateAt}). The ice cover doesn't depend on the rain, so it stays one per climate. The climate's one state
 * ({@link #state}) then has the deepest snow of each climate on loaded land, and the climate's one reading
 * ({@link #reading}) the snow at the world's spawn.
 */
final class SeasonSnow {
    private static @Nullable SeasonSnow current;

    private final SnowData snow;
    private final SnowNodes nodes = new SnowNodes();
    private final LongSet wanted = new LongOpenHashSet();
    private SnowState state;
    private SnowReading reading;
    private @Nullable SnowField field;
    private long fieldTick;
    private boolean regional;
    private int spawnX;
    private int spawnZ;
    private long logChanges;
    /** The log as it was at its last change, for the clients. */
    private @Nullable SnowLog logCopy;
    private long copiedAt = -1;

    private SeasonSnow(SnowData snow, SeasonTick start) {
        this.snow = snow;
        SnowClocks clocks = clocks(Services.seasons().snowSettings());
        this.state = new SnowState(start.climate().subSeason(), Map.of(), Map.of(), clocks.maxLayers(),
                Services.seasons().snowSettings().nativeSnowMelts(), 1, start.climateSettings(), start.noSeasonalSnow());
        this.reading = new SnowReading(Map.of(), Map.of(), Map.of(), Map.of(), clocks);
    }

    static void seasonStarted(MinecraftServer server, SeasonTick start) {
        current = new SeasonSnow(server.overworld().getDataStorage().computeIfAbsent(SnowData.TYPE), start);
    }

    static void serverStopped() {
        current = null;
    }

    static @Nullable SeasonSnow get() {
        return current;
    }

    /** The clocks the settings ask for, before a world's own floor on the most layers. */
    static SnowClocks clocks(SnowSettings settings) {
        int iceTicks = settings.iceTicks() > 0 ? settings.iceTicks() : settings.snowfallTicks();
        return new SnowClocks(settings.snowfallTicks(), settings.thawTicks(), iceTicks, settings.maxLayers());
    }

    /** The snow nodes loaded land needs this tick (around its chunks); the next {@link #update} works them out. */
    void want(LongSet nodesNeeded) {
        wanted.clear();
        wanted.addAll(nodesNeeded);
    }

    /** Logs the weather and works out each climate's snow and the {@link SnowState} for this tick. */
    void update(ServerLevel overworld, SeasonTick now) {
        SubSeason subSeason = now.climate().subSeason();
        long gameTime = overworld.getGameTime();
        int snowHeight = overworld.getGameRules().get(GameRules.MAX_SNOW_ACCUMULATION_HEIGHT);
        SnowSettings settings = Services.seasons().snowSettings();
        SnowLog log = snow.log(gameTime, snowHeight);
        SnowClocks asked = clocks(settings);
        SnowClocks clocks = new SnowClocks(asked.snowfallTicks(), asked.thawTicks(), asked.iceTicks(),
                Math.max(asked.maxLayers(), snow.keptMaxLayers()));
        int foldsBefore = log.approximateFolds();
        if (log.observe(gameTime, subSeason, now.raining(), now.climate().reached(), clocks, now.weatherVersion())) {
            snow.changed();
            logChanges++;
        }
        if (logCopy == null || logChanges != copiedAt) {
            logCopy = log.copy();
            copiedAt = logChanges;
        }
        if (log.approximateFolds() != foldsBefore) {
            nodes.forgetPast();
        }
        Map<Climate, Double> depths = new EnumMap<>(Climate.class);
        Map<Climate, Double> ice = new EnumMap<>(Climate.class);
        Map<Climate, Double> depthPace = new EnumMap<>(Climate.class);
        Map<Climate, Double> icePace = new EnumMap<>(Climate.class);
        for (SnowArea area : SnowArea.all()) {
            SnowLog.Trend trend = log.trend(area, gameTime, RainSource.GLOBAL);
            depths.put(area.climate(), trend.now().depth());
            ice.put(area.climate(), trend.now().ice());
            depthPace.put(area.climate(), trend.depthPerTick());
            icePace.put(area.climate(), trend.icePerTick());
        }
        WeatherView weather = Services.weather().view(overworld);
        regional = now.weatherVersion() > 0 && weather != null;
        BlockPos spawn = overworld.getRespawnData().pos();
        spawnX = spawn.getX();
        spawnZ = spawn.getZ();
        if (regional) {
            LongSet needed = new LongOpenHashSet(wanted);
            SnowField.nodesOf(spawnX >> 4, spawnZ >> 4, needed);
            nodes.refresh(log, weather, needed, gameTime, clocks.maxLayers());
            field = nodes.field();
            fieldTick = gameTime;
            depths = field.deepest(gameTime);
            Map<Climate, Double> atSpawn = field.depthsAt(spawnX, spawnZ, gameTime);
            Map<Climate, Double> spawnPace = field.pacesAt(spawnX, spawnZ, gameTime);
            reading = new SnowReading(atSpawn == null ? Map.of() : atSpawn, ice, spawnPace, icePace, clocks);
        } else {
            field = null;
            reading = new SnowReading(depths, ice, depthPace, icePace, clocks);
        }
        state = new SnowState(subSeason, depths, ice, clocks.maxLayers(), settings.nativeSnowMelts(), snowHeight,
                now.climateSettings(), now.noSeasonalSnow());
    }

    /** Whether the snow differs from place to place: regional weather decides where it falls. */
    boolean regional() {
        return regional;
    }

    /**
     * The world's state: where snow and ice belong right now (see {@link SnowState}). Under regional weather its depths
     * are each climate's deepest on loaded land; a chunk is brought to {@link #stateAt} instead.
     */
    SnowState state() {
        return state;
    }

    /** The newest worked-out snow nodes, and the tick they were read at; null without regional weather. */
    @Nullable SnowField field() {
        return field;
    }

    /** The state a chunk is brought to now: its own depths under regional weather; null while its nodes aren't known. */
    @Nullable SnowState stateAt(int chunkX, int chunkZ, long gameTime) {
        SnowField current = field;
        if (!regional || current == null) {
            return state;
        }
        Map<Climate, Double> depths = current.chunkDepths(chunkX, chunkZ, gameTime);
        return depths == null ? null : state.withDepths(depths);
    }

    /** Each climate's snow and ice now and how they move, for the public API and the season sync: at the spawn. */
    SnowReading reading() {
        return reading;
    }

    /** The snow and ice at a column now and how they move there, for the season sync of the player standing there. */
    SnowReading readingAt(double x, double z) {
        long gameTime = fieldTick;
        SnowField current = field;
        if (!regional || current == null) {
            return reading;
        }
        Map<Climate, Double> depths = current.depthsAt(x, z, gameTime);
        if (depths == null) {
            return reading;
        }
        return new SnowReading(depths, reading.ice(), current.pacesAt(x, z, gameTime), reading.icePerTick(),
                reading.clocks());
    }

    /** Each climate's depth at a column of loaded land now; null elsewhere. Without regional weather, the climate's. */
    @Nullable Map<Climate, Double> depthsAt(double x, double z) {
        SnowField current = field;
        if (!regional || current == null) {
            Map<Climate, Double> depths = new EnumMap<>(Climate.class);
            for (Climate climate : Climate.values()) {
                depths.put(climate, state.depth(climate));
            }
            return depths;
        }
        return current.depthsAt(x, z, fieldTick);
    }

    @Nullable CompoundTag logForClients() {
        return logCopy == null ? null : ClientSnow.forClients(logCopy);
    }

    long logChanges() {
        return logChanges;
    }

    /** The log as it was at its last change; null before the first tick. */
    @Nullable SnowLog loggedCopy() {
        return logCopy;
    }

    String describe() {
        return regional ? nodes.describe() + ", " + wanted.size() + " wanted around loaded land" : "snow by climate";
    }
}
