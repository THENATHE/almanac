package com.thenathe.almanac.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.almanac.Almanac;
import com.thenathe.almanac.client.colour.ClientColours;
import com.thenathe.almanac.client.colour.PaletteReloader;
import com.thenathe.almanac.climate.ClimateHooks;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.internal.Services;
import com.thenathe.almanac.network.ClientCheck;
import com.thenathe.almanac.network.ClimateTablePayload;
import com.thenathe.almanac.network.FarClimatePayloads;
import com.thenathe.almanac.network.HandshakePayloads;
import com.thenathe.almanac.network.SeasonSyncPayload;
import com.thenathe.almanac.network.SettingsPayloads;
import com.thenathe.almanac.network.SnowLogPayload;
import com.thenathe.almanac.network.WeatherSyncPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

public final class AlmanacClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "main"));
    private static KeyMapping openSettings;

    @Override
    public void onInitializeClient() {
        ClimateHooks.setClientClimate(ClientSeasons::climate);
        ClimateHooks.setClientSnow(ClientSeasons::snow);
        ClimateHooks.setClientStorm((level, thunder) -> level instanceof ClientLevel client
                ? ClientRain.stormLevel(client, thunder) : null);
        // The server asks which Almanac this is while connecting, and refuses a game without one (from 0.2.0).
        ClientConfigurationNetworking.registerGlobalReceiver(HandshakePayloads.ServerHello.TYPE, (payload, context) ->
                context.responseSender().sendPacket(new HandshakePayloads.ClientHello(ClientCheck.version(),
                        ClientCheck.PROTOCOL, LiteLightsCheck.tooOldForFarTerrain().orElse(""))));
        ClientPlayNetworking.registerGlobalReceiver(ClimateTablePayload.TYPE, (payload, context) -> ClientSeasons.receiveTable(payload));
        ClientPlayNetworking.registerGlobalReceiver(SeasonSyncPayload.TYPE, (payload, context) -> ClientSeasons.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(WeatherSyncPayload.TYPE, (payload, context) ->
                Services.weather().clientReceived(payload.record()));
        ClientPlayNetworking.registerGlobalReceiver(SnowLogPayload.TYPE, (payload, context) ->
                Services.snow().clientReceivedLog(payload.log()));
        ClientPlayNetworking.registerGlobalReceiver(SettingsPayloads.Snapshot.TYPE, (payload, context) ->
                ClientState.settings(payload.canEdit(), ServerConfig.fromJson(payload.json())));
        // Registering these is what tells the server this client takes the climate map. Beside a LiteLights too old
        // for this Almanac it doesn't, so far terrain keeps its captured snow.
        if (LiteLightsCheck.farTerrainShared()) {
            registerClimateMap();
        }
        LiteLightsCheck.logAtStart();
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> LiteLightsCheck.joined(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientClimateMap.INSTANCE.disconnected();
            ClientSeasons.disconnected();
            ClientState.reset();
            ClientColours.disconnected();
            ClientRain.forget();
            Services.weather().clientDisconnected();
            Services.snow().clientDisconnected();
        });

        ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(PaletteReloader.ID, new PaletteReloader());
        ClientLifecycleEvents.CLIENT_STARTED.register(ClientColours::clientStarted);
        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            if (client) {
                ClientColours.tagsLoaded();
            }
        });

        // Unbound by default, so it never takes a key another mod already uses.
        openSettings = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.almanac.settings",
                InputConstants.Type.KEYBOARD, InputConstants.UNKNOWN.getValue(), CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openSettings.consumeClick()) {
                client.gui.setScreen(AlmanacSettings.create(client.gui.screen()));
            }
            ClientColours.tick(client);
            if (client.level != null && client.getCameraEntity() != null) {
                Services.weather().clientTicked(client.level, client.getCameraEntity().getX(),
                        client.getCameraEntity().getZ());
                Services.snow().clientTicked(client.level);
            }
            ClientRain.tick(client);
        });
    }

    private static void registerClimateMap() {
        ClimateHooks.setFarClimate(ClientClimateMap.INSTANCE);
        ClientPlayNetworking.registerGlobalReceiver(FarClimatePayloads.State.TYPE, (payload, context) ->
                ClientClimateMap.INSTANCE.receiveState(payload));
        ClientPlayNetworking.registerGlobalReceiver(FarClimatePayloads.Tile.TYPE, (payload, context) ->
                ClientClimateMap.INSTANCE.receiveTile(payload));
        ClientPlayNetworking.registerGlobalReceiver(FarClimatePayloads.Forget.TYPE, (payload, context) ->
                ClientClimateMap.INSTANCE.receiveForget(payload));
        ClientPlayNetworking.registerGlobalReceiver(FarClimatePayloads.SnowChanged.TYPE, (payload, context) ->
                ClientClimateMap.INSTANCE.receiveSnowChanged(payload));
        ClimateHooks.setClientFarSnowChanged(ClientClimateMap.INSTANCE::snowChangedTick);
    }
}
