package com.thenathe.almanac.client.colour;

import com.thenathe.almanac.biome.SeasonalBiomeIndex;
import com.thenathe.almanac.client.ClientClimateMap;
import com.thenathe.almanac.client.ClientConfig;
import com.thenathe.almanac.client.ClientSeasons;
import com.thenathe.almanac.colour.ColourChannel;
import com.thenathe.almanac.colour.SeasonBlend;
import com.thenathe.almanac.colour.SeasonalPalette;
import com.thenathe.almanac.colour.SeasonalPalette.LeafTint;
import com.thenathe.almanac.mixin.client.BiomeAccessor;
import com.thenathe.almanac.network.SeasonSyncPayload;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

/**
 * Seasonal colours on a client with Almanac, which sees the real biomes: the day's {@link ColourTable}, the leaf
 * blocks that have palettes of their own, and the redraws that bring loaded chunks up to date.
 *
 * <p>The colour for a day is worked out every time the server's day changes, but it only replaces the colours in
 * use once it differs from them by at least {@link #VISIBLE_STEP} in some channel. Then the loaded sections that
 * hold seasonal blocks are redrawn a few at a time. Everything here runs on the client thread except
 * {@link #table()}, which chunk-building threads read.
 */
public final class ClientColours {
    /** The smallest change, out of 255 in any channel, worth redrawing chunks for. */
    static final int VISIBLE_STEP = 3;

    private static volatile ColourTable table = ColourTable.EMPTY;
    private static SeasonalPalette palette = SeasonalPalette.bundled();
    private static boolean started;
    private static boolean wrapWhenLeaving;
    private static final Map<Block, LeafTint> wrappedLeaves = new IdentityHashMap<>();
    private static final Set<Block> seasonalBlocks = new ReferenceOpenHashSet<>();
    private static final Predicate<BlockState> HOLDS_SEASONAL_BLOCK = state -> seasonalBlocks.contains(state.getBlock());
    private static final RedrawQueue redraws = new RedrawQueue();

    private static @Nullable ClientLevel builtFor;
    private static @Nullable SeasonBlend lastTarget;
    private static boolean rebuildNeeded;
    private static int colourChanges;

    private ClientColours() {
    }

    /** The colours in use. Safe to call from any thread. */
    public static ColourTable table() {
        return table;
    }

    /** Once the game has started, every mod's tint sources are registered and leaf blocks can get their palettes. */
    public static void clientStarted(Minecraft client) {
        started = true;
        findSeasonalBlocks(client.getBlockColors());
    }

    /** A resource reload read the palette (from the mod, or a resource pack replacing it). */
    public static void paletteLoaded(SeasonalPalette loaded) {
        palette = loaded;
        rebuildNeeded = true;
        Minecraft client = Minecraft.getInstance();
        if (!started) {
            return;
        }
        // Chunk-building threads read the block colours while a world is open, so new leaf palettes wait for it
        // to close before their blocks are wrapped.
        if (client.level == null) {
            findSeasonalBlocks(client.getBlockColors());
        } else {
            wrapWhenLeaving = true;
        }
    }

    /** The server sent its tags, so leaf entries matched by tag can be worked out properly. */
    public static void tagsLoaded() {
        rebuildNeeded = true;
    }

    public static void disconnected() {
        if (wrapWhenLeaving) {
            wrapWhenLeaving = false;
            findSeasonalBlocks(Minecraft.getInstance().getBlockColors());
        }
        table = ColourTable.EMPTY;
        redraws.clear();
        builtFor = null;
        lastTarget = null;
    }

    public static void tick(Minecraft client) {
        ClientLevel level = client.level;
        SeasonSyncPayload sync = ClientSeasons.lastSync();
        if (level == null || sync == null || client.player == null) {
            return;
        }
        ClientConfig config = ClientConfig.get();
        SeasonBlend target = config.colourBlending
                ? SeasonBlend.at(sync.dayOfYear(), sync.daysPerSeason())
                : SeasonBlend.of(sync.subSeason());
        if (rebuildNeeded || builtFor != level || table.isEmpty()) {
            rebuildNeeded = false;
            builtFor = level;
            use(build(level, target), level, client);
        } else if (!target.equals(lastTarget)) {
            ColourTable candidate = build(level, target);
            if (candidate.largestDifference(table) >= VISIBLE_STEP) {
                use(candidate, level, client);
            }
        }
        lastTarget = target;
        if (!redraws.isEmpty()) {
            redraws.tick(level, config.redrawSectionsPerTick, HOLDS_SEASONAL_BLOCK);
        }
    }

