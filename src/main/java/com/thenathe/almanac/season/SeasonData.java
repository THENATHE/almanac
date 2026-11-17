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
 * ({@code data/almanac/season.dat}). Fields are only ever added, each with a default, so older saves keep loading.
 */
public final class SeasonData extends SavedData {
    private static final int FORMAT = 1;
    /** The clock hasn't seen a day yet: a new world, or a world from before Almanac was installed. */
    public static final long NOT_STARTED = Long.MIN_VALUE;

    private record Saved(int format, int dayOfYear, int daysPerSeason, long lastClockDay, long rainEvent, boolean wasRaining,
                         Map<String, Double> thawStartSnow) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.optionalFieldOf("format", FORMAT).forGetter(Saved::format),
                Codec.INT.optionalFieldOf("day_of_year", 0).forGetter(Saved::dayOfYear),
                Codec.INT.optionalFieldOf("days_per_season", 24).forGetter(Saved::daysPerSeason),
                Codec.LONG.optionalFieldOf("last_clock_day", NOT_STARTED).forGetter(Saved::lastClockDay),
                Codec.LONG.optionalFieldOf("rain_event", 0L).forGetter(Saved::rainEvent),
                Codec.BOOL.optionalFieldOf("was_raining", false).forGetter(Saved::wasRaining),
                // Added in 0.1.2; worlds from 0.1.0 and 0.1.1 have none and work it out again (see ServerSeasons).
                Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).optionalFieldOf("thaw_start_snow", Map.of())
                        .forGetter(Saved::thawStartSnow)
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

    public SeasonData() {
    }

    private SeasonData(Saved saved) {
        dayOfYear = saved.dayOfYear();
        daysPerSeason = Math.max(1, saved.daysPerSeason());
        lastClockDay = saved.lastClockDay();
        rainEvent = saved.rainEvent();
        wasRaining = saved.wasRaining();
        thawStartSnow = readDepths(saved.thawStartSnow());
    }

    private Saved toSaved() {
        Map<String, Double> depths = new LinkedHashMap<>();
        thawStartSnow.forEach((climate, depth) -> depths.put(climate.id(), depth));
        return new Saved(FORMAT, dayOfYear, daysPerSeason, lastClockDay, rainEvent, wasRaining, depths);
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

    public void setLastClockDay(long lastClockDay) {
        this.lastClockDay = lastClockDay;
        setDirty();
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
}
