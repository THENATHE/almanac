package com.thenathe.almanac.weather;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stretches of time as sorted lists of half-open spans {@code [start, end)}, for working out how long a column was
 * under rain: the spans of every storm over it joined, those an operator cleared taken out.
 */
final class Spans {
    private Spans() {
    }

    /** A span of time; {@code start <= end}. */
    record Span(double start, double end) {
        double length() {
            return end - start;
        }
    }

    /** The spans joined where they overlap or touch, sorted. */
    static List<Span> union(List<Span> spans) {
        if (spans.size() < 2) {
            return spans.isEmpty() ? List.of() : List.of(spans.getFirst());
        }
        List<Span> sorted = new ArrayList<>(spans);
        sorted.sort(Comparator.comparingDouble(Span::start));
        List<Span> joined = new ArrayList<>();
        Span open = sorted.getFirst();
        for (int index = 1; index < sorted.size(); index++) {
            Span next = sorted.get(index);
            if (next.start() <= open.end()) {
                open = new Span(open.start(), Math.max(open.end(), next.end()));
            } else {
                joined.add(open);
                open = next;
            }
        }
        joined.add(open);
        return joined;
    }

    /** {@code spans} (sorted, joined) without any time in {@code removed} (sorted, joined). */
    static List<Span> without(List<Span> spans, List<Span> removed) {
        if (removed.isEmpty() || spans.isEmpty()) {
            return spans;
        }
        List<Span> left = new ArrayList<>();
        int next = 0;
        for (Span span : spans) {
            double from = span.start();
            while (next < removed.size() && removed.get(next).end() <= from) {
                next++;
            }
            int cut = next;
            while (cut < removed.size() && removed.get(cut).start() < span.end()) {
                Span hole = removed.get(cut);
                if (hole.start() > from) {
                    left.add(new Span(from, hole.start()));
                }
                from = Math.max(from, hole.end());
                cut++;
            }
            if (from < span.end()) {
                left.add(new Span(from, span.end()));
            }
        }
        return left;
    }

    /** How much of {@code [from, to)} the spans (sorted, joined) cover. */
    static double measure(List<Span> spans, double from, double to) {
        double total = 0.0;
        for (Span span : spans) {
            double start = Math.max(from, span.start());
            double end = Math.min(to, span.end());
            if (end > start) {
                total += end - start;
            }
        }
        return total;
    }

    /**
     * How many whole ticks {@code t} in {@code [from, to)} the spans (sorted, joined) cover, a tick counting when its
     * moment is inside a span. Counting ticks rather than measuring time keeps stretches adding up exactly: the ticks
     * of {@code [a, b)} and of {@code [b, c)} are those of {@code [a, c)}, however the stretch is cut.
     */
    static long ticks(List<Span> spans, long from, long to) {
        long total = 0;
        for (Span span : spans) {
            long first = Math.max(from, (long) Math.ceil(span.start()));
            long last = Math.min(to, (long) Math.ceil(span.end()));
            if (last > first) {
                total += last - first;
            }
        }
        return total;
    }

    /** Whether the spans (sorted, joined) cover the moment {@code at}. */
    static boolean covers(List<Span> spans, double at) {
        for (Span span : spans) {
            if (span.start() <= at && at < span.end()) {
                return true;
            }
        }
        return false;
    }

    /** The first edge of a span (a start or an end) after {@code after}, or {@code otherwise} if there is none. */
    static double nextEdge(List<Span> spans, double after, double otherwise) {
        double next = otherwise;
        for (Span span : spans) {
            if (span.start() > after) {
                next = Math.min(next, span.start());
            }
            if (span.end() > after) {
                next = Math.min(next, span.end());
            }
        }
        return next;
    }
}
