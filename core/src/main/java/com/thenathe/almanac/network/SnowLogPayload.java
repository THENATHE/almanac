package com.thenathe.almanac.network;

import com.thenathe.almanac.Almanac;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * S2C, from 0.4.0: the overworld's weather log, as the snow module saves it in {@code snow.dat}, so a client can work
 * out the snow of land it hasn't loaded (far terrain) the way the server works out loaded land, from the log and the
 * storms. Sent on joining and when the log changes, which under regional weather is a new sub-season or a changed
 * setting. A few KB at most, since the log folds its oldest stretches away.
 */
public record SnowLogPayload(CompoundTag log) implements CustomPacketPayload {
    /** Far more than a log ever is; a broken or hostile server can't make the client read without end. */
    private static final long MOST_BYTES = 2L * 1024 * 1024;

    public static final Type<SnowLogPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "snow_log"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SnowLogPayload> CODEC = StreamCodec.of(
            (buffer, payload) -> ByteBufCodecs.COMPOUND_TAG.encode(buffer, payload.log()),
            buffer -> {
                CompoundTag log = ByteBufCodecs.compoundTagCodec(() -> NbtAccounter.create(MOST_BYTES)).decode(buffer);
                PayloadFormat.skipNewerFields(buffer);
                return new SnowLogPayload(log);
            });

    @Override
    public Type<SnowLogPayload> type() {
        return TYPE;
    }
}
