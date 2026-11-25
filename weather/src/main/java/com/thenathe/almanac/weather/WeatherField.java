package com.thenathe.almanac.weather;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.Storm;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.api.Wind;
import com.thenathe.almanac.internal.WeatherHere;
import com.thenathe.almanac.internal.WeatherRecord;
import com.thenathe.almanac.internal.WeatherView;
import com.thenathe.almanac.internal.WetSpell;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

/**
 * The weather worked out from a {@link WeatherRecord}: the storms of the lattice, the storms operators placed, their
 * clearings and world-wide weather, the wind and the weather clock. Immutable, so any thread may ask it, and the same
 * record gives the same answers on the server and on every client.
 *
 * <p>Asking about one place looks at every lattice cell a storm could have come from: about 8,000 blocks around and
 * the last few days. The server and the client keep a {@link StormIndex} of the storms near the players for the
 * questions vanilla asks every tick; this is what the index is built from, and what answers everything else.
 */
final class WeatherField implements WeatherView {
    /** How far past the end of a stretch {@link #wetSpell} looks for when the weather next changes. */
    static final long HORIZON = 1_200;
    /** The radius of a storm placed by {@code /weather rain} or {@code thunder}, in blocks. */
    static final double PLACED_RADIUS = 1_200.0;

    private final WeatherRecord record;
    private final long seed;
    private final WeatherClock clock;
    private final List<WeatherRecord.Era> eras;
    private final double[] eraWeatherStarts;
    private final List<WeatherRecord.Event> events;
    private final List<StormShape> placed;
    private final CellCache cache;
    /** Whether the record has any world-wide rain or thunder at all, so a column with nothing overhead skips them. */
    private final boolean anyEverywhere;

    WeatherField(WeatherRecord record, CellCache cache) {
        this.record = record;
        this.seed = record.seed();
        this.cache = cache;
        this.clock = new WeatherClock(record.freezes());
        List<WeatherRecord.Era> sorted = new ArrayList<>(record.eras());
        sorted.sort(Comparator.comparingLong(WeatherRecord.Era::start));
        this.eras = List.copyOf(sorted);
        this.eraWeatherStarts = new double[eras.size()];
        for (int index = 0; index < eras.size(); index++) {
            eraWeatherStarts[index] = clock.weatherTick(eras.get(index).start());
        }
        this.events = record.events();
        List<StormShape> placedStorms = new ArrayList<>();
        for (WeatherRecord.Event event : events) {
            if (event.kind() == WeatherRecord.EventKind.STORM || event.kind() == WeatherRecord.EventKind.THUNDERSTORM) {
                placedStorms.add(new StormShape(placedStorm(event), event.start()));
            }
        }
        this.placed = List.copyOf(placedStorms);
        this.anyEverywhere = events.stream().anyMatch(event -> event.kind() == WeatherRecord.EventKind.RAIN_EVERYWHERE
                || event.kind() == WeatherRecord.EventKind.THUNDER_EVERYWHERE);
    }

    /**
     * The storm a local {@code /weather rain} or {@code thunder} places: over the place it was asked at, still, for
     * as long as it was asked for, reaching every climate that gets any rain. It is full grown from the moment it is
     * asked for (it was born a little before, as if it had formed there), so it rains and thunders at once, and it
     * shrinks away over the last part of the time asked.
     */
    private Storm placedStorm(WeatherRecord.Event event) {
        boolean thunder = event.kind() == WeatherRecord.EventKind.THUNDERSTORM;
        long asked = Math.max(1, event.end() - event.start());
        int life = (int) Math.min(Integer.MAX_VALUE, Math.ceil(asked / (1.0 - Storm.GROWTH)));
        long birth = (long) Math.floor(clock.weatherTick(event.start())) - (life - asked);
        long id = Hashes.of(seed, 0x504C_4143_4544L, event.start(), Double.doubleToLongBits(event.x()),
                Double.doubleToLongBits(event.z()));
        double radius = event.radius() > 0.0 ? event.radius() : PLACED_RADIUS;
        return new Storm(id, event.x(), event.z(), birth, life, 0.0, 0.0, radius, 1.0, 0.0, 0.8F, thunder, 320, 0.0F);
    }

    @Override
    public WeatherRecord record() {
        return record;
    }

