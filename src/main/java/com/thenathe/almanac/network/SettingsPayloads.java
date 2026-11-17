package com.thenathe.almanac.network;

import com.thenathe.almanac.Almanac;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server settings travel as the same JSON the server keeps in its config file. */
public final class SettingsPayloads {
    private static final int MAX_JSON_LENGTH = 65536;

    private SettingsPayloads() {
    }

    /** S2C: the server's current settings, and whether this player may change them. */
    public record Snapshot(boolean canEdit, String json) implements CustomPacketPayload {
        public static final Type<Snapshot> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "server_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
                (buffer, snapshot) -> {
                    buffer.writeBoolean(snapshot.canEdit());
                    buffer.writeUtf(snapshot.json(), MAX_JSON_LENGTH);
                },
                buffer -> new Snapshot(buffer.readBoolean(), buffer.readUtf(MAX_JSON_LENGTH)));

        @Override
        public Type<Snapshot> type() {
            return TYPE;
        }
    }

    /** C2S: an operator saved new server settings. The server checks the permission and every value. */
    public record Update(String json) implements CustomPacketPayload {
        public static final Type<Update> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "update_server_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Update> CODEC = StreamCodec.of(
                (buffer, update) -> buffer.writeUtf(update.json(), MAX_JSON_LENGTH),
                buffer -> new Update(buffer.readUtf(MAX_JSON_LENGTH)));

        @Override
        public Type<Update> type() {
            return TYPE;
        }
    }
}
