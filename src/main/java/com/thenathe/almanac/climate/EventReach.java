package com.thenathe.almanac.climate;

/**
 * Decides which rain events reach a climate. Every event gets a strength between 0 and 1 from the world seed and
 * its number, and it reaches a climate when the strength is below that climate's share. The server, modded clients
 * and the snow model all get the same answer, and a stronger share always includes the events a weaker one gets.
 */
public final class EventReach {
    private EventReach() {
    }

    /** The event's strength, evenly spread over [0, 1). */
    public static double strength(long worldSeed, long eventNumber) {
        long mixed = mix(worldSeed ^ mix(eventNumber + 0x5EA5_0A1D_A1A1_A1ACL));
        return (mixed >>> 11) * 0x1.0p-53;
    }

    /** Whether the event reaches a climate that gets {@code sharePercent} of the rain events. */
    public static boolean reaches(double strength, int sharePercent) {
        return strength * 100.0 < sharePercent;
    }

    /** SplitMix64's finaliser: a cheap, well-spread 64-bit hash. */
    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D0_49BB_1331_11EBL;
        return value ^ (value >>> 31);
    }
}
