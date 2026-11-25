package com.thenathe.almanac.snow;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thenathe.almanac.internal.AlmanacModule;
import com.thenathe.almanac.internal.SavedFields;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A dimension's {@link SnowLog}, kept in its saved data ({@code data/almanac/snow.dat}). A world without one (new, or
 * from before Almanac) starts a fresh log the first time the season runs: no snow until it snows. Fields are read
 * one by one ({@link SavedFields}), so a damaged one doesn't throw the snow history away.
 *
 * <p>Format 2 (0.3.0) adds the clocks to each segment and the ice to the checkpoint ({@link SnowLog}), and
 * {@code legacy_max_layers}: for a world first saved by an older Almanac, the snow height game rule when this version
 * first opened it. Until then the game rule was how deep the season's snow could lie, so the log's older segments are
 * read with it, and the world's snow may keep building that deep if the rule was raised above Almanac's own setting
 * ({@link #keptMaxLayers}).
 */
public final class SnowData extends SavedData {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlmanacModule.MOD_ID);
    private static final int FORMAT = 2;
    private static final int FIRST_FORMAT = 1;
    private static final long NOT_STARTED = Long.MIN_VALUE;
    /** {@code legacy_max_layers} of a world this version or a later one started: there is nothing older to read. */
    private static final int NO_LEGACY = 0;
    /** {@code legacy_max_layers} of an older world not yet opened by this version. */
    private static final int LEGACY_UNKNOWN = -1;
    static final String FILE = "snow.dat";

    private record Saved(int format, long started, SnowLog log, int legacyMaxLayers) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                // A file without a format was written before the field was: by the first format.
                SavedFields.optional(FILE, "format", Codec.INT, FIRST_FORMAT).forGetter(Saved::format),
                SavedFields.optional(FILE, "started", Codec.LONG, NOT_STARTED).forGetter(Saved::started),
                SavedFields.optional(FILE, "log", SnowLog.CODEC, new SnowLog(0L)).forGetter(Saved::log),
                SavedFields.optional(FILE, "legacy_max_layers", Codec.INT, LEGACY_UNKNOWN).forGetter(Saved::legacyMaxLayers)
        ).apply(instance, Saved::new));
    }

    public static final Codec<SnowData> CODEC = Saved.CODEC.xmap(SnowData::new, SnowData::toSaved);
    public static final SavedDataType<SnowData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(AlmanacModule.MOD_ID, "snow"), SnowData::new, CODEC, null);

    private long started = NOT_STARTED;
    private SnowLog log = new SnowLog(0L);
    private int legacyMaxLayers = NO_LEGACY;
    private boolean timesChecked;

    public SnowData() {
    }

    private SnowData(Saved saved) {
        log = saved.log();
        // A log with weather in it has started, even if the field saying so was lost.
        boolean hasWeather = !log.segments().isEmpty();
        started = saved.started() == NOT_STARTED && hasWeather ? log.checkpointTick() : saved.started();
        if (saved.legacyMaxLayers() > 0) {
            legacyMaxLayers = saved.legacyMaxLayers();
        } else if (saved.format() < FORMAT && started != NOT_STARTED) {
            legacyMaxLayers = LEGACY_UNKNOWN;
        }
        if (legacyMaxLayers > 0) {
            log.setLegacyMaxLayers(legacyMaxLayers);
        }
    }

    private Saved toSaved() {
        return new Saved(FORMAT, started, log, legacyMaxLayers == LEGACY_UNKNOWN ? NO_LEGACY : legacyMaxLayers);
    }

    /**
     * The log, started at {@code gameTime} if this world has none yet. A log from another world (a copied
     * {@code snow.dat}) can be ahead of this world's game time, which would hold the snow depths still until the game
     * time caught up; the first time it is asked for, such a log is moved back to end now.
     *
     * @param snowHeight the snow height game rule now, which a log from before 0.3.0 was read with
     */
    public SnowLog log(long gameTime, int snowHeight) {
        if (started == NOT_STARTED) {
            started = gameTime;
            log = new SnowLog(gameTime);
            timesChecked = true;
            setDirty();
        } else if (!timesChecked) {
            timesChecked = true;
            if (legacyMaxLayers == LEGACY_UNKNOWN) {
                legacyMaxLayers = Math.clamp(snowHeight, 1, 8);
                log.setLegacyMaxLayers(legacyMaxLayers);
                LOGGER.info("Almanac: {} was saved by an older Almanac; its snow so far is read as it was then, up to "
                        + "{} layers deep (the snow height game rule)", FILE, legacyMaxLayers);
                setDirty();
            }
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

    /**
     * How deep this world's snow may build at least, whatever Almanac's setting: the snow height game rule an older
     * Almanac let it build to, or 0 for a world this version started. A world whose rule was raised keeps its depth.
     */
    public int keptMaxLayers() {
        return Math.max(NO_LEGACY, legacyMaxLayers);
    }

    public void changed() {
        setDirty();
    }
}
