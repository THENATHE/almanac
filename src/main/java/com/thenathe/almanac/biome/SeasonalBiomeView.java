package com.thenathe.almanac.biome;

import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.config.ServerConfig;
import com.thenathe.almanac.network.ServerNetworking;
import com.thenathe.almanac.season.ServerSeasons;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import org.jspecify.annotations.Nullable;

/**
 * Rewrites the biomes in chunk data built for a player without Almanac to the current sub-season's copies.
 *
 * <p>Chunk and biome packets are built one player at a time on the server thread, and they serialise the chunk as
 * they are built. While one is built for such a player, this view is active on the thread and each section's
 * biomes are swapped for a rewritten container; the chunk itself is never touched, so nothing saved can contain a
 * copy. Every section is rewritten once per packet, since the packet reads it twice (to size and to write it), and
 * on the server thread once per tick for all players ({@link SharedRewrites}), since they all get the same copies.
 */
public final class SeasonalBiomeView {
    private static final ThreadLocal<@Nullable SeasonalBiomeView> ACTIVE = new ThreadLocal<>();
    private static final int BIOMES_PER_SIDE = 4;
    /** Only touched on the server thread. */
    private static final SharedRewrites<PalettedContainerRO<Holder<Biome>>, PalettedContainerRO<Holder<Biome>>> SHARED =
            new SharedRewrites<>();

    private final SeasonalBiomeIndex index;
    private final SubSeason subSeason;
    private final Map<PalettedContainerRO<Holder<Biome>>, PalettedContainerRO<Holder<Biome>>> rewritten;

    private SeasonalBiomeView(SeasonalBiomeIndex index, SubSeason subSeason,
                              Map<PalettedContainerRO<Holder<Biome>>, PalettedContainerRO<Holder<Biome>>> rewritten) {
        this.index = index;
        this.subSeason = subSeason;
        this.rewritten = rewritten;
    }

    /** The two things a rewrite depends on. */
    private record Season(SeasonalBiomeIndex index, SubSeason subSeason) {
    }

    /** Builds a packet for {@code player}, in seasonal biomes if the player gets the copies. */
    public static <T> T build(ServerPlayer player, ServerLevel level, Supplier<T> packet) {
        ServerSeasons seasons = ServerSeasons.get();
        if (seasons == null || !getsCopies(player, level) || seasons.index().isEmpty() || ACTIVE.get() != null) {
            return packet.get();
        }
        MinecraftServer server = level.getServer();
        // Packets built on any other thread (a mod's own sender, say) keep their rewrites to themselves.
        Map<PalettedContainerRO<Holder<Biome>>, PalettedContainerRO<Holder<Biome>>> rewritten = server.isSameThread()
                ? SHARED.forTick(server.getTickCount(), new Season(seasons.index(), seasons.subSeason()))
                : new IdentityHashMap<>();
        ACTIVE.set(new SeasonalBiomeView(seasons.index(), seasons.subSeason(), rewritten));
        try {
            return packet.get();
        } finally {
            ACTIVE.remove();
        }
    }

    /** Lets go of the shared rewrites when the server stops. */
    public static void serverStopped() {
        SHARED.clear();
    }

    /** Players without Almanac in the overworld, while the copies are switched on. */
    public static boolean getsCopies(ServerPlayer player, Level level) {
        return level.dimension() == Level.OVERWORLD && ServerConfig.get().vanillaClientCopies
                && !ServerNetworking.hasAlmanac(player);
    }

    /** The biomes to write for a section: rewritten while a view is active, otherwise {@code biomes} itself. */
    public static PalettedContainerRO<Holder<Biome>> biomesToSend(PalettedContainerRO<Holder<Biome>> biomes) {
        SeasonalBiomeView view = ACTIVE.get();
        return view == null ? biomes : view.rewritten.computeIfAbsent(biomes, view::rewrite);
    }

    private PalettedContainerRO<Holder<Biome>> rewrite(PalettedContainerRO<Holder<Biome>> biomes) {
        if (!biomes.maybeHas(holder -> index.entry(holder.value()) != null)) {
            return biomes;
        }
        PalettedContainer<Holder<Biome>> seasonal = biomes.recreate();
        for (int x = 0; x < BIOMES_PER_SIDE; x++) {
            for (int y = 0; y < BIOMES_PER_SIDE; y++) {
                for (int z = 0; z < BIOMES_PER_SIDE; z++) {
                    Holder<Biome> biome = biomes.get(x, y, z);
                    SeasonalBiomeIndex.Entry entry = index.entry(biome.value());
                    seasonal.getAndSetUnchecked(x, y, z, entry == null ? biome : entry.copy(subSeason));
                }
            }
        }
        return seasonal;
    }
}
