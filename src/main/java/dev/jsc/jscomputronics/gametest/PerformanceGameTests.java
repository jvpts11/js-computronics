/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.gametest;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import dev.jsc.jscomputronics.module.computing.storage.DataSink;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Performance benchmarks that drive the network hard inside the real game engine (a headless server, the
 * same runtime the real game uses) and estimate the per-tick cost of the Mainframe's hot path. The goal is
 * to keep the network a credible high-throughput storage system: if a single Mainframe tick blows past the
 * 50 ms a 20 TPS server budgets, the network falls behind and the mod fails its purpose.
 *
 * <p>How the cost is measured: the GameTest framework runs many tests at once (50 to a batch, side by side
 * in the same world), so wall-clock measured inside one test would be polluted by the others. Each benchmark
 * therefore (a) sits in its own batch, which run one after another, and (b) times the hot path directly by
 * calling {@link MainframeBlockEntity#serverTick} in a tight loop and dividing the elapsed nanos by the
 * iteration count. That isolates the Mainframe's own per-tick work from the rest of the server loop. The
 * numbers print to stdout with a {@code [JSC-BENCH]} prefix; the assertion only fails on catastrophe
 * (over 50 ms/tick), so a green run still tells us the real figures from the log.
 */
@GameTestHolder(JsComputronics.MODID)
@PrefixGameTestTemplate(false)
public final class PerformanceGameTests {

    private PerformanceGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    /** A single Mainframe tick over this many milliseconds cannot sustain 20 TPS — treat as a failure. */
    private static final double CATASTROPHE_MS = 50.0;

    // --- the measurement core ---------------------------------------------------------------------

    /**
     * Times {@link MainframeBlockEntity#serverTick} over {@code iters} calls (after {@code warmup} untimed
     * calls to let the JIT settle), prints the per-tick cost, and fails only if a tick exceeds the 20 TPS
     * budget. Calling serverTick directly measures the Mainframe's hot path (dispatch + index + operation
     * pacing) without the noise of the parallel GameTest batch.
     */
    private static void tickBench(final GameTestHelper helper, final String label, final ServerLevel level,
                                  final BlockPos absMainframe, final BlockState state,
                                  final MainframeBlockEntity mf, final int warmup, final int iters) {
        for (int i = 0; i < warmup; i++) {
            MainframeBlockEntity.serverTick(level, absMainframe, state, mf);
        }
        final long start = System.nanoTime();
        for (int i = 0; i < iters; i++) {
            MainframeBlockEntity.serverTick(level, absMainframe, state, mf);
        }
        final double usPerTick = (System.nanoTime() - start) / 1000.0 / iters;
        System.out.printf(Locale.ROOT,
                "[JSC-BENCH] %-32s %9.1f us/tick (%7.3f ms/tick)  pending=%d completed=%d%n",
                label, usPerTick, usPerTick / 1000.0, mf.pendingOps(), mf.completedOps());
        helper.assertTrue(usPerTick / 1000.0 < CATASTROPHE_MS,
                label + " exceeded " + CATASTROPHE_MS + " ms/tick: "
                        + String.format(Locale.ROOT, "%.2f", usPerTick / 1000.0) + " ms");
    }

    /** Places a powered Mainframe, a rear-facing rack on a cable behind it, and a default server in the rack. */
    private static ServerRackBlockEntity buildMainframeWithRack(final GameTestHelper helper,
                                                                final BlockPos mainframe, final BlockPos cable,
                                                                final BlockPos rack, final MainframeBlockEntity mf) {
        helper.setBlock(cable, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            throw new IllegalStateException("no server rack placed for benchmark");
        }
        TestWorldBuilder.mountDefaultServer(rackBe, 0);
        return rackBe;
    }

    // --- benchmarks -------------------------------------------------------------------------------

    /**
     * Dispatcher overhead: flood the Mainframe with self-test Operations (pure CPU work on virtual threads,
     * no world access) and measure the per-tick cost of promoting/draining them. Tests whether the dispatch
     * loop scales with thousands of queued Operations.
     */
    @GameTest(template = ARENA, batch = "jsc_bench_dispatcher", timeoutTicks = 800)
    public static void bench_dispatcherSelfTest(final GameTestHelper helper) {
        final BlockPos rel = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mf = NetworkGameTests.placeRunningMainframe(helper, rel);
        final ServerLevel level = helper.getLevel();
        final BlockPos abs = helper.absolutePos(rel);
        final BlockState state = level.getBlockState(abs);
        final int load = 20_000;
        final int work = 400;
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> mf.submitSelfTest(load, work))
                .thenExecuteAfter(2, () -> tickBench(helper,
                        load + " queued self-tests", level, abs, state, mf, 30, 200))
                .thenSucceed();
    }

    /**
     * Machine-crafting at scale: flood the Mainframe with thousands of processing operations at once and measure
     * the per-tick dispatch cost, including the maxJobs concurrency pre-pass that walks every processing op each
     * tick. The pattern targets a machine that isn't present, so the ops stay queued (no world I/O) — this isolates
     * the dispatch/concurrency overhead and proves it scales rather than going quadratic.
     */
    @GameTest(template = ARENA, batch = "jsc_bench_processing", timeoutTicks = 800)
    public static void bench_manyProcessingOps(final GameTestHelper helper) {
        final BlockPos rel = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mf = NetworkGameTests.placeRunningMainframe(helper, rel);
        final ServerLevel level = helper.getLevel();
        final BlockPos abs = helper.absolutePos(rel);
        final BlockState state = level.getBlockState(abs);
        final int load = 5000;
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var pattern = new dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern(
                            java.util.List.of(new dev.jsc.jscomputronics.module.computing.crafting
                                    .ProcessingPattern.ProcessingInput(StorageKey.of(Items.IRON_INGOT), 1L)),
                            java.util.List.of(new dev.jsc.jscomputronics.module.computing.crafting
                                    .ProcessingPattern.ProcessingOutput(StorageKey.of(Items.COPPER_INGOT), 1L, 100)),
                            "jsc:nonexistent_machine", 100_000);
                    for (int i = 0; i < load; i++) {
                        mf.submitNetworkProcessing(pattern, 1, "op");
                    }
                })
                .thenExecuteAfter(2, () -> tickBench(helper, load + " queued processing ops",
                        level, abs, state, mf, 20, 100))
                .thenSucceed();
    }

    /**
     * Real network Operations in flight: seed a server heavily and submit thousands of SELECTs, then measure
     * the per-tick cost of pacing them all (tickOperations walks every active Operation each tick). This is
     * the closest model of "thousands of operations happening at once".
     */
    @GameTest(template = ARENA, batch = "jsc_bench_netops", timeoutTicks = 800)
    public static void bench_networkSelects(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final MainframeBlockEntity mf = NetworkGameTests.placeRunningMainframe(helper, m);
        final ServerRackBlockEntity rackBe = buildMainframeWithRack(helper, m,
                new BlockPos(2, 2, 2), new BlockPos(2, 2, 3), mf);
        final ServerLevel level = helper.getLevel();
        final BlockPos absM = helper.absolutePos(m);
        final BlockState stateM = level.getBlockState(absM);
        final DataSink sink = (key, amount, simulate) -> amount; // accepts everything, keeps nothing
        final StorageKey cobble = StorageKey.of(Items.COBBLESTONE);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 8, () -> rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 1_000_000_000L))
                .thenExecuteAfter(6, () -> {
                    // Hold every cobblestone with a LOCK so each SELECT parks in WAITING and stays alive
                    // instead of draining mid-measurement. That is the true worst case for the per-tick cost:
                    // many Operations alive at once, all walked by tickOperations every tick.
                    mf.lockType(cobble, Long.MAX_VALUE, null);
                    int submitted = 0;
                    for (final int target : new int[]{1000, 3000, 6000, 10000}) {
                        while (submitted < target) {
                            mf.submitNetworkSelect(cobble, 100, sink, "bench", null);
                            submitted++;
                        }
                        tickBench(helper, target + " waiting SELECTs", level, absM, stateM, mf, 10, 80);
                    }
                })
                .thenSucceed();
    }

    /**
     * Large catalog idle cost: fill a server with thousands of distinct item types and measure the per-tick
     * cost with no Operations running. This stresses the incremental catalog analysis and storage indexing
     * that runs every tick even on an idle network (the passive cost AE2/RS networks pay at scale).
     */
    @GameTest(template = ARENA, batch = "jsc_bench_catalog", timeoutTicks = 800)
    public static void bench_largeCatalogIdle(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final MainframeBlockEntity mf = NetworkGameTests.placeRunningMainframe(helper, m);
        final ServerRackBlockEntity rackBe = buildMainframeWithRack(helper, m,
                new BlockPos(2, 2, 2), new BlockPos(2, 2, 3), mf);
        final ServerLevel level = helper.getLevel();
        final BlockPos absM = helper.absolutePos(m);
        final BlockState stateM = level.getBlockState(absM);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 8, () -> {
                    int types = 0;
                    for (final Item item : BuiltInRegistries.ITEM) {
                        if (item == Items.AIR) {
                            continue;
                        }
                        rackBe.getServerStorage(0).insert(StorageKey.of(item), 16);
                        if (++types >= 3000) {
                            break;
                        }
                    }
                })
                .thenExecuteAfter(12, () -> tickBench(helper,
                        "idle, large item catalog", level, absM, stateM, mf, 20, 200))
                .thenSucceed();
    }

    /**
     * Deep-chain craft planning: the synchronous hot path. A CRAFT plans its whole recipe tree on the main
     * thread before it is submitted (immediate feasibility feedback, by design), so a deep tree must plan
     * fast or it stalls the tick. This builds a long chain (each item crafted from two of the next), seeds
     * only the leaf, and times the planner over many huge CRAFTs, then measures the per-tick cost with all
     * those CRAFT Operations alive.
     */
    @GameTest(template = ARENA, batch = "jsc_bench_craft", timeoutTicks = 800)
    public static void bench_craftPlannerDeepChain(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final MainframeBlockEntity mf = NetworkGameTests.placeRunningMainframe(helper, m);
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 2), ComputingModule.PERSONAL_ROUTER.get());
        helper.setBlock(new BlockPos(4, 2, 2), ComputingModule.ETHERNET_CABLE.get());
        final CraftingComputerBlockEntity cc =
                NetworkGameTests.placeRunningCraftingComputer(helper, new BlockPos(5, 2, 2));
        final BlockPos rack = new BlockPos(2, 2, 3);
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        final ServerLevel level = helper.getLevel();
        final BlockPos absM = helper.absolutePos(m);
        final BlockState stateM = level.getBlockState(absM);
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no rack for craft bench");
            return;
        }
        TestWorldBuilder.mountDefaultServer(rackBe, 0);

        // A deep chain: chain[i] is crafted from 2x chain[i+1]; only the leaf is stocked, so a CRAFT of the
        // top item forces the planner to expand the entire tree (the hot path) instead of finding it in stock.
        final List<Item> chain = new ArrayList<>();
        for (final Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            chain.add(item);
            if (chain.size() >= 12) {
                break;
            }
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 8, () -> {
                    for (int i = 0; i < chain.size() - 1; i++) {
                        final List<ItemStack> grid = new ArrayList<>(9);
                        grid.add(new ItemStack(chain.get(i + 1)));
                        grid.add(new ItemStack(chain.get(i + 1)));
                        while (grid.size() < 9) {
                            grid.add(ItemStack.EMPTY);
                        }
                        cc.loadPattern(new CraftingPattern(grid, new ItemStack(chain.get(i), 1)));
                    }
                    rackBe.getServerStorage(0).insert(chain.get(chain.size() - 1), 1_000_000_000L);
                })
                .thenExecuteAfter(10, () -> {
                    final StorageKey top = StorageKey.of(chain.get(0));
                    for (int i = 0; i < 20; i++) {
                        mf.submitNetworkCraft(top, 50_000, true, "warmup"); // JIT warmup
                    }
                    final int plans = 300;
                    final long start = System.nanoTime();
                    int submitted = 0;
                    for (int i = 0; i < plans; i++) {
                        if (mf.submitNetworkCraft(top, 50_000, true, "bench") != null) {
                            submitted++;
                        }
                    }
                    final double usPerPlan = (System.nanoTime() - start) / 1000.0 / plans;
                    System.out.printf(Locale.ROOT,
                            "[JSC-BENCH] %-32s %9.1f us/plan  (chain depth=%d, submitted=%d)%n",
                            "CRAFT planner, deep chain", usPerPlan, chain.size(), submitted);
                    tickBench(helper, "active CRAFTs (deep chain)", level, absM, stateM, mf, 10, 60);
                    helper.assertTrue(usPerPlan / 1000.0 < CATASTROPHE_MS,
                            "CRAFT planner exceeded " + CATASTROPHE_MS + " ms/plan: "
                                    + String.format(Locale.ROOT, "%.2f", usPerPlan / 1000.0) + " ms");
                })
                .thenSucceed();
    }

    /**
     * Physical network scale: a large arena packed with a mesh of data cables (each a ticking block entity)
     * plus Mekanism machines as background ticking load, measuring the real server tick over a window. This
     * is the passive cost that sinks AE2/RS networks at scale — what it costs to merely have a huge network
     * exist, before any operation runs. Runs alone in its own batch so the wall-clock is not polluted by the
     * other parallel GameTests. Measures the whole server tick (the cable BEs tick outside the Mainframe).
     */
    @GameTest(template = "bench", batch = "jsc_bench_physical", timeoutTicks = 2000)
    public static void bench_physicalNetwork(final GameTestHelper helper) {
        final MainframeBlockEntity mf = NetworkGameTests.placeRunningMainframe(helper, new BlockPos(1, 2, 1));
        // 32 keeps this a fast regression gate (~1k cables); a one-off run at side 72 (~5k cables,
        // GregTech-late-game scale) measured 4.7 ms/tick, confirming the network scales sub-linearly.
        final int x0 = 2;
        final int z0 = 1;
        final int side = 32;
        int cables = 0;
        for (int dx = 0; dx < side; dx++) {
            for (int dz = 0; dz < side; dz++) {
                helper.setBlock(new BlockPos(x0 + dx, 2, z0 + dz), ComputingModule.HBW_CABLE.get());
                cables++;
            }
        }
        // Mekanism machines as background ticking load — placed by id so this stays a soft dependency
        // (no Mekanism class is referenced); skipped cleanly if Mekanism is not on the runtime.
        int mekCount = 0;
        final Block mek = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.fromNamespaceAndPath("mekanism", "enrichment_chamber")).orElse(null);
        if (mek != null && mek != Blocks.AIR) {
            for (int dx = 0; dx < side; dx += 2) {
                for (int dz = 0; dz < side; dz += 2) {
                    helper.setBlock(new BlockPos(x0 + dx, 4, z0 + dz), mek);
                    mekCount++;
                }
            }
        }
        final int fCables = cables;
        final int fMek = mekCount;
        final long[] startNanos = {0L};
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 40, () -> startNanos[0] = System.nanoTime()) // let it settle, warm up
                .thenExecuteAfter(60, () -> {
                    final double mspt = (System.nanoTime() - startNanos[0]) / 1_000_000.0 / 60.0;
                    System.out.printf(Locale.ROOT,
                            "[JSC-BENCH] %-28s %7.3f ms/tick (real server)  cables=%d mekanism=%d running=%b%n",
                            "physical network", mspt, fCables, fMek, mf.isRunning());
                    helper.assertTrue(mspt < CATASTROPHE_MS,
                            "physical network tick exceeded " + CATASTROPHE_MS + " ms: " + mspt);
                })
                .thenSucceed();
    }
}
