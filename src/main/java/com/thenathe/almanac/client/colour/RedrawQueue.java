package com.thenathe.almanac.client.colour;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Asks the renderer to redraw the loaded sections that hold seasonal blocks, a few each tick and nearest first, so
 * a colour change never makes vanilla or Sodium rebuild every section at once. Sections without grass, leaves or
 * other biome-tinted blocks are skipped.
 */
final class RedrawQueue {
    private final LongArrayFIFOQueue chunks = new LongArrayFIFOQueue();
    private int redrawnSections;

    /** Starts over from the chunks around {@code centre}, out to the render distance. */
    void restart(ChunkPos centre, int radius) {
        chunks.clear();
        redrawnSections = 0;
        chunks.enqueue(ChunkPos.pack(centre.x(), centre.z()));
        for (int ring = 1; ring <= radius; ring++) {
            for (int step = -ring; step < ring; step++) {
                chunks.enqueue(ChunkPos.pack(centre.x() + step, centre.z() - ring));
                chunks.enqueue(ChunkPos.pack(centre.x() + ring, centre.z() + step));
                chunks.enqueue(ChunkPos.pack(centre.x() - step, centre.z() + ring));
                chunks.enqueue(ChunkPos.pack(centre.x() - ring, centre.z() - step));
            }
        }
    }

    void clear() {
        chunks.clear();
    }

    boolean isEmpty() {
        return chunks.isEmpty();
    }

    /** Sections handed to the renderer since the last restart. */
    int redrawnSections() {
        return redrawnSections;
    }

    /** Marks up to about {@code budget} sections for redrawing; a chunk is always finished once started. */
    void tick(ClientLevel level, int budget, Predicate<BlockState> seasonal) {
        int marked = 0;
        while (marked < budget && !chunks.isEmpty()) {
            long packed = chunks.dequeueLong();
            int chunkX = ChunkPos.getX(packed);
            int chunkZ = ChunkPos.getZ(packed);
            LevelChunk chunk = (LevelChunk) level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            if (chunk == null) {
                continue;
            }
            LevelChunkSection[] sections = chunk.getSections();
            for (int index = 0; index < sections.length; index++) {
                LevelChunkSection section = sections[index];
                if (!section.hasOnlyAir() && section.maybeHas(seasonal)) {
                    Minecraft.getInstance().levelExtractor.setSectionDirty(chunkX, level.getSectionYFromSectionIndex(index), chunkZ);
                    marked++;
                }
            }
        }
        redrawnSections += marked;
    }
}
