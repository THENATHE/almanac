package com.thenathe.almanac.snow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SnowClocks;
import com.thenathe.almanac.api.SubSeason;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

/**
 * Far terrain's snow by place under regional weather (far-snow-111): which snow nodes a client works out when far
 * terrain asks about more than it keeps, and when the far snow lines count a climate's snow as begun.
 */
class FarSnowByPlaceTest {
    private static final SnowClocks CLOCKS = new SnowClocks(400, 400, 400, 3);
    private static final Set<Climate> ALL = EnumSet.allOf(Climate.class);

    /** The nodes LiteLights 1.1.0 to 1.1.4 ask about: the four round the middle of each of 64 x 64 places 512 apart. */
    private static List<Long> askedByFarSnow(int cameraX, int cameraZ) {
        List<Long> keys = new ArrayList<>();
        int originX = Math.floorDiv(cameraX, 512) * 512 - 32 * 512;
        int originZ = Math.floorDiv(cameraZ, 512) * 512 - 32 * 512;
        for (int z = 0; z < 64; z++) {
            for (int x = 0; x < 64; x++) {
                LongSet around = new LongOpenHashSet(4);
                SnowField.nodesOf((originX + x * 512 + 256) >> 4, (originZ + z * 512 + 256) >> 4, around);
                around.forEach(keys::add);
            }
        }
        return keys;
    }

    private static boolean placeKnown(LongSet chosen, int x, int z) {
        LongSet around = new LongOpenHashSet(4);
        SnowField.nodesOf(x >> 4, z >> 4, around);
        return chosen.containsAll(around);
    }

    @Test
    void withMoreAskedThanKeptTheNodesNearestTheCameraAreWorkedOut() {
        List<Long> asked = askedByFarSnow(704, -2_056);
        assertEquals(16_384, asked.size());
        LongSet chosen = ClientSnow.nearest(asked, 704, -2_056, ClientSnow.MOST_NODES);
        assertEquals(ClientSnow.MOST_NODES, chosen.size());
        // Every place within 4 km (Almanac's climate map at its default radius) is known, wherever it is.
        int known = 0;
        int near = 0;
        for (int dz = -4_096; dz <= 4_096; dz += 512) {
            for (int dx = -4_096; dx <= 4_096; dx += 512) {
                if (Math.hypot(dx, dz) <= 4_096) {
                    near++;
                    known += placeKnown(chosen, 704 + dx, -2_056 + dz) ? 1 : 0;
                }
            }
        }
        assertEquals(near, known);
        for (long key : chosen) {
            double distance = Math.hypot(SnowArea.keyX(key) * 256.0 - 704, SnowArea.keyZ(key) * 256.0 + 2_056);
            assertTrue(distance < 7_000, "nothing far away is chosen over the land round the camera: " + distance);
        }
    }

    @Test
    void upTo110TheFirstKeysTheMapKeptLeftMostPlacesNearTheCameraUnknown() {
        // What 1.1.0 did: the first MOST_NODES in the order a ConcurrentHashMap keeps them.
        Map<Long, Long> map = new ConcurrentHashMap<>();
        for (long key : askedByFarSnow(704, -2_056)) {
            map.put(key, 0L);
        }
        LongSet firstKept = new LongOpenHashSet();
        for (Long key : map.keySet()) {
            if (firstKept.size() >= ClientSnow.MOST_NODES) {
                break;
            }
            firstKept.add(key.longValue());
        }
        int known = 0;
        int near = 0;
        for (int dz = -4_096; dz <= 4_096; dz += 512) {
            for (int dx = -4_096; dx <= 4_096; dx += 512) {
                if (Math.hypot(dx, dz) <= 4_096) {
                    near++;
                    known += placeKnown(firstKept, 704 + dx, -2_056 + dz) ? 1 : 0;
                }
            }
        }
        assertTrue(known < near / 2, known + " of " + near);
    }

    @Test
    void withFewerAskedThanKeptEveryOneIsWorkedOut() {
        List<Long> asked = List.of(SnowArea.key(0, 0), SnowArea.key(100, -100), SnowArea.key(-4_000, 9));
        assertEquals(Set.copyOf(asked), Set.copyOf(ClientSnow.nearest(asked, 0, 0, ClientSnow.MOST_NODES)));
    }

    @Test
    void regionalSnowMayLieFromTheFirstColdStormableTickUntilASureMelt() {
        SnowLog log = new SnowLog(0);
        log.observe(0, SubSeason.MID_FALL, false, ALL, CLOCKS, 2);
        assertFalse(log.snowMayLie(Climate.TEMPERATE, 1_000), "temperate land is warm in fall");
        log.observe(1_000, SubSeason.MID_WINTER, false, ALL, CLOCKS, 2);
        assertTrue(log.snowMayLie(Climate.TEMPERATE, 1_100), "storms may reach it in winter, wherever they go");
        assertFalse(log.snowMayLie(Climate.HOT, 1_100), "winter brings hot land rain, not snow");
        log.observe(5_000, SubSeason.EARLY_SPRING, false, ALL, CLOCKS, 2);
        assertTrue(log.snowMayLie(Climate.TEMPERATE, 5_100), "a thaw only just begun");
        assertTrue(log.snowMayLie(Climate.TEMPERATE, 7_000), "five layers' worth of thaw: the deepest snow there could be is eight");
        log.observe(9_000, SubSeason.MID_SPRING, false, ALL, CLOCKS, 2);
        assertFalse(log.snowMayLie(Climate.TEMPERATE, 9_100), "ten layers' worth of thaw: none can be left anywhere");
    }

    @Test
    void underVanillasWeatherOnlyRainThatReachedCounts() {
        SnowLog log = new SnowLog(0);
        log.observe(0, SubSeason.MID_WINTER, false, ALL, CLOCKS);
        assertFalse(log.snowMayLie(Climate.TEMPERATE, 100));
        log.observe(200, SubSeason.MID_WINTER, true, Set.of(Climate.COLD), CLOCKS);
        assertFalse(log.snowMayLie(Climate.TEMPERATE, 300));
        assertTrue(log.snowMayLie(Climate.COLD, 300));
    }

    @Test
    void farTerrainCountsSnowBegunWhereLoadedLandIsBareOnlyUnderRegionalWeather() {
        SnowLog log = new SnowLog(0);
        log.observe(0, SubSeason.MID_WINTER, false, ALL, CLOCKS, 2);
        Map<Climate, Double> bare = new EnumMap<>(Climate.class);
        Map<Climate, Double> frozen = new EnumMap<>(Climate.class);
        for (Climate climate : Climate.values()) {
            bare.put(climate, 0.0);
            frozen.put(climate, 0.0);
        }
        SnowState loadedLandBare = new SnowState(SubSeason.MID_WINTER, bare, frozen, 3, true, 1);
        Map<Climate, Integer> regional = SeasonSnow.farLayers(loadedLandBare, log, 100);
        assertEquals(1, regional.get(Climate.TEMPERATE));
        assertEquals(1, regional.get(Climate.COLD));
        assertEquals(0, regional.get(Climate.HOT));
        assertEquals(0, SeasonSnow.farLayers(loadedLandBare, null, 100).get(Climate.TEMPERATE), "vanilla's weather: as before");
        Map<Climate, Double> deep = new EnumMap<>(bare);
        deep.put(Climate.TEMPERATE, 2.5);
        SnowState loadedLandDeep = new SnowState(SubSeason.MID_WINTER, deep, frozen, 3, true, 1);
        assertEquals(3, SeasonSnow.farLayers(loadedLandDeep, log, 100).get(Climate.TEMPERATE), "loaded land's own layers stand");
    }
}
