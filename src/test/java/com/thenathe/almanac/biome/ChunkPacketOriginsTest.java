package com.thenathe.almanac.biome;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Only a chunk packet that reaches a player who should get other biomes counts as reused wrongly (R5). */
class ChunkPacketOriginsTest {
    @Test
    void sharingMattersOnlyBetweenTheTwoKindsOfPlayer() {
        UUID builtFor = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        ChunkPacketOrigins.Origin vanilla = new ChunkPacketOrigins.Origin(builtFor, "Alex", true);
        assertFalse(ChunkPacketOrigins.mismatched(vanilla, builtFor, true), "its own player");
        assertFalse(ChunkPacketOrigins.mismatched(vanilla, other, true), "another player without Almanac");
        assertTrue(ChunkPacketOrigins.mismatched(vanilla, other, false), "a player with Almanac gets copies");
    }
}
