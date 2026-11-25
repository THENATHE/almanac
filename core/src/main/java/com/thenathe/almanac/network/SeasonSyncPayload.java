package com.thenathe.almanac.network;

import com.thenathe.almanac.Almanac;
import com.thenathe.almanac.api.AlmanacEvents;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SnowClocks;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.internal.SnowReading;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * S2C: the season clock, which climates the current rain event reaches, and the snow and ice in each climate. Sent on
 * joining and whenever any of it changes.
 *
 * <p>From 0.3.0 (format 3) the snow goes as each climate's depth and ice cover at a game tick, with how fast each is
 * moving, and the clocks ({@link SnowReading}); a client follows them forward to its own game time ({@link #snow}),
 * so its layer shares move as smoothly as the server's. The sync goes again whenever the pace changes (the weather
 * changed), and when a depth or an ice cover has moved a sixteenth since the last one, which keeps
 * {@link AlmanacEvents.Change#snowDepth} firing as it always has. Until 0.2.0 the depths went in sixteenths of a layer
 * and nothing else; a client of another protocol can't join ({@link ClientCheck}), so the layout changed in place.
 *
 * @param snowTick the overworld game tick {@code snow} was read at
 */
public record SeasonSyncPayload(SubSeason subSeason, int dayOfYear, int daysPerSeason, boolean paused,
                                Set<Climate> reached, long rainEvent, long snowTick, SnowReading snow)
        implements CustomPacketPayload {
    private static final int FORMAT = 3;
    /** The steps a depth or an ice cover is sent again for, and counted as changed in: sixteenths. */
    private static final double STEPS = 16.0;

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
                buffer.writeVarLong(payload.snowTick());
                SnowClocks clocks = payload.snow().clocks();
                buffer.writeVarInt(clocks.snowfallTicks());
                buffer.writeVarInt(clocks.thawTicks());
                buffer.writeVarInt(clocks.iceTicks());
                buffer.writeVarInt(clocks.maxLayers());
                for (Climate climate : Climate.values()) {
                    buffer.writeDouble(payload.snow().depth(climate));
                    buffer.writeDouble(payload.snow().depthPerTick().get(climate));
                    buffer.writeDouble(payload.snow().ice(climate));
                    buffer.writeDouble(payload.snow().icePerTick().get(climate));
                }
            },
            buffer -> {
                buffer.readVarInt();
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
                long snowTick = buffer.readVarLong();
                SnowClocks clocks = new SnowClocks(buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                        buffer.readVarInt());
                Map<Climate, Double> depths = new EnumMap<>(Climate.class);
                Map<Climate, Double> depthPace = new EnumMap<>(Climate.class);
                Map<Climate, Double> ice = new EnumMap<>(Climate.class);
                Map<Climate, Double> icePace = new EnumMap<>(Climate.class);
                for (Climate climate : Climate.values()) {
                    depths.put(climate, buffer.readDouble());
                    depthPace.put(climate, buffer.readDouble());
                    ice.put(climate, buffer.readDouble());
                    icePace.put(climate, buffer.readDouble());
                }
                PayloadFormat.skipNewerFields(buffer);
                return new SeasonSyncPayload(subSeason, dayOfYear, daysPerSeason, paused, reached, rainEvent, snowTick,
                        new SnowReading(depths, ice, depthPace, icePace, clocks));
            });

    public SeasonSyncPayload {
        reached = Set.copyOf(reached);
    }

    /**
     * Whether this sync says nothing new against {@code previous}: the same season, the same pace and clocks for the
     * snow, and no depth or ice cover a sixteenth further on. The server sends a sync only when it isn't.
     */
    public boolean sameAs(@Nullable SeasonSyncPayload previous) {
        return previous != null && previous.subSeason == subSeason && previous.dayOfYear == dayOfYear
                && previous.daysPerSeason == daysPerSeason && previous.paused == paused
                && previous.reached.equals(reached) && previous.rainEvent == rainEvent
                && previous.snow.clocks().equals(snow.clocks())
                && previous.snow.depthPerTick().equals(snow.depthPerTick())
                && previous.snow.icePerTick().equals(snow.icePerTick())
                && sameSteps(previous);
    }

    private boolean sameSteps(SeasonSyncPayload previous) {
        for (Climate climate : Climate.values()) {
            if (steps(previous.snow.depth(climate)) != steps(snow.depth(climate))
                    || steps(previous.snow.ice(climate)) != steps(snow.ice(climate))) {
                return false;
            }
        }
        return true;
    }

    private static long steps(double amount) {
        return Math.round(amount * STEPS);
    }

    /** What differs from the previous sync; everything, when there was none. */
    public AlmanacEvents.Change changeFrom(@Nullable SeasonSyncPayload previous) {
        if (previous == null) {
            return new AlmanacEvents.Change(true, true, true);
        }
        boolean snowChanged = !sameSteps(previous) || previous.snow.clocks().maxLayers() != snow.clocks().maxLayers();
        return new AlmanacEvents.Change(previous.subSeason != subSeason, !previous.reached.equals(reached), snowChanged);
    }

    /** The snow and ice at {@code gameTime}, followed forward from when this sync was read. */
    public SnowReading snow(long gameTime) {
        return snow.after(gameTime - snowTick);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SeasonSyncPayload payload && payload.snowTick == snowTick && payload.sameAs(this)
                && payload.snow.equals(snow);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subSeason, dayOfYear, reached, rainEvent, snowTick);
    }

    @Override
    public Type<SeasonSyncPayload> type() {
        return TYPE;
    }
}
