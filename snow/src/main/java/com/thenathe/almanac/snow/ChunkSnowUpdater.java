package com.thenathe.almanac.snow;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.BiomeClimate;
import com.thenathe.almanac.internal.ClimateNow;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
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
 * Brings columns of a loaded chunk to the current {@link SnowState} by {@link SnowRules}: each column straight to its
 * target, the layers its point gets from its climate's shares and whether its water is frozen by its freezing point.
 * Under regional weather the state is the chunk's own ({@link #withState}), with its depths from the snow nodes
 * around it.
 * The same columns get the same snow whether the chunk stayed loaded, loads now or was just made; only which columns
 * are looked at differs ({@code SnowUpdates}).
 *
 * <p>Only blocks inside the chunk are written, and blocks are set without neighour updates, so working on a chunk
 * never loads or changes its neighbours. Snowy grass, podzol and mycelium are set by hand instead. Where the shore is
 * comes from the chunk and those of its neighbours already loaded.
 */
final class ChunkSnowUpdater {
    /** Changes go to players, but neighbours aren't updated: nothing outside the column depends on snow or ice. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** Vanilla neither snows nor freezes at block light 10 or more. */
    private static final int MAX_BLOCK_LIGHT = 10;
    /** The chunk and the ring around it that the shore is looked for in. */
    private static final int REACH = ColumnPoints.SHORE_REACH;
    private static final int GRID = 16 + 2 * REACH;

    private final ServerLevel level;
    private final ClimateNow climate;
    private final SnowState state;
    private final long seed;
    private final int seaLevel;
    private final Map<Climate, @Nullable SubSeason> coldBefore = new EnumMap<>(Climate.class);

    /** One look at a chunk's columns, and what it found and did. */
    static final class Visit {
        final LevelChunk chunk;
        final ChunkSnow record;
        final ChunkPoints points;
        /** The base climates of the columns and recorded spots looked at. */
        final Set<Climate> climates = EnumSet.noneOf(Climate.class);
        int columns;
        int changedColumns;
        boolean changed;
        private double @Nullable [] icePoints;

        Visit(LevelChunk chunk, ChunkSnow record, ChunkPoints points) {
            this.chunk = chunk;
            this.record = record;
            this.points = points;
        }
    }

    ChunkSnowUpdater(ServerLevel level, ClimateNow climate, SnowState state) {
        this.level = level;
        this.climate = climate;
        this.state = state;
        this.seed = level.getSeed();
        this.seaLevel = level.getSeaLevel();
    }

    /**
     * The same, bringing chunks to another state: under regional weather each chunk has depths of its own and the rest
     * of the world's state, so what was worked out about the sub-season is shared.
     */
    ChunkSnowUpdater withState(SnowState chunkState) {
        ChunkSnowUpdater updater = new ChunkSnowUpdater(level, climate, chunkState);
        updater.coldBefore.putAll(coldBefore);
        return updater;
    }

    ClimateNow climate() {
        return climate;
    }

    SnowState state() {
        return state;
    }

    /**
     * Brings the chunk's columns in {@code columns}, or every column and every recorded spot when that is null, to
     * the current state.
     */
    Visit visit(LevelChunk chunk, ChunkSnow record, ChunkPoints points, ChunkPoints.@Nullable ColumnSet columns) {
        Visit visit = new Visit(chunk, record, points);
        for (int column = 0; column < 256; column++) {
            if (columns == null || columns.contains(column)) {
                boolean changedBefore = visit.changed;
                visit.changed = false;
                column(visit, column);
                visit.columns++;
                if (visit.changed) {
                    visit.changedColumns++;
                }
                visit.changed |= changedBefore;
            }
        }
        if (columns == null) {
            records(visit);
        }
        if (visit.changed) {
            chunk.markUnsaved();
        }
        return visit;
    }

    /** Marks the chunk as brought to the current state, holding {@code climates}. */
    void settle(ChunkSnow record, LevelChunk chunk, Set<Climate> climates) {
        record.markUpdated(state, climates, level.getGameTime());
        chunk.markUnsaved();
    }

