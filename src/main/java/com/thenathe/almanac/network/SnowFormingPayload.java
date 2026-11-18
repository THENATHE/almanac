package com.thenathe.almanac.network;

import com.thenathe.almanac.Almanac;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C, from Almanac 0.1.3.2: how long falling snow takes to whiten loaded land, in game ticks
 * ({@link com.thenathe.almanac.season.ServerSeasons#formingTicks}). It follows the {@code randomTickSpeed} game rule,
 * which vanilla doesn't send to clients, so the server sends the answer. Sent to clients that register this channel
 * (0.1.3.2 and later) on joining and whenever it changes.
 *
 * <p>A channel of its own rather than a field on {@code far_snow_changed}: it isn't part of the far map, so it reaches
 * clients with the map turned off too, and resending it when the game rule changes can't touch the far-snow change
 * tick that far-terrain animations are timed by.
 */
public record SnowFormingPayload(long formingTicks) implements CustomPacketPayload {
    private static final int FORMAT = 1;

    public static final Type<SnowFormingPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "snow_forming"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SnowFormingPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(FORMAT);
                buffer.writeVarLong(payload.formingTicks());
            },
            buffer -> {
                buffer.readVarInt();
                SnowFormingPayload forming = new SnowFormingPayload(Math.max(1L, buffer.readVarLong()));
                PayloadFormat.skipNewerFields(buffer);
                return forming;
            });

    @Override
    public Type<SnowFormingPayload> type() {
        return TYPE;
    }
}
