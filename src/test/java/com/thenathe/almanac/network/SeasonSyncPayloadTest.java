package com.thenathe.almanac.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.thenathe.almanac.api.AlmanacEvents.Change;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SeasonSyncPayloadTest {
    private static SeasonSyncPayload sync(SubSeason subSeason, int day, Set<Climate> reached, int temperateSnow) {
        return new SeasonSyncPayload(subSeason, day, 24, false, reached, 7, Map.of(Climate.TEMPERATE, temperateSnow));
    }

    @Test
    void theSeasonChangedEventSaysWhatChanged() {
        SeasonSyncPayload winter = sync(SubSeason.MID_WINTER, 40, Set.of(Climate.COLD), 4);
        assertEquals(new Change(true, true, true), winter.changeFrom(null));
        assertEquals(new Change(true, false, false), sync(SubSeason.LATE_WINTER, 40, Set.of(Climate.COLD), 4).changeFrom(winter));
        assertEquals(new Change(false, true, false), sync(SubSeason.MID_WINTER, 40, Set.of(), 4).changeFrom(winter));
        assertEquals(new Change(false, false, true), sync(SubSeason.MID_WINTER, 40, Set.of(Climate.COLD), 5).changeFrom(winter));
        // A new day inside the same sub-season isn't a change listeners need to hear about.
        assertFalse(sync(SubSeason.MID_WINTER, 41, Set.of(Climate.COLD), 4).changeFrom(winter).any());
    }
}
