package com.thenathe.almanac.weather;

import com.thenathe.almanac.api.Storm;
import java.util.ArrayList;
import java.util.List;

/**
 * A storm with what every question about it needs worked out once: the turn of its ellipse, its axes, and when it
 * lives. The same arithmetic answers whether a column is under its rain now ({@link #rainsOn}) and when the column is
 * under its rain at all ({@link #rainSpans}), so the two always agree.
 *
 * <p>Where a column sits in the storm's ellipse, measured so the full-grown edge is 1, changes with the storm's drift
 * as a quadratic in time; the storm's size changes linearly in each part of its life (growing, full grown,
 * shrinking). So the times a column is inside the rain core, "distance squared at most the core times the size,
 * squared", are where one quadratic in each part is at most zero: closed form, no stepping through time.
 */
final class StormShape {
    final Storm storm;
    final long birth;
    final long end;
    private final double cos;
    private final double sin;
    private final double majorSquared;
    private final double minorSquared;
    /** How far out the cloud reaches from its centre at most, in blocks. */
    final double extent;
    /**
     * The game tick an operator asked for this storm, for one placed by {@code /weather}; {@link Long#MIN_VALUE} for
     * the lattice's. A clearing asked for before it doesn't clear it: the later command wins.
     */
    final long placedAt;

    StormShape(Storm storm) {
        this(storm, Long.MIN_VALUE);
    }

    StormShape(Storm storm, long placedAt) {
        this.placedAt = placedAt;
        this.storm = storm;
        this.birth = storm.birthTick();
        this.end = storm.endTick();
        this.cos = StrictMath.cos(storm.angle());
        this.sin = StrictMath.sin(storm.angle());
        double major = storm.majorAxis();
        double minor = storm.minorAxis();
        this.majorSquared = major * major;
        this.minorSquared = minor * minor;
        this.extent = major;
    }

    boolean aliveAt(double tick) {
        return tick >= birth && tick < end;
    }

    /**
     * Whether the storm counts over a column at a game tick, as far as operators' weather goes: a placed storm only
     * from when it was asked for, and no storm under a clearing asked for after it ({@code clearedSince}, the latest
     * clearing's start, or {@link Long#MIN_VALUE} for none).
     */
    boolean countsAt(long gameTick, long clearedSince) {
        boolean placed = placedAt != Long.MIN_VALUE;
        if (placed && gameTick < placedAt) {
            return false;
        }
        return clearedSince == Long.MIN_VALUE || (placed && placedAt > clearedSince);
    }

    double centreX(double tick) {
        return storm.centreX(tick);
    }

    double centreZ(double tick) {
        return storm.centreZ(tick);
    }

    /** The column's distance from the centre at {@code tick}, squared, with the full-grown edge at 1. */
    double distanceSquared(double x, double z, double tick) {
        double dx = x - storm.centreX(tick);
        double dz = z - storm.centreZ(tick);
        double along = dx * cos + dz * sin;
        double across = -dx * sin + dz * cos;
        return along * along / majorSquared + across * across / minorSquared;
    }

    double size(double tick) {
        return storm.size(tick);
    }

    boolean rainsOn(double x, double z, double tick) {
        double size = size(tick);
        if (size <= 0.0) {
            return false;
        }
        double core = Storm.RAIN_CORE * size;
        return distanceSquared(x, z, tick) <= core * core;
    }

    boolean thundersOn(double x, double z, double tick) {
        return storm.thunder() && storm.fullGrown(tick)
                && distanceSquared(x, z, tick) <= Storm.THUNDER_CORE * Storm.THUNDER_CORE;
    }

    boolean covers(double x, double z, double tick) {
        double size = size(tick);
        return size > 0.0 && distanceSquared(x, z, tick) <= size * size;
    }

    /** The rain level under its core: heavier storms rain harder, and even the lightest is plainly rain. */
    float rainLevel() {
        return 0.55F + 0.45F * storm.strength();
    }

    /**
     * The weather ticks within {@code [from, to)} that a column is under the storm's rain core, as sorted spans.
     */
    List<Spans.Span> rainSpans(double x, double z, double from, double to) {
        return spans(x, z, from, to, false);
    }

    /** The weather ticks within {@code [from, to)} that a thunderstorm thunders over a column. */
    List<Spans.Span> thunderSpans(double x, double z, double from, double to) {
        if (!storm.thunder()) {
            return List.of();
        }
        return spans(x, z, from, to, true);
    }

