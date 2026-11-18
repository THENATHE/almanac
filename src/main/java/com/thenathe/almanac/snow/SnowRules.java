package com.thenathe.almanac.snow;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import java.util.EnumSet;
import java.util.Set;

/**
 * Which snow and ice may come and go (user, 2026-11-17). The rules only look at facts about one position, so they
 * are the same for chunks brought up to date on load and for the gradual work in loaded chunks.
 *
 * <ul>
 *   <li>Snow and ice the season placed melt when it's warm there.</li>
 *   <li>Native snow in COLD biomes melts in a warm season when the server allows it, but only single layers of
 *   natural height on natural ground; native ice never melts.</li>
 *   <li>Snow players placed never melts, and nor does any other snow: snow on built blocks, deep snow, snow blocks,
 *   and altitude snow (where the biome's real climate snows at that height) all stay.</li>
 * </ul>
 */
public final class SnowRules {
    /**
     * Thaw time (user, 2026-11-17): the sub-seasons in which a chunk brought up to date on load keeps the snow and ice
     * it should lose, and thaws at the pace of loaded chunks instead. Early spring is when the winter's snow is still
     * going, and far terrain (Distant Horizons, Voxy) switches from snowy to clear only at mid spring. In every other
     * sub-season, catch-up on load is instant both ways.
     */
    private static final Set<SubSeason> THAW_TIME = EnumSet.of(SubSeason.EARLY_SPRING);

    private SnowRules() {
    }

    /** Whether chunks brought up to date on load in this sub-season keep their snow and ice for the paced thaw. */
    public static boolean isThawTime(SubSeason subSeason) {
        return THAW_TIME.contains(subSeason);
    }

    /**
     * What is known about the snow position of one column (or any recorded position).
     *
     * @param base          the biome's base climate
     * @param coldNow       the season makes it cold enough for snow here
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

    /**
     * The layers a position should end up with and how many of them are the season's. {@code settled} is false when
     * a gradual step left more to do.
     */
    public record Outcome(int layers, int modLayers, boolean settled) {
    }

    /**
     * Moves the snow at a spot towards the target: all the way when a chunk is brought up to date on load, one
     * layer at a time when {@code gradual}.
     *
     * @param depthLayers the layers of snow in the spot's climate, used where it's cold now
     */
    public static Outcome snow(SnowSpot spot, int depthLayers, boolean nativeSnowMelts, int naturalHeight, boolean gradual) {
        int target = spot.coldNow() ? depthLayers : 0;
        int layers = spot.layers();
        int modLayers = Math.min(spot.modLayers(), layers);
        if (layers < target) {
            if (!spot.canHoldSnow() || spot.playerPlaced()) {
                return new Outcome(layers, modLayers, true);
            }
            int step = gradual ? 1 : target - layers;
            // Snow the biome's own climate would bring is native, so only the season's extra snow is recorded.
            int added = spot.realCold() ? 0 : step;
            return new Outcome(layers + step, modLayers + added, layers + step == target);
        }
        if (layers > target) {
            int removable = modLayers;
            if (!spot.playerPlaced() && nativeMayMelt(spot, nativeSnowMelts, naturalHeight)) {
                removable = layers;
            }
            int wanted = Math.min(layers - target, removable);
            int step = gradual ? Math.min(1, wanted) : wanted;
            return new Outcome(layers - step, Math.max(0, modLayers - step), step == wanted);
        }
        return new Outcome(layers, modLayers, true);
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

    /** Still water freezes where the season makes it cold, as vanilla's freezing would. */
    public static boolean freezes(boolean stillWater, boolean coldNow, boolean darkEnough) {
        return stillWater && coldNow && darkEnough;
    }

    /**
     * Whether water beside a spot holds back its freezing in a loaded chunk, which freezes from the shore inward: only
     * water that will freeze itself does, still water on top of its column where the season brings ice. Kelp,
     * seagrass, waterlogged blocks, flowing water, water under a dock and oceans count as shore, so the ice closes
     * around them instead of waiting all winter. Vanilla counts all of those as water.
     */
    public static boolean holdsBackFreezing(boolean stillWater, boolean onTop, boolean seasonalIce, boolean coldNow,
                                            boolean darkEnough) {
        return onTop && seasonalIce && freezes(stillWater, coldNow, darkEnough);
    }

    /** Only the season's ice melts; native ice stays frozen. */
    public static boolean iceMelts(boolean modIce, boolean coldNow) {
        return modIce && !coldNow;
    }
}
