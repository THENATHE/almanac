package com.thenathe.almanac.weather;

import com.thenathe.almanac.internal.AlmanacModule;
import com.thenathe.almanac.internal.WeatherHere;
import com.thenathe.almanac.internal.WeatherRecord;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A client's regional weather: the field worked out from the record the server sent, and an index of the storms
 * around the camera for the questions asked every frame (the rain drawn around the player, is the player in rain).
 * Nothing about the weather comes over the network each tick; the client works it out from the record and the game
 * time, the same way the server does.
 */
final class ClientWeather {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlmanacModule.MOD_ID);
    /** How far around the camera the index reaches, in blocks. */
    static final double INDEX_REACH = 512.0;

    private final WeatherField field;
    private volatile @Nullable StormIndex index;
    private final AtomicBoolean building = new AtomicBoolean();
    private int ticks;

    ClientWeather(WeatherRecord record, CellCache cache) {
        this.field = new WeatherField(record, cache);
    }

    WeatherField field() {
        return field;
    }

    void tick(long gameTick, double cameraX, double cameraZ) {
        StormIndex current = index;
        double weatherTick = field.weatherTick(gameTick);
        boolean stale = current == null || weatherTick > current.toTick() - ServerWeather.INDEX_AHEAD / 2.0
                || !current.covers(cameraX, cameraZ, weatherTick);
        if ((stale || ticks++ % ServerWeather.INDEX_EVERY == 0) && building.compareAndSet(false, true)) {
            try {
                WeatherModule.worker().execute(() -> {
                    try {
                        index = StormIndex.build(field, List.of(new StormIndex.Area(cameraX, cameraZ, INDEX_REACH)),
                                weatherTick, weatherTick + ServerWeather.INDEX_AHEAD);
                    } catch (RuntimeException failure) {
                        LOGGER.error("Almanac: building the client's storm index failed", failure);
                    } finally {
                        building.set(false);
                    }
                });
            } catch (RuntimeException refused) {
                building.set(false);
            }
        }
    }

    WeatherHere at(double x, double y, double z, long gameTick) {
        StormIndex current = index;
        if (current != null && current.covers(x, z, field.weatherTick(gameTick))) {
            return current.at(x, y, z, gameTick);
        }
        return field.at(x, y, z, gameTick);
    }

    String describe() {
        StormIndex current = index;
        WeatherRecord record = field.record();
        return String.format(Locale.ROOT, "client record: %d eras, %d events, %d freezes; index %s",
                record.eras().size(), record.events().size(), record.freezes().size(),
                current == null ? "none yet" : current.squares() + " squares, " + current.storms() + " storms");
    }
}
