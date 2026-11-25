package com.thenathe.almanac.weather;

import com.thenathe.almanac.internal.WeatherHere;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * The storms near some places for a short stretch of weather time, sorted into squares, so the questions vanilla
 * asks every tick (is it raining on this entity, this chunk, this block) cost a look at the one or two storms over a
 * square instead of the few hundred lattice cells around it. Built off the server thread from one
 * {@link WeatherField} and never changed after; a question it doesn't cover (another place, a later tick) is answered
 * by the field directly, with the same arithmetic, so the answer is the same either way.
 */
final class StormIndex {
    /** The side of a square, in blocks. */
    static final int SQUARE = 128;
    private static final StormShape[] NONE = new StormShape[0];

    /** One place to cover: a square around a centre. */
    record Area(double x, double z, double halfSide) {
    }

    private final WeatherField field;
    private final double fromTick;
    private final double toTick;
    private final Long2ObjectOpenHashMap<StormShape[]> squares;
    private final int storms;

    private StormIndex(WeatherField field, double fromTick, double toTick, Long2ObjectOpenHashMap<StormShape[]> squares,
                       int storms) {
        this.field = field;
        this.fromTick = fromTick;
        this.toTick = toTick;
        this.squares = squares;
        this.storms = storms;
    }

    /** The storms over {@code areas} between weather ticks {@code from} and {@code to}, both included. */
    static StormIndex build(WeatherField field, List<Area> areas, double from, double to) {
        Long2ObjectOpenHashMap<List<StormShape>> found = new Long2ObjectOpenHashMap<>();
        for (Area area : areas) {
            int firstX = Math.floorDiv((int) Math.floor(area.x() - area.halfSide()), SQUARE);
            int lastX = Math.floorDiv((int) Math.floor(area.x() + area.halfSide()), SQUARE);
            int firstZ = Math.floorDiv((int) Math.floor(area.z() - area.halfSide()), SQUARE);
            int lastZ = Math.floorDiv((int) Math.floor(area.z() + area.halfSide()), SQUARE);
            for (int squareX = firstX; squareX <= lastX; squareX++) {
                for (int squareZ = firstZ; squareZ <= lastZ; squareZ++) {
                    found.computeIfAbsent(key(squareX, squareZ), unused -> new ArrayList<>());
                }
            }
        }
        Set<StormShape> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Area area : areas) {
            field.forEachStorm(area.x(), area.z(), area.halfSide() * Math.sqrt(2.0) + SQUARE, from, to, storm -> {
                if (!seen.add(storm)) {
                    return;
                }
                double start = Math.max(from, storm.birth);
                double end = Math.min(to, storm.end);
                double minX = Math.min(storm.centreX(start), storm.centreX(end)) - storm.extent;
                double maxX = Math.max(storm.centreX(start), storm.centreX(end)) + storm.extent;
                double minZ = Math.min(storm.centreZ(start), storm.centreZ(end)) - storm.extent;
                double maxZ = Math.max(storm.centreZ(start), storm.centreZ(end)) + storm.extent;
                int firstX = (int) Math.floor(minX / SQUARE);
                int lastX = (int) Math.floor(maxX / SQUARE);
                int firstZ = (int) Math.floor(minZ / SQUARE);
                int lastZ = (int) Math.floor(maxZ / SQUARE);
                for (int squareX = firstX; squareX <= lastX; squareX++) {
                    for (int squareZ = firstZ; squareZ <= lastZ; squareZ++) {
                        List<StormShape> list = found.get(key(squareX, squareZ));
                        if (list != null) {
                            list.add(storm);
                        }
                    }
                }
            });
        }
        Long2ObjectOpenHashMap<StormShape[]> squares = new Long2ObjectOpenHashMap<>(found.size());
        for (Long2ObjectOpenHashMap.Entry<List<StormShape>> entry : found.long2ObjectEntrySet()) {
            List<StormShape> list = entry.getValue();
            squares.put(entry.getLongKey(), list.isEmpty() ? NONE : list.toArray(NONE));
        }
        return new StormIndex(field, from, to, squares, seen.size());
    }

    private static long key(int squareX, int squareZ) {
        return ((long) squareX << 32) ^ (squareZ & 0xFFFF_FFFFL);
    }

    WeatherField field() {
        return field;
    }

    /** Whether the index holds every storm that could be over a column at a weather tick. */
    boolean covers(double x, double z, double weatherTick) {
        return weatherTick >= fromTick && weatherTick <= toTick
                && squares.containsKey(key((int) Math.floor(x / SQUARE), (int) Math.floor(z / SQUARE)));
    }

    /** The weather over a column at a game tick from the index, or null where the index doesn't cover it. */
    @Nullable WeatherHere tryAt(double x, double y, double z, long gameTick) {
        double weatherTick = field.weatherTick(gameTick);
        if (weatherTick < fromTick || weatherTick > toTick) {
            return null;
        }
        StormShape[] storms = squares.get(key((int) Math.floor(x / SQUARE), (int) Math.floor(z / SQUARE)));
        return storms == null ? null : field.sample(Arrays.asList(storms), x, y, z, gameTick, weatherTick);
    }

    /** The weather over a column at a game tick: from the index where it covers it, and from the field elsewhere. */
    WeatherHere at(double x, double y, double z, long gameTick) {
        double weatherTick = field.weatherTick(gameTick);
        StormShape[] storms = weatherTick >= fromTick && weatherTick <= toTick
                ? squares.get(key((int) Math.floor(x / SQUARE), (int) Math.floor(z / SQUARE))) : null;
        if (storms == null) {
            return field.at(x, y, z, gameTick);
        }
        return field.sample(Arrays.asList(storms), x, y, z, gameTick, weatherTick);
    }

    double fromTick() {
        return fromTick;
    }

    double toTick() {
        return toTick;
    }

    int squares() {
        return squares.size();
    }

    int storms() {
        return storms;
    }
}
