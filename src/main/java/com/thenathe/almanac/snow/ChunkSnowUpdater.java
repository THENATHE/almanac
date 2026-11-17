package com.thenathe.almanac.snow;

import com.thenathe.almanac.biome.SeasonalBiomeIndex;
import com.thenathe.almanac.climate.SeasonalClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

/**
 * Applies {@link SnowRules} to a loaded chunk: whole chunks at once when they load, or a column at a time, a layer at
 * a time, for chunks that stay loaded. Snow and ice only go where the {@link Thaw} has reached.
 *
 * <p>Only blocks inside the chunk are read and written, and blocks are set without neighbour updates, so working on
 * a chunk never loads or changes its neighbours. Snowy grass, podzol and mycelium are set by hand instead.
 */
final class ChunkSnowUpdater {
    /** Changes go to players, but neighbours aren't updated: nothing outside the column depends on snow or ice. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Vanilla neither snows nor freezes at block light 10 or more. */
    private static final int MAX_BLOCK_LIGHT = 10;

    private final ServerLevel level;
    private final SeasonalClimate climate;
    private final SnowState state;
    private final int seaLevel;

    ChunkSnowUpdater(ServerLevel level, SeasonalClimate climate, SnowState state) {
        this.level = level;
        this.climate = climate;
        this.state = state;
        this.seaLevel = level.getSeaLevel();
    }

    /**
     * Brings the whole chunk to the current state at once, except for snow and ice the thaw hasn't reached.
     *
     * @return whether the chunk reached the state; if not, it still has snow or ice to lose and isn't marked as up
     * to date
     */
    boolean catchUp(LevelChunk chunk, ChunkSnow record, Thaw thaw) {
        boolean settled = true;
        for (int column = 0; column < 256; column++) {
            settled &= column(chunk, record, column & 15, column >> 4, false, thaw);
        }
        settled &= records(chunk, record, false, thaw);
        if (settled) {
            record.markUpdated(state, level.getGameTime());
        }
        chunk.markUnsaved();
        return settled;
    }

    /**
     * Moves one column's surface snow and ice towards the current state.
     *
     * @return whether the column is where it should be, or as close as it can get
     */
    boolean column(LevelChunk chunk, ChunkSnow record, int localX, int localZ, boolean gradual, Thaw thaw) {
        int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, localX, localZ) + 1;
        if (surface <= level.getMinY() || surface > level.getMaxY()) {
            return true;
        }
        BlockPos snowPos = new BlockPos(chunk.getPos().getMinBlockX() + localX, surface, chunk.getPos().getMinBlockZ() + localZ);
        if (!chunk.getBlockState(snowPos).is(Blocks.SNOW) && chunk.getBlockState(snowPos.below()).is(Blocks.SNOW)) {
            snowPos = snowPos.below();
        }
        Biome biome = level.getBiome(snowPos).value();
        SeasonalBiomeIndex.Entry entry = climate.entry(biome);
        if (entry == null) {
            return true;
        }
        Biome seasonal = entry.copy(state.subSeason()).value();
        BlockPos groundPos = snowPos.below();
        BlockState ground = chunk.getBlockState(groundPos);

