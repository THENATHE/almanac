package com.thenathe.almanac.biome;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.thenathe.almanac.climate.ClimateSorter;
import com.thenathe.almanac.climate.WeatherKind;
import com.thenathe.almanac.colour.ColourChannel;
import com.thenathe.almanac.colour.Colormaps;
import com.thenathe.almanac.colour.SeasonalPalette;
import java.util.Locale;
import java.util.function.Function;

/**
 * Turns a biome, as encoded by the biome codec, into its copy for one sub-season. Only the climate
 * ({@code has_precipitation}, {@code temperature}, {@code temperature_modifier}) and the three seasonal colours
 * change; every other field, including ones added by future versions, is carried over as it was.
 */
public final class SeasonalCopies {
    /** Vanilla starts cooling the air 17 blocks above sea level, by 0.05 for every 40 blocks. */
    private static final int VANILLA_SNOW_LEVEL = 17;
    private static final float COOLING_PER_BLOCK = 0.05F / 40.0F;

    private SeasonalCopies() {
    }

    /** The temperatures the copies use. They are built once, at server start. */
    public record CopyClimate(float winterTemperature, float summerTemperature, int snowLineAboveSea) {
        /** A base temperature at which vanilla's cooling with height reaches the snow line at the given height. */
        public float snowLineTemperature() {
            return ClimateSorter.SNOW_TEMPERATURE + (snowLineAboveSea - VANILLA_SNOW_LEVEL) * COOLING_PER_BLOCK;
        }
    }

    public static JsonObject copy(JsonObject original, WeatherKind kind, CopyClimate climate,
                                  Function<ColourChannel, SeasonalPalette.Step> colours) {
        JsonObject copy = original.deepCopy();
        float temperature = original.get("temperature").getAsFloat();
        float downfall = original.get("downfall").getAsFloat();
        float seasonal = temperature;
        boolean snowsAtGround = temperature < ClimateSorter.SNOW_TEMPERATURE;

        copy.addProperty("has_precipitation", kind != WeatherKind.DRY);
        switch (kind) {
            case DRY -> {
            }
            case RAIN -> {
                if (snowsAtGround) {
                    seasonal = Math.max(temperature, climate.summerTemperature());
                }
            }
            case SNOW -> {
                if (!snowsAtGround) {
                    seasonal = Math.min(temperature, climate.winterTemperature());
                }
            }
            case SNOW_LINE -> seasonal = climate.snowLineTemperature();
        }
        boolean temperatureChanged = seasonal != temperature;
        if (temperatureChanged) {
            copy.addProperty("temperature", seasonal);
            // Frozen oceans warm their ice patches with this modifier; the season decides instead.
            copy.remove("temperature_modifier");
        }

        JsonObject effects = copy.getAsJsonObject("effects");
        for (ColourChannel channel : ColourChannel.values()) {
            SeasonalPalette.Step step = colours.apply(channel);
            JsonElement override = effects.get(channel.biomeField());
            if (step.isUnchanged() && (override != null || !temperatureChanged)) {
                continue;
            }
            // Without an override the colour follows the temperature, so a changed temperature needs it written out.
            int base = override != null ? readColour(override) : Colormaps.sample(channel, temperature, downfall);
            effects.add(channel.biomeField(), new JsonPrimitive(writeColour(step.apply(base))));
        }
        return copy;
    }

    static int readColour(JsonElement element) {
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            return primitive.getAsInt() & 0xFFFFFF;
        }
        return Integer.parseInt(primitive.getAsString().substring(1), 16) & 0xFFFFFF;
    }

    static String writeColour(int rgb) {
        return String.format(Locale.ROOT, "#%06x", rgb & 0xFFFFFF);
    }
}
