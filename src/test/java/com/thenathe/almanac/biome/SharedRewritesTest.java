package com.thenathe.almanac.biome;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.SubSeason;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Players without Almanac share rewritten chunks within a tick, and take turns for the refresh (F4). */
class SharedRewritesTest {
    @Test
    void rewritesAreSharedForOneTickAndOneSeason() {
        SharedRewrites<Object, String> shared = new SharedRewrites<>();
        Object section = new Object();
        shared.forTick(10, SubSeason.MID_FALL).put(section, "mid fall");
        assertEquals("mid fall", shared.forTick(10, SubSeason.MID_FALL).get(section), "a second player that tick");
        assertTrue(shared.forTick(10, SubSeason.LATE_FALL).isEmpty(), "a new sub-season starts over");
        shared.forTick(10, SubSeason.LATE_FALL).put(section, "late fall");
        assertTrue(shared.forTick(11, SubSeason.LATE_FALL).isEmpty(), "and so does the next tick");
    }

    @Test
    void sectionsAreKeyedByIdentity() {
        SharedRewrites<List<Integer>, String> shared = new SharedRewrites<>();
        Map<List<Integer>, String> rewrites = shared.forTick(1, SubSeason.MID_FALL);
        rewrites.put(List.of(1, 2), "first");
        // An equal but different container is another section: never handed the first one's rewrite.
        assertEquals(null, rewrites.get(List.of(1, 2)));
    }

    @Test
    void theRefreshGoesRoundThePlayers() {
        Deque<UUID> line = new ArrayDeque<>();
        List<UUID> players = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), UUID.randomUUID());
        line.addAll(players);
        List<UUID> first = SeasonRefresh.nextTurns(line);
        assertEquals(players.subList(0, SeasonRefresh.PLAYERS_PER_TICK), first);
        line.addAll(first);
        List<UUID> second = SeasonRefresh.nextTurns(line);
        assertEquals(List.of(players.get(4), players.get(5), players.get(0), players.get(1)), second);
        assertEquals(2, line.size());
    }
}
