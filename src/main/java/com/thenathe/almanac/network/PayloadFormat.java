package com.thenathe.almanac.network;

import io.netty.buffer.ByteBuf;

/**
 * The rule every Almanac payload follows, so clients and servers of different versions keep talking: a payload only
 * ever gains fields at its end, and readers skip whatever follows the fields they know. Minecraft disconnects a
 * player whose packet has bytes left over, so without the skip an older reader would drop the connection.
 *
 * <p>Anything else (a field removed, reordered or read differently) needs a new channel. Readers from 0.1.2 and
 * earlier don't skip, so no existing payload may grow while those versions are still about.
 */
final class PayloadFormat {
    private PayloadFormat() {
    }

    /** Skips fields a newer version added after the ones this version reads. */
    static void skipNewerFields(ByteBuf buffer) {
        buffer.skipBytes(buffer.readableBytes());
    }
}
