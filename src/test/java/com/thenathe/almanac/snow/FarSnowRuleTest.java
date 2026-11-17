package com.thenathe.almanac.snow;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.biome.SeasonalCopies;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The far rule (docs/FAR-SNOW.md): far terrain shows what a loaded chunk would show now, except that early spring
 * holds the snow of the start of early spring (user, 2026-11-17).
 */
class FarSnowRuleTest {
    private static final int SEA = 63;
    private static final int MIN_Y = -64;
    private static final int MAX_Y = 319;
    private static final SeasonalCopies.CopyClimate COPIES = new SeasonalCopies.CopyClimate(0.0F, 0.5F, 48);
    private static final float PLAINS = 0.8F;
    private static final float WINDSWEPT_HILLS = 0.2F;
    private static final float SNOWY_PLAINS = 0.0F;
    private static final float DESERT = 2.0F;

    /** The far snow line of a column with no temperature noise, fitted to an overworld's heights. */
    private static int line(Climate base, float seasonal, float real, int layers, boolean nativeSnowMelts) {
        return line(base, seasonal, real, layers, nativeSnowMelts, false);
    }

    private static int line(Climate base, float seasonal, float real, int layers, boolean nativeSnowMelts, boolean snowSurface) {
        int line = FarSnowRule.snowLine(base, FarSnowRule.coldLine(seasonal, 0.0F, SEA), FarSnowRule.coldLine(real, 0.0F, SEA),
                layers, nativeSnowMelts, 1, snowSurface);
        return FarSnowRule.withinLevel(line, MIN_Y, MAX_Y);
    }

    @Test
    void coldLinesFollowVanillasArithmetic() {
        // Cold at sea level: everywhere. Warmer: the line climbs 40 blocks for every 0.05, from 17 above the sea.
        assertEquals(FarSnowRule.EVERYWHERE, FarSnowRule.coldLine(SNOWY_PLAINS, 0.0F, SEA));
        // The early and late summer copies of COLD biomes: rain up to the snow line setting (48 above the sea), snow above.
        assertEquals(SEA + 49, FarSnowRule.coldLine(COPIES.snowLineTemperature(), 0.0F, SEA));
        assertEquals(SEA + 17 + 40 + 1, FarSnowRule.coldLine(WINDSWEPT_HILLS, 0.0F, SEA));
        // Noise moves the line down by as many blocks as it adds.
        assertEquals(SEA + 17 + 40 + 1 - 5, FarSnowRule.coldLine(WINDSWEPT_HILLS, 5.0F, SEA));
        assertEquals(FarSnowRule.NOWHERE, FarSnowRule.withinLevel(FarSnowRule.coldLine(PLAINS, 0.0F, SEA), MIN_Y, MAX_Y));
        assertEquals(FarSnowRule.NOWHERE, FarSnowRule.coldLine(Float.NaN, 0.0F, SEA));
    }

    @Test
    void coldLinesMatchABlockByBlockSearch() {
        int snowLevel = SEA + 17;
        for (float temperature = 0.15F; temperature < 0.6F; temperature += 0.0037F) {
            for (float noise = -8.0F; noise <= 8.0F; noise += 0.731F) {
                int expected = snowLevel + 1;
                for (int y = 2000; y > snowLevel; y--) {
                    if (!FarSnowRule.isCold(temperature, noise, y, snowLevel)) {
                        expected = y + 1;
                        break;
                    }
                }
                assertEquals(expected, FarSnowRule.coldLine(temperature, noise, SEA), "temperature " + temperature + ", noise " + noise);
            }
        }
    }

