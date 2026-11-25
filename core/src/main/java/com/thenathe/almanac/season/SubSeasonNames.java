package com.thenathe.almanac.season;

import com.thenathe.almanac.api.SubSeason;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Sub-season names for chat and the action bar, with English fallbacks for the console. */
public final class SubSeasonNames {
    private SubSeasonNames() {
    }

    public static Component name(SubSeason subSeason) {
        return Component.translatableWithFallback("almanac.sub_season." + subSeason.id(), english(subSeason));
    }

    /** "Mid Winter" for {@code MID_WINTER}. */
    public static String english(SubSeason subSeason) {
        StringBuilder name = new StringBuilder();
        for (String word : subSeason.id().split("_")) {
            if (!name.isEmpty()) {
                name.append(' ');
            }
            name.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return name.toString();
    }
}
