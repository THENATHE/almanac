package com.thenathe.almanac.climate;

import com.thenathe.almanac.biome.SeasonalBiomeIndex;
import com.thenathe.almanac.network.WorldWeatherPayload;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;

/**
 * Gives every overworld player their own rain and thunder strength through vanilla's weather game events. Vanilla
 * has one rain level per dimension; here each player's is that level times their share of the current event, faded
 * at vanilla's own rate as they walk between climates:
 * <ul>
 *   <li>where rain or snow can fall this sub-season, all of it if the event reaches the climate there and none if it
 *   doesn't, so the sky stays clear wherever nothing will fall;</li>
 *   <li>where nothing can fall this sub-season (the sub-season's copy of the biome has no precipitation: deserts and
 *   badlands outside winter, temperate land in mid summer), all of it. The sky darkens as in plain Minecraft and
 *   nothing falls, which mods such as LiteLights draw as a sandstorm (user, 2026-11-17).</li>
 * </ul>
 * Clients with Almanac 0.1.2 or later are also sent the dimension's own levels ({@link WorldWeatherPayload}).
 */
public final class PlayerWeather {
    /** Vanilla fades rain in and out by 0.01 a tick. */
    private static final float FADE_PER_TICK = 0.01F;
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static float sentWorldRain = -1.0F;
    private static float sentWorldThunder = -1.0F;

    private PlayerWeather() {
    }

    private static final class State {
        float reach;
        float sentRain = -1.0F;
        float sentThunder = -1.0F;

        State(float reach) {
            this.reach = reach;
        }
    }

    /** Whether this mod sends the level's rain and thunder levels itself. */
    public static boolean manages(ServerLevel level) {
        return ClimateHooks.forLevel(level) != null;
    }

    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        SeasonalClimate climate = ClimateHooks.forLevel(overworld);
        if (climate == null) {
            STATES.clear();
            sentWorldRain = -1.0F;
            sentWorldThunder = -1.0F;
            return;
        }
        sendWorldWeather(server, overworld);
        STATES.keySet().removeIf(id -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            return player == null || player.level() != overworld;
        });
        for (ServerPlayer player : overworld.players()) {
            float target = shareWhereStanding(climate, overworld, player);
            State state = STATES.computeIfAbsent(player.getUUID(), id -> new State(target));
            state.reach += Math.clamp(target - state.reach, -FADE_PER_TICK, FADE_PER_TICK);
            float rain = overworld.getRainLevel(1.0F) * state.reach;
            float thunder = overworld.getThunderLevel(1.0F) * state.reach;
            if (rain != state.sentRain) {
                player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rain));
                state.sentRain = rain;
            }
            if (thunder != state.sentThunder) {
                player.connection.send(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunder));
                state.sentThunder = thunder;
            }
        }
    }

    /** The rain level to tell a player who just arrived in {@code level}. */
    public static float rainFor(ServerPlayer player, ServerLevel level) {
        return level.getRainLevel(1.0F) * startingReach(player, level);
    }

    public static float thunderFor(ServerPlayer player, ServerLevel level) {
        return level.getThunderLevel(1.0F) * startingReach(player, level);
    }

    private static float startingReach(ServerPlayer player, ServerLevel level) {
        SeasonalClimate climate = ClimateHooks.forLevel(level);
        if (climate == null) {
            return 1.0F;
        }
        float reach = shareWhereStanding(climate, level, player);
        State state = new State(reach);
        state.sentRain = level.getRainLevel(1.0F) * reach;
        state.sentThunder = level.getThunderLevel(1.0F) * reach;
        STATES.put(player.getUUID(), state);
        return reach;
    }

    /** Vanilla's start and stop events reset a client's rain level, so every player is sent theirs again. */
    public static void resendAll() {
        for (State state : STATES.values()) {
            state.sentRain = -1.0F;
            state.sentThunder = -1.0F;
        }
    }

    public static void forget(ServerPlayer player) {
        STATES.remove(player.getUUID());
    }

    public static boolean isWeatherLevelPacket(ClientboundGameEventPacket packet) {
        return packet.getEvent() == ClientboundGameEventPacket.RAIN_LEVEL_CHANGE
                || packet.getEvent() == ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE;
    }

    public static boolean isRainStartOrStop(ClientboundGameEventPacket packet) {
        return packet.getEvent() == ClientboundGameEventPacket.START_RAINING
                || packet.getEvent() == ClientboundGameEventPacket.STOP_RAINING;
    }

    /**
     * The player's share of the dimension's rain and thunder where they stand: 1 where nothing can fall this
     * sub-season or the current event reaches the climate, 0 where rain or snow could fall but the event doesn't
     * reach it. Biomes without seasons get vanilla's weather.
     */
    private static float shareWhereStanding(SeasonalClimate climate, ServerLevel level, ServerPlayer player) {
        Biome biome = level.getBiome(player.blockPosition()).value();
        SeasonalBiomeIndex.Entry entry = climate.entry(biome);
        if (entry == null) {
            return 1.0F;
        }
        return share(entry.copy(climate.subSeason()).value().hasPrecipitation(), climate.reaches(biome));
    }

    /**
     * @param canFall something can fall here this sub-season (the sub-season's copy of the biome has precipitation)
     * @param reached the current event reaches the climate here
     */
    static float share(boolean canFall, boolean reached) {
        return !canFall || reached ? 1.0F : 0.0F;
    }

    /** The dimension's own levels, to clients that take them, when they change. */
    private static void sendWorldWeather(MinecraftServer server, ServerLevel overworld) {
        float rain = overworld.getRainLevel(1.0F);
        float thunder = overworld.getThunderLevel(1.0F);
        if (rain == sentWorldRain && thunder == sentWorldThunder) {
            return;
        }
        sentWorldRain = rain;
        sentWorldThunder = thunder;
        WorldWeatherPayload payload = new WorldWeatherPayload(rain, thunder);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, WorldWeatherPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    /** A player who just joined gets the dimension's levels at once. */
    public static void playerJoined(ServerPlayer player) {
        ServerLevel overworld = player.level().getServer().overworld();
        if (ClimateHooks.forLevel(overworld) != null && ServerPlayNetworking.canSend(player, WorldWeatherPayload.TYPE)) {
            ServerPlayNetworking.send(player, new WorldWeatherPayload(overworld.getRainLevel(1.0F), overworld.getThunderLevel(1.0F)));
        }
    }

    /** For diagnosis: the rain and thunder levels each overworld player was last sent. */
    public static String describe(MinecraftServer server) {
        StringBuilder text = new StringBuilder(String.format(Locale.ROOT, "world rain %.3f thunder %.3f",
                server.overworld().getRainLevel(1.0F), server.overworld().getThunderLevel(1.0F)));
        STATES.forEach((id, state) -> {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                text.append(String.format(Locale.ROOT, "; %s rain %.3f thunder %.3f share %.2f", player.getName().getString(),
                        state.sentRain, state.sentThunder, state.reach));
            }
        });
        return text.toString();
    }
}
