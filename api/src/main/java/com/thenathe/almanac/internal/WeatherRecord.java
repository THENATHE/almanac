package com.thenathe.almanac.internal;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import java.util.List;

/**
 * Everything regional weather can't work out from the weather seed and the time, as the weather module saves it
 * ({@code weather.dat}) and sends it to clients: when each stretch of the season began and what it was (eras), what
 * operators did with {@code /weather} (events), and when the weather stood still ({@code advance_weather} off,
 * freezes). Storms themselves are never saved. All times are game ticks of the overworld.
 *
 * @param seed the weather seed: a one-way hash of the world seed, so clients never learn the world seed
 */
public record WeatherRecord(long seed, List<Era> eras, List<Event> events, List<Freeze> freezes) {
    public WeatherRecord {
        eras = List.copyOf(eras);
        events = List.copyOf(events);
        freezes = List.copyOf(freezes);
    }

    /**
     * A stretch of the season from {@code start}: its sub-season, which storm maths it ran on ({@code version} 0 when
     * regional weather was off and vanilla's weather ran), the rain shares that decided which climates its storms
     * reached, in percent, and the share of time every place was to be under rain, in percent ({@code rainShare},
     * from 1.1.0; 0 where the era doesn't record one, as in every era before it, whose maths had its share built in).
     */
    public record Era(long start, SubSeason subSeason, int version, int hotShare, int temperateShare, int coldShare,
                      int rainShare) {
        public int share(Climate climate) {
            return switch (climate) {
                case HOT -> hotShare;
                case TEMPERATE -> temperateShare;
                case COLD -> coldShare;
            };
        }

        public boolean regional() {
            return version > 0;
        }

        public boolean sameWeather(Era other) {
            return subSeason == other.subSeason && version == other.version && hotShare == other.hotShare
                    && temperateShare == other.temperateShare && coldShare == other.coldShare
                    && rainShare == other.rainShare;
        }
    }

    /** What an operator's {@code /weather} did, from {@code start} until {@code end}. */
    public enum EventKind {
        /** No rain, snow or thunder within {@code radius} of the place. */
        CLEAR_AROUND,
        /** A storm over the place, raining or snowing as the season has it. */
        STORM,
        /** A thunderstorm over the place. */
        THUNDERSTORM,
        /** No rain, snow or thunder anywhere. */
        CLEAR_EVERYWHERE,
        /** Rain or snow everywhere, as vanilla's world-wide weather. */
        RAIN_EVERYWHERE,
        /** Rain or snow and thunder everywhere. */
        THUNDER_EVERYWHERE;

        public boolean everywhere() {
            return this == CLEAR_EVERYWHERE || this == RAIN_EVERYWHERE || this == THUNDER_EVERYWHERE;
        }

        public boolean clears() {
            return this == CLEAR_AROUND || this == CLEAR_EVERYWHERE;
        }
    }

    /** One {@code /weather}: where (for the local kinds), how far it reaches, and for how long. */
    public record Event(EventKind kind, long start, long end, double x, double z, double radius) {
        public boolean activeAt(long tick) {
            return tick >= start && tick < end;
        }
    }

    /** A stretch while the weather stood still; {@code end} is {@link Long#MAX_VALUE} while it still does. */
    public record Freeze(long start, long end) {
        public static final long OPEN = Long.MAX_VALUE;
    }
}
