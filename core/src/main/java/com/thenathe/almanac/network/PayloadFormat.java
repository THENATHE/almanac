package com.thenathe.almanac.network;

import io.netty.buffer.ByteBuf;

/**
 * The rule every Almanac payload follows, so clients and servers of different versions keep talking: a payload only
 * ever gains fields at its end, and readers skip whatever follows the fields they know. Minecraft disconnects a
 * player whose packet has bytes left over, so without the skip an older reader would drop the connection.
 *
 * <p>Anything else (a field removed, reordered or read differently) needs a new channel, or a new protocol
 * ({@link ClientCheck#PROTOCOL}): from 0.2.0 only clients of the server's protocol can join, so a protocol change may
 * change a payload in place, as 0.3.0's did.
 */
final class PayloadFormat {
    private PayloadFormat() {
    }

    /** Skips fields a newer version added after the ones this version reads. */
    static void skipNewerFields(ByteBuf buffer) {
        buffer.skipBytes(buffer.readableBytes());
    }
}
