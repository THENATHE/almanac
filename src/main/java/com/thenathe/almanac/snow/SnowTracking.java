package com.thenathe.almanac.snow;

import com.thenathe.almanac.Almanac;
import com.thenathe.almanac.climate.ClimateHooks;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * The per-chunk snow record and the hooks that keep it: weather snow and ice the season brings, and snow players
 * place.
 */
public final class SnowTracking {
    public static final AttachmentType<ChunkSnow> CHUNK_SNOW = AttachmentRegistry.createPersistent(
            Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "snow"), ChunkSnow.CODEC);
    /** Ground native snow may melt off: grass, dirt, sand, stone, leaves and the like. Datapacks can extend it. */
    public static final TagKey<Block> NATURAL_SNOW_GROUND = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "natural_snow_ground"));

    private SnowTracking() {
    }

    /** Loads the class, so the attachment is registered before any chunk is read. */
    public static void initialize() {
    }

    public static boolean tracks(Level level) {
        return level instanceof ServerLevel && ClimateHooks.forLevel(level) != null;
    }

    public static ChunkSnow record(LevelChunk chunk) {
        return chunk.getAttachedOrCreate(CHUNK_SNOW, ChunkSnow::new);
    }

    /**
     * Vanilla's snowfall or freezing just changed {@code pos}. What the biome's real climate would have brought is
     * native and stays unrecorded; what only the season brought is recorded so it can melt.
     */
    public static void weatherPlaced(ServerLevel level, BlockPos pos, BlockState before, BlockState placed) {
        if (!tracks(level)) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        Biome biome = level.getBiome(pos).value();
        int key = ChunkSnow.pack(pos);
        if (placed.is(Blocks.ICE)) {
            if (biome.warmEnoughToRain(pos, level.getSeaLevel())) {
                record(chunk).setModIce(key, true);
                chunk.markUnsaved();
            }
            return;
        }
        if (!placed.is(Blocks.SNOW)) {
            return;
        }
        boolean seasonal = !biome.coldEnoughToSnow(pos, level.getSeaLevel());
        ChunkSnow existing = chunk.getAttached(CHUNK_SNOW);
        if (!seasonal && existing == null) {
            return;
        }
        ChunkSnow record = existing == null ? record(chunk) : existing;
        if (!before.is(Blocks.SNOW)) {
            // Fresh snow on an empty spot: whatever was recorded here before is gone.
            record.forget(key);
        }
        if (seasonal) {
            record.setModSnowLayers(key, record.modSnowLayers(key) + 1);
        }
        chunk.markUnsaved();
    }

    /**
     * A player placed a block. Snow they place is theirs and never melts; anything else replaces what was recorded.
     * The season's ice under whatever they place is theirs too, so an ice-fishing hut keeps its floor in spring.
     */
    public static void playerPlaced(Level level, BlockPos pos, BlockState placed) {
        if (!tracks(level)) {
            return;
        }
        LevelChunk chunk = level.getChunkAt(pos);
        boolean snow = placed.is(Blocks.SNOW) || placed.is(Blocks.SNOW_BLOCK);
        ChunkSnow existing = chunk.getAttached(CHUNK_SNOW);
        if (!snow && existing == null) {
            return;
        }
        ChunkSnow record = existing == null ? record(chunk) : existing;
        int key = ChunkSnow.pack(pos);
        record.forget(key);
        record.setPlayerSnow(key, snow);
        record.setModIce(ChunkSnow.pack(pos.below()), false);
        chunk.markUnsaved();
    }

    public static @Nullable ChunkSnow existingRecord(LevelChunk chunk) {
        return chunk.getAttached(CHUNK_SNOW);
    }
}
