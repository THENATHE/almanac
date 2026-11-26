package com.thenathe.almanac.weather;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.AlmanacModule;
import com.thenathe.almanac.internal.SavedFields;
import com.thenathe.almanac.internal.WeatherRecord;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * The overworld's weather record ({@code data/almanac/weather.dat}, from 0.4.0): the eras (with their share of time
 * under rain, {@code rain_share}, from 1.1.0; an older era has none), the operators' weather and the stretches the
 * weather stood still ({@link WeatherRecord}). Storms aren't in it: they are worked out from the
 * weather seed, which comes from the world seed, and the time. A world without the file (new, or from before 0.4.0)
 * starts an empty record the first time the season runs, and its regional weather starts then: that tick is the
 * switch-over, before which the world's weather was vanilla's one rain. Fields are read one by one
 * ({@link SavedFields}), so a damaged list doesn't take the others with it.
 */
final class WeatherData extends SavedData {
    static final String FILE = "weather.dat";
    private static final int FORMAT = 1;

    private static final Codec<WeatherRecord.Era> ERA_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("start").forGetter(WeatherRecord.Era::start),
            Codec.STRING.fieldOf("sub_season").forGetter(era -> era.subSeason().id()),
            Codec.INT.fieldOf("version").forGetter(WeatherRecord.Era::version),
            Codec.INT.optionalFieldOf("hot_share", 0).forGetter(WeatherRecord.Era::hotShare),
            Codec.INT.optionalFieldOf("temperate_share", 100).forGetter(WeatherRecord.Era::temperateShare),
            Codec.INT.optionalFieldOf("cold_share", 100).forGetter(WeatherRecord.Era::coldShare),
            // Written whenever there is one, never left to a default: a default could change, and the past with it.
            Codec.INT.optionalFieldOf("rain_share").forGetter(era -> era.rainShare() > 0
                    ? Optional.of(era.rainShare()) : Optional.empty())
    ).apply(instance, (start, subSeason, version, hot, temperate, cold, rain) -> new WeatherRecord.Era(start,
            SubSeason.byId(subSeason).orElse(SubSeason.EARLY_SPRING), version, hot, temperate, cold, rain.orElse(0))));

    private static final Codec<WeatherRecord.Event> EVENT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("kind").forGetter(event -> event.kind().name().toLowerCase(Locale.ROOT)),
            Codec.LONG.fieldOf("start").forGetter(WeatherRecord.Event::start),
            Codec.LONG.fieldOf("end").forGetter(WeatherRecord.Event::end),
            Codec.DOUBLE.optionalFieldOf("x", 0.0).forGetter(WeatherRecord.Event::x),
            Codec.DOUBLE.optionalFieldOf("z", 0.0).forGetter(WeatherRecord.Event::z),
            Codec.DOUBLE.optionalFieldOf("radius", 0.0).forGetter(WeatherRecord.Event::radius)
    ).apply(instance, (kind, start, end, x, z, radius) -> new WeatherRecord.Event(kindOf(kind), start, end, x, z,
            radius)));

    /** A kind this version doesn't know (only a later Almanac could have written one) reads as a clearing. */
    private static WeatherRecord.EventKind kindOf(String name) {
        for (WeatherRecord.EventKind kind : WeatherRecord.EventKind.values()) {
            if (kind.name().equalsIgnoreCase(name)) {
                return kind;
            }
        }
        return WeatherRecord.EventKind.CLEAR_AROUND;
    }

    private static final Codec<WeatherRecord.Freeze> FREEZE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("start").forGetter(WeatherRecord.Freeze::start),
            Codec.LONG.optionalFieldOf("end", WeatherRecord.Freeze.OPEN).forGetter(WeatherRecord.Freeze::end)
    ).apply(instance, WeatherRecord.Freeze::new));

    private record Saved(int format, List<WeatherRecord.Era> eras, List<WeatherRecord.Event> events,
                         List<WeatherRecord.Freeze> freezes) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                SavedFields.optional(FILE, "format", Codec.INT, FORMAT).forGetter(Saved::format),
                SavedFields.optional(FILE, "eras", ERA_CODEC.listOf(), List.<WeatherRecord.Era>of())
                        .forGetter(Saved::eras),
                SavedFields.optional(FILE, "events", EVENT_CODEC.listOf(), List.<WeatherRecord.Event>of())
                        .forGetter(Saved::events),
                SavedFields.optional(FILE, "freezes", FREEZE_CODEC.listOf(), List.<WeatherRecord.Freeze>of())
                        .forGetter(Saved::freezes)
        ).apply(instance, Saved::new));
    }

    static final Codec<WeatherData> CODEC = Saved.CODEC.xmap(WeatherData::new, WeatherData::toSaved);
    static final SavedDataType<WeatherData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(AlmanacModule.MOD_ID, "weather"), WeatherData::new, CODEC, null);

    private final List<WeatherRecord.Era> eras = new ArrayList<>();
    private final List<WeatherRecord.Event> events = new ArrayList<>();
    private final List<WeatherRecord.Freeze> freezes = new ArrayList<>();

    WeatherData() {
    }

    private WeatherData(Saved saved) {
        long previous = Long.MIN_VALUE;
        for (WeatherRecord.Era era : saved.eras()) {
            if (era.start() >= previous) {
                eras.add(era);
                previous = era.start();
            }
        }
        events.addAll(saved.events());
        freezes.addAll(saved.freezes());
    }

    private Saved toSaved() {
        return new Saved(FORMAT, List.copyOf(eras), List.copyOf(events), List.copyOf(freezes));
    }

    List<WeatherRecord.Era> eras() {
        return eras;
    }

    List<WeatherRecord.Event> events() {
        return events;
    }

    List<WeatherRecord.Freeze> freezes() {
        return freezes;
    }

    WeatherRecord record(long seed) {
        return new WeatherRecord(seed, eras, events, freezes);
    }

    void changed() {
        setDirty();
    }
}
