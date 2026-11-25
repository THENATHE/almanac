package com.thenathe.almanac.internal;

import java.util.concurrent.Executor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * The weather module's side of the internal API: regional weather, its record and the storms worked out from it, on
 * the server and on a client. Server calls belong on the server thread; {@link #view} and {@link #at} are safe from
 * any thread.
 */
public interface Weather {
    /** The storm maths this Almanac uses for new weather. */
    int mathsVersion();

    /** The season has started: reads {@code weather.dat}. */
    void serverStarted(MinecraftServer server);

    /** Once a server tick, before the snow: records the season and the shares, and keeps the storm index fresh. */
    void serverTicked(ServerLevel overworld, WeatherTick tick);

    void serverStopped();

    /** The overworld's weather as this side knows it now; null for any other level and while none is known. */
    @Nullable WeatherView view(Level level);

    /**
     * Whether regional weather decides the rain in a level now. False outside the overworld, with it off, and on a
     * client until the server has sent its record.
     */
    boolean active(Level level);

    /** The weather over a column now, read from the storms near the players (or worked out directly elsewhere). */
    WeatherHere at(Level level, double x, double y, double z);

    /** Records what an operator's {@code /weather} did; it goes to every client with the record. */
    void addEvent(ServerLevel level, WeatherRecord.Event event);

    /** The server's record, for the weather sync; null while no season runs. */
    @Nullable WeatherRecord serverRecord();

    /** Counts up whenever the server's record changes, so core knows to send it again. */
    long serverRecordChanges();

    /** A client received the server's record. */
    void clientReceived(WeatherRecord record);

    /** Once a client tick: keeps the client's storm index around the camera fresh. */
    void clientTicked(Level level, double cameraX, double cameraZ);

    void clientDisconnected();

    /** Almanac's background thread for weather maths, shared by the modules; lowest priority, never the server's. */
    Executor background();

    /** A few lines for {@code /almanac weather}: the index, its cost, the record. */
    String describe(Level level);
}
