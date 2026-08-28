/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.gametest;

import com.mojang.logging.LogUtils;
import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.HbwInterfaceBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.SupercomputerNodeBlockEntity;
import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import dev.jsc.jscomputronics.module.computing.crafting.MultiStagePattern;
import dev.jsc.jscomputronics.module.computing.crafting.NetworkRecipe;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.program.ServerCliComputer;
import dev.jsc.jscomputronics.module.computing.program.iql.IqlParseResult;
import dev.jsc.jscomputronics.module.computing.program.iql.IqlParser;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * The whole-mod crafting stress battery: a large synthetic recipe graph over real Mekanism items, crafted
 * through every entry point at once, so the immensity itself flushes out boundary cases. The crafting engine
 * trusts a bench pattern (it consumes the grid and produces the declared result), so a generated dependency
 * DAG lets the network craft items that have no real bench recipe — the point is to exercise the engine, the
 * planner, and every request path across hundreds of item types, not to reproduce Mekanism's own recipes.
 *
 * <p>Randomness is seeded and the seed is logged as {@code [JSC-MEGA] seed=...}; pass {@code -Djsc.megaseed=N}
 * to replay a run that surfaced a failure. This first slice covers the bench graph through two entry points
 * (the shared craft request and IQL); machines, multi-stage, supercomputers and larger scale layer on top.
 */
@GameTestHolder(JsComputronics.MODID)
@PrefixGameTestTemplate(false)
public final class MekanismEverythingGameTests {

    private MekanismEverythingGameTests() {
    }

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    /** The mod namespaces whose items form the craft graph; absent ones simply contribute nothing. */
    private static final String[] NAMESPACES = {"mekanism", "mekanismgenerators", "mekanismtools", "mekanismadditions"};

    /** Working-set size, tier depth and per-recipe fan-in. Bumped up as the battery is scaled. */
    private static final int TARGET_COUNT = 60;
    private static final int TIERS = 3;
    private static final int BRANCHING = 2;
    private static final int LEAF_SEED = 64;
    private static final int REQUEST_QTY = 1;

    /** The supercomputer cluster sits off the HBW cable so its slots parallelize the crafts. */
    private static final BlockPos CLUSTER_HUB = new BlockPos(2, 2, 3);
    private static final int CLUSTER_NODES = 2;

    /** Places an HBW interface plus a chain of powered supercomputer nodes, the way a player wires a cluster. */
    private static void placeSupercomputer(final GameTestHelper helper, final BlockPos hub, final int nodes) {
        helper.setBlock(hub, ComputingModule.HBW_INTERFACE.get());
        for (int i = 1; i <= nodes; i++) {
            final BlockPos pos = hub.east(i);
            helper.setBlock(pos, ComputingModule.SUPERCOMPUTER_NODE.get());
            if (helper.getBlockEntity(pos) instanceof SupercomputerNodeBlockEntity node) {
                final var hw = node.getHardware();
                hw.setStackInSlot(SupercomputerNodeBlockEntity.MOTHERBOARD_SLOT,
                        new ItemStack(ComputingModule.MOTHERBOARD_EEB_P.get()));
                hw.setStackInSlot(SupercomputerNodeBlockEntity.CPU_SLOT,
                        new ItemStack(ComputingModule.CPU_SERVO_2620.get()));
                hw.setStackInSlot(SupercomputerNodeBlockEntity.RAM_SLOTS_START,
                        new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
                hw.setStackInSlot(SupercomputerNodeBlockEntity.PHI_SLOT,
                        new ItemStack(ComputingModule.PHI_5100.get()));
                hw.setStackInSlot(SupercomputerNodeBlockEntity.PSU_SLOT,
                        new ItemStack(ComputingModule.PSU_650G.get()));
                node.togglePower();
            }
        }
    }

    private static long seed() {
        final String override = System.getProperty("jsc.megaseed", "");
        return override.isEmpty() ? System.nanoTime() : Long.parseLong(override);
    }

    /** Every registered item in the target namespaces, sorted by id so a seed reproduces the same graph. */
    private static List<Item> modItems() {
        final List<Item> items = new ArrayList<>();
        for (final Item item : BuiltInRegistries.ITEM) {
            final ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            for (final String ns : NAMESPACES) {
                if (id.getNamespace().equals(ns)) {
                    items.add(item);
                    break;
                }
            }
        }
        items.sort(Comparator.comparing(i -> BuiltInRegistries.ITEM.getKey(i).toString()));
        return items;
    }

    private static CraftingPattern benchRecipe(final List<Item> ingredients, final Item result) {
        final List<ItemStack> grid = new ArrayList<>(CraftingPattern.GRID_SIZE);
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            grid.add(i < ingredients.size() ? new ItemStack(ingredients.get(i)) : ItemStack.EMPTY);
        }
        return new CraftingPattern(grid, new ItemStack(result, 1));
    }

