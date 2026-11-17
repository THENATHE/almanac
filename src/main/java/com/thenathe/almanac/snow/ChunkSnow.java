package com.thenathe.almanac.snow;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * Almanac's record for one chunk, saved with the chunk as a Fabric data attachment ({@code almanac:snow}):
 * <ul>
 *   <li>when the chunk was last brought up to date, and to which {@link SnowState};</li>
 *   <li>snow and ice the season put there (seasonal snowfall, freezing and catch-up), which may melt again;</li>
 *   <li>snow layers and snow blocks players placed, which never melt.</li>
 * </ul>
 * Snow and ice the biome's real climate would bring aren't recorded: they are native, like world generation's.
 *
 * <p>Positions are packed as {@code x | z << 4 | y << 8} with x and z inside the chunk. Fields are only ever added,
 * each with a default, so a record from an older version keeps loading; {@link #FORMAT} says which version wrote it.
 * The record is changed in place, so whoever changes it marks the chunk unsaved.
 */
public final class ChunkSnow {
    public static final int FORMAT = 1;
    public static final long NEVER = Long.MIN_VALUE;

    private long updated = NEVER;
    private @Nullable SnowState state;
    /** Seasonal snow by position: how many of the layers there the season added. */
    private final Int2IntOpenHashMap modSnow = new Int2IntOpenHashMap();
    private final IntOpenHashSet modIce = new IntOpenHashSet();
    private final IntOpenHashSet playerSnow = new IntOpenHashSet();

    public static int pack(BlockPos pos) {
        return pack(pos.getX() & 15, pos.getY(), pos.getZ() & 15);
    }

    public static int pack(int localX, int y, int localZ) {
        return localX | localZ << 4 | y << 8;
    }

    public static int localX(int packed) {
        return packed & 15;
    }

    public static int localZ(int packed) {
        return packed >> 4 & 15;
    }

    public static int y(int packed) {
        return packed >> 8;
    }

    /** The game time this chunk was last brought up to date, or {@link #NEVER}. */
    public long updated() {
        return updated;
    }

    public @Nullable SnowState state() {
        return state;
    }

    public boolean isUpTo(SnowState current) {
        return current.equals(state);
    }

    public void markUpdated(SnowState current, long gameTime) {
        state = current;
        updated = gameTime;
    }

    public int modSnowLayers(int packed) {
        return modSnow.get(packed);
    }

    public void setModSnowLayers(int packed, int layers) {
        if (layers > 0) {
            modSnow.put(packed, layers);
        } else {
            modSnow.remove(packed);
        }
    }

    public int[] modSnowPositions() {
        return modSnow.keySet().toIntArray();
    }

    public boolean isModIce(int packed) {
        return modIce.contains(packed);
    }

    public void setModIce(int packed, boolean seasonal) {
        if (seasonal) {
            modIce.add(packed);
        } else {
            modIce.remove(packed);
        }
    }

    public int[] modIcePositions() {
        return modIce.toIntArray();
    }

    public boolean isPlayerSnow(int packed) {
        return playerSnow.contains(packed);
    }

    public void setPlayerSnow(int packed, boolean placedByPlayer) {
        if (placedByPlayer) {
            playerSnow.add(packed);
        } else {
            playerSnow.remove(packed);
        }
    }

    public int[] playerSnowPositions() {
        return playerSnow.toIntArray();
    }

    /** Forgets everything recorded at a position, for when the block there is gone or no longer snow or ice. */
    public void forget(int packed) {
        modSnow.remove(packed);
        modIce.remove(packed);
        playerSnow.remove(packed);
    }

    private static final int[] NONE = new int[0];
    private static final Codec<int[]> INT_ARRAY = Codec.INT_STREAM.xmap(IntStream::toArray, IntStream::of);

    private record Saved(int format, long updated, Optional<SnowState> state, int[] modSnow, int[] modSnowLayers,
                         int[] modIce, int[] playerSnow) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.INT.lenientOptionalFieldOf("format", FORMAT).forGetter(Saved::format),
                Codec.LONG.lenientOptionalFieldOf("updated", NEVER).forGetter(Saved::updated),
                SnowState.CODEC.lenientOptionalFieldOf("state").forGetter(Saved::state),
                INT_ARRAY.lenientOptionalFieldOf("mod_snow", NONE).forGetter(Saved::modSnow),
                INT_ARRAY.lenientOptionalFieldOf("mod_snow_layers", NONE).forGetter(Saved::modSnowLayers),
                INT_ARRAY.lenientOptionalFieldOf("mod_ice", NONE).forGetter(Saved::modIce),
                INT_ARRAY.lenientOptionalFieldOf("player_snow", NONE).forGetter(Saved::playerSnow)
        ).apply(instance, Saved::new));
    }

    public static final Codec<ChunkSnow> CODEC = Saved.CODEC.xmap(ChunkSnow::fromSaved, ChunkSnow::toSaved);

    private static ChunkSnow fromSaved(Saved saved) {
        ChunkSnow record = new ChunkSnow();
        record.updated = saved.updated();
        record.state = saved.state().orElse(null);
        int[] positions = saved.modSnow();
        int[] layers = saved.modSnowLayers();
        for (int index = 0; index < positions.length; index++) {
            // A layer count that is missing or out of range counts as one layer: the least that can melt.
            int count = index < layers.length ? layers[index] : 1;
            record.modSnow.put(positions[index], count >= 1 && count <= 8 ? count : 1);
        }
        record.modIce.addAll(IntArrayList.wrap(saved.modIce()));
        record.playerSnow.addAll(IntArrayList.wrap(saved.playerSnow()));
        return record;
    }

    private Saved toSaved() {
        int[] positions = modSnow.keySet().toIntArray();
        Arrays.sort(positions);
        int[] layers = new int[positions.length];
        for (int index = 0; index < positions.length; index++) {
            layers[index] = modSnow.get(positions[index]);
        }
        int[] ice = modIce.toIntArray();
        Arrays.sort(ice);
        int[] player = playerSnow.toIntArray();
        Arrays.sort(player);
        return new Saved(FORMAT, updated, Optional.ofNullable(state), positions, layers, ice, player);
    }
}
