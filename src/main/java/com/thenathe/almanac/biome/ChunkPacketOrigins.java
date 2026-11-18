package com.thenathe.almanac.biome;

import com.google.common.collect.MapMaker;
import com.thenathe.almanac.Almanac;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Notices, once, a mod sending one player's chunk packet to another. Chunk packets for players without Almanac carry
 * the seasonal copies and the others the real biomes, so a mod that builds a chunk packet once and sends it to several
 * players (a chunk packet cache, say) hands some of them the wrong kind. Nothing breaks on the server, so without
 * this it would only show as wrong colours or biome names on those players' screens.
 *
 * <p>Each chunk packet Almanac sees built is remembered with whom it was built for, weakly and by identity, until
 * the first warning; after that nothing more is recorded.
 */
public final class ChunkPacketOrigins {
    private static final Logger LOGGER = LoggerFactory.getLogger(Almanac.MOD_ID);
    private static final Map<Object, Origin> BUILT = new MapMaker().weakKeys().makeMap();
    private static volatile boolean warned;

    private ChunkPacketOrigins() {
    }

    /** Who a packet was built for, and whether it carries the seasonal copies. */
    record Origin(UUID player, String name, boolean seasonal) {
    }

    public static void built(Object packet, ServerPlayer player, boolean seasonal) {
        if (!warned) {
            BUILT.put(packet, new Origin(player.getUUID(), player.getName().getString(), seasonal));
        }
    }

    /** A chunk packet is about to go to {@code recipient}, who should get the copies or not as {@code seasonal} says. */
    public static void sending(Object packet, ServerPlayer recipient, boolean seasonal) {
        if (warned) {
            return;
        }
        Origin origin = BUILT.get(packet);
        if (origin != null && mismatched(origin, recipient.getUUID(), seasonal)) {
            warned = true;
            BUILT.clear();
            LOGGER.warn("Almanac: a chunk packet built for {} ({}) was sent to {}, who should get {}. Another mod is "
                            + "sending one chunk packet to several players, so some see the wrong biomes, colours or "
                            + "weather. Only this first case is reported.", origin.name(),
                    origin.seasonal() ? "seasonal copies" : "real biomes", recipient.getName().getString(),
                    seasonal ? "seasonal copies" : "real biomes");
        }
    }

    /** Sharing a packet only matters when the two players should get different biomes. */
    static boolean mismatched(Origin origin, UUID recipient, boolean recipientSeasonal) {
        return !origin.player().equals(recipient) && origin.seasonal() != recipientSeasonal;
    }
}
