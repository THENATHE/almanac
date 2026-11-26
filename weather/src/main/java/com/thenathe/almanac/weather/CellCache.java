package com.thenathe.almanac.weather;

import com.thenathe.almanac.api.Storm;
import com.thenathe.almanac.api.SubSeason;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lattice cells already worked out into storms, kept so the same cell isn't drawn again for every question. Safe from
 * any thread. A cell's storms depend only on the seed, the maths, the cell, its season and (from version 2 of the
 * maths) its share of time under rain, which are all in the key, so an entry is never wrong, only perhaps no longer
 * needed; past a few tens of thousands the cache starts again.
 */
final class CellCache {
    private static final int MOST_CELLS = 40_000;

    private record Key(long seed, int version, SubSeason.Season season, int rainShare, int i, int j, long day) {
    }

    private final ConcurrentHashMap<Key, StormShape[]> cells = new ConcurrentHashMap<>();

    StormShape[] cell(StormMaths maths, long seed, int i, int j, long day, SubSeason.Season season, int rainShare) {
        int share = maths.followsRainShare() ? rainShare : 0;
        Key key = new Key(seed, maths.version(), season, share, i, j, day);
        StormShape[] known = cells.get(key);
        if (known != null) {
            return known;
        }
        Storm[] storms = maths.cell(seed, i, j, day, season, share);
        StormShape[] shapes = new StormShape[storms.length];
        for (int index = 0; index < storms.length; index++) {
            shapes[index] = new StormShape(storms[index]);
        }
        if (cells.size() > MOST_CELLS) {
            cells.clear();
        }
        cells.put(key, shapes);
        return shapes;
    }

    int size() {
        return cells.size();
    }

    void clear() {
        cells.clear();
    }
}
