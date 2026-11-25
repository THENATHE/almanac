package com.thenathe.almanac.modules;

import com.thenathe.almanac.internal.AlmanacModule;
import com.thenathe.almanac.internal.Services;
import net.fabricmc.loader.api.FabricLoader;

/** Installs core's side of the internal API and starts the modules (docs/ARCHITECTURE.md, "Modules"). */
public final class ModuleHost {
    private ModuleHost() {
    }

    /** First thing as Almanac starts, so the public API answers from then on. */
    public static void installApi() {
        Services.installSeasons(new CoreSeasons());
    }

    /** Every {@code almanac:module} entrypoint; each installs its own side of the internal API. */
    public static void startModules() {
        for (AlmanacModule module : FabricLoader.getInstance().getEntrypoints(AlmanacModule.ENTRYPOINT, AlmanacModule.class)) {
            module.initialize();
        }
        // Fails here, at start-up, if a module is missing from the jar.
        Services.snow();
        Services.weather();
    }
}
