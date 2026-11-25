package com.thenathe.almanac.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.Storm;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.WetSpell;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The amount of rain holds at vanilla's: every place is under rain about 15.8 % of the time, in every season, with
 * thunder about 9 % of the rain time over the year; and storm sizes vary widely. Measured on the storms themselves,
 * by the exact time each place spends under a rain core over a long span.
 */
class CalibrationTest {
    private static final int DAY = StormMathsV1.DAY;
    private static final double VANILLA = 18_000.0 / 114_000.0;

    /** The exact share of {@code days} a place spends under rain, from its wet spell. */
    private static double rainShare(WeatherField field, double x, double z, long from, int days) {
        WetSpell spell = field.wetSpell(x, z, from, from + (long) days * DAY);
        return spell.ticks(Climate.TEMPERATE) / ((double) days * DAY);
    }

    private static double[] places(long seed, int count, double spread) {
        Random random = new Random(seed);
        double[] places = new double[count * 2];
        for (int index = 0; index < places.length; index++) {
            places[index] = (random.nextDouble() * 2.0 - 1.0) * spread;
        }
        return places;
    }

    /**
     * The share of {@code days} vanilla rains in one world, from its own timers: rain for 12,000 to 24,000 ticks, then
     * clear for 12,000 to 180,000. Vanilla's share over a stretch varies from one stretch to the next too; this is
     * what a place's share is held against.
     */
    private static double vanillaShare(Random random, int days) {
        long span = (long) days * DAY;
        long rained = 0;
        long at = -random.nextInt(180_000);
        while (at < span) {
            long clear = 12_000 + random.nextInt(168_001);
            at += clear;
            long rain = 12_000 + random.nextInt(12_001);
            rained += Math.max(0, Math.min(span, at + rain) - Math.max(0, at));
            at += rain;
        }
        return rained / (double) span;
    }

    private static double spread(double[] shares) {
        double mean = Arrays.stream(shares).average().orElseThrow();
        return Math.sqrt(Arrays.stream(shares).map(share -> (share - mean) * (share - mean)).sum() / shares.length);
    }

    @Test
    void everySeasonRainsVanillasShare() {
        int days = 240;
        Random vanillaWorlds = new Random(7);
        double[] vanilla = new double[400];
        for (int world = 0; world < vanilla.length; world++) {
            vanilla[world] = vanillaShare(vanillaWorlds, days);
        }
        double vanillaSpread = spread(vanilla);
        System.out.printf("vanilla over %d days: mean %.2f %%, spread %.2f points%n", days,
                Arrays.stream(vanilla).average().orElseThrow() * 100, vanillaSpread * 100);
        for (SubSeason subSeason : List.of(SubSeason.MID_SPRING, SubSeason.MID_SUMMER, SubSeason.MID_FALL,
                SubSeason.MID_WINTER)) {
            WeatherField field = Worlds.field(Worlds.heldIn(subSeason));
            double[] places = places(subSeason.ordinal(), 24, 400_000.0);
            double[] shares = new double[places.length / 2];
            for (int place = 0; place < shares.length; place++) {
                shares[place] = rainShare(field, places[place * 2], places[place * 2 + 1], 20L * DAY, days);
            }
            double mean = Arrays.stream(shares).average().orElseThrow();
            double spread = spread(shares);
            System.out.printf("%s: mean rain share %.2f %%, spread between places %.2f points (lowest %.2f %%, "
                            + "highest %.2f %%)%n", subSeason.id(), mean * 100, spread * 100,
                    Arrays.stream(shares).min().orElseThrow() * 100, Arrays.stream(shares).max().orElseThrow() * 100);
            assertEquals(VANILLA, mean, 0.01, subSeason.id() + ": the mean share of time under rain");
            assertTrue(spread < vanillaSpread * 2.5,
                    subSeason.id() + ": places differ from each other no more than vanilla's stretches do");
        }
    }

    @Test
    void theShareHoldsThroughTheYear() {
        WeatherField field = Worlds.field(Worlds.year(8, 3));
        double[] places = places(99, 16, 300_000.0);
        double total = 0.0;
        for (int place = 0; place < places.length / 2; place++) {
            total += rainShare(field, places[place * 2], places[place * 2 + 1], 10L * DAY, 3 * 96 - 20);
        }
        double mean = total / (places.length / 2);
        System.out.printf("a year of seasons: mean rain share %.2f %%%n", mean * 100);
        assertEquals(VANILLA, mean, 0.01);
    }

