package com.thenathe.almanac.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** How a depth lies layer by layer: the 90 % rule both ways and the most layers (user, 2026-11-22 to 24). */
class SnowLayersTest {
    private static final double EPSILON = 1e-12;

    @Test
    void theFirstLayerHasTheGroundToItselfUntilItIs90PercentDown() {
        for (double depth = 0.0; depth <= 0.9; depth += 0.05) {
            SnowLayers layers = SnowLayers.of(depth, 3);
            assertEquals(depth, layers.cover(), EPSILON);
            assertEquals(0.0, layers.share(2), "no second layer before the first is 90 % down");
        }
        assertEquals(1, SnowLayers.of(0.9, 3).started());
    }

    @Test
    void secondLayersStartOnceTheFirstIs90PercentDownAndTheTwoShareTheSnowfall() {
        SnowLayers justAfter = SnowLayers.of(0.92, 3);
        assertEquals(0.91, justAfter.share(1), EPSILON);
        assertEquals(0.01, justAfter.share(2), EPSILON);
        SnowLayers oneLayerOfSnow = SnowLayers.of(1.0, 3);
        assertEquals(0.95, oneLayerOfSnow.share(1), EPSILON);
        assertEquals(0.05, oneLayerOfSnow.share(2), EPSILON);
        SnowLayers whole = SnowLayers.of(1.1, 3);
        assertEquals(1.0, whole.share(1), EPSILON, "the first layer is whole once the second has a tenth");
        assertEquals(0.1, whole.share(2), EPSILON);
        assertEquals(0.6, SnowLayers.of(1.6, 3).share(2), EPSILON, "then the second has the snowfall to itself");
    }

    @Test
    void everyFurtherLayerStartsWhenTheOneUnderItIs90PercentDown() {
        for (int layer = 2; layer <= 5; layer++) {
            // The layer under starts sharing 0.1 of depth before it would be whole.
            SnowLayers before = SnowLayers.of(layer - 1.1, 5);
            SnowLayers after = SnowLayers.of(layer - 1.1 + 1e-9, 5);
            assertEquals(0.9, before.share(layer - 1), 1e-6, "layer " + (layer - 1) + " is 90 % down");
            assertEquals(0.0, before.share(layer));
            assertTrue(after.share(layer) > 0.0, "layer " + layer + " has begun");
        }
    }

    @Test
    void theThawTakesTheTopLayerFirstTheSameRuleBackwards() {
        // Melting from two whole layers: the second goes alone until it is down to a tenth, then both go.
        SnowLayers twoDeep = SnowLayers.of(2.0, 2);
        assertEquals(1.0, twoDeep.share(1), EPSILON);
        assertEquals(1.0, twoDeep.share(2), EPSILON);
        assertEquals(1.0, SnowLayers.of(1.5, 2).share(1), EPSILON, "the first layer waits");
        assertEquals(0.5, SnowLayers.of(1.5, 2).share(2), EPSILON);
        assertEquals(1.0, SnowLayers.of(1.1, 2).share(1), EPSILON);
        assertEquals(0.95, SnowLayers.of(1.0, 2).share(1), EPSILON, "then it starts to go with the last of the second");
        assertEquals(0.9, SnowLayers.of(0.9, 2).share(1), EPSILON);
        assertEquals(0.0, SnowLayers.of(0.9, 2).share(2), EPSILON);
    }

    @Test
    void sharesAlwaysAddUpToTheDepthAndNeverOutgrowTheLayerUnder() {
        for (int most = 1; most <= SnowLayers.MOST_LAYERS; most++) {
            for (double depth = 0.0; depth <= most; depth += 0.0137) {
                SnowLayers layers = SnowLayers.of(depth, most);
                double sum = 0.0;
                for (int layer = 1; layer <= most; layer++) {
                    sum += layers.share(layer);
                    if (layer > 1) {
                        assertTrue(layers.share(layer) <= layers.share(layer - 1) + EPSILON, depth + " layer " + layer);
                    }
                }
                assertEquals(depth, sum, 1e-9, "depth " + depth + " up to " + most);
            }
        }
    }

    @Test
    void theSharesMoveSmoothlyWithTheDepth() {
        for (int most = 1; most <= 4; most++) {
            SnowLayers before = SnowLayers.of(0.0, most);
            for (double depth = 0.001; depth <= most; depth += 0.001) {
                SnowLayers now = SnowLayers.of(depth, most);
                for (int layer = 1; layer <= most; layer++) {
                    assertTrue(Math.abs(now.share(layer) - before.share(layer)) <= 0.0011, "no jumps: " + depth);
                }
                before = now;
            }
        }
    }

    @Test
    void snowStopsAtTheMostLayersWithTheTopOneWhole() {
        SnowLayers full = SnowLayers.of(99.0, 3);
        assertEquals(3.0, full.depth());
        assertEquals(1.0, full.share(3), EPSILON);
        assertEquals(0.0, full.share(4));
        assertEquals(3, full.layersAt(0.999));
        SnowLayers oneDeep = SnowLayers.of(1.0, 1);
        assertEquals(1.0, oneDeep.cover(), EPSILON, "with a single layer allowed there is nothing to share with");
    }

    @Test
    void aColumnHoldsALayerForEveryShareItsPointIsUnder() {
        SnowLayers layers = SnowLayers.of(1.6, 3);
        assertEquals(2, layers.layersAt(0.1));
        assertEquals(2, layers.layersAt(0.59));
        assertEquals(1, layers.layersAt(0.61));
        assertEquals(0, SnowLayers.of(0.4, 3).layersAt(0.5));
        // Over many evenly spread points the share of columns with at least n layers is the share of layer n, and the
        // average number of layers is the depth.
        Random random = new Random(7);
        int columns = 200_000;
        int[] atLeast = new int[4];
        long total = 0;
        SnowLayers deep = SnowLayers.of(2.3, 3);
        for (int column = 0; column < columns; column++) {
            int held = deep.layersAt(random.nextDouble());
            total += held;
            for (int layer = 1; layer <= held; layer++) {
                atLeast[layer]++;
            }
        }
        for (int layer = 1; layer <= 3; layer++) {
            assertEquals(deep.share(layer), atLeast[layer] / (double) columns, 0.005, "layer " + layer);
        }
        assertEquals(2.3, total / (double) columns, 0.01);
    }
}
