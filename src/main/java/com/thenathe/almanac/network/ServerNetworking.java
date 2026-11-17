package com.thenathe.almanac.network;

import com.thenathe.almanac.api.AlmanacEvents;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.biome.SeasonRefresh;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.season.ServerSeasons;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.jspecify.annotations.Nullable;

/** Registers the payloads, keeps clients with Almanac in step and answers settings changes. */
public final class ServerNetworking {
    private static @Nullable SeasonSyncPayload lastSync;

    private ServerNetworking() {
    }

    public static void initialize() {
        PayloadTypeRegistry.clientboundPlay().register(SeasonSyncPayload.TYPE, SeasonSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SettingsPayloads.Snapshot.TYPE, SettingsPayloads.Snapshot.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SettingsPayloads.Update.TYPE, SettingsPayloads.Update.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FarClimatePayloads.State.TYPE, FarClimatePayloads.State.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FarClimatePayloads.Tile.TYPE, FarClimatePayloads.Tile.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FarClimatePayloads.Forget.TYPE, FarClimatePayloads.Forget.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WorldWeatherPayload.TYPE, WorldWeatherPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SettingsPayloads.Update.TYPE, (payload, context) ->
                updateSettings(context.player(), payload.json()));
    }

    /**
     * Whether the player's client has Almanac. Fabric agrees on channels while the player is configured, so this
     * is known before the first chunk is sent.
     */
    public static boolean hasAlmanac(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, SeasonSyncPayload.TYPE);
    }

    public static void playerJoined(ServerPlayer player) {
        SeasonSyncPayload sync = currentSync();
        if (sync != null && hasAlmanac(player)) {
            ServerPlayNetworking.send(player, sync);
        }
        sendSettings(player);
    }

    /**
     * Sends the season to every client with Almanac when anything in it changed, and tells the server side's
     * {@link AlmanacEvents#SEASON_CHANGED} listeners. Clients fire the same event from the same comparison when the
     * sync arrives, so both sides agree on what counts as a change.
     */
    public static void syncSeasonIfChanged(MinecraftServer server) {
        SeasonSyncPayload sync = currentSync();
        if (sync == null || sync.equals(lastSync)) {
            return;
        }
        AlmanacEvents.Change change = sync.changeFrom(lastSync);
        lastSync = sync;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (hasAlmanac(player)) {
                ServerPlayNetworking.send(player, sync);
            }
        }
        if (change.any()) {
            AlmanacEvents.SEASON_CHANGED.invoker().onSeasonChanged(server.overworld(), change);
        }
    }

    public static void serverStopped() {
        lastSync = null;
    }

    private static @Nullable SeasonSyncPayload currentSync() {
        ServerSeasons seasons = ServerSeasons.get();
        if (seasons == null) {
            return null;
        }
        Map<Climate, Integer> snow = new EnumMap<>(Climate.class);
        for (Climate climate : Climate.values()) {
            snow.put(climate, SeasonSyncPayload.sixteenths(seasons.snowDepth(climate)));
        }
        return new SeasonSyncPayload(seasons.subSeason(), seasons.dayOfYear(), seasons.daysPerSeason(),
                ServerConfig.get().paused, seasons.climate().reached(), seasons.rainEvent(), snow);
    }

    public static void sendSettings(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, SettingsPayloads.Snapshot.TYPE)) {
            ServerPlayNetworking.send(player, new SettingsPayloads.Snapshot(mayEditSettings(player), ServerConfig.get().toJson()));
        }
    }

    /** Operators (permission level 2 and up) and the host of a singleplayer world. */
    public static boolean mayEditSettings(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || server.isSingleplayerOwner(player.nameAndId());
    }

    private static void updateSettings(ServerPlayer player, String json) {
        if (!mayEditSettings(player)) {
            player.sendSystemMessage(Component.translatableWithFallback("almanac.settings.denied",
                    "Only operators can change the server settings."));
            sendSettings(player);
            return;
        }
        ServerConfig updated = ServerConfig.fromJson(json);
        if (updated == null) {
            player.sendSystemMessage(Component.translatableWithFallback("almanac.settings.unreadable",
                    "The server couldn't read those settings, so nothing changed."));
            sendSettings(player);
            return;
        }
        applySettings(player.level().getServer(), updated);
    }

    /** Makes {@code updated} the live settings, brings the season up to date and tells every player. */
    public static void applySettings(MinecraftServer server, ServerConfig updated) {
        boolean copiesChanged = ServerConfig.get().vanillaClientCopies != updated.vanillaClientCopies;
        ServerConfig.replace(updated);
        ServerSeasons seasons = ServerSeasons.get();
        if (seasons != null && Objects.equals(seasons.server(), server)) {
            seasons.tick();
        }
        if (copiesChanged) {
            SeasonRefresh.refreshAll(server);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendSettings(player);
        }
    }
}
