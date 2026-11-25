package com.thenathe.almanac.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.Storm;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.internal.WeatherHere;
import com.thenathe.almanac.internal.WeatherRecord;
import com.thenathe.almanac.internal.WetSpell;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

/**
 * The same place and tick always get the same weather: from the index and directly, from a second field built from a
 * copy of the record (as a client builds its own from the server's), and from several threads at once. And the two
 * ways a storm's rain is worked out (now, and over a stretch) agree.
 */
class DeterminismTest {
    private static final long TICK = 30L * StormMathsV1.DAY + 7_777;

    private static List<double[]> places(long seed, int count, double centreX, double centreZ, double spread) {
        Random random = new Random(seed);
        List<double[]> places = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            places.add(new double[] {centreX + (random.nextDouble() * 2 - 1) * spread,
                    centreZ + (random.nextDouble() * 2 - 1) * spread, 40 + random.nextInt(260),
                    TICK + random.nextInt(60)});
        }
        return places;
    }

    /** A place that is under a storm at {@link #TICK}, so the comparisons below compare real rain, not only clear sky. */
    private static double[] stormyPlace(WeatherField field) {
        for (int step = 0; step < 4_000; step++) {
            double x = step * 97.0;
            double z = step * -61.0;
            if (field.at(x, 64, z, TICK).stormy()) {
                return new double[] {x, z};
            }
        }
        throw new AssertionError("no storm found");
    }

    @Test
    void theIndexAnswersAsTheFieldDoes() {
        WeatherField field = Worlds.field(Worlds.heldIn(SubSeason.MID_SPRING));
        double[] centre = stormyPlace(field);
        StormIndex index = StormIndex.build(field, List.of(new StormIndex.Area(centre[0], centre[1], 400)),
                field.weatherTick(TICK), field.weatherTick(TICK + 80));
        int stormy = 0;
        for (double[] place : places(1, 4_000, centre[0], centre[1], 400)) {
            assertTrue(index.covers(place[0], place[1], place[3]));
            WeatherHere direct = field.at(place[0], place[2], place[1], (long) place[3]);
            assertEquals(direct, index.at(place[0], place[2], place[1], (long) place[3]));
            stormy += direct.stormy() ? 1 : 0;
        }
        assertTrue(stormy > 100, "enough of the places are under the storm to compare rain: " + stormy);
    }

    @Test
    void aCopyOfTheRecordGivesTheSameWeather() {
        WeatherRecord record = Worlds.year(8, 2);
        WeatherRecord copy = new WeatherRecord(record.seed(), new ArrayList<>(record.eras()), List.of(), List.of());
        WeatherField server = Worlds.field(record);
        WeatherField client = Worlds.field(copy);
        double[] centre = stormyPlace(server);
        for (double[] place : places(2, 2_000, centre[0], centre[1], 3_000)) {
            assertEquals(server.at(place[0], place[2], place[1], (long) place[3]),
                    client.at(place[0], place[2], place[1], (long) place[3]));
        }
        assertEquals(server.stormsNear(centre[0], centre[1], 4_000, server.weatherTick(TICK)),
                client.stormsNear(centre[0], centre[1], 4_000, client.weatherTick(TICK)));
    }

    @Test
    void severalThreadsGetTheSameAnswers() throws Exception {
        WeatherField field = Worlds.field(Worlds.heldIn(SubSeason.EARLY_WINTER));
        double[] centre = stormyPlace(field);
        List<double[]> places = places(3, 3_000, centre[0], centre[1], 5_000);
        List<WeatherHere> alone = new ArrayList<>();
        WeatherField fresh = Worlds.field(Worlds.heldIn(SubSeason.EARLY_WINTER));
        for (double[] place : places) {
            alone.add(fresh.at(place[0], place[2], place[1], (long) place[3]));
        }
        ExecutorService threads = Executors.newFixedThreadPool(4);
        try {
            List<Future<List<WeatherHere>>> answers = new ArrayList<>();
            for (int thread = 0; thread < 4; thread++) {
                answers.add(threads.submit(() -> {
                    List<WeatherHere> mine = new ArrayList<>();
                    for (double[] place : places) {
                        mine.add(field.at(place[0], place[2], place[1], (long) place[3]));
                    }
                    return mine;
                }));
            }
            for (Future<List<WeatherHere>> answer : answers) {
                assertEquals(alone, answer.get());
            }
        } finally {
            threads.shutdownNow();
        }
    }

    @Test
    void theRainNowAndTheRainOverAStretchAgree() {
        Random random = new Random(4);
        int checked = 0;
        for (int i = -3; i <= 3; i++) {
            for (Storm storm : StormMathsV1.INSTANCE.cell(Worlds.SEED, i, 2, 5, SubSeason.Season.SPRING)) {
                StormShape shape = new StormShape(storm);
                for (int point = 0; point < 30; point++) {
                    // Places along the storm's path, where its rain passes.
                    double along = random.nextDouble();
                    double tick = storm.birthTick() + along * storm.lifeTicks();
                    double x = storm.centreX(tick) + (random.nextDouble() * 2 - 1) * storm.radius();
                    double z = storm.centreZ(tick) + (random.nextDouble() * 2 - 1) * storm.radius();
                    List<Spans.Span> spans = shape.rainSpans(x, z, storm.birthTick(), storm.endTick());
                    for (int sample = 0; sample < 200; sample++) {
                        double at = storm.birthTick() + (sample + 0.5) / 200.0 * storm.lifeTicks();
                        boolean edge = spans.stream().anyMatch(span -> Math.abs(span.start() - at) < 1e-3
                                || Math.abs(span.end() - at) < 1e-3);
                        if (!edge) {
                            assertEquals(shape.rainsOn(x, z, at), Spans.covers(spans, at),
                                    "storm " + storm.id() + " at " + at);
                            checked++;
                        }
                    }
                }
            }
        }
        assertTrue(checked > 10_000);
    }

    @Test
    void atAStableTickWhatFallsChangesAndNotBefore() {
        // The snow follows a place forward until its stable tick: every tick before it falls as the start does, and at
        // it the weather has changed (a storm's edge has crossed).
        WeatherField field = Worlds.field(Worlds.heldIn(SubSeason.MID_SPRING));
        double[] centre = stormyPlace(field);
        Random random = new Random(5);
        int changes = 0;
        for (int sample = 0; sample < 1_500; sample++) {
            double x = centre[0] + (random.nextDouble() * 2 - 1) * 1_500;
            double z = centre[1] + (random.nextDouble() * 2 - 1) * 1_500;
            long from = TICK + random.nextInt(20_000);
            WetSpell start = field.wetSpell(x, z, from, from);
            boolean wet = start.wetAtEnd(Climate.TEMPERATE);
            long stable = start.stableUntil();
            if (stable >= from + WeatherField.HORIZON) {
                continue;
            }
            assertEquals(!wet, field.at(x, 64, z, stable).reaches(Climate.TEMPERATE),
                    "at the stable tick " + stable + " from " + from);
            assertEquals(wet, field.at(x, 64, z, stable - 1).reaches(Climate.TEMPERATE),
                    "the tick before " + stable);
            changes++;
        }
        assertTrue(changes > 40, "edges crossed: " + changes);
    }

    @Test
    void theWeatherSeedHidesTheWorldSeed() {
        long worldSeed = 123_456_789L;
        assertEquals(Hashes.weatherSeed(worldSeed), Hashes.weatherSeed(worldSeed));
        assertNotEquals(worldSeed, Hashes.weatherSeed(worldSeed));
        assertNotEquals(Hashes.weatherSeed(worldSeed), Hashes.weatherSeed(worldSeed + 1));
    }

    @Test
    void stormsAreTheSameWhenWorkedOutAgain() {
        Storm[] first = StormMathsV1.INSTANCE.cell(Worlds.SEED, 11, -4, 300, SubSeason.Season.SUMMER);
        Storm[] again = StormMathsV1.INSTANCE.cell(Worlds.SEED, 11, -4, 300, SubSeason.Season.SUMMER);
        assertEquals(List.of(first), List.of(again));
    }
}
