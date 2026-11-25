package com.thenathe.almanac.internal;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SnowClocks;
import com.thenathe.almanac.api.SnowLayers;
import java.util.EnumMap;
import java.util.Map;

/**
 * The season's snow and ice in each climate at one moment, and how they are moving, as the public API reads them: on
 * the server from the snow module, on a client from the season sync, followed forward to the client's game time
 * ({@link #after}). Between two changes of the weather both move at a steady pace, so a client needs a new sync only
 * when the weather changes.
 *
 * @param depths       each climate's snow depth in layers (the sum of its layers' shares)
 * @param ice          each climate's ice cover, 0 to 1: how far still water has frozen over
 * @param depthPerTick how fast each depth is moving now, in layers a tick (below 0 in a thaw)
 * @param icePerTick   how fast each ice cover is moving now
 * @param clocks       the clocks and the most layers in force now
 */
public record SnowReading(Map<Climate, Double> depths, Map<Climate, Double> ice, Map<Climate, Double> depthPerTick,
                          Map<Climate, Double> icePerTick, SnowClocks clocks) {
    public SnowReading {
        depths = complete(depths, 0.0, clocks.maxLayers());
        ice = complete(ice, 0.0, 1.0);
        depthPerTick = complete(depthPerTick, -1.0, 1.0);
        icePerTick = complete(icePerTick, -1.0, 1.0);
    }

    private static Map<Climate, Double> complete(Map<Climate, Double> values, double min, double max) {
        Map<Climate, Double> all = new EnumMap<>(Climate.class);
        for (Climate climate : Climate.values()) {
            double value = values.getOrDefault(climate, 0.0);
            all.put(climate, Double.isFinite(value) ? Math.clamp(value, min, max) : 0.0);
        }
        return Map.copyOf(all);
    }

    public double depth(Climate climate) {
        return depths.get(climate);
    }

    public double ice(Climate climate) {
        return ice.get(climate);
    }

    public SnowLayers layers(Climate climate) {
        return SnowLayers.of(depth(climate), clocks.maxLayers());
    }

    /** The same snow {@code ticks} later, if the weather doesn't change meanwhile. */
    public SnowReading after(long ticks) {
        if (ticks <= 0) {
            return this;
        }
        Map<Climate, Double> laterDepths = new EnumMap<>(Climate.class);
        Map<Climate, Double> laterIce = new EnumMap<>(Climate.class);
        for (Climate climate : Climate.values()) {
            laterDepths.put(climate, depth(climate) + depthPerTick.get(climate) * ticks);
            laterIce.put(climate, ice(climate) + icePerTick.get(climate) * ticks);
        }
        return new SnowReading(laterDepths, laterIce, depthPerTick, icePerTick, clocks);
    }
}