        if (ground.is(Blocks.ICE)) {
            int iceKey = ChunkSnow.pack(groundPos);
            if (SnowRules.iceMelts(record.isModIce(iceKey), coldAt(seasonal, groundPos))) {
                if (!thaw.reached(groundPos)) {
                    return false;
                }
                meltIce(chunk, record, groundPos);
                return true;
            }
        } else if (isStillWater(ground)) {
            boolean cold = coldAt(seasonal, groundPos);
            if (SnowRules.freezes(true, cold, darkEnough(groundPos))) {
                // Like vanilla, loaded chunks freeze from the edges inward; chunks brought up to date freeze at once.
                if (gradual && surroundedByWater(chunk, groundPos)) {
                    return false;
                }
                setBlock(groundPos, Blocks.ICE.defaultBlockState());
                record.setModIce(ChunkSnow.pack(groundPos), !biome.coldEnoughToSnow(groundPos, seaLevel));
                chunk.markUnsaved();
                ground = Blocks.ICE.defaultBlockState();
            }
        }
        return snowAt(chunk, record, snowPos, ground, biome, seasonal, entry, true, gradual, thaw);
    }

    /**
     * Brings recorded spots that aren't on top of their column (something was built over them) and stale records up
     * to date, and forgets records whose block is gone.
     */
    boolean records(LevelChunk chunk, ChunkSnow record, boolean gradual, Thaw thaw) {
        boolean settled = true;
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (int key : record.modSnowPositions()) {
            BlockPos pos = new BlockPos(minX + ChunkSnow.localX(key), ChunkSnow.y(key), minZ + ChunkSnow.localZ(key));
            if (!chunk.getBlockState(pos).is(Blocks.SNOW)) {
                record.setModSnowLayers(key, 0);
                chunk.markUnsaved();
                continue;
            }
            Biome biome = level.getBiome(pos).value();
            SeasonalBiomeIndex.Entry entry = climate.entry(biome);
            if (entry != null) {
                Biome seasonal = entry.copy(state.subSeason()).value();
                settled &= snowAt(chunk, record, pos, chunk.getBlockState(pos.below()), biome, seasonal, entry, false,
                        gradual, thaw);
            }
        }
        for (int key : record.modIcePositions()) {
            BlockPos pos = new BlockPos(minX + ChunkSnow.localX(key), ChunkSnow.y(key), minZ + ChunkSnow.localZ(key));
            if (!chunk.getBlockState(pos).is(Blocks.ICE)) {
                record.setModIce(key, false);
                chunk.markUnsaved();
                continue;
            }
            SeasonalBiomeIndex.Entry entry = climate.entry(level.getBiome(pos).value());
            if (entry != null && SnowRules.iceMelts(true, coldAt(entry.copy(state.subSeason()).value(), pos))) {
                if (thaw.reached(pos)) {
                    meltIce(chunk, record, pos);
                } else {
                    settled = false;
                }
            }
        }
        for (int key : record.playerSnowPositions()) {
            BlockPos pos = new BlockPos(minX + ChunkSnow.localX(key), ChunkSnow.y(key), minZ + ChunkSnow.localZ(key));
            if (!chunk.getBlockState(pos).is(BlockTags.SNOW)) {
                record.setPlayerSnow(key, false);
                chunk.markUnsaved();
            }
        }
        return settled;
    }

    private boolean snowAt(LevelChunk chunk, ChunkSnow record, BlockPos pos, BlockState ground, Biome biome,
                           Biome seasonal, SeasonalBiomeIndex.Entry entry, boolean mayAdd, boolean gradual, Thaw thaw) {
        BlockState here = chunk.getBlockState(pos);
        int key = ChunkSnow.pack(pos);
        boolean isSnow = here.is(Blocks.SNOW);
        if (!isSnow) {
            // Recorded snow that is gone, or was never there.
            if (record.modSnowLayers(key) > 0) {
                record.setModSnowLayers(key, 0);
                chunk.markUnsaved();
            }
            if (!here.isAir()) {
                return true;
            }
        }
        int layers = isSnow ? here.getValue(SnowLayerBlock.LAYERS) : 0;
        boolean canHold = mayAdd
                && Blocks.SNOW.defaultBlockState().canSurvive(level, pos)
                && darkEnough(pos);
        SnowRules.SnowSpot spot = new SnowRules.SnowSpot(entry.base(), coldAt(seasonal, pos),
                biome.coldEnoughToSnow(pos, seaLevel), layers, record.modSnowLayers(key), record.isPlayerSnow(key),
                canHold, ground.is(SnowTracking.NATURAL_SNOW_GROUND));
        SnowRules.Outcome outcome = SnowRules.snow(spot, state.layers(entry.base()), state.nativeSnowMelts(),
                state.naturalHeight(), gradual);
        if (outcome.layers() < layers && !thaw.reached(pos)) {
            // Not this spot's turn in the thaw yet.
            return false;
        }
        if (outcome.layers() != layers) {
            setSnowLayers(pos, here, outcome.layers());
        }
        if (outcome.layers() == 0) {
            if (layers > 0) {
                record.forget(key);
                chunk.markUnsaved();
            }
        } else if (outcome.modLayers() != record.modSnowLayers(key)) {
            record.setModSnowLayers(key, outcome.modLayers());
            chunk.markUnsaved();
        }
        return outcome.settled();
    }

    /** Turns the season's ice back into water, clearing snow that lay on it. A player's snow keeps it frozen. */
    private void meltIce(LevelChunk chunk, ChunkSnow record, BlockPos icePos) {
        BlockPos above = icePos.above();
        BlockState aboveState = chunk.getBlockState(above);
        int aboveKey = ChunkSnow.pack(above);
        if (aboveState.is(BlockTags.SNOW)) {
            if (record.isPlayerSnow(aboveKey) || !aboveState.is(Blocks.SNOW)) {
                return;
            }
            setBlock(above, Blocks.AIR.defaultBlockState());
            record.forget(aboveKey);
        }
        setBlock(icePos, IceBlock.meltsInto());
        record.setModIce(ChunkSnow.pack(icePos), false);
        chunk.markUnsaved();
    }

    private void setSnowLayers(BlockPos pos, BlockState before, int layers) {
        BlockState after = layers == 0
                ? Blocks.AIR.defaultBlockState()
                : Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers);
        if (layers > 0 && before.is(Blocks.SNOW)) {
            Block.pushEntitiesUp(before, after, level, pos);
        }
        setBlock(pos, after);
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (belowState.hasProperty(BlockStateProperties.SNOWY)) {
            boolean snowy = after.is(BlockTags.SNOW);
            if (belowState.getValue(BlockStateProperties.SNOWY) != snowy) {
                setBlock(below, belowState.setValue(BlockStateProperties.SNOWY, snowy));
            }
        }
    }

    private void setBlock(BlockPos pos, BlockState state) {
        level.setBlock(pos, state, FLAGS);
    }

    private boolean coldAt(Biome seasonal, BlockPos pos) {
        return seasonal.coldEnoughToSnow(pos, seaLevel);
    }

    private boolean darkEnough(BlockPos pos) {
        return level.getBrightness(LightLayer.BLOCK, pos) < MAX_BLOCK_LIGHT;
    }

    private static boolean isStillWater(BlockState state) {
        return state.getBlock() instanceof LiquidBlock && state.getFluidState().is(Fluids.WATER);
    }

    /** Water on all four sides, reading only chunks already loaded; anything unknown counts as shore. */
    private boolean surroundedByWater(LevelChunk chunk, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            int chunkX = side.getX() >> 4;
            int chunkZ = side.getZ() >> 4;
            LevelChunk sideChunk = chunkX == chunk.getPos().x() && chunkZ == chunk.getPos().z() ? chunk
                    : level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (sideChunk == null || !sideChunk.getFluidState(side).is(Fluids.WATER)) {
                return false;
            }
        }
        return true;
    }
}
