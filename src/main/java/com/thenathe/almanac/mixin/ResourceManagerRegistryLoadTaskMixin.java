package com.thenathe.almanac.mixin;

import com.thenathe.almanac.biome.LoadingContext;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceManagerRegistryLoadTask;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remembers the registry context a datapack registry is read with; only the server reads registries this way. */
@Mixin(ResourceManagerRegistryLoadTask.class)
abstract class ResourceManagerRegistryLoadTaskMixin implements LoadingContext {
    @Unique
    private RegistryOps.@Nullable RegistryInfoLookup almanac$context;

    @Inject(method = "load", at = @At("HEAD"))
    private void almanac$rememberContext(RegistryOps.RegistryInfoLookup context, Executor executor,
                                         CallbackInfoReturnable<CompletableFuture<?>> callback) {
        almanac$context = context;
    }

    @Override
    public RegistryOps.@Nullable RegistryInfoLookup almanac$registryContext() {
        return almanac$context;
    }
}
