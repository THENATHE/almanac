package com.thenathe.almanac.client.colour;

import com.thenathe.almanac.Almanac;
import com.thenathe.almanac.colour.SeasonalPalette;
import java.io.IOException;
import java.io.Reader;
import java.util.Optional;
import net.fabricmc.fabric.api.resource.v1.reloader.SimpleReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads {@code assets/almanac/palette.json} through the resource packs, so a pack can replace the palette on a
 * client with Almanac. A palette that can't be read falls back to the one in the mod, and says why in the log.
 */
public final class PaletteReloader extends SimpleReloadListener<SeasonalPalette> {
    public static final Identifier ID = Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "palette");
    private static final Identifier FILE = Identifier.fromNamespaceAndPath(Almanac.MOD_ID, "palette.json");
    private static final Logger LOGGER = LoggerFactory.getLogger(Almanac.MOD_ID);

    @Override
    protected SeasonalPalette prepare(PreparableReloadListener.SharedState state) {
        Optional<Resource> resource = state.resourceManager().getResource(FILE);
        if (resource.isEmpty()) {
            return SeasonalPalette.bundled();
        }
        try (Reader reader = resource.get().openAsReader()) {
            return SeasonalPalette.read(reader);
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Couldn't read the palette from resource pack {}; using the one in Almanac",
                    resource.get().sourcePackId(), failure);
            return SeasonalPalette.bundled();
        }
    }

    @Override
    protected void apply(SeasonalPalette palette, PreparableReloadListener.SharedState state) {
        ClientColours.paletteLoaded(palette);
    }
}
