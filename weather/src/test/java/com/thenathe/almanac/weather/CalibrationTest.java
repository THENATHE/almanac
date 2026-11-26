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
 * The amount of rain holds at the share asked for: on the current maths (version 2) every place is under rain about
 * 25 % of the time, in every season, with each season's thunder its share of the rain time (about 9 % over the year);
 * storms are larger and fewer than version 1's; and storm sizes vary widely. Version 1 still holds vanilla's 15.8 %.
 * Measured on the storms themselves, by the exact time each place spends under a rain core over a long span.
 */
class CalibrationTest {
    private static final int DAY = StormMathsV1.DAY;
    private static final double VANILLA = 18_000.0 / 114_000.0;
    private static final double SHARE = StormMathsV2.DEFAULT_RAIN_SHARE / 100.0;
    /** How far the mean share of 24 places over 240 days may be from the share asked for, in parts of 1. */
    private static final double MEAN_TOLERANCE = 0.01;
    private static final List<SubSeason> MID_SEASONS = List.of(SubSeason.MID_SPRING, SubSeason.MID_SUMMER,
            SubSeason.MID_FALL, SubSeason.MID_WINTER);

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
     * clear for 12,000 to 180,000, each clear spell scaled by {@code clearScale} (1 for vanilla's own). Vanilla's share
     * over a stretch varies from one stretch to the next too; this is what a place's share is held against.
     */
    private static double vanillaShare(Random random, int days, double clearScale) {
        long span = (long) days * DAY;
        long rained = 0;
        long at = -random.nextInt(180_000);
        while (at < span) {
            long clear = Math.round((12_000 + random.nextInt(168_001)) * clearScale);
            at += clear;
            long rain = 12_000 + random.nextInt(12_001);
            rained += Math.max(0, Math.min(span, at + rain) - Math.max(0, at));
            at += rain;
        }
        return rained / (double) span;
    }

    /** How much vanilla's clear spells would be shortened for its timers to rain {@code share} of the time. */
    private static double clearScaleFor(double share) {
        return 18_000.0 * (1.0 - share) / share / 96_000.0;
    }

    private static double spread(double[] shares) {
        double mean = Arrays.stream(shares).average().orElseThrow();
        return Math.sqrt(Arrays.stream(shares).map(share -> (share - mean) * (share - mean)).sum() / shares.length);
    }

    @Test
    void everySeasonRainsItsShare() {
        int days = 240;
        Random vanillaWorlds = new Random(7);
        double[] vanilla = new double[400];
        for (int world = 0; world < vanilla.length; world++) {
            vanilla[world] = vanillaShare(vanillaWorlds, days, clearScaleFor(SHARE));
        }
        double vanillaSpread = spread(vanilla);
        System.out.printf("vanilla's timers with clear spells shortened to rain %.0f %% over %d days: mean %.2f %%, "
                + "spread %.2f points%n", SHARE * 100, days, Arrays.stream(vanilla).average().orElseThrow() * 100,
                vanillaSpread * 100);
        for (SubSeason subSeason : MID_SEASONS) {
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
            assertEquals(SHARE, mean, MEAN_TOLERANCE, subSeason.id() + ": the mean share of time under rain");
            assertTrue(spread < vanillaSpread * 2.5,
                    subSeason.id() + ": places differ from each other no more than vanilla's stretches do");
        }
    }

    @Test
    void version1StillRainsVanillasShare() {
        for (SubSeason subSeason : List.of(SubSeason.MID_SUMMER, SubSeason.MID_WINTER)) {
            WeatherField field = Worlds.field(Worlds.heldIn(subSeason, StormMathsV1.VERSION, 0));
            double[] places = places(subSeason.ordinal(), 12, 400_000.0);
            double total = 0.0;
            for (int place = 0; place < places.length / 2; place++) {
                total += rainShare(field, places[place * 2], places[place * 2 + 1], 20L * DAY, 240);
            }
            double mean = total / (places.length / 2);
            System.out.printf("version 1, %s: mean rain share %.2f %%%n", subSeason.id(), mean * 100);
            assertEquals(VANILLA, mean, 0.015, subSeason.id());
        }
    }

    @Test
    void anotherShareOfTimeUnderRainHoldsToo() {
        // The server's timeUnderRain: the storm count is solved for whatever share an era records.
        for (int percent : new int[] {10, 40}) {
            WeatherField field = Worlds.field(Worlds.heldIn(SubSeason.MID_SPRING, StormMathsV2.VERSION, percent));
            double[] places = places(500 + percent, 12, 400_000.0);
            double total = 0.0;
            for (int place = 0; place < places.length / 2; place++) {
                total += rainShare(field, places[place * 2], places[place * 2 + 1], 20L * DAY, 240);
            }
            double mean = total / (places.length / 2);
            System.out.printf("time under rain set to %d %%: mean rain share %.2f %%%n", percent, mean * 100);
            assertEquals(percent / 100.0, mean, 0.015, percent + " %");
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
        assertEquals(SHARE, mean, MEAN_TOLERANCE);
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
            assertEquals(SHARE, share, 0.05);
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
    void thunderKeepsEachSeasonsShareOfTheRain() {
        double yearTotal = 0.0;
        double yearThunder = 0.0;
        for (SubSeason subSeason : MID_SEASONS) {
            WeatherField field = Worlds.field(Worlds.heldIn(subSeason));
            double target = StormMathsV2.INSTANCE.character(subSeason.season()).thunderShareOfRain();
            assertEquals(StormMathsV1.INSTANCE.character(subSeason.season()).thunderShareOfRain(), target,
                    subSeason.id() + ": thunder's share of the rain time is version 1's");
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
            double ofTime = thunder / (places.length / 2);
            System.out.printf("%s: thunder %.2f %% of the time, %.1f %% of the rain time (aim %.0f %%, so %.2f %% of "
                            + "the time)%n", subSeason.id(), ofTime * 100, ofRain * 100, target * 100,
                    target * SHARE * 100);
            assertEquals(target, ofRain, Math.max(0.015, target * 0.3), subSeason.id());
            assertEquals(target * SHARE, ofTime, Math.max(0.004, target * SHARE * 0.3), subSeason.id());
            yearTotal += ofRain;
            yearThunder += ofTime;
        }
        double year = yearTotal / 4.0;
        System.out.printf("over the year: thunder %.1f %% of the rain time, %.2f %% of the time%n", year * 100,
                yearThunder / 4.0 * 100);
        assertEquals(StormCharacter.VANILLA_THUNDER_SHARE_OF_RAIN, year, 0.02);
    }

    @Test
    void stormsAreLargerAndFewerThanVersion1s() {
        // The extra rain comes from larger storms, not more of them: in every season the solved count is lower.
        for (SubSeason.Season season : SubSeason.Season.values()) {
            StormCharacter before = StormMathsV1.INSTANCE.character(season);
            StormCharacter after = StormMathsV2.INSTANCE.character(season);
            double countBefore = StormMathsV1.INSTANCE.density(season);
            double countAfter = StormMathsV2.INSTANCE.density(season, StormMathsV2.DEFAULT_RAIN_SHARE);
            double areaRatio = after.meanSquaredRadius() / before.meanSquaredRadius();
            System.out.printf("%s: radius %.0f to %.0f (was %.0f to %.0f), area %.2f times; %.3f storms per cell a day "
                            + "(was %.3f, %.0f %% fewer); %.1f %% thunderstorms (was %.1f %%)%n", season,
                    after.minRadius(), after.maxRadius(), before.minRadius(), before.maxRadius(), areaRatio,
                    countAfter, countBefore, (1.0 - countAfter / countBefore) * 100,
                    StormMathsV2.INSTANCE.thunderChance(season, StormMathsV2.DEFAULT_RAIN_SHARE) * 100,
                    StormMathsV1.INSTANCE.thunderChance(season) * 100);
            assertTrue(countAfter < countBefore, season + ": fewer storms than version 1");
            assertTrue(areaRatio >= 1.5 && areaRatio <= 2.0, season + ": storms 1.5 to 2 times version 1's area");
            assertEquals(before.maxStretch(), after.maxStretch());
            assertEquals(before.minLife(), after.minLife());
            assertEquals(before.maxLife(), after.maxLife());
            assertEquals(before.minSpeed(), after.minSpeed());
            assertEquals(before.maxSpeed(), after.maxSpeed());
        }
        // Each season keeps its character beside the others: summer's storms the smallest, winter's the largest.
        StormMaths maths = StormMathsV2.INSTANCE;
        assertTrue(maths.character(SubSeason.Season.SUMMER).maxRadius() < maths.character(SubSeason.Season.SPRING).maxRadius());
        assertTrue(maths.character(SubSeason.Season.SPRING).maxRadius() < maths.character(SubSeason.Season.WINTER).maxRadius());
    }

    /** How many separate wet spells a place has a day, on average, and how long they last. */
    @Test
    void howOftenAPlaceIsRainedOn() {
        for (int version : new int[] {StormMathsV1.VERSION, StormMathsV2.VERSION}) {
            for (SubSeason subSeason : MID_SEASONS) {
                WeatherField field = Worlds.field(Worlds.heldIn(subSeason, version,
                        version == StormMathsV1.VERSION ? 0 : StormMathsV2.DEFAULT_RAIN_SHARE));
                double[] places = places(3_000 + subSeason.ordinal(), 12, 400_000.0);
                long spells = 0;
                double wet = 0.0;
                int days = 120;
                for (int place = 0; place < places.length / 2; place++) {
                    double x = places[place * 2];
                    double z = places[place * 2 + 1];
                    double from = 20.0 * DAY;
                    double to = from + (double) days * DAY;
                    List<Spans.Span> spans = new ArrayList<>();
                    field.forEachStorm(x, z, 0.0, from, to, storm -> spans.addAll(storm.rainSpans(x, z, from, to)));
                    List<Spans.Span> joined = Spans.union(spans);
                    spells += joined.size();
                    wet += Spans.measure(joined, from, to);
                }
                double perDay = spells / (double) (places.length / 2) / days;
                System.out.printf("version %d, %s: %.2f wet spells a day at a place, %.0f ticks each on average%n",
                        version, subSeason.id(), perDay, wet / Math.max(1, spells));
            }
        }
    }

    @Test
    void stormSizesVaryWidely() {
        for (SubSeason.Season season : SubSeason.Season.values()) {
            StormCharacter character = StormMathsV2.INSTANCE.character(season);
            List<Double> radii = new ArrayList<>();
            List<Double> stretches = new ArrayList<>();
            for (int i = -20; i < 20; i++) {
                for (int day = 0; day < 40; day++) {
                    for (Storm storm : StormMathsV2.INSTANCE.cell(Worlds.SEED, i, 7, day, season, StormMathsV2.DEFAULT_RAIN_SHARE)) {
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

    /** The edge-keeping growth of a year of storms as they are born, sorted. */
    private static double[] edgeKeepingGrowths(StormMaths maths) {
        List<Double> shares = new ArrayList<>();
        for (SubSeason.Season season : SubSeason.Season.values()) {
            for (int i = -20; i < 20; i++) {
                for (int day = 0; day < 40; day++) {
                    for (Storm storm : maths.cell(Worlds.SEED, i, 7, day, season, StormMathsV2.DEFAULT_RAIN_SHARE)) {
                        shares.add(edgeKeepingGrowth(storm));
                    }
                }
            }
        }
        return shares.stream().mapToDouble(Double::doubleValue).sorted().toArray();
    }

    @Test
    void theTypicalStormGrowsNoFasterThanItMoves() {
        // Storm.GROWTH is the median of edgeKeepingGrowth over a year of version 1's storms as they are born, rounded
        // to two places: half of all storms grow and shrink with both edges moving the storm's way. It is shared by
        // every storm (it is in the API), so version 2's larger storms keep it; their share is measured and shown.
        for (StormMaths maths : List.of(StormMathsV1.INSTANCE, StormMathsV2.INSTANCE)) {
            double[] sorted = edgeKeepingGrowths(maths);
            double median = sorted[sorted.length / 2];
            long keeping = Arrays.stream(sorted).filter(share -> share <= Storm.GROWTH).count();
            System.out.printf("version %d, a year of %d storms: edge-keeping growth 10th %.3f, median %.3f, 90th %.3f; "
                            + "at %.2f, %.0f %% of storms keep both edges moving their way%n", maths.version(),
                    sorted.length, sorted[sorted.length / 10], median, sorted[sorted.length * 9 / 10], Storm.GROWTH,
                    keeping * 100.0 / sorted.length);
            if (maths == StormMathsV1.INSTANCE) {
                assertEquals(median, Storm.GROWTH, 0.005);
            } else {
                // Larger storms on the same paths: about 28 % come and go with both edges moving their way.
                assertTrue(keeping * 4 > sorted.length, "a quarter of version 2's storms or more keep both edges moving");
            }
        }
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
    void theCalibrationArithmeticHoldsTheShare() {
        // The solved count, put back through the coverage formula, gives the share asked for exactly: vanilla's on
        // version 1, the era's on version 2.
        for (SubSeason.Season season : SubSeason.Season.values()) {
            for (StormMaths maths : List.of(StormMathsV1.INSTANCE, StormMathsV2.INSTANCE)) {
                StormCharacter character = maths.character(season);
                boolean first = maths == StormMathsV1.INSTANCE;
                double share = first ? VANILLA : SHARE;
                double density = first ? StormMathsV1.INSTANCE.density(season)
                        : StormMathsV2.INSTANCE.density(season, StormMathsV2.DEFAULT_RAIN_SHARE);
                double thunderChance = first ? StormMathsV1.INSTANCE.thunderChance(season)
                        : StormMathsV2.INSTANCE.thunderChance(season, StormMathsV2.DEFAULT_RAIN_SHARE);
                double coreAreaTime = Math.PI * Storm.RAIN_CORE * Storm.RAIN_CORE * character.meanSquaredRadius()
                        * StormCharacter.lifeAreaFactor() * character.meanLife();
                double cover = density * coreAreaTime / ((double) StormMathsV1.CELL * StormMathsV1.CELL * DAY);
                assertEquals(share, 1.0 - Math.exp(-cover), 1e-12);
                assertTrue(thunderChance > 0.0 && thunderChance <= 1.0);
                System.out.printf("version %d, %s: %.3f storms per cell a day, %.1f %% thunderstorms%n",
                        maths.version(), season, density, thunderChance * 100);
            }
        }
        assertTrue(Arrays.stream(SubSeason.Season.values()).mapToDouble(season ->
                StormMathsV2.INSTANCE.character(season).thunderShareOfRain()).average().orElseThrow() > 0.085);
    }
}
