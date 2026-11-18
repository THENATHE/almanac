package com.thenathe.almanac.season;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thenathe.almanac.Almanac;
import com.thenathe.almanac.api.Climate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The world's place in the year and its rain-event count, kept in the overworld's saved data
 * ({@code data/almanac/season.dat}). Fields are only ever added, each with a default, so older saves keep loading, and
 * each is read on its own ({@link SavedFields}), so a damaged one doesn't reset the year.
 */
public final class SeasonData extends SavedData {
    private static final int FORMAT = 1;
    private static final String FILE = "season.dat";
    public static final long NOT_STARTED = SeasonClock.NOT_STARTED;
    private static final long UNKNOWN_TICK = -1L;

    private record Saved(int format, int dayOfYear, int daysPerSeason, long lastClockDay, long rainEvent, boolean wasRaining,
                         Map<String, Double> thawStartSnow, int farSnowShown, long farSnowChanged,
                         boolean farSnowByRule) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                SavedFields.optional(FILE, "format", Codec.INT, FORMAT).forGetter(Saved::format),
                SavedFields.optional(FILE, "day_of_year", Codec.INT, 0).forGetter(Saved::dayOfYear),
                SavedFields.optional(FILE, "days_per_season", Codec.INT, 24).forGetter(Saved::daysPerSeason),
                SavedFields.optional(FILE, "last_clock_day", Codec.LONG, NOT_STARTED).forGetter(Saved::lastClockDay),
                SavedFields.optional(FILE, "rain_event", Codec.LONG, 0L).forGetter(Saved::rainEvent),
                SavedFields.optional(FILE, "was_raining", Codec.BOOL, false).forGetter(Saved::wasRaining),
                // Added in 0.1.2; worlds from 0.1.0 and 0.1.1 have none and work it out again (see ServerSeasons).
                SavedFields.optional(FILE, "thaw_start_snow", Codec.unboundedMap(Codec.STRING, Codec.DOUBLE), Map.of())
                        .forGetter(Saved::thawStartSnow),
                // Added in 0.1.3; older worlds count their first far-terrain state as a change.
                SavedFields.optional(FILE, "far_snow_shown", Codec.INT, 0).forGetter(Saved::farSnowShown),
                SavedFields.optional(FILE, "far_snow_changed", Codec.LONG, UNKNOWN_TICK).forGetter(Saved::farSnowChanged),
                // Added in 0.1.3.1: far_snow_shown is the far rule's fingerprint rather than 0.1.3's, which counted
                // every whole layer of snow.
                SavedFields.optional(FILE, "far_snow_by_rule", Codec.BOOL, false).forGetter(Saved::farSnowByRule)
        ).apply(instance, Saved::new));
    }

    public static final Codec<SeasonData> CODEC = Saved.CODEC.xmap(SeasonData::new, SeasonData::toSaved);
    public static final SavedDataType<SeasonData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "season"), SeasonData::new, CODEC, null);

    private int dayOfYear;
    private int daysPerSeason = 24;
    private long lastClockDay = NOT_STARTED;
    private long rainEvent;
    private boolean wasRaining;
    /** The snow depth of each climate when the current or latest thaw time began; empty when it isn't known. */
    private Map<Climate, Double> thawStartSnow = Map.of();
    /** What far terrain showed ({@code FarClimatePayloads.State#farRuleFingerprint}) and the game tick it began. */
    private int farSnowShown;
    private long farSnowChanged = UNKNOWN_TICK;
    /** Whether {@link #farSnowShown} is the far rule's fingerprint; false for one saved by 0.1.3. */
    private boolean farSnowByRule;

    public SeasonData() {
    }

    private SeasonData(Saved saved) {
        dayOfYear = saved.dayOfYear();
        daysPerSeason = Math.max(1, saved.daysPerSeason());
        lastClockDay = saved.lastClockDay();
        rainEvent = saved.rainEvent();
        wasRaining = saved.wasRaining();
        thawStartSnow = readDepths(saved.thawStartSnow());
        farSnowShown = saved.farSnowShown();
        farSnowChanged = saved.farSnowChanged();
        farSnowByRule = saved.farSnowByRule();
    }

    private Saved toSaved() {
        Map<String, Double> depths = new LinkedHashMap<>();
        thawStartSnow.forEach((climate, depth) -> depths.put(climate.id(), depth));
        return new Saved(FORMAT, dayOfYear, daysPerSeason, lastClockDay, rainEvent, wasRaining, depths, farSnowShown,
                farSnowChanged, farSnowByRule);
    }

    /** Depths by climate ID; only a full, readable set counts, anything else means not known. */
    private static Map<Climate, Double> readDepths(Map<String, Double> saved) {
        Map<Climate, Double> depths = new EnumMap<>(Climate.class);
        for (Climate climate : Climate.values()) {
            Double depth = saved.get(climate.id());
            if (depth == null || !Double.isFinite(depth)) {
                return Map.of();
            }
            depths.put(climate, Math.max(0.0, depth));
        }
        return Map.copyOf(depths);
    }

    public int dayOfYear() {
        return dayOfYear;
    }

    /** The season length {@link #dayOfYear()} is counted in. */
    public int daysPerSeason() {
        return daysPerSeason;
    }

    public void setDay(int dayOfYear, int daysPerSeason) {
        this.dayOfYear = dayOfYear;
        this.daysPerSeason = daysPerSeason;
        setDirty();
    }

    public long lastClockDay() {
        return lastClockDay;
    }

    public SeasonClock.Reading clock() {
        return new SeasonClock.Reading(dayOfYear, daysPerSeason, lastClockDay);
    }

    public void setClock(SeasonClock.Reading reading) {
        if (!reading.equals(clock())) {
            dayOfYear = reading.dayOfYear();
            daysPerSeason = reading.daysPerSeason();
            lastClockDay = reading.lastClockDay();
            setDirty();
        }
    }

    /** How many rain events this world has had; the current or last one has this number. */
    public long rainEvent() {
        return rainEvent;
    }

    public boolean wasRaining() {
        return wasRaining;
    }

    public void setRaining(boolean raining) {
        if (raining && !wasRaining) {
            rainEvent++;
        }
        if (raining != wasRaining) {
            wasRaining = raining;
            setDirty();
        }
    }

    /**
     * The snow depth of each climate, in layers, when the current or latest thaw time (early spring) began. Far
     * terrain shows the snow of that moment until thaw time ends. Empty for a world that hasn't been through the
     * start of a thaw time since this was added.
     */
    public Optional<Map<Climate, Double>> thawStartSnow() {
        return thawStartSnow.isEmpty() ? Optional.empty() : Optional.of(thawStartSnow);
    }

    public void setThawStartSnow(Map<Climate, Double> depths) {
        thawStartSnow = Map.copyOf(depths);
        setDirty();
    }

    /**
     * The game tick at which far terrain began to show {@code shown}: the saved tick if it was already showing it
     * (across restarts, since game time stops while the server is off), otherwise {@code gameTime}, which is saved.
     * A saved tick ahead of the game time (a file from another world) counts as unknown.
     *
     * @param shown      the far rule's fingerprint of what far terrain shows now
     * @param shownBy013 0.1.3's fingerprint of the same state, to compare with a tick 0.1.3 saved, so worlds it saved
     *                   mid-melt keep their tick
     */
    public long farSnowChanged(int shown, int shownBy013, long gameTime) {
        int compared = farSnowByRule ? shown : shownBy013;
        boolean showingIt = farSnowChanged != UNKNOWN_TICK && compared == farSnowShown && farSnowChanged <= gameTime;
        if (!showingIt) {
            farSnowChanged = gameTime;
        }
        if (!showingIt || !farSnowByRule) {
            farSnowShown = shown;
            farSnowByRule = true;
            setDirty();
        }
        return farSnowChanged;
    }
}
