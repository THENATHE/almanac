package com.thenathe.almanac.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.climate.ClimateTable;
import com.thenathe.almanac.config.ServerConfig;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

/** Every payload reads what it knows and skips fields a newer Almanac added after them (R3). */
class PayloadFormatTest {
    /** Writes the payload, adds bytes a newer version might have appended, and reads it back. */
    private static <T> T withNewerFields(T payload, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        codec.encode(buffer, payload);
        buffer.writeVarInt(12345);
        buffer.writeUtf("a field from the future");
        T read = codec.decode(buffer);
        assertEquals(0, buffer.readableBytes(), "nothing left over for Minecraft to refuse");
        return read;
    }

    @Test
    void everyPayloadSkipsNewerFields() {
        SeasonSyncPayload sync = new SeasonSyncPayload(SubSeason.MID_WINTER, 130, 24, false, Set.of(Climate.COLD), 9,
                40_000L, SeasonSyncPayloadTest.snow(1.25, 1.0 / 12_000, 0.75));
        assertEquals(sync, withNewerFields(sync, SeasonSyncPayload.CODEC));

        HandshakePayloads.ServerHello serverHello = new HandshakePayloads.ServerHello("0.2.0+26.3", ClientCheck.PROTOCOL);
        assertEquals(serverHello, withNewerFields(serverHello, HandshakePayloads.ServerHello.CODEC));
        HandshakePayloads.ClientHello clientHello = new HandshakePayloads.ClientHello("0.2.0+26.3", ClientCheck.PROTOCOL, "1.0.6+26.3");
        assertEquals(clientHello, withNewerFields(clientHello, HandshakePayloads.ClientHello.CODEC));

        SettingsPayloads.Snapshot snapshot = new SettingsPayloads.Snapshot(true, "{\"paused\": true}");
        assertEquals(snapshot, withNewerFields(snapshot, SettingsPayloads.Snapshot.CODEC));
        SettingsPayloads.Change change = new SettingsPayloads.Change("{\"daysPerSeason\": 30}");
        assertEquals(change, withNewerFields(change, SettingsPayloads.Change.CODEC));

        FarClimatePayloads.State state = new FarClimatePayloads.State(true, SubSeason.LATE_WINTER, new int[] {0, 1, 2},
                true, 1);
        assertEquals(state, withNewerFields(state, FarClimatePayloads.State.CODEC));
        FarClimatePayloads.Forget forget = new FarClimatePayloads.Forget(false, new long[] {3L, -7L});
        assertArrayEquals(forget.tiles(), withNewerFields(forget, FarClimatePayloads.Forget.CODEC).tiles());
        FarClimatePayloads.SnowChanged changed = new FarClimatePayloads.SnowChanged(1_234_567L);
        assertEquals(changed, withNewerFields(changed, FarClimatePayloads.SnowChanged.CODEC));
    }

    private static <T> byte[] encoded(T payload, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        codec.encode(buffer, payload);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }

    @Test
    void theFarSnowPayloadsKeepTheirFieldsButTheRetiredThawTime() {
        // 0.1.3.1's fields; the thaw time that ended the state up to 0.1.6, and its 0 until 0.2.0, went in 0.3.0.
        FarClimatePayloads.State state = new FarClimatePayloads.State(true, SubSeason.MID_WINTER, new int[] {0, 1, 1},
                true, 1);
        assertArrayEquals(new byte[] {1, 1, 10, 0, 1, 1, 1, 1}, encoded(state, FarClimatePayloads.State.CODEC));
        assertArrayEquals(new byte[] {1, (byte) 0x87, (byte) 0xAD, 0x4B},
                encoded(new FarClimatePayloads.SnowChanged(1_234_567L), FarClimatePayloads.SnowChanged.CODEC));
    }

