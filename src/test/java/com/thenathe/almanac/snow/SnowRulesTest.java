package com.thenathe.almanac.snow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.snow.SnowRules.Outcome;
import com.thenathe.almanac.snow.SnowRules.SnowSpot;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SnowRulesTest {
    private static final int NATURAL_HEIGHT = 1;

    /** A plains column in winter: cold now, warm in its real climate, free to hold snow on grass. */
    private static SnowSpot temperate(boolean coldNow, int layers, int modLayers) {
        return new SnowSpot(Climate.TEMPERATE, coldNow, false, layers, modLayers, false, true, true);
    }

    private static SnowSpot cold(boolean coldNow, int layers, boolean naturalGround) {
        return new SnowSpot(Climate.COLD, coldNow, true, layers, 0, false, true, naturalGround);
    }

    private static Outcome atOnce(SnowSpot spot, int depthLayers) {
        return SnowRules.snow(spot, depthLayers, true, NATURAL_HEIGHT, false);
    }

    @Test
    void catchUpLaysTheDepthAndRecordsItAsTheSeasons() {
        assertEquals(new Outcome(3, 3, true), atOnce(temperate(true, 0, 0), 3));
        assertEquals(new Outcome(0, 0, true), atOnce(temperate(true, 0, 0), 0));
    }

    @Test
    void gradualWorkMovesOneLayerAtATime() {
        assertEquals(new Outcome(1, 1, false), SnowRules.snow(temperate(true, 0, 0), 3, true, 1, true));
        assertEquals(new Outcome(3, 3, true), SnowRules.snow(temperate(true, 2, 2), 3, true, 1, true));
        assertEquals(new Outcome(2, 2, false), SnowRules.snow(temperate(false, 3, 3), 3, true, 1, true));
    }

    @Test
    void theSeasonsSnowMeltsWhenItIsWarm() {
        assertEquals(new Outcome(0, 0, true), atOnce(temperate(false, 2, 2), 2));
    }

    @Test
    void onlyTheSeasonsLayersMeltOffNativeOrPlayerSnow() {
        // Two seasonal layers on a player's single layer: the player's stays.
        SnowSpot onPlayerSnow = new SnowSpot(Climate.TEMPERATE, false, false, 3, 2, true, true, true);
        assertEquals(new Outcome(1, 0, true), atOnce(onPlayerSnow, 0));
        // Unrecorded snow in a TEMPERATE biome is a player's or altitude snow: it never melts.
        assertEquals(new Outcome(1, 0, true), atOnce(temperate(false, 1, 0), 0));
    }

    @Test
    void altitudeSnowStays() {
        // Windswept hills high up: the real climate snows there, so the snow is native and never removed.
        SnowSpot altitude = new SnowSpot(Climate.TEMPERATE, false, true, 1, 0, false, true, true);
        assertEquals(1, atOnce(altitude, 0).layers());
        // Snow the season adds there is native too, so nothing is recorded.
        SnowSpot altitudeInWinter = new SnowSpot(Climate.TEMPERATE, true, true, 0, 0, false, true, true);
        assertEquals(new Outcome(1, 0, true), atOnce(altitudeInWinter, 1));
    }

    @Test
    void nativeColdSnowMeltsInSummerOnlyAsThinSnowOnNaturalGround() {
        assertEquals(0, atOnce(cold(false, 1, true), 0).layers());
        // Built-on, deeper than natural, or while it's still cold: it stays.
        assertEquals(1, atOnce(cold(false, 1, false), 0).layers());
        assertEquals(3, atOnce(cold(false, 3, true), 0).layers());
        assertEquals(1, atOnce(cold(true, 1, true), 0).layers());
        // With the setting off, native snow never melts.
        assertEquals(1, SnowRules.snow(cold(false, 1, true), 0, false, NATURAL_HEIGHT, false).layers());
        // A higher snow height game rule makes deeper native snow natural.
        assertEquals(0, SnowRules.snow(cold(false, 3, true), 0, true, 3, false).layers());
    }

    @Test
    void playersSnowNeverMeltsAndGetsNothingAdded() {
        SnowSpot player = new SnowSpot(Climate.COLD, false, true, 1, 0, true, true, true);
        assertEquals(1, atOnce(player, 0).layers());
        SnowSpot playerInWinter = new SnowSpot(Climate.TEMPERATE, true, false, 1, 0, true, true, true);
        assertEquals(1, atOnce(playerInWinter, 4).layers());
    }

    @Test
    void snowIsOnlyAddedWhereItCanLie() {
        SnowSpot noSupport = new SnowSpot(Climate.TEMPERATE, true, false, 0, 0, false, false, true);
        assertEquals(new Outcome(0, 0, true), atOnce(noSupport, 2));
    }

    @Test
    void nativeColdSnowReturnsAsNativeSnow() {
        // Fall in a snowy biome: the snow comes back, and since the real climate snows there it isn't recorded.
        assertEquals(new Outcome(1, 0, true), atOnce(cold(true, 0, true), 1));
    }

    @Test
    void iceRules() {
        assertTrue(SnowRules.freezes(true, true, true));
        assertFalse(SnowRules.freezes(true, true, false));
        assertFalse(SnowRules.freezes(true, false, true));
        assertFalse(SnowRules.freezes(false, true, true));
        assertTrue(SnowRules.iceMelts(true, false));
        // Native ice stays frozen, and nothing melts while it's cold.
        assertFalse(SnowRules.iceMelts(false, false));
        assertFalse(SnowRules.iceMelts(true, true));
    }

    @Test
    void onlyWaterThatWillFreezeHoldsBackTheIce() {
        // Still water on top, where the season brings ice: the ice waits for it to freeze first.
        assertTrue(SnowRules.holdsBackFreezing(true, true, true, true, true));
        // Kelp, seagrass, waterlogged blocks and flowing water aren't still water; they count as shore.
        assertFalse(SnowRules.holdsBackFreezing(false, true, true, true, true));
        // Water under a dock or a lily pad, oceans, warm water and lit water never freeze here either.
        assertFalse(SnowRules.holdsBackFreezing(true, false, true, true, true));
        assertFalse(SnowRules.holdsBackFreezing(true, true, false, true, true));
        assertFalse(SnowRules.holdsBackFreezing(true, true, true, false, true));
        assertFalse(SnowRules.holdsBackFreezing(true, true, true, true, false));
    }

    @Test
    void aPassThatCantGetAnyFurtherCountsAsDone() {
        // More to do, and something changed here or elsewhere since: another pass.
        assertEquals(SnowUpdates.Next.AGAIN, SnowUpdates.next(true, false, true, true));
        assertEquals(SnowUpdates.Next.AGAIN, SnowUpdates.next(true, false, false, false));
        // Water ringed by water that never comes, with nothing changing anywhere: done, not scanned all winter.
        assertEquals(SnowUpdates.Next.DONE, SnowUpdates.next(true, false, false, true));
        // Waiting only for the thaw or the snow forming: asleep until the next patch.
        assertEquals(SnowUpdates.Next.SLEEP, SnowUpdates.next(false, true, true, false));
        assertEquals(SnowUpdates.Next.SLEEP, SnowUpdates.next(true, true, false, true));
        assertEquals(SnowUpdates.Next.DONE, SnowUpdates.next(false, false, true, false));
    }

    @Test
    void onlyChangesThatCanTakeSnowAwayStartAThaw() {
        SnowState winter = new SnowState(SubSeason.MID_WINTER, Map.of(Climate.TEMPERATE, 1, Climate.COLD, 1), true, 1);
        SnowState moreSnow = new SnowState(SubSeason.MID_WINTER, Map.of(Climate.TEMPERATE, 2, Climate.COLD, 1), true, 1);
        SnowState lessSnow = new SnowState(SubSeason.MID_WINTER, Map.of(Climate.COLD, 1), true, 1);
        SnowState spring = new SnowState(SubSeason.EARLY_SPRING, Map.of(Climate.TEMPERATE, 1, Climate.COLD, 1), true, 1);
        SnowState meltOff = new SnowState(SubSeason.MID_WINTER, Map.of(Climate.TEMPERATE, 1, Climate.COLD, 1), false, 1);
        assertTrue(winter.mayThawFrom(null));
        assertFalse(moreSnow.mayThawFrom(winter));
        assertTrue(lessSnow.mayThawFrom(winter));
        assertTrue(spring.mayThawFrom(winter));
        assertFalse(meltOff.mayThawFrom(winter));
        assertTrue(winter.mayThawFrom(meltOff));
    }
}
