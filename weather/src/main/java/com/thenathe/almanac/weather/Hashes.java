package com.thenathe.almanac.weather;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * The hashing behind every storm. A storm is the same wherever and whenever it is worked out because each of its
 * numbers is drawn from a stream seeded only by the weather seed and the storm's place in the lattice. The mixing is
 * SplitMix64's finaliser, as {@code EventReach} uses for vanilla's rain events.
 *
 * <p>Everything worked out from the draws uses {@link StrictMath} for sines, logarithms and powers: {@link Math} may
 * answer a last bit differently once the JIT compiles it, and the server and every client must agree to the bit, or a
 * storm's edge can land a tick apart on two machines.
 */
final class Hashes {
    /** The weather seed's salt. Changing it changes every world's weather, so it never changes. */
    private static final byte[] WEATHER_SALT = "almanac:weather:1".getBytes(StandardCharsets.UTF_8);
    private static final long GOLDEN = 0x9E37_79B9_7F4A_7C15L;

    private Hashes() {
    }

    /**
     * The weather seed of a world: the first eight bytes of SHA-256 over a salt and the world seed. Clients get this,
     * never the world seed, and nothing about the world seed can be worked back from it.
     */
    static long weatherSeed(long worldSeed) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(WEATHER_SALT);
            digest.update(ByteBuffer.allocate(Long.BYTES).putLong(worldSeed).array());
            return ByteBuffer.wrap(digest.digest()).getLong();
        } catch (NoSuchAlgorithmException missing) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", missing);
        }
    }

    /** SplitMix64's finaliser: a cheap, well-spread 64-bit hash. */
    static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58_476D_1CE4_E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D0_49BB_1331_11EBL;
        return value ^ (value >>> 31);
    }

    /** A hash of several numbers, each mixed in turn. */
    static long of(long seed, long... parts) {
        long hash = mix(seed ^ 0x5EA5_0A1D_A1A1_A1ACL);
        for (long part : parts) {
            hash = mix((hash + GOLDEN) ^ mix(part));
        }
        return hash;
    }

    /** A stream of evenly spread numbers in [0, 1), the same for the same start. */
    static final class Draws {
        private long state;

        Draws(long start) {
            state = start;
        }

        double next() {
            state += GOLDEN;
            return (mix(state) >>> 11) * 0x1.0p-53;
        }

        /** Between {@code low} and {@code high}. */
        double between(double low, double high) {
            return low + (high - low) * next();
        }
    }
}