    private List<Spans.Span> spans(double x, double z, double from, double to, boolean thunder) {
        double lo = Math.max(from, birth) - birth;
        double hi = Math.min(to, end) - birth;
        if (hi <= lo) {
            return List.of();
        }
        // The column against the storm's centre at birth, and how that moves a tick, in the ellipse's own axes.
        double qx = x - storm.birthX();
        double qz = z - storm.birthZ();
        double alongStart = qx * cos + qz * sin;
        double acrossStart = -qx * sin + qz * cos;
        double alongPace = storm.velocityX() * cos + storm.velocityZ() * sin;
        double acrossPace = -storm.velocityX() * sin + storm.velocityZ() * cos;
        // Distance squared after t ticks: a t^2 + b t + c.
        double a = alongPace * alongPace / majorSquared + acrossPace * acrossPace / minorSquared;
        double b = -2.0 * (alongStart * alongPace / majorSquared + acrossStart * acrossPace / minorSquared);
        double c = alongStart * alongStart / majorSquared + acrossStart * acrossStart / minorSquared;
        double life = storm.lifeTicks();
        double grown = Storm.GROWTH * life;
        double core = thunder ? Storm.THUNDER_CORE : Storm.RAIN_CORE;
        double coreSquared = core * core;
        // The nearest the column comes to the centre, whatever the size: if that's outside the core, it never rains.
        double closest = a > 0.0 ? Math.clamp(-b / (2.0 * a), lo, hi) : (b >= 0.0 ? lo : hi);
        if (a * closest * closest + b * closest + c > coreSquared) {
            return List.of();
        }
        List<Spans.Span> found = new ArrayList<>(2);
        if (thunder) {
            atMostZero(a, b, c - coreSquared, Math.max(lo, grown), Math.min(hi, life - grown), found);
        } else {
            // Growing, size t / grown; full grown, size 1; shrinking, size (life - t) / grown.
            double k = coreSquared / (grown * grown);
            atMostZero(a - k, b, c, lo, Math.min(hi, grown), found);
            atMostZero(a, b, c - coreSquared, Math.max(lo, grown), Math.min(hi, life - grown), found);
            atMostZero(a - k, b + 2.0 * life * k, c - k * life * life, Math.max(lo, life - grown), hi, found);
        }
        if (found.isEmpty()) {
            return List.of();
        }
        List<Spans.Span> joined = Spans.union(found);
        List<Spans.Span> ticks = new ArrayList<>(joined.size());
        for (Spans.Span span : joined) {
            ticks.add(new Spans.Span(span.start() + birth, span.end() + birth));
        }
        return ticks;
    }

    /** Adds the parts of {@code [lo, hi]} where {@code a t^2 + b t + c <= 0}. */
    static void atMostZero(double a, double b, double c, double lo, double hi, List<Spans.Span> out) {
        if (hi <= lo) {
            return;
        }
        double scale = Math.max(Math.abs(a) * hi * hi, Math.max(Math.abs(b) * hi, Math.abs(c)));
        if (Math.abs(a) * hi * hi <= 1e-12 * Math.max(scale, 1e-300)) {
            // Straight line (or flat).
            if (Math.abs(b) * hi <= 1e-12 * Math.max(scale, 1e-300)) {
                if (c <= 0.0) {
                    out.add(new Spans.Span(lo, hi));
                }
                return;
            }
            double root = -c / b;
            if (b > 0.0) {
                add(lo, Math.min(hi, root), out);
            } else {
                add(Math.max(lo, root), hi, out);
            }
            return;
        }
        double discriminant = b * b - 4.0 * a * c;
        if (discriminant < 0.0) {
            if (a < 0.0) {
                out.add(new Spans.Span(lo, hi));
            }
            return;
        }
        double root = Math.sqrt(discriminant);
        double q = -0.5 * (b + Math.copySign(root, b));
        double first = q / a;
        double second = q != 0.0 ? c / q : first;
        double low = Math.min(first, second);
        double high = Math.max(first, second);
        if (a > 0.0) {
            add(Math.max(lo, low), Math.min(hi, high), out);
        } else {
            add(lo, Math.min(hi, low), out);
            add(Math.max(lo, high), hi, out);
        }
    }

    private static void add(double start, double end, List<Spans.Span> out) {
        if (end > start) {
            out.add(new Spans.Span(start, end));
        }
    }
}
