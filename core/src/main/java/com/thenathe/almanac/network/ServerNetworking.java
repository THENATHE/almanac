package com.thenathe.almanac.network;

import com.thenathe.almanac.api.AlmanacEvents;
import com.thenathe.almanac.climate.ClimateTable;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.internal.Services;
import com.thenathe.almanac.internal.SnowReading;
import com.thenathe.almanac.internal.WeatherRecord;
import com.thenathe.almanac.season.ServerSeasons;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.jspecify.annotations.Nullable;

/**
 * Registers the payloads, keeps clients in step and answers settings changes. Every client has Almanac of the same
 * protocol as the server ({@link ClientCheck}, from 0.2.0); payloads still follow {@link PayloadFormat}.
 */
public final class ServerNetworking {
    private static @Nullable SeasonSyncPayload lastSync;
    /**
     * Under regional weather each player's season sync carries the snow where they stand, so each is compared with
     * the last one that player got.
     */
    private static final Map<UUID, SeasonSyncPayload> PLAYER_SYNCS = new HashMap<>();
    /** The weather record's and the weather log's change counts when they were last sent to everyone. */
    private static long sentWeather = -1;
    private static long sentLog = -1;
    /** Whether each player was last told they may change the settings, to tell them again when that changes. */
    private static final Map<UUID, Boolean> SENT_EDIT_RIGHTS = new HashMap<>();

    private ServerNetworking() {
    }

