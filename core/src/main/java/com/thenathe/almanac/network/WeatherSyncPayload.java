package com.thenathe.almanac.network;

import com.thenathe.almanac.Almanac;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.WeatherRecord;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C, from 0.4.0: the overworld's weather record ({@link WeatherRecord}), so the client works out the same storms as
 * the server from the same weather seed and the game time. The weather seed is a one-way hash of the world seed;
 * the world seed itself never goes to clients. Sent on joining and again whenever the record changes (a new
 * sub-season, a share changed, an operator's {@code /weather}, the weather standing still or moving again), never
 * each tick. A few hundred bytes for a world a few years old.
 */
public record WeatherSyncPayload(WeatherRecord record) implements CustomPacketPayload {
    private static final int FORMAT = 1;
    /** More than any record holds; a broken or hostile server can't make the client allocate without end. */
    private static final int MOST_ENTRIES = 65_536;

    public static final Type<WeatherSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "weather"));
    public static final StreamCodec<RegistryFriendlyByteBuf, WeatherSyncPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                WeatherRecord record = payload.record();
                buffer.writeVarInt(FORMAT);
                buffer.writeLong(record.seed());
                buffer.writeVarInt(record.eras().size());
                for (WeatherRecord.Era era : record.eras()) {
                    buffer.writeLong(era.start());
                    buffer.writeVarInt(era.subSeason().ordinal());
                    buffer.writeVarInt(era.version());
                    buffer.writeVarInt(era.hotShare());
                    buffer.writeVarInt(era.temperateShare());
                    buffer.writeVarInt(era.coldShare());
                }
                buffer.writeVarInt(record.events().size());
                for (WeatherRecord.Event event : record.events()) {
                    buffer.writeVarInt(event.kind().ordinal());
                    buffer.writeLong(event.start());
                    buffer.writeLong(event.end());
                    buffer.writeDouble(event.x());
                    buffer.writeDouble(event.z());
                    buffer.writeDouble(event.radius());
                }
                buffer.writeVarInt(record.freezes().size());
                for (WeatherRecord.Freeze freeze : record.freezes()) {
                    buffer.writeLong(freeze.start());
                    buffer.writeLong(freeze.end());
                }
            },
            buffer -> {
                buffer.readVarInt();
                long seed = buffer.readLong();
                List<WeatherRecord.Era> eras = new ArrayList<>();
                int eraCount = count(buffer);
                for (int index = 0; index < eraCount; index++) {
                    eras.add(new WeatherRecord.Era(buffer.readLong(), SubSeason.byIndex(buffer.readVarInt()),
                            buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()));
                }
                List<WeatherRecord.Event> events = new ArrayList<>();
                int eventCount = count(buffer);
                WeatherRecord.EventKind[] kinds = WeatherRecord.EventKind.values();
                for (int index = 0; index < eventCount; index++) {
                    WeatherRecord.EventKind kind = kinds[Math.floorMod(buffer.readVarInt(), kinds.length)];
                    events.add(new WeatherRecord.Event(kind, buffer.readLong(), buffer.readLong(), buffer.readDouble(),
                            buffer.readDouble(), buffer.readDouble()));
                }
                List<WeatherRecord.Freeze> freezes = new ArrayList<>();
                int freezeCount = count(buffer);
                for (int index = 0; index < freezeCount; index++) {
                    freezes.add(new WeatherRecord.Freeze(buffer.readLong(), buffer.readLong()));
                }
                PayloadFormat.skipNewerFields(buffer);
                return new WeatherSyncPayload(new WeatherRecord(seed, eras, events, freezes));
            });

    private static int count(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > MOST_ENTRIES) {
            throw new IllegalArgumentException("a weather record with " + count + " entries");
        }
        return count;
    }

    @Override
    public Type<WeatherSyncPayload> type() {
        return TYPE;
    }
}
