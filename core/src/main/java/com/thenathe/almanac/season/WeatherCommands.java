package com.thenathe.almanac.season;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.Storm;
import com.thenathe.almanac.climate.LocalWeather;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.internal.Services;
import com.thenathe.almanac.internal.WeatherHere;
import com.thenathe.almanac.internal.WeatherRecord;
import com.thenathe.almanac.internal.WeatherView;
import com.thenathe.almanac.internal.WetSpell;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.TimeArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /weather} under regional weather, and {@code /almanac weather} for operators (from 0.4.0).
 *
 * <p>{@code /weather} acts around whoever runs it, the way a player means it (the planner's choice from the weather
 * writeup, decision 8): {@code rain} and {@code thunder} place a storm over them for the time asked (vanilla's
 * default times when none is given), and {@code clear} keeps the sky clear for {@link #CLEAR_RADIUS} blocks around
 * them. With {@code weatherCommandEverywhere} on, or with {@code /almanac weather everywhere}, it acts on the whole
 * world as vanilla's does: a world-wide clear also stops the snow everywhere. Either way it is logged in the weather
 * record, so it holds after a restart and the snow follows it.
 */
public final class WeatherCommands {
    /** How far a local {@code /weather clear} keeps the sky clear, in blocks: past what a player can see. */
    static final double CLEAR_RADIUS = 2_500.0;
    /** How far a local {@code /weather rain} storm reaches, in blocks. */
    static final double STORM_RADIUS = 1_200.0;

    public enum Kind {
        CLEAR, RAIN, THUNDER
    }

    private WeatherCommands() {
    }

    /** Whether {@code /weather} from this source acts on the storms: regional weather runs in its level's overworld. */
    public static boolean regional(CommandSourceStack source) {
        return LocalWeather.active(source.getServer().overworld());
    }

    /** Vanilla's {@code /weather}, with its duration or -1 for vanilla's own default. */
    public static int vanillaCommand(CommandSourceStack source, Kind kind, int duration) {
        boolean everywhere = ServerConfig.get().weatherCommandEverywhere || source.getLevel().dimension() != Level.OVERWORLD;
        return act(source, kind, duration, everywhere);
    }

    static LiteralArgumentBuilder<CommandSourceStack> almanacWeather() {
        return Commands.literal("weather")
                .executes(WeatherCommands::showHere)
                .then(Commands.literal("storms")
                        .executes(context -> showStorms(context, 8_000))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(100, 32_000))
                                .executes(context -> showStorms(context, IntegerArgumentType.getInteger(context, "radius")))))
                .then(Commands.literal("forecast").executes(WeatherCommands::showForecast))
                .then(Commands.literal("status").executes(WeatherCommands::showStatus))
                .then(Commands.literal("everywhere")
                        .then(everywhere("clear", Kind.CLEAR))
                        .then(everywhere("rain", Kind.RAIN))
                        .then(everywhere("thunder", Kind.THUNDER)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> everywhere(String name, Kind kind) {
        return Commands.literal(name)
                .executes(context -> act(context.getSource(), kind, -1, true))
                .then(Commands.argument("duration", TimeArgument.time(1))
                        .executes(context -> act(context.getSource(), kind,
                                IntegerArgumentType.getInteger(context, "duration"), true)));
    }

    private static int act(CommandSourceStack source, Kind kind, int asked, boolean everywhere) {
        ServerLevel overworld = source.getServer().overworld();
        if (!LocalWeather.active(overworld)) {
            source.sendFailure(Component.literal("Regional weather isn't running (regionalWeather is off)."));
            return 0;
        }
        IntProvider defaults = switch (kind) {
            case CLEAR -> ServerLevel.RAIN_DELAY;
            case RAIN -> ServerLevel.RAIN_DURATION;
            case THUNDER -> ServerLevel.THUNDER_DURATION;
        };
        int duration = asked < 0 ? defaults.sample(overworld.getRandom()) : asked;
        long now = overworld.getGameTime();
        Vec3 place = source.getPosition();
        WeatherRecord.EventKind eventKind = switch (kind) {
            case CLEAR -> everywhere ? WeatherRecord.EventKind.CLEAR_EVERYWHERE : WeatherRecord.EventKind.CLEAR_AROUND;
            case RAIN -> everywhere ? WeatherRecord.EventKind.RAIN_EVERYWHERE : WeatherRecord.EventKind.STORM;
            case THUNDER -> everywhere ? WeatherRecord.EventKind.THUNDER_EVERYWHERE : WeatherRecord.EventKind.THUNDERSTORM;
        };
        double radius = kind == Kind.CLEAR ? CLEAR_RADIUS : STORM_RADIUS;
        Services.weather().addEvent(overworld, new WeatherRecord.Event(eventKind, now, now + duration, place.x, place.z,
                everywhere ? 0.0 : radius));
        String where = everywhere ? "everywhere" : String.format(Locale.ROOT, "within %.0f blocks of %d, %d", radius,
                (int) Math.floor(place.x), (int) Math.floor(place.z));
        String what = switch (kind) {
            case CLEAR -> "Clear skies";
            case RAIN -> everywhere ? "Rain or snow" : "A storm";
            case THUNDER -> everywhere ? "Thunderstorms" : "A thunderstorm";
        };
        source.sendSuccess(() -> Component.literal(what + " " + where + " for " + duration + " ticks."), true);
        return duration;
    }

    private static int showHere(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        if (!LocalWeather.active(level)) {
            source.sendFailure(Component.literal("Regional weather isn't running here."));
            return 0;
        }
        BlockPos pos = BlockPos.containing(source.getPosition());
        WeatherHere here = LocalWeather.storms(level, pos);
        String text = String.format(Locale.ROOT, "Here (%d, %d, %d): storm %.2f, rain %.2f, thunder %.0f, %s falls. %s",
                pos.getX(), pos.getY(), pos.getZ(), here.storm(), LocalWeather.rainLevel(level, pos, here),
                LocalWeather.thunderLevel(level, pos, here), level.precipitationAt(pos).getSerializedName(), here);
        source.sendSuccess(() -> Component.literal(text), false);
        return Math.round(here.storm() * 100);
    }

    private static int showStorms(CommandContext<CommandSourceStack> context, int radius) {
        CommandSourceStack source = context.getSource();
        WeatherView view = Services.weather().view(source.getLevel());
        if (view == null || !LocalWeather.active(source.getLevel())) {
            source.sendFailure(Component.literal("Regional weather isn't running here."));
            return 0;
        }
        Vec3 place = source.getPosition();
        long gameTick = source.getLevel().getGameTime();
        double tick = view.weatherTick(gameTick);
        List<Storm> storms = view.stormsNear(place.x, place.z, radius, tick, gameTick);
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "%d storms within %d blocks (weather tick %.0f):",
                storms.size(), radius, tick));
        for (Storm storm : storms.subList(0, Math.min(12, storms.size()))) {
            double dx = storm.centreX(tick) - place.x;
            double dz = storm.centreZ(tick) - place.z;
            double heading = Math.toDegrees(Math.atan2(storm.velocityZ(), storm.velocityX()));
            text.append(String.format(Locale.ROOT, "%n  %s%s, centre %.0f blocks %s, radius %.0f x%.1f, moving %.2f "
                            + "blocks a tick %s, %.0f%% through a life of %d ticks, rain %.2f, cloud top %d",
                    Long.toHexString(storm.id()).substring(0, 6), storm.thunder() ? " (thunderstorm)" : "",
                    Math.hypot(dx, dz), compass(Math.toDegrees(Math.atan2(dz, dx))), storm.radius(), storm.stretch(),
                    Math.hypot(storm.velocityX(), storm.velocityZ()), compass(heading), storm.phase(tick) * 100,
                    storm.lifeTicks(), storm.strength(), storm.cloudTop()));
        }
        source.sendSuccess(() -> Component.literal(text.toString()), false);
        return storms.size();
    }

    /** How far ahead {@code /almanac weather forecast} looks, in ticks: three days. */
    static final long FORECAST_TICKS = 72_000;

    /**
     * When it next rains or snows where the caller stands, and for how long: worked out from the storms, which are
     * known ahead, so it is exact unless an operator changes the weather meanwhile.
     */
    private static int showForecast(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        WeatherView view = Services.weather().view(level);
        if (view == null || !LocalWeather.active(level)) {
            source.sendFailure(Component.literal("Regional weather isn't running here."));
            return 0;
        }
        Vec3 place = source.getPosition();
        BlockPos pos = BlockPos.containing(place);
        Climate climate = LocalWeather.climateAt(level, pos);
        long now = level.getGameTime();
        long start = -1;
        long end = -1;
        long tick = now;
        while (tick < now + FORECAST_TICKS) {
            WetSpell spell = view.wetSpell(place.x, place.z, tick, tick);
            boolean wet = spell.wetAtEnd(climate);
            if (wet && start < 0) {
                start = tick;
            } else if (!wet && start >= 0) {
                end = tick;
                break;
            }
            tick = Math.max(tick + 1, spell.stableUntil());
        }
        String text;
        if (start < 0) {
            text = "No rain or snow here (" + climate.id() + ") in the next " + FORECAST_TICKS + " ticks.";
        } else if (start == now) {
            text = "Raining or snowing here (" + climate.id() + ") now" + (end < 0 ? ", for the next "
                    + FORECAST_TICKS + " ticks at least." : ", for another " + (end - now) + " ticks (until tick " + end + ").");
        } else {
            text = "Rain or snow here (" + climate.id() + ") in " + (start - now) + " ticks (at tick " + start + ")"
                    + (end < 0 ? ", lasting past the forecast." : ", for " + (end - start) + " ticks (until tick " + end + ").");
        }
        String reply = text;
        source.sendSuccess(() -> Component.literal(reply), false);
        return start < 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, start - now);
    }

    /** A direction in degrees from east towards south, as a compass point. */
    private static String compass(double degrees) {
        String[] points = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        return points[Math.floorMod((int) Math.round(degrees / 45.0), 8)];
    }

    private static int showStatus(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String text = "Regional weather " + (ServerConfig.get().regionalWeather ? "on" : "off")
                + ", /weather acts " + (ServerConfig.get().weatherCommandEverywhere ? "everywhere" : "around who runs it")
                + "\n" + Services.weather().describe(source.getServer().overworld());
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
