package com.thenathe.almanac.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.mojang.serialization.Lifecycle;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.biome.SeasonalBiomeIndex;
import com.thenathe.almanac.network.SeasonSyncPayload;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;

/** The client's climate lives in one snapshot, replaced whole, that always matches the latest sync (R2). */
class ClientSeasonsSnapshotTest {
    private static SeasonSyncPayload sync(SubSeason subSeason) {
        return new SeasonSyncPayload(subSeason, 0, 24, false, Set.of(Climate.TEMPERATE), 1, Map.of());
    }

    @Test
    void aSnapshotFollowsTheSyncAndKeepsItsIndex() {
        Registry<Biome> registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        AtomicInteger builds = new AtomicInteger();
        Function<Registry<Biome>, SeasonalBiomeIndex> build = ignored -> {
            builds.incrementAndGet();
            return SeasonalBiomeIndex.EMPTY;
        };
        Object level = new Object();
        SeasonSyncPayload winter = sync(SubSeason.MID_WINTER);

        ClientSeasons.Snapshot first = ClientSeasons.Snapshot.next(null, level, winter, registry, build);
        assertEquals(SubSeason.MID_WINTER, first.climate().subSeason());
        assertSame(first, ClientSeasons.Snapshot.next(first, level, winter, registry, build), "nothing new, nothing made");

        // A snapshot made from an older sync (a reader that lost a race) is replaced by the next reader.
        SeasonSyncPayload spring = sync(SubSeason.EARLY_SPRING);
        ClientSeasons.Snapshot second = ClientSeasons.Snapshot.next(first, level, spring, registry, build);
        assertEquals(SubSeason.EARLY_SPRING, second.climate().subSeason());
        assertSame(first.index(), second.index());

        // Another client level of the same connection shares the index; a new connection's registry builds its own.
        ClientSeasons.Snapshot otherLevel = ClientSeasons.Snapshot.next(second, new Object(), spring, registry, build);
        assertNotSame(second, otherLevel);
        assertEquals(1, builds.get());
        ClientSeasons.Snapshot.next(otherLevel, level, spring, new MappedRegistry<>(Registries.BIOME, Lifecycle.stable()), build);
        assertEquals(2, builds.get());
    }
}