    long seed() {
        return seed;
    }

    CellCache cache() {
        return cache;
    }

    @Override
    public boolean regional(long gameTick) {
        WeatherRecord.Era era = eraAt(gameTick);
        return era != null && era.regional();
    }

    @Override
    public double weatherTick(double gameTick) {
        return clock.weatherTick(gameTick);
    }

    boolean frozenAt(long gameTick) {
        return clock.frozenAt(gameTick);
    }

    /** The era in force at a game tick, or null before the first. */
    WeatherRecord.@Nullable Era eraAt(long gameTick) {
        WeatherRecord.Era found = null;
        for (WeatherRecord.Era era : eras) {
            if (era.start() > gameTick) {
                break;
            }
            found = era;
        }
        return found;
    }

    /** The era a lattice day takes its season and maths from: the one in force as the day begins in weather time. */
    private WeatherRecord.@Nullable Era eraOfWeatherTick(double weatherTick) {
        WeatherRecord.Era found = eras.isEmpty() ? null : eras.getFirst();
        for (int index = 0; index < eras.size(); index++) {
            if (eraWeatherStarts[index] > weatherTick) {
                break;
            }
            found = eras.get(index);
        }
        return found;
    }

    /** The storm maths of a weather tick: its era's, or, in an era with regional weather off, the nearest one's. */
    private StormMaths mathsAt(double weatherTick) {
        WeatherRecord.Era era = eraOfWeatherTick(weatherTick);
        if (era != null && era.regional() && StormMaths.known(era.version())) {
            return StormMaths.of(era.version());
        }
        for (WeatherRecord.Era other : eras) {
            if (other.regional() && StormMaths.known(other.version())) {
                return StormMaths.of(other.version());
            }
        }
        return StormMathsV1.INSTANCE;
    }

    private SubSeason.Season seasonAt(double weatherTick) {
        WeatherRecord.Era era = eraOfWeatherTick(weatherTick);
        return era == null ? SubSeason.Season.SPRING : era.subSeason().season();
    }

    /**
     * Every storm whose life overlaps weather ticks {@code [from, to]} and that could come within {@code extra}
     * blocks of a column: the lattice's and the placed ones.
     */
    void forEachStorm(double x, double z, double extra, double from, double to, Consumer<StormShape> action) {
        StormMaths latest = mathsAt(to);
        long firstDay = (long) Math.floor((from - latest.longestLife()) / latest.dayTicks());
        long lastDay = (long) Math.floor(to / latest.dayTicks());
        for (long day = firstDay; day <= lastDay; day++) {
            double dayStart = (double) day * latest.dayTicks();
            StormMaths maths = mathsAt(dayStart);
            SubSeason.Season season = seasonAt(dayStart);
            double reach = maths.reach() + extra;
            int size = maths.cellSize();
            int firstI = (int) Math.floor((x - reach) / size);
            int lastI = (int) Math.floor((x + reach) / size);
            int firstJ = (int) Math.floor((z - reach) / size);
            int lastJ = (int) Math.floor((z + reach) / size);
            for (int i = firstI; i <= lastI; i++) {
                for (int j = firstJ; j <= lastJ; j++) {
                    for (StormShape storm : cache.cell(maths, seed, i, j, day, season)) {
                        if (storm.birth <= to && storm.end > from) {
                            action.accept(storm);
                        }
                    }
                }
            }
        }
        for (StormShape storm : placed) {
            if (storm.birth <= to && storm.end > from) {
                action.accept(storm);
            }
        }
    }

    /** The operator's world-wide weather at a game tick, or null. */
    private WeatherRecord.@Nullable Event everywhereAt(long gameTick) {
        WeatherRecord.Event found = null;
        for (WeatherRecord.Event event : events) {
            if (event.kind().everywhere() && event.activeAt(gameTick) && (found == null || event.start() >= found.start())) {
                found = event;
            }
        }
        return found;
    }

