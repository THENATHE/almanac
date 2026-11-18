package com.thenathe.almanac.season;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.thenathe.almanac.Almanac;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fields of Almanac's saved world data ({@code season.dat}, {@code snow.dat}) that are read one by one, so a damaged
 * field costs only itself. Minecraft throws a whole saved data file away when it can't be read, and starts it fresh;
 * for these files that would reset the year or the snow history.
 */
public final class SavedFields {
    private static final Logger LOGGER = LoggerFactory.getLogger(Almanac.MOD_ID);

    private SavedFields() {
    }

    /**
     * An optional field: a missing one reads as {@code fallback}, as a file from an older version has it, and one that
     * can't be read is logged and read as {@code fallback} too. Lists and maps keep the entries that can be read.
     *
     * @param file where the field is, for the log
     */
    public static <A> MapCodec<A> optional(String file, String name, Codec<A> codec, A fallback) {
        return new MapCodec<>() {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops) {
                return Stream.of(ops.createString(name));
            }

            @Override
            public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input) {
                T value = input.get(name);
                if (value == null) {
                    return DataResult.success(fallback);
                }
                A read = codec.parse(ops, value)
                        .resultOrPartial(error -> LOGGER.warn("Almanac: dropped what couldn't be read of {} in {}: {}",
                                name, file, error))
                        .orElse(fallback);
                return DataResult.success(read);
            }

            @Override
            public <T> RecordBuilder<T> encode(A value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                return prefix.add(name, codec.encodeStart(ops, value));
            }

            @Override
            public String toString() {
                return "SavedFields[" + name + "]";
            }
        };
    }
}
