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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Registers the seasonal biome copies while the server loads its biome registry, after every datapack has had its
 * say and before the registry freezes. Copies live in memory only: they aren't in any biome source, so world
 * generation never places them, and the chunk packets for players without Almanac are the only place they appear.
 *
 * <p>Copies join every tag their original is in, so a vanilla client treats a copy exactly as the original. The tag
 * files don't list them, so they are added again whenever a reload rebuilds the tags ({@link #reloadedTags}).
 */
public final class SeasonalBiomeRegistration {
    private static final Logger LOGGER = LoggerFactory.getLogger(Almanac.MOD_ID);
    private static final Set<String> OTHER_DIMENSIONS = Set.of("minecraft:is_nether", "minecraft:is_end", "c:is_nether", "c:is_end");
    private static volatile SeasonalCopies.@Nullable CopyClimate copyClimate;
    /** The weather and colours each copy was made with, to notice another mod changing them afterwards. */
    private static volatile Map<ResourceKey<Biome>, CopyLook> madeCopies = Map.of();

    private SeasonalBiomeRegistration() {
    }

    /**
     * The climate settings the copies were last built with. They are read when the server loads its registries, so a
     * change to them only counts after a restart.
     */
    public static SeasonalCopies.CopyClimate copyClimate() {
        SeasonalCopies.CopyClimate built = copyClimate;
        return built != null ? built : SeasonalCopies.CopyClimate.of(ServerConfig.get());
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

        // A world is being loaded, which is when a server starts: hand edits to the file since the last one count.
        ServerConfig.reload();
        SeasonalCopies.CopyClimate climate = SeasonalCopies.CopyClimate.of(ServerConfig.get());
        copyClimate = climate;
        SeasonalPalette palette = SeasonalPalette.bundled();

        List<Map.Entry<Biome, Holder.Reference<Biome>>> originals = new ArrayList<>(registered.entrySet());
        originals.sort(Comparator.comparing(entry -> entry.getValue().key().identifier()));
        Map<ResourceKey<Biome>, List<Holder<Biome>>> copiesByOriginal = new HashMap<>();
        Map<ResourceKey<Biome>, CopyLook> looks = new HashMap<>();
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
                ResourceKey<Biome> copyKey = ResourceKey.create(Registries.BIOME, copyId);
                Biome copy = copies.get(subSeason.ordinal());
                holders.add(registry.register(copyKey, copy, RegistrationInfo.BUILT_IN));
                looks.put(copyKey, CopyLook.of(copy));
            }
            copiesByOriginal.put(key, holders);
            made += SubSeason.COUNT;
        }

        madeCopies = Map.copyOf(looks);
        LOGGER.info("Made {} seasonal biome copies of {} biomes, for players without Almanac", made, copiesByOriginal.size());
        return withCopiesInTags(pendingTags, copiesByOriginal);
    }

    /**
     * Every tag with each original's copies added beside it. The one rule for both the first load and reloads; a copy
     * already in a tag isn't added twice.
     */
    public static Map<TagKey<Biome>, List<Holder<Biome>>> withCopiesInTags(Map<TagKey<Biome>, List<Holder<Biome>>> tags,
                                                                          Map<ResourceKey<Biome>, List<Holder<Biome>>> copiesByOriginal) {
        Map<TagKey<Biome>, List<Holder<Biome>>> withCopies = new HashMap<>();
        tags.forEach((tag, members) -> {
            List<Holder<Biome>> extended = new ArrayList<>(members);
            Set<Holder<Biome>> present = Collections.newSetFromMap(new IdentityHashMap<>());
            present.addAll(members);
            for (Holder<Biome> member : members) {
                List<Holder<Biome>> copies = member.unwrapKey().map(copiesByOriginal::get).orElse(List.of());
                for (Holder<Biome> copy : copies) {
                    if (present.add(copy)) {
                        extended.add(copy);
                    }
                }
            }
            withCopies.put(tag, List.copyOf(extended));
        });
        return withCopies;
    }

    /**
     * The tags a reload ({@code /reload}, {@code /datapack enable|disable}) read from the tag files, with the copies
     * already in the registry put back beside their originals. The copies themselves aren't remade: their climate and
     * the climates the server sorted biomes into stay as they were at the start, so changes to the climate tags
     * ({@code #almanac:climate/*} and the conventional tags) apply after a restart.
     */
    public static Map<TagKey<Biome>, List<Holder<Biome>>> reloadedTags(Registry<Biome> registry,
                                                                      Map<TagKey<Biome>, List<Holder<Biome>>> tags) {
        Map<ResourceKey<Biome>, List<Holder<Biome>>> copies = copiesIn(registry);
        if (copies.isEmpty()) {
            return tags;
        }
        LOGGER.info("Almanac: kept the seasonal copies of {} biomes in their tags after a reload; changes to climate tags "
                + "apply after a restart", copies.size());
        return withCopiesInTags(tags, copies);
    }

    /** The copies registered for each original biome, in sub-season order. */
    static Map<ResourceKey<Biome>, List<Holder<Biome>>> copiesIn(Registry<Biome> registry) {
        Map<ResourceKey<Biome>, Holder<Biome>[]> bySubSeason = new HashMap<>();
        registry.listElements().forEach(holder -> CopyNames.parse(holder.key().identifier()).ifPresent(copy ->
                bySubSeason.computeIfAbsent(ResourceKey.create(Registries.BIOME, copy.original()), ignored -> newArray())
                        [copy.subSeason().ordinal()] = holder));
        Map<ResourceKey<Biome>, List<Holder<Biome>>> copies = new HashMap<>();
        bySubSeason.forEach((original, holders) ->
                copies.put(original, Arrays.stream(holders).filter(Objects::nonNull).toList()));
        return copies;
    }

    @SuppressWarnings("unchecked")
    private static Holder<Biome>[] newArray() {
        return new Holder[SubSeason.COUNT];
    }

    /**
     * Warns, once at server start, about copies whose weather or seasonal colours another mod changed after they were
     * made: typically through Fabric's biome modification API with a selector by tag, which reaches the copies since
     * they carry their originals' tags. Those copies no longer have the season's weather for players without Almanac.
     */
    public static void checkCopiesUnchanged(Registry<Biome> registry) {
        List<String> changed = new ArrayList<>();
        madeCopies.forEach((key, made) -> registry.get(key).ifPresent(holder -> {
            if (!made.equals(CopyLook.of(holder.value()))) {
                changed.add(key.identifier().toString());
            }
        }));
        if (changed.isEmpty()) {
            return;
        }
        changed.sort(Comparator.naturalOrder());
        LOGGER.warn("Almanac: another mod changed the weather or colours of {} seasonal biome copies after they were made "
                + "(for example {}), probably with Fabric's biome modification API and a selector by biome tag, which "
                + "reaches the copies too. Players without Almanac lose the season's weather or colours in those biomes. "
                + "That mod's modification should leave biomes in the almanac namespace alone.", changed.size(),
                String.join(", ", changed.subList(0, Math.min(3, changed.size()))));
    }

    /** What the season decides about a copy: whether anything falls, how warm it is and its three colours. */
    record CopyLook(boolean hasPrecipitation, float temperature, Optional<Integer> grass, Optional<Integer> foliage,
                    Optional<Integer> dryFoliage) {
        static CopyLook of(Biome biome) {
            BiomeSpecialEffects effects = biome.getSpecialEffects();
            return new CopyLook(biome.hasPrecipitation(), biome.getBaseTemperature(), effects.grassColorOverride(),
                    effects.foliageColorOverride(), effects.dryFoliageColorOverride());
        }
    }
}
