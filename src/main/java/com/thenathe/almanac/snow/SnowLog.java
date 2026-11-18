package com.thenathe.almanac.snow;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.climate.WeatherKind;
import com.thenathe.almanac.season.SavedFields;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The snow depth of each climate, worked out from a log of the dimension's weather. Weather is global, so one depth
 * per climate is enough: snow builds while an event that reaches the climate brings it snow, holds while the climate
 * is cold and dry, and melts while the climate is warm. Depths are in snow layers, between 0 and the
 * {@code max_snow_accumulation_height} game rule.
 *
 * <p>The log is a list of segments, each starting when the sub-season, the rain or the event's reach changes. Old
 * segments are folded into a checkpoint so the log stays short. Every segment carries the reach it had, so the depth
 * doesn't change when the rain shares are edited later.
 */
public final class SnowLog {
    /**
     * Vanilla picks one random column per chunk about every 16 ticks for snowfall, so a column gets a layer about
     * every 4,096 ticks while it snows. The depth builds at the same pace.
     */
    public static final double SNOW_PER_TICK = 1.0 / 4096.0;
    /** A layer melts in two minutes of warm weather. */
    public static final double MELT_PER_TICK = 1.0 / 2400.0;
    static final int MAX_SEGMENTS = 64;

    public record Segment(long start, SubSeason subSeason, boolean raining, Set<Climate> reached) {
        public Segment {
            reached = reached.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(reached));
        }

        boolean sameWeather(SubSeason otherSubSeason, boolean otherRaining, Set<Climate> otherReached) {
            return subSeason == otherSubSeason && raining == otherRaining && reached.equals(otherReached);
        }

