package com.thenathe.almanac.network;

import com.thenathe.almanac.Almanac;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.climatemap.ClimateTile;
import java.util.Arrays;
import java.util.Objects;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * The climate map for far terrain (docs/FAR-SNOW.md), sent only to clients with Almanac 0.1.2 or later, which
 * register these channels. Everything is server to client.
 */
public final class FarClimatePayloads {
    private static final int FORMAT = 1;
    private static final int MAX_PACKED_BYTES = 1 << 20;
    private static final int MAX_FORGOTTEN = 1 << 16;

    private FarClimatePayloads() {
    }

    /**
     * What far terrain shows now ({@link com.thenathe.almanac.snow.FarSnowRule#shown}), and whether the climate map
     * is on at all. Sent on joining and whenever it changes; turning the map off tells the client to drop its tiles.
     *
     * @param layers    the whole layers of snow in each climate, by {@link Climate} ordinal
     * @param thawTicks how long a thaw in loaded chunks takes right now, in game ticks (not part of the far rule)
     */
    public record State(boolean enabled, SubSeason shownSubSeason, int[] layers, boolean nativeSnowMelts, int naturalHeight,
                        long thawTicks) implements CustomPacketPayload {
        public static final Type<State> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "far_climate_state"));
        public static final StreamCodec<RegistryFriendlyByteBuf, State> CODEC = StreamCodec.of(
                (buffer, state) -> {
                    buffer.writeVarInt(FORMAT);
                    buffer.writeBoolean(state.enabled());
                    buffer.writeVarInt(state.shownSubSeason().ordinal());
                    for (Climate climate : Climate.values()) {
                        buffer.writeVarInt(state.layers(climate));
                    }
                    buffer.writeBoolean(state.nativeSnowMelts());
                    buffer.writeVarInt(state.naturalHeight());
                    buffer.writeVarLong(state.thawTicks());
                },
                buffer -> {
                    buffer.readVarInt();
                    boolean enabled = buffer.readBoolean();
                    SubSeason subSeason = SubSeason.byIndex(buffer.readVarInt());
                    int[] layers = new int[Climate.values().length];
                    for (int climate = 0; climate < layers.length; climate++) {
                        layers[climate] = buffer.readVarInt();
                    }
                    boolean nativeSnowMelts = buffer.readBoolean();
                    int naturalHeight = buffer.readVarInt();
                    long thawTicks = Math.max(0L, buffer.readVarLong());
                    PayloadFormat.skipNewerFields(buffer);
                    return new State(enabled, subSeason, layers, nativeSnowMelts, naturalHeight, thawTicks);
                });

        public State {
            layers = Arrays.copyOf(layers, Climate.values().length);
        }

        public int layers(Climate climate) {
            return Math.clamp(layers[climate.ordinal()], 0, 8);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof State state && sameFarSnow(state) && thawTicks == state.thawTicks;
        }

        /** Whether the far rule gives the same answers under both: everything but the thaw time. */
        public boolean sameFarSnow(@Nullable State other) {
            return other != null && enabled == other.enabled && shownSubSeason == other.shownSubSeason
                    && Arrays.equals(layers, other.layers) && nativeSnowMelts == other.nativeSnowMelts
                    && naturalHeight == other.naturalHeight;
        }

        @Override
        public int hashCode() {
            return 31 * shownSubSeason.hashCode() + Arrays.hashCode(layers);
        }

        /**
         * Whether the far rule gives the same answers under both, for {@code farSnowChangedTick}. Narrower than
         * {@link #sameFarSnow}: the rule only asks whether a climate has any snow ({@link com.thenathe.almanac.snow.FarSnowRule#snowLies}), so
         * snow going from two layers to one changes nothing far terrain shows.
         */
        public boolean sameFarRule(@Nullable State other) {
            return other != null && enabled == other.enabled && shownSubSeason == other.shownSubSeason
                    && Arrays.equals(anySnow(), other.anySnow()) && nativeSnowMelts == other.nativeSnowMelts
                    && naturalHeight == other.naturalHeight;
        }

        /**
         * A number for what {@link #sameFarRule} compares, the same on every run (unlike {@link #hashCode}), so it can
         * be saved to tell whether far snow changed while the server was off.
         */
        public int farRuleFingerprint() {
            return Objects.hash(enabled, shownSubSeason.ordinal(), Arrays.hashCode(anySnow()), nativeSnowMelts, naturalHeight);
        }

        /** 0.1.3's fingerprint, of what {@link #sameFarSnow} compares, to read the tick a 0.1.3 world saved. */
        public int farSnowFingerprint() {
            return Objects.hash(enabled, shownSubSeason.ordinal(), Arrays.hashCode(layers), nativeSnowMelts, naturalHeight);
        }

        private int[] anySnow() {
            int[] any = new int[layers.length];
            for (int climate = 0; climate < any.length; climate++) {
                any[climate] = layers[climate] > 0 ? 1 : 0;
            }
            return any;
        }

        @Override
        public Type<State> type() {
            return TYPE;
        }
    }

    /** One tile of the climate map: its palette of biome network IDs and its packed, compressed cells. */
    public record Tile(int tileX, int tileZ, int cellShift, int[] palette, byte[] packedCells) implements CustomPacketPayload {
        public static final Type<Tile> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "far_climate_tile"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Tile> CODEC = StreamCodec.of(
                (buffer, tile) -> {
                    buffer.writeVarInt(FORMAT);
                    buffer.writeInt(tile.tileX());
                    buffer.writeInt(tile.tileZ());
                    buffer.writeByte(tile.cellShift());
                    buffer.writeVarIntArray(tile.palette());
                    buffer.writeByteArray(tile.packedCells());
                },
                buffer -> {
                    buffer.readVarInt();
                    Tile tile = new Tile(buffer.readInt(), buffer.readInt(), buffer.readByte(),
                            buffer.readVarIntArray(ClimateTile.MAX_PALETTE), buffer.readByteArray(MAX_PACKED_BYTES));
                    PayloadFormat.skipNewerFields(buffer);
                    return tile;
                });

        public static Tile of(ClimateTile tile) {
            return new Tile(tile.tileX(), tile.tileZ(), tile.cellShift(), tile.palette(), tile.packCells());
        }

        /**
         * The tile it carries.
         *
         * @throws IllegalArgumentException if the payload doesn't make a tile
         */
        public ClimateTile unpack() {
            return ClimateTile.unpack(tileX, tileZ, cellShift, palette, packedCells);
        }

        /** The payload's size as encoded, without the channel name and packet framing. */
        public int sizeInBytes() {
            int size = VarInt.getByteSize(FORMAT) + 4 + 4 + 1 + VarInt.getByteSize(palette.length);
            for (int id : palette) {
                size += VarInt.getByteSize(id);
            }
            return size + VarInt.getByteSize(packedCells.length) + packedCells.length;
        }

        @Override
        public Type<Tile> type() {
            return TYPE;
        }
    }

    /**
     * From Almanac 0.1.3, to clients that register it: the overworld game tick ({@code Level#getGameTime}) at which
     * what far terrain shows ({@link State#sameFarSnow}) last changed. Sent on joining and just before each
     * {@link State} that changes it, so a far-terrain melt or snow-forming animation can resume part way through
     * instead of starting again. It is saved with the season, so a server restart isn't a change.
     */
    public record SnowChanged(long gameTick) implements CustomPacketPayload {
        public static final Type<SnowChanged> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "far_snow_changed"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SnowChanged> CODEC = StreamCodec.of(
                (buffer, changed) -> {
                    buffer.writeVarInt(FORMAT);
                    buffer.writeVarLong(changed.gameTick());
                },
                buffer -> {
                    buffer.readVarInt();
                    long gameTick = Math.max(0L, buffer.readVarLong());
                    PayloadFormat.skipNewerFields(buffer);
                    return new SnowChanged(gameTick);
                });

        @Override
        public Type<SnowChanged> type() {
            return TYPE;
        }
    }

    /**
     * Tiles the client may drop, because the player has moved away from them; {@code all} drops every tile (on
     * leaving the overworld, and when the map starts again with another cell size).
     */
    public record Forget(boolean all, long[] tiles) implements CustomPacketPayload {
        public static final Type<Forget> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "far_climate_forget"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Forget> CODEC = StreamCodec.of(
                (buffer, forget) -> {
                    buffer.writeVarInt(FORMAT);
                    buffer.writeBoolean(forget.all());
                    buffer.writeVarInt(forget.tiles().length);
                    for (long tile : forget.tiles()) {
                        buffer.writeLong(tile);
                    }
                },
                buffer -> {
                    buffer.readVarInt();
                    boolean all = buffer.readBoolean();
                    int count = buffer.readVarInt();
                    if (count < 0 || count > MAX_FORGOTTEN) {
                        throw new IllegalArgumentException("too many tiles to forget: " + count);
                    }
                    long[] tiles = new long[count];
                    for (int index = 0; index < count; index++) {
                        tiles[index] = buffer.readLong();
                    }
                    PayloadFormat.skipNewerFields(buffer);
                    return new Forget(all, tiles);
                });

        @Override
        public Type<Forget> type() {
            return TYPE;
        }
    }
}
