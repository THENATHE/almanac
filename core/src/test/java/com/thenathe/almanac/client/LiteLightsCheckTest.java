package com.thenathe.almanac.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import org.junit.jupiter.api.Test;

/** Which LiteLights shares far terrain and the season's climate with this Almanac. */
class LiteLightsCheckTest {
    private static boolean tooOld(String version, LiteLightsCheck.Shared feature) throws VersionParsingException {
        return LiteLightsCheck.tooOld(Version.parse(version), feature);
    }

    @Test
    void liteLightsBefore107IsTooOldForFarTerrain() throws VersionParsingException {
        assertTrue(tooOld("1.0.6+26.3", LiteLightsCheck.Shared.FAR_TERRAIN));
        assertTrue(tooOld("1.0.1+26.3", LiteLightsCheck.Shared.FAR_TERRAIN));
        assertTrue(tooOld("0.9.10+26.3", LiteLightsCheck.Shared.FAR_TERRAIN));
        assertTrue(tooOld("1.0.7-beta.1+26.3", LiteLightsCheck.Shared.FAR_TERRAIN), "a pre-release of 1.0.7 comes before it");
    }

    @Test
    void liteLights107AndLaterShareFarTerrain() throws VersionParsingException {
        assertFalse(tooOld("1.0.7+26.3", LiteLightsCheck.Shared.FAR_TERRAIN), "the part after + doesn't count");
        assertFalse(tooOld("1.0.7", LiteLightsCheck.Shared.FAR_TERRAIN));
        assertFalse(tooOld("1.0.10+26.3", LiteLightsCheck.Shared.FAR_TERRAIN));
        assertFalse(tooOld("1.1.0+26.4", LiteLightsCheck.Shared.FAR_TERRAIN));
    }

    @Test
    void liteLights107And108StillAskForSeasonalCopies() throws VersionParsingException {
        assertTrue(tooOld("1.0.8+26.3", LiteLightsCheck.Shared.SEASONAL_CLIMATE), "1.0.9 is the first to read the climate");
        assertTrue(tooOld("1.0.7+26.3", LiteLightsCheck.Shared.SEASONAL_CLIMATE));
        assertTrue(tooOld("1.0.6+26.3", LiteLightsCheck.Shared.SEASONAL_CLIMATE));
        assertFalse(tooOld("1.0.9+26.3", LiteLightsCheck.Shared.SEASONAL_CLIMATE));
        assertFalse(tooOld("1.1.0+26.3", LiteLightsCheck.Shared.SEASONAL_CLIMATE));
    }
}
