package com.thenathe.almanac.api;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.world.level.Level;

/** Events other mods can listen to. */
public final class AlmanacEvents {
    /**
     * Fired when the overworld's sub-season, the rain reach or a snow depth changes, so listeners can drop anything
     * they worked out from the season, such as caches keyed by biome.
     *
     * <ul>
     *   <li>On the server, on the server thread, with the overworld, at most once a tick. The first call comes as
     *   the server starts.</li>
     *   <li>On a client connected to an Almanac server, on the client thread, with the client's level, when the
     *   server's season sync arrives, including the first one after joining.</li>
     * </ul>
     * In singleplayer both happen, each with its own level; check {@link Level#isClientSide()} if only one matters.
     * Snow depths count as changed when they move by a sixteenth of a layer, the precision clients get, so a long
     * snowfall fires every few hundred ticks. Listeners should be quick: drop a cache rather than rebuild it here.
     */
    public static final Event<SeasonChanged> SEASON_CHANGED = EventFactory.createArrayBacked(SeasonChanged.class,
            listeners -> (level, change) -> {
                for (SeasonChanged listener : listeners) {
                    listener.onSeasonChanged(level, change);
                }
            });

    /**
     * Fired on a client with Almanac when anything the far-terrain lookups answer may have changed in an area:
     * {@link AlmanacApi#farSnowLine}, {@link AlmanacApi#seasonalGrassColour}, {@link AlmanacApi#seasonalFoliageColour}
     * and {@link AlmanacApi#farClimate}. Use it to fill again only what changed.
     *
     * <ul>
     *   <li>When a climate map tile arrives (its area, everything in it) or is dropped (its area).</li>
     *   <li>When what far terrain shows changes (everywhere, snow lines): a new sub-season, snow appearing or going in a
     *   climate, the native snow setting, or the climate map turned on or off.</li>
     *   <li>When the day's colours change visibly (everywhere, colours), and when the sub-season or the rain reach
     *   changes (everywhere, climate).</li>
     * </ul>
     * Always on the client thread, with the client's level, after the change has been applied, so the lookups already
     * give the new answers. Never on the server. Nothing fires on disconnecting, when every answer becomes unknown.
     */
    public static final Event<FarClimateChanged> FAR_CLIMATE_CHANGED = EventFactory.createArrayBacked(FarClimateChanged.class,
            listeners -> (level, change) -> {
                for (FarClimateChanged listener : listeners) {
                    listener.onFarClimateChanged(level, change);
                }
            });

    private AlmanacEvents() {
    }

    @FunctionalInterface
    public interface SeasonChanged {
        void onSeasonChanged(Level level, Change change);
    }

    /**
     * What changed. At least one is true.
     *
     * @param subSeason a new sub-season, which changes every biome's seasonal copy, climate and colours
     * @param rainReach a different set of climates reached by the current rain event
     * @param snowDepth a different snow depth in at least one climate
     */
    public record Change(boolean subSeason, boolean rainReach, boolean snowDepth) {
        public boolean any() {
            return subSeason || rainReach || snowDepth;
        }
    }

    @FunctionalInterface
    public interface FarClimateChanged {
        void onFarClimateChanged(Level level, FarChange change);
    }

    /**
     * Where and what changed for the far-terrain lookups. The area is in block coordinates, both ends included; at
     * least one of the three kinds is true.
     *
     * @param snowLines {@link AlmanacApi#farSnowLine} may give other answers
     * @param colours   the seasonal grass and foliage colours may differ
     * @param climate   {@link AlmanacApi#farClimate} may differ
     */
    public record FarChange(int minX, int minZ, int maxX, int maxZ, boolean snowLines, boolean colours, boolean climate) {
        /** Every column. */
        public static FarChange everywhere(boolean snowLines, boolean colours, boolean climate) {
            return new FarChange(Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, snowLines,
                    colours, climate);
        }

        public boolean isEverywhere() {
            return minX == Integer.MIN_VALUE && minZ == Integer.MIN_VALUE && maxX == Integer.MAX_VALUE
                    && maxZ == Integer.MAX_VALUE;
        }

        /** Whether the change touches any column of a rectangle (both ends included). */
        public boolean intersects(int otherMinX, int otherMinZ, int otherMaxX, int otherMaxZ) {
            return minX <= otherMaxX && otherMinX <= maxX && minZ <= otherMaxZ && otherMinZ <= maxZ;
        }
    }
}
