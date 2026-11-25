package com.thenathe.almanac.weather;

import com.thenathe.almanac.internal.WeatherRecord;
import java.util.ArrayList;
import java.util.List;

/**
 * Weather time: the game time less every stretch the weather stood still ({@code advance_weather} off). Storms live
 * and move in weather time, so while the weather stands still every storm stays where it is, raining where it
 * rained, and moves on from there when the weather moves again.
 */
final class WeatherClock {
    private final List<WeatherRecord.Freeze> freezes;

    WeatherClock(List<WeatherRecord.Freeze> freezes) {
        List<WeatherRecord.Freeze> sorted = new ArrayList<>(freezes);
        sorted.sort((first, second) -> Long.compare(first.start(), second.start()));
        this.freezes = List.copyOf(sorted);
    }

    /** The weather tick at a game tick. */
    double weatherTick(double gameTick) {
        double stood = 0.0;
        for (WeatherRecord.Freeze freeze : freezes) {
            if (freeze.start() >= gameTick) {
                break;
            }
            stood += Math.min(gameTick, (double) freeze.end()) - freeze.start();
        }
        return gameTick - stood;
    }

    /** Whether the weather stands still at a game tick. */
    boolean frozenAt(long gameTick) {
        for (WeatherRecord.Freeze freeze : freezes) {
            if (freeze.start() <= gameTick && gameTick < freeze.end()) {
                return true;
            }
        }
        return false;
    }

    /**
     * A stretch of game time in which the weather either moves with the game ({@code frozen} false: weather tick =
     * game tick - {@code offset}) or stands at one weather tick ({@code frozen} true).
     */
    record Piece(long start, long end, boolean frozen, double offset) {
        double weatherStart() {
            return start - offset;
        }
    }

    /** Game ticks {@code [from, to)} cut where the weather stops or starts again. */
    List<Piece> pieces(long from, long to) {
        List<Piece> pieces = new ArrayList<>();
        long at = from;
        while (at < to) {
            WeatherRecord.Freeze inside = null;
            long nextStart = to;
            for (WeatherRecord.Freeze freeze : freezes) {
                if (freeze.start() <= at && at < freeze.end()) {
                    inside = freeze;
                    break;
                }
                if (freeze.start() > at) {
                    nextStart = Math.min(nextStart, freeze.start());
                }
            }
            double offset = at - weatherTick(at);
            if (inside != null) {
                long end = Math.min(to, inside.end());
                pieces.add(new Piece(at, end, true, offset));
                at = end;
            } else {
                pieces.add(new Piece(at, nextStart, false, offset));
                at = nextStart;
            }
        }
        return pieces;
    }

    /** The next game tick after {@code after} at which the weather stops or starts again, or {@code otherwise}. */
    long nextChange(long after, long otherwise) {
        long next = otherwise;
        for (WeatherRecord.Freeze freeze : freezes) {
            if (freeze.start() > after) {
                next = Math.min(next, freeze.start());
            }
            if (freeze.end() > after && freeze.end() != WeatherRecord.Freeze.OPEN) {
                next = Math.min(next, freeze.end());
            }
        }
        return next;
    }
}
