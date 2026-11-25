package com.thenathe.almanac.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.client.settings.Accent;
import com.thenathe.almanac.client.settings.SettingsStyle;
import org.junit.jupiter.api.Test;

class ClientConfigTest {
    @Test
    void defaultsBlendColoursAndUseTheHouseLook() {
        ClientConfig defaults = ClientConfig.fromJson("{}");
        assertTrue(defaults.colourBlending);
        assertEquals(24, defaults.redrawSectionsPerTick);
        assertEquals(SettingsStyle.HOUSE, defaults.style);
        assertTrue(defaults._credits.contains("Idea by Fabric Seasons"));
    }

    @Test
    void valuesAreKeptInRange() {
        assertEquals(ClientConfig.MIN_REDRAW_SECTIONS, ClientConfig.fromJson("{\"redrawSectionsPerTick\": 0}").redrawSectionsPerTick);
        assertEquals(ClientConfig.MAX_REDRAW_SECTIONS, ClientConfig.fromJson("{\"redrawSectionsPerTick\": 99999}").redrawSectionsPerTick);
    }

    @Test
    void aFileFromAnotherVersionKeepsWhatItCan() {
        ClientConfig read = ClientConfig.fromJson("""
                {"colourBlending": false, "style": "SPARKLY", "accent": "ORANGE", "somethingNew": 3}""");
        assertFalse(read.colourBlending, "known settings survive");
        assertEquals(Accent.ORANGE, read.accent);
        assertEquals(SettingsStyle.HOUSE, read.style, "an unknown look falls back to the default");
    }

    @Test
    void savedTextReadsBackTheSame() {
        ClientConfig config = ClientConfig.fromJson("{}");
        config.colourBlending = false;
        config.redrawSectionsPerTick = 64;
        config.lightMode = true;
        assertEquals(config.toJson(), ClientConfig.fromJson(config.toJson()).toJson());
        assertNull(ClientConfig.fromJson("not json {"));
    }
}
