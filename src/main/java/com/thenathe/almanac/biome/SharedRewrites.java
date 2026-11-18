package com.thenathe.almanac.biome;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Rewritten biome sections shared by every packet built on the server thread in one tick for the same season, so
 * players without Almanac who are sent the same chunk (players near each other, or the refresh after a sub-season
 * change) don't each pay for rewriting it.
 *
 * <p>Sections are keyed by their biome container itself. The game never changes a section's biomes in place: biome
 * changes ({@code /fillbiome}, world generation) give the section a new container, so a rewrite stays right for as
 * long as its key is the section's container. Entries only live for one tick, which bounds what they hold on to.
 *
 * @param <K> the original container
 * @param <V> its rewrite
 */
final class SharedRewrites<K, V> {
    private final Map<K, V> rewrites = new IdentityHashMap<>();
    private long tick = Long.MIN_VALUE;
    private @Nullable Object season;

    /**
     * The rewrites for this tick and season; empty again at a new tick or once the season (the sub-season and the
     * biome index the copies come from) is different.
     */
    Map<K, V> forTick(long tick, Object season) {
        if (tick != this.tick || !Objects.equals(season, this.season)) {
            rewrites.clear();
            this.tick = tick;
            this.season = season;
        }
        return rewrites;
    }

    void clear() {
        rewrites.clear();
        tick = Long.MIN_VALUE;
        season = null;
    }
}
