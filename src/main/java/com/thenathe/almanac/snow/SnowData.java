package com.thenathe.almanac.snow;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thenathe.almanac.Almanac;
import com.thenathe.almanac.season.SavedFields;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A dimension's {@link SnowLog}, kept in its saved data ({@code data/almanac/snow.dat}). A world without one (new, or
 * from before this version) starts a fresh log the first time the season runs: no snow until it snows. Fields are read
 * one by one ({@link SavedFields}), so a damaged one doesn't throw the snow history away.
 */
public final class SnowData extends SavedData {
    private static final Logger LOGGER = LoggerFactory.getLogger(Almanac.MOD_ID);
    private static final int FORMAT = 1;
    private static final long NOT_STARTED = Long.MIN_VALUE;
    static final String FILE = "snow.dat";

    private record Saved(int format, long started, SnowLog log) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                SavedFields.optional(FILE, "format", Codec.INT, FORMAT).forGetter(Saved::format),
                SavedFields.optional(FILE, "started", Codec.LONG, NOT_STARTED).forGetter(Saved::started),
                SavedFields.optional(FILE, "log", SnowLog.CODEC, new SnowLog(0L)).forGetter(Saved::log)
        ).apply(instance, Saved::new));
    }

    public static final Codec<SnowData> CODEC = Saved.CODEC.xmap(SnowData::new, SnowData::toSaved);
    public static final SavedDataType<SnowData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "snow"), SnowData::new, CODEC, null);

    private long started = NOT_STARTED;
    private SnowLog log = new SnowLog(0L);
    private boolean timesChecked;

    public SnowData() {
    }

    private SnowData(Saved saved) {
        log = saved.log();
        // A log with weather in it has started, even if the field saying so was lost.
        boolean hasWeather = !log.segments().isEmpty();
        started = saved.started() == NOT_STARTED && hasWeather ? log.checkpointTick() : saved.started();
    }

    private Saved toSaved() {
        return new Saved(FORMAT, started, log);
    }

    /**
     * The log, started at {@code gameTime} if this world has none yet. A log from another world (a copied
     * {@code snow.dat}) can be ahead of this world's game time, which would hold the snow depths still until the game
     * time caught up; the first time it is asked for, such a log is moved back to end now.
     */
    public SnowLog log(long gameTime) {
        if (started == NOT_STARTED) {
            started = gameTime;
            log = new SnowLog(gameTime);
            timesChecked = true;
            setDirty();
        } else if (!timesChecked) {
            timesChecked = true;
            long ahead = log.latestTick() - gameTime;
            if (ahead > 0) {
                LOGGER.warn("Almanac: the weather log in {} is {} ticks ahead of this world's game time; moving it back",
                        FILE, ahead);
                log.moveBack(ahead);
                started -= ahead;
                setDirty();
            }
        }
        return log;
    }

    public void changed() {
        setDirty();
    }
}
