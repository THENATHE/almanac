package com.thenathe.almanac.config;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.climate.RainShares;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * One server setting by name, for {@code /almanac config}. Every field of {@link ServerConfig} has one; the rain
 * shares are named {@code <climate>Share.<sub-season>}, for example {@code temperateShare.mid_winter}.
 */
public record ServerSetting(String name, String range, boolean needsRestart,
                            Function<ServerConfig, String> getter, BiConsumer<ServerConfig, String> setter) {
    private static final List<ServerSetting> ALL = build();

    public static List<ServerSetting> all() {
        return ALL;
    }

    public static Optional<ServerSetting> byName(String name) {
        return ALL.stream().filter(setting -> setting.name().equals(name)).findFirst();
    }

    public String get(ServerConfig config) {
        return getter.apply(config);
    }

    /**
     * Sets the value on {@code config}, which the caller then validates.
     *
     * @throws IllegalArgumentException if the text isn't a value of this setting's kind
     */
    public void set(ServerConfig config, String value) {
        setter.accept(config, value.trim());
    }

    private static List<ServerSetting> build() {
        List<ServerSetting> settings = new ArrayList<>();
        settings.add(integer("daysPerSeason", ServerConfig.MIN_DAYS_PER_SEASON, ServerConfig.MAX_DAYS_PER_SEASON, false,
                config -> config.daysPerSeason, (config, value) -> config.daysPerSeason = value));
        settings.add(new ServerSetting("startSubSeason",
                String.join(", ", Arrays.stream(SubSeason.values()).map(SubSeason::id).toList()), false,
                config -> config.startSubSeason,
                (config, value) -> config.startSubSeason = SubSeason.byId(value.toLowerCase(Locale.ROOT))
                        .orElseThrow(() -> new IllegalArgumentException("not a sub-season: " + value)).id()));
        settings.add(bool("paused", config -> config.paused, (config, value) -> config.paused = value));
        settings.add(bool("announceSubSeasons", config -> config.announceSubSeasons, (config, value) -> config.announceSubSeasons = value));
        for (Climate climate : Climate.values()) {
            for (SubSeason subSeason : SubSeason.values()) {
                if (RainShares.hasShare(climate, subSeason)) {
                    settings.add(integer(climate.id() + "Share." + subSeason.id(), 0, 100, false,
                            config -> config.share(climate, subSeason),
                            (config, value) -> config.shares(climate).put(subSeason.id(), value)));
                }
            }
        }
        settings.add(integer("snowLineAboveSea", ServerConfig.MIN_SNOW_LINE, ServerConfig.MAX_SNOW_LINE, true,
                config -> config.snowLineAboveSea, (config, value) -> config.snowLineAboveSea = value));
        settings.add(decimal("winterTemperature", ServerConfig.MIN_WINTER_TEMPERATURE, ServerConfig.MAX_WINTER_TEMPERATURE, true,
                config -> config.winterTemperature, (config, value) -> config.winterTemperature = value));
        settings.add(decimal("summerTemperature", ServerConfig.MIN_SUMMER_TEMPERATURE, ServerConfig.MAX_SUMMER_TEMPERATURE, true,
                config -> config.summerTemperature, (config, value) -> config.summerTemperature = value));
        settings.add(bool("nativeSnowMeltsInSummer", config -> config.nativeSnowMeltsInSummer,
                (config, value) -> config.nativeSnowMeltsInSummer = value));
        settings.add(decimal("thawDays", ServerConfig.MIN_THAW_DAYS, ServerConfig.MAX_THAW_DAYS, false,
                config -> config.thawDays, (config, value) -> config.thawDays = value));
        settings.add(bool("snowCatchUp", config -> config.snowCatchUp, (config, value) -> config.snowCatchUp = value));
        settings.add(integer("catchUpChunksPerTick", ServerConfig.MIN_CATCH_UP_CHUNKS, ServerConfig.MAX_CATCH_UP_CHUNKS, false,
                config -> config.catchUpChunksPerTick, (config, value) -> config.catchUpChunksPerTick = value));
        settings.add(integer("snowColumnsPerTick", ServerConfig.MIN_SNOW_COLUMNS, ServerConfig.MAX_SNOW_COLUMNS, false,
                config -> config.snowColumnsPerTick, (config, value) -> config.snowColumnsPerTick = value));
        settings.add(bool("vanillaClientCopies", config -> config.vanillaClientCopies, (config, value) -> config.vanillaClientCopies = value));
        settings.add(integer("refreshChunksPerTick", ServerConfig.MIN_REFRESH_CHUNKS, ServerConfig.MAX_REFRESH_CHUNKS, false,
                config -> config.refreshChunksPerTick, (config, value) -> config.refreshChunksPerTick = value));
        settings.add(bool("farClimateMap", config -> config.farClimateMap, (config, value) -> config.farClimateMap = value));
        settings.add(integer("farClimateRadius", ServerConfig.MIN_FAR_RADIUS, ServerConfig.MAX_FAR_RADIUS, false,
                config -> config.farClimateRadius, (config, value) -> config.farClimateRadius = value));
        settings.add(new ServerSetting("farClimateCellSize",
                String.join(", ", ServerConfig.FAR_CELL_SIZES.stream().map(String::valueOf).toList()), false,
                config -> String.valueOf(config.farClimateCellSize), (config, text) -> {
                    int size = parseInteger(text);
                    if (!ServerConfig.FAR_CELL_SIZES.contains(size)) {
                        throw new IllegalArgumentException("not one of the cell sizes: " + text);
                    }
                    config.farClimateCellSize = size;
                }));
        settings.add(integer("farClimateBytesPerTick", ServerConfig.MIN_FAR_BYTES, ServerConfig.MAX_FAR_BYTES, false,
                config -> config.farClimateBytesPerTick, (config, value) -> config.farClimateBytesPerTick = value));
        return List.copyOf(settings);
    }

    private static ServerSetting bool(String name, Function<ServerConfig, Boolean> getter, BiConsumer<ServerConfig, Boolean> setter) {
        return new ServerSetting(name, "true or false", false, config -> String.valueOf(getter.apply(config)), (config, text) -> {
            switch (text.toLowerCase(Locale.ROOT)) {
                case "true", "on", "yes" -> setter.accept(config, true);
                case "false", "off", "no" -> setter.accept(config, false);
                default -> throw new IllegalArgumentException("not true or false: " + text);
            }
        });
    }

    private static ServerSetting integer(String name, int min, int max, boolean needsRestart,
                                         Function<ServerConfig, Integer> getter, BiConsumer<ServerConfig, Integer> setter) {
        return new ServerSetting(name, min + " to " + max, needsRestart, config -> String.valueOf(getter.apply(config)),
                (config, text) -> setter.accept(config, parseInteger(text)));
    }

    private static int parseInteger(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("not a whole number: " + text);
        }
    }

    private static ServerSetting decimal(String name, double min, double max, boolean needsRestart,
                                         Function<ServerConfig, Double> getter, BiConsumer<ServerConfig, Double> setter) {
        return new ServerSetting(name, min + " to " + max, needsRestart, config -> String.valueOf(getter.apply(config)),
                (config, text) -> {
                    try {
                        setter.accept(config, Double.parseDouble(text));
                    } catch (NumberFormatException invalid) {
                        throw new IllegalArgumentException("not a number: " + text);
                    }
                });
    }
}
