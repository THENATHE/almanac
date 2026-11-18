package com.thenathe.almanac.climatemap;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.List;

/** Which climate map tiles a player gets, in what order, and in what order players' missing tiles are sampled. */
final class TilePlan {
    private TilePlan() {
    }

    /** The tiles within {@code radius} blocks of the middle of the player's tile, nearest first, by tile key. */
    static long[] nearestFirst(int tileX, int tileZ, int radius) {
        int reach = radius / ClimateTile.SIZE + 1;
        List<long[]> byDistance = new ArrayList<>();
        for (int dz = -reach; dz <= reach; dz++) {
            for (int dx = -reach; dx <= reach; dx++) {
                if (within(dx, dz, radius)) {
                    byDistance.add(new long[] {(long) dx * dx + (long) dz * dz, ClimateTile.key(tileX + dx, tileZ + dz)});
                }
            }
        }
        // A stable sort, so tiles at the same distance keep the row order above and every run plans the same.
        byDistance.sort((first, second) -> Long.compare(first[0], second[0]));
        return byDistance.stream().mapToLong(entry -> entry[1]).toArray();
    }

    /** Whether the player, now on this tile, should drop a tile they were sent: once it is a tile beyond the radius. */
    static boolean dropped(long key, int tileX, int tileZ, int radius) {
        return !within(ClimateTile.keyX(key) - tileX, ClimateTile.keyZ(key) - tileZ, radius + ClimateTile.SIZE);
    }

    /** Whether the nearest point of a tile is within {@code radius} blocks of the middle of the player's tile. */
    static boolean within(int dx, int dz, int radius) {
        long x = Math.max(0, (long) Math.abs(dx) * ClimateTile.SIZE - ClimateTile.SIZE / 2);
        long z = Math.max(0, (long) Math.abs(dz) * ClimateTile.SIZE - ClimateTile.SIZE / 2);
        return x * x + z * z <= (long) radius * radius;
    }

    /**
     * The order to sample players' missing tiles in, at most {@code limit} of them: each player's nearest missing
     * tile in turn, starting with {@code firstPlayer}, then each one's second nearest, and so on. A tile two players
     * both miss is taken once. Moving {@code firstPlayer} on each tick shares the sampler out round-robin, so no
     * player waits for everyone before them in the list to have their whole map.
     *
     * @param missing each player's missing tiles, nearest first
     */
    static long[] fairOrder(List<long[]> missing, int firstPlayer, int limit) {
        LongLinkedOpenHashSet order = new LongLinkedOpenHashSet();
        int players = missing.size();
        int longest = missing.stream().mapToInt(tiles -> tiles.length).max().orElse(0);
        for (int rank = 0; rank < longest && order.size() < limit; rank++) {
            for (int turn = 0; turn < players && order.size() < limit; turn++) {
                long[] tiles = missing.get(Math.floorMod(firstPlayer + turn, players));
                if (rank < tiles.length) {
                    order.add(tiles[rank]);
                }
            }
        }
        return new LongArrayList(order).toLongArray();
    }
}
