package com.thenathe.almanac.snow;

import com.thenathe.almanac.api.Climate;

/**
 * Which snow and ice may come and go (user, 2026-11-17). The rules only look at facts about one position and its
 * column's target, so they are the same for chunks that stayed loaded, chunks that load and far terrain.
 *
 * <ul>
 *   <li>A column's target is how many layers its climate's shares give its point ({@code SnowLayers#layersAt}), where
 *   the season makes it cold enough for snow; its water freezes once the climate's ice cover is past the water's
 *   freezing point.</li>
 *   <li>While the climate is cold, snow and ice only come: the season's snow is never taken away in the cold, so snow
 *   that is ahead of the shares (vanilla's, or an older version's) waits for the thaw. In a thaw, and where a spot is
 *   too warm, the season's snow and ice go down to the target.</li>
 *   <li>Native snow in COLD biomes melts in a warm season when the server allows it, but only single layers of
 *   natural height on natural ground; native ice never melts.</li>
 *   <li>Snow players placed never melts, and nor does any other snow: snow on built blocks, snow blocks, and altitude
 *   snow (where the biome's real climate snows at that height) all stay.</li>
 * </ul>
 */
public final class SnowRules {
    private SnowRules() {
    }

    /**
     * What is known about the snow position of one column (or any recorded position).
     *
     * @param base          the biome's base climate
     * @param coldNow       the season's snow belongs here: it is cold enough now, or the climate is thawing and it was
     *                      cold enough in its last cold sub-season
     * @param realCold      the biome's own climate is cold enough for snow here
     * @param layers        snow layers here now, 0 for none
     * @param modLayers     how many of those the season added
     * @param playerPlaced  a player placed snow here
     * @param canHoldSnow   snow could lie here: the space is free or already snow, the block below holds it and it's
     *                      dark enough
     * @param naturalGround the block below is natural ground ({@code #almanac:natural_snow_ground})
     */
    public record SnowSpot(Climate base, boolean coldNow, boolean realCold, int layers, int modLayers,
                           boolean playerPlaced, boolean canHoldSnow, boolean naturalGround) {
    }

    /** The layers a position should have and how many of them are the season's. */
    public record Outcome(int layers, int modLayers) {
    }

    /**
     * Moves the snow at a spot to its target.
     *
     * @param target  the layers the column's point gets from its climate's shares, used where the snow belongs
     * @param holding the climate is cold this sub-season, so its snow only comes
     */
    public static Outcome snow(SnowSpot spot, int target, boolean holding, boolean nativeSnowMelts, int naturalHeight) {
        int wanted = spot.coldNow() ? target : 0;
        int layers = spot.layers();
        int modLayers = Math.min(spot.modLayers(), layers);
        if (layers < wanted) {
            if (!spot.canHoldSnow() || spot.playerPlaced()) {
                return new Outcome(layers, modLayers);
            }
            // Where the biome's own climate snows, the first layers are what vanilla's snowfall would bring: native.
            // Everything deeper is the season's, so it can melt again.
            int nativeLayers = layers - modLayers;
            if (spot.realCold()) {
                nativeLayers = Math.max(nativeLayers, Math.min(wanted, naturalHeight));
            }
            return new Outcome(wanted, wanted - nativeLayers);
        }
        if (layers > wanted) {
            if (holding && spot.coldNow()) {
                return new Outcome(layers, modLayers);
            }
            int removable = modLayers;
            if (!spot.playerPlaced() && nativeMayMelt(spot, nativeSnowMelts, naturalHeight)) {
                removable = layers;
            }
            int removed = Math.min(layers - wanted, removable);
            return new Outcome(layers - removed, Math.max(0, modLayers - removed));
        }
        return new Outcome(layers, modLayers);
    }

    /**
     * Native snow melts only in COLD biomes, only while it's warm there, and only as thin snow on natural ground:
     * anything deeper, or on built blocks, is someone's work. Altitude snow in other biomes (where their real climate
     * snows at that height) is never native snow of a COLD biome, so it always stays.
     */
    static boolean nativeMayMelt(SnowSpot spot, boolean nativeSnowMelts, int naturalHeight) {
        int nativeLayers = spot.layers() - spot.modLayers();
        return nativeSnowMelts
                && spot.base() == Climate.COLD
                && !spot.coldNow()
                && spot.naturalGround()
                && nativeLayers <= naturalHeight;
    }

    /**
     * Whether still water at the top of a column freezes now.
     *
     * @param iceBelongs the season makes the water cold enough to freeze: now, or in its climate's last cold
     *                   sub-season while it thaws; and it is dark enough
     * @param reached    the climate's ice cover is past the water's freezing point
     */
    public static boolean freezes(boolean iceBelongs, boolean reached) {
        return iceBelongs && reached;
    }

    /**
     * Whether the season's ice at the top of a column melts now. Native ice never does; nor does the season's while
     * its climate is cold and the spot is too, whatever the cover says, so ice is never taken away in the cold.
     *
     * @param seasonalIceBiome the biome gets seasonal ice at all (oceans don't)
     */
    public static boolean iceMelts(boolean modIce, boolean seasonalIceBiome, boolean iceBelongs, boolean reached,
                                   boolean holding) {
        if (!modIce) {
            return false;
        }
        if (!seasonalIceBiome || !iceBelongs) {
            return true;
        }
        return !holding && !reached;
    }
}
