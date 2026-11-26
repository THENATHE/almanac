package com.thenathe.almanac.weather;

import com.thenathe.almanac.internal.AlmanacModule;
import com.thenathe.almanac.internal.Services;
import com.thenathe.almanac.internal.Weather;
import com.thenathe.almanac.internal.WeatherHere;
import com.thenathe.almanac.internal.WeatherRecord;
import com.thenathe.almanac.internal.WeatherTick;
import com.thenathe.almanac.internal.WeatherView;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The weather module: its entrypoint, and its side of the internal API. On the server it keeps the overworld's
 * weather record and the storm index; on a client, the record the server sent and an index around the camera. In a
 * single-player game both run in the one game, each with its own state, as the server and a client would.
 */
public final class WeatherModule implements AlmanacModule, Weather {
    /**
     * Almanac's one background thread for the weather maths, shared by the server's and the client's work and by the
     * snow module's areas: lowest priority, a daemon, so it never holds up the game or its closing.
     */
    private static final ExecutorService BACKGROUND = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Almanac weather");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });

    private static volatile @Nullable ServerWeather server;
    private static volatile @Nullable ClientWeather client;
    private static final CellCache CLIENT_CELLS = new CellCache();

    static Executor worker() {
        return BACKGROUND;
    }

    /**
     * The weather worked out from a record, for tools and tests outside the module (the pictures of the storms, the
     * snow module's tests); the game itself asks {@code Services.weather()}.
     */
    public static WeatherView viewOf(WeatherRecord record) {
        return new WeatherField(record, new CellCache());
    }

    /** The weather seed of a world seed: what clients are sent in its place. */
    public static long weatherSeed(long worldSeed) {
        return Hashes.weatherSeed(worldSeed);
    }

    @Override
    public void initialize() {
        Services.installWeather(this);
    }

    @Override
    public int mathsVersion() {
        return StormMathsV2.VERSION;
    }

    @Override
    public void serverStarted(MinecraftServer minecraftServer) {
        server = new ServerWeather(minecraftServer);
    }

    @Override
    public void serverTicked(ServerLevel overworld, WeatherTick tick) {
        ServerWeather weather = server;
        if (weather != null) {
            weather.tick(overworld, tick, mathsVersion());
        }
    }

    @Override
    public void serverStopped() {
        server = null;
    }

    @Override
    public @Nullable WeatherView view(Level level) {
        if (level.dimension() != Level.OVERWORLD) {
            return null;
        }
        if (level.isClientSide()) {
            ClientWeather weather = client;
            return weather == null ? null : weather.field();
        }
        ServerWeather weather = server;
        return weather == null ? null : weather.field();
    }

    @Override
    public boolean active(Level level) {
        WeatherView view = view(level);
        return view != null && view.regional(level.getGameTime());
    }

    @Override
    public WeatherHere at(Level level, double x, double y, double z) {
        if (level.dimension() != Level.OVERWORLD) {
            return WeatherHere.CLEAR;
        }
        long gameTick = level.getGameTime();
        if (level.isClientSide()) {
            ClientWeather weather = client;
            return weather == null ? WeatherHere.CLEAR : weather.at(x, y, z, gameTick);
        }
        ServerWeather weather = server;
        return weather == null ? WeatherHere.CLEAR : weather.at(x, y, z, gameTick);
    }

    @Override
    public void addEvent(ServerLevel level, WeatherRecord.Event event) {
        ServerWeather weather = server;
        if (weather != null) {
            weather.addEvent(event);
        }
    }

    @Override
    public @Nullable WeatherRecord serverRecord() {
        ServerWeather weather = server;
        return weather == null ? null : weather.record();
    }

    @Override
    public long serverRecordChanges() {
        ServerWeather weather = server;
        return weather == null ? -1 : weather.changes();
    }

    @Override
    public void clientReceived(WeatherRecord record) {
        ClientWeather previous = client;
        if (previous != null && previous.field().seed() != record.seed()) {
            CLIENT_CELLS.clear();
        }
        client = new ClientWeather(record, CLIENT_CELLS);
    }

    @Override
    public void clientTicked(Level level, double cameraX, double cameraZ) {
        ClientWeather weather = client;
        if (weather != null && level.dimension() == Level.OVERWORLD) {
            weather.tick(level.getGameTime(), cameraX, cameraZ);
        }
    }

    @Override
    public void clientDisconnected() {
        client = null;
        CLIENT_CELLS.clear();
    }

    @Override
    public Executor background() {
        return BACKGROUND;
    }

    @Override
    public String describe(Level level) {
        if (level.isClientSide()) {
            ClientWeather weather = client;
            return weather == null ? "no weather record from the server" : weather.describe();
        }
        ServerWeather weather = server;
        return weather == null ? "no season running" : weather.describe();
    }
}
