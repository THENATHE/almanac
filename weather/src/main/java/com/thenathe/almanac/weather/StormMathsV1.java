package com.thenathe.almanac.weather;

import com.thenathe.almanac.api.Storm;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.api.Wind;
import java.util.EnumMap;
import java.util.Map;

/**
 * Version 1 of the storm maths, Almanac 0.4.0's. Released, so frozen: every number and every draw stays as it is,
 * in this order, or worlds that ran on it would get a different past.
 *
 * <p>The world is a lattice of cells 2,048 blocks square and one day (24,000 weather ticks) long. Each cell holds a
 * Poisson number of storms, drawn from the weather seed and the cell, with the mean its season's character solves for
 * vanilla's share of rain. Each storm is born at an even draw of place and time inside its cell, with a size, stretch,
 * life, strength, cloud top and thunder of its own, and drifts with the prevailing wind at its birth, turned by up to
 * 30 degrees, for life. Its long axis lies across its path, turned by up to 35 degrees, so a front moves broadside on.
 *
 * <p>The seasons: spring and fall have middling storms with more fronts; summer has small, short-lived, quick storms
 * that thunder more often; winter has large, slow, long-lived storms that seldom thunder, the long snowstorms of
 * winter. Thunder over the year averages vanilla's 9 % of the rain time.
 */
final class StormMathsV1 implements StormMaths {
    static final int VERSION = 1;
    static final StormMathsV1 INSTANCE = new StormMathsV1();

    static final int CELL = 2048;
    static final int DAY = 24_000;
    /** A storm's own turn away from the wind, at most, in radians. */
    private static final double DRIFT_TURN = Math.toRadians(30.0);
    /** How far a storm's long axis may lie from straight across its path, in radians. */
    private static final double AXIS_TURN = Math.toRadians(35.0);
    private static final double WIND_SPEED = 0.07;
    private static final double WIND_GUST = 0.25;
    // The wind's direction wanders as three slow waves of these lengths and sizes (radians), so it comes round over
    // days and never repeats in a year; its strength as one more.
    private static final double[] WIND_PERIODS = {7.3 * DAY, 17.9 * DAY, 2.9 * DAY};
    private static final double[] WIND_SWINGS = {1.6, 1.0, 0.45};
    private static final double GUST_PERIOD = 5.3 * DAY;
    private static final int[] CLOUD_TOP = {200, 40};
    private static final int[] THUNDER_CLOUD_TOP = {250, 70};

    private final Map<SubSeason.Season, StormCharacter> characters = new EnumMap<>(SubSeason.Season.class);
    private final Map<SubSeason.Season, Double> densities = new EnumMap<>(SubSeason.Season.class);
    private final Map<SubSeason.Season, Double> thunderChances = new EnumMap<>(SubSeason.Season.class);
    private final double reach;
    private final int longestLife;

    private StormMathsV1() {
        characters.put(SubSeason.Season.SPRING,
                new StormCharacter("spring", 400, 1500, 3.0, 14_000, 40_000, 0.05, 0.10, 0.10));
        characters.put(SubSeason.Season.SUMMER,
                new StormCharacter("summer", 300, 1100, 2.0, 8_000, 28_000, 0.06, 0.12, 0.18));
        characters.put(SubSeason.Season.FALL,
                new StormCharacter("fall", 400, 1500, 3.0, 14_000, 40_000, 0.05, 0.10, 0.07));
        characters.put(SubSeason.Season.WINTER,
                new StormCharacter("winter", 600, 1800, 2.2, 24_000, 52_000, 0.04, 0.08, 0.02));
        double furthest = 0.0;
        int longest = 0;
        for (Map.Entry<SubSeason.Season, StormCharacter> entry : characters.entrySet()) {
            StormCharacter character = entry.getValue();
            densities.put(entry.getKey(), character.density(CELL, DAY));
            thunderChances.put(entry.getKey(), character.thunderChance());
            double travel = character.maxSpeed() * (1.0 + WIND_GUST) * character.maxLife();
            furthest = Math.max(furthest, travel + character.maxRadius() * Math.sqrt(character.maxStretch()));
            longest = Math.max(longest, character.maxLife());
        }
        reach = furthest;
        longestLife = longest;
    }

    @Override
    public int version() {
        return VERSION;
    }

    @Override
    public int cellSize() {
        return CELL;
    }

    @Override
    public int dayTicks() {
        return DAY;
    }

