package com.thenathe.almanac.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.mojang.serialization.Lifecycle;
import com.thenathe.almanac.api.Climate;
import com.thenathe.almanac.api.SnowClocks;
import com.thenathe.almanac.api.SubSeason;
import com.thenathe.almanac.climate.ClimateTable;
import com.thenathe.almanac.network.ClimateTablePayload;
import com.thenathe.almanac.internal.SnowReading;
import com.thenathe.almanac.network.SeasonSyncPayload;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.biome.Biome;
import org.junit.jupiter.api.Test;

/** The client's climate lives in one snapshot, replaced whole, that always matches the latest sync and table (R2). */
class ClientSeasonsSnapshotTest {
    private static SeasonSyncPayload sync(SubSeason subSeason) {
        return new SeasonSyncPayload(subSeason, 0, 24, false, Set.of(Climate.TEMPERATE), 1, 0L,
                new SnowReading(Map.of(), Map.of(), Map.of(), Map.of(), new SnowClocks(12_000, 18_000, 12_000, 3)));
    }

    @Test
    void aSnapshotFollowsTheSyncAndKeepsItsTable() {
        Registry<Biome> registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        AtomicInteger resolved = new AtomicInteger();
        BiFunction<ClimateTablePayload, Registry<Biome>, ClimateTable> resolve = (sent, ignored) -> {
            resolved.incrementAndGet();
            return ClimateTable.EMPTY;
        };
        Object level = new Object();
        SeasonSyncPayload winter = sync(SubSeason.MID_WINTER);
        ClimateTablePayload table = new ClimateTablePayload(List.of());

        ClientSeasons.Snapshot first = ClientSeasons.Snapshot.next(null, level, winter, registry, table, resolve);
        assertEquals(SubSeason.MID_WINTER, first.climate().subSeason());
        assertSame(first, ClientSeasons.Snapshot.next(first, level, winter, registry, table, resolve), "nothing new, nothing made");

        // A snapshot made from an older sync (a reader that lost a race) is replaced by the next reader.
        SeasonSyncPayload spring = sync(SubSeason.EARLY_SPRING);
        ClientSeasons.Snapshot second = ClientSeasons.Snapshot.next(first, level, spring, registry, table, resolve);
        assertEquals(SubSeason.EARLY_SPRING, second.climate().subSeason());
        assertSame(first.table(), second.table());

        // Another client level of the same connection shares the table; a new connection's registry resolves its own.
        ClientSeasons.Snapshot otherLevel = ClientSeasons.Snapshot.next(second, new Object(), spring, registry, table, resolve);
        assertNotSame(second, otherLevel);
        assertEquals(1, resolved.get());
        ClientSeasons.Snapshot.next(otherLevel, level, spring, new MappedRegistry<>(Registries.BIOME, Lifecycle.stable()), table,
                resolve);
        assertEquals(2, resolved.get());

        // A new table from the server (a setting or the tags changed) is resolved again.
        ClientSeasons.Snapshot newTable = ClientSeasons.Snapshot.next(otherLevel, new Object(), spring, registry,
                new ClimateTablePayload(List.of()), resolve);
        assertEquals(3, resolved.get());
        assertEquals(SubSeason.EARLY_SPRING, newTable.climate().subSeason());
    }
}
