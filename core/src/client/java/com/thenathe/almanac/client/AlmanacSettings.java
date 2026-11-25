package com.thenathe.almanac.client;

import com.google.gson.JsonObject;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.client.settings.Accent;
import com.thenathe.almanac.client.settings.KitSettingsScreen;
import com.thenathe.almanac.client.settings.LookPreferences;
import com.thenathe.almanac.client.settings.Setting;
import com.thenathe.almanac.client.settings.SettingsPage;
import com.thenathe.almanac.client.settings.SettingsStyle;
import com.thenathe.almanac.climate.RainShares;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.network.SettingsPayloads;
import com.thenathe.almanac.season.SubSeasonNames;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The one settings screen: the server's settings, which operators and the singleplayer host can change, and this
 * client's own choices. It opens from Mod Menu or a key binding.
 */
public final class AlmanacSettings {
    private AlmanacSettings() {
    }

    /** Where the server settings shown on the screen come from, and where they go when it closes. */
    private enum ServerSource {
        /** Connected to a server with Almanac (singleplayer included) and allowed to change its settings. */
        CONNECTED_EDITABLE,
        /** Connected, but only operators may change the settings. */
        CONNECTED_READ_ONLY,
        /** Connected to a server without Almanac. */
        CONNECTED_WITHOUT_MOD,
        /** Not in a world: the file the next singleplayer world will use. */
        LOCAL_FILE
    }

    public static Screen create(Screen parent) {
        ClientConfig client = ClientConfig.get();
        ServerSource source = serverSource();
        if (source == ServerSource.LOCAL_FILE) {
            // Show the file as it is now, hand edits included.
            ServerConfig.reload();
        }
        ServerConfig original = switch (source) {
            case CONNECTED_EDITABLE, CONNECTED_READ_ONLY -> ClientState.serverSettings();
            case LOCAL_FILE -> ServerConfig.get();
            case CONNECTED_WITHOUT_MOD -> null;
        };
        ServerConfig shown = original == null ? null : original.copy();
        boolean editable = source == ServerSource.CONNECTED_EDITABLE || source == ServerSource.LOCAL_FILE;

        Runnable save = () -> {
            ClientConfig.save();
            if (editable && shown != null) {
                saveServerSettings(source, ServerConfig.changes(original, shown));
            }
        };
        return new KitSettingsScreen(Component.translatable("almanac.title"), parent,
                pages(source, shown, client), new ConfigLook(client), save);
    }

    /**
     * Sends only what the player changed, so a save never undoes what someone else changed while the screen was
     * open. Servers with Almanac 0.1.3 or later apply the changes on top of their own latest settings; older ones
     * take a whole set, so they get the server's latest as this client last heard it, with the changes on top.
     */
    private static void saveServerSettings(ServerSource source, JsonObject changes) {
        if (changes.isEmpty()) {
            return;
        }
        if (source == ServerSource.LOCAL_FILE) {
            ServerConfig.reload();
            ServerConfig updated = ServerConfig.withChanges(ServerConfig.get(), changes.toString());
            if (updated != null) {
                ServerConfig.replace(updated);
            }
        } else if (ClientPlayNetworking.canSend(SettingsPayloads.Change.TYPE)) {
            ClientPlayNetworking.send(new SettingsPayloads.Change(changes.toString()));
        }
    }

