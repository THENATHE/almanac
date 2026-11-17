package com.thenathe.almanac.climatemap;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.jspecify.annotations.Nullable;

/**
 * Finds the surface biome at a column from the overworld's own generator, without loading or generating any chunk.
 *
 * <p>Two ways, measured in round 0.1.2 (docs/FAR-SNOW.md has the numbers):
 * <ul>
 *   <li>{@link Method#SURFACE_CLIMATE}, for multi-noise biome sources (vanilla's overworld and the data packs and mods
 *   built on it): one climate sample, with the depth parameter set to the surface's (0), looked up in the source's
 *   own parameter list. Biome sources place surface biomes at depth 0 and cave biomes below, so this is the surface
 *   biome without working out the surface height.</li>
 *   <li>{@link Method#BASE_HEIGHT}, for any other source: the generator's height estimate, then the biome there.</li>
 * </ul>
 * One sampler belongs to one thread (it keeps a resolver of its own); biome sources and the generator's noise are
 * built to be used from world generation's worker threads.
 */
public final class TileSampler {
    public enum Method {
        SURFACE_CLIMATE,
        BASE_HEIGHT
    }

    private final ChunkGenerator generator;
    private final BiomeSource source;
    private final RandomState randomState;
    private final LevelHeightAccessor heights;
    private final Registry<Biome> biomes;
    private final int seaLevel;
    private Climate.@Nullable Sampler climateSampler;
    private @Nullable BiomeResolver resolver;

    public TileSampler(ServerLevel level) {
        this.generator = level.getChunkSource().getGenerator();
        this.source = generator.getBiomeSource();
        this.randomState = level.getChunkSource().randomState();
        this.heights = LevelHeightAccessor.create(level.getMinY(), level.getHeight());
        this.biomes = level.registryAccess().lookupOrThrow(Registries.BIOME);
        this.seaLevel = level.getSeaLevel();
    }

    /** The way this level's biome source is sampled. */
    public Method method() {
        return source instanceof MultiNoiseBiomeSource ? Method.SURFACE_CLIMATE : Method.BASE_HEIGHT;
    }

    /** The biome network ID of each cell of a tile, row by row, sampled at the cell's centre. */
    public int[] sample(int tileX, int tileZ, int cellShift) {
        int cellSize = 1 << cellShift;
        int side = ClimateTile.SIZE >> cellShift;
        int minX = tileX << ClimateTile.SIZE_SHIFT;
        int minZ = tileZ << ClimateTile.SIZE_SHIFT;
        Method method = method();
        int[] ids = new int[side * side];
        for (int cellZ = 0; cellZ < side; cellZ++) {
            for (int cellX = 0; cellX < side; cellX++) {
                Holder<Biome> biome = surfaceBiome(minX + cellX * cellSize + cellSize / 2,
                        minZ + cellZ * cellSize + cellSize / 2, method);
                ids[cellZ * side + cellX] = biomes.getId(biome.value());
            }
        }
        return ids;
    }

    public Holder<Biome> surfaceBiome(int x, int z, Method method) {
        if (method == Method.SURFACE_CLIMATE && source instanceof MultiNoiseBiomeSource multiNoise) {
            Climate.TargetPoint sampled = climateSampler().sample(QuartPos.fromBlock(x), QuartPos.fromBlock(seaLevel),
                    QuartPos.fromBlock(z));
            return multiNoise.getNoiseBiome(new Climate.TargetPoint(sampled.temperature(), sampled.humidity(),
                    sampled.continentalness(), sampled.erosion(), 0L, sampled.weirdness()));
        }
        int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, randomState);
        return resolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(surface), QuartPos.fromBlock(z));
    }

    /** The biome at a fixed height, for comparing ways of sampling in QA. */
    public Holder<Biome> biomeAt(int x, int y, int z) {
        return resolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
    }

    private Climate.Sampler climateSampler() {
        if (climateSampler == null) {
            climateSampler = randomState.createClimateSampler(SamplerContext.EMPTY_UNCACHED);
        }
        return climateSampler;
    }

    private BiomeResolver resolver() {
        if (resolver == null) {
            resolver = source.createUncachedResolver(randomState);
        }
        return resolver;
    }
}