    @Test
    void aStateFromA016ServerIsReadWithItsThawTimeSkipped() {
        byte[] from016 = {1, 1, 10, 0, 1, 1, 1, 1, (byte) 0x80, (byte) 0xF7, 2};
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(from016), RegistryAccess.EMPTY);
        FarClimatePayloads.State read = FarClimatePayloads.State.CODEC.decode(buffer);
        assertEquals(0, buffer.readableBytes());
        assertEquals(new FarClimatePayloads.State(true, SubSeason.MID_WINTER, new int[] {0, 1, 1}, true, 1), read);
    }

    @Test
    void theClimateTableComesBackWhole() {
        List<ClimateTable.Cell> cells = new ArrayList<>();
        for (SubSeason subSeason : SubSeason.values()) {
            cells.add(new ClimateTable.Cell(subSeason != SubSeason.MID_SUMMER, subSeason.ordinal() / 10.0F, subSeason.ordinal() % 2 == 0));
        }
        ClimateTablePayload table = new ClimateTablePayload(List.of(
                new ClimateTablePayload.Row(7, Climate.TEMPERATE, false, true, true, cells),
                new ClimateTablePayload.Row(300, Climate.COLD, true, false, false, cells)));
        assertEquals(table, withNewerFields(table, ClimateTablePayload.CODEC));
    }

    @Test
    void aClientOfAnotherProtocolIsReadForItsVersionAlone() {
        // What follows the protocol may be laid out differently there; the client is refused for its version.
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        buffer.writeUtf("0.3.0+26.4");
        buffer.writeVarInt(ClientCheck.PROTOCOL + 1);
        buffer.writeVarLong(Long.MAX_VALUE);
        HandshakePayloads.ClientHello read = HandshakePayloads.ClientHello.CODEC.decode(buffer);
        assertEquals(new HandshakePayloads.ClientHello("0.3.0+26.4", ClientCheck.PROTOCOL + 1, ""), read);
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    void aSeasonLengthOfNothingIsReadAsTheShortestSeason() {
        // A broken server sending 0 days per season would make the client divide by zero.
        SeasonSyncPayload broken = new SeasonSyncPayload(SubSeason.EARLY_SPRING, 5, 0, false, Set.of(), 0, 0L,
                SeasonSyncPayloadTest.snow(0.0, 0.0, 0.0));
        assertEquals(ServerConfig.MIN_DAYS_PER_SEASON, withNewerFields(broken, SeasonSyncPayload.CODEC).daysPerSeason());
        SeasonSyncPayload huge = new SeasonSyncPayload(SubSeason.EARLY_SPRING, 5, Integer.MAX_VALUE, false, Set.of(), 0,
                0L, SeasonSyncPayloadTest.snow(0.0, 0.0, 0.0));
        assertEquals(ServerConfig.MAX_DAYS_PER_SEASON, withNewerFields(huge, SeasonSyncPayload.CODEC).daysPerSeason());
    }

    @Test
    void theFarSnowFingerprintIsFixed() {
        FarClimatePayloads.State winter = new FarClimatePayloads.State(true, SubSeason.MID_WINTER, new int[] {0, 1, 1},
                true, 1);
        // Saved in season.dat to tell a restart from a change, so the formula must give the same number on every run.
        assertEquals(1194479477, winter.farSnowFingerprint());
        FarClimatePayloads.State moreSnow = new FarClimatePayloads.State(true, SubSeason.MID_WINTER, new int[] {0, 2, 1},
                true, 1);
        assertNotEquals(winter.farSnowFingerprint(), moreSnow.farSnowFingerprint());
    }

    @Test
    void theFarRuleOnlyCountsWhetherAClimateHasSnow() {
        FarClimatePayloads.State winter = new FarClimatePayloads.State(true, SubSeason.MID_WINTER, new int[] {0, 1, 1},
                true, 1);
        assertEquals(1194479477, winter.farRuleFingerprint());
        FarClimatePayloads.State deeper = new FarClimatePayloads.State(true, SubSeason.MID_WINTER, new int[] {0, 3, 2},
                true, 1);
        assertTrue(deeper.sameFarRule(winter), "two layers or three, the far rule shows the same");
        assertFalse(deeper.sameFarSnow(winter), "but the state and its event still change");
        assertEquals(winter.farRuleFingerprint(), deeper.farRuleFingerprint());
        FarClimatePayloads.State melted = new FarClimatePayloads.State(true, SubSeason.MID_WINTER, new int[] {0, 0, 1},
                true, 1);
        assertFalse(melted.sameFarRule(winter), "a climate losing its snow is a far change");
        assertNotEquals(winter.farRuleFingerprint(), melted.farRuleFingerprint());
        FarClimatePayloads.State nextSeason = new FarClimatePayloads.State(true, SubSeason.LATE_WINTER, new int[] {0, 1, 1},
                true, 1);
        assertFalse(nextSeason.sameFarRule(winter));
        assertFalse(winter.sameFarRule(null));
    }
}
