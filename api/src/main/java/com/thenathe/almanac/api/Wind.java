package com.thenathe.almanac.api;

/**
 * The prevailing wind of Almanac's regional weather at one moment (since 0.4.0): the way storms are carried. It is a
 * smooth function of the weather tick, the same on the server and every client, and wanders over days. Each storm
 * takes the wind at its birth with a turn of its own, and keeps that heading for life ({@link Storm#velocityX}).
 *
 * @param direction where it blows towards, in radians from east (+X) towards south (+Z)
 * @param speed     how far it carries a cloud a tick, in blocks
 * @since 0.4.0
 */
public record Wind(double direction, double speed) {
    public double velocityX() {
        return StrictMath.cos(direction) * speed;
    }

    public double velocityZ() {
        return StrictMath.sin(direction) * speed;
    }
}