    /**
     * A storm of invalid and extreme craft requests through both entry points, against a plain vanilla recipe:
     * non-positive and overflowing demands, an unknown item, and malformed IQL. None may crash, none may leave a
     * stuck operation, and the network must create nothing — cobblestone plus twice the stone made can never
     * exceed what was seeded. This is where the boundaries of the request path show.
     */
    @GameTest(template = ARENA, timeoutTicks = 6000)
    public static void handlesAdversarialCraftRequestsGracefully(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        final StorageKey stone = StorageKey.of(Items.STONE);
        final String stoneId = "minecraft:stone";
        final int seeded = 256;

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.cc().loadPattern(benchRecipe(List.of(Items.COBBLESTONE, Items.COBBLESTONE), Items.STONE));
                    net.seed(Items.COBBLESTONE, seeded);
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var mf = net.mainframe();
                    // Non-positive demand must be refused outright, never queued.
                    helper.assertTrue(mf.submitCraftRequest(stone, 0, true, "adv", null) == null, "qty 0 must be rejected");
                    helper.assertTrue(mf.submitCraftRequest(stone, -1, true, "adv", null) == null, "negative qty must be rejected");
                    helper.assertTrue(mf.submitCraftRequest(stone, Long.MIN_VALUE, true, "adv", null) == null,
                            "Long.MIN_VALUE demand must be rejected");
                    // An enormous demand is valid but must clamp to what the stock can make, not overflow or hang.
                    mf.submitCraftRequest(stone, Long.MAX_VALUE, true, "adv", null);

                    final ServerCliComputer cli = new ServerCliComputer(mf, helper.getLevel());
                    // Malformed / extreme IQL: whatever parses must run without throwing; the rest is rejected.
                    for (final String line : new String[]{
                            "craft 0 " + stoneId, "craft -5 " + stoneId, "craft 999999999999999 " + stoneId,
                            "craft 1 minecraft:__nope__", "craft 1 not a real id", "craft", "craft " + stoneId,
                            "", "   ", "garbage tokens here", "select", "operation"}) {
                        final IqlParseResult parsed = IqlParser.tryParse(line);
                        if (parsed.ok()) {
                            cli.execute(parsed.operation());
                        }
                    }
                    // The typed craft facade must reject an unknown item cleanly.
                    helper.assertTrue(!cli.craft("minecraft:__nope__", 1).ok(), "an unknown item must be rejected");
                })
                .thenWaitUntil(() -> {
                    final int inflight = net.mainframe().activeOperationRecords().size();
                    if (inflight > 0) {
                        throw new GameTestAssertException("a bad request left " + inflight + " ops stuck");
                    }
                })
                .thenExecute(() -> {
                    final NetworkStorage storage = net.storage(helper.getLevel());
                    final long stoneCount = storage.count(Items.STONE);
                    final long cobble = storage.count(Items.COBBLESTONE);
                    helper.assertTrue(stoneCount >= 0 && cobble >= 0, "no count may go negative; stone="
                            + stoneCount + " cobble=" + cobble);
                    helper.assertTrue(cobble + 2 * stoneCount <= seeded, "the network created matter: cobble("
                            + cobble + ") + 2*stone(" + stoneCount + ") exceeds the seeded " + seeded);
                    final int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(0, 0, 0)),
                                    helper.absolutePos(new BlockPos(16, 8, 16)))).size();
                    helper.assertTrue(dropped == 0, dropped + " items leaked to the world after the storm");
                })
                .thenSucceed();
    }

    private static final int RAW_POOL_SIZE = 12;
    private static final int RAW_LEVEL = 96;
    private static final int WAVE_SIZE = 40;

    private static final int CHAINS = 20;
    private static final int CHAIN_SEED = 8;

    /**
     * Multi-stage pipelines at scale: each of {@link #CHAINS} chains turns two raws into an intermediate and the
     * intermediate into a final item, over two bench stages, and is crafted half through the direct multi-stage
     * submit and half through the shared craft request (which discovers the multi-stage recipe by its result).
     * Every final must appear and the pipeline must consume its intermediates, so nothing lingers between stages.
     */
    @GameTest(template = ARENA, timeoutTicks = 20000)
    public static void craftsMultiStageChainsViaBothPaths(final GameTestHelper helper) {
        final long seed = seed();
        LOGGER.info("[JSC-MEGA] multi-stage seed={}", seed);
        final List<Item> all = modItems();
        if (all.size() < CHAINS * 4) {
            helper.succeed();
            return;
        }
        // Each chain claims four distinct items: two raws, one intermediate, one final.
        final List<Item[]> chains = new ArrayList<>();
        final List<MultiStagePattern> patterns = new ArrayList<>();
        for (int k = 0; k < CHAINS; k++) {
            final Item a = all.get(4 * k);
            final Item b = all.get(4 * k + 1);
            final Item mid = all.get(4 * k + 2);
            final Item fin = all.get(4 * k + 3);
            chains.add(new Item[]{a, b, mid, fin});
            patterns.add(new MultiStagePattern(List.of(
                    MultiStagePattern.Stage.bench(benchRecipe(List.of(a, b), mid)),
                    MultiStagePattern.Stage.bench(benchRecipe(List.of(mid), fin)))));
        }

        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    for (int k = 0; k < CHAINS; k++) {
                        helper.assertTrue(net.cc().loadMachineRecipe(NetworkRecipe.ofMultiStage(patterns.get(k))),
                                "multi-stage recipe must load");
                        net.seed(chains.get(k)[0], CHAIN_SEED);
                        net.seed(chains.get(k)[1], CHAIN_SEED);
                    }
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    for (int k = 0; k < CHAINS; k++) {
                        final Item fin = chains.get(k)[3];
                        if (k % 2 == 0) {
                            net.mainframe().submitNetworkMultiStage(patterns.get(k), REQUEST_QTY, "ms");
                        } else {
                            net.mainframe().submitCraftRequest(StorageKey.of(fin), REQUEST_QTY, true, "ms", null);
                        }
                    }
                })
                .thenWaitUntil(() -> {
                    if (!net.mainframe().activeOperationRecords().isEmpty()) {
                        throw new GameTestAssertException("multi-stage chains still running");
                    }
                })
                .thenExecute(() -> {
                    final NetworkStorage storage = net.storage(helper.getLevel());
                    for (int k = 0; k < CHAINS; k++) {
                        final Item mid = chains.get(k)[2];
                        final Item fin = chains.get(k)[3];
                        helper.assertTrue(storage.count(fin) >= REQUEST_QTY, "final " + BuiltInRegistries.ITEM.getKey(fin)
                                + " of chain " + k + " must be crafted; have " + storage.count(fin) + " (seed=" + seed + ")");
                        helper.assertTrue(storage.count(mid) == 0, "intermediate " + BuiltInRegistries.ITEM.getKey(mid)
                                + " must be fully consumed by the pipeline; left " + storage.count(mid));
                    }
                    final int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(0, 0, 0)),
                                    helper.absolutePos(new BlockPos(16, 8, 16)))).size();
                    helper.assertTrue(dropped == 0, dropped + " items leaked from the multi-stage chains (seed=" + seed + ")");
                })
                .thenSucceed();
    }

    /**
     * Churns through every Mekanism item in waves: a small raw pool is topped up, a wave of items is crafted
     * from it through mixed entry points (parallelized by the supercomputer), verified, then exported straight
     * back out to free capacity — a base that crafts and drains a lot, over and over, across the whole mod. The
     * wave model keeps storage bounded no matter how many item types exist, so it scales to the full catalogue.
     */
    @GameTest(template = ARENA, timeoutTicks = 60000)
    public static void churnsEveryMekanismItemInWaves(final GameTestHelper helper) {
        final long seed = seed();
        LOGGER.info("[JSC-MEGA] wave churn seed={}", seed);
        final Random rng = new Random(seed);

        final List<Item> all = modItems();
        if (all.size() < RAW_POOL_SIZE + WAVE_SIZE) {
            helper.succeed(); // the mod is not present on this runtime
            return;
        }
        final List<Item> rawPool = new ArrayList<>(all.subList(0, RAW_POOL_SIZE));
        final List<Item> craftable = new ArrayList<>(all.subList(RAW_POOL_SIZE, all.size()));
        LOGGER.info("[JSC-MEGA] churning {} items in waves of {} from a {}-item raw pool",
                craftable.size(), WAVE_SIZE, RAW_POOL_SIZE);

        // Every craftable item gets a depth-1 bench recipe from two random raw-pool items. The Recipe ROM holds
        // only RECIPE_ROM_LIMIT patterns, so each wave loads its own recipes and clears them again afterwards —
        // WAVE_SIZE stays under the limit, and the churn covers the whole catalogue without ever overflowing it.
        final List<CraftingPattern> recipes = new ArrayList<>();
        for (final Item item : craftable) {
            recipes.add(benchRecipe(List.of(rawPool.get(rng.nextInt(RAW_POOL_SIZE)),
                    rawPool.get(rng.nextInt(RAW_POOL_SIZE))), item));
        }
        final List<List<Item>> waves = new ArrayList<>();
        final List<List<CraftingPattern>> waveRecipes = new ArrayList<>();
        for (int i = 0; i < craftable.size(); i += WAVE_SIZE) {
            final int end = Math.min(i + WAVE_SIZE, craftable.size());
            waves.add(craftable.subList(i, end));
            waveRecipes.add(recipes.subList(i, end));
        }

        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        final ServerCliComputer[] cli = new ServerCliComputer[1];
        final int[] wave = {0};
        final int[] phase = {0}; // 0 SEED, 1 SUBMIT, 2 WAIT, 3 VERIFY
        final int[] cooldown = {0};
        final String[] failure = {null};

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    placeSupercomputer(helper, CLUSTER_HUB, CLUSTER_NODES);
                    cli[0] = new ServerCliComputer(net.mainframe(), helper.getLevel());
                })
                .thenWaitUntil(() -> {
                    if (failure[0] != null || wave[0] >= waves.size()) {
                        return; // done (or bailing out with a recorded failure)
                    }
                    if (cooldown[0] > 0) {
                        cooldown[0]--;
                        throw new GameTestAssertException("settling");
                    }
                    final NetworkStorage storage = net.storage(helper.getLevel());
                    final List<Item> batch = waves.get(wave[0]);
                    switch (phase[0]) {
                        case 0 -> { // SEED: load this wave's recipes, top the raw pool up, let the index absorb it
                            for (final CraftingPattern recipe : waveRecipes.get(wave[0])) {
                                helper.assertTrue(net.cc().loadPattern(recipe), "wave recipe must load into the ROM");
                            }
                            for (final Item raw : rawPool) {
                                final long have = storage.count(raw);
                                if (have < RAW_LEVEL) {
                                    net.seed(raw, (int) (RAW_LEVEL - have));
                                }
                            }
                            phase[0] = 1;
                            cooldown[0] = 4;
                            throw new GameTestAssertException("seeded wave " + wave[0]);
                        }
                        case 1 -> { // SUBMIT: every item in the wave through a random entry point
                            for (final Item item : batch) {
                                if (rng.nextBoolean()) {
                                    net.mainframe().submitCraftRequest(StorageKey.of(item), REQUEST_QTY, true, "wave", null);
                                } else {
                                    final String id = BuiltInRegistries.ITEM.getKey(item).toString();
                                    final IqlParseResult parsed = IqlParser.tryParse("craft " + REQUEST_QTY + " " + id);
                                    if (parsed.ok()) {
                                        cli[0].execute(parsed.operation());
                                    } else {
                                        // A valid item id IQL cannot parse is itself worth seeing; keep the churn
                                        // going through the shared entry point so one quirk doesn't mask the rest.
                                        LOGGER.warn("[JSC-MEGA] IQL could not parse craft for {}: {}", id, parsed.error());
                                        net.mainframe().submitCraftRequest(StorageKey.of(item), REQUEST_QTY, true, "wave", null);
                                    }
                                }
                            }
                            phase[0] = 2;
                            throw new GameTestAssertException("submitted wave " + wave[0]);
                        }
                        case 2 -> { // WAIT: hold until this wave's crafts settle
                            if (!net.mainframe().activeOperationRecords().isEmpty()) {
                                throw new GameTestAssertException("wave " + wave[0] + " still crafting");
                            }
                            phase[0] = 3;
                            throw new GameTestAssertException("wave " + wave[0] + " crafted");
                        }
                        default -> { // VERIFY + EXPORT
                            for (final Item item : batch) {
                                final long have = storage.count(item);
                                if (have < REQUEST_QTY) {
                                    failure[0] = "wave " + wave[0] + ": " + BuiltInRegistries.ITEM.getKey(item)
                                            + " not crafted (have " + have + ", seed=" + seed + ")";
                                    return;
                                }
                                storage.select(StorageKey.of(item), have, (key, amount, simulate) -> amount);
                            }
                            // Clear the ROM so the next wave's recipes fit under RECIPE_ROM_LIMIT.
                            while (!net.cc().romPatterns().isEmpty()) {
                                net.cc().removePattern(0);
                            }
                            wave[0]++;
                            phase[0] = 0;
                            throw new GameTestAssertException("exported wave " + (wave[0] - 1));
                        }
                    }
                })
                .thenExecute(() -> helper.assertTrue(failure[0] == null, String.valueOf(failure[0])))
                .thenExecute(() -> {
                    final int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(0, 0, 0)),
                                    helper.absolutePos(new BlockPos(16, 8, 16)))).size();
                    helper.assertTrue(dropped == 0, dropped + " items leaked to the world over the churn (seed=" + seed + ")");
                    LOGGER.info("[JSC-MEGA] churned {} items across {} waves clean", craftable.size(), waves.size());
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 20000)
    public static void craftsAWideMekanismGraphViaMixedPaths(final GameTestHelper helper) {
        final long seed = seed();
        LOGGER.info("[JSC-MEGA] seed={}", seed);
        final Random rng = new Random(seed);

        final List<Item> all = modItems();
        LOGGER.info("[JSC-MEGA] {} mod items found across {}", all.size(), Arrays.toString(NAMESPACES));
        if (all.size() < TIERS * BRANCHING) {
            helper.succeed(); // the mod is not present on this runtime; nothing to craft
            return;
        }

        // Slice a working set and split it into tiers; tier 0 is the raw stock, each higher tier is crafted
        // from strictly lower tiers, so the whole set is reachable from the seeded leaves.
        final List<Item> set = new ArrayList<>(all.subList(0, Math.min(TARGET_COUNT, all.size())));
        final int tierSize = Math.max(1, set.size() / TIERS);
        final List<List<Item>> tiers = new ArrayList<>();
        for (int t = 0; t < TIERS; t++) {
            tiers.add(new ArrayList<>());
        }
        for (int i = 0; i < set.size(); i++) {
            tiers.get(Math.min(TIERS - 1, i / tierSize)).add(set.get(i));
        }

        final List<CraftingPattern> recipes = new ArrayList<>();
        final List<Item> targets = new ArrayList<>();
        for (int t = 1; t < TIERS; t++) {
            // The pool of everything below this tier, to draw ingredients from.
            final List<Item> below = new ArrayList<>();
            for (int lower = 0; lower < t; lower++) {
                below.addAll(tiers.get(lower));
            }
            for (final Item item : tiers.get(t)) {
                final List<Item> ingredients = new ArrayList<>();
                for (int b = 0; b < BRANCHING; b++) {
                    ingredients.add(below.get(rng.nextInt(below.size())));
                }
                recipes.add(benchRecipe(ingredients, item));
                targets.add(item);
            }
        }

        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    placeSupercomputer(helper, CLUSTER_HUB, CLUSTER_NODES);
                    for (final Item leaf : tiers.get(0)) {
                        net.seed(leaf, LEAF_SEED);
                    }
                    for (final CraftingPattern recipe : recipes) {
                        helper.assertTrue(net.cc().loadPattern(recipe), "recipe must load into the ROM");
                    }
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    // The cluster must be online so the crafts fan out across parallel slots.
                    helper.assertTrue(helper.getBlockEntity(CLUSTER_HUB) instanceof HbwInterfaceBlockEntity hbw
                                    && hbw.clusterOnline() && net.mainframe().supercomputerPositions().size() >= 1,
                            "the supercomputer cluster must be online and registered on the network");
                    // Submit every target through a randomly chosen entry point: the shared craft request the
                    // terminal/NI use, or an IQL statement run on a server-side CLI (the Command Prompt path).
                    final ServerCliComputer cli = new ServerCliComputer(net.mainframe(), helper.getLevel());
                    for (final Item item : targets) {
                        if (rng.nextBoolean()) {
                            net.mainframe().submitCraftRequest(StorageKey.of(item), REQUEST_QTY, true, "mega", null);
                        } else {
                            final String id = BuiltInRegistries.ITEM.getKey(item).toString();
                            final IqlParseResult parsed = IqlParser.tryParse("craft " + REQUEST_QTY + " " + id);
                            helper.assertTrue(parsed.ok(), "IQL must parse 'craft " + REQUEST_QTY + " " + id + "'");
                            cli.execute(parsed.operation());
                        }
                    }
                })
                .thenWaitUntil(() -> {
                    final int inflight = net.mainframe().activeOperationRecords().size();
                    if (inflight > 0) {
                        throw new GameTestAssertException(inflight + " crafts still in flight");
                    }
                })
                .thenExecute(() -> {
                    final NetworkStorage storage = net.storage(helper.getLevel());
                    int missing = 0;
                    for (final Item item : targets) {
                        if (storage.count(item) < REQUEST_QTY) {
                            missing++;
                            LOGGER.warn("[JSC-MEGA] not crafted: {} (have {})", BuiltInRegistries.ITEM.getKey(item),
                                    storage.count(item));
                        }
                    }
                    helper.assertTrue(missing == 0, missing + "/" + targets.size()
                            + " targets were not crafted (seed=" + seed + "); recent=" + net.mainframe().recentOperations());
                    // Nothing may leak into the world as a dropped item: every transfer stays accounted for.
                    final int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            AABB.encapsulatingFullBlocks(helper.absolutePos(new BlockPos(0, 0, 0)),
                                    helper.absolutePos(new BlockPos(16, 8, 16)))).size();
                    helper.assertTrue(dropped == 0, dropped + " items leaked to the world (seed=" + seed + ")");
                })
                .thenExecute(() -> {
                    // Churn: export every crafted target back out of the network, the way a SELECT / Export Bus
                    // drains storage. Everything the network handed out must leave cleanly and free its capacity.
                    final NetworkStorage storage = net.storage(helper.getLevel());
                    for (final Item item : targets) {
                        final long have = storage.count(item);
                        if (have <= 0) {
                            continue;
                        }
                        final long taken = storage.select(StorageKey.of(item), have, (key, amount, simulate) -> amount);
                        helper.assertTrue(taken == have, "export must drain all " + have + " "
                                + BuiltInRegistries.ITEM.getKey(item) + "; drained " + taken);
                        helper.assertTrue(storage.count(item) == 0, "the network must hold no "
                                + BuiltInRegistries.ITEM.getKey(item) + " after export; left " + storage.count(item));
                    }
                })
                .thenSucceed();
    }
}
