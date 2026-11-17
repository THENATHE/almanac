package com.thenathe.almanac.mixin;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.thenathe.almanac.biome.HiddenCopies;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.arguments.ResourceOrTagArgument;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** {@code /locate biome}, {@code /execute if biome} and {@code /fillbiome}'s filter never take a seasonal copy. */
@Mixin(ResourceOrTagArgument.class)
abstract class ResourceOrTagArgumentMixin<T> {
    @Shadow
    @Final
    private ResourceKey<? extends Registry<T>> registryKey;

    @Inject(method = "parse(Lcom/mojang/brigadier/StringReader;)Lnet/minecraft/commands/arguments/ResourceOrTagArgument$Result;",
            at = @At("RETURN"))
    private void almanac$rejectCopies(StringReader reader, CallbackInfoReturnable<ResourceOrTagArgument.Result<T>> callback)
            throws CommandSyntaxException {
        var element = callback.getReturnValue().unwrap().left();
        if (element.isPresent()) {
            HiddenCopies.rejectCopy(registryKey, element.get().key(), reader);
        }
    }

    @Inject(method = "listSuggestions", at = @At("RETURN"), cancellable = true)
    private <S> void almanac$hideCopies(CommandContext<S> context, SuggestionsBuilder builder,
                                        CallbackInfoReturnable<CompletableFuture<Suggestions>> callback) {
        callback.setReturnValue(HiddenCopies.withoutCopies(registryKey, callback.getReturnValue()));
    }
}
