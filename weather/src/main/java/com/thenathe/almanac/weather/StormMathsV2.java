package com.thenathe.almanac.weather;

import com.thenathe.almanac.api.Storm;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.api.Wind;
import java.util.EnumMap;
import java.util.Map;

/**
 * Version 2 of the storm maths, Almanac 1.1.0's. Released, so frozen: every number and every draw stays as it is,
 * in this order, or worlds that ran on it would get a different past.
 *
 * <p>The lattice, the draws and their order are version 1's ({@link StormMathsV1}); three things changed. Every
 * place is under rain the share of the time the era records ({@code WeatherRecord.Era#rainShare}, the server's
 * {@code timeUnderRain}, 25 % by default) where version 1 had vanilla's 15.8 % built in. Storms are larger, their
 * radii 1.4 times version 1's (twice the area), so the storm count solved for 25 % comes out lower than version 1's
 * in every season (user, 2026-11-25: "make the rain areas moderately larger but more infrequent"). And each season's
 * thunder keeps its share of the rain time, so thunder comes in step with the rain.
 *
 * <p>The wind is version 1's, drawn the same from the same seed, so it doesn't turn at the tick a world takes up this
 * version; storms born either side of it drift alike.
 */
final class StormMathsV2 implements StormMaths {
    static final int VERSION = 2;
    static final StormMathsV2 INSTANCE = new StormMathsV2();

    /** The share of time under rain, in percent, an era asks for when it records none. */
    static final int DEFAULT_RAIN_SHARE = 25;

    static final int CELL = StormMathsV1.CELL;
    static final int DAY = StormMathsV1.DAY;
    /** A storm's own turn away from the wind, at most, in radians. */
    private static final double DRIFT_TURN = Math.toRadians(30.0);
    /** How far a storm's long axis may lie from straight across its path, in radians. */
    private static final double AXIS_TURN = Math.toRadians(35.0);
    private static final double WIND_SPEED = 0.07;
    private static final double WIND_GUST = 0.25;
    private static final int[] CLOUD_TOP = {200, 40};
    private static final int[] THUNDER_CLOUD_TOP = {250, 70};

    private final Map<SubSeason.Season, StormCharacter> characters = new EnumMap<>(SubSeason.Season.class);
    private final double reach;
    private final int longestLife;

    private StormMathsV2() {
        // Version 1's seasons with every radius 1.4 times as large; stretch, life, speed and thunder's share as they were.
        characters.put(SubSeason.Season.SPRING,
                new StormCharacter("spring", 560, 2100, 3.0, 14_000, 40_000, 0.05, 0.10, 0.10));
        characters.put(SubSeason.Season.SUMMER,
                new StormCharacter("summer", 420, 1540, 2.0, 8_000, 28_000, 0.06, 0.12, 0.18));
        characters.put(SubSeason.Season.FALL,
                new StormCharacter("fall", 560, 2100, 3.0, 14_000, 40_000, 0.05, 0.10, 0.07));
        characters.put(SubSeason.Season.WINTER,
                new StormCharacter("winter", 840, 2520, 2.2, 24_000, 52_000, 0.04, 0.08, 0.02));
        double furthest = 0.0;
        int longest = 0;
        for (StormCharacter character : characters.values()) {
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

    @Override
    public boolean followsRainShare() {
        return true;
    }

    /** An era's share of time under rain as a part of 1; a share it doesn't record is the default. */
    static double rainShare(int percent) {
        return (percent > 0 ? percent : DEFAULT_RAIN_SHARE) / 100.0;
    }

    double density(SubSeason.Season season, int rainShare) {
        return characters.get(season).density(CELL, DAY, rainShare(rainShare));
    }

    double thunderChance(SubSeason.Season season, int rainShare) {
        return characters.get(season).thunderChance(rainShare(rainShare));
    }

    @Override
    public Storm[] cell(long seed, int i, int j, long day, SubSeason.Season season, int rainShare) {
        StormCharacter character = characters.get(season);
        double thunderChance = thunderChance(season, rainShare);
        Hashes.Draws cellDraws = new Hashes.Draws(Hashes.of(seed, VERSION, season.ordinal(), i, j, day));
        // A larger share draws more storms from the same draw, and storm n is the same storm whatever the count.
        int count = StormMathsV1.poisson(cellDraws.next(), density(season, rainShare));
        Storm[] storms = new Storm[count];
        for (int n = 0; n < count; n++) {
            long id = Hashes.of(seed, VERSION, i, j, day, n);
            storms[n] = storm(seed, id, i, j, day, character, thunderChance);
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

    @Override
    public Wind wind(long seed, double weatherTick) {
        return StormMathsV1.INSTANCE.wind(seed, weatherTick);
    }
}
