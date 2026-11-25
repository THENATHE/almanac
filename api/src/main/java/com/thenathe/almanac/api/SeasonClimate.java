package com.thenathe.almanac.api;

import com.thenathe.almanac.internal.BiomeClimate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * A biome's climate in the current sub-season, from {@link AlmanacApi#seasonClimateOf} (since 0.2.0): what the
 * seasonal biome copies of 0.1.x carried, without a biome of their own. Ask this rather than the biome's own climate
 * methods ({@link Biome#coldEnoughToSnow}, {@link Biome#hasPrecipitation}, {@link Biome#getBaseTemperature}), which
 * only know the biome's all-year climate. A value for one moment: ask again after
 * {@link AlmanacEvents#SEASON_CHANGED}. Every method is safe from any thread.
 */
public final class SeasonClimate {
    private final BiomeClimate entry;
    private final SubSeason subSeason;
    private final boolean rainReaches;

    SeasonClimate(BiomeClimate entry, SubSeason subSeason, boolean rainReaches) {
        this.entry = entry;
        this.subSeason = subSeason;
        this.rainReaches = rainReaches;
    }

    /** The biome this is the climate of. */
    public Holder<Biome> biome() {
        return entry.biome();
    }

    public SubSeason subSeason() {
        return subSeason;
    }

    /** The biome's climate in spring and fall. */
    public Climate base() {
        return entry.base();
    }

    /** The climate this sub-season: one step colder in winter and one step warmer in summer. */
    public Climate seasonal() {
        return entry.seasonal(subSeason);
    }

    /** Whether the current (or most recent) rain event reaches this climate; when it doesn't, nothing falls. */
    public boolean rainReaches() {
        return rainReaches;
    }

    /**
     * Whether rain or snow can fall here this sub-season when an event reaches the climate: false for HOT biomes from
     * early spring to late fall and TEMPERATE ones in mid summer, where the sky darkens in a storm with nothing falling.
     */
    public boolean canFall() {
        return entry.canFall(subSeason);
    }

    /**
     * The biome's temperature this sub-season, before vanilla's cooling with height and its temperature modifier: what
     * {@link Biome#getBaseTemperature} would say if the biome had the season's climate.
     */
    public float temperature() {
        return entry.temperature(subSeason);
    }

    /**
     * Whether the season never brings snow or ice to this biome ({@code #almanac:no_seasonal_snow}, jungles by
     * default): it gets rain where its climate would give snow, and is never cold enough to snow.
     */
    public boolean noSeasonalSnow() {
        return entry.noSeasonalSnow();
    }

    /** The season's temperature at a position: cooled with height as vanilla cools it. */
    public float temperatureAt(BlockPos pos, int seaLevel) {
        return entry.temperatureAt(subSeason, pos, seaLevel);
    }

    /** Whether the season makes a position cold enough for snow, whether or not anything falls. */
    public boolean coldEnoughToSnow(BlockPos pos, int seaLevel) {
        return entry.coldEnoughToSnow(subSeason, pos, seaLevel);
    }

    /**
     * What falls at a position this sub-season when an event reaches the climate ({@code NONE}, {@code RAIN} or
     * {@code SNOW}); {@link AlmanacApi#precipitationOf} also applies the rain reach.
     */
    public Biome.Precipitation precipitationAt(BlockPos pos, int seaLevel) {
        return entry.precipitationAt(subSeason, pos, seaLevel);
    }

    @Override
    public String toString() {
        return "SeasonClimate[" + biome().unwrapKey().map(key -> key.identifier().toString()).orElse("?") + ", "
                + subSeason.id() + ", " + base().id() + " now " + seasonal().id() + (canFall() ? "" : ", dry")
                + ", temperature " + temperature() + (rainReaches ? "" : ", not reached") + "]";
    }
}
