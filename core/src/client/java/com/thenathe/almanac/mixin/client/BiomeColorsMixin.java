package com.thenathe.almanac.mixin.client;

import com.thenathe.almanac.client.colour.SeasonalResolvers;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.ColorResolver;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Every biome-tinted block, in vanilla's renderer and in Sodium's alike, gets its colour through these resolvers.
 * Wrapping them, rather than the biome's own colour methods (which Sodium replaces), gives seasonal colours with the
 * fewest assumptions about who renders the world. The wrapped resolvers are in place before anything can read them.
 */
@Mixin(BiomeColors.class)
abstract class BiomeColorsMixin {
    @Shadow @Final @Mutable public static ColorResolver GRASS_COLOR_RESOLVER;
    @Shadow @Final @Mutable public static ColorResolver FOLIAGE_COLOR_RESOLVER;
    @Shadow @Final @Mutable public static ColorResolver DRY_FOLIAGE_COLOR_RESOLVER;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void almanac$seasonalResolvers(CallbackInfo info) {
        GRASS_COLOR_RESOLVER = SeasonalResolvers.grass(GRASS_COLOR_RESOLVER);
        FOLIAGE_COLOR_RESOLVER = SeasonalResolvers.foliage(FOLIAGE_COLOR_RESOLVER);
        DRY_FOLIAGE_COLOR_RESOLVER = SeasonalResolvers.dryFoliage(DRY_FOLIAGE_COLOR_RESOLVER);
    }
}
