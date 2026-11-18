package com.thenathe.almanac.snow;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.biome.SeasonalBiomeIndex;
import com.thenathe.almanac.climate.SeasonalClimate;
import java.util.EnumSet;
import java.util.Set;
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
import org.jspecify.annotations.Nullable;

/**
 * Applies {@link SnowRules} to a loaded chunk: whole chunks at once when they load, or a column at a time, a layer at
 * a time, for chunks that stay loaded. Snow and ice only change where the {@link Pace} allows.
 *
 * <p>Only blocks inside the chunk are written, and blocks are set without neighbour updates, so working on a chunk
 * never loads or changes its neighbours. Snowy grass, podzol and mycelium are set by hand instead. Freezing reads the
 * water beside a column in neighbouring chunks that are already loaded.
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

    /**
     * One pass over a chunk's columns and recorded spots, and what it found. A pass in a loaded chunk can take several
     * ticks.
     */
    static final class Pass {
        final LevelChunk chunk;
        final ChunkSnow record;
        /** A layer at a time, and water freezing from the shore inward; otherwise everything at once. */
        final boolean gradual;
        final Pace pace;
        /** The base climates of the surface and the recorded spots, saved when the chunk is up to date. */
        final Set<Climate> climates = EnumSet.noneOf(Climate.class);
        /** The next column to visit, in the caller's order. */
        int nextColumn;
        /** Something has more to do after this pass: another layer, or water waiting for the ice to reach it. */
        boolean moreToDo;
        /** Something waits for the pace: the thaw or the snow forming hasn't reached it yet. */
        boolean waiting;
        /** The pass changed a block. */
        boolean changed;

        Pass(LevelChunk chunk, ChunkSnow record, boolean gradual, Pace pace) {
            this.chunk = chunk;
            this.record = record;
            this.gradual = gradual;
            this.pace = pace;
        }

        /** Nothing is left to do: the chunk has reached the state. */
        boolean settled() {
            return !moreToDo && !waiting;
        }
    }

    ChunkSnowUpdater(ServerLevel level, SeasonalClimate climate, SnowState state) {
        this.level = level;
        this.climate = climate;
        this.state = state;
        this.seaLevel = level.getSeaLevel();
    }

    /** Brings the whole chunk to the current state at once, except for what the pace holds back. */
    Pass catchUp(LevelChunk chunk, ChunkSnow record, Pace pace) {
        Pass pass = new Pass(chunk, record, false, pace);
        for (int column = 0; column < 256; column++) {
            column(pass, column & 15, column >> 4);
        }
        records(pass);
        if (pass.settled()) {
            settle(pass);
        }
        chunk.markUnsaved();
        return pass;
    }

    /** Marks the pass's chunk as brought up to the current state. */
    void settle(Pass pass) {
        pass.record.markUpdated(state, pass.climates, level.getGameTime());
        pass.chunk.markUnsaved();
    }

    /** Moves one column's surface snow and ice towards the current state. */
    void column(Pass pass, int localX, int localZ) {
        LevelChunk chunk = pass.chunk;
        BlockPos snowPos = snowSpot(chunk, localX, localZ);
        if (snowPos == null) {
            return;
        }
        Biome biome = level.getBiome(snowPos).value();
        SeasonalBiomeIndex.Entry entry = climate.entry(biome);
        if (entry == null) {
            return;
        }
        pass.climates.add(entry.base());
        Biome seasonal = entry.copy(state.subSeason()).value();
        BlockPos groundPos = snowPos.below();
        BlockState ground = chunk.getBlockState(groundPos);

        if (ground.is(Blocks.ICE)) {
            if (pass.record.isModIce(ChunkSnow.pack(groundPos)) && iceMelts(entry, seasonal, groundPos)) {
                if (!pass.pace.mayMelt(groundPos)) {
                    pass.waiting = true;
                    return;
                }
                meltIce(pass, groundPos);
                return;
            }
        } else if (entry.seasonalIce() && isStillWater(ground)) {
            if (SnowRules.freezes(true, coldAt(seasonal, groundPos), darkEnough(groundPos))) {
                // Loaded chunks freeze from the shore inward, as vanilla does; chunks brought up to date freeze at once.
                if (pass.gradual && surroundedByFreezingWater(chunk, groundPos)) {
                    pass.moreToDo = true;
                    return;
                }
                setBlock(pass, groundPos, Blocks.ICE.defaultBlockState());
                pass.record.setModIce(ChunkSnow.pack(groundPos), !biome.coldEnoughToSnow(groundPos, seaLevel));
                ground = Blocks.ICE.defaultBlockState();
            }
        }
        snowAt(pass, snowPos, ground, biome, seasonal, entry, true);
    }

    /**
     * Brings the recorded spots the column pass doesn't reach up to date (snow and ice something was built over), and
     * forgets records whose block is gone. Spots on top of their column were handled by {@link #column}.
     */
    void records(Pass pass) {
        LevelChunk chunk = pass.chunk;
        ChunkSnow record = pass.record;
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        for (int key : record.modSnowPositions()) {
            BlockPos pos = new BlockPos(minX + ChunkSnow.localX(key), ChunkSnow.y(key), minZ + ChunkSnow.localZ(key));
            if (!chunk.getBlockState(pos).is(Blocks.SNOW)) {
                record.setModSnowLayers(key, 0);
                chunk.markUnsaved();
                continue;
            }
            if (pos.equals(snowSpot(chunk, ChunkSnow.localX(key), ChunkSnow.localZ(key)))) {
                continue;
            }
            Biome biome = level.getBiome(pos).value();
            SeasonalBiomeIndex.Entry entry = climate.entry(biome);
            if (entry != null) {
                pass.climates.add(entry.base());
                Biome seasonal = entry.copy(state.subSeason()).value();
                snowAt(pass, pos, chunk.getBlockState(pos.below()), biome, seasonal, entry, false);
            }
        }
        for (int key : record.modIcePositions()) {
            BlockPos pos = new BlockPos(minX + ChunkSnow.localX(key), ChunkSnow.y(key), minZ + ChunkSnow.localZ(key));
            if (!chunk.getBlockState(pos).is(Blocks.ICE)) {
                record.setModIce(key, false);
                chunk.markUnsaved();
                continue;
            }
            BlockPos top = snowSpot(chunk, ChunkSnow.localX(key), ChunkSnow.localZ(key));
            if (top != null && pos.equals(top.below())) {
                continue;
            }
            SeasonalBiomeIndex.Entry entry = climate.entry(level.getBiome(pos).value());
            if (entry != null && iceMelts(entry, entry.copy(state.subSeason()).value(), pos)) {
                if (pass.pace.mayMelt(pos)) {
                    meltIce(pass, pos);
                } else {
                    pass.waiting = true;
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
    }

    /**
     * Where a column's snow lies or would lie: the snow layer on top, or the free space above the top block. Null
     * when that is outside the world.
     */
    private @Nullable BlockPos snowSpot(LevelChunk chunk, int localX, int localZ) {
        int surface = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, localX, localZ) + 1;
        if (surface <= level.getMinY() || surface > level.getMaxY()) {
            return null;
        }
        BlockPos pos = new BlockPos(chunk.getPos().getMinBlockX() + localX, surface, chunk.getPos().getMinBlockZ() + localZ);
        if (!chunk.getBlockState(pos).is(Blocks.SNOW) && chunk.getBlockState(pos.below()).is(Blocks.SNOW)) {
            return pos.below();
        }
        return pos;
    }

    private void snowAt(Pass pass, BlockPos pos, BlockState ground, Biome biome, Biome seasonal,
                        SeasonalBiomeIndex.Entry entry, boolean mayAdd) {
        LevelChunk chunk = pass.chunk;
        ChunkSnow record = pass.record;
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
                return;
            }
        }
        int layers = isSnow ? here.getValue(SnowLayerBlock.LAYERS) : 0;
        int depthLayers = state.layers(entry.base());
        // Most columns, most of the year: no snow, and none can come. The checks below are the costly ones.
        if (layers == 0 && (!mayAdd || depthLayers == 0)) {
            return;
        }
        boolean coldNow = coldAt(seasonal, pos);
        if (layers == 0 && !coldNow) {
            return;
        }
        boolean canHold = mayAdd
                && Blocks.SNOW.defaultBlockState().canSurvive(level, pos)
                && darkEnough(pos);
        SnowRules.SnowSpot spot = new SnowRules.SnowSpot(entry.base(), coldNow, biome.coldEnoughToSnow(pos, seaLevel),
                layers, record.modSnowLayers(key), record.isPlayerSnow(key), canHold,
                ground.is(SnowTracking.NATURAL_SNOW_GROUND));
        SnowRules.Outcome outcome = SnowRules.snow(spot, depthLayers, state.nativeSnowMelts(), state.naturalHeight(),
                pass.gradual);
        if (outcome.layers() < layers && !pass.pace.mayMelt(pos)
                || outcome.layers() > layers && !pass.pace.mayForm(pos)) {
            // Not this spot's turn in the thaw, or in the snow forming, yet.
            pass.waiting = true;
            return;
        }
        if (outcome.layers() != layers) {
            setSnowLayers(pass, pos, here, outcome.layers());
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
        if (!outcome.settled()) {
            pass.moreToDo = true;
        }
    }

    /**
     * Whether the season's ice at a spot should melt: where it is warm now, and anywhere in biomes that get no seasonal
     * ice (oceans frozen by an older version).
     */
    private boolean iceMelts(SeasonalBiomeIndex.Entry entry, Biome seasonal, BlockPos pos) {
        return SnowRules.iceMelts(true, entry.seasonalIce() && coldAt(seasonal, pos));
    }

    /** Turns the season's ice back into water, clearing snow that lay on it. A player's snow keeps it frozen. */
    private void meltIce(Pass pass, BlockPos icePos) {
        LevelChunk chunk = pass.chunk;
        ChunkSnow record = pass.record;
        BlockPos above = icePos.above();
        BlockState aboveState = chunk.getBlockState(above);
        int aboveKey = ChunkSnow.pack(above);
        if (aboveState.is(BlockTags.SNOW)) {
            if (record.isPlayerSnow(aboveKey) || !aboveState.is(Blocks.SNOW)) {
                return;
            }
            setBlock(pass, above, Blocks.AIR.defaultBlockState());
            record.forget(aboveKey);
        }
        setBlock(pass, icePos, IceBlock.meltsInto());
        record.setModIce(ChunkSnow.pack(icePos), false);
        chunk.markUnsaved();
    }

    private void setSnowLayers(Pass pass, BlockPos pos, BlockState before, int layers) {
        BlockState after = layers == 0
                ? Blocks.AIR.defaultBlockState()
                : Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers);
        if (layers > 0 && before.is(Blocks.SNOW)) {
            Block.pushEntitiesUp(before, after, level, pos);
        }
        setBlock(pass, pos, after);
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (belowState.hasProperty(BlockStateProperties.SNOWY)) {
            boolean snowy = after.is(BlockTags.SNOW);
            if (belowState.getValue(BlockStateProperties.SNOWY) != snowy) {
                setBlock(pass, below, belowState.setValue(BlockStateProperties.SNOWY, snowy));
            }
        }
    }

    private void setBlock(Pass pass, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, FLAGS);
        pass.record.leftLastState();
        pass.chunk.markUnsaved();
        pass.changed = true;
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

    /**
     * Water that will freeze on all four sides ({@link SnowRules#holdsBackFreezing}), reading only chunks already
     * loaded; anything else, an unloaded chunk included, counts as shore.
     */
    private boolean surroundedByFreezingWater(LevelChunk chunk, BlockPos pos) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(direction);
            int chunkX = side.getX() >> 4;
            int chunkZ = side.getZ() >> 4;
            LevelChunk sideChunk = chunkX == chunk.getPos().x() && chunkZ == chunk.getPos().z() ? chunk
                    : level.getChunkSource().getChunkNow(chunkX, chunkZ);
            if (sideChunk == null || !holdsBackFreezing(sideChunk, side)) {
                return false;
            }
        }
        return true;
    }

    private boolean holdsBackFreezing(LevelChunk chunk, BlockPos pos) {
        boolean stillWater = isStillWater(chunk.getBlockState(pos));
        boolean onTop = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX() & 15, pos.getZ() & 15) == pos.getY();
        if (!stillWater || !onTop) {
            return false;
        }
        SeasonalBiomeIndex.Entry entry = climate.entry(level.getBiome(pos).value());
        if (entry == null || !entry.seasonalIce()) {
            return false;
        }
        boolean coldNow = coldAt(entry.copy(state.subSeason()).value(), pos);
        return SnowRules.holdsBackFreezing(true, true, true, coldNow, darkEnough(pos));
    }
}
