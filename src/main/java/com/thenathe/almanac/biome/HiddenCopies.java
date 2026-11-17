package com.thenathe.almanac.biome;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * Keeps the biome copies out of biome commands: they are never suggested and never accepted, so {@code /fillbiome}
 * can't put one into the world and {@code /locate} doesn't offer them.
 */
public final class HiddenCopies {
    private HiddenCopies() {
    }

    public static void rejectCopy(ResourceKey<? extends Registry<?>> registry, ResourceKey<?> element, StringReader reader)
            throws CommandSyntaxException {
        if (registry.equals(Registries.BIOME) && CopyNames.isCopy(element.identifier())) {
            throw ResourceArgument.ERROR_UNKNOWN_RESOURCE.createWithContext(reader, element.identifier(), registry.identifier());
        }
    }

    public static CompletableFuture<Suggestions> withoutCopies(ResourceKey<? extends Registry<?>> registry,
                                                               CompletableFuture<Suggestions> suggestions) {
        if (!registry.equals(Registries.BIOME)) {
            return suggestions;
        }
        return suggestions.thenApply(all -> {
            List<Suggestion> kept = all.getList().stream().filter(suggestion -> {
                Identifier id = Identifier.tryParse(suggestion.getText());
                return id == null || !CopyNames.isCopy(id);
            }).toList();
            return kept.size() == all.getList().size() ? all : new Suggestions(all.getRange(), kept);
        });
    }
}