    @Test
    void theShareIsTheSameAtNearbyPlacesAndFarOnes() {
        // Two places 300 blocks apart share most storms; places across the world share none. Both hold the share.
        WeatherField field = Worlds.field(Worlds.heldIn(SubSeason.MID_SPRING));
        double near = rainShare(field, 1_000.0, 1_000.0, 20L * DAY, 300);
        double nextDoor = rainShare(field, 1_300.0, 1_000.0, 20L * DAY, 300);
        double far = rainShare(field, 600_000.0, -900_000.0, 20L * DAY, 300);
        System.out.printf("one place %.2f %%, 300 blocks on %.2f %%, far away %.2f %%%n", near * 100, nextDoor * 100,
                far * 100);
        for (double share : new double[] {near, nextDoor, far}) {
            assertEquals(VANILLA, share, 0.05);
        }
    }

    /** The exact share of {@code days} a place spends under thunder. */
    private static double thunderShare(WeatherField field, double x, double z, long from, int days) {
        double to = from + (double) days * DAY;
        List<Spans.Span> spans = new ArrayList<>();
        field.forEachStorm(x, z, 0.0, from, to, storm -> spans.addAll(storm.thunderSpans(x, z, from, to)));
        return Spans.measure(Spans.union(spans), from, to) / (to - from);
    }

    @Test
    void thunderIsAboutNinePercentOfTheRainOverTheYear() {
        double yearTotal = 0.0;
        for (SubSeason subSeason : List.of(SubSeason.MID_SPRING, SubSeason.MID_SUMMER, SubSeason.MID_FALL,
                SubSeason.MID_WINTER)) {
            WeatherField field = Worlds.field(Worlds.heldIn(subSeason));
            double target = StormMathsV1.INSTANCE.character(subSeason.season()).thunderShareOfRain();
            double[] places = places(1_000 + subSeason.ordinal(), 24, 400_000.0);
            double thunder = 0.0;
            double rain = 0.0;
            for (int place = 0; place < places.length / 2; place++) {
                double x = places[place * 2];
                double z = places[place * 2 + 1];
                thunder += thunderShare(field, x, z, 20L * DAY, 240);
                rain += rainShare(field, x, z, 20L * DAY, 240);
            }
            double ofRain = thunder / rain;
            System.out.printf("%s: thunder %.2f %% of the time, %.1f %% of the rain time (aim %.0f %%)%n",
                    subSeason.id(), thunder / (places.length / 2) * 100, ofRain * 100, target * 100);
            assertEquals(target, ofRain, Math.max(0.015, target * 0.3), subSeason.id());
            yearTotal += ofRain;
        }
        double year = yearTotal / 4.0;
        System.out.printf("over the year: thunder %.1f %% of the rain time%n", year * 100);
        assertEquals(StormCharacter.VANILLA_THUNDER_SHARE_OF_RAIN, year, 0.02);
    }

    @Test
    void stormSizesVaryWidely() {
        for (SubSeason.Season season : SubSeason.Season.values()) {
            StormCharacter character = StormMathsV1.INSTANCE.character(season);
            List<Double> radii = new ArrayList<>();
            List<Double> stretches = new ArrayList<>();
            for (int i = -20; i < 20; i++) {
                for (int day = 0; day < 40; day++) {
                    for (Storm storm : StormMathsV1.INSTANCE.cell(Worlds.SEED, i, 7, day, season)) {
                        radii.add(storm.radius());
                        stretches.add(storm.stretch());
                    }
                }
            }
            double[] sorted = radii.stream().mapToDouble(Double::doubleValue).sorted().toArray();
            double tenth = sorted[sorted.length / 10];
            double median = sorted[sorted.length / 2];
            double ninetieth = sorted[sorted.length * 9 / 10];
            long fronts = stretches.stream().filter(stretch -> stretch > 1.5).count();
            System.out.printf("%s: %d storms, radius 10th %.0f, median %.0f, 90th %.0f blocks (from %.0f to %.0f); "
                            + "%.0f %% stretched past 1.5%n", season, sorted.length, tenth, median, ninetieth,
                    sorted[0], sorted[sorted.length - 1], fronts * 100.0 / stretches.size());
            assertTrue(sorted[0] >= character.minRadius() && sorted[sorted.length - 1] <= character.maxRadius());
            assertTrue(ninetieth > 2.2 * tenth, season + ": the large storms are well over twice the small ones");
            assertEquals(Math.sqrt(character.minRadius() * character.maxRadius()), median, median * 0.15,
                    season + ": radii are even on a log scale");
        }
    }

