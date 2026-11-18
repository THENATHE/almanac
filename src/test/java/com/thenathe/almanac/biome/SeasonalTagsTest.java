package com.thenathe.almanac.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.mojang.serialization.Lifecycle;
import com.thenathe.almanac.api.SubSeason;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** The copies sit beside their originals in every tag, on the first load and again after a reload (E2). */
class SeasonalTagsTest {
    private static final TagKey<Biome> IS_FOREST = TagKey.create(Registries.BIOME, Identifier.parse("minecraft:is_forest"));
    private static final TagKey<Biome> IS_DESERT = TagKey.create(Registries.BIOME, Identifier.parse("c:is_desert"));
    private static HolderLookup.RegistryLookup<Biome> vanilla;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        vanilla = VanillaRegistries.createWorldLookup().lookupOrThrow(Registries.BIOME);
    }

    /** A registry holding plains, forest and desert, and the plains' copies for two sub-seasons. */
    private static MappedRegistry<Biome> registryWithPlainsCopies() {
        MappedRegistry<Biome> registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        for (ResourceKey<Biome> key : List.of(Biomes.PLAINS, Biomes.FOREST, Biomes.DESERT)) {
            registry.register(key, vanilla.getOrThrow(key).value(), RegistrationInfo.BUILT_IN);
        }
        // A registry takes each value once, so two other biomes stand in for the copies.
        registry.register(ResourceKey.create(Registries.BIOME, CopyNames.copyOf(Biomes.PLAINS.identifier(), SubSeason.MID_WINTER)),
                vanilla.getOrThrow(Biomes.SNOWY_PLAINS).value(), RegistrationInfo.BUILT_IN);
        registry.register(ResourceKey.create(Registries.BIOME, CopyNames.copyOf(Biomes.PLAINS.identifier(), SubSeason.EARLY_SPRING)),
                vanilla.getOrThrow(Biomes.MEADOW).value(), RegistrationInfo.BUILT_IN);
        return registry;
    }

    @Test
    void copiesFoundInARegistryComeInSubSeasonOrder() {
        MappedRegistry<Biome> registry = registryWithPlainsCopies();
        Map<ResourceKey<Biome>, List<Holder<Biome>>> copies = SeasonalBiomeRegistration.copiesIn(registry);
        assertEquals(1, copies.size());
        List<String> names = copies.get(Biomes.PLAINS).stream()
                .map(holder -> holder.unwrapKey().orElseThrow().identifier().toString()).toList();
        assertEquals(List.of("almanac:early_spring/minecraft/plains", "almanac:mid_winter/minecraft/plains"), names);
    }

    @Test
    void aRebuiltTagGetsItsCopiesBackOnceAndOthersStayAsTheyAre() {
        MappedRegistry<Biome> registry = registryWithPlainsCopies();
        Holder<Biome> plains = registry.get(Biomes.PLAINS).orElseThrow();
        Holder<Biome> forest = registry.get(Biomes.FOREST).orElseThrow();
        Holder<Biome> desert = registry.get(Biomes.DESERT).orElseThrow();
        // What a reload reads from the tag files: no copies anywhere.
        Map<TagKey<Biome>, List<Holder<Biome>>> fromFiles = Map.of(IS_FOREST, List.of(forest, plains), IS_DESERT, List.of(desert));

        Map<ResourceKey<Biome>, List<Holder<Biome>>> copies = SeasonalBiomeRegistration.copiesIn(registry);
        Map<TagKey<Biome>, List<Holder<Biome>>> rebuilt = SeasonalBiomeRegistration.withCopiesInTags(fromFiles, copies);
        assertEquals(List.of(forest, plains, copies.get(Biomes.PLAINS).get(0), copies.get(Biomes.PLAINS).get(1)),
                rebuilt.get(IS_FOREST));
        assertEquals(List.of(desert), rebuilt.get(IS_DESERT));
        // Tags that already hold the copies (a client's, say) don't get them twice.
        assertEquals(rebuilt, SeasonalBiomeRegistration.withCopiesInTags(rebuilt, copies));
    }

    @Test
    void aCopyChangedByAnotherModLooksDifferent() {
        Biome plains = vanilla.getOrThrow(Biomes.PLAINS).value();
        Biome desert = vanilla.getOrThrow(Biomes.DESERT).value();
        assertEquals(SeasonalBiomeRegistration.CopyLook.of(plains), SeasonalBiomeRegistration.CopyLook.of(plains));
        // Desert has no precipitation and another temperature: what a weather modification would do to a copy.
        assertNotEquals(SeasonalBiomeRegistration.CopyLook.of(plains), SeasonalBiomeRegistration.CopyLook.of(desert));
        assertFalse(SeasonalBiomeRegistration.CopyLook.of(desert).hasPrecipitation());
    }
}
