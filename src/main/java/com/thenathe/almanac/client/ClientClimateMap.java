package com.thenathe.almanac.client;

import com.thenathe.almanac.Almanac;
import com.thenathe.almanac.api.AlmanacApi;
import com.thenathe.almanac.api.AlmanacEvents;
import com.thenathe.almanac.api.AlmanacEvents.FarChange;
import com.thenathe.almanac.biome.SeasonalBiomeIndex;
import com.thenathe.almanac.client.colour.ClientColours;
import com.thenathe.almanac.client.colour.ColourTable;
import com.thenathe.almanac.climate.SeasonalClimate;
import com.thenathe.almanac.climatemap.ClimateTile;
import com.thenathe.almanac.climatemap.FarClimateView;
import com.thenathe.almanac.climatemap.SnowLineGrid;
import com.thenathe.almanac.mixin.client.BiomeAccessor;
import com.thenathe.almanac.network.FarClimatePayloads;
import com.thenathe.almanac.network.SeasonSyncPayload;
import com.thenathe.almanac.snow.FarSnowRule;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.synth.Noise;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The climate map on a client with Almanac (docs/FAR-SNOW.md): the tiles the server has sent and what far terrain
 * shows now, turned into the far-terrain answers of {@link AlmanacApi}.
 *
 * <p>Tiles and the state arrive on the client thread. Both are published as values that never change afterwards (the
 * tile map is copied on every change), so the answers can be read from any thread without locks.
 */
public final class ClientClimateMap implements FarClimateView {
    static final ClientClimateMap INSTANCE = new ClientClimateMap();
    private static final Logger LOGGER = LoggerFactory.getLogger(Almanac.MOD_ID);

    private volatile Long2ObjectOpenHashMap<Tile> tiles = new Long2ObjectOpenHashMap<>();
    private volatile FarClimatePayloads.@Nullable State state;
    /** The game tick at which far snow last changed, as the server said; -1 until a 0.1.3 server says. */
    private volatile long snowChangedTick = -1;
    private boolean warnedAboutTile;

    private ClientClimateMap() {
    }

    /**
     * A tile with its palette looked up in the client's registry: the biome of each palette entry (null if the client
     * doesn't know the ID) and its seasonal entry (null for biomes without seasons).
     */
    private record Tile(ClimateTile cells, @Nullable Holder<Biome>[] biomes, SeasonalBiomeIndex.@Nullable Entry[] entries) {
        @Nullable Holder<Biome> biomeAt(int x, int z) {
            return biomes[cells.cells()[cells.cellIndex(x, z)]];
        }

        SeasonalBiomeIndex.@Nullable Entry entryAt(int x, int z) {
            return entries[cells.cells()[cells.cellIndex(x, z)]];
        }
    }

    void receiveState(FarClimatePayloads.State received) {
        FarClimatePayloads.State previous = state;
        state = received;
        if (!received.enabled()) {
            tiles = new Long2ObjectOpenHashMap<>();
        }
        if (!received.sameFarSnow(previous)) {
            boolean switched = previous == null || previous.enabled() != received.enabled();
            fire(FarChange.everywhere(true, switched, switched));
        }
    }

    @SuppressWarnings("unchecked")
    void receiveTile(FarClimatePayloads.Tile payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        ClimateTile cells;
        try {
            cells = payload.unpack();
        } catch (IllegalArgumentException damaged) {
            if (!warnedAboutTile) {
                warnedAboutTile = true;
                LOGGER.warn("Almanac: the server sent a climate map tile that doesn't read; leaving it out", damaged);
            }
            return;
        }
        Registry<Biome> registry = level.registryAccess().lookupOrThrow(Registries.BIOME);
        SeasonalBiomeIndex index = ClientSeasons.index(level);
        int[] palette = cells.palette();
        Holder<Biome>[] biomes = new Holder[palette.length];
        SeasonalBiomeIndex.Entry[] entries = new SeasonalBiomeIndex.Entry[palette.length];
        for (int slot = 0; slot < palette.length; slot++) {
            Holder<Biome> biome = registry.get(palette[slot]).orElse(null);
            biomes[slot] = biome;
            entries[slot] = biome == null ? null : index.entry(biome.value());
        }
        Long2ObjectOpenHashMap<Tile> next = new Long2ObjectOpenHashMap<>(tiles);
        next.put(cells.key(), new Tile(cells, biomes, entries));
        tiles = next;
        fire(new FarChange(cells.minBlockX(), cells.minBlockZ(), cells.minBlockX() + ClimateTile.SIZE - 1,
                cells.minBlockZ() + ClimateTile.SIZE - 1, true, true, true));
    }

