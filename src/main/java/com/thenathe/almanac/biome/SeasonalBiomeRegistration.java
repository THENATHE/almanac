package com.thenathe.almanac.biome;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.thenathe.almanac.Almanac;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.climate.ClimateSorter;
import com.thenathe.almanac.climate.WeatherKind;
import com.thenathe.almanac.colour.SeasonalPalette;
import com.thenathe.almanac.config.ServerConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the seasonal biome copies while the server loads its biome registry, after every datapack has had its
 * say and before the registry freezes. Copies live in memory only: they aren't in any biome source, so world
 * generation never places them, and the chunk packets for players without Almanac are the only place they appear.
 *
 * <p>Copies join every tag their original is in, so a vanilla client treats a copy exactly as the original.
 */
public final class SeasonalBiomeRegistration {
    private static final Logger LOGGER = LoggerFactory.getLogger(Almanac.MOD_ID);
    private static final Set<String> OTHER_DIMENSIONS = Set.of("minecraft:is_nether", "minecraft:is_end", "c:is_nether", "c:is_end");

    private SeasonalBiomeRegistration() {
    }

    /**
     * Registers the copies of every biome outside the Nether and the End, and returns {@code pendingTags} with the
     * copies added beside their originals.
     */
    public static Map<TagKey<Biome>, List<Holder<Biome>>> addCopies(
            WritableRegistry<Biome> registry, Map<Biome, Holder.Reference<Biome>> registered,
            RegistryOps<JsonElement> ops, Map<TagKey<Biome>, List<Holder<Biome>>> pendingTags) {
        Map<ResourceKey<Biome>, Set<String>> tagsByBiome = new HashMap<>();
        pendingTags.forEach((tag, members) -> {
            for (Holder<Biome> member : members) {
                member.unwrapKey().ifPresent(key ->
                        tagsByBiome.computeIfAbsent(key, ignored -> new HashSet<>()).add(tag.location().toString()));
            }
        });

        ServerConfig config = ServerConfig.get();
        SeasonalCopies.CopyClimate climate = new SeasonalCopies.CopyClimate(
                (float) config.winterTemperature, (float) config.summerTemperature, config.snowLineAboveSea);
        SeasonalPalette palette = SeasonalPalette.bundled();

        List<Map.Entry<Biome, Holder.Reference<Biome>>> originals = new ArrayList<>(registered.entrySet());
        originals.sort(Comparator.comparing(entry -> entry.getValue().key().identifier()));
        Map<ResourceKey<Biome>, List<Holder<Biome>>> copiesByOriginal = new HashMap<>();
        int made = 0;
        for (Map.Entry<Biome, Holder.Reference<Biome>> entry : originals) {
            ResourceKey<Biome> key = entry.getValue().key();
            Set<String> tags = tagsByBiome.getOrDefault(key, Set.of());
            if (key.identifier().getNamespace().equals(Almanac.MOD_ID) || tags.stream().anyMatch(OTHER_DIMENSIONS::contains)) {
                continue;
            }
            Biome original = entry.getKey();
            JsonObject encoded;
            try {
                encoded = Biome.DIRECT_CODEC.encodeStart(ops, original).getOrThrow().getAsJsonObject();
            } catch (RuntimeException failure) {
                LOGGER.warn("Couldn't encode biome {}; it keeps its real climate for players without Almanac", key.identifier(), failure);
                continue;
            }
            Climate base = ClimateSorter.sort(tags::contains, original.getBaseTemperature(), original.hasPrecipitation());
            String biomeId = key.identifier().toString();

            List<Biome> copies = new ArrayList<>(SubSeason.COUNT);
            try {
                for (SubSeason subSeason : SubSeason.values()) {
                    JsonObject copyJson = SeasonalCopies.copy(encoded, WeatherKind.of(base, subSeason), climate,
                            channel -> palette.step(biomeId, tags::contains, channel, subSeason));
                    copies.add(Biome.DIRECT_CODEC.parse(ops, copyJson).getOrThrow());
                }
            } catch (RuntimeException failure) {
                LOGGER.warn("Couldn't make the seasonal copies of biome {}; it keeps its real climate for players without Almanac",
                        key.identifier(), failure);
                continue;
            }
            List<Holder<Biome>> holders = new ArrayList<>(SubSeason.COUNT);
            for (SubSeason subSeason : SubSeason.values()) {
                Identifier copyId = CopyNames.copyOf(key.identifier(), subSeason);
                holders.add(registry.register(ResourceKey.create(Registries.BIOME, copyId), copies.get(subSeason.ordinal()),
                        RegistrationInfo.BUILT_IN));
            }
            copiesByOriginal.put(key, holders);
            made += SubSeason.COUNT;
        }

        Map<TagKey<Biome>, List<Holder<Biome>>> withCopies = new HashMap<>();
        pendingTags.forEach((tag, members) -> {
            List<Holder<Biome>> extended = new ArrayList<>(members);
            for (Holder<Biome> member : members) {
                member.unwrapKey().map(copiesByOriginal::get).ifPresent(extended::addAll);
            }
            withCopies.put(tag, List.copyOf(extended));
        });
        LOGGER.info("Made {} seasonal biome copies of {} biomes, for players without Almanac", made, copiesByOriginal.size());
        return withCopies;
    }
}
