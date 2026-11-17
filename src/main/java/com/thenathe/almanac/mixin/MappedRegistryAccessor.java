package com.thenathe.almanac.mixin;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** A registry's values before it freezes, when its holders can't hand them out yet. */
@Mixin(MappedRegistry.class)
public interface MappedRegistryAccessor<T> {
    @Accessor("byValue")
    Map<T, Holder.Reference<T>> almanac$byValue();
}
