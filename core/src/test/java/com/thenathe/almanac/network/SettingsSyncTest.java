package com.thenathe.almanac.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.config.ServerSetting;
import com.thenathe.almanac.internal.WeatherRecord;
import io.netty.buffer.Unpooled;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

/**
 * The server's settings reach the client whole, every one of them, and an operator's changes reach the server whole;
 * the server takes changes only from a player its own check lets change the settings.
 */
class SettingsSyncTest {
    private static <T> T roundTrip(T payload, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        RegistryFriendlyByteBuf buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        codec.encode(buffer, payload);
        T read = codec.decode(buffer);
        assertEquals(0, buffer.readableBytes());
        return read;
    }

    /** Settings with every one changed from its default, to a value inside its range. */
    private static ServerConfig everySettingChanged() {
        ServerConfig config = new ServerConfig();
        config.daysPerSeason = 31;
        config.startSubSeason = SubSeason.LATE_FALL.id();
        config.paused = true;
        config.announceSubSeasons = false;
        config.regionalWeather = false;
        config.weatherCommandEverywhere = true;
        config.timeUnderRain = 37;
        for (var climate : com.thenathe.almanac.api.Climate.values()) {
            config.shares(climate).replaceAll((subSeason, share) -> share == 45 ? 55 : 45);
        }
        config.snowLineAboveSea = 70;
        config.winterTemperature = -0.35;
        config.summerTemperature = 0.9;
        config.snowfallTicks = 6_000;
        config.thawTicks = 9_000;
        config.iceTicks = 4_000;
        config.maxSnowLayers = 5;
        config.nativeSnowMeltsInSummer = false;
        config.snowCatchUp = false;
        config.catchUpChunksPerTick = 9;
        config.snowColumnsPerTick = 300;
        config.farClimateMap = false;
        config.farClimateRadius = 2_048;
        config.farClimateCellSize = 64;
        config.farClimateBytesPerTick = 8_192;
        return config.validated();
    }

    @Test
    void theTestChangesEverySetting() {
        ServerConfig defaults = new ServerConfig();
        ServerConfig changed = everySettingChanged();
        for (ServerSetting setting : ServerSetting.all()) {
            assertNotEquals(setting.get(defaults), setting.get(changed), setting.name() + " is changed by the test");
        }
    }

    @Test
    void everySettingReachesTheClientInTheSnapshot() {
        ServerConfig server = everySettingChanged();
        SettingsPayloads.Snapshot read = roundTrip(new SettingsPayloads.Snapshot(false, server.toJson()),
                SettingsPayloads.Snapshot.CODEC);
        ServerConfig onTheClient = ServerConfig.fromJson(read.json());
        for (ServerSetting setting : ServerSetting.all()) {
            assertEquals(setting.get(server), setting.get(onTheClient), setting.name());
        }
        assertEquals(false, read.canEdit());
    }

    @Test
    void everySettingAnOperatorChangesReachesTheServer() {
        ServerConfig before = new ServerConfig().validated();
        ServerConfig after = everySettingChanged();
        SettingsPayloads.Change sent = roundTrip(new SettingsPayloads.Change(ServerConfig.changes(before, after).toString()),
                SettingsPayloads.Change.CODEC);
        SettingsChange change = SettingsChange.judge(true, before, sent.json());
        assertEquals(SettingsChange.Outcome.APPLIED, change.outcome());
        for (ServerSetting setting : ServerSetting.all()) {
            assertEquals(setting.get(after), setting.get(change.settings()), setting.name());
        }
    }

    @Test
    void theServerRefusesChangesFromAPlayerWhoMayNotChangeThem() {
        ServerConfig current = new ServerConfig().validated();
        String changes = ServerConfig.changes(current, everySettingChanged()).toString();
        SettingsChange change = SettingsChange.judge(false, current, changes);
        assertEquals(SettingsChange.Outcome.DENIED, change.outcome());
        assertNull(change.settings());
        // Refused whatever the client sends, even nothing at all or what isn't settings.
        assertEquals(SettingsChange.Outcome.DENIED, SettingsChange.judge(false, current, "{}").outcome());
        assertEquals(SettingsChange.Outcome.DENIED, SettingsChange.judge(false, current, "not json").outcome());
    }

    @Test
    void anOperatorsChangeAppliesOnTopOfTheServersLatestAndIsChecked() {
        ServerConfig latest = new ServerConfig().validated();
        latest.daysPerSeason = 40;
        SettingsChange change = SettingsChange.judge(true, latest, "{\"timeUnderRain\": 99, \"paused\": true}");
        assertEquals(SettingsChange.Outcome.APPLIED, change.outcome());
        assertEquals(40, change.settings().daysPerSeason, "what someone else changed meanwhile stays");
        assertEquals(ServerConfig.MAX_TIME_UNDER_RAIN, change.settings().timeUnderRain, "kept in range");
        assertEquals(true, change.settings().paused);
        assertEquals(SettingsChange.Outcome.UNREADABLE, SettingsChange.judge(true, latest, "[1, 2]").outcome());
    }

    @Test
    void theWeatherRecordCarriesEachErasShareOfTimeUnderRain() {
        WeatherRecord record = new WeatherRecord(42L, List.of(
                new WeatherRecord.Era(0, SubSeason.MID_SPRING, 1, 100, 100, 100, 0),
                new WeatherRecord.Era(48_000, SubSeason.MID_SPRING, 2, 100, 80, 100, 25),
                new WeatherRecord.Era(96_000, SubSeason.LATE_SPRING, 2, 100, 80, 100, 40)),
                List.of(new WeatherRecord.Event(WeatherRecord.EventKind.STORM, 10, 20, 1.5, -2.5, 0.0)),
                List.of(new WeatherRecord.Freeze(30, WeatherRecord.Freeze.OPEN)));
        assertEquals(record, roundTrip(new WeatherSyncPayload(record), WeatherSyncPayload.CODEC).record());
    }

    @Test
    void copyFromTakesEverySetting() {
        ServerConfig shown = new ServerConfig().validated();
        ServerConfig sent = everySettingChanged();
        shown.copyFrom(sent);
        for (ServerSetting setting : ServerSetting.all()) {
            assertEquals(setting.get(sent), setting.get(shown), setting.name());
        }
        sent.temperateShare.put(SubSeason.MID_WINTER.id(), 5);
        assertNotEquals(5, shown.share(com.thenathe.almanac.api.Climate.TEMPERATE, SubSeason.MID_WINTER),
                "a copy, not the same map");
    }
}