    void receiveForget(FarClimatePayloads.Forget forget) {
        if (forget.all()) {
            tiles = new Long2ObjectOpenHashMap<>();
            fire(FarChange.everywhere(true, true, true));
            return;
        }
        Long2ObjectOpenHashMap<Tile> next = new Long2ObjectOpenHashMap<>(tiles);
        for (long key : forget.tiles()) {
            next.remove(key);
        }
        tiles = next;
        for (long key : forget.tiles()) {
            int minX = ClimateTile.keyX(key) << ClimateTile.SIZE_SHIFT;
            int minZ = ClimateTile.keyZ(key) << ClimateTile.SIZE_SHIFT;
            fire(new FarChange(minX, minZ, minX + ClimateTile.SIZE - 1, minZ + ClimateTile.SIZE - 1, true, true, true));
        }
    }

    /** Comes just before the state that brings the change, so listeners to that change read the new tick. */
    void receiveSnowChanged(FarClimatePayloads.SnowChanged changed) {
        snowChangedTick = changed.gameTick();
    }

    /** The game tick at which far snow last changed; empty before the server has said (or if it is older than 0.1.3). */
    OptionalLong snowChangedTick(Level level) {
        long tick = snowChangedTick;
        return tick < 0 || state == null || !(level instanceof ClientLevel) || level.dimension() != Level.OVERWORLD
                ? OptionalLong.empty() : OptionalLong.of(tick);
    }

    /** How long a thaw in loaded chunks takes now, as the server last said; empty before it has. */
    OptionalLong thawTicks(Level level) {
        FarClimatePayloads.State current = state;
        return current == null || !(level instanceof ClientLevel) || level.dimension() != Level.OVERWORLD
                ? OptionalLong.empty() : OptionalLong.of(current.thawTicks());
    }

    /** The day's colours changed (from {@link ClientColours}). */
    public static void coloursChanged() {
        INSTANCE.fire(FarChange.everywhere(false, true, false));
    }

    /** The sub-season or the rain reach changed (from {@link ClientSeasons}). */
    static void climateChanged() {
        INSTANCE.fire(FarChange.everywhere(false, false, true));
    }

    void disconnected() {
        tiles = new Long2ObjectOpenHashMap<>();
        state = null;
        snowChangedTick = -1;
    }

    /** For the QA driver: how many tiles the client holds and what far terrain shows. */
    public static String status() {
        FarClimatePayloads.State current = INSTANCE.state;
        return INSTANCE.tiles.size() + " tiles; " + (current == null ? "no state"
                : (current.enabled() ? "on" : "off") + ", showing " + current.shownSubSeason().id() + ", layers "
                + Arrays.toString(current.layers()) + ", native snow melts " + current.nativeSnowMelts());
    }

