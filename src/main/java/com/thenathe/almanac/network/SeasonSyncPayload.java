package com.thenathe.almanac.network;

import com.thenathe.almanac.Almanac;
import com.thenathe.almanac.api.AlmanacEvents;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.config.ServerConfig;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * S2C: the season clock, which climates the current rain event reaches and the snow depth in each climate. Sent on
 * joining and whenever any of it changes. Its channel is also how the server tells a client with Almanac from one
 * without.
 *
 * <p>Snow depths travel in sixteenths of a layer, so the payload only changes every few hundred ticks while it snows.
 */
public record SeasonSyncPayload(SubSeason subSeason, int dayOfYear, int daysPerSeason, boolean paused,
                                Set<Climate> reached, long rainEvent, Map<Climate, Integer> snowSixteenths)
        implements CustomPacketPayload {
    private static final int FORMAT = 2;

    public static final Type<SeasonSyncPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "season"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SeasonSyncPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeVarInt(FORMAT);
                buffer.writeVarInt(payload.subSeason().ordinal());
                buffer.writeVarInt(payload.dayOfYear());
                buffer.writeVarInt(payload.daysPerSeason());
                buffer.writeBoolean(payload.paused());
                int mask = 0;
                for (Climate climate : payload.reached()) {
                    mask |= 1 << climate.ordinal();
                }
                buffer.writeVarInt(mask);
                buffer.writeVarLong(payload.rainEvent());
                for (Climate climate : Climate.values()) {
                    buffer.writeVarInt(payload.snowSixteenths().getOrDefault(climate, 0));
                }
            },
            buffer -> {
                int format = buffer.readVarInt();
                SubSeason subSeason = SubSeason.byIndex(buffer.readVarInt());
                int dayOfYear = buffer.readVarInt();
                // A broken server could send 0, which the client divides by.
                int daysPerSeason = Math.clamp(buffer.readVarInt(), ServerConfig.MIN_DAYS_PER_SEASON,
                        ServerConfig.MAX_DAYS_PER_SEASON);
                boolean paused = buffer.readBoolean();
                int mask = buffer.readVarInt();
                Set<Climate> reached = EnumSet.noneOf(Climate.class);
                for (Climate climate : Climate.values()) {
                    if ((mask & 1 << climate.ordinal()) != 0) {
                        reached.add(climate);
                    }
                }
                long rainEvent = buffer.readVarLong();
                Map<Climate, Integer> snow = new EnumMap<>(Climate.class);
                if (format >= 2) {
                    for (Climate climate : Climate.values()) {
                        snow.put(climate, buffer.readVarInt());
                    }
                }
                PayloadFormat.skipNewerFields(buffer);
                return new SeasonSyncPayload(subSeason, dayOfYear, daysPerSeason, paused, reached, rainEvent, snow);
            });

    public SeasonSyncPayload {
        reached = Set.copyOf(reached);
        snowSixteenths = Map.copyOf(snowSixteenths);
    }

    public static int sixteenths(double layers) {
        return (int) Math.round(layers * 16.0);
    }

    /** What differs from the previous sync; everything, when there was none. */
    public AlmanacEvents.Change changeFrom(@Nullable SeasonSyncPayload previous) {
        if (previous == null) {
            return new AlmanacEvents.Change(true, true, true);
        }
        return new AlmanacEvents.Change(previous.subSeason != subSeason, !previous.reached.equals(reached),
                !previous.snowSixteenths.equals(snowSixteenths));
    }

    /** The snow depth in a climate, in layers. */
    public double snowDepth(Climate climate) {
        return snowSixteenths.getOrDefault(climate, 0) / 16.0;
    }

    @Override
    public Type<SeasonSyncPayload> type() {
        return TYPE;
    }
}
