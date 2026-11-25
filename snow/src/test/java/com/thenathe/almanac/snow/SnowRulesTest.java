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

    /** In a sub-season that is cold for the climate. */
    private static Outcome atOnce(SnowSpot spot, int target) {
        return SnowRules.snow(spot, target, true, true, NATURAL_HEIGHT);
    }

    /** In a sub-season that is warm for the climate: its snow is melting. */
    private static Outcome thawing(SnowSpot spot, int target) {
        return SnowRules.snow(spot, target, false, true, NATURAL_HEIGHT);
    }

    @Test
    void catchUpLaysTheDepthAndRecordsItAsTheSeasons() {
        assertEquals(new Outcome(3, 3), atOnce(temperate(true, 0, 0), 3));
        assertEquals(new Outcome(0, 0), atOnce(temperate(true, 0, 0), 0));
    }

    @Test
    void aColumnGoesStraightToItsTarget() {
        assertEquals(new Outcome(3, 3), atOnce(temperate(true, 1, 1), 3));
        assertEquals(new Outcome(1, 1), thawing(temperate(true, 3, 3), 1));
    }

    @Test
    void inTheColdTheSeasonsSnowOnlyComes() {
        // Snow ahead of the column's share (vanilla's, or laid by an older version) waits for the thaw.
        assertEquals(new Outcome(3, 3), atOnce(temperate(true, 3, 3), 1));
        // In a thaw it goes down to the target, and where the spot is too warm it goes whatever the season.
        assertEquals(new Outcome(1, 1), thawing(temperate(true, 3, 3), 1));
        assertEquals(new Outcome(0, 0), atOnce(temperate(false, 3, 3), 3));
    }

    @Test
    void layersTheSeasonAddsInASnowyBiomeAboveTheNaturalHeightAreTheSeasons() {
        // A snowy plains column in winter: the first layer is what vanilla would bring, the rest can melt again.
        assertEquals(new Outcome(3, 2), atOnce(cold(true, 1, true), 3));
        assertEquals(new Outcome(3, 2), atOnce(cold(true, 0, true), 3));
        assertEquals(new Outcome(2, 0), SnowRules.snow(cold(true, 0, true), 2, true, true, 2), "a raised game rule");
    }

    @Test
    void theSeasonsSnowMeltsWhenItIsWarm() {
        assertEquals(new Outcome(0, 0), atOnce(temperate(false, 2, 2), 2));
    }

    @Test
    void onlyTheSeasonsLayersMeltOffNativeOrPlayerSnow() {
        // Two seasonal layers on a player's single layer: the player's stays.
        SnowSpot onPlayerSnow = new SnowSpot(Climate.TEMPERATE, false, false, 3, 2, true, true, true);
        assertEquals(new Outcome(1, 0), atOnce(onPlayerSnow, 0));
        // Unrecorded snow in a TEMPERATE biome is a player's or altitude snow: it never melts.
        assertEquals(new Outcome(1, 0), atOnce(temperate(false, 1, 0), 0));
    }

    @Test
    void altitudeSnowStays() {
        // Windswept hills high up: the real climate snows there, so the snow is native and never removed.
        SnowSpot altitude = new SnowSpot(Climate.TEMPERATE, false, true, 1, 0, false, true, true);
        assertEquals(1, atOnce(altitude, 0).layers());
        // Snow the season adds there is native too, so nothing is recorded.
        SnowSpot altitudeInWinter = new SnowSpot(Climate.TEMPERATE, true, true, 0, 0, false, true, true);
        assertEquals(new Outcome(1, 0), atOnce(altitudeInWinter, 1));
    }

    @Test
    void nativeColdSnowMeltsInSummerOnlyAsThinSnowOnNaturalGround() {
        assertEquals(0, atOnce(cold(false, 1, true), 0).layers());
        // Built-on, deeper than natural, or while it's still cold: it stays.
        assertEquals(1, atOnce(cold(false, 1, false), 0).layers());
        assertEquals(3, atOnce(cold(false, 3, true), 0).layers());
        assertEquals(1, atOnce(cold(true, 1, true), 0).layers());
        // With the setting off, native snow never melts.
        assertEquals(1, SnowRules.snow(cold(false, 1, true), 0, true, false, NATURAL_HEIGHT).layers());
        // A higher snow height game rule makes deeper native snow natural.
        assertEquals(0, SnowRules.snow(cold(false, 3, true), 0, true, true, 3).layers());
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
        assertEquals(new Outcome(0, 0), atOnce(noSupport, 2));
    }

    @Test
    void nativeColdSnowReturnsAsNativeSnow() {
        // Fall in a snowy biome: the snow comes back, and since the real climate snows there it isn't recorded.
        assertEquals(new Outcome(1, 0), atOnce(cold(true, 0, true), 1));
    }

    @Test
    void iceComesWithTheCoverAndOnlyGoesInAThaw() {
        assertTrue(SnowRules.freezes(true, true));
        assertFalse(SnowRules.freezes(true, false), "the ice cover hasn't reached the column yet");
        assertFalse(SnowRules.freezes(false, true), "too warm here");
        // The season's ice in the cold stays, whatever the cover says; in a thaw it goes once the cover falls past.
        assertFalse(SnowRules.iceMelts(true, true, true, false, true));
        assertTrue(SnowRules.iceMelts(true, true, true, false, false));
        assertFalse(SnowRules.iceMelts(true, true, true, true, false));
        // Too warm, or a biome without seasonal ice (an ocean an older version froze): it goes.
        assertTrue(SnowRules.iceMelts(true, true, false, true, true));
        assertTrue(SnowRules.iceMelts(true, false, true, true, true));
        // Native ice never melts.
        assertFalse(SnowRules.iceMelts(false, true, false, false, false));
    }
}