    /**
     * When the clearing that holds over a column at a game tick was asked for (a local one around it, or one
     * everywhere), the latest if several; {@link Long#MIN_VALUE} where the sky isn't cleared. Storms placed after it
     * still count.
     */
    long clearedSince(double x, double z, long gameTick) {
        long since = Long.MIN_VALUE;
        for (WeatherRecord.Event event : events) {
            if (!event.activeAt(gameTick)) {
                continue;
            }
            boolean clears = event.kind() == WeatherRecord.EventKind.CLEAR_EVERYWHERE
                    || (event.kind() == WeatherRecord.EventKind.CLEAR_AROUND
                    && StrictMath.hypot(x - event.x(), z - event.z()) <= event.radius());
            if (clears) {
                since = Math.max(since, event.start());
            }
        }
        return since;
    }

    @Override
    public WeatherHere at(double x, double y, double z, long gameTick) {
        List<StormShape> storms = new ArrayList<>();
        double weatherTick = clock.weatherTick(gameTick);
        forEachStorm(x, z, 0.0, weatherTick, weatherTick, storms::add);
        return sample(storms, x, y, z, gameTick, weatherTick);
    }

    /**
     * The weather over a column from these storms (which must include every storm that could be over it): the
     * index's answer and the direct one are this same arithmetic.
     */
    WeatherHere sample(Iterable<StormShape> storms, double x, double y, double z, long gameTick, double weatherTick) {
        WeatherRecord.Era era = eraAt(gameTick);
        if (era == null || !era.regional()) {
            return WeatherHere.CLEAR;
        }
        if (!anyRainsOn(storms, x, y, z, weatherTick) && !anyEverywhere) {
            // Most columns most of the time: no storm's rain overhead and no operator's weather for the world.
            return WeatherHere.CLEAR;
        }
        WeatherRecord.Event everywhere = everywhereAt(gameTick);
        if (everywhere != null && !everywhere.kind().clears()) {
            float thunder = everywhere.kind() == WeatherRecord.EventKind.THUNDER_EVERYWHERE ? 1.0F : 0.0F;
            float[] rain = new float[Climate.values().length];
            float[] thunders = new float[rain.length];
            for (Climate climate : Climate.values()) {
                rain[climate.ordinal()] = 1.0F;
                thunders[climate.ordinal()] = thunder;
            }
            return new WeatherHere(1.0F, thunder, rain, thunders);
        }
        long clearedSince = clearedSince(x, z, gameTick);
        float storm = 0.0F;
        float thunder = 0.0F;
        float[] rain = null;
        float[] thunders = null;
        for (StormShape shape : storms) {
            if (!shape.aliveAt(weatherTick) || !shape.countsAt(gameTick, clearedSince) || y > shape.storm.cloudTop()
                    || !shape.rainsOn(x, z, weatherTick)) {
                continue;
            }
            boolean thundering = shape.thundersOn(x, z, weatherTick);
            // Under a thunderstorm's thunder the rain is at its heaviest, as vanilla's thunder only comes with full rain.
            float level = thundering ? 1.0F : shape.rainLevel();
            storm = Math.max(storm, level);
            if (thundering) {
                thunder = 1.0F;
            }
            for (Climate climate : Climate.values()) {
                if (reaches(shape.storm, era, climate)) {
                    if (rain == null) {
                        rain = new float[Climate.values().length];
                        thunders = new float[rain.length];
                    }
                    rain[climate.ordinal()] = Math.max(rain[climate.ordinal()], level);
                    if (thundering) {
                        thunders[climate.ordinal()] = 1.0F;
                    }
                }
            }
        }
        if (storm == 0.0F) {
            return WeatherHere.CLEAR;
        }
        return new WeatherHere(storm, thunder, rain == null ? new float[3] : rain, thunders == null ? new float[3] : thunders);
    }

    private static boolean anyRainsOn(Iterable<StormShape> storms, double x, double y, double z, double weatherTick) {
        for (StormShape shape : storms) {
            if (shape.aliveAt(weatherTick) && y <= shape.storm.cloudTop() && shape.rainsOn(x, z, weatherTick)) {
                return true;
            }
        }
        return false;
    }

    /** Whether a storm brings rain to a climate under an era's shares: its draw is under the climate's share. */
    static boolean reaches(Storm storm, WeatherRecord.Era era, Climate climate) {
        return storm.reach() * 100.0F < era.share(climate);
    }

    @Override
    public List<Storm> stormsNear(double x, double z, double radius, double weatherTick) {
        return stormsNear(x, z, radius, weatherTick, gameTickOf(weatherTick));
    }

