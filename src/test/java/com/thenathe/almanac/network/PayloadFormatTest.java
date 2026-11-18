package com.thenathe.almanac.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.config.ServerConfig;
import io.netty.buffer.Unpooled;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

/** Every payload reads what it knows and skips fields a newer Almanac added after them (R3). */
class PayloadFormatTest {
    /** Writes the payload, adds bytes a newer version might have appended, and reads it back. */
    private static <T> T withNewerFields(T payload, StreamCodec<RegistryFriendlyByteBuf, T> codec) {
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
                Map.of(Climate.HOT, 0, Climate.TEMPERATE, 20, Climate.COLD, 32));
        assertEquals(sync, withNewerFields(sync, SeasonSyncPayload.CODEC));

        WorldWeatherPayload weather = new WorldWeatherPayload(0.5F, 0.25F);
        assertEquals(weather, withNewerFields(weather, WorldWeatherPayload.CODEC));

        SettingsPayloads.Snapshot snapshot = new SettingsPayloads.Snapshot(true, "{\"paused\": true}");
        assertEquals(snapshot, withNewerFields(snapshot, SettingsPayloads.Snapshot.CODEC));
        SettingsPayloads.Update update = new SettingsPayloads.Update("{}");
        assertEquals(update, withNewerFields(update, SettingsPayloads.Update.CODEC));
        SettingsPayloads.Change change = new SettingsPayloads.Change("{\"daysPerSeason\": 30}");
        assertEquals(change, withNewerFields(change, SettingsPayloads.Change.CODEC));

        FarClimatePayloads.State state = new FarClimatePayloads.State(true, SubSeason.LATE_WINTER, new int[] {0, 1, 2},
                true, 1, 48_000);
        assertEquals(state, withNewerFields(state, FarClimatePayloads.State.CODEC));
        FarClimatePayloads.Forget forget = new FarClimatePayloads.Forget(false, new long[] {3L, -7L});
        assertArrayEquals(forget.tiles(), withNewerFields(forget, FarClimatePayloads.Forget.CODEC).tiles());
        FarClimatePayloads.SnowChanged changed = new FarClimatePayloads.SnowChanged(1_234_567L);
        assertEquals(changed, withNewerFields(changed, FarClimatePayloads.SnowChanged.CODEC));
    }

    @Test
    void aSeasonLengthOfNothingIsReadAsTheShortestSeason() {
        // A broken server sending 0 days per season would make the client divide by zero.
        SeasonSyncPayload broken = new SeasonSyncPayload(SubSeason.EARLY_SPRING, 5, 0, false, Set.of(), 0, Map.of());
        assertEquals(ServerConfig.MIN_DAYS_PER_SEASON, withNewerFields(broken, SeasonSyncPayload.CODEC).daysPerSeason());
        SeasonSyncPayload huge = new SeasonSyncPayload(SubSeason.EARLY_SPRING, 5, Integer.MAX_VALUE, false, Set.of(), 0, Map.of());
        assertEquals(ServerConfig.MAX_DAYS_PER_SEASON, withNewerFields(huge, SeasonSyncPayload.CODEC).daysPerSeason());
    }

    @Test
    void theFarSnowFingerprintIsFixed() {
        FarClimatePayloads.State winter = new FarClimatePayloads.State(true, SubSeason.MID_WINTER, new int[] {0, 1, 1},
                true, 1, 48_000);
        // Saved in season.dat to tell a restart from a change, so the formula must give the same number on every run.
        assertEquals(1194479477, winter.farSnowFingerprint());
        FarClimatePayloads.State longerThaw = new FarClimatePayloads.State(true, SubSeason.MID_WINTER, new int[] {0, 1, 1},
                true, 1, 96_000);
        assertEquals(winter.farSnowFingerprint(), longerThaw.farSnowFingerprint(), "the thaw time isn't far snow");
        FarClimatePayloads.State moreSnow = new FarClimatePayloads.State(true, SubSeason.MID_WINTER, new int[] {0, 2, 1},
                true, 1, 48_000);
        assertNotEquals(winter.farSnowFingerprint(), moreSnow.farSnowFingerprint());
    }

    @Test
    void theFarRuleOnlyCountsWhetherAClimateHasSnow() {
        FarClimatePayloads.State winter = new FarClimatePayloads.State(true, SubSeason.MID_WINTER, new int[] {0, 1, 1},
                true, 1, 48_000);
        assertEquals(1194479477, winter.farRuleFingerprint());
        FarClimatePayloads.State deeper = new FarClimatePayloads.State(true, SubSeason.MID_WINTER, new int[] {0, 3, 2},
                true, 1, 48_000);
        assertTrue(deeper.sameFarRule(winter), "two layers or three, the far rule shows the same");
        assertFalse(deeper.sameFarSnow(winter), "but the state and its event still change");
        assertEquals(winter.farRuleFingerprint(), deeper.farRuleFingerprint());
        FarClimatePayloads.State melted = new FarClimatePayloads.State(true, SubSeason.MID_WINTER, new int[] {0, 0, 1},
                true, 1, 48_000);
        assertFalse(melted.sameFarRule(winter), "a climate losing its snow is a far change");
        assertNotEquals(winter.farRuleFingerprint(), melted.farRuleFingerprint());
        FarClimatePayloads.State nextSeason = new FarClimatePayloads.State(true, SubSeason.LATE_WINTER, new int[] {0, 1, 1},
                true, 1, 48_000);
        assertFalse(nextSeason.sameFarRule(winter));
        assertFalse(winter.sameFarRule(null));
    }
}
