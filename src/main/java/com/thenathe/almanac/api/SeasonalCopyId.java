package com.thenathe.almanac.api;

import java.util.Optional;

/**
 * The ID of a seasonal biome copy: {@code almanac:<sub_season>/<namespace>/<path>}, for example
 * {@code almanac:mid_winter/minecraft/plains} for plains in mid winter. Players without Almanac receive these biomes
 * in place of the real ones, and see the name on their debug screen.
 *
 * <p>The format is a stable contract from Almanac 0.1.1 on. This class only handles strings, so another mod can copy
 * its logic rather than depend on Almanac.
 *
 * @param subSeason the sub-season the copy stands for
 * @param original  the real biome's ID, such as {@code minecraft:plains}
 */
public record SeasonalCopyId(SubSeason subSeason, String original) {
    public static final String NAMESPACE = "almanac";

    /** Reads a copy's ID; empty for any other ID, including Almanac's own non-copy IDs. */
    public static Optional<SeasonalCopyId> parse(String id) {
        String prefix = NAMESPACE + ":";
        if (!id.startsWith(prefix)) {
            return Optional.empty();
        }
        String[] parts = id.substring(prefix.length()).split("/", 3);
        if (parts.length < 3 || !isNamespace(parts[1]) || !isPath(parts[2])) {
            return Optional.empty();
        }
        return SubSeason.byId(parts[0]).map(subSeason -> new SeasonalCopyId(subSeason, parts[1] + ":" + parts[2]));
    }

    /** The ID of a biome's copy for a sub-season; {@code original} must be a full ID such as {@code minecraft:plains}. */
    public static String of(SubSeason subSeason, String original) {
        int colon = original.indexOf(':');
        if (colon < 0 || !isNamespace(original.substring(0, colon)) || !isPath(original.substring(colon + 1))) {
            throw new IllegalArgumentException("not a biome ID: " + original);
        }
        return prefixFor(subSeason) + original.substring(0, colon) + "/" + original.substring(colon + 1);
    }

    /** The full ID, the same text {@link #parse} reads. */
    public String id() {
        return of(subSeason, original);
    }

    @Override
    public String toString() {
        return id();
    }

    private static String prefixFor(SubSeason subSeason) {
        return NAMESPACE + ":" + subSeason.id() + "/";
    }

    // The game's own rules for the parts of an ID.
    private static boolean isNamespace(String text) {
        return !text.isEmpty() && text.chars().allMatch(c -> c >= 'a' && c <= 'z' || c >= '0' && c <= '9'
                || c == '_' || c == '-' || c == '.');
    }

    private static boolean isPath(String text) {
        return !text.isEmpty() && text.chars().allMatch(c -> c >= 'a' && c <= 'z' || c >= '0' && c <= '9'
                || c == '_' || c == '-' || c == '.' || c == '/');
    }
}