    @Override
    public List<Storm> stormsNear(double x, double z, double radius, double weatherTick, long gameTick) {
        record Near(Storm storm, double distance) {
        }
        List<Near> near = new ArrayList<>();
        forEachStorm(x, z, radius, weatherTick, weatherTick, shape -> {
            if (!shape.aliveAt(weatherTick)) {
                return;
            }
            double distance = StrictMath.hypot(shape.centreX(weatherTick) - x, shape.centreZ(weatherTick) - z);
            if (distance - shape.extent * shape.size(weatherTick) > radius
                    || !shape.countsAt(gameTick, clearedSince(shape.centreX(weatherTick), shape.centreZ(weatherTick), gameTick))) {
                return;
            }
            near.add(new Near(shape.storm, distance));
        });
        near.sort(Comparator.comparingDouble(Near::distance));
        return near.stream().map(Near::storm).toList();
    }

    /**
     * A game tick at a weather tick, for the operator's weather; while the weather stands still, the first. Asking for
     * the storms now, pass the game tick itself instead: a storm placed during a stand comes after its first tick.
     */
    private long gameTickOf(double weatherTick) {
        long gameTick = (long) Math.floor(weatherTick);
        while (clock.weatherTick(gameTick) < weatherTick) {
            gameTick += (long) Math.ceil(weatherTick - clock.weatherTick(gameTick));
        }
        return gameTick;
    }

    @Override
    public Wind wind(double weatherTick) {
        return mathsAt(weatherTick).wind(seed, weatherTick);
    }

    @Override
    public WetSpell wetSpell(double x, double z, long from, long to) {
        int climates = Climate.values().length;
        long horizon = to + HORIZON;
        // Per climate, the spans of the lattice's storms, and of each placed storm by when it was asked for: a
        // clearing takes out the lattice's storms and the storms placed before it, never one placed after it.
        List<List<Spans.Span>> wet = new ArrayList<>(climates);
        List<Map<Long, List<Spans.Span>>> placedWet = new ArrayList<>(climates);
        for (int climate = 0; climate < climates; climate++) {
            wet.add(new ArrayList<>());
            placedWet.add(new HashMap<>());
        }
        long stableUntil = horizon;
        for (EraPiece piece : eraPieces(from, horizon)) {
            stableUntil = piece.start() > to ? Math.min(stableUntil, piece.start()) : stableUntil;
            if (!piece.era().regional()) {
                continue;
            }
            for (WeatherClock.Piece part : clock.pieces(piece.start(), piece.end())) {
                stormSpans(x, z, piece.era(), part, wet, placedWet);
            }
        }
        stableUntil = clock.nextChange(to, stableUntil);
        List<Clearing> clearings = new ArrayList<>();
        List<Spans.Span> everywhere = new ArrayList<>();
        for (WeatherRecord.Event event : events) {
            if (event.end() <= from || event.start() >= horizon) {
                continue;
            }
            Spans.Span span = new Spans.Span(event.start(), Math.min(event.end(), horizon));
            if (event.kind() == WeatherRecord.EventKind.CLEAR_EVERYWHERE
                    || (event.kind() == WeatherRecord.EventKind.CLEAR_AROUND
                    && StrictMath.hypot(x - event.x(), z - event.z()) <= event.radius())) {
                clearings.add(new Clearing(event.start(), span));
            } else if (event.kind() == WeatherRecord.EventKind.RAIN_EVERYWHERE
                    || event.kind() == WeatherRecord.EventKind.THUNDER_EVERYWHERE) {
                everywhere.add(span);
            }
        }
        List<Spans.Span> allClearings = Spans.union(clearings.stream().map(Clearing::span).toList());
        long[] ticks = new long[climates];
        boolean[] wetAtEnd = new boolean[climates];
        double nextEdge = stableUntil;
        for (int climate = 0; climate < climates; climate++) {
            List<Spans.Span> kept = new ArrayList<>(Spans.without(Spans.union(wet.get(climate)), allClearings));
            for (Map.Entry<Long, List<Spans.Span>> placed : placedWet.get(climate).entrySet()) {
                List<Spans.Span> later = Spans.union(clearings.stream()
                        .filter(clearing -> clearing.start() >= placed.getKey()).map(Clearing::span).toList());
                kept.addAll(Spans.without(Spans.union(placed.getValue()), later));
            }
            kept.addAll(regionalOnly(everywhere));
            List<Spans.Span> spans = Spans.union(kept);
            ticks[climate] = Spans.ticks(spans, from, to);
            wetAtEnd[climate] = Spans.covers(spans, to);
            nextEdge = Spans.nextEdge(spans, to, nextEdge);
        }
        List<Spans.Span> operatorSpans = new ArrayList<>(allClearings);
        operatorSpans.addAll(everywhere);
        nextEdge = Spans.nextEdge(operatorSpans, to, nextEdge);
        // A tick counts as wet when its moment is inside a span (Spans.ticks), so what is falling changes at the first
        // whole tick at or after an edge.
        long stable = (long) Math.max(to + 1, Math.ceil(nextEdge));
        return new WetSpell(ticks, wetAtEnd, stable);
    }

