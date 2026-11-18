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
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Finds the surface biome at a column from the overworld's own generator, without loading or generating any chunk.
 *
 * <p>Two ways, measured in round 0.1.2 (docs/FAR-SNOW.md has the numbers):
 * <ul>
 *   <li>{@link Method#SURFACE_CLIMATE}, for multi-noise biome sources (vanilla's overworld and the data packs and mods
 *   built on it): one climate sample, with the depth parameter set to the surface's (0). Biome sources place surface
 *   biomes at depth 0 and cave biomes below, so this is the surface biome without working out the surface height.
 *   The sample goes through the source's own {@link BiomeSource#createResolver}, the entry point world generation
 *   and the game's biome lookups use, so mods that add biomes by hooking it (or the parameter list behind it) show on
 *   the map too; for vanilla the answer is the same as looking the sample up in the parameter list directly. If the
 *   resolver fails, that direct lookup is used from then on.</li>
 *   <li>{@link Method#BASE_HEIGHT}, for any other source: the generator's height estimate, then the biome there.</li>
 * </ul>
 * One sampler belongs to one thread (it keeps a resolver of its own); biome sources and the generator's noise are
 * built to be used from world generation's worker threads.
 */
public final class TileSampler {
    private static final Logger LOGGER = LoggerFactory.getLogger("almanac");
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
    private @Nullable BiomeResolver surfaceResolver;
    private boolean directLookup;

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
            int quartX = QuartPos.fromBlock(x);
            int quartY = QuartPos.fromBlock(seaLevel);
            int quartZ = QuartPos.fromBlock(z);
            if (!directLookup) {
                try {
                    return surfaceResolver().getNoiseBiome(quartX, quartY, quartZ);
                } catch (RuntimeException failure) {
                    directLookup = true;
                    LOGGER.warn("Almanac: the overworld's biome source failed when asked for a surface biome; the climate "
                            + "map looks samples up in its parameter list instead, so biomes added by mods may not show", failure);
                }
            }
            return multiNoise.getNoiseBiome(atSurfaceDepth(climateSampler()).sample(quartX, quartY, quartZ));
        }
        int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, randomState);
        return resolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(surface), QuartPos.fromBlock(z));
    }

    /** The biome at a fixed height, for comparing ways of sampling in QA. */
    public Holder<Biome> biomeAt(int x, int y, int z) {
        return resolver().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
    }

    /** The source's resolver for a sampler whose depth is the surface's everywhere. */
    private BiomeResolver surfaceResolver() {
        if (surfaceResolver == null) {
            surfaceResolver = source.createResolver(atSurfaceDepth(climateSampler()));
        }
        return surfaceResolver;
    }

    /**
     * The same climate sampler with its depth fixed at the surface's (0), so any height asks for the surface biome.
     * The other five parameters, and the context they are sampled in, are the sampler's own.
     */
    static Climate.Sampler atSurfaceDepth(Climate.Sampler sampler) {
        return new Climate.Sampler(sampler.temperature(), sampler.humidity(), sampler.continentalness(), sampler.erosion(),
                new DensitySampler.Bound(SURFACE_DEPTH, sampler.depth().context()), sampler.weirdness());
    }

    private static final DensitySampler SURFACE_DEPTH = new DensitySampler() {
        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return 0.0F;
        }

        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            DensitySampler.sampleVolumeNaive(context, outputBuffer, volume, this);
        }
    };

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
