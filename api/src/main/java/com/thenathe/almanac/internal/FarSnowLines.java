package com.thenathe.almanac.internal;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The far rule (the snow module's {@code FarSnowRule}, {@code docs/FAR-SNOW.md}): where far terrain shows snow in a
 * column, from the snow rules applied to the column as world generation left it. Lines are block Ys or
 * {@code AlmanacApi}'s {@code FAR_SNOW_} values. Safe from any thread.
 */
public interface FarSnowLines {
    /** The sub-season far terrain shows: the present, or while a warm climate's snow melts, its last cold one. */
    SubSeason shownSubSeason(SubSeason now, Map<Climate, Integer> layers);

    /** The lowest Y at which a column of this temperature is cold enough for snow, by vanilla's arithmetic. */
    int coldLine(float temperature, float noise, int seaLevel);

    /** A column's far snow line from its two cold lines; {@code base} is null for a biome without seasons. */
    int snowLine(@Nullable Climate base, int seasonalColdLine, int realColdLine, int layers, boolean nativeSnowMelts,
                 int naturalHeight, boolean snowSurface);

    /** Fits a line to a level's heights. */
    int withinLevel(int line, int minY, int maxY);
}
