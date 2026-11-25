package com.thenathe.almanac.internal;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SnowLayers;
import java.util.Map;

/** Where snow lies now, as far terrain and {@code /almanac snow} show it: the snow module's {@code SnowState}. */
public interface SnowNow {
    /** How many layers of snow have begun in each climate (any share at all), every climate present. */
    Map<Climate, Integer> layers();

    int layers(Climate climate);

    /** Each layer's share of the open ground in a climate. */
    SnowLayers shares(Climate climate);

    /** How far still water in a climate has frozen over, 0 to 1. */
    double ice(Climate climate);

    boolean nativeSnowMelts();

    /** The {@code max_snow_accumulation_height} game rule. */
    int snowHeight();

    /** The most layers natural snow reaches: the game rule, at least one. */
    int naturalHeight();

    /** How deep the season's snow may build, in layers. */
    int maxLayers();
}
