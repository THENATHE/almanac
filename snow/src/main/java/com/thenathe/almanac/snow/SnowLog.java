package com.thenathe.almanac.snow;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SnowClocks;
import com.thenathe.almanac.api.SnowLayers;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.SavedFields;
import com.thenathe.almanac.internal.WeatherKind;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The snow depth and ice cover of each area, worked out from a log of the dimension's weather. Snow builds while the
 * {@link RainSource} brings an area snow, holds while the area is cold and dry, and melts while it is warm; still
 * water freezes over while the area is cold and thaws while it is warm. Depths are in snow layers, between 0 and the
 * most layers; ice covers between 0 and 1. How the depth lies layer by layer is {@code SnowLayers}' rule.
 *
 * <p>The log is a list of segments, each starting when the sub-season, the rain, the event's reach, the clocks or
 * the weather change. Old segments are folded into a checkpoint so the log stays short. Every segment carries the
 * reach and the clocks it had (from 0.3.0), so neither a change to the rain shares nor one to the clocks rewrites the
 * past. Segments saved before 0.3.0 have no clocks of their own: they ran on {@link #legacyClocks}, the fixed paces of
 * those versions, so a world keeps the snow it had when it is opened in this one.
 *
 * <p>From 0.4.0 a segment also says which weather decided the rain ({@link Segment#weatherVersion}): 0 for vanilla's
 * one weather, as every older segment was, or the storm maths of regional weather. Under regional weather the log
 * holds no rain of its own (vanilla's is held clear); the snow on a node comes from the storms over it through the
 * {@link RainSource}. The first such segment is the switch-over: before it every node of a climate had the climate's
 * one depth, so the snow of a world from before regional weather carries over exactly.
 */
public final class SnowLog {
    /** Snow built one layer per 4,096 ticks up to 0.2.0, the depth of vanilla's own snowfall. */
    static final int LEGACY_SNOWFALL_TICKS = 4096;
    /** And melted one layer per 2,400 ticks (two minutes). */
    static final int LEGACY_THAW_TICKS = 2400;
    /**
     * Nothing measured the ice then: still water froze over at once where it was cold (chunks that loaded froze all of
     * it, loaded ones within a few passes), so the ice of those segments counts as whole within a tick of the cold.
     */
    static final int LEGACY_ICE_TICKS = 1;
    static final int MAX_SEGMENTS = 64;
    /**
     * Segments with regional weather fold only once every node's snow is known to be gone (a long enough warm spell
     * for every climate; mid summer does it each year at the defaults), so the log may grow past
     * {@link #MAX_SEGMENTS} until then. Past this many, the oldest is folded as if the weather were global, which
     * forgets where that segment's snow fell; the log says so.
     */
    static final int MOST_REGIONAL_SEGMENTS = 1024;

    /**
     * One stretch of unchanged weather.
     *
     * @param clocks         the clocks in force through it; null for segments saved before 0.3.0
     * @param weatherVersion 0 while vanilla's one weather decided the rain (every segment before 0.4.0); otherwise
     *                       the storm maths of regional weather
     */
    public record Segment(long start, SubSeason subSeason, boolean raining, Set<Climate> reached,
                          @Nullable SnowClocks clocks, int weatherVersion) {
        public Segment {
            reached = reached.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(reached));
        }

        /** A segment of vanilla's one weather. */
        public Segment(long start, SubSeason subSeason, boolean raining, Set<Climate> reached,
                       @Nullable SnowClocks clocks) {
            this(start, subSeason, raining, reached, clocks, 0);
        }

        /** A segment as saved before 0.3.0, on the legacy clocks. */
        public Segment(long start, SubSeason subSeason, boolean raining, Set<Climate> reached) {
            this(start, subSeason, raining, reached, null, 0);
        }

        boolean sameWeather(SubSeason otherSubSeason, boolean otherRaining, Set<Climate> otherReached,
                            SnowClocks otherClocks, int otherWeatherVersion) {
            return subSeason == otherSubSeason && raining == otherRaining && reached.equals(otherReached)
                    && otherClocks.equals(clocks) && weatherVersion == otherWeatherVersion;
        }

        Segment movedBack(long ticks) {
            return new Segment(start - ticks, subSeason, raining, reached, clocks, weatherVersion);
        }

        private static final Codec<SnowClocks> CLOCKS_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.fieldOf("snowfall_ticks").forGetter(SnowClocks::snowfallTicks),
                Codec.INT.fieldOf("thaw_ticks").forGetter(SnowClocks::thawTicks),
                Codec.INT.fieldOf("ice_ticks").forGetter(SnowClocks::iceTicks),
                Codec.INT.fieldOf("max_layers").forGetter(SnowClocks::maxLayers)
        ).apply(instance, SnowClocks::new));

        /** A segment without a start or a sub-season can't be placed in the log, so the log leaves it out. */
        static final Codec<Segment> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("start").forGetter(Segment::start),
                Codec.STRING.fieldOf("sub_season").forGetter(segment -> segment.subSeason().id()),
                SavedFields.optional(SnowData.FILE, "raining", Codec.BOOL, false).forGetter(Segment::raining),
                SavedFields.optional(SnowData.FILE, "reached", Codec.STRING.listOf(), List.<String>of())
                        .forGetter(segment -> segment.reached().stream().sorted().map(Climate::id).toList()),
                // Added in 0.3.0. A segment without it, or with clocks that can't be read, ran on the legacy clocks.
                CLOCKS_CODEC.lenientOptionalFieldOf("clocks")
                        .forGetter(segment -> Optional.ofNullable(segment.clocks())),
                // Added in 0.4.0, and only written for regional weather: every older segment was vanilla's weather.
                Codec.INT.lenientOptionalFieldOf("weather_version", 0).forGetter(Segment::weatherVersion)
        ).apply(instance, (start, subSeason, raining, reached, clocks, weatherVersion) -> new Segment(start,
                SubSeason.byId(subSeason).orElse(SubSeason.EARLY_SPRING), raining, climates(reached),
                clocks.orElse(null), Math.max(0, weatherVersion))));
    }

    /** An area's snow at one moment. */
    public record Amount(double depth, double ice) {
        static final Amount NONE = new Amount(0.0, 0.0);
    }

    /**
     * An area's snow now and how it is moving, in layers (and ice cover) a tick, until {@code stableUntil}: the next
     * game tick its snowfall may start or stop, as far as the rain source can tell.
     */
    public record Trend(Amount now, double depthPerTick, double icePerTick, long stableUntil) {
    }

    private long checkpointTick;
    private final Map<Climate, Amount> checkpoint = new EnumMap<>(Climate.class);
    private final Deque<Segment> segments = new ArrayDeque<>();
    /** How deep snow could lie in segments saved before 0.3.0: the snow height game rule they were read with. */
    private int legacyMaxLayers = 1;
    private int approximateFolds;

    /** A new log: no snow anywhere until it snows. */
    public SnowLog(long startTick) {
        checkpointTick = startTick;
        for (Climate climate : Climate.values()) {
            checkpoint.put(climate, Amount.NONE);
        }
    }

    /** The clocks the segments saved before 0.3.0 ran on: 0.2.0's paces, and ice at once. */
    SnowClocks legacyClocks() {
        return new SnowClocks(LEGACY_SNOWFALL_TICKS, LEGACY_THAW_TICKS, LEGACY_ICE_TICKS, legacyMaxLayers);
    }

    int legacyMaxLayers() {
        return legacyMaxLayers;
    }

    void setLegacyMaxLayers(int layers) {
        legacyMaxLayers = Math.clamp(layers, 1, 8);
    }

    /** Whether any segment, or the checkpoint before them, comes from a version before 0.3.0. */
    boolean hasLegacySegments() {
        return segments.stream().anyMatch(segment -> segment.clocks() == null);
    }

    /** {@link #observe(long, SubSeason, boolean, Set, SnowClocks, int)} under vanilla's one weather. */
    public boolean observe(long tick, SubSeason subSeason, boolean raining, Set<Climate> reached, SnowClocks clocks) {
        return observe(tick, subSeason, raining, reached, clocks, 0);
    }

    /**
     * Notes the weather and the clocks at {@code tick}, starting a segment when either differs from the current one.
     *
     * @param weatherVersion which weather decides the rain from now: 0 for vanilla's, or regional weather's maths
     * @return whether the log changed
     */
    public boolean observe(long tick, SubSeason subSeason, boolean raining, Set<Climate> reached, SnowClocks clocks,
                           int weatherVersion) {
        Segment last = segments.peekLast();
        if (last != null && last.sameWeather(subSeason, raining, reached, clocks, weatherVersion)) {
            return false;
        }
        long start = Math.max(tick, last == null ? checkpointTick : last.start());
        if (last != null && last.start() == start) {
            segments.pollLast();
        }
        segments.addLast(new Segment(start, subSeason, raining, reached, clocks, weatherVersion));
        fold();
        return true;
    }

    /**
     * Keeps the log short. Segments of vanilla's weather fold into the checkpoint as before. A segment of regional
     * weather can only fold once its snow is gone everywhere, since its snow differs from node to node and the
     * checkpoint holds one amount per climate: the log folds through the newest such point it has.
     */
    private void fold() {
        while (segments.size() > MAX_SEGMENTS) {
            Segment oldest = segments.peekFirst();
            if (oldest != null && oldest.weatherVersion() == 0) {
                foldOldest();
                continue;
            }
            if (foldThroughMelt()) {
                continue;
            }
            if (segments.size() <= MOST_REGIONAL_SEGMENTS) {
                return;
            }
            approximateFolds++;
            foldOldest();
        }
    }

    /**
     * Folds every segment up to the newest one after which no snow can be left anywhere: one that is warm for every
     * climate for long enough to melt the deepest snow there could be. The checkpoint then holds no snow, and the ice
     * the log gives (which doesn't depend on the rain).
     */
    private boolean foldThroughMelt() {
        List<Segment> list = new ArrayList<>(segments);
        for (int index = list.size() - 2; index >= 0; index--) {
            Segment segment = list.get(index);
            long length = list.get(index + 1).start() - segment.start();
            if (meltsEverything(segment, length)) {
                Map<Climate, Amount> folded = new EnumMap<>(Climate.class);
                for (Climate climate : Climate.values()) {
                    double ice = trendFrom(SnowArea.of(climate), checkpointTick, checkpoint.get(climate),
                            list.get(index + 1).start(), RainSource.GLOBAL).now().ice();
                    folded.put(climate, new Amount(0.0, ice));
                }
                for (int fold = 0; fold <= index; fold++) {
                    segments.pollFirst();
                }
                checkpoint.putAll(folded);
                checkpointTick = list.get(index + 1).start();
                return true;
            }
        }
        return false;
    }

    /** Whether a segment of this length leaves no snow in any climate, however much there was. */
    private boolean meltsEverything(Segment segment, long length) {
        for (Climate climate : Climate.values()) {
            if (holdsSnow(climate, segment.subSeason())) {
                return false;
            }
        }
        return length >= (long) SnowLayers.MOST_LAYERS * clocksOf(segment).thawTicks();
    }

    /**
     * The latest segment boundary at or before {@code now} at which a climate's snow is surely gone everywhere: the
     * end of a warm spell long enough to melt the deepest snow there could be. Working a node's snow out from there
     * gives the same as from the checkpoint, with less of the past to look at. {@link Long#MIN_VALUE} if there is
     * none, so work starts from the checkpoint.
     */
    long meltedBy(Climate climate, long now) {
        List<Segment> list = new ArrayList<>(segments);
        double melted = 0.0;
        long spellEnd = Long.MIN_VALUE;
        for (int index = list.size() - 2; index >= 0; index--) {
            Segment segment = list.get(index);
            long end = list.get(index + 1).start();
            if (end > now) {
                continue;
            }
            if (holdsSnow(climate, segment.subSeason())) {
                melted = 0.0;
                spellEnd = Long.MIN_VALUE;
                continue;
            }
            if (spellEnd == Long.MIN_VALUE) {
                spellEnd = end;
            }
            melted += (end - segment.start()) / (double) clocksOf(segment).thawTicks();
            if (melted >= SnowLayers.MOST_LAYERS) {
                return spellEnd;
            }
        }
        return Long.MIN_VALUE;
    }

    /** How many times the log has had to fold a segment of regional weather before its snow was gone. */
    int approximateFolds() {
        return approximateFolds;
    }

    /**
     * An area's snow at {@code now}. {@code now} may be in the past, as long as it is after the checkpoint: segments
     * that start later are left out.
     */
    public Amount amount(SnowArea area, long now, RainSource rain) {
        return trend(area, now, rain).now();
    }

    /** A climate's snow depth at {@code now}, under vanilla's one rain. */
    double depth(Climate climate, long now) {
        return amount(SnowArea.of(climate), now, RainSource.GLOBAL).depth();
    }

    /** A climate's ice cover at {@code now}, under vanilla's one rain. */
    double ice(Climate climate, long now) {
        return amount(SnowArea.of(climate), now, RainSource.GLOBAL).ice();
    }

    /** An area's snow at {@code now}, and how it moves in the segment {@code now} falls in. */
    public Trend trend(SnowArea area, long now, RainSource rain) {
        return trendFrom(area, checkpointTick, checkpoint.get(area.climate()), now, rain);
    }

    /**
     * An area's snow at {@code now}, from what it was at {@code from} (the checkpoint, a point where it is known, or
     * a value worked out before), and how it moves at {@code now}. Time before the first segment passed with unknown
     * weather, so it changes nothing.
     */
    public Trend trendFrom(SnowArea area, long from, Amount start, long now, RainSource rain) {
        Amount amount = start;
        Segment previous = null;
        long previousStart = 0;
        for (Segment segment : segments) {
            if (segment.start() > now) {
                break;
            }
            if (previous != null && segment.start() > from) {
                amount = advance(amount, area, previous, Math.max(from, previousStart), segment.start(), rain);
            }
            previous = segment;
            previousStart = segment.start();
        }
        if (previous == null) {
            return new Trend(amount, 0.0, 0.0, Long.MAX_VALUE);
        }
        long lastFrom = Math.max(from, previousStart);
        // While it is warm the snow only melts, so the weather doesn't matter and isn't asked.
        RainSource.Spell spell = holdsSnow(area.climate(), previous.subSeason())
                ? rain.spell(previous, area, lastFrom, Math.max(lastFrom, now)) : RainSource.Spell.NONE;
        amount = advance(amount, area, previous, lastFrom, now, spell);
        return new Trend(amount, pace(amount, area, previous, spell.fallingAtEnd(), true),
                pace(amount, area, previous, spell.fallingAtEnd(), false), spell.stableUntil());
    }

    /** How an area's snow changes through game ticks {@code [from, to)} of a segment. */
    Amount advance(Amount amount, SnowArea area, Segment segment, long from, long to, RainSource rain) {
        boolean asks = to > from && holdsSnow(area.climate(), segment.subSeason());
        return advance(amount, area, segment, from, to, asks ? rain.spell(segment, area, from, to) : RainSource.Spell.NONE);
    }

    private Amount advance(Amount amount, SnowArea area, Segment segment, long from, long to, RainSource.Spell spell) {
        SnowClocks clocks = clocksOf(segment);
        double depth = Math.min(amount.depth(), clocks.maxLayers());
        double ice = amount.ice();
        long duration = Math.max(0, to - from);
        if (duration > 0) {
            if (!holdsSnow(area.climate(), segment.subSeason())) {
                depth -= duration / (double) clocks.thawTicks();
                ice -= duration / (double) clocks.thawTicks();
            } else {
                // Snow builds only while it falls; water freezes whatever the weather. Neither melts while it is cold.
                depth += spell.ticks() / (double) clocks.snowfallTicks();
                ice += duration / (double) clocks.iceTicks();
            }
        }
        return new Amount(Math.clamp(depth, 0.0, clocks.maxLayers()), Math.clamp(ice, 0.0, 1.0));
    }

    /** How fast the depth (or the ice) moves now in a segment, a tick, from {@code amount}; 0 once it can't move. */
    private double pace(Amount amount, SnowArea area, Segment segment, boolean falling, boolean snow) {
        SnowClocks clocks = clocksOf(segment);
        double value = snow ? Math.min(amount.depth(), clocks.maxLayers()) : amount.ice();
        double most = snow ? clocks.maxLayers() : 1.0;
        if (!holdsSnow(area.climate(), segment.subSeason())) {
            return value > 0.0 ? -1.0 / clocks.thawTicks() : 0.0;
        }
        boolean building = !snow || falling;
        if (!building || value >= most) {
            return 0.0;
        }
        return 1.0 / (snow ? clocks.snowfallTicks() : clocks.iceTicks());
    }

    SnowClocks clocksOf(Segment segment) {
        return segment.clocks() == null ? legacyClocks() : segment.clocks();
    }

    /**
     * Whether a climate keeps its snow and ice in a sub-season: it is cold there, so the depth builds in snowfall,
     * water freezes, and both hold otherwise. In every other sub-season both melt. The same climates the climate table
     * makes cold; that only holds because the winter and summer temperature settings stay either side of vanilla's
     * snow line (ColdRulesAgreeTest keeps the two together).
     */
    public static boolean holdsSnow(Climate climate, SubSeason subSeason) {
        WeatherKind kind = WeatherKind.of(climate, subSeason);
        return kind == WeatherKind.SNOW || kind == WeatherKind.SNOW_LINE;
    }

    private void foldOldest() {
        Segment oldest = segments.pollFirst();
        Segment next = segments.peekFirst();
        if (oldest == null || next == null) {
            return;
        }
        for (SnowArea area : SnowArea.all()) {
            Amount folded = advance(checkpoint.get(area.climate()), area, oldest, Math.max(checkpointTick, oldest.start()),
                    next.start(), RainSource.GLOBAL);
            checkpoint.put(area.climate(), folded);
        }
        checkpointTick = next.start();
    }

    public List<Segment> segments() {
        return List.copyOf(segments);
    }

    /** When the log's checkpoint is: the amounts there are known, and the weather before it is folded in. */
    public long checkpointTick() {
        return checkpointTick;
    }

    Amount checkpoint(Climate climate) {
        return checkpoint.get(climate);
    }

    /** The latest time the log knows of: the start of its newest segment, or its checkpoint. */
    public long latestTick() {
        Segment last = segments.peekLast();
        return last == null ? checkpointTick : Math.max(checkpointTick, last.start());
    }

    /** A copy that doesn't change when this log does, for working snow out on another thread. */
    SnowLog copy() {
        SnowLog copy = new SnowLog(checkpointTick);
        copy.checkpoint.putAll(checkpoint);
        copy.segments.addAll(segments);
        copy.legacyMaxLayers = legacyMaxLayers;
        return copy;
    }

    /** Moves every time in the log back by {@code ticks}, keeping the weather and the amounts it gives. */
    public void moveBack(long ticks) {
        checkpointTick -= ticks;
        List<Segment> moved = new ArrayList<>(segments.size());
        for (Segment segment : segments) {
            moved.add(segment.movedBack(ticks));
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

    private record Saved(long checkpointTick, Map<String, Double> checkpoint, Map<String, Double> checkpointIce,
                         List<Segment> segments) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                SavedFields.optional(SnowData.FILE, "checkpoint_tick", Codec.LONG, 0L).forGetter(Saved::checkpointTick),
                SavedFields.optional(SnowData.FILE, "checkpoint_depth", Codec.unboundedMap(Codec.STRING, Codec.DOUBLE),
                        Map.<String, Double>of()).forGetter(Saved::checkpoint),
                // Added in 0.3.0.
                SavedFields.optional(SnowData.FILE, "checkpoint_ice", Codec.unboundedMap(Codec.STRING, Codec.DOUBLE),
                        Map.<String, Double>of()).forGetter(Saved::checkpointIce),
                SavedFields.optional(SnowData.FILE, "segments", Segment.CODEC.listOf(), List.<Segment>of())
                        .forGetter(Saved::segments)
        ).apply(instance, Saved::new));
    }

    public static final Codec<SnowLog> CODEC = Saved.CODEC.xmap(SnowLog::fromSaved, SnowLog::toSaved);

    private static SnowLog fromSaved(Saved saved) {
        SnowLog log = new SnowLog(saved.checkpointTick());
        long previousStart = Long.MIN_VALUE;
        for (Segment segment : saved.segments()) {
            // A log edited by hand could be out of order; keep it usable rather than refusing it.
            if (segment.start() >= previousStart) {
                log.segments.addLast(segment);
                previousStart = segment.start();
            }
        }
        Segment first = log.segments.peekFirst();
        for (Climate climate : Climate.values()) {
            double depth = Optional.ofNullable(saved.checkpoint().get(climate.id())).orElse(0.0);
            Double savedIce = saved.checkpointIce().get(climate.id());
            double ice;
            if (savedIce != null && Double.isFinite(savedIce)) {
                ice = Math.clamp(savedIce, 0.0, 1.0);
            } else {
                // Saved before 0.3.0, when nothing measured the ice: water that froze then stayed frozen through the
                // cold, so a climate that was cold when the log begins counts as frozen over.
                ice = first != null && holdsSnow(climate, first.subSeason()) ? 1.0 : 0.0;
            }
            log.checkpoint.put(climate, new Amount(Double.isFinite(depth) ? Math.max(0.0, depth) : 0.0, ice));
        }
        return log;
    }

    private Saved toSaved() {
        Map<String, Double> depths = new LinkedHashMap<>();
        Map<String, Double> ice = new LinkedHashMap<>();
        checkpoint.forEach((climate, amount) -> {
            depths.put(climate.id(), amount.depth());
            ice.put(climate.id(), amount.ice());
        });
        return new Saved(checkpointTick, depths, ice, new ArrayList<>(segments));
    }
}
