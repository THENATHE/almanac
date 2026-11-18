package com.thenathe.almanac;

import com.thenathe.almanac.biome.SeasonRefresh;
import com.thenathe.almanac.biome.SeasonalBiomeView;
import com.thenathe.almanac.climate.PlayerWeather;
import com.thenathe.almanac.climatemap.ServerClimateMap;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.network.ServerNetworking;
import com.thenathe.almanac.season.SeasonCommands;
import com.thenathe.almanac.season.ServerSeasons;
import com.thenathe.almanac.snow.SnowTracking;
import com.thenathe.almanac.snow.SnowUpdates;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Almanac implements ModInitializer {
    public static final String MOD_ID = "almanac";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ServerConfig.get();
        ServerNetworking.initialize();
        SnowTracking.initialize();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> SnowUpdates.serverStarting());
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            ServerSeasons.serverStarted(server);
            ServerClimateMap.serverStarted(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(Almanac::serverStopped);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            ServerSeasons seasons = ServerSeasons.get();
            if (seasons != null) {
                seasons.tick();
            }
            PlayerWeather.tick(server);
            SeasonRefresh.tick(server);
            ServerClimateMap.tick(server);
        });
        // At the end of the overworld's tick and before players are sent their chunks, so most chunks that loaded
        // since the last tick go out with the season's snow already on them.
        ServerTickEvents.END_LEVEL_TICK.register(SnowUpdates::tick);
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> SnowUpdates.chunkLoaded(level, chunk));
        ServerChunkEvents.CHUNK_UNLOAD.register(SnowUpdates::chunkUnloaded);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerNetworking.playerJoined(handler.player);
            ServerClimateMap.playerJoined(handler.player);
            PlayerWeather.playerJoined(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            PlayerWeather.forget(handler.player);
            SeasonRefresh.forget(handler.player);
            ServerClimateMap.playerLeft(handler.player);
            ServerNetworking.playerLeft(handler.player);
        });
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) ->
                SeasonRefresh.forget(player));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                SeasonCommands.register(dispatcher));

        LOGGER.info("Almanac is ready.");
    }

    private static void serverStopped(MinecraftServer server) {
        ServerClimateMap.serverStopped();
        ServerSeasons.serverStopped();
        SnowUpdates.serverStopped();
        ServerNetworking.serverStopped();
        SeasonalBiomeView.serverStopped();
    }
}
