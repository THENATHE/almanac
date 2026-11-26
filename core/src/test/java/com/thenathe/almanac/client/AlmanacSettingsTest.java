package com.thenathe.almanac.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thenathe.almanac.client.settings.PageRow;
import com.thenathe.almanac.client.settings.Setting;
import com.thenathe.almanac.client.settings.SettingsPage;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.config.ServerSetting;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

/**
 * The settings screen has a row for every setting there is, on the server's pages for the server's settings and on
 * this computer's page for its own, each with a name and a description, and no row for anything that isn't a setting.
 */
class AlmanacSettingsTest {
    private static List<SettingsPage> pages(AlmanacSettings.ServerSource source) {
        return AlmanacSettings.pages(source, false, new ServerConfig().validated(), new ClientConfig());
    }

    private static Set<String> keys(List<SettingsPage> pages) {
        Set<String> keys = new LinkedHashSet<>();
        for (SettingsPage page : pages) {
            for (PageRow row : page.rows()) {
                if (row instanceof Setting setting) {
                    keys.add(setting.key());
                }
            }
        }
        return keys;
    }

    /** {@code daysPerSeason} to {@code days_per_season}; {@code coldShare.mid_fall} to {@code share.cold.mid_fall}. */
    private static String rowKey(String settingName) {
        int dot = settingName.indexOf('.');
        if (dot > 0) {
            String climate = settingName.substring(0, dot).replace("Share", "");
            return "share." + climate + settingName.substring(dot);
        }
        return settingName.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT);
    }

    @Test
    void everyServerSettingHasARowOnTheServersPagesAndNothingElseDoes() {
        List<SettingsPage> all = pages(AlmanacSettings.ServerSource.CONNECTED_EDITABLE);
        Set<String> onServerPages = keys(all.subList(0, 4));
        Set<String> settings = new LinkedHashSet<>();
        for (ServerSetting setting : ServerSetting.all()) {
            settings.add(rowKey(setting.name()));
        }
        assertEquals(settings, onServerPages);
        assertEquals(Set.of("colour_blending", "redraw_sections_per_tick"), keys(all.subList(4, all.size())));
    }

    @Test
    void everyRowAndPageHasItsWords() throws Exception {
        JsonObject lang;
        try (var reader = new InputStreamReader(AlmanacSettingsTest.class.getResourceAsStream(
                "/assets/almanac/lang/en_us.json"), StandardCharsets.UTF_8)) {
            lang = JsonParser.parseReader(reader).getAsJsonObject();
        }
        List<String> missing = new ArrayList<>();
        for (AlmanacSettings.ServerSource source : AlmanacSettings.ServerSource.values()) {
            for (SettingsPage page : pages(source)) {
                List<Object> texts = new ArrayList<>();
                texts.add(page.name());
                for (PageRow row : page.rows()) {
                    switch (row) {
                        case Setting setting -> {
                            texts.add(setting.label());
                            texts.add(setting.description());
                        }
                        case PageRow.Caption caption -> texts.add(caption.text());
                        case PageRow.Note note -> texts.add(note.text());
                        default -> {
                        }
                    }
                }
                for (Object text : texts) {
                    String key = ((TranslatableContents) ((net.minecraft.network.chat.Component) text).getContents()).getKey();
                    if (!lang.has(key)) {
                        missing.add(key);
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "missing words: " + missing);
    }

    @Test
    void aPlayerWhoMayNotChangeTheServersSettingsSeesThemLocked() {
        List<SettingsPage> readOnly = pages(AlmanacSettings.ServerSource.CONNECTED_READ_ONLY);
        for (SettingsPage page : readOnly.subList(0, 4)) {
            assertTrue(page.isReadOnly());
        }
        for (SettingsPage page : readOnly.subList(4, readOnly.size())) {
            assertTrue(!page.isReadOnly(), "this computer's own settings stay open to everyone");
        }
        for (SettingsPage page : pages(AlmanacSettings.ServerSource.CONNECTED_EDITABLE)) {
            assertTrue(!page.isReadOnly());
        }
    }
}
