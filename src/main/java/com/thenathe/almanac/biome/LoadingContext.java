package com.thenathe.almanac.biome;

import net.minecraft.resources.RegistryOps;
import org.jspecify.annotations.Nullable;

/**
 * Implemented by the registry load task that reads a datapack registry, so the biome copies can be encoded and
 * decoded with the same registry context the biomes themselves were read with.
 */
public interface LoadingContext {
    RegistryOps.@Nullable RegistryInfoLookup almanac$registryContext();
}
