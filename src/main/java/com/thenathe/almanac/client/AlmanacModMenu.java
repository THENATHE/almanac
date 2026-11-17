package com.thenathe.almanac.client;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/** Only loaded when Mod Menu is installed. */
public final class AlmanacModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return AlmanacSettings::create;
    }
}
