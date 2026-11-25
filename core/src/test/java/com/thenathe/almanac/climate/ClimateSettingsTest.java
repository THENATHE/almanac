package com.thenathe.almanac.climate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.internal.SeasonTick;
import org.junit.jupiter.api.Test;

/** The climate settings' fingerprint, which every chunk's snow state saves. */
class ClimateSettingsTest {
    @Test
    void theFingerprintFollowsEachClimateSetting() {
        ClimateSettings defaults = ClimateSettings.of(new ServerConfig());
        assertEquals(new ClimateSettings(0.0F, 0.5F, 48), defaults);
        assertEquals(ClimateSettings.DEFAULTS, defaults);
        int fingerprint = defaults.fingerprint();
        assertNotEquals(fingerprint, new ClimateSettings(-0.05F, 0.5F, 48).fingerprint());
        assertNotEquals(fingerprint, new ClimateSettings(0.0F, 0.55F, 48).fingerprint());
        assertNotEquals(fingerprint, new ClimateSettings(0.0F, 0.5F, 49).fingerprint());
        // Saved in every chunk record: if this number changes, every chunk in every world is brought up to date again.
        // It is the number 0.1.3 to 0.1.7 saved for the seasonal copies' settings, so their chunks stay up to date.
        assertEquals(-1_593_805_681, fingerprint);
        // The snow module reads it as a constant, since it can't see the settings.
        assertEquals(SeasonTick.DEFAULT_CLIMATE_SETTINGS, fingerprint);
    }
}