    @Override
    public double reach() {
        return reach;
    }

    @Override
    public double reach(SubSeason.Season season, double age) {
        return Math.min(reach, characters.get(season).reach(WIND_GUST, age));
    }

    @Override
    public int longestLife() {
        return longestLife;
    }

    @Override
    public StormCharacter character(SubSeason.Season season) {
        return characters.get(season);
    }

    double density(SubSeason.Season season) {
        return densities.get(season);
    }

    double thunderChance(SubSeason.Season season) {
        return thunderChances.get(season);
    }

    @Override
    public boolean followsRainShare() {
        return false;
    }

    @Override
    public Storm[] cell(long seed, int i, int j, long day, SubSeason.Season season, int rainShare) {
        return cell(seed, i, j, day, season);
    }

    /** The storms of a cell, at vanilla's share of rain: the only share version 1 knows. */
    Storm[] cell(long seed, int i, int j, long day, SubSeason.Season season) {
        StormCharacter character = characters.get(season);
        Hashes.Draws cellDraws = new Hashes.Draws(Hashes.of(seed, VERSION, season.ordinal(), i, j, day));
        int count = poisson(cellDraws.next(), densities.get(season));
        Storm[] storms = new Storm[count];
        for (int n = 0; n < count; n++) {
            long id = Hashes.of(seed, VERSION, i, j, day, n);
            storms[n] = storm(seed, id, i, j, day, character, thunderChances.get(season));
        }
        return storms;
    }

    private Storm storm(long seed, long id, int i, int j, long day, StormCharacter character, double thunderChance) {
        Hashes.Draws draws = new Hashes.Draws(id);
        double birthX = (i + draws.next()) * CELL;
        double birthZ = (j + draws.next()) * CELL;
        long birthTick = day * DAY + (long) Math.floor(draws.next() * DAY);
        double radius = character.minRadius() * StrictMath.pow(character.maxRadius() / character.minRadius(), draws.next());
        // Most storms are round or nearly so; a few are long fronts.
        double stretchDraw = draws.next();
        double stretch = 1.0 + (character.maxStretch() - 1.0) * stretchDraw * stretchDraw;
        int life = (int) Math.round(draws.between(character.minLife(), character.maxLife()));
        Wind wind = wind(seed, birthTick);
        double heading = wind.direction() + draws.between(-DRIFT_TURN, DRIFT_TURN);
        double speed = draws.between(character.minSpeed(), character.maxSpeed()) * (wind.speed() / WIND_SPEED);
        double angle = heading + Math.PI / 2.0 + draws.between(-AXIS_TURN, AXIS_TURN);
        float strength = (float) draws.between(0.35, 1.0);
        boolean thunder = draws.next() < thunderChance;
        int[] top = thunder ? THUNDER_CLOUD_TOP : CLOUD_TOP;
        int cloudTop = top[0] + (int) Math.floor(draws.next() * top[1]);
        float reachDraw = (float) draws.next();
        return new Storm(id, birthX, birthZ, birthTick, life, StrictMath.cos(heading) * speed, StrictMath.sin(heading) * speed,
                radius, stretch, angle, strength, thunder, cloudTop, reachDraw);
    }

    /** A Poisson number with the given mean, from one even draw: the inverse of its distribution. */
    static int poisson(double draw, double mean) {
        double chance = StrictMath.exp(-mean);
        double below = chance;
        int count = 0;
        while (draw > below && count < 64) {
            count++;
            chance *= mean / count;
            below += chance;
        }
        return count;
    }

    @Override
    public Wind wind(long seed, double weatherTick) {
        Hashes.Draws draws = new Hashes.Draws(Hashes.of(seed, VERSION, 0x57_49_4E_44L));
        double direction = draws.next() * 2.0 * Math.PI;
        for (int wave = 0; wave < WIND_PERIODS.length; wave++) {
            double phase = draws.next() * 2.0 * Math.PI;
            direction += WIND_SWINGS[wave] * StrictMath.sin(2.0 * Math.PI * weatherTick / WIND_PERIODS[wave] + phase);
        }
        double gustPhase = draws.next() * 2.0 * Math.PI;
        double speed = WIND_SPEED * (1.0 + WIND_GUST * StrictMath.sin(2.0 * Math.PI * weatherTick / GUST_PERIOD + gustPhase));
        return new Wind(direction, speed);
    }
}
