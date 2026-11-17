package com.thenathe.almanac.client.colour;

import net.minecraft.world.level.ColorResolver;

/**
 * The game's grass, foliage and dry-foliage resolvers with the season added. A biome Almanac knows gets its colour
 * from the current {@link ColourTable}; anything else, and everything while not on an Almanac server, gets the
 * original resolver's answer.
 */
public final class SeasonalResolvers {
    private SeasonalResolvers() {
    }

    public static ColorResolver grass(ColorResolver original) {
        return (biome, x, z) -> {
            int[] colours = ClientColours.table().colours(biome);
            if (colours == null) {
                return original.getColor(biome, x, z);
            }
            // The season changes the base colour; dark forests and swamps then apply their own modifier, as the
            // biome copies do for players without Almanac.
            return biome.getSpecialEffects().grassColorModifier().modifyColor(x, z, colours[ColourTable.GRASS]);
        };
    }

    public static ColorResolver foliage(ColorResolver original) {
        return (biome, x, z) -> {
            int[] colours = ClientColours.table().colours(biome);
            return colours == null ? original.getColor(biome, x, z) : colours[ColourTable.FOLIAGE];
        };
    }

    public static ColorResolver dryFoliage(ColorResolver original) {
        return (biome, x, z) -> {
            int[] colours = ClientColours.table().colours(biome);
            return colours == null ? original.getColor(biome, x, z) : colours[ColourTable.DRY_FOLIAGE];
        };
    }
}