    /**
     * The freezing points of a chunk's columns ({@link ColumnPoints#ice}), {@link ChunkPoints#NOT_WATER} where the top
     * isn't still water or ice. The shore is any column whose top is something else; columns of neighbouring chunks
     * that aren't loaded count as water, so a pond across a chunk border never gets a shore that isn't there.
     */
    double[] icePoints(LevelChunk chunk) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        boolean[] pond = new boolean[GRID * GRID];
        LevelChunk[] neighbours = new LevelChunk[9];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int gz = 0; gz < GRID; gz++) {
            for (int gx = 0; gx < GRID; gx++) {
                int x = minX - REACH + gx;
                int z = minZ - REACH + gz;
                LevelChunk owner = neighbour(chunk, neighbours, x >> 4, z >> 4);
                pond[gz * GRID + gx] = owner == null || pondSurface(owner, x, z, pos);
            }
        }
        int[] distance = ColumnPoints.shoreDistances(pond, GRID);
        double[] points = new double[256];
        for (int column = 0; column < 256; column++) {
            int gx = (column & 15) + REACH;
            int gz = (column >> 4) + REACH;
            int cell = gz * GRID + gx;
            points[column] = pond[cell]
                    ? ColumnPoints.ice(seed, minX + (column & 15), minZ + (column >> 4), distance[cell])
                    : ChunkPoints.NOT_WATER;
        }
        return points;
    }

    private @Nullable LevelChunk neighbour(LevelChunk chunk, LevelChunk[] cache, int chunkX, int chunkZ) {
        int dx = chunkX - chunk.getPos().x();
        int dz = chunkZ - chunk.getPos().z();
        if (dx == 0 && dz == 0) {
            return chunk;
        }
        int index = (dz + 1) * 3 + dx + 1;
        if (cache[index] == null) {
            cache[index] = level.getChunkSource().getChunkNow(chunkX, chunkZ);
        }
        return cache[index];
    }

    /** Whether a column's top is still water or ice: part of a pond, frozen or not. */
    private static boolean pondSurface(LevelChunk chunk, int x, int z, BlockPos.MutableBlockPos pos) {
        int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15);
        BlockState state = chunk.getBlockState(pos.set(x, top, z));
        if (state.is(Blocks.SNOW)) {
            // Snow lying on the ice: the pond is under it.
            state = chunk.getBlockState(pos.set(x, top - 1, z));
        }
        return state.is(Blocks.ICE) || isStillWater(state);
    }

    private double icePoint(Visit visit, int column) {
        if (visit.icePoints == null) {
            double[] cached = visit.points.icePoints(level.getGameTime());
            if (cached == null) {
                cached = icePoints(visit.chunk);
                visit.points.setIcePoints(cached, level.getGameTime());
            }
            visit.icePoints = cached;
        }
        return visit.icePoints[column];
    }

    /**
     * While a climate's snow is melting (it is warm for it now), the last sub-season in which it was cold: where it
     * was cold enough then, the season's snow and ice stay until the shares and the ice cover fall past the column.
     * Null while the climate holds its snow.
     */
    private @Nullable SubSeason lastColdSubSeason(Climate base) {
        if (!coldBefore.containsKey(base)) {
            SubSeason before = null;
            if (state.melting(base)) {
                before = state.subSeason();
                for (int step = 0; step < SubSeason.COUNT && !SnowLog.holdsSnow(base, before); step++) {
                    before = before.previous();
                }
            }
            coldBefore.put(base, before);
        }
        return coldBefore.get(base);
    }

    /** Whether the season's snow belongs at a spot: cold enough now, or in a thaw, in the last cold sub-season. */
    private boolean snowBelongs(BiomeClimate entry, BlockPos pos) {
        if (entry.coldEnoughToSnow(state.subSeason(), pos, seaLevel)) {
            return true;
        }
        SubSeason before = lastColdSubSeason(entry.base());
        return before != null && entry.coldEnoughToSnow(before, pos, seaLevel);
    }

    /** Moves one column's surface snow and ice to the current state. */
    private void column(Visit visit, int column) {
        LevelChunk chunk = visit.chunk;
        int localX = column & 15;
        int localZ = column >> 4;
        BlockPos snowPos = snowSpot(chunk, localX, localZ);
        if (snowPos == null) {
            return;
        }
        Biome biome = level.getBiome(snowPos).value();
        BiomeClimate entry = climate.entry(biome);
        if (entry == null) {
            return;
        }
        Climate base = entry.base();
        visit.climates.add(base);
        SnowArea area = SnowArea.of(base);
        BlockPos groundPos = snowPos.below();
        BlockState ground = chunk.getBlockState(groundPos);

        boolean modIce = ground.is(Blocks.ICE) && visit.record.isModIce(ChunkSnow.pack(groundPos));
        boolean water = !ground.is(Blocks.ICE) && entry.seasonalIce() && isStillWater(ground);
        if (modIce || water) {
            boolean belongs = snowBelongs(entry, groundPos) && darkEnough(groundPos);
            boolean reached = icePoint(visit, column) < state.ice(area.climate());
            if (modIce && SnowRules.iceMelts(true, entry.seasonalIce(), belongs, reached, !state.melting(base))) {
                meltIce(visit, groundPos);
                return;
            }
            if (water) {
                if (!SnowRules.freezes(belongs, reached)) {
                    return;
                }
                setBlock(visit, groundPos, Blocks.ICE.defaultBlockState());
                visit.record.setModIce(ChunkSnow.pack(groundPos), !biome.coldEnoughToSnow(groundPos, seaLevel));
                ground = Blocks.ICE.defaultBlockState();
            }
        }
        snowAt(visit, snowPos, ground, biome, entry, area, visit.points.point(column), true);
    }

    /**
     * Brings the recorded spots the column pass doesn't reach up to date (snow and ice something was built over), and
     * forgets records whose block is gone. Spots on top of their column were handled by {@link #column}.
     */
    private void records(Visit visit) {
        LevelChunk chunk = visit.chunk;
        ChunkSnow record = visit.record;
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
            BiomeClimate entry = climate.entry(biome);
            if (entry != null) {
                visit.climates.add(entry.base());
                int column = ChunkSnow.localX(key) | ChunkSnow.localZ(key) << 4;
                snowAt(visit, pos, chunk.getBlockState(pos.below()), biome, entry,
                        SnowArea.of(entry.base()), visit.points.point(column), false);
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
            BiomeClimate entry = climate.entry(level.getBiome(pos).value());
            if (entry != null) {
                // Ice something was built over has no shore to go by; its column's snow point paces it instead.
                int column = ChunkSnow.localX(key) | ChunkSnow.localZ(key) << 4;
                SnowArea area = SnowArea.of(entry.base());
                boolean reached = visit.points.point(column) < state.ice(area.climate());
                if (SnowRules.iceMelts(true, entry.seasonalIce(), snowBelongs(entry, pos), reached,
                        !state.melting(entry.base()))) {
                    meltIce(visit, pos);
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

    /**
     * @param point  the column's snow point
     * @param mayAdd the spot is the top of its column, so snow may come; recorded spots under something only lose it
     */
    private void snowAt(Visit visit, BlockPos pos, BlockState ground, Biome biome, BiomeClimate entry, SnowArea area,
                        double point, boolean mayAdd) {
        LevelChunk chunk = visit.chunk;
        ChunkSnow record = visit.record;
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
        int target = state.shares(area.climate()).layersAt(point);
        // Most columns, most of the year: no snow, and none to come. The checks below are the costly ones.
        if (layers == 0 && (!mayAdd || target == 0)) {
            return;
        }
        boolean belongs = snowBelongs(entry, pos);
        if (layers == 0 && !belongs) {
            return;
        }
        boolean canHold = mayAdd
                && Blocks.SNOW.defaultBlockState().canSurvive(level, pos)
                && darkEnough(pos);
        SnowRules.SnowSpot spot = new SnowRules.SnowSpot(entry.base(), belongs, biome.coldEnoughToSnow(pos, seaLevel),
                layers, record.modSnowLayers(key), record.isPlayerSnow(key), canHold,
                ground.is(SnowTracking.NATURAL_SNOW_GROUND));
        SnowRules.Outcome outcome = SnowRules.snow(spot, target, !state.melting(entry.base()), state.nativeSnowMelts(),
                state.naturalHeight());
        if (outcome.layers() != layers) {
            setSnowLayers(visit, pos, here, outcome.layers());
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
    }

    /** Turns the season's ice back into water, clearing snow that lay on it. A player's snow keeps it frozen. */
    private void meltIce(Visit visit, BlockPos icePos) {
        LevelChunk chunk = visit.chunk;
        ChunkSnow record = visit.record;
        BlockPos above = icePos.above();
        BlockState aboveState = chunk.getBlockState(above);
        int aboveKey = ChunkSnow.pack(above);
        if (aboveState.is(BlockTags.SNOW)) {
            if (record.isPlayerSnow(aboveKey) || !aboveState.is(Blocks.SNOW)) {
                return;
            }
            setBlock(visit, above, Blocks.AIR.defaultBlockState());
            record.forget(aboveKey);
        }
        setBlock(visit, icePos, IceBlock.meltsInto());
        record.setModIce(ChunkSnow.pack(icePos), false);
        chunk.markUnsaved();
    }

    private void setSnowLayers(Visit visit, BlockPos pos, BlockState before, int layers) {
        BlockState after = layers == 0
                ? Blocks.AIR.defaultBlockState()
                : Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers);
        if (layers > 0 && before.is(Blocks.SNOW)) {
            Block.pushEntitiesUp(before, after, level, pos);
        }
        setBlock(visit, pos, after);
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (belowState.hasProperty(BlockStateProperties.SNOWY)) {
            boolean snowy = after.is(BlockTags.SNOW);
            if (belowState.getValue(BlockStateProperties.SNOWY) != snowy) {
                setBlock(visit, below, belowState.setValue(BlockStateProperties.SNOWY, snowy));
            }
        }
    }

    private void setBlock(Visit visit, BlockPos pos, BlockState block) {
        level.setBlock(pos, block, FLAGS);
        visit.record.leftLastState();
        visit.changed = true;
    }

    private boolean darkEnough(BlockPos pos) {
        return level.getBrightness(LightLayer.BLOCK, pos) < MAX_BLOCK_LIGHT;
    }

    private static boolean isStillWater(BlockState state) {
        return state.getBlock() instanceof LiquidBlock && state.getFluidState().is(Fluids.WATER);
    }
}
