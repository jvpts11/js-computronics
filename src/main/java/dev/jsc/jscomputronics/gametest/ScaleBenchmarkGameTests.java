/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.gametest;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import net.minecraft.core.BlockPos;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.operation.payload.NetworkItemEntry;
import dev.jsc.jscomputronics.module.computing.operation.payload.NetworkSnapshotPayload;
import dev.jsc.jscomputronics.module.computing.storage.DriveVolumes;
import dev.jsc.jscomputronics.module.computing.storage.ServerStorageContents;
import dev.jsc.jscomputronics.module.computing.storage.ServerStore;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.module.computing.storage.StorageVolume;
import dev.jsc.jscomputronics.testkit.BenchReport;
import dev.jsc.jscomputronics.testkit.BenchmarkLoad;
import dev.jsc.jscomputronics.testkit.BigBaseScenario;
import dev.jsc.jscomputronics.testkit.TestWorldBuilder;
import io.netty.buffer.Unpooled;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * The proof of scale: a whole late-game base, built for real (cabinets with their part blocks, hundreds
 * of servers, thousands of item types with component variants, supercomputers, computers), put under a
 * terminal's worth of traffic every tick, and measured on the five axes that decide whether the mod holds
 * up — the server tick, the bytes an item catalog costs on the wire, what a save writes, what a reload
 * costs, and whether the cost of an operation stays flat as the catalog grows.
 *
 * <p>Runs alone in its own batch so the clock is not shared with other tests. The numbers go to
 * {@code local/bench/} through {@link BenchReport}; the first run is the baseline and later runs fail
 * when a timed metric gets slower than it by more than {@link #REGRESSION_FACTOR}. The absolute gate is
 * the tick budget: a base this size must stay far from the 50 ms that would drop the server below 20 TPS.
 */
@GameTestHolder(JsComputronics.MODID)
@PrefixGameTestTemplate(false)
public final class ScaleBenchmarkGameTests {

    private static final String ARENA = "bench";
    private static final int SETTLE = 4;
    /** Ticks for the network to register everything and the JIT to warm up before a measurement. */
    private static final int WARMUP_TICKS = 140;
    /** Longer than the server's 100-tick average window, so the average is the loaded phase alone. */
    private static final int MEASURE_TICKS = 200;
    /** Each ramp step runs long enough for the average window to hold only that step. */
    private static final int RAMP_TICKS = 120;
    /** A busy terminal: eight operations a tick, six pulls and two deposits. */
    private static final int OPS_PER_TICK = 8;
    /** The small catalog is this fraction of the full one; the per-operation cost is compared between them. */
    private static final int SMALL_CATALOG_DIVISOR = 40;

    private static final double CATASTROPHE_MS = 50.0;
    private static final double REGRESSION_FACTOR = 2.5;
    private static final double REGRESSION_FLOOR_MS = 2.0;

    private ScaleBenchmarkGameTests() {
    }

    @GameTest(template = ARENA, batch = "jsc_bench_scale", timeoutTicks = 6000)
    public static void bench_bigBaseUnderLoad(final GameTestHelper helper) {
        run(helper, "scale", BigBaseScenario.Params.DEFAULT, 2000);
    }

    /**
     * The base must come up whole when it is raised in SOLID GROUND, which is where a player raises it with
     * {@code /jsc benchmark build}. A cabinet will not raise its parts into occupied space, so before this
     * was handled every cabinet in a real world stayed a lone controller: no racks, no servers, no network,
     * and a benchmark that measured an empty base while reporting success.
     */
    @GameTest(template = ARENA, batch = "jsc_bench_ground", timeoutTicks = 900)
    public static void bench_baseComesUpWholeInSolidGround(final GameTestHelper helper) {
        final BigBaseScenario.Params params = BigBaseScenario.Params.DEFAULT.withTypes(200);
        // Bury the whole build area, exactly as a hillside would.
        for (int x = 0; x < 46; x++) {
            for (int z = 0; z < 46; z++) {
                for (int y = 1; y < 6; y++) {
                    helper.setBlock(new BlockPos(x, y, z), net.minecraft.world.level.block.Blocks.STONE);
                }
            }
        }
        final BigBaseScenario.Built base = BigBaseScenario.build(TestWorldBuilder.forGameTest(helper), params);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 20, () -> guarded(() -> {
                    final NetworkUuid net = base.mainframe().networkUuid();
                    helper.assertTrue(net != null, "the Mainframe joined a network even though it was buried");
                    final int servers = NetworkSystem.get(helper.getLevel()).serversOf(net).size();
                    helper.assertTrue(servers == params.servers(),
                            "every cabinet formed and registered its servers; got " + servers + " of "
                                    + params.servers());
                    // A base whose computers have no disk has no software on it at all, which is not a base.
                    helper.assertTrue(base.mainframe().installedOsId() != null, "the Mainframe runs a system");
                    for (final var pc : base.personalComputers()) {
                        helper.assertTrue(pc.installedOsId() != null, "every desktop machine runs a system");
                    }
                    for (final var cc : base.craftingComputers()) {
                        helper.assertTrue(cc.installedOsId() != null, "every crafting computer runs a system");
                    }
                }))
                .thenSucceed();
    }

    /**
     * The same measurement on a base the size of an expert pack's endgame: a hundred and fifty million items
     * of thirty thousand types, and a crafting workload of bench recipes, recipe chains and machine runs.
     */
    @GameTest(template = ARENA, batch = "jsc_bench_expert_pack", timeoutTicks = 12000)
    public static void bench_expertPackScaleBase(final GameTestHelper helper) {
        run(helper, "expert_pack", BigBaseScenario.Params.EXPERT_PACK, 8000);
    }

    /** How often the crafting requests fire, and how much each asks for. */
    private static final int CRAFT_EVERY_TICKS = 10;
    private static final long RECIPE_QUANTITY = 4096;
    private static final long PROCESSING_QUANTITY = 512;

    private static void run(final GameTestHelper helper, final String name, final BigBaseScenario.Params params,
                            final int denseTypes) {
        final int smallTypes = Math.max(1, params.types() / SMALL_CATALOG_DIVISOR);
        final ServerLevel level = helper.getLevel();
        final MinecraftServer server = level.getServer();
        final BenchReport report = new BenchReport(name);

        final long buildStart = System.nanoTime();
        final BigBaseScenario.Built base = BigBaseScenario.build(TestWorldBuilder.forGameTest(helper),
                params.withTypes(smallTypes));
        report.put("build_ms", (System.nanoTime() - buildStart) / 1_000_000.0);
        report.put("racks", params.racks()).put("servers", params.servers()).put("nodes", params.nodes())
                .put("personal_computers", params.personalComputers()).put("types", params.types())
                .put("types_small", smallTypes).put("ops_per_tick", OPS_PER_TICK)
                .put("drives_per_server", params.drivesPerServer()).put("drive_size", params.driveSize().name())
                .put("max_lot", params.maxLot()).put("mainframe_queues", base.mainframe().parallelQueues());

        // Both traffic runs are prepared up front: a sequence step that fails still lets the next steps run
        // in the same tick, and anything but an assertion failure escaping a step brings the server down.
        final List<StorageKey> fullCatalog = BigBaseScenario.catalog(params.types(), params.variantPercent());
        final BenchmarkLoad.Workload crafting = base.workload(CRAFT_EVERY_TICKS, RECIPE_QUANTITY, PROCESSING_QUANTITY);
        report.put("craft_requests_per_burst", crafting.requests().size())
                .put("machines", base.machines().size()).put("crafting_computers", base.craftingComputers().size());
        final BenchmarkLoad.Session small = new BenchmarkLoad.Session(base.mainframe(), base.catalog(),
                OPS_PER_TICK, MEASURE_TICKS, crafting, 7L);
        final BenchmarkLoad.Session full = new BenchmarkLoad.Session(base.mainframe(), fullCatalog,
                OPS_PER_TICK, MEASURE_TICKS, crafting, 11L);
        // The ramp: the same base under four and sixteen times the traffic, to price one operation.
        final BenchmarkLoad.Session ramp4 = new BenchmarkLoad.Session(base.mainframe(), fullCatalog,
                OPS_PER_TICK * 4, RAMP_TICKS, crafting, 13L);
        final BenchmarkLoad.Session ramp16 = new BenchmarkLoad.Session(base.mainframe(), fullCatalog,
                OPS_PER_TICK * 16, RAMP_TICKS, crafting, 17L);
        final String[] crashed = new String[1];
        final long[] craftedBefore = new long[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + WARMUP_TICKS, () -> guarded(() -> {
                    final boolean networked = base.mainframe().networkUuid() != null;
                    final int servers = networked
                            ? NetworkSystem.get(level).serversOf(base.mainframe().networkUuid()).size() : 0;
                    int online = 0;
                    for (final var hub : base.hubs()) {
                        online += hub.clusterOnline() ? 1 : 0;
                    }
                    final int craftingComputers = base.mainframe().craftingComputerPositions().size();
                    helper.assertTrue(networked && servers == params.servers() && online == base.hubs().size()
                                    && craftingComputers == base.craftingComputers().size(),
                            "the base must come up whole: network " + networked + ", servers " + servers + "/"
                                    + params.servers() + ", supercomputers online " + online + "/" + base.hubs().size()
                                    + ", crafting computers " + craftingComputers + "/" + base.craftingComputers().size());
                    report.put("idle_ms", averageTickMs(server));
                }))
                .thenWaitUntil(() -> drive(helper, small, crashed))
                .thenExecute(() -> guarded(() -> {
                    helper.assertTrue(crashed[0] == null, "the load driver crashed: " + crashed[0]);
                    report.put("load_small_ms", averageTickMs(server));
                    report.put("ops_small_submitted", small.submitted()).put("ops_small_accepted", small.accepted())
                            .put("ops_small_in_flight", base.mainframe().activeOperationRecords().size());
                    helper.assertTrue(small.accepted() == small.submitted(),
                            "the Mainframe accepts every operation; refused " + (small.submitted() - small.accepted()));
                    // Grow the catalog to its full size: the same traffic must not get slower per operation.
                    // The whole catalog is seeded, small keys included: the first run's pulls drained the
                    // smallest lots among them, and every key must be present for the completeness check.
                    final long seeded = BigBaseScenario.seed(base, fullCatalog, 2L);
                    final long[] storage = BigBaseScenario.storageItems(base);
                    report.put("items_seeded_full", seeded).put("storage_items_capacity", storage[0])
                            .put("storage_items_used", storage[1]);
                    helper.assertTrue(storage[1] < storage[0],
                            "the catalog must fit the base's drives with room to spare; used " + storage[1]
                                    + " of " + storage[0]);
                    // The index must know every key right after seeding; traffic will legitimately drain
                    // some small lots later, so completeness is checked here, not at the end.
                    final int distinct = new HashSet<>(fullCatalog).size();
                    final Map<StorageKey, Long> indexed = NetworkStorage.of(level, base.mainframe().networkUuid()).query();
                    long missing = 0;
                    for (final StorageKey key : fullCatalog) {
                        if (!indexed.containsKey(key)) {
                            missing++;
                        }
                    }
                    report.put("catalog_distinct", distinct).put("query_types_seeded", indexed.size());
                    helper.assertTrue(missing == 0,
                            "the index knows every catalog key; " + missing + " of " + distinct + " missing: "
                                    + missingKeys(base, fullCatalog, indexed));
                }))
                // Long enough for the average to have rolled past the seeding tick itself. Seeding the whole
                // catalog is one enormous tick; measured any sooner, a hundredth of THAT is what the number
                // reports, which is a measurement of how fast the machine seeded and not of the idle cost.
                .thenExecuteAfter(TICK_AVERAGE_WINDOW + 20, () -> {
                    report.put("idle_full_ms", averageTickMs(server));
                    craftedBefore[0] = craftedOutput(level, base);
                })
                .thenWaitUntil(() -> drive(helper, full, crashed))
                .thenExecute(() -> guarded(() -> {
                    helper.assertTrue(crashed[0] == null, "the load driver crashed: " + crashed[0]);
                    report.put("load_full_ms", averageTickMs(server));
                    report.put("ops_full_submitted", full.submitted()).put("ops_full_accepted", full.accepted());
                    final double smallMs = Math.max(report.get("load_small_ms"), 0.001);
                    report.put("load_full_over_small", report.get("load_full_ms") / smallMs);
                    // The crafting side: how many requests the Mainframe took, and what it has settled so far.
                    report.put("crafts_submitted", full.craftsSubmitted()).put("crafts_accepted", full.craftsAccepted());
                    report.put("craft_output_delta", craftedOutput(level, base) - craftedBefore[0]);
                    report.put("ops_full_in_flight", base.mainframe().activeOperationRecords().size());
                }))
                .thenWaitUntil(() -> drive(helper, ramp4, crashed))
                .thenExecute(() -> report.put("load_x4_ms", averageTickMs(server))
                        .put("ops_x4_accepted", ramp4.accepted())
                        .put("ops_x4_in_flight", base.mainframe().activeOperationRecords().size()))
                .thenWaitUntil(() -> drive(helper, ramp16, crashed))
                .thenExecute(() -> guarded(() -> {
                    helper.assertTrue(crashed[0] == null, "the load driver crashed: " + crashed[0]);
                    report.put("load_x16_ms", averageTickMs(server)).put("ops_x16_accepted", ramp16.accepted())
                            .put("ops_x16_in_flight", base.mainframe().activeOperationRecords().size());
                    // What one operation costs on the tick, from the slope between the two heaviest runs.
                    final double perOpMs = (report.get("load_x16_ms") - report.get("load_x4_ms"))
                            / (OPS_PER_TICK * 16 - OPS_PER_TICK * 4);
                    report.put("op_marginal_us", perOpMs * 1000.0);
                    measureWire(level, base, report);
                    measurePersistence(level, base, report);
                    measureDenseDrive(level, base, report, denseTypes);
                    finish(helper, report);
                }))
                .thenSucceed();
    }

    private static final int PROBE_INSERTS = 200;

    /**
     * The catalog packed onto ONE server, the way a base's "everything drawer" ends up: what its drive costs
     * on the wire as an item stack, and whether one more insert gets slower as the drive fills with types.
     * These are the numbers behind the item-component storage design; no budget is asserted on them.
     */
    private static void measureDenseDrive(final ServerLevel level, final BigBaseScenario.Built base,
                                          final BenchReport report, final int denseTypes) {
        final ServerRackBlockEntity rack = base.serverRacks().get(base.serverRacks().size() - 1);
        final int slot = BigBaseScenario.serverRow(base.params(), base.params().serversPerRack() - 1);
        final ServerStore store = rack.getServerStorage(slot);
        final ItemStack probeStack = new ItemStack(Items.STONE);
        probeStack.set(DataComponents.CUSTOM_NAME, Component.literal("probe"));
        final StorageKey probe = StorageKey.of(probeStack);

        report.put("dense_types_before", store.view().size());
        report.put("insert_us_few_types", insertMicros(store, probe));
        int placed = 0;
        for (final StorageKey key : BigBaseScenario.catalog(denseTypes, 100)) {
            placed += store.insert(key, 1) == 1 ? 1 : 0;
        }
        report.put("dense_types_after", store.view().size()).put("dense_types_placed", placed);
        report.put("insert_us_many_types", insertMicros(store, probe));
        final double few = Math.max(report.get("insert_us_few_types"), 0.001);
        report.put("insert_cost_many_over_few", report.get("insert_us_many_types") / few);

        int heaviest = 0;
        for (int i = 0; i < rack.getFrontSlots().getSlots(); i++) {
            final ItemStack drive = rack.getFrontSlots().getStackInSlot(i);
            if (!drive.isEmpty()) {
                heaviest = Math.max(heaviest, encodedBytes(level, drive));
            }
        }
        report.put("dense_drive_stack_bytes", heaviest);
    }

    /** Microseconds per single-item insert of {@code key}, averaged over a burst. */
    private static double insertMicros(final ServerStore store, final StorageKey key) {
        final long start = System.nanoTime();
        for (int i = 0; i < PROBE_INSERTS; i++) {
            store.insert(key, 1);
        }
        return (System.nanoTime() - start) / 1000.0 / PROBE_INSERTS;
    }

    /** Everything the crafting traffic has produced so far: planks, sticks and copper plates on the network. */
    private static long craftedOutput(final ServerLevel level, final BigBaseScenario.Built base) {
        final NetworkStorage storage = NetworkStorage.of(level, base.mainframe().networkUuid());
        long total = storage.count(Items.OAK_PLANKS) + storage.count(Items.STICK);
        if (base.processing() != null) {
            for (final var output : base.processing().outputs()) {
                total += storage.count(output.key());
            }
        }
        return total;
    }

    /** The catalog keys the network view lacks, each with the server it was seeded on and what that store holds. */
    private static String missingKeys(final BigBaseScenario.Built base, final List<StorageKey> catalog,
                                      final Map<StorageKey, Long> indexed) {
        final StringBuilder out = new StringBuilder();
        final int racks = base.serverRacks().size();
        final int perRack = base.params().serversPerRack();
        for (int i = 0; i < catalog.size() && out.length() < 1500; i++) {
            final StorageKey key = catalog.get(i);
            if (indexed.containsKey(key)) {
                continue;
            }
            final ServerRackBlockEntity rack = base.serverRacks().get(i % racks);
            final int slot = BigBaseScenario.serverRow(base.params(), (i / racks) % perRack);
            final ServerStore store = rack.getServerStorage(slot);
            out.append(key).append(" @rack").append(i % racks).append("/slot").append(slot)
                    .append(" store(types=").append(store.view().size()).append(", used=").append(store.used())
                    .append(", cap=").append(store.capacity()).append(", has=").append(store.count(key)).append("); ");
        }
        return out.isEmpty() ? "nothing" : out.toString();
    }

    /** Ticks the traffic until its run is over; a crash inside the mod ends the wait and is reported later. */
    private static void drive(final GameTestHelper helper, final BenchmarkLoad.Session session, final String[] crashed) {
        if (crashed[0] != null || session.done()) {
            return;
        }
        try {
            session.tick();
        } catch (final RuntimeException e) {
            crashed[0] = e.toString();
            return;
        }
        helper.assertTrue(session.done(), "the load is still running");
    }

    /** Turns any crash in a measurement step into a test failure instead of a server crash. */
    private static void guarded(final Runnable step) {
        try {
            step.run();
        } catch (final GameTestAssertException e) {
            throw e;
        } catch (final RuntimeException e) {
            throw new GameTestAssertException("benchmark step crashed: " + e);
        }
    }

    /** The bytes the catalog costs on the wire, from the three things that carry it. */
    private static void measureWire(final ServerLevel level, final BigBaseScenario.Built base,
                                    final BenchReport report) {
        final ServerRackBlockEntity rack = base.serverRacks().get(0);
        // A drive in a rack GUI slot syncs as an item stack, contents and all.
        ItemStack heaviest = ItemStack.EMPTY;
        int heaviestBytes = 0;
        for (int slot = 0; slot < rack.getFrontSlots().getSlots(); slot++) {
            final ItemStack drive = rack.getFrontSlots().getStackInSlot(slot);
            if (!drive.isEmpty()) {
                final int bytes = encodedBytes(level, drive);
                if (bytes > heaviestBytes) {
                    heaviestBytes = bytes;
                    heaviest = drive;
                }
            }
        }
        report.put("drive_stack_bytes", heaviestBytes);
        report.put("drive_stack_types", heaviest.isEmpty() ? 0 : DriveVolumes.contents(heaviest).items().size());
        // The cabinet's update tag, sent whenever its visuals change.
        report.put("rack_update_tag_bytes", nbtBytes(rack.getUpdateTag(level.registryAccess())));
        // One page of the item catalog, as the terminal receives it.
        final Map<StorageKey, Long> totals = NetworkStorage.of(level, base.mainframe().networkUuid()).query();
        final List<NetworkItemEntry> page = new ArrayList<>(NetworkSnapshotPayload.MAX_ENTRIES);
        for (final Map.Entry<StorageKey, Long> entry : totals.entrySet()) {
            if (page.size() >= NetworkSnapshotPayload.MAX_ENTRIES) {
                break;
            }
            page.add(new NetworkItemEntry(entry.getKey(), entry.getValue()));
        }
        final RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            NetworkSnapshotPayload.STREAM_CODEC.encode(buf, new NetworkSnapshotPayload(page));
            report.put("catalog_page_bytes", buf.writerIndex());
        } finally {
            buf.release();
        }
    }

    /** What a save writes for the racks and the Mainframe, what loading it back costs, and a full index query. */
    private static void measurePersistence(final ServerLevel level, final BigBaseScenario.Built base,
                                           final BenchReport report) {
        final HolderLookup.Provider registries = level.registryAccess();
        final List<BlockEntity> entities = new ArrayList<>(base.serverRacks());
        entities.addAll(base.nodeRacks());
        entities.add(base.mainframe());
        final List<CompoundTag> tags = new ArrayList<>(entities.size());
        long bytes = 0;
        final long saveStart = System.nanoTime();
        for (final BlockEntity entity : entities) {
            tags.add(entity.saveWithFullMetadata(registries));
        }
        final long saveNanos = System.nanoTime() - saveStart;
        for (final CompoundTag tag : tags) {
            bytes += nbtBytes(tag);
        }
        report.put("save_ms", saveNanos / 1_000_000.0).put("save_bytes", bytes);

        // The stored items themselves live in the volume store, saved once per level save: its cost is
        // the real price of the catalog on disk. Only this base's volumes are counted — the store is
        // shared by every test that ran in this world.
        final RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        final List<StorageVolume> volumes = new ArrayList<>();
        for (final ServerRackBlockEntity rack : entities.stream()
                .filter(ServerRackBlockEntity.class::isInstance).map(ServerRackBlockEntity.class::cast).toList()) {
            for (int slot = 0; slot < rack.getFrontSlots().getSlots(); slot++) {
                final StorageVolume volume = DriveVolumes.peek(rack.getFrontSlots().getStackInSlot(slot));
                if (!volume.isEmpty()) {
                    volumes.add(volume);
                }
            }
        }
        final long volumesStart = System.nanoTime();
        final List<Tag> encoded = new ArrayList<>(volumes.size());
        for (final StorageVolume volume : volumes) {
            encoded.add(ServerStorageContents.CODEC.encodeStart(ops, volume.snapshot()).getOrThrow());
        }
        final long volumesNanos = System.nanoTime() - volumesStart;
        long volumeBytes = 0;
        for (final Tag tag : encoded) {
            final CompoundTag holder = new CompoundTag();
            holder.put("Items", tag);
            volumeBytes += nbtBytes(holder);
        }
        report.put("volumes_save_ms", volumesNanos / 1_000_000.0).put("volumes_save_bytes", volumeBytes)
                .put("volumes_count", volumes.size());

        // A reload is what a chunk coming back does: a fresh block entity reading the saved tag.
        final long loadStart = System.nanoTime();
        for (int i = 0; i < base.serverRacks().size(); i++) {
            final ServerRackBlockEntity rack = base.serverRacks().get(i);
            final ServerRackBlockEntity fresh = new ServerRackBlockEntity(rack.getBlockPos(), rack.getBlockState());
            fresh.loadWithComponents(tags.get(i), registries);
        }
        report.put("reload_ms", (System.nanoTime() - loadStart) / 1_000_000.0);

        final long queryStart = System.nanoTime();
        final Map<StorageKey, Long> totals = NetworkStorage.of(level, base.mainframe().networkUuid()).query();
        report.put("query_ms", (System.nanoTime() - queryStart) / 1_000_000.0).put("query_types", totals.size());
    }

    private static void finish(final GameTestHelper helper, final BenchReport report) {
        final List<String> slower;
        try {
            report.write();
            slower = report.regressions(REGRESSION_FACTOR, REGRESSION_FLOOR_MS);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        helper.assertTrue(report.get("load_full_ms") < CATASTROPHE_MS,
                "a full base under load exceeded " + CATASTROPHE_MS + " ms/tick: " + report.get("load_full_ms"));
        helper.assertTrue(slower.isEmpty(), "slower than the recorded baseline: " + slower);
    }

    /**
     * The server's mean tick over its last {@link #TICK_AVERAGE_WINDOW} ticks. A measurement must therefore
     * be taken at least that many ticks after any one-off heavy tick, or that tick's whole cost is divided
     * into the average and reported as if the server were doing it every tick.
     */
    private static double averageTickMs(final MinecraftServer server) {
        return server.getAverageTickTimeNanos() / 1_000_000.0;
    }

    /** How many ticks {@link MinecraftServer#getAverageTickTimeNanos()} averages over. */
    private static final int TICK_AVERAGE_WINDOW = 100;

    private static int encodedBytes(final ServerLevel level, final ItemStack stack) {
        final RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try {
            ItemStack.STREAM_CODEC.encode(buf, stack);
            return buf.writerIndex();
        } finally {
            buf.release();
        }
    }

    private static int nbtBytes(final CompoundTag tag) {
        final long[] count = new long[1];
        final OutputStream counter = new OutputStream() {
            @Override
            public void write(final int b) {
                count[0]++;
            }

            @Override
            public void write(final byte[] b, final int off, final int len) {
                count[0] += len;
            }
        };
        try {
            NbtIo.write(tag, new DataOutputStream(counter));
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
        return (int) count[0];
    }
}
