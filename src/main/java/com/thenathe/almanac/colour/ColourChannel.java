package com.thenathe.almanac.colour;

/** The three biome colours a season changes, named as in biome JSON and in the palette. */
public enum ColourChannel {
    GRASS("grass", "grass_color"),
    FOLIAGE("foliage", "foliage_color"),
    DRY_FOLIAGE("dry_foliage", "dry_foliage_color");

    private final String paletteKey;
    private final String biomeField;

    ColourChannel(String paletteKey, String biomeField) {
        this.paletteKey = paletteKey;
        this.biomeField = biomeField;
    }

    public String paletteKey() {
        return paletteKey;
    }

    /** The field in a biome's {@code effects} block. */
    public String biomeField() {
        return biomeField;
    }
}