    private static ServerSource serverSource() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            return ServerSource.LOCAL_FILE;
        }
        if (ClientState.serverSettings() == null) {
            return ServerSource.CONNECTED_WITHOUT_MOD;
        }
        return ClientState.mayEditServerSettings() ? ServerSource.CONNECTED_EDITABLE : ServerSource.CONNECTED_READ_ONLY;
    }

    private static List<SettingsPage> pages(ServerSource source, ServerConfig server, ClientConfig client) {
        ServerConfig defaults = new ServerConfig();
        SettingsPage seasons = new SettingsPage(Component.translatable("almanac.page.seasons"));
        SettingsPage weather = new SettingsPage(Component.translatable("almanac.page.weather"));
        SettingsPage snow = new SettingsPage(Component.translatable("almanac.page.snow"));
        SettingsPage colours = new SettingsPage(Component.translatable("almanac.page.colours"));

        List<SettingsPage> serverPages = List.of(seasons, weather, snow, colours);
        if (server == null) {
            for (SettingsPage page : serverPages) {
                page.caption("server").note("server_without_mod");
            }
        } else {
            if (source == ServerSource.CONNECTED_READ_ONLY) {
                for (SettingsPage page : serverPages) {
                    page.readOnly().caption("server").note("server_read_only");
                }
            }
            seasonRows(seasons, server, defaults);
            weatherRows(weather, server, defaults);
            snowRows(snow, server, defaults);
            colourRows(colours, server, defaults);
        }

        ClientConfig clientDefaults = new ClientConfig();
        SettingsPage thisClient = new SettingsPage(Component.translatable("almanac.page.client"))
                .caption("colours_here")
                .add(toggle("colour_blending", clientDefaults.colourBlending, () -> client.colourBlending,
                        value -> client.colourBlending = value))
                .add(Setting.slider("redraw_sections_per_tick", clientDefaults.redrawSectionsPerTick,
                        ClientConfig.MIN_REDRAW_SECTIONS, ClientConfig.MAX_REDRAW_SECTIONS, 1,
                        value -> Component.translatable("almanac.sections", value),
                        () -> client.redrawSectionsPerTick, value -> client.redrawSectionsPerTick = value));

        SettingsPage help = new SettingsPage(Component.translatable("almanac.page.help"))
                .caption("who_can_change")
                .note("permissions")
                .caption("when_changes_apply")
                .note("live_changes")
                .caption("keys")
                .note("keys")
                .caption("players_need_almanac")
                .note("players_need_almanac")
                .caption("credits")
                .note("credits");

        return List.of(seasons, weather, snow, colours, thisClient, help);
    }

    private static void seasonRows(SettingsPage page, ServerConfig server, ServerConfig defaults) {
        List<Component> subSeasonNames = Arrays.stream(SubSeason.values()).map(SubSeasonNames::name).toList();
        page.caption("calendar")
                .add(Setting.number("days_per_season", defaults.daysPerSeason, ServerConfig.MIN_DAYS_PER_SEASON,
                        ServerConfig.MAX_DAYS_PER_SEASON, value -> Component.translatable("almanac.days", value),
                        () -> server.daysPerSeason, value -> server.daysPerSeason = value))
                .add(Setting.choice("start_sub_season", defaults.startSubSeason().ordinal(), subSeasonNames,
                        () -> server.startSubSeason().ordinal(),
                        value -> server.startSubSeason = SubSeason.byIndex(value).id()))
                .add(toggle("paused", defaults.paused, () -> server.paused, value -> server.paused = value))
                .caption("messages")
                .add(toggle("announce_sub_seasons", defaults.announceSubSeasons, () -> server.announceSubSeasons,
                        value -> server.announceSubSeasons = value));
    }

    private static void weatherRows(SettingsPage page, ServerConfig server, ServerConfig defaults) {
        page.caption("regional_weather")
                .add(toggle("regional_weather", defaults.regionalWeather, () -> server.regionalWeather,
                        value -> server.regionalWeather = value))
                .add(toggle("weather_command_everywhere", defaults.weatherCommandEverywhere,
                        () -> server.weatherCommandEverywhere, value -> server.weatherCommandEverywhere = value));
        for (Climate climate : List.of(Climate.TEMPERATE, Climate.COLD, Climate.HOT)) {
            page.caption("share." + climate.id());
            for (SubSeason subSeason : SubSeason.values()) {
                if (RainShares.hasShare(climate, subSeason)) {
                    page.add(Setting.slider("share." + climate.id() + "." + subSeason.id(),
                            RainShares.defaultShare(climate, subSeason), 0, 100, 5,
                            value -> Component.translatable("almanac.percent", value),
                            () -> server.share(climate, subSeason),
                            value -> server.shares(climate).put(subSeason.id(), value)));
                }
            }
        }
        page.foldedCaption("temperatures")
                .add(Setting.slider("snow_line_above_sea", defaults.snowLineAboveSea, ServerConfig.MIN_SNOW_LINE,
                        ServerConfig.MAX_SNOW_LINE, 1, value -> Component.translatable("almanac.blocks", value),
                        () -> server.snowLineAboveSea, value -> server.snowLineAboveSea = value))
                .add(decimal("winter_temperature", defaults.winterTemperature, ServerConfig.MIN_WINTER_TEMPERATURE,
                        ServerConfig.MAX_WINTER_TEMPERATURE, 0.05, () -> server.winterTemperature,
                        value -> server.winterTemperature = value))
                .add(decimal("summer_temperature", defaults.summerTemperature, ServerConfig.MIN_SUMMER_TEMPERATURE,
                        ServerConfig.MAX_SUMMER_TEMPERATURE, 0.05, () -> server.summerTemperature,
                        value -> server.summerTemperature = value));
    }

    private static void snowRows(SettingsPage page, ServerConfig server, ServerConfig defaults) {
        page.caption("snow_clocks")
                .note("snow_clocks")
                .add(Setting.number("snowfall_ticks", defaults.snowfallTicks, ServerConfig.MIN_CLOCK_TICKS,
                        ServerConfig.MAX_CLOCK_TICKS, AlmanacSettings::clock, () -> server.snowfallTicks,
                        value -> server.snowfallTicks = value))
                .add(Setting.number("thaw_ticks", defaults.thawTicks, ServerConfig.MIN_CLOCK_TICKS,
                        ServerConfig.MAX_CLOCK_TICKS, AlmanacSettings::clock, () -> server.thawTicks,
                        value -> server.thawTicks = value))
                .add(Setting.number("ice_ticks", defaults.iceTicks, 0, ServerConfig.MAX_CLOCK_TICKS,
                        value -> value == 0 ? Component.translatable("almanac.same_as_snowfall") : clock(value),
                        () -> server.iceTicks, value -> server.iceTicks = value))
                .add(count("max_snow_layers", defaults.maxSnowLayers, ServerConfig.MIN_SNOW_LAYERS,
                        ServerConfig.MAX_SNOW_LAYERS, "almanac.layers", () -> server.maxSnowLayers,
                        value -> server.maxSnowLayers = value))
                .caption("melting")
                .add(toggle("native_snow_melts_in_summer", defaults.nativeSnowMeltsInSummer,
                        () -> server.nativeSnowMeltsInSummer, value -> server.nativeSnowMeltsInSummer = value))
                .caption("catching_up")
                .add(toggle("snow_catch_up", defaults.snowCatchUp, () -> server.snowCatchUp, value -> server.snowCatchUp = value))
                .add(count("catch_up_chunks_per_tick", defaults.catchUpChunksPerTick, ServerConfig.MIN_CATCH_UP_CHUNKS,
                        ServerConfig.MAX_CATCH_UP_CHUNKS, "almanac.chunks", () -> server.catchUpChunksPerTick,
                        value -> server.catchUpChunksPerTick = value))
                .add(count("snow_columns_per_tick", defaults.snowColumnsPerTick, ServerConfig.MIN_SNOW_COLUMNS,
                        ServerConfig.MAX_SNOW_COLUMNS, "almanac.columns", () -> server.snowColumnsPerTick,
                        value -> server.snowColumnsPerTick = value));
        List<Integer> cellSizes = ServerConfig.FAR_CELL_SIZES;
        page.caption("far_terrain")
                .note("far_terrain")
                .add(toggle("far_climate_map", defaults.farClimateMap, () -> server.farClimateMap,
                        value -> server.farClimateMap = value))
                .add(Setting.slider("far_climate_radius", defaults.farClimateRadius, ServerConfig.MIN_FAR_RADIUS,
                        ServerConfig.MAX_FAR_RADIUS, 256, value -> Component.translatable("almanac.blocks", value),
                        () -> server.farClimateRadius, value -> server.farClimateRadius = value))
                .add(Setting.choice("far_climate_cell_size", cellSizes.indexOf(defaults.farClimateCellSize),
                        cellSizes.stream().map(size -> (Component) Component.translatable("almanac.blocks", size)).toList(),
                        () -> cellSizes.indexOf(server.farClimateCellSize),
                        value -> server.farClimateCellSize = cellSizes.get(value)))
                .add(Setting.slider("far_climate_bytes_per_tick", defaults.farClimateBytesPerTick,
                        ServerConfig.MIN_FAR_BYTES, ServerConfig.MAX_FAR_BYTES, 512,
                        value -> Component.translatable("almanac.bytes", value),
                        () -> server.farClimateBytesPerTick, value -> server.farClimateBytesPerTick = value));
    }

    private static void colourRows(SettingsPage page, ServerConfig server, ServerConfig defaults) {
        page.note("palette");
    }

    private interface BooleanGetter {
        boolean get();
    }

    private static Setting toggle(String key, boolean defaultValue, BooleanGetter getter, Consumer<Boolean> setter) {
        return Setting.toggle(key, defaultValue, () -> getter.get() ? 1 : 0, value -> setter.accept(value != 0));
    }

    /** A clock in ticks, with how long that is in minutes of play. */
    private static Component clock(int ticks) {
        return Component.translatable("almanac.ticks", ticks, formatDecimal(Math.round(ticks / 120.0) / 10.0));
    }

    private static Setting count(String key, int defaultValue, int min, int max, String unitKey,
                                 IntSupplier getter, IntConsumer setter) {
        return Setting.slider(key, defaultValue, min, max, 1, value -> Component.translatable(unitKey, value), getter, setter);
    }

    /** A decimal setting on a slider, stored as a whole number of {@code step}s. */
    private static Setting decimal(String key, double defaultValue, double min, double max, double step,
                                   DoubleSupplier getter, DoubleConsumer setter) {
        return decimal(key, defaultValue, min, max, step, Component::literal, getter, setter);
    }

    /** The same, with the formatted value shown through {@code label}, for example with its unit. */
    private static Setting decimal(String key, double defaultValue, double min, double max, double step,
                                   Function<String, Component> label, DoubleSupplier getter, DoubleConsumer setter) {
        int scale = (int) Math.round(1 / step);
        return Setting.slider(key, (int) Math.round(defaultValue * scale), (int) Math.round(min * scale),
                (int) Math.round(max * scale), 1, value -> label.apply(formatDecimal(value / (double) scale)),
                () -> (int) Math.round(getter.getAsDouble() * scale), value -> setter.accept(value / (double) scale));
    }

    private static String formatDecimal(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(Math.round(value * 100) / 100.0);
    }

    /** Keeps the look choices in Almanac's own client config file. */
    private record ConfigLook(ClientConfig config) implements LookPreferences {
        @Override
        public SettingsStyle style() {
            return config.style;
        }

        @Override
        public void setStyle(SettingsStyle style) {
            config.style = style;
            ClientConfig.save();
        }

        @Override
        public boolean light() {
            return config.lightMode;
        }

        @Override
        public void setLight(boolean light) {
            config.lightMode = light;
            ClientConfig.save();
        }

        @Override
        public Accent accent() {
            return config.accent;
        }

        @Override
        public void setAccent(Accent accent) {
            config.accent = accent;
            ClientConfig.save();
        }
    }
}