    /** One line for diagnosis: the blend in use, how often colours changed and how far the latest redraw got. */
    public static String status() {
        SeasonBlend blend = table.blend();
        String inUse = blend == null ? "none" : blend.isBlended()
                ? String.format(Locale.ROOT, "%s to %s at %.3f", blend.from().id(), blend.to().id(), blend.weight())
                : blend.from().id();
        return String.format(Locale.ROOT, "colours %s; %d changes; redraw %s, %d sections; %d seasonal blocks, %d with leaf palettes",
                inUse, colourChanges, redraws.isEmpty() ? "done" : "running", redraws.redrawnSections(),
                seasonalBlocks.size(), wrappedLeaves.size());
    }

    private static void use(ColourTable next, ClientLevel level, Minecraft client) {
        table = next;
        colourChanges++;
        level.clearTintCaches();
        ClientClimateMap.coloursChanged();
        redraws.restart(ChunkPos.containing(client.player.blockPosition()), client.options.getEffectiveRenderDistance() + 1);
    }

    private static ColourTable build(ClientLevel level, SeasonBlend blend) {
        SeasonalBiomeIndex index = ClientSeasons.index(level);
        Map<Biome, int[]> biomes = new IdentityHashMap<>();
        for (SeasonalBiomeIndex.Entry entry : index.entries()) {
            Holder.Reference<Biome> holder = entry.original();
            Biome biome = holder.value();
            String id = holder.key().identifier().toString();
            Set<String> tags = holder.tags().map(tag -> tag.location().toString()).collect(Collectors.toSet());
            int[] colours = new int[3];
            colours[ColourTable.GRASS] = seasonal(id, tags, ColourChannel.GRASS, blend, ((BiomeAccessor) (Object) biome).almanac$baseGrassColor());
            colours[ColourTable.FOLIAGE] = seasonal(id, tags, ColourChannel.FOLIAGE, blend, biome.getFoliageColor());
            colours[ColourTable.DRY_FOLIAGE] = seasonal(id, tags, ColourChannel.DRY_FOLIAGE, blend, biome.getDryFoliageColor());
            biomes.put(biome, colours);
        }
        Map<Block, SeasonalPalette.LeafSteps> leaves = new IdentityHashMap<>();
        wrappedLeaves.forEach((block, tint) -> {
            SeasonalPalette.LeafSteps steps = palette.leafSteps(blockId(block), blockTags(block)::contains, tint);
            if (!steps.isUnchanged()) {
                leaves.put(block, steps);
            }
        });
        return new ColourTable(blend, biomes, leaves);
    }

    private static int seasonal(String biomeId, Set<String> tags, ColourChannel channel, SeasonBlend blend, int base) {
        return 0xFF000000 | blend.apply(base & 0xFFFFFF, subSeason -> palette.step(biomeId, tags::contains, channel, subSeason));
    }

    /**
     * Finds the blocks whose colour follows the season (for the redraws), and gives leaf blocks with palettes of
     * their own a tint source that applies them. Leaves are wrapped once; whether a wrapped leaf changes is decided
     * again with every table, so a reload or the server's tags can change it later.
     */
    private static void findSeasonalBlocks(BlockColors blockColors) {
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            List<BlockTintSource> sources = blockColors.getTintSources(state);
            if (sources.isEmpty()) {
                continue;
            }
            if (wrappedLeaves.containsKey(block)) {
                continue;
            }
            LeafTint tint = TintProbe.classify(state, sources);
            if (tint == LeafTint.BIOME) {
                seasonalBlocks.add(block);
            }
            if (needsLeafPalette(block, tint)) {
                List<BlockTintSource> wrapped = new ArrayList<>(sources.size());
                for (BlockTintSource source : sources) {
                    wrapped.add(new SeasonalLeafTint(block, source));
                }
                blockColors.register(List.copyOf(wrapped), block);
                wrappedLeaves.put(block, tint);
                seasonalBlocks.add(block);
            }
        }
    }

    /**
     * Whether the palette gives a block steps of its own. Before a server has sent its tags, a {@link LeavesBlock}
     * is taken to be in {@code #minecraft:leaves}, which is where vanilla puts every one of them.
     */
    private static boolean needsLeafPalette(Block block, LeafTint tint) {
        Set<String> tags = blockTags(block);
        if (tags.isEmpty() && block instanceof LeavesBlock) {
            tags = Set.of("minecraft:leaves");
        }
        return !palette.leafSteps(blockId(block), tags::contains, tint).isUnchanged();
    }

    private static String blockId(Block block) {
        return BuiltInRegistries.BLOCK.getKey(block).toString();
    }

    private static Set<String> blockTags(Block block) {
        return BuiltInRegistries.BLOCK.wrapAsHolder(block).tags().map(tag -> tag.location().toString()).collect(Collectors.toSet());
    }
}