    @Test
    void earlySpringHoldsTheSnowOfItsStart() {
        Map<Climate, Integer> melted = Map.of(Climate.TEMPERATE, 0);
        Map<Climate, Integer> atThawStart = Map.of(Climate.TEMPERATE, 1);
        FarSnowRule.Shown earlySpring = FarSnowRule.shown(SubSeason.EARLY_SPRING, melted, atThawStart);
        assertEquals(new FarSnowRule.Shown(SubSeason.LATE_WINTER, atThawStart), earlySpring);
        // Plains show late winter's snow (its copy is cold everywhere) all through early spring...
        assertEquals(FarSnowRule.EVERYWHERE, line(Climate.TEMPERATE, COPIES.winterTemperature(), PLAINS,
                earlySpring.layers(Climate.TEMPERATE), true));
        // ...and turn clear at mid spring.
        FarSnowRule.Shown midSpring = FarSnowRule.shown(SubSeason.MID_SPRING, melted, atThawStart);
        assertEquals(new FarSnowRule.Shown(SubSeason.MID_SPRING, melted), midSpring);
        assertEquals(FarSnowRule.NOWHERE, line(Climate.TEMPERATE, PLAINS, PLAINS, midSpring.layers(Climate.TEMPERATE), true));
        // Every other sub-season shows the present.
        for (SubSeason subSeason : SubSeason.values()) {
            if (subSeason != SubSeason.EARLY_SPRING) {
                assertEquals(subSeason, FarSnowRule.shown(subSeason, melted, atThawStart).subSeason());
            }
        }
    }

    @Test
    void earlySpringAfterASnowlessWinterShowsNoTemperateSnow() {
        FarSnowRule.Shown shown = FarSnowRule.shown(SubSeason.EARLY_SPRING, Map.of(), Map.of(Climate.TEMPERATE, 0));
        assertEquals(FarSnowRule.NOWHERE, line(Climate.TEMPERATE, COPIES.winterTemperature(), PLAINS,
                shown.layers(Climate.TEMPERATE), true));
    }

    @Test
    void temperateLandIsWhiteOnlyOnceItHasSnowedThisWinter() {
        assertEquals(FarSnowRule.NOWHERE, line(Climate.TEMPERATE, COPIES.winterTemperature(), PLAINS, 0, true));
        assertEquals(FarSnowRule.EVERYWHERE, line(Climate.TEMPERATE, COPIES.winterTemperature(), PLAINS, 1, true));
        // Before it snows, windswept hills still have their altitude snow.
        assertEquals(SEA + 58, line(Climate.TEMPERATE, COPIES.winterTemperature(), WINDSWEPT_HILLS, 0, true));
    }

    @Test
    void hotLandHasNoSnowButAltitudeSnow() {
        // Winter makes HOT biomes rainy, not cold, whatever the snow depth says.
        assertEquals(FarSnowRule.NOWHERE, line(Climate.HOT, DESERT, DESERT, 8, true));
        // A HOT biome high and cool enough for its own snow keeps it.
        assertEquals(SEA + 58, line(Climate.HOT, WINDSWEPT_HILLS, WINDSWEPT_HILLS, 0, true));
    }

    @Test
    void altitudeSnowAlwaysStays() {
        for (SubSeason subSeason : SubSeason.values()) {
            for (int layers = 0; layers <= 2; layers++) {
                // The season can only make windswept hills colder (winter) or leave them as they are.
                float seasonal = subSeason.season() == SubSeason.Season.WINTER ? COPIES.winterTemperature() : WINDSWEPT_HILLS;
                int line = line(Climate.TEMPERATE, seasonal, WINDSWEPT_HILLS, layers, true);
                assertTrue(line <= SEA + 58, subSeason + " with " + layers + " layers: " + line);
            }
        }
    }

