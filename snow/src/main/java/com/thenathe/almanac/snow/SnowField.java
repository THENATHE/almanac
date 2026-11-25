package com.thenathe.almanac.snow;

import com.thenathe.almanac.api.Climate;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.EnumMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The snow depth of each snow node near loaded land under regional weather, as the background thread last worked it
 * out ({@code SnowNodes}): each node's depth per climate at a tick, how fast it moves from there, and until when it
 * keeps that pace. Never changed once made; the server thread reads the newest one.
 *
 * <p>A chunk's depth is its four nearest nodes' weighed by how near the chunk's centre is to each, so where a
 * storm's edge passed the snow thins out over the 256 blocks between nodes, a chunk at a time. Every column of a
 * chunk has its chunk's amounts, which its record saves.
 */
final class SnowField {
    static final SnowField EMPTY = new SnowField(new Long2ObjectOpenHashMap<>(), 1);

    /**
     * A node's snow: {@code depths} at {@code tick}, moving by {@code paces} a tick until {@code stableUntil}, when it
     * may start or stop snowing there.
     */
    record Node(long tick, double[] depths, double[] paces, long stableUntil) {
        double depth(Climate climate, long at, int maxLayers) {
            long until = Math.max(tick, Math.min(at, stableUntil));
            double depth = depths[climate.ordinal()] + paces[climate.ordinal()] * (until - tick);
            return Math.clamp(depth, 0.0, maxLayers);
        }
    }

    private final Long2ObjectOpenHashMap<Node> nodes;
    private final int maxLayers;

    SnowField(Long2ObjectOpenHashMap<Node> nodes, int maxLayers) {
        this.nodes = nodes;
        this.maxLayers = maxLayers;
    }

    int size() {
        return nodes.size();
    }

    boolean has(long key) {
        return nodes.containsKey(key);
    }

    @Nullable Node node(long key) {
        return nodes.get(key);
    }

    Long2ObjectMap<Node> nodes() {
        return nodes;
    }

    /** The four snow nodes around a chunk's centre. */
    static void nodesOf(int chunkX, int chunkZ, LongSet into) {
        int x = chunkX * 16 + 8;
        int z = chunkZ * 16 + 8;
        int nodeX = Math.floorDiv(x, SnowArea.NODE_SPACING);
        int nodeZ = Math.floorDiv(z, SnowArea.NODE_SPACING);
        into.add(SnowArea.key(nodeX, nodeZ));
        into.add(SnowArea.key(nodeX + 1, nodeZ));
        into.add(SnowArea.key(nodeX, nodeZ + 1));
        into.add(SnowArea.key(nodeX + 1, nodeZ + 1));
    }

    /** Each climate's depth at a column at a tick, from its four nodes; null while one of them isn't worked out. */
    @Nullable Map<Climate, Double> depthsAt(double x, double z, long tick) {
        double gridX = x / SnowArea.NODE_SPACING;
        double gridZ = z / SnowArea.NODE_SPACING;
        int nodeX = (int) Math.floor(gridX);
        int nodeZ = (int) Math.floor(gridZ);
        double alongX = gridX - nodeX;
        double alongZ = gridZ - nodeZ;
        Node a = nodes.get(SnowArea.key(nodeX, nodeZ));
        Node b = nodes.get(SnowArea.key(nodeX + 1, nodeZ));
        Node c = nodes.get(SnowArea.key(nodeX, nodeZ + 1));
        Node d = nodes.get(SnowArea.key(nodeX + 1, nodeZ + 1));
        if (a == null || b == null || c == null || d == null) {
            return null;
        }
        Map<Climate, Double> depths = new EnumMap<>(Climate.class);
        for (Climate climate : Climate.values()) {
            double near = a.depth(climate, tick, maxLayers) * (1 - alongX) + b.depth(climate, tick, maxLayers) * alongX;
            double far = c.depth(climate, tick, maxLayers) * (1 - alongX) + d.depth(climate, tick, maxLayers) * alongX;
            depths.put(climate, near * (1 - alongZ) + far * alongZ);
        }
        return depths;
    }

    /** The depths at the centre of a chunk. */
    @Nullable Map<Climate, Double> chunkDepths(int chunkX, int chunkZ, long tick) {
        return depthsAt(chunkX * 16 + 8, chunkZ * 16 + 8, tick);
    }

    /** How fast each climate's depth moves at a column now, from its four nodes; zero where they aren't known. */
    Map<Climate, Double> pacesAt(double x, double z, long tick) {
        Map<Climate, Double> now = depthsAt(x, z, tick);
        Map<Climate, Double> later = depthsAt(x, z, tick + 1);
        Map<Climate, Double> paces = new EnumMap<>(Climate.class);
        for (Climate climate : Climate.values()) {
            paces.put(climate, now == null || later == null ? 0.0 : later.get(climate) - now.get(climate));
        }
        return paces;
    }

    /** Each climate's deepest snow over these nodes at a tick. */
    Map<Climate, Double> deepest(long tick) {
        Map<Climate, Double> deepest = new EnumMap<>(Climate.class);
        for (Climate climate : Climate.values()) {
            deepest.put(climate, 0.0);
        }
        for (Node node : nodes.values()) {
            for (Climate climate : Climate.values()) {
                deepest.merge(climate, node.depth(climate, tick, maxLayers), Math::max);
            }
        }
        return deepest;
    }

    /**
     * Whether any node's snow in a climate has moved enough for loaded land to follow ({@code SnowUpdates#movedEnough})
     * between {@code before} at {@code beforeTick} and this field at {@code tick}, or a node is known now that wasn't.
     */
    boolean movedSince(SnowField before, long beforeTick, long tick) {
        for (Long2ObjectMap.Entry<Node> entry : nodes.long2ObjectEntrySet()) {
            Node was = before.nodes.get(entry.getLongKey());
            if (was == null) {
                return true;
            }
            for (Climate climate : Climate.values()) {
                double then = was.depth(climate, beforeTick, before.maxLayers);
                double now = entry.getValue().depth(climate, tick, maxLayers);
                if (SnowUpdates.movedEnough(then, now, maxLayers)) {
                    return true;
                }
            }
        }
        return false;
    }
}
