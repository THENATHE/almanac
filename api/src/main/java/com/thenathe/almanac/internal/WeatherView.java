package com.thenathe.almanac.internal;

import com.thenathe.almanac.api.Storm;
import com.thenathe.almanac.api.Wind;
import java.util.List;

/**
 * The overworld's regional weather as one side knows it at one moment: the weather seed and the
 * {@link WeatherRecord}, and everything worked out from them. A view never changes, so it can be handed to another
 * thread; a newer one replaces it when the record changes. The server and a client with the same record give the
 * same answers, for any place and any tick.
 */
public interface WeatherView {
    WeatherRecord record();

    /** Whether regional weather decides the rain at a game tick (an era with storm maths in force). */
    boolean regional(long gameTick);

    /** The weather tick at a game tick: the game time less the time the weather stood still before it. */
    double weatherTick(double gameTick);

    /** The weather over a column at a height and game tick. Nothing falls above a storm's cloud top. */
    WeatherHere at(double x, double y, double z, long gameTick);

    /** The storms whose clouds come within {@code radius} of a column at a weather tick, nearest first. */
    List<Storm> stormsNear(double x, double z, double radius, double weatherTick);

    /**
     * The same, with operators' storms and clearings as they stand at a game tick. While the weather stands still one
     * weather tick lasts many game ticks, and a storm placed or a clearing asked for during the stand counts from its own
     * game tick, as it does for the weather at a place ({@link #at}); this is what to ask for the storms now.
     */
    List<Storm> stormsNear(double x, double z, double radius, double weatherTick, long gameTick);

    Wind wind(double weatherTick);

    /** How long rain or snow fell on a column through game ticks {@code from} to {@code to}, per climate. */
    WetSpell wetSpell(double x, double z, long from, long to);
}
