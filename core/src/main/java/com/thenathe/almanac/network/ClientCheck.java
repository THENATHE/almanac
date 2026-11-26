package com.thenathe.almanac.network;

import com.thenathe.almanac.Almanac;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ConfigurationTask;
import net.minecraft.server.network.ServerConfigurationPacketListenerImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * From 0.2.0 Almanac is needed on the client as well as the server (user, 2026-11-24): a vanilla client can no
 * longer join. While a player connects, before they are in the world, the server asks the
 * client which Almanac it has, and refuses a client without one, or with one whose {@link #PROTOCOL} differs, with a
 * message naming the Almanac it needs. Guests joining a single-player world opened to LAN connect the same way and
 * are refused the same way; the host's own game always has Almanac.
 *
 * <p>Fabric only lets a server send what the client said it takes, so a client that doesn't take
 * {@link HandshakePayloads.ServerHello} (a vanilla client, or Almanac 0.1.7 or earlier) is refused at once.
 */
public final class ClientCheck {
    /**
     * Clients and servers of the same protocol work together; any other pair doesn't. It goes up whenever what the
     * two send each other changes in a way the other side can't read: 2 from 0.2.0 (0.1.x had no check), 3 from 0.3.0
     * (the season sync carries the snow's layers, the far state lost its last field, the full settings update went),
     * 4 from 0.4.0 (regional weather: the client works the rain out from the weather record, which an older client
     * doesn't take, so it would see no rain at all), 5 from 1.1.0 (version 2 of the storm maths, which an older
     * client doesn't have, and each era of the weather record carries its share of time under rain).
     */
    public static final int PROTOCOL = 5;

    private static final Logger LOGGER = LoggerFactory.getLogger(Almanac.MOD_ID);
    private static final ConfigurationTask.Type TASK = new ConfigurationTask.Type(Almanac.MOD_ID + ":client_check");
    /** What each connected player's client said, by player. */
    private static final Map<UUID, HandshakePayloads.ClientHello> CLIENTS = new ConcurrentHashMap<>();

    private ClientCheck() {
    }

    static void initialize() {
        PayloadTypeRegistry.clientboundConfiguration().register(HandshakePayloads.ServerHello.TYPE, HandshakePayloads.ServerHello.CODEC);
        PayloadTypeRegistry.serverboundConfiguration().register(HandshakePayloads.ClientHello.TYPE, HandshakePayloads.ClientHello.CODEC);
        ServerConfigurationConnectionEvents.CONFIGURE.register(ClientCheck::configure);
        ServerConfigurationNetworking.registerGlobalReceiver(HandshakePayloads.ClientHello.TYPE, ClientCheck::answered);
    }

    /** This Almanac's version, as people read it ({@code 0.2.0+26.3}). */
    public static String version() {
        return FabricLoader.getInstance().getModContainer(Almanac.MOD_ID)
                .map(almanac -> almanac.getMetadata().getVersion().getFriendlyString())
                .orElse("?");
    }

    /** The version of a LiteLights too old to share far terrain with the player's client, if it said it has one. */
    public static Optional<String> oldLiteLights(ServerPlayer player) {
        HandshakePayloads.ClientHello hello = CLIENTS.get(player.getUUID());
        return hello == null || hello.oldLiteLights().isEmpty() ? Optional.empty() : Optional.of(hello.oldLiteLights());
    }

    public static void playerLeft(ServerPlayer player) {
        CLIENTS.remove(player.getUUID());
    }

    private static void configure(ServerConfigurationPacketListenerImpl handler, MinecraftServer server) {
        if (!ServerConfigurationNetworking.canSend(handler, HandshakePayloads.ServerHello.TYPE)) {
            LOGGER.info("Almanac: refused {}, whose game has no Almanac {}", handler.getOwner().name(), version());
            handler.disconnect(noAlmanac(server));
            return;
        }
        handler.addTask(new Task());
    }

    private static void answered(HandshakePayloads.ClientHello hello, ServerConfigurationNetworking.Context context) {
        ServerConfigurationPacketListenerImpl handler = context.packetListener();
        if (hello.protocol() != PROTOCOL) {
            LOGGER.info("Almanac: refused {}, whose Almanac {} doesn't work with this Almanac {}", handler.getOwner().name(),
                    hello.version(), version());
            handler.disconnect(otherAlmanac(context.server(), hello));
            return;
        }
        CLIENTS.put(handler.getOwner().id(), hello);
        handler.completeTask(TASK);
    }

    /** What a player without Almanac reads. In English as well as by key, since their game doesn't know the key. */
    static Component noAlmanac(MinecraftServer server) {
        return Component.translatableWithFallback("almanac.refused.no_almanac",
                "%s needs Almanac %s in your game too, for its seasons. Install Almanac %s (Fabric) and join again.",
                where(server), version(), version());
    }

    /** What a player whose Almanac is too old or too new for this one reads. */
    static Component otherAlmanac(MinecraftServer server, HandshakePayloads.ClientHello hello) {
        String key = hello.protocol() < PROTOCOL ? "almanac.refused.older_almanac" : "almanac.refused.newer_almanac";
        String age = hello.protocol() < PROTOCOL ? "older" : "newer";
        return Component.translatableWithFallback(key,
                "%s runs Almanac %s, and your Almanac %s is " + age + " than that and doesn't work with it. Install Almanac %s "
                        + "and join again.", where(server), version(), hello.version(), version());
    }

    private static Component where(MinecraftServer server) {
        return server.isDedicatedServer()
                ? Component.translatableWithFallback("almanac.refused.this_server", "This server")
                : Component.translatableWithFallback("almanac.refused.this_world", "This world");
    }

    /** Asks the client which Almanac it has; the player waits in configuration until it answers. */
    private record Task() implements ConfigurationTask {
        @Override
        public void start(Consumer<Packet<?>> sender) {
            sender.accept(ServerConfigurationNetworking.createClientboundPacket(
                    new HandshakePayloads.ServerHello(version(), PROTOCOL)));
        }

        @Override
        public Type type() {
            return TASK;
        }
    }
}
