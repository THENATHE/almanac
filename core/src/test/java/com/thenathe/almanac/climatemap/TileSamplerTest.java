package com.thenathe.almanac.climatemap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.mojang.datafixers.util.Pair;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The surface biome comes through the biome source's own resolver with the depth fixed at the surface's, and for a
 * plain multi-noise source that is exactly the old direct lookup (R1).
 */
class TileSamplerTest {
    private static Holder<Biome> plains;
    private static Holder<Biome> lushCaves;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        HolderLookup.RegistryLookup<Biome> biomes = VanillaRegistries.createWorldLookup().lookupOrThrow(Registries.BIOME);
        plains = biomes.getOrThrow(Biomes.PLAINS);
        lushCaves = biomes.getOrThrow(Biomes.LUSH_CAVES);
    }

    /** A density that is a fixed value everywhere. */
    private static DensitySampler.Bound constant(float value) {
        DensitySampler sampler = new DensitySampler() {
            @Override
            public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
                return value;
            }

            @Override
            public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
                DensitySampler.sampleVolumeNaive(context, outputBuffer, volume, this);
            }
        };
        return new DensitySampler.Bound(sampler, SamplerContext.EMPTY_UNCACHED);
    }

    /** Deep underground: a depth well below the surface. */
    private static Climate.Sampler underground() {
        return new Climate.Sampler(constant(0.2F), constant(-0.1F), constant(0.3F), constant(0.4F), constant(1.1F),
                constant(-0.5F));
    }

    @Test
    void onlyTheDepthChanges() {
        Climate.TargetPoint original = underground().sample(4, 20, -9);
        Climate.TargetPoint surface = TileSampler.atSurfaceDepth(underground()).sample(4, 20, -9);
        assertEquals(new Climate.TargetPoint(original.temperature(), original.humidity(), original.continentalness(),
                original.erosion(), 0L, original.weirdness()), surface);
    }

    @Test
    void theResolverGivesTheDirectLookupsSurfaceBiome() {
        // Plains at the surface, lush caves below it, anywhere else in the climate.
        Climate.ParameterPoint anywhere = Climate.parameters(Climate.Parameter.span(-1, 1), Climate.Parameter.span(-1, 1),
                Climate.Parameter.span(-1, 1), Climate.Parameter.span(-1, 1), Climate.Parameter.point(0.0F),
                Climate.Parameter.span(-1, 1), 0.0F);
        Climate.ParameterPoint below = Climate.parameters(Climate.Parameter.span(-1, 1), Climate.Parameter.span(-1, 1),
                Climate.Parameter.span(-1, 1), Climate.Parameter.span(-1, 1), Climate.Parameter.span(0.2F, 2.0F),
                Climate.Parameter.span(-1, 1), 0.0F);
        MultiNoiseBiomeSource source = MultiNoiseBiomeSource.createFromList(new Climate.ParameterList<>(List.of(
                Pair.of(anywhere, plains), Pair.of(below, lushCaves))));

        assertSame(lushCaves, source.createResolver(underground()).getNoiseBiome(4, 20, -9), "the sampler as it is");
        Holder<Biome> throughResolver = source.createResolver(TileSampler.atSurfaceDepth(underground())).getNoiseBiome(4, 20, -9);
        Climate.TargetPoint sampled = underground().sample(4, 20, -9);
        Holder<Biome> direct = source.getNoiseBiome(new Climate.TargetPoint(sampled.temperature(), sampled.humidity(),
                sampled.continentalness(), sampled.erosion(), 0L, sampled.weirdness()));
        assertSame(plains, throughResolver);
        assertSame(direct, throughResolver);
    }
}
