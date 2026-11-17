package com.thenathe.almanac.snow;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thenathe.almanac.Almanac;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * A dimension's {@link SnowLog}, kept in its saved data ({@code data/almanac/snow.dat}). A world without one (new, or
 * from before this version) starts a fresh log the first time the season runs: no snow until it snows.
 */
public final class SnowData extends SavedData {
    private static final int FORMAT = 1;
    private static final long NOT_STARTED = Long.MIN_VALUE;

    private record Saved(int format, long started, SnowLog log) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("format", FORMAT).forGetter(Saved::format),
                Codec.LONG.optionalFieldOf("started", NOT_STARTED).forGetter(Saved::started),
                SnowLog.CODEC.optionalFieldOf("log", new SnowLog(0L)).forGetter(Saved::log)
        ).apply(instance, Saved::new));
    }

    public static final Codec<SnowData> CODEC = Saved.CODEC.xmap(SnowData::new, SnowData::toSaved);
    public static final SavedDataType<SnowData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "snow"), SnowData::new, CODEC, null);

    private long started = NOT_STARTED;
    private SnowLog log = new SnowLog(0L);

    public SnowData() {
    }

    private SnowData(Saved saved) {
        started = saved.started();
        log = saved.log();
    }

    private Saved toSaved() {
        return new Saved(FORMAT, started, log);
    }

    /** The log, started at {@code gameTime} if this world has none yet. */
    public SnowLog log(long gameTime) {
        if (started == NOT_STARTED) {
            started = gameTime;
            log = new SnowLog(gameTime);
            setDirty();
        }
        return log;
    }

    public void changed() {
        setDirty();
    }
}
