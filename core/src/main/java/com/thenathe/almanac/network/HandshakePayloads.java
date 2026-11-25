package com.thenathe.almanac.network;

import com.thenathe.almanac.Almanac;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The two payloads of the check a server makes while a player connects (from 0.2.0, {@link ClientCheck}): the
 * server says which Almanac it runs and the client answers with its own. They are the one place two different
 * Almanacs still talk, so their first two fields never change: a version text for people and a protocol number for
 * the check. Anything after them is read only by clients and servers of the same protocol.
 */
public final class HandshakePayloads {
    private HandshakePayloads() {
    }

    /** S2C, while configuring: the server's Almanac. */
    public record ServerHello(String version, int protocol) implements CustomPacketPayload {
        public static final Type<ServerHello> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "server_hello"));
        public static final StreamCodec<FriendlyByteBuf, ServerHello> CODEC = StreamCodec.of(
                (buffer, hello) -> {
                    buffer.writeUtf(hello.version(), 64);
                    buffer.writeVarInt(hello.protocol());
                },
                buffer -> {
                    ServerHello hello = new ServerHello(buffer.readUtf(64), buffer.readVarInt());
                    PayloadFormat.skipNewerFields(buffer);
                    return hello;
                });

        @Override
        public Type<ServerHello> type() {
            return TYPE;
        }
    }

    /**
     * C2S, while configuring: the client's Almanac, and the version of a LiteLights too old to share far terrain
     * with it (empty when there is none), so {@code /almanac snow} can say why that player gets no climate map.
     */
    public record ClientHello(String version, int protocol, String oldLiteLights) implements CustomPacketPayload {
        public static final Type<ClientHello> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "client_hello"));
        public static final StreamCodec<FriendlyByteBuf, ClientHello> CODEC = StreamCodec.of(
                (buffer, hello) -> {
                    buffer.writeUtf(hello.version(), 64);
                    buffer.writeVarInt(hello.protocol());
                    buffer.writeUtf(hello.oldLiteLights(), 64);
                },
                buffer -> {
                    String version = buffer.readUtf(64);
                    int protocol = buffer.readVarInt();
                    // Another protocol may lay out what follows differently; that client is refused, so it isn't read.
                    String oldLiteLights = protocol == ClientCheck.PROTOCOL && buffer.isReadable() ? buffer.readUtf(64) : "";
                    PayloadFormat.skipNewerFields(buffer);
                    return new ClientHello(version, protocol, oldLiteLights);
                });

        @Override
        public Type<ClientHello> type() {
            return TYPE;
        }
    }
}
