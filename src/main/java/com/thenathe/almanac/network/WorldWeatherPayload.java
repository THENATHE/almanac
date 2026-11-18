package com.thenathe.almanac.network;

import com.thenathe.almanac.Almanac;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C: the overworld's own rain and thunder levels, before each player's share is applied (players are sent their
 * share through vanilla's weather events). Sent to clients with Almanac 0.1.2 or later on joining and whenever either
 * level changes, which during a fade is every tick, like vanilla's own weather events.
 */
public record WorldWeatherPayload(float rain, float thunder) implements CustomPacketPayload {
    private static final int FORMAT = 1;

    public static final Type<WorldWeatherPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "world_weather"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WorldWeatherPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(FORMAT);
                buffer.writeFloat(payload.rain());
                buffer.writeFloat(payload.thunder());
            },
            buffer -> {
                buffer.readVarInt();
                WorldWeatherPayload weather = new WorldWeatherPayload(Math.clamp(buffer.readFloat(), 0.0F, 1.0F),
                        Math.clamp(buffer.readFloat(), 0.0F, 1.0F));
                PayloadFormat.skipNewerFields(buffer);
                return weather;
            });

    @Override
    public Type<WorldWeatherPayload> type() {
        return TYPE;
    }
}
