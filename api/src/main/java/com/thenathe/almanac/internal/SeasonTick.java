package com.thenathe.almanac.internal;

/**
 * What the snow follows on one server tick, as core hands it over: the season's climate, whether it is raining in the
 * overworld, which weather decides the rain, and fingerprints of what the climate table was built from, which every
 * chunk's snow state saves.
 *
 * <p>With regional weather off ({@code weatherVersion} 0) core takes the rain from vanilla's one weather and the
 * climates it reaches from the rain shares, as up to 0.3.0. With it on (from 0.4.0) the storms decide where snow
 * falls, the snow asks the weather module for each area, and {@code raining} is false: vanilla's weather is held
 * clear.
 *
 * @param weatherVersion  0 while vanilla's one weather decides the rain; otherwise the storm maths in use
 * @param climateSettings the fingerprint of the three climate settings ({@code ClimateSettings#fingerprint})
 * @param noSeasonalSnow  the fingerprint of the biomes in {@code #almanac:no_seasonal_snow}
 */
public record SeasonTick(ClimateNow climate, boolean raining, int weatherVersion, int climateSettings,
                         int noSeasonalSnow) {
    /**
     * {@code climateSettings} of the default settings, which snow states saved before 0.1.3 were reached with. It is
     * in every chunk record (core's tests check it against the settings).
     */
    public static final int DEFAULT_CLIMATE_SETTINGS = -1_593_805_681;
    /** {@code noSeasonalSnow} as states saved before 0.2.0 read it: no rule at all. */
    public static final int NO_SEASONAL_SNOW_UNKNOWN = Integer.MIN_VALUE;
}
