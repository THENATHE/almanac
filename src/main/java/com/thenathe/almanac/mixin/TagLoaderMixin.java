package com.thenathe.almanac.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.thenathe.almanac.biome.SeasonalBiomeRegistration;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * A reload ({@code /reload}, {@code /datapack enable|disable}) rebuilds every tag of the running server's registries
 * from the tag files, which don't list the seasonal biome copies; this puts them back beside their originals before
 * the new tags are bound. Only the server reloads tags this way: clients get them from the server, copies included.
 */
@Mixin(TagLoader.class)
abstract class TagLoaderMixin {
    @SuppressWarnings("unchecked")
    @WrapOperation(method = "loadPendingTags", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/core/Registry;prepareTagReload(Lnet/minecraft/tags/TagLoader$LoadResult;)Lnet/minecraft/core/Registry$PendingTags;"))
    private static <T> Registry.PendingTags<T> almanac$keepCopiesInTags(Registry<T> registry, TagLoader.LoadResult<T> tags,
                                                                       Operation<Registry.PendingTags<T>> original) {
        if (!registry.key().equals(Registries.BIOME)) {
            return original.call(registry, tags);
        }
        Map<TagKey<Biome>, List<Holder<Biome>>> withCopies = SeasonalBiomeRegistration.reloadedTags(
                (Registry<Biome>) (Object) registry, (Map<TagKey<Biome>, List<Holder<Biome>>>) (Object) tags.tags());
        TagLoader.LoadResult<T> result = new TagLoader.LoadResult<>(tags.key(),
                (Map<TagKey<T>, List<Holder<T>>>) (Object) withCopies);
        return original.call(registry, result);
    }
}
