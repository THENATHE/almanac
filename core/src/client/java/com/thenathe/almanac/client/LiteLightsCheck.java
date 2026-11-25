package com.thenathe.almanac.client;

import com.thenathe.almanac.Almanac;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Whether the LiteLights installed beside Almanac is new enough for each feature the two share. When one of the two
 * mods is too old for the other, the features they share switch off with a notice, and neither keeps code for the
 * other's older versions (user, 2026-11-24). The log says so as the game starts, and the chat the first time the
 * player joins a world.
 *
 * <p>Both mods read the other's version from Fabric Loader; LiteLights does the same the other way round.
 */
public final class LiteLightsCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(Almanac.MOD_ID);
    static final String LITELIGHTS_ID = "litelights";

    /** A feature Almanac shares with LiteLights, and the first LiteLights that works with this Almanac's side of it. */
    enum Shared {
        /**
         * Far terrain's seasonal snow. LiteLights before 1.0.7 times far snow by {@code AlmanacApi.thawTicks} and
         * {@code formingTicks}, which 0.1.7 removed: it asks for them once what far terrain shows changes, or once the
         * server has said when it last changed, and would stop on the missing call. So with such a LiteLights this
         * client doesn't take the climate map. The server then sends none, the far lookups answer unknown,
         * {@code FAR_CLIMATE_CHANGED} never reports the snow lines changing and {@code farSnowChangedTick} stays empty,
         * so LiteLights never reaches those calls and keeps the snow far terrain was captured with.
         */
        FAR_TERRAIN("1.0.7", "almanac.notice.litelights_too_old",
                "Almanac: far terrain's seasonal snow is off. LiteLights %s is older than %s, the first that works with "
                        + "this Almanac. Update LiteLights to turn it back on."),
        /**
         * The season's climate in LiteLights' weather: how cold and how dry a place is this sub-season. LiteLights
         * 1.0.7 and 1.0.8 ask for it as Almanac's seasonal biome copies ({@code AlmanacApi.seasonalBiome}), which went
         * in 0.2.0. That call stays, answering that there is no seasonal biome, which those LiteLights take as Almanac
         * having nothing to say: their weather looks then go by each biome's own climate. What falls still follows the
         * season ({@code precipitationOf}). LiteLights 1.0.9 is the first that reads {@code AlmanacApi.seasonClimateOf}
         * instead (until 0.3.0 this said 1.0.8, a guess made before 1.0.9 shipped).
         */
        SEASONAL_CLIMATE("1.0.9", "almanac.notice.litelights_climate",
                "Almanac: LiteLights %s asks for seasonal biome copies, which this Almanac no longer has, so its weather "
                        + "looks go by each biome's own climate (rain and snow still follow the season). LiteLights %s "
                        + "or later works with this Almanac.");

        final String firstSupported;
        final String key;
        final String text;

        Shared(String firstSupported, String key, String text) {
            this.firstSupported = firstSupported;
            this.key = key;
            this.text = text;
        }
    }

    /** The installed LiteLights' version, or null without LiteLights; looked up once. */
    private static @Nullable Optional<Version> installed;
    private static boolean told;

    private LiteLightsCheck() {
    }

    /** Whether far terrain is shared with LiteLights: true without LiteLights, or with 1.0.7 or later. */
    public static boolean farTerrainShared() {
        return !isOff(Shared.FAR_TERRAIN);
    }

    /** The installed LiteLights' version when it is too old to share far terrain, for the server's {@code /almanac snow}. */
    public static Optional<String> tooOldForFarTerrain() {
        return isOff(Shared.FAR_TERRAIN) ? installed().map(Version::getFriendlyString) : Optional.empty();
    }

    /** Logs which shared features are off, once as the client starts. */
    static void logAtStart() {
        installed().ifPresent(version -> {
            for (Shared feature : off()) {
                LOGGER.warn("Almanac: LiteLights {} is older than {}, the first that works with this Almanac's {}; that "
                        + "part is off until LiteLights is updated", version.getFriendlyString(), feature.firstSupported,
                        feature == Shared.FAR_TERRAIN ? "far terrain" : "seasonal climate");
            }
        });
    }

    /** Says so in chat when the player first joins a world this session. */
    static void joined(Minecraft client) {
        Optional<Version> version = installed();
        if (told || version.isEmpty() || off().isEmpty()) {
            return;
        }
        told = true;
        for (Shared feature : off()) {
            client.gui.hud.getChat().addClientSystemMessage(Component.translatableWithFallback(feature.key, feature.text,
                    version.get().getFriendlyString(), feature.firstSupported));
        }
    }

    private static boolean isOff(Shared feature) {
        return installed().map(version -> tooOld(version, feature)).orElse(false);
    }

    private static List<Shared> off() {
        List<Shared> off = new ArrayList<>();
        for (Shared feature : Shared.values()) {
            if (isOff(feature)) {
                off.add(feature);
            }
        }
        return off;
    }

    private static Optional<Version> installed() {
        if (installed == null) {
            installed = FabricLoader.getInstance().getModContainer(LITELIGHTS_ID)
                    .map(litelights -> litelights.getMetadata().getVersion());
        }
        return installed;
    }

    /**
     * Whether a LiteLights version is older than the first that shares a feature with this Almanac. The part after
     * {@code +} (the Minecraft version) doesn't count. A version that can't be compared counts as new enough, since
     * only known old versions are a problem.
     */
    static boolean tooOld(Version version, Shared feature) {
        try {
            return version.compareTo(Version.parse(feature.firstSupported)) < 0;
        } catch (VersionParsingException unreadable) {
            return false;
        }
    }
}