    private void fire(FarChange change) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null) {
            AlmanacEvents.FAR_CLIMATE_CHANGED.invoker().onFarClimateChanged(level, change);
        }
    }

    /** What far terrain shows, when this level is the overworld of a client on an Almanac server with the map on. */
    private FarClimatePayloads.@Nullable State stateFor(Level level) {
        FarClimatePayloads.State current = state;
        if (current == null || !current.enabled() || !(level instanceof ClientLevel) || level.dimension() != Level.OVERWORLD) {
            return null;
        }
        return current;
    }

    private static @Nullable Tile tileAt(Long2ObjectOpenHashMap<Tile> tiles, int x, int z) {
        return tiles.get(ClimateTile.key(ClimateTile.tileOf(x), ClimateTile.tileOf(z)));
    }

    @Override
    public int snowLine(Level level, int x, int z) {
        FarClimatePayloads.State current = stateFor(level);
        Tile tile = current == null ? null : tileAt(tiles, x, z);
        if (tile == null) {
            return FarSnowRule.UNKNOWN;
        }
        return snowLine(level, current, tile, x, z, new BlockPos.MutableBlockPos());
    }

    @Override
    public int fillSnowLines(Level level, int originX, int originZ, int spacing, int width, int height, int[] lines) {
        FarClimatePayloads.State current = stateFor(level);
        Long2ObjectOpenHashMap<Tile> known = current == null ? new Long2ObjectOpenHashMap<>() : tiles;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        return SnowLineGrid.fill(new SnowLineGrid.Source<Tile>() {
            @Override
            public @Nullable Tile tile(long key) {
                return known.get(key);
            }

            @Override
            public int line(Tile tile, int x, int z) {
                return snowLine(level, current, tile, x, z, pos);
            }
        }, originX, originZ, spacing, width, height, lines);
    }

    /**
     * A column's far snow line ({@link FarSnowRule}): the cold lines of the shown sub-season's copy of the cell's
     * biome and of the biome itself, from the biome's own temperature at this column and vanilla's temperature noise.
     */
    private static int snowLine(Level level, FarClimatePayloads.State state, Tile tile, int x, int z,
                                BlockPos.MutableBlockPos pos) {
        Holder<Biome> biome = tile.biomeAt(x, z);
        if (biome == null) {
            return FarSnowRule.UNKNOWN;
        }
        int seaLevel = level.getSeaLevel();
        Noise temperatureNoise = BiomeAccessor.almanac$temperatureNoise();
        float noise = temperatureNoise.get(x / 8.0F, z / 8.0F) * 8.0F;
        // At sea level the temperature has its modifier (frozen oceans' warm patches) and no cooling with height yet.
        pos.set(x, seaLevel, z);
        int realLine = FarSnowRule.coldLine(temperature(biome.value(), pos, seaLevel), noise, seaLevel);
        SeasonalBiomeIndex.Entry entry = tile.entryAt(x, z);
        int line;
        if (entry == null) {
            line = FarSnowRule.snowLine(null, realLine, realLine, 0, state.nativeSnowMelts(), state.naturalHeight(), false);
        } else {
            Biome seasonal = entry.copy(state.shownSubSeason()).value();
            int seasonalLine = FarSnowRule.coldLine(temperature(seasonal, pos, seaLevel), noise, seaLevel);
            line = FarSnowRule.snowLine(entry.base(), seasonalLine, realLine, state.layers(entry.base()),
                    state.nativeSnowMelts(), state.naturalHeight(), entry.snowSurface());
        }
        return FarSnowRule.withinLevel(line, level.getMinY(), level.getMaxY());
    }

    private static float temperature(Biome biome, BlockPos pos, int seaLevel) {
        return ((BiomeAccessor) (Object) biome).almanac$heightAdjustedTemperature(pos, seaLevel);
    }

    @Override
    public OptionalInt grassColour(Level level, int x, int z) {
        Holder<Biome> biome = knownBiome(level, x, z);
        if (biome == null) {
            return OptionalInt.empty();
        }
        int[] colours = ClientColours.table().colours(biome.value());
        int grass = colours == null ? biome.value().getGrassColor(x, z)
                : biome.value().getSpecialEffects().grassColorModifier().modifyColor(x, z, colours[ColourTable.GRASS]);
        return OptionalInt.of(0xFF000000 | grass);
    }

    @Override
    public OptionalInt foliageColour(Level level, int x, int z) {
        Holder<Biome> biome = knownBiome(level, x, z);
        if (biome == null) {
            return OptionalInt.empty();
        }
        int[] colours = ClientColours.table().colours(biome.value());
        return OptionalInt.of(0xFF000000 | (colours == null ? biome.value().getFoliageColor() : colours[ColourTable.FOLIAGE]));
    }

    @Override
    public Optional<AlmanacApi.FarClimate> climate(Level level, int x, int z) {
        FarClimatePayloads.State current = stateFor(level);
        Tile tile = current == null ? null : tileAt(tiles, x, z);
        SeasonSyncPayload sync = ClientSeasons.lastSync();
        if (tile == null || sync == null) {
            return Optional.empty();
        }
        Holder<Biome> biome = tile.biomeAt(x, z);
        SeasonalBiomeIndex.Entry entry = tile.entryAt(x, z);
        if (biome == null || entry == null) {
            return Optional.empty();
        }
        return Optional.of(new AlmanacApi.FarClimate(biome, entry.base(),
                SeasonalClimate.shifted(entry.base(), sync.subSeason()), sync.reached().contains(entry.base())));
    }

    private @Nullable Holder<Biome> knownBiome(Level level, int x, int z) {
        Tile tile = stateFor(level) == null ? null : tileAt(tiles, x, z);
        return tile == null ? null : tile.biomeAt(x, z);
    }
}
