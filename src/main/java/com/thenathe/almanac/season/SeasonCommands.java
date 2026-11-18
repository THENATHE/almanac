package com.thenathe.almanac.season;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.climatemap.ServerClimateMap;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.config.ServerSetting;
import com.thenathe.almanac.config.SettingsFile;
import com.thenathe.almanac.network.ServerNetworking;
import com.thenathe.almanac.snow.SnowState;
import com.thenathe.almanac.snow.SnowUpdates;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

/**
 * {@code /season} for everyone; {@code /season set|pause|resume|length}, {@code /almanac config} (and
 * {@code /almanac config reload}) and {@code /almanac snow} for operators
 * (level 2) and the console. Replies have English fallbacks, so players without Almanac read them too.
 */
public final class SeasonCommands {
    private SeasonCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("season")
                .executes(SeasonCommands::show)
                .then(Commands.literal("set").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("sub_season", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(SubSeason.values()).map(SubSeason::id), builder))
                                .executes(SeasonCommands::set)))
                .then(Commands.literal("pause").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> setPaused(context, true)))
                .then(Commands.literal("resume").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(context -> setPaused(context, false)))
                .then(Commands.literal("length").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.argument("days_per_season", IntegerArgumentType.integer(
                                        ServerConfig.MIN_DAYS_PER_SEASON, ServerConfig.MAX_DAYS_PER_SEASON))
                                .executes(SeasonCommands::setLength))));

        dispatcher.register(Commands.literal("almanac")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("snow").executes(SeasonCommands::showSnow))
                .then(Commands.literal("config")
                        .executes(SeasonCommands::listSettings)
                        .then(Commands.literal("reload").executes(SeasonCommands::reloadSettings))
                        .then(Commands.argument("setting", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        ServerSetting.all().stream().map(ServerSetting::name), builder))
                                .executes(SeasonCommands::showSetting)
                                .then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(SeasonCommands::changeSetting)))));
    }

    private static int show(CommandContext<CommandSourceStack> context) {
        ServerSeasons seasons = ServerSeasons.get();
        if (seasons == null) {
            context.getSource().sendFailure(Component.literal("The season isn't running yet."));
            return 0;
        }
        SubSeason subSeason = seasons.subSeason();
        int left = seasons.daysLeft();
        Component message = Component.translatableWithFallback("almanac.command.season",
                "It is %s, with %s left.", SubSeasonNames.name(subSeason),
                left == 1 ? Component.translatableWithFallback("almanac.command.one_day", "1 day")
                        : Component.translatableWithFallback("almanac.command.days", "%s days", left));
        if (ServerConfig.get().paused) {
            message = Component.empty().append(message).append(" ")
                    .append(Component.translatableWithFallback("almanac.command.paused", "The seasons are paused."));
        }
        Component reply = message;
        context.getSource().sendSuccess(() -> reply, false);
        return subSeason.ordinal() + 1;
    }

    private static int set(CommandContext<CommandSourceStack> context) {
        String id = StringArgumentType.getString(context, "sub_season");
        Optional<SubSeason> subSeason = SubSeason.byId(id);
        ServerSeasons seasons = ServerSeasons.get();
        if (subSeason.isEmpty() || seasons == null) {
            context.getSource().sendFailure(Component.literal("Unknown sub-season: " + id));
            return 0;
        }
        seasons.setSubSeason(subSeason.get());
        context.getSource().sendSuccess(() -> Component.translatableWithFallback("almanac.command.set",
                "It is now %s.", SubSeasonNames.name(subSeason.get())), true);
        return 1;
    }

    private static int setPaused(CommandContext<CommandSourceStack> context, boolean paused) {
        ServerConfig updated = ServerConfig.get().copy();
        updated.paused = paused;
        ServerNetworking.applySettings(context.getSource().getServer(), updated);
        context.getSource().sendSuccess(() -> paused
                ? Component.translatableWithFallback("almanac.command.pause", "The seasons are paused.")
                : Component.translatableWithFallback("almanac.command.resume", "The seasons are moving again."), true);
        return 1;
    }

    private static int setLength(CommandContext<CommandSourceStack> context) {
        int days = IntegerArgumentType.getInteger(context, "days_per_season");
        ServerConfig updated = ServerConfig.get().copy();
        updated.daysPerSeason = days;
        ServerNetworking.applySettings(context.getSource().getServer(), updated);
        context.getSource().sendSuccess(() -> Component.translatableWithFallback("almanac.command.length",
                "Each season now lasts %s days.", days), true);
        return days;
    }

    private static int showSnow(CommandContext<CommandSourceStack> context) {
        ServerSeasons seasons = ServerSeasons.get();
        SnowUpdates updates = SnowUpdates.get();
        if (seasons == null || updates == null) {
            context.getSource().sendFailure(Component.literal("The season isn't running yet."));
            return 0;
        }
        SnowState state = seasons.snowState();
        StringBuilder text = new StringBuilder("Snow depth in layers (snow height game rule ")
                .append(state.snowHeight()).append("):");
        for (Climate climate : Climate.values()) {
            text.append(String.format(Locale.ROOT, "\n  %s %.2f, %d on the ground where it's cold enough",
                    climate.id(), seasons.snowDepth(climate), state.layers(climate)));
        }
        text.append("\nNative snow melts in summer: ").append(state.nativeSnowMelts());
        text.append("\n").append(updates.describeQueues());
        ServerClimateMap climateMap = ServerClimateMap.get();
        if (climateMap != null) {
            climateMap.describe().forEach(line -> text.append("\n").append(line));
        }
        context.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
        return state.layers(Climate.TEMPERATE);
    }

    private static int listSettings(CommandContext<CommandSourceStack> context) {
        ServerConfig config = ServerConfig.get();
        StringBuilder text = new StringBuilder("Almanac server settings:");
        for (ServerSetting setting : ServerSetting.all()) {
            text.append("\n  ").append(setting.name()).append(" = ").append(setting.get(config));
        }
        context.getSource().sendSuccess(() -> Component.literal(text.toString()), false);
        return ServerSetting.all().size();
    }

    /** Reads the settings file again, for hand edits made while the server runs. */
    private static int reloadSettings(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerConfig before = ServerConfig.get();
        String file = ServerConfig.file().getFileName().toString();
        switch (ServerConfig.readFile()) {
            case SettingsFile.Reading.Read<ServerConfig> read -> {
                ServerNetworking.applySettings(source.getServer(), read.settings());
                List<String> afterRestart = ServerSetting.all().stream()
                        .filter(setting -> setting.needsRestart() && !setting.get(before).equals(setting.get(ServerConfig.get())))
                        .map(ServerSetting::name)
                        .toList();
                source.sendSuccess(() -> Component.literal("Read " + file + " again."
                        + (afterRestart.isEmpty() ? "" : " " + String.join(", ", afterRestart) + " apply after a restart.")), true);
                return 1;
            }
            case SettingsFile.Reading.Missing<ServerConfig> missing -> {
                ServerConfig.save();
                source.sendSuccess(() -> Component.literal("There was no " + file + "; wrote the current settings to it."), true);
                return 1;
            }
            case SettingsFile.Reading.Unreadable<ServerConfig> unreadable -> {
                ServerConfig.save();
                String where = unreadable.setAside().map(path -> "moved it to " + path.getFileName() + ", ")
                        .orElse("couldn't move it aside, ");
                source.sendFailure(Component.literal("Couldn't read " + file + "; " + where
                        + "and kept the current settings. The log has the reason."));
                return 0;
            }
        }
    }

    private static int showSetting(CommandContext<CommandSourceStack> context) {
        Optional<ServerSetting> setting = setting(context);
        if (setting.isEmpty()) {
            return 0;
        }
        String value = setting.get().get(ServerConfig.get());
        context.getSource().sendSuccess(() -> Component.literal(setting.get().name() + " = " + value
                + " (" + setting.get().range() + (setting.get().needsRestart() ? "; applies after a restart" : "") + ")"), false);
        return 1;
    }

    private static int changeSetting(CommandContext<CommandSourceStack> context) {
        Optional<ServerSetting> setting = setting(context);
        if (setting.isEmpty()) {
            return 0;
        }
        ServerConfig updated = ServerConfig.get().copy();
        try {
            setting.get().set(updated, StringArgumentType.getString(context, "value"));
        } catch (IllegalArgumentException invalid) {
            context.getSource().sendFailure(Component.literal(setting.get().name() + ": " + invalid.getMessage()
                    + " (" + setting.get().range() + ")"));
            return 0;
        }
        updated.validated();
        ServerNetworking.applySettings(context.getSource().getServer(), updated);
        String value = setting.get().get(ServerConfig.get());
        context.getSource().sendSuccess(() -> Component.literal(setting.get().name() + " is now " + value
                + (setting.get().needsRestart() ? " (applies after a restart)" : "")), true);
        return 1;
    }

    private static Optional<ServerSetting> setting(CommandContext<CommandSourceStack> context) {
        String name = StringArgumentType.getString(context, "setting");
        Optional<ServerSetting> setting = ServerSetting.byName(name);
        if (setting.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Unknown setting: " + name + ". /almanac config lists them."));
        }
        return setting;
    }
}
