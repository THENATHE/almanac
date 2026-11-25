package com.thenathe.almanac.internal;

/**
 * A module's entrypoint, listed under {@code almanac:module} in {@code fabric.mod.json}. Core starts the modules as
 * Almanac starts, after its own {@link Seasons} is installed and before it registers its game events, on the client
 * as on a dedicated server; a module installs its side of the internal API there ({@link Services}).
 */
public interface AlmanacModule {
    /** The one mod id every module shares. */
    String MOD_ID = "almanac";
    String ENTRYPOINT = "almanac:module";

    void initialize();
}
