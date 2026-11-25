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
                buffer -> {
                    Snapshot snapshot = new Snapshot(buffer.readBoolean(), buffer.readUtf(MAX_JSON_LENGTH));
                    PayloadFormat.skipNewerFields(buffer);
                    return snapshot;
                });

        @Override
        public Type<Snapshot> type() {
            return TYPE;
        }
    }

    /**
     * C2S: only the settings an operator changed on the screen, as a JSON object of those fields
     * ({@link com.thenathe.almanac.config.ServerConfig#changes}). The server applies them on top of its current
     * settings, so changes others made meanwhile stay. (Until 0.3.0 the server also took every field at once on
     * {@code almanac:update_server_settings}, from 0.1.2 clients, which can't join since 0.2.0.)
     */
    public record Change(String json) implements CustomPacketPayload {
        public static final Type<Change> TYPE =
                new Type<>(Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "change_server_settings"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Change> CODEC = StreamCodec.of(
                (buffer, change) -> buffer.writeUtf(change.json(), MAX_JSON_LENGTH),
                buffer -> {
                    Change change = new Change(buffer.readUtf(MAX_JSON_LENGTH));
                    PayloadFormat.skipNewerFields(buffer);
                    return change;
                });

        @Override
        public Type<Change> type() {
            return TYPE;
        }
    }
}
