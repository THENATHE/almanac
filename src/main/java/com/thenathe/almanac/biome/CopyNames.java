package com.thenathe.almanac.biome;

import com.thenathe.almanac.api.SeasonalCopyId;
import com.thenathe.almanac.api.SubSeason;
import java.util.Optional;
import net.minecraft.resources.Identifier;

/**
 * Seasonal biome copies are named {@code almanac:<sub-season>/<namespace>/<path>}, for example
 * {@code almanac:mid_winter/minecraft/plains}. That is also the name a vanilla client shows on its debug screen. The
 * format itself lives in the public {@link SeasonalCopyId}.
 */
public final class CopyNames {
    private CopyNames() {
    }

    public static Identifier copyOf(Identifier original, SubSeason subSeason) {
        return Identifier.parse(SeasonalCopyId.of(subSeason, original.toString()));
    }

    public static boolean isCopy(Identifier id) {
        return parse(id).isPresent();
    }

    /** The sub-season and original biome of a copy's name. */
    public static Optional<Copy> parse(Identifier id) {
        if (!id.getNamespace().equals(SeasonalCopyId.NAMESPACE)) {
            return Optional.empty();
        }
        return SeasonalCopyId.parse(id.toString()).map(copy -> new Copy(copy.subSeason(), Identifier.parse(copy.original())));
    }

    public record Copy(SubSeason subSeason, Identifier original) {
    }
}