    @Test
    void coldLandMeltsInSummerOnlyWhenNativeSnowMelts() {
        // Mid summer gives COLD biomes the summer temperature: rain below about 360 blocks.
        float summer = COPIES.summerTemperature();
        assertEquals(FarSnowRule.NOWHERE, line(Climate.COLD, summer, SNOWY_PLAINS, 0, true));
        assertEquals(FarSnowRule.EVERYWHERE, line(Climate.COLD, summer, SNOWY_PLAINS, 0, false));
        // Early and late summer split rain below the snow line from snow above it.
        assertEquals(SEA + 49, line(Climate.COLD, COPIES.snowLineTemperature(), SNOWY_PLAINS, 1, true));
        assertEquals(FarSnowRule.EVERYWHERE, line(Climate.COLD, COPIES.snowLineTemperature(), SNOWY_PLAINS, 1, false));
        // The rest of the year COLD land is white, snowed on this season or not.
        assertEquals(FarSnowRule.EVERYWHERE, line(Climate.COLD, SNOWY_PLAINS, SNOWY_PLAINS, 0, true));
    }

    @Test
    void biomesWithSnowBlockGroundAreLeftAsCapturedWhileTheirSnowMelts() {
        // Groves, snowy slopes and peaks (#almanac:snow_surface): native snow on their snow blocks never melts, on
        // their grass, dirt and leaves it does. One line can't say both, so far terrain stays as captured then.
        float grove = -0.2F;
        assertEquals(FarSnowRule.UNKNOWN, line(Climate.COLD, COPIES.summerTemperature(), grove, 0, true, true));
        assertEquals(FarSnowRule.UNKNOWN, line(Climate.COLD, COPIES.snowLineTemperature(), grove, 1, true, true));
        // With native melting off, or outside summer, both kinds of ground keep their snow.
        assertEquals(FarSnowRule.EVERYWHERE, line(Climate.COLD, COPIES.summerTemperature(), grove, 0, false, true));
        assertEquals(FarSnowRule.EVERYWHERE, line(Climate.COLD, COPIES.winterTemperature(), grove, 0, true, true));
        // The same biome without the tag melts like snowy plains.
        assertEquals(FarSnowRule.NOWHERE, line(Climate.COLD, COPIES.summerTemperature(), grove, 0, true, false));
    }

    @Test
    void biomesWithoutSeasonsKeepTheirOwnSnow() {
        assertEquals(FarSnowRule.EVERYWHERE, FarSnowRule.snowLine(null, FarSnowRule.NOWHERE, FarSnowRule.EVERYWHERE, 0, true, 1, false));
        assertEquals(SEA + 58, FarSnowRule.snowLine(null, FarSnowRule.EVERYWHERE, SEA + 58, 3, true, 1, false));
    }

    @Test
    void snowLiesWhereCatchUpWouldLeaveSnow() {
        // Snow lies where SnowRules leaves a generated column with any snow; check every case against it directly.
        for (Climate base : Climate.values()) {
            for (boolean coldNow : new boolean[] {false, true}) {
                for (boolean realCold : new boolean[] {false, true}) {
                    for (int layers = 0; layers <= 2; layers++) {
                        for (boolean melts : new boolean[] {false, true}) {
                            for (boolean natural : new boolean[] {false, true}) {
                                boolean nativeStays = realCold && !(melts && natural && base == Climate.COLD && !coldNow);
                                boolean expected = coldNow && layers > 0 || nativeStays;
                                assertEquals(expected, FarSnowRule.snowLies(base, coldNow, realCold, layers, melts, 1, natural),
                                        base + " cold now " + coldNow + " real " + realCold + " layers " + layers
                                                + " melts " + melts + " natural " + natural);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void linesFitTheLevel() {
        assertEquals(FarSnowRule.EVERYWHERE, FarSnowRule.withinLevel(MIN_Y, MIN_Y, MAX_Y));
        assertEquals(MIN_Y + 1, FarSnowRule.withinLevel(MIN_Y + 1, MIN_Y, MAX_Y));
        assertEquals(MAX_Y, FarSnowRule.withinLevel(MAX_Y, MIN_Y, MAX_Y));
        assertEquals(FarSnowRule.NOWHERE, FarSnowRule.withinLevel(MAX_Y + 1, MIN_Y, MAX_Y));
        assertEquals(FarSnowRule.UNKNOWN, FarSnowRule.withinLevel(FarSnowRule.UNKNOWN, MIN_Y, MAX_Y));
    }
}
