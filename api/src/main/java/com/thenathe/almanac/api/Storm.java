package com.thenathe.almanac.api;

/**
 * One storm of Almanac's regional weather (since 0.4.0): a cloud shaped like an ellipse that is born somewhere,
 * grows, drifts in a straight line at a steady speed, holds, shrinks and is gone. Everything about it is fixed when it
 * is born, so where it is at any moment follows from these numbers alone: {@link #centreX} and {@link #centreZ} place
 * it at any tick, partial ticks included, with no further data.
 *
 * <p>Times are weather ticks ({@link AlmanacApi#weatherTick}): the game time, less any time the weather stood still
 * because the {@code advance_weather} game rule was off. Positions are in blocks.
 *
 * <p>The shape at full size is an ellipse with semi-axes {@link #majorAxis} (along {@link #angle}) and
 * {@link #minorAxis}; its area is that of a circle of {@link #radius}. It grows from nothing over the first
 * {@link #GROWTH} of its life and shrinks back over the last, so its size at a moment is {@link #size} times that.
 * Rain or snow falls in the inner {@link #RAIN_CORE} of it (the rest is a shield of cloud), and a thunderstorm
 * thunders in the inner {@link #THUNDER_CORE} while it is full grown. Nothing falls and no thunder is heard above its
 * {@link #cloudTop}.
 *
 * @param id        the same storm has the same id on the server and every client, for as long as it lives
 * @param birthX    where its centre is at {@code birthTick}
 * @param birthZ    where its centre is at {@code birthTick}
 * @param birthTick the weather tick it is born at, with no size
 * @param lifeTicks how long it lives, in ticks
 * @param velocityX how far its centre moves a tick, in blocks
 * @param velocityZ how far its centre moves a tick, in blocks
 * @param radius    the radius of a circle of its full-grown area, in blocks
 * @param stretch   how much longer the ellipse is than it is wide, 1 or more: a front is long and thin
 * @param angle     the direction of its long axis, in radians from east (+X) towards south (+Z)
 * @param strength  how heavy its rain or snow is, 0 to 1
 * @param thunder   whether it is a thunderstorm
 * @param cloudTop  the block height of the top of its cloud
 * @param reach     its draw against the rain shares, 0 to 1: it brings rain or snow to a climate whose share this
 *                  sub-season is more than {@code reach * 100} percent, and passes over the others dry
 * @since 0.4.0
 */
public record Storm(long id, double birthX, double birthZ, long birthTick, int lifeTicks, double velocityX,
                    double velocityZ, double radius, double stretch, double angle, float strength, boolean thunder,
                    int cloudTop, float reach) {
    /** How far out, as a part of the cloud's size, rain or snow falls. */
    public static final double RAIN_CORE = 0.8;
    /** How far out a thunderstorm thunders while it is full grown. */
    public static final double THUNDER_CORE = 0.5;
    /**
     * The part of its life a storm takes to grow, and again to shrink at the end. Chosen so the typical storm grows no
     * faster than it moves: while it grows its back edge then never creeps against the wind, and while it shrinks its
     * front edge never falls back, so a storm reads as one that arrives and passes rather than one that swells in
     * place. Measured on a year of storms in Almanac's calibration test.
     */
    public static final double GROWTH = 0.29;

    /** The weather tick it is gone at. */
    public long endTick() {
        return birthTick + lifeTicks;
    }

    public boolean aliveAt(double tick) {
        return tick >= birthTick && tick < endTick();
    }

    /** How far through its life it is at {@code tick}: 0 at birth, 1 at its end. */
    public double phase(double tick) {
        return Math.clamp((tick - birthTick) / lifeTicks, 0.0, 1.0);
    }

    /** Its size at {@code tick} as a part of full grown: growing from 0, holding at 1, shrinking back to 0. */
    public double size(double tick) {
        double phase = phase(tick);
        if (!aliveAt(tick)) {
            return 0.0;
        }
        if (phase < GROWTH) {
            return phase / GROWTH;
        }
        if (phase > 1.0 - GROWTH) {
            return (1.0 - phase) / GROWTH;
        }
        return 1.0;
    }

    /** Whether it is full grown at {@code tick}, the only time a thunderstorm thunders. */
    public boolean fullGrown(double tick) {
        double phase = phase(tick);
        return aliveAt(tick) && phase >= GROWTH && phase <= 1.0 - GROWTH;
    }

    public double centreX(double tick) {
        return birthX + velocityX * (tick - birthTick);
    }

    public double centreZ(double tick) {
        return birthZ + velocityZ * (tick - birthTick);
    }

    /** The long semi-axis at full size, in blocks. */
    public double majorAxis() {
        return radius * Math.sqrt(stretch);
    }

    /** The short semi-axis at full size, in blocks. */
    public double minorAxis() {
        return radius / Math.sqrt(stretch);
    }

    /**
     * How far a column is from the storm's centre at {@code tick}, measured in its full-grown ellipse: 0 at the
     * centre and 1 on the edge of the full-grown cloud. The cloud covers the column while this is at most
     * {@link #size}, and rain falls there while it is at most {@code RAIN_CORE * size}.
     */
    public double distance(double x, double z, double tick) {
        double dx = x - centreX(tick);
        double dz = z - centreZ(tick);
        double cos = StrictMath.cos(angle);
        double sin = StrictMath.sin(angle);
        double along = (dx * cos + dz * sin) / majorAxis();
        double across = (-dx * sin + dz * cos) / minorAxis();
        return Math.sqrt(along * along + across * across);
    }

    /** Whether the cloud covers a column at {@code tick}. */
    public boolean covers(double x, double z, double tick) {
        double size = size(tick);
        return size > 0.0 && distance(x, z, tick) <= size;
    }

    /** Whether rain or snow from it falls on a column at {@code tick} (below its cloud top). */
    public boolean rainsOn(double x, double z, double tick) {
        double size = size(tick);
        return size > 0.0 && distance(x, z, tick) <= RAIN_CORE * size;
    }

    /** Whether it thunders over a column at {@code tick}. */
    public boolean thundersOn(double x, double z, double tick) {
        return thunder && fullGrown(tick) && distance(x, z, tick) <= THUNDER_CORE;
    }
}
