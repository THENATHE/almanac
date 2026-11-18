package com.thenathe.almanac.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.thenathe.almanac.Almanac;
import com.thenathe.almanac.client.colour.ClientColours;
import com.thenathe.almanac.client.colour.PaletteReloader;
import com.thenathe.almanac.climate.ClimateHooks;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.network.FarClimatePayloads;
import com.thenathe.almanac.network.SeasonSyncPayload;
import com.thenathe.almanac.network.WorldWeatherPayload;
import com.thenathe.almanac.network.SettingsPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;

public final class AlmanacClient implements ClientModInitializer {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "main"));
    private static KeyMapping openSettings;

    @Override
    public void onInitializeClient() {
        ClimateHooks.setClientClimate(ClientSeasons::climate);
        ClimateHooks.setClientSnowDepth(ClientSeasons::snowDepth);
        ClientPlayNetworking.registerGlobalReceiver(SeasonSyncPayload.TYPE, (payload, context) -> ClientSeasons.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(SettingsPayloads.Snapshot.TYPE, (payload, context) ->
                ClientState.settings(payload.canEdit(), ServerConfig.fromJson(payload.json())));
        // Registering these is what tells the server this client takes the climate map and the world's weather.
        ClimateHooks.setFarClimate(ClientClimateMap.INSTANCE);
        ClimateHooks.setClientWorldWeather(ClientSeasons::worldWeather);
        ClimateHooks.setClientThawTicks(ClientClimateMap.INSTANCE::thawTicks);
        ClientPlayNetworking.registerGlobalReceiver(WorldWeatherPayload.TYPE, (payload, context) ->
                ClientSeasons.receiveWorldWeather(payload));
        ClientPlayNetworking.registerGlobalReceiver(FarClimatePayloads.State.TYPE, (payload, context) ->
                ClientClimateMap.INSTANCE.receiveState(payload));
        ClientPlayNetworking.registerGlobalReceiver(FarClimatePayloads.Tile.TYPE, (payload, context) ->
                ClientClimateMap.INSTANCE.receiveTile(payload));
        ClientPlayNetworking.registerGlobalReceiver(FarClimatePayloads.Forget.TYPE, (payload, context) ->
                ClientClimateMap.INSTANCE.receiveForget(payload));
        ClientPlayNetworking.registerGlobalReceiver(FarClimatePayloads.SnowChanged.TYPE, (payload, context) ->
                ClientClimateMap.INSTANCE.receiveSnowChanged(payload));
        ClimateHooks.setClientFarSnowChanged(ClientClimateMap.INSTANCE::snowChangedTick);
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientClimateMap.INSTANCE.disconnected();
            ClientSeasons.disconnected();
            ClientState.reset();
            ClientColours.disconnected();
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
        });
    }
}