        /** A segment without a start or a sub-season can't be placed in the log, so the log leaves it out. */
        static final Codec<Segment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("start").forGetter(Segment::start),
                Codec.STRING.fieldOf("sub_season").forGetter(segment -> segment.subSeason().id()),
                SavedFields.optional(SnowData.FILE, "raining", Codec.BOOL, false).forGetter(Segment::raining),
                SavedFields.optional(SnowData.FILE, "reached", Codec.STRING.listOf(), List.<String>of())
                        .forGetter(segment -> segment.reached().stream().sorted().map(Climate::id).toList())
        ).apply(instance, (start, subSeason, raining, reached) -> new Segment(start,
                SubSeason.byId(subSeason).orElse(SubSeason.EARLY_SPRING), raining, climates(reached))));
    }

    private long checkpointTick;
    private final Map<Climate, Double> checkpoint = new EnumMap<>(Climate.class);
    private final Deque<Segment> segments = new ArrayDeque<>();

    /** A new log: no snow anywhere until it snows. */
    public SnowLog(long startTick) {
        checkpointTick = startTick;
        for (Climate climate : Climate.values()) {
            checkpoint.put(climate, 0.0);
        }
    }

    /**
     * Notes the weather at {@code tick}, starting a segment when it differs from the current one.
     *
     * @return whether the log changed
     */
    public boolean observe(long tick, SubSeason subSeason, boolean raining, Set<Climate> reached, int cap) {
        Segment last = segments.peekLast();
        if (last != null && last.sameWeather(subSeason, raining, reached)) {
            return false;
        }
        long start = Math.max(tick, last == null ? checkpointTick : last.start());
        if (last != null && last.start() == start) {
            segments.pollLast();
        }
        segments.addLast(new Segment(start, subSeason, raining, reached));
        while (segments.size() > MAX_SEGMENTS) {
            foldOldest(cap);
        }
        return true;
    }

    /**
     * The depth of snow in a climate at {@code now}, in layers. {@code now} may be in the past, as long as it is after
     * the checkpoint: segments that start later are left out.
     */
    public double depth(Climate climate, long now, int cap) {
        double depth = Math.min(checkpoint.get(climate), cap);
        long from = checkpointTick;
        Segment previous = null;
        for (Segment segment : segments) {
            if (segment.start() > now) {
                break;
            }
            if (previous != null) {
                depth = advance(depth, climate, previous, segment.start() - from, cap);
                from = segment.start();
            } else {
                // Time between the checkpoint and the first segment passed with unknown weather.
                from = segment.start();
            }
            previous = segment;
        }
        if (previous != null) {
            depth = advance(depth, climate, previous, now - from, cap);
        }
        return depth;
    }

    /** The whole layers of snow that should lie where a climate is cold enough for it: half a layer rounds up. */
    public static int layers(double depth, int cap) {
        return Math.clamp((long) Math.floor(depth + 0.5), 0, Math.max(0, cap));
    }

    /** How a segment of {@code duration} ticks changes a climate's depth. */
    static double advance(double depth, Climate climate, Segment segment, long duration, int cap) {
        if (duration <= 0) {
            return depth;
        }
        WeatherKind kind = WeatherKind.of(climate, segment.subSeason());
        // The same climates the seasonal biome copies make cold. That only holds because the winter and summer
        // temperature settings stay either side of vanilla's snow line; ColdRulesAgreeTest keeps the two together.
        boolean cold = kind == WeatherKind.SNOW || kind == WeatherKind.SNOW_LINE;
        if (!cold) {
            depth -= duration * MELT_PER_TICK;
        } else if (segment.raining() && segment.reached().contains(climate)) {
            depth += duration * SNOW_PER_TICK;
        }
        return Math.clamp(depth, 0.0, Math.max(0, cap));
    }

    private void foldOldest(int cap) {
        Segment oldest = segments.pollFirst();
        Segment next = segments.peekFirst();
        if (oldest == null || next == null) {
            return;
        }
        for (Climate climate : Climate.values()) {
            double folded = advance(Math.min(checkpoint.get(climate), cap), climate, oldest, next.start() - oldest.start(), cap);
            checkpoint.put(climate, folded);
        }
        checkpointTick = next.start();
    }

    /**
     * When the latest unbroken run of segments in the given sub-seasons began, for example the start of this early
     * spring. Empty when the latest segment isn't in them, or when the run reaches back past the oldest segment kept
     * (folded into the checkpoint, or the log began during it), so the start isn't known.
     */
    public OptionalLong startOfLatestRun(Predicate<SubSeason> subSeasons) {
        Segment first = null;
        for (Iterator<Segment> newestFirst = segments.descendingIterator(); newestFirst.hasNext(); ) {
            Segment segment = newestFirst.next();
            if (!subSeasons.test(segment.subSeason())) {
                return first == null ? OptionalLong.empty() : OptionalLong.of(first.start());
            }
            first = segment;
        }
        return OptionalLong.empty();
    }

    public List<Segment> segments() {
        return List.copyOf(segments);
    }

    /** When the log's checkpoint is: the depths there are known, and the weather before it is folded in. */
    public long checkpointTick() {
        return checkpointTick;
    }

    /** The latest time the log knows of: the start of its newest segment, or its checkpoint. */
    public long latestTick() {
        Segment last = segments.peekLast();
        return last == null ? checkpointTick : Math.max(checkpointTick, last.start());
    }

    /** Moves every time in the log back by {@code ticks}, keeping the weather and the depths it gives. */
    public void moveBack(long ticks) {
        checkpointTick -= ticks;
        List<Segment> moved = new ArrayList<>(segments.size());
        for (Segment segment : segments) {
            moved.add(new Segment(segment.start() - ticks, segment.subSeason(), segment.raining(), segment.reached()));
        }
        segments.clear();
        segments.addAll(moved);
    }

    private static Set<Climate> climates(List<String> ids) {
        Set<Climate> climates = EnumSet.noneOf(Climate.class);
        for (String id : ids) {
            for (Climate climate : Climate.values()) {
                if (climate.id().equals(id)) {
                    climates.add(climate);
                }
            }
        }
        return climates;
    }

    private record Saved(long checkpointTick, Map<String, Double> checkpoint, List<Segment> segments) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                SavedFields.optional(SnowData.FILE, "checkpoint_tick", Codec.LONG, 0L).forGetter(Saved::checkpointTick),
                SavedFields.optional(SnowData.FILE, "checkpoint_depth", Codec.unboundedMap(Codec.STRING, Codec.DOUBLE),
                        Map.<String, Double>of()).forGetter(Saved::checkpoint),
                SavedFields.optional(SnowData.FILE, "segments", Segment.CODEC.listOf(), List.<Segment>of())
                        .forGetter(Saved::segments)
        ).apply(instance, Saved::new));
    }

    public static final Codec<SnowLog> CODEC = Saved.CODEC.xmap(SnowLog::fromSaved, SnowLog::toSaved);

    private static SnowLog fromSaved(Saved saved) {
        SnowLog log = new SnowLog(saved.checkpointTick());
        for (Climate climate : Climate.values()) {
            double depth = Optional.ofNullable(saved.checkpoint().get(climate.id())).orElse(0.0);
            log.checkpoint.put(climate, Double.isFinite(depth) ? Math.max(0.0, depth) : 0.0);
        }
        long previousStart = Long.MIN_VALUE;
        for (Segment segment : saved.segments()) {
            // A log edited by hand could be out of order; keep it usable rather than refusing it.
            if (segment.start() >= previousStart) {
                log.segments.addLast(segment);
                previousStart = segment.start();
            }
        }
        return log;
    }

    private Saved toSaved() {
        Map<String, Double> depths = new LinkedHashMap<>();
        checkpoint.forEach((climate, depth) -> depths.put(climate.id(), depth));
        return new Saved(checkpointTick, depths, new ArrayList<>(segments));
    }
}
