package com.thenathe.almanac;

import com.thenathe.almanac.climatemap.ServerClimateMap;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.internal.AlmanacModule;
import com.thenathe.almanac.internal.Services;
import com.thenathe.almanac.internal.Snow;
import com.thenathe.almanac.modules.ModuleHost;
import com.thenathe.almanac.network.ServerNetworking;
import com.thenathe.almanac.season.SeasonCommands;
import com.thenathe.almanac.season.ServerSeasons;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Almanac implements ModInitializer {
    public static final String MOD_ID = AlmanacModule.MOD_ID;
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModuleHost.installApi();
        ServerConfig.get();
        ServerNetworking.initialize();
        // Where the snow chunk records were always registered, so they still are before any chunk is read.
        ModuleHost.startModules();
        Snow snow = Services.snow();

        ServerLifecycleEvents.SERVER_STARTING.register(server -> snow.serverStarting());
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
            ServerClimateMap.tick(server);
        });
        // At the end of the overworld's tick and before players are sent their chunks, so most chunks that loaded
        // since the last tick go out with the season's snow already on them.
        ServerTickEvents.END_LEVEL_TICK.register(snow::levelTicked);
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk, generated) -> snow.chunkLoaded(level, chunk));
        ServerChunkEvents.CHUNK_UNLOAD.register(snow::chunkUnloaded);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerNetworking.playerJoined(handler.player);
            ServerClimateMap.playerJoined(handler.player);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerClimateMap.playerLeft(handler.player);
            ServerNetworking.playerLeft(handler.player);
        });
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            if (!client) {
                ServerSeasons.tagsReloaded();
            }
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                SeasonCommands.register(dispatcher));

        LOGGER.info("Almanac is ready.");
    }

    private static void serverStopped(MinecraftServer server) {
        ServerClimateMap.serverStopped();
        ServerSeasons.serverStopped();
        Services.snow().serverStopped();
        Services.weather().serverStopped();
        ServerNetworking.serverStopped();
    }
}