    /** An operator's clearing over a column: when it was asked for, and the game ticks it holds. */
    private record Clearing(long start, Spans.Span span) {
    }

    /** The world-wide rain an operator asked for, only where regional weather was on. */
    private List<Spans.Span> regionalOnly(List<Spans.Span> spans) {
        List<Spans.Span> kept = new ArrayList<>();
        for (Spans.Span span : spans) {
            for (EraPiece piece : eraPieces((long) span.start(), (long) span.end())) {
                if (piece.era().regional()) {
                    kept.add(new Spans.Span(piece.start(), piece.end()));
                }
            }
        }
        return kept;
    }

    private record EraPiece(WeatherRecord.Era era, long start, long end) {
    }

    /** Game ticks {@code [from, to)} cut where the era changes; time before the first era is left out. */
    private List<EraPiece> eraPieces(long from, long to) {
        List<EraPiece> pieces = new ArrayList<>();
        for (int index = 0; index < eras.size(); index++) {
            WeatherRecord.Era era = eras.get(index);
            long start = Math.max(from, era.start());
            long end = Math.min(to, index + 1 < eras.size() ? eras.get(index + 1).start() : Long.MAX_VALUE);
            if (end > start) {
                pieces.add(new EraPiece(era, start, end));
            }
        }
        return pieces;
    }

    /**
     * Adds the game ticks each climate is under a storm core reaching it, through one piece of one era: the lattice's
     * storms to {@code wet}, the placed ones to {@code placedWet} by when they were asked for.
     */
    private void stormSpans(double x, double z, WeatherRecord.Era era, WeatherClock.Piece part,
                            List<List<Spans.Span>> wet, List<Map<Long, List<Spans.Span>>> placedWet) {
        double weatherFrom = part.frozen() ? part.weatherStart() : part.start() - part.offset();
        double weatherTo = part.frozen() ? part.weatherStart() : part.end() - part.offset();
        forEachStorm(x, z, 0.0, weatherFrom, weatherTo, shape -> {
            List<Spans.Span> spans;
            if (part.frozen()) {
                spans = shape.aliveAt(weatherFrom) && shape.rainsOn(x, z, weatherFrom)
                        ? List.of(new Spans.Span(part.start(), part.end())) : List.of();
            } else {
                spans = new ArrayList<>();
                for (Spans.Span span : shape.rainSpans(x, z, weatherFrom, weatherTo)) {
                    spans.add(new Spans.Span(span.start() + part.offset(), span.end() + part.offset()));
                }
            }
            if (spans.isEmpty()) {
                return;
            }
            for (Climate climate : Climate.values()) {
                if (!reaches(shape.storm, era, climate)) {
                    continue;
                }
                if (shape.placedAt == Long.MIN_VALUE) {
                    wet.get(climate.ordinal()).addAll(spans);
                } else {
                    // A placed storm counts only from when it was asked for, though it was born full grown before.
                    List<Spans.Span> asked = placedWet.get(climate.ordinal())
                            .computeIfAbsent(shape.placedAt, start -> new ArrayList<>());
                    for (Spans.Span span : spans) {
                        if (span.end() > shape.placedAt) {
                            asked.add(new Spans.Span(Math.max(span.start(), shape.placedAt), span.end()));
                        }
                    }
                }
            }
        });
    }
}
