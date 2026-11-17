package com.thenathe.almanac.mixin;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.thenathe.almanac.biome.HiddenCopies;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@code /fillbiome} and other single-biome arguments never take a seasonal copy. */
@Mixin(ResourceArgument.class)
abstract class ResourceArgumentMixin<T> {
    @Shadow
    @Final
    private ResourceKey<? extends Registry<T>> registryKey;

    @Inject(method = "parse(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/core/Holder$Reference;", at = @At("RETURN"))
    private void almanac$rejectCopies(StringReader reader, CallbackInfoReturnable<Holder.Reference<T>> callback)
            throws CommandSyntaxException {
        HiddenCopies.rejectCopy(registryKey, callback.getReturnValue().key(), reader);
    }

    @Inject(method = "listSuggestions", at = @At("RETURN"), cancellable = true)
    private <S> void almanac$hideCopies(CommandContext<S> context, SuggestionsBuilder builder,
                                        CallbackInfoReturnable<CompletableFuture<Suggestions>> callback) {
        callback.setReturnValue(HiddenCopies.withoutCopies(registryKey, callback.getReturnValue()));
    }
}
