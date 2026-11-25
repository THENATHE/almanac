package com.thenathe.almanac.season;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thenathe.almanac.Almanac;
import com.thenathe.almanac.internal.SavedFields;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The world's place in the year and its rain-event count, kept in the overworld's saved data
 * ({@code data/almanac/season.dat}). Every field has a default and is read on its own ({@link SavedFields}), so older
 * saves keep loading and a damaged field doesn't reset the year. A field this version no longer has is skipped when
 * read and left out of the next save: {@code thaw_start_snow}, the snow depths at the start of early spring that
 * 0.1.2 to 0.1.6 saved for far terrain's thaw, which nothing has gone by since 0.1.6.
 */
public final class SeasonData extends SavedData {
    private static final int FORMAT = 1;
    private static final String FILE = "season.dat";
    public static final long NOT_STARTED = SeasonClock.NOT_STARTED;
    private static final long UNKNOWN_TICK = -1L;

    private record Saved(int format, int dayOfYear, int daysPerSeason, long lastClockDay, long rainEvent, boolean wasRaining,
                         int farSnowShown, long farSnowChanged, boolean farSnowByRule) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                SavedFields.optional(FILE, "format", Codec.INT, FORMAT).forGetter(Saved::format),
                SavedFields.optional(FILE, "day_of_year", Codec.INT, 0).forGetter(Saved::dayOfYear),
                SavedFields.optional(FILE, "days_per_season", Codec.INT, 24).forGetter(Saved::daysPerSeason),
                SavedFields.optional(FILE, "last_clock_day", Codec.LONG, NOT_STARTED).forGetter(Saved::lastClockDay),
                SavedFields.optional(FILE, "rain_event", Codec.LONG, 0L).forGetter(Saved::rainEvent),
                SavedFields.optional(FILE, "was_raining", Codec.BOOL, false).forGetter(Saved::wasRaining),
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
        farSnowShown = saved.farSnowShown();
        farSnowChanged = saved.farSnowChanged();
        farSnowByRule = saved.farSnowByRule();
    }

    private Saved toSaved() {
        return new Saved(FORMAT, dayOfYear, daysPerSeason, lastClockDay, rainEvent, wasRaining, farSnowShown,
                farSnowChanged, farSnowByRule);
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
