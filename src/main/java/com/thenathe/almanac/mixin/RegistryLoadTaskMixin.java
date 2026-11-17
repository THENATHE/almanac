package com.thenathe.almanac.mixin;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import com.thenathe.almanac.biome.LoadingContext;
import com.thenathe.almanac.biome.SeasonalBiomeRegistration;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryLoadTask;
import net.minecraft.resources.RegistryOps;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Adds the seasonal biome copies once every biome and biome tag of the server's datapacks is known, just before the
 * tags are bound. Registries a client receives from a server are left alone: the copies arrive with them.
 */
@Mixin(RegistryLoadTask.class)
abstract class RegistryLoadTaskMixin<T> {
    @Shadow
    @Final
    private Object registryWriteLock;

    @Shadow
    @Final
    private WritableRegistry<T> registry;

    @SuppressWarnings("unchecked")
    @ModifyVariable(method = "registerTags", at = @At("HEAD"), argsOnly = true)
    private Map<TagKey<T>, List<Holder<T>>> almanac$addSeasonalCopies(Map<TagKey<T>, List<Holder<T>>> pendingTags) {
        if (!registry.key().equals(Registries.BIOME) || !(this instanceof LoadingContext loading)) {
            return pendingTags;
        }
        RegistryOps.RegistryInfoLookup context = loading.almanac$registryContext();
        if (context == null || !(registry instanceof MappedRegistry<T>)) {
            return pendingTags;
        }
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, context);
        synchronized (registryWriteLock) {
            WritableRegistry<Biome> biomes = (WritableRegistry<Biome>) registry;
            Map<Biome, Holder.Reference<Biome>> registered = Map.copyOf(((MappedRegistryAccessor<Biome>) biomes).almanac$byValue());
            Object withCopies = SeasonalBiomeRegistration.addCopies(biomes, registered, ops,
                    (Map<TagKey<Biome>, List<Holder<Biome>>>) (Object) pendingTags);
            return (Map<TagKey<T>, List<Holder<T>>>) withCopies;
        }
    }
}