    /**
     * The least share of its life a storm could grow over without its back edge creeping against the wind: the rain
     * core's reach along its path, at full size, over how far it moves in its life. Grown more slowly than this, the
     * edge only ever moves with the storm; shrinking is the same in reverse for the front edge.
     */
    private static double edgeKeepingGrowth(Storm storm) {
        double speed = Math.hypot(storm.velocityX(), storm.velocityZ());
        double acrossPath = storm.angle() - Math.atan2(storm.velocityZ(), storm.velocityX());
        double reachAlongPath = Storm.RAIN_CORE * Math.hypot(storm.majorAxis() * Math.cos(acrossPath),
                storm.minorAxis() * Math.sin(acrossPath));
        return reachAlongPath / (speed * storm.lifeTicks());
    }

    @Test
    void theTypicalStormGrowsNoFasterThanItMoves() {
        // Storm.GROWTH is the median of edgeKeepingGrowth over a year of storms as they are born, rounded to two
        // places: half of all storms grow and shrink with both edges moving the storm's way.
        List<Double> shares = new ArrayList<>();
        for (SubSeason.Season season : SubSeason.Season.values()) {
            for (int i = -20; i < 20; i++) {
                for (int day = 0; day < 40; day++) {
                    for (Storm storm : StormMathsV1.INSTANCE.cell(Worlds.SEED, i, 7, day, season)) {
                        shares.add(edgeKeepingGrowth(storm));
                    }
                }
            }
        }
        double[] sorted = shares.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        double median = sorted[sorted.length / 2];
        long keeping = shares.stream().filter(share -> share <= Storm.GROWTH).count();
        System.out.printf("a year of %d storms: edge-keeping growth 10th %.3f, median %.3f, 90th %.3f; at %.2f, "
                        + "%.0f %% of storms keep both edges moving their way%n", sorted.length,
                sorted[sorted.length / 10], median, sorted[sorted.length * 9 / 10], Storm.GROWTH,
                keeping * 100.0 / sorted.length);
        assertEquals(median, Storm.GROWTH, 0.005);
    }

    @Test
    void thePoissonDrawHasItsMean() {
        double mean = 0.6;
        long total = 0;
        int draws = 200_000;
        Hashes.Draws stream = new Hashes.Draws(42);
        for (int index = 0; index < draws; index++) {
            total += StormMathsV1.poisson(stream.next(), mean);
        }
        assertEquals(mean, total / (double) draws, 0.01);
    }

    @Test
    void theCalibrationArithmeticHoldsVanillasShare() {
        // The solved count, put back through the coverage formula, gives vanilla's share exactly.
        for (SubSeason.Season season : SubSeason.Season.values()) {
            StormCharacter character = StormMathsV1.INSTANCE.character(season);
            double density = character.density(StormMathsV1.CELL, DAY);
            double coreAreaTime = Math.PI * Storm.RAIN_CORE * Storm.RAIN_CORE * character.meanSquaredRadius()
                    * StormCharacter.lifeAreaFactor() * character.meanLife();
            double cover = density * coreAreaTime / ((double) StormMathsV1.CELL * StormMathsV1.CELL * DAY);
            assertEquals(VANILLA, 1.0 - Math.exp(-cover), 1e-12);
            assertTrue(character.thunderChance() > 0.0 && character.thunderChance() <= 1.0);
            System.out.printf("%s: %.3f storms per cell a day, %.1f %% thunderstorms%n", season, density,
                    character.thunderChance() * 100);
        }
        assertTrue(Arrays.stream(SubSeason.Season.values()).mapToDouble(season ->
                StormMathsV1.INSTANCE.character(season).thunderShareOfRain()).average().orElseThrow() > 0.085);
    }
}
