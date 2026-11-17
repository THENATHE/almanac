package com.thenathe.almanac.colour;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thenathe.almanac.api.SubSeason;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Seasonal grass, foliage, dry-foliage and leaf colours, read from {@code assets/almanac/palette.json}.
 *
 * <p>Each sub-season changes a colour with a {@link Step}; a sub-season that isn't listed keeps it. The {@code biomes}
 * list replaces whole channels for matching biomes, matched by biome ID or {@code #tag}; the first entry that matches
 * and has the channel wins, and everything else uses {@code default}.
 *
 * <p>The {@code leaves} list is for clients with Almanac, which can colour each kind of leaf on its own. A leaf block
 * is matched by block ID first, then by tag, then by its tint: see {@link #leafSteps}.
 */
public final class SeasonalPalette {
    public static final String RESOURCE = "/assets/almanac/palette.json";

    private final Map<ColourChannel, Map<SubSeason, Step>> defaults;
    private final List<BiomeEntry> biomes;
    private final List<LeafEntry> leaves;

    private SeasonalPalette(Map<ColourChannel, Map<SubSeason, Step>> defaults, List<BiomeEntry> biomes, List<LeafEntry> leaves) {
        this.defaults = defaults;
        this.biomes = biomes;
        this.leaves = leaves;
    }

    /** The palette bundled with the mod. */
    public static SeasonalPalette bundled() {
        try (InputStream stream = SeasonalPalette.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("The mod is missing " + RESOURCE);
            }
            return read(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException failure) {
            throw new IllegalStateException("Couldn't read " + RESOURCE, failure);
        }
    }

    public static SeasonalPalette read(Reader reader) {
        return parse(JsonParser.parseReader(reader).getAsJsonObject());
    }

    public static SeasonalPalette parse(JsonObject json) {
        Map<ColourChannel, Map<SubSeason, Step>> defaults = channels(json.getAsJsonObject("default"));
        List<BiomeEntry> biomes = new ArrayList<>();
        if (json.has("biomes")) {
            for (JsonElement element : json.getAsJsonArray("biomes")) {
                JsonObject entry = element.getAsJsonObject();
                biomes.add(new BiomeEntry(matchList(entry), channels(entry)));
            }
        }
        List<LeafEntry> leaves = new ArrayList<>();
        if (json.has("leaves")) {
            for (JsonElement element : json.getAsJsonArray("leaves")) {
                leaves.add(LeafEntry.parse(element.getAsJsonObject()));
            }
        }
        return new SeasonalPalette(defaults, List.copyOf(biomes), List.copyOf(leaves));
    }

    /** The step for one biome, channel and sub-season. {@code hasTag} takes tag IDs without the {@code #}. */
    public Step step(String biomeId, Predicate<String> hasTag, ColourChannel channel, SubSeason subSeason) {
        for (BiomeEntry entry : biomes) {
            Map<SubSeason, Step> steps = entry.channels().get(channel);
            if (steps != null && matches(entry.match(), biomeId, hasTag)) {
                return steps.getOrDefault(subSeason, Step.UNCHANGED);
            }
        }
        return defaultSteps(channel).step(subSeason);
    }

    /**
     * What a leaf block does through the year, on top of the colour its own tint gives it. An entry naming the
     * block's ID wins, then the first entry with one of its tags; a block no entry matches is left alone.
     *
     * <ul>
     *   <li>An untinted block has no tint to change, so it always stays as it is.</li>
     *   <li>{@code "steps"}: the entry's own steps.</li>
     *   <li>{@code "follow": "foliage"}: the biome foliage palette. A block whose tint reads the biome's foliage or
     *       grass colour already follows it, so it needs nothing more; a block with a fixed tint gets the default
     *       foliage steps.</li>
     *   <li>{@code "keep": true}: unchanged.</li>
     * </ul>
     */
    public LeafSteps leafSteps(String blockId, Predicate<String> hasTag, LeafTint tint) {
        if (tint == LeafTint.UNTINTED) {
            return LeafSteps.UNCHANGED;
        }
        LeafEntry entry = null;
        for (LeafEntry candidate : leaves) {
            if (candidate.match().contains(blockId)) {
                entry = candidate;
                break;
            }
        }
        if (entry == null) {
            for (LeafEntry candidate : leaves) {
                if (matches(candidate.match().stream().filter(id -> id.startsWith("#")).toList(), blockId, hasTag)) {
                    entry = candidate;
                    break;
                }
            }
        }
        if (entry == null) {
            return LeafSteps.UNCHANGED;
        }
        return switch (entry.mode()) {
            case STEPS -> entry.steps();
            case FOLLOW_FOLIAGE -> tint == LeafTint.FIXED ? defaultSteps(ColourChannel.FOLIAGE) : LeafSteps.UNCHANGED;
            case KEEP -> LeafSteps.UNCHANGED;
        };
    }

    /** Every block ID the leaf list names directly, for clients that need to know which blocks to watch. */
    public List<String> namedLeafBlocks() {
        List<String> named = new ArrayList<>();
        for (LeafEntry entry : leaves) {
            entry.match().stream().filter(id -> !id.startsWith("#")).forEach(named::add);
        }
        return named;
    }

    private LeafSteps defaultSteps(ColourChannel channel) {
        Map<SubSeason, Step> steps = defaults.get(channel);
        return steps == null ? LeafSteps.UNCHANGED : new LeafSteps(steps);
    }

    private static boolean matches(List<String> match, String id, Predicate<String> hasTag) {
        for (String entry : match) {
            if (entry.startsWith("#") ? hasTag.test(entry.substring(1)) : entry.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> matchList(JsonObject entry) {
        List<String> match = new ArrayList<>();
        for (JsonElement id : entry.getAsJsonArray("match")) {
            match.add(id.getAsString());
        }
        return List.copyOf(match);
    }

    private static Map<ColourChannel, Map<SubSeason, Step>> channels(JsonObject json) {
        Map<ColourChannel, Map<SubSeason, Step>> channels = new EnumMap<>(ColourChannel.class);
        if (json == null) {
            return channels;
        }
        for (ColourChannel channel : ColourChannel.values()) {
            JsonObject steps = json.getAsJsonObject(channel.paletteKey());
            if (steps != null) {
                channels.put(channel, subSeasonSteps(steps));
            }
        }
        return channels;
    }

    private static Map<SubSeason, Step> subSeasonSteps(JsonObject json) {
        Map<SubSeason, Step> bySubSeason = new EnumMap<>(SubSeason.class);
        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            SubSeason subSeason = SubSeason.byId(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown sub-season in palette: " + entry.getKey()));
            bySubSeason.put(subSeason, Step.parse(entry.getValue().getAsJsonObject()));
        }
        return bySubSeason;
    }

    private record BiomeEntry(List<String> match, Map<ColourChannel, Map<SubSeason, Step>> channels) {
    }

    /** How a leaf block's tint works, as far as the palette cares. */
    public enum LeafTint {
        /** No tint: the texture's own colours. */
        UNTINTED,
        /** The tint reads the biome's foliage or grass colour, so it already follows the season. */
        BIOME,
        /** A fixed tint, like vanilla birch and spruce. */
        FIXED
    }

    private enum LeafMode { STEPS, FOLLOW_FOLIAGE, KEEP }

    private record LeafEntry(List<String> match, LeafMode mode, LeafSteps steps) {
        static LeafEntry parse(JsonObject json) {
            List<String> match = matchList(json);
            if (json.has("steps")) {
                return new LeafEntry(match, LeafMode.STEPS, new LeafSteps(subSeasonSteps(json.getAsJsonObject("steps"))));
            }
            if (json.has("follow")) {
                String follow = json.get("follow").getAsString();
                if (!follow.equals("foliage")) {
                    throw new IllegalArgumentException("Leaves can only follow \"foliage\": " + json);
                }
                return new LeafEntry(match, LeafMode.FOLLOW_FOLIAGE, LeafSteps.UNCHANGED);
            }
            if (json.has("keep") && json.get("keep").getAsBoolean()) {
                return new LeafEntry(match, LeafMode.KEEP, LeafSteps.UNCHANGED);
            }
            throw new IllegalArgumentException("A leaf entry needs \"steps\", \"follow\": \"foliage\" or \"keep\": true: " + json);
        }
    }

    /** A leaf's step for each sub-season; sub-seasons that aren't listed keep the leaf's own colour. */
    public record LeafSteps(Map<SubSeason, Step> steps) {
        public static final LeafSteps UNCHANGED = new LeafSteps(Map.of());

        public LeafSteps {
            steps = steps.isEmpty() ? Map.of() : new EnumMap<>(steps);
        }

        public Step step(SubSeason subSeason) {
            return steps.getOrDefault(subSeason, Step.UNCHANGED);
        }

        public boolean isUnchanged() {
            return steps.values().stream().allMatch(Step::isUnchanged);
        }
    }

    /**
     * One sub-season's change to a colour: each channel multiplied, then mixed towards a colour. In the palette a
     * step is written {@code "multiply": [r, g, b]}, {@code "mix": "#rrggbb", "amount": a} (a share between 0 and 1,
     * so some of the biome's own colour shows through), both together, or {@code "color": "#rrggbb"}, which is a
     * full mix.
     */
    public record Step(float red, float green, float blue, int mixColor, float mixAmount) {
        public static final Step UNCHANGED = new Step(1.0F, 1.0F, 1.0F, 0, 0.0F);

        public static Step multiply(float red, float green, float blue) {
            return new Step(red, green, blue, 0, 0.0F);
        }

        public static Step mix(int color, float amount) {
            return new Step(1.0F, 1.0F, 1.0F, color & 0xFFFFFF, amount);
        }

        public static Step color(int color) {
            return mix(color, 1.0F);
        }

        static Step parse(JsonObject json) {
            float red = 1.0F;
            float green = 1.0F;
            float blue = 1.0F;
            int mixColor = 0;
            float mixAmount = 0.0F;
            if (json.has("multiply")) {
                JsonArray multiply = json.getAsJsonArray("multiply");
                if (multiply.size() != 3) {
                    throw new IllegalArgumentException("\"multiply\" needs three numbers: " + json);
                }
                red = multiply.get(0).getAsFloat();
                green = multiply.get(1).getAsFloat();
                blue = multiply.get(2).getAsFloat();
            }
            if (json.has("color")) {
                mixColor = parseColor(json.get("color").getAsString());
                mixAmount = 1.0F;
            } else if (json.has("mix")) {
                mixColor = parseColor(json.get("mix").getAsString());
                mixAmount = json.has("amount") ? json.get("amount").getAsFloat() : 1.0F;
                if (!(mixAmount >= 0.0F && mixAmount <= 1.0F)) {
                    throw new IllegalArgumentException("A mix \"amount\" is between 0 and 1: " + json);
                }
            } else if (!json.has("multiply")) {
                throw new IllegalArgumentException("A palette step needs \"multiply\", \"mix\" or \"color\": " + json);
            }
            return new Step(red, green, blue, mixColor, mixAmount);
        }

        public boolean isUnchanged() {
            return mixAmount == 0.0F && red == 1.0F && green == 1.0F && blue == 1.0F;
        }

        /** The seasonal colour for something whose own colour is {@code base} (RGB; any alpha is dropped). */
        public int apply(int base) {
            return channel(base >> 16, red, mixColor >> 16) << 16
                    | channel(base >> 8, green, mixColor >> 8) << 8
                    | channel(base, blue, mixColor);
        }

        private int channel(int base, float factor, int mix) {
            float multiplied = (base & 0xFF) * factor;
            float mixed = multiplied + ((mix & 0xFF) - multiplied) * mixAmount;
            return Math.clamp(Math.round(mixed), 0, 255);
        }

        private static int parseColor(String text) {
            if (!text.matches("#[0-9a-fA-F]{6}")) {
                throw new IllegalArgumentException("Palette colours are written #rrggbb: " + text);
            }
            return Integer.parseInt(text.substring(1), 16);
        }
    }
}