    public static void initialize() {
        ClientCheck.initialize();
        PayloadTypeRegistry.clientboundPlay().register(ClimateTablePayload.TYPE, ClimateTablePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SeasonSyncPayload.TYPE, SeasonSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(WeatherSyncPayload.TYPE, WeatherSyncPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SnowLogPayload.TYPE, SnowLogPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SettingsPayloads.Snapshot.TYPE, SettingsPayloads.Snapshot.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SettingsPayloads.Change.TYPE, SettingsPayloads.Change.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FarClimatePayloads.State.TYPE, FarClimatePayloads.State.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FarClimatePayloads.Tile.TYPE, FarClimatePayloads.Tile.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FarClimatePayloads.Forget.TYPE, FarClimatePayloads.Forget.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(FarClimatePayloads.SnowChanged.TYPE, FarClimatePayloads.SnowChanged.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SettingsPayloads.Change.TYPE, (payload, context) ->
                updateSettings(context.player(), payload.json()));
    }

    /**
     * Whether the player's client takes the season. Every player has Almanac from 0.2.0, so this only fails for a
     * player still joining; Fabric agrees on channels while the player is configured, before the first chunk is sent.
     */
    public static boolean hasAlmanac(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, SeasonSyncPayload.TYPE);
    }

    public static void playerJoined(ServerPlayer player) {
        ServerSeasons seasons = ServerSeasons.get();
        if (seasons != null && ServerPlayNetworking.canSend(player, ClimateTablePayload.TYPE)) {
            ServerPlayNetworking.send(player, tablePayload(seasons.server(), seasons.table()));
        }
        // The weather record before the season, so a client reads the season's rain from the storms at once.
        WeatherRecord weather = Services.weather().serverRecord();
        if (weather != null && ServerPlayNetworking.canSend(player, WeatherSyncPayload.TYPE)) {
            ServerPlayNetworking.send(player, new WeatherSyncPayload(weather));
        }
        CompoundTag log = Services.snow().logForClients();
        if (log != null && ServerPlayNetworking.canSend(player, SnowLogPayload.TYPE)) {
            ServerPlayNetworking.send(player, new SnowLogPayload(log));
        }
        SeasonSyncPayload sync = syncFor(player);
        if (sync != null && hasAlmanac(player)) {
            ServerPlayNetworking.send(player, sync);
            PLAYER_SYNCS.put(player.getUUID(), sync);
        }
        sendSettings(player);
    }

    /** Sends the weather record and the weather log to every client when either changed since it was last sent. */
    public static void syncWeatherIfChanged(MinecraftServer server) {
        long changes = Services.weather().serverRecordChanges();
        WeatherRecord weather = Services.weather().serverRecord();
        if (changes != sentWeather && weather != null) {
            sentWeather = changes;
            WeatherSyncPayload payload = new WeatherSyncPayload(weather);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (ServerPlayNetworking.canSend(player, WeatherSyncPayload.TYPE)) {
                    ServerPlayNetworking.send(player, payload);
                }
            }
        }
        long logChanges = Services.snow().logChanges();
        CompoundTag log = logChanges == sentLog ? null : Services.snow().logForClients();
        if (log != null) {
            sentLog = logChanges;
            SnowLogPayload payload = new SnowLogPayload(log);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (ServerPlayNetworking.canSend(player, SnowLogPayload.TYPE)) {
                    ServerPlayNetworking.send(player, payload);
                }
            }
        }
    }

    /** The table was built anew: every player gets it, and their climate follows at once. */
    public static void sendClimateTable(MinecraftServer server, ClimateTable table) {
        ClimateTablePayload payload = tablePayload(server, table);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (ServerPlayNetworking.canSend(player, ClimateTablePayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }

    private static ClimateTablePayload tablePayload(MinecraftServer server, ClimateTable table) {
        return ClimateTablePayload.of(table, server.registryAccess().lookupOrThrow(Registries.BIOME));
    }

    /**
     * Sends the season to every client with Almanac when anything in it changed, and tells the server side's
     * {@link AlmanacEvents#SEASON_CHANGED} listeners. Clients fire the same event from the same comparison when the
     * sync arrives, so both sides agree on what counts as a change.
     */
    public static void syncSeasonIfChanged(MinecraftServer server) {
        SeasonSyncPayload sync = currentSync(null);
        if (sync == null) {
            return;
        }
        boolean worldChanged = !sync.sameAs(lastSync);
        AlmanacEvents.Change change = sync.changeFrom(lastSync);
        if (worldChanged) {
            lastSync = sync;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!hasAlmanac(player)) {
                continue;
            }
            // Under regional weather the snow is the player's own; otherwise everyone gets the world's.
            SeasonSyncPayload theirs = regionalSnow() ? syncFor(player) : sync;
            SeasonSyncPayload sent = PLAYER_SYNCS.get(player.getUUID());
            if (theirs != null && (worldChanged || !theirs.sameAs(sent))) {
                ServerPlayNetworking.send(player, theirs);
                PLAYER_SYNCS.put(player.getUUID(), theirs);
            }
        }
        if (worldChanged && change.any()) {
            AlmanacEvents.SEASON_CHANGED.invoker().onSeasonChanged(server.overworld(), change);
        }
    }

    private static boolean regionalSnow() {
        return ServerConfig.get().regionalWeather;
    }

    /** The season sync for one player: with the snow where they stand under regional weather. */
    private static @Nullable SeasonSyncPayload syncFor(ServerPlayer player) {
        if (!regionalSnow()) {
            return currentSync(null);
        }
        // A player in another dimension gets the snow where they would come back to the overworld, near enough.
        SnowReading here = Services.snow().readingAt(player.getX(), player.getZ());
        return currentSync(here);
    }

    public static void serverStopped() {
        lastSync = null;
        PLAYER_SYNCS.clear();
        sentWeather = -1;
        sentLog = -1;
        SENT_EDIT_RIGHTS.clear();
    }

    public static void playerLeft(ServerPlayer player) {
        SENT_EDIT_RIGHTS.remove(player.getUUID());
        PLAYER_SYNCS.remove(player.getUUID());
        ClientCheck.playerLeft(player);
    }

    /**
     * The game told a player their permission level, for example after {@code /op} or {@code /deop}. A player whose
     * right to change the settings changed gets the settings again with the new right, so the settings screen follows
     * without reconnecting.
     */
    public static void permissionsSent(ServerPlayer player) {
        Boolean sent = SENT_EDIT_RIGHTS.get(player.getUUID());
        if (sent != null && sent != mayEditSettings(player)) {
            sendSettings(player);
        }
    }

    /** The season sync now, with the world's snow or, given one, the snow of a place. */
    private static @Nullable SeasonSyncPayload currentSync(@Nullable SnowReading place) {
        ServerSeasons seasons = ServerSeasons.get();
        if (seasons == null) {
            return null;
        }
        SnowReading snow = place != null ? place : seasons.snow();
        if (snow == null) {
            return null;
        }
        return new SeasonSyncPayload(seasons.subSeason(), seasons.dayOfYear(), seasons.daysPerSeason(),
                ServerConfig.get().paused, seasons.climate().reached(), seasons.rainEvent(),
                seasons.server().overworld().getGameTime(), snow);
    }

    public static void sendSettings(ServerPlayer player) {
        if (ServerPlayNetworking.canSend(player, SettingsPayloads.Snapshot.TYPE)) {
            boolean mayEdit = mayEditSettings(player);
            ServerPlayNetworking.send(player, new SettingsPayloads.Snapshot(mayEdit, ServerConfig.get().toJson()));
            SENT_EDIT_RIGHTS.put(player.getUUID(), mayEdit);
        }
    }

    /** Operators (permission level 2 and up) and the host of a singleplayer world. */
    public static boolean mayEditSettings(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER) || server.isSingleplayerOwner(player.nameAndId());
    }

    /** Applies the changes a player sent, if the server's own check says they may change the settings. */
    private static void updateSettings(ServerPlayer player, String json) {
        SettingsChange change = SettingsChange.judge(mayEditSettings(player), ServerConfig.get(), json);
        switch (change.outcome()) {
            case DENIED -> {
                player.sendSystemMessage(Component.translatableWithFallback("almanac.settings.denied",
                        "Only operators can change the server settings."));
                sendSettings(player);
            }
            case UNREADABLE -> {
                player.sendSystemMessage(Component.translatableWithFallback("almanac.settings.unreadable",
                        "The server couldn't read those settings, so nothing changed."));
                sendSettings(player);
            }
            case APPLIED -> applySettings(player.level().getServer(), change.settings());
        }
    }

    /** Makes {@code updated} the live settings, brings the season up to date and tells every player. */
    public static void applySettings(MinecraftServer server, ServerConfig updated) {
        ServerConfig.replace(updated);
        ServerSeasons seasons = ServerSeasons.get();
        if (seasons != null && Objects.equals(seasons.server(), server)) {
            seasons.tick();
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sendSettings(player);
        }
    }
}
