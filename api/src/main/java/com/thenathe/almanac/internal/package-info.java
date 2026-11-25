/**
 * Almanac's internal API: the interfaces and plain data its modules reach each other through. Every module ships in
 * the one Almanac jar and release, so this changes freely; other mods use {@code com.thenathe.almanac.api} instead.
 *
 * <p>Core installs its {@link com.thenathe.almanac.internal.Seasons} first thing as Almanac starts, then starts every
 * {@link com.thenathe.almanac.internal.AlmanacModule}, which installs its own side (the snow module's
 * {@link com.thenathe.almanac.internal.Snow}). Both are reached through {@link com.thenathe.almanac.internal.Services}.
 * {@code docs/ARCHITECTURE.md} has the module layout.
 */
package com.thenathe.almanac.internal;
