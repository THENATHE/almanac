package com.thenathe.almanac.client;

import com.thenathe.almanac.climate.ClimateHooks;
import com.thenathe.almanac.climate.ClimateTable;
import com.thenathe.almanac.climate.LocalWeather;
import com.thenathe.almanac.climate.SeasonalClimate;
import com.thenathe.almanac.internal.WeatherHere;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * The rain and thunder this client shows, where the camera is. {@code Level#getRainLevel} and
 * {@code Level#getThunderLevel} on this client give them ({@code LevelRainMixin}), so the sky, the rain drawn, the
 * fog and every mod that reads those levels (LiteLights among them) follow it.
 *
 * <p><b>With regional weather</b> (from 0.4.0) they come from the storm over the camera, which the client works out
 * itself from the weather record the server sent and the game time; vanilla's own levels, which the server holds at
 * nothing, don't count. Under a storm that reaches the climate there, its rain; under one that passes the climate
 * by, nothing; where nothing can fall this sub-season, the whole storm, dark and dry ({@link LocalWeather}).
 *
 * <p><b>Without it</b> (as up to 0.3.0) the server sends the world's rain and thunder levels as vanilla does, and the
 * player gets a share of them where they stand, from the season the server syncs:
 * <ul>
 *   <li>where rain or snow can fall this sub-season, all of the rain if the current event reaches the climate there
 *   and none if it doesn't, so the sky stays clear wherever nothing will fall;</li>
 *   <li>where nothing can fall this sub-season (deserts and badlands outside winter, temperate land in mid summer),
 *   all of it: the sky darkens as in plain Minecraft and nothing falls, which mods such as LiteLights draw as a
 *   sandstorm (user, 2026-11-17).</li>
 * </ul>
 * Either way the levels fade at vanilla's own rate as the player moves or the weather passes, and start where they
 * should when the player arrives. Client thread only, but for the levels, which any thread may read.
 */
public final class ClientRain {
    /** Vanilla fades rain in and out by 0.01 a tick. */
    private static final float FADE_PER_TICK = 0.01F;

    private static @Nullable ClientLevel followed;
    /** Whether the levels have been set from a known season in {@link #followed}, so they fade from here on. */
    private static boolean started;
    private static boolean regional;
    /** Without regional weather, the share of the world's rain; with it, the rain level itself. */
    private static float share = 1.0F;
    private static float previousShare = 1.0F;
    /** With regional weather, the thunder level. */
    private static float thunder;
    private static float previousThunder;
    /** With regional weather, the storm overhead whatever falls from it: the "world's own" rain at the camera. */
    private static float storm;
    private static float previousStorm;
    private static float stormThunder;

    private ClientRain() {
    }

    /** Once a client tick: moves the levels towards the ones where the camera stands. */
    static void tick(Minecraft client) {
        ClientLevel level = client.level;
        Entity camera = client.getCameraEntity();
        if (level == null || camera == null) {
            forget();
            return;
        }
        SeasonalClimate climate = ClimateHooks.forLevel(level);
        boolean regionalNow = LocalWeather.active(level);
        BlockPos pos = BlockPos.containing(camera.getEyePosition());
        float rainTarget;
        float thunderTarget = 0.0F;
        float stormTarget = 0.0F;
        float stormThunderTarget = 0.0F;
        if (regionalNow) {
            WeatherHere here = LocalWeather.storms(level, camera.getEyePosition());
            rainTarget = LocalWeather.rainLevel(level, pos, here);
            thunderTarget = LocalWeather.thunderLevel(level, pos, here);
            stormTarget = here.storm();
            stormThunderTarget = here.thunder();
        } else {
            rainTarget = climate == null ? 1.0F : shareAt(climate, level, pos);
        }
        previousShare = share;
        previousThunder = thunder;
        previousStorm = storm;
        if (level != followed || !started || regionalNow != regional) {
            followed = level;
            started = climate != null;
            regional = regionalNow;
            share = previousShare = rainTarget;
            thunder = previousThunder = thunderTarget;
            storm = previousStorm = stormTarget;
            stormThunder = stormThunderTarget;
            return;
        }
        share = fade(share, rainTarget);
        thunder = fade(thunder, thunderTarget);
        storm = fade(storm, stormTarget);
        stormThunder = fade(stormThunder, stormThunderTarget);
    }

    private static float fade(float level, float target) {
        return level + Math.clamp(target - level, -FADE_PER_TICK, FADE_PER_TICK);
    }

    static void forget() {
        followed = null;
        started = false;
        regional = false;
        share = previousShare = 1.0F;
        thunder = previousThunder = 0.0F;
        storm = previousStorm = 0.0F;
        stormThunder = 0.0F;
    }

    /** The rain level this client shows: the storm's with regional weather, otherwise the world's times the share. */
    public static float rainLevel(ClientLevel level, float worldRain, float partialTick) {
        if (level != followed) {
            return worldRain;
        }
        float here = Mth.lerp(partialTick, previousShare, share);
        return regional ? here : worldRain * here;
    }

    /**
     * With regional weather, the thunder level this client shows ({@code Level#getThunderLevel}): the thunder where
     * the camera is, times the rain there as vanilla multiplies them. Null without it, for vanilla's own.
     */
    public static @Nullable Float thunderLevel(ClientLevel level, float partialTick) {
        if (level != followed || !regional) {
            return null;
        }
        return Mth.lerp(partialTick, previousThunder, thunder) * rainLevel(level, 0.0F, partialTick);
    }

    /**
     * With regional weather, the storm over the camera whatever falls from it, for {@code AlmanacApi.worldRainLevel}
     * (a sandstorm's strength); null without it.
     */
    public static @Nullable Float stormLevel(ClientLevel level, boolean thunderToo) {
        if (level != followed || !regional) {
            return null;
        }
        return thunderToo ? stormThunder * storm : storm;
    }

    /** For the QA driver: the levels now and what they are heading for. */
    public static String status(Minecraft client) {
        ClientLevel level = client.level;
        Entity camera = client.getCameraEntity();
        SeasonalClimate climate = level == null ? null : ClimateHooks.forLevel(level);
        if (level != null && camera != null && LocalWeather.active(level)) {
            WeatherHere here = LocalWeather.storms(level, camera.getEyePosition());
            BlockPos pos = BlockPos.containing(camera.getEyePosition());
            return String.format(Locale.ROOT, "regional rain %.3f target %.3f thunder %.3f target %.3f storm %.3f shown rain"
                            + " %.3f thunder %.3f world rain %.3f",
                    share, LocalWeather.rainLevel(level, pos, here), thunder, LocalWeather.thunderLevel(level, pos, here),
                    storm, level.getRainLevel(1.0F), level.getThunderLevel(1.0F), ClimateHooks.worldWeather(level, false));
        }
        float target = climate == null || camera == null ? 1.0F : shareAt(climate, level, camera.blockPosition());
        return String.format(Locale.ROOT, "share %.2f target %.2f world rain %.3f shown rain %.3f thunder %.3f", share, target,
                level == null ? 0.0F : ClimateHooks.worldWeather(level, false), level == null ? 0.0F : level.getRainLevel(1.0F),
                level == null ? 0.0F : level.getThunderLevel(1.0F));
    }

    private static float shareAt(SeasonalClimate climate, ClientLevel level, BlockPos pos) {
        ClimateTable.Entry entry = climate.entry(level.getBiome(pos).value());
        if (entry == null) {
            return 1.0F;
        }
        return share(entry.canFall(climate.subSeason()), climate.reached().contains(entry.base()));
    }

    /**
     * @param canFall something can fall here this sub-season
     * @param reached the current event reaches the climate here
     */
    static float share(boolean canFall, boolean reached) {
        return !canFall || reached ? 1.0F : 0.0F;
    }
}
