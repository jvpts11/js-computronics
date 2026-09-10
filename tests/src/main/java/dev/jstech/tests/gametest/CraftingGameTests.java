/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computers.blockentity.DataCableBlockEntity;
import dev.jstech.computers.blockentity.PatternEncoderBlockEntity;
import dev.jstech.computers.crafting.CraftingPattern;
import dev.jstech.computers.os.FilesystemKind;
import dev.jstech.computers.os.fs.CraftFile;
import dev.jstech.computers.os.fs.DiskFilesystem;
import dev.jstech.computers.os.fs.FileType;
import dev.jstech.tests.JsTests;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * GameTests for the autocrafting pattern chain: the Pattern Encoder resolving and writing recipes onto media, rewritable-media erase cycles, the Pattern Reader copying patterns into an adjacent Crafting Computer's Recipe ROM (dedupe + hard cap), and ROM persistence through NBT.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class CraftingGameTests {

    private CraftingGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void studio_benchDraftBurnsOntoMediaAtTheLinkedEncoder(final GameTestHelper helper) {
        /*
         * The workbench lives on the computer; the encoder beside it is its burner. One oak log resolves to
         * four planks through the recipe book, and the burned file reads back as that pattern.
         */
        final Network net = buildCraftingNetwork(helper);
        final BlockPos encoderPos = new BlockPos(5, 2, 3); // adjacent to the Crafting Computer at (5,2,2)
        helper.setBlock(encoderPos, ComputingModule.PATTERN_ENCODER.get());
        if (!(helper.getBlockEntity(encoderPos) instanceof PatternEncoderBlockEntity encoder)) {
            throw new IllegalStateException("no pattern encoder at " + encoderPos);
        }
        encoder.media().setStackInSlot(0, new ItemStack(ComputingModule.DVD_RW.get()));
        final var studio = net.cc.studio();
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(encoder.ownerPos() != null && encoder.ownerPos().equals(net.cc.getBlockPos()),
                            "the encoder links to the adjacent computer; got " + encoder.ownerPos());
                    studio.refreshPreview(helper.getLevel());
                    helper.assertTrue(studio.serialize(dev.jstech.computers.crafting.PatternWorkbench.Kind.BENCH,
                            helper.getLevel().registryAccess()).isEmpty(), "an empty bench serializes to nothing");
                    studio.setGhost(0, new ItemStack(Items.OAK_LOG));
                    studio.refreshPreview(helper.getLevel());
                    helper.assertTrue(studio.preview().is(Items.OAK_PLANKS) && studio.preview().getCount() == 4,
                            "one log previews four planks");
                    final var content = studio.serialize(dev.jstech.computers.crafting.PatternWorkbench.Kind.BENCH,
                            helper.getLevel().registryAccess());
                    helper.assertTrue(content.isPresent(), "a resolved bench draft serializes");
                    helper.assertTrue(encoder.queueBurn("oak_planks", content.get()), "the encoder queues the burn");
                })
                .thenExecuteAfter(120, () -> {
                    final ItemStack media = encoder.mediaStack();
                    final List<DiskFilesystem.FileEntry> files =
                            DiskFilesystem.list(media, "", FilesystemKind.HIERARCHICAL);
                    int craftCount = 0;
                    String craftPath = null;
                    for (final DiskFilesystem.FileEntry e : files) {
                        if (e.type() == FileType.CRAFT) {
                            craftCount++;
                            craftPath = e.path();
                        }
                    }
                    helper.assertTrue(craftCount == 1, "exactly one .craft file is burned");
                    final var content = DiskFilesystem.read(media, craftPath);
                    helper.assertTrue(content.isPresent(), "the .craft file is readable");
                    final var parsed = CraftFile.parse(content.get(), helper.getLevel().registryAccess());
                    helper.assertTrue(parsed.isPresent(), "the .craft round-trips back into a pattern");
                    helper.assertTrue(parsed.get().result().is(Items.OAK_PLANKS) && parsed.get().result().getCount() == 4,
                            "the burned pattern produces four planks");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void encoder_requiresWritableMediaOfItsEra(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.PATTERN_ENCODER.get());
        if (!(helper.getBlockEntity(pos) instanceof PatternEncoderBlockEntity encoder)) {
            throw new IllegalStateException("no pattern encoder at " + pos);
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertFalse(encoder.hasMedia(), "nothing in the bay to write to");
                    // Read-only media (a pressed CD-ROM) is rejected by the bay.
                    helper.assertFalse(encoder.media().isItemValid(0, new ItemStack(ComputingModule.CD_ROM.get())),
                            "read-only media is rejected by the bay");
                    // A floppy is the wrong era for a Standard encoder; a DVD-RW is right.
                    helper.assertFalse(encoder.media().isItemValid(0, new ItemStack(ComputingModule.FLOPPY_DISK.get())),
                            "a Standard encoder refuses a floppy");
                    helper.assertTrue(encoder.media().isItemValid(0, new ItemStack(ComputingModule.DVD_RW.get())),
                            "a Standard encoder takes a DVD-RW");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void recipeRom_capsAtFiftyPatterns(final GameTestHelper helper) {
        final BlockPos cc = new BlockPos(2, 2, 2);
        helper.setBlock(cc, ComputingModule.CRAFTING_COMPUTER.get());
        if (!(helper.getBlockEntity(cc) instanceof CraftingComputerBlockEntity computer)) {
            throw new IllegalStateException("no crafting computer at " + cc);
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    for (int i = 1; i <= CraftingComputerBlockEntity.RECIPE_ROM_LIMIT; i++) {
                        helper.assertTrue(computer.loadPattern(planksPattern(i)),
                                "pattern " + i + " fits under the cap");
                    }
                    helper.assertFalse(computer.loadPattern(sticksPattern()),
                            "the 51st pattern must be rejected");
                    helper.assertTrue(computer.romUsed() == CraftingComputerBlockEntity.RECIPE_ROM_LIMIT,
                            "ROM sits exactly at its hard cap");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void recipeRom_persistsThroughNbtRoundTrip(final GameTestHelper helper) {
        final BlockPos cc = new BlockPos(2, 2, 2);
        helper.setBlock(cc, ComputingModule.CRAFTING_COMPUTER.get());
        if (!(helper.getBlockEntity(cc) instanceof CraftingComputerBlockEntity computer)) {
            throw new IllegalStateException("no crafting computer at " + cc);
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    computer.loadPattern(planksPattern(1));
                    computer.loadPattern(sticksPattern());
                    final CompoundTag saved = computer.saveWithFullMetadata(helper.getLevel().registryAccess());

                    final CraftingComputerBlockEntity reloaded = new CraftingComputerBlockEntity(
                            computer.getBlockPos(), computer.getBlockState());
                    reloaded.loadWithComponents(saved, helper.getLevel().registryAccess());
                    helper.assertTrue(reloaded.romUsed() == 2, "ROM must survive the NBT round-trip");
                    helper.assertTrue(reloaded.romContains(sticksPattern()),
                            "reloaded ROM still holds the same recipes");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void craftingPattern_comparesByValueNotIdentity(final GameTestHelper helper) {
        final CraftingPattern a = planksPattern(4);
        final CraftingPattern b = planksPattern(4);
        helper.assertTrue(a.equals(b), "two patterns holding the same recipe must be equal");
        helper.assertTrue(a.hashCode() == b.hashCode(), "equal patterns must share a hash code");
        helper.assertFalse(a.equals(planksPattern(8)), "a different result count is a different pattern");
        helper.assertFalse(a.equals(sticksPattern()), "a different recipe is not equal");
        helper.succeed();
    }

    // CRAFT engine: end to end over a real network

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void craft_executesSinglePatternEndToEnd(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.OAK_LOG, 2);
                    net.cc.loadPattern(planksPattern(4));
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var op = net.mainframe.submitNetworkCraft(
                            storageKey(Items.OAK_PLANKS), 8, false, "test");
                    helper.assertTrue(op != null, "a feasible CRAFT must be accepted");
                })
                .thenExecuteAfter(40, () -> {
                    final var storage = net.storage(helper);
                    helper.assertTrue(storage.count(Items.OAK_PLANKS) == 8,
                            "network should hold the 8 crafted planks; got "
                                    + storage.count(Items.OAK_PLANKS));
                    helper.assertTrue(storage.count(Items.OAK_LOG) == 0,
                            "both logs are consumed; got " + storage.count(Items.OAK_LOG));
                    helper.assertFalse(net.cc.craftBusy(), "the computer frees up after the craft");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void craft_recursesAndReturnsSurplus(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.OAK_LOG, 2);
                    net.cc.loadPattern(planksPattern(4));
                    net.cc.loadPattern(sticksPattern());
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var op = net.mainframe.submitNetworkCraft(
                            storageKey(Items.STICK), 4, false, "test");
                    helper.assertTrue(op != null, "the recursive CRAFT must be accepted");
                })
                .thenExecuteAfter(40, () -> {
                    final var storage = net.storage(helper);
                    helper.assertTrue(storage.count(Items.STICK) == 4,
                            "network should hold the 4 crafted sticks; got " + storage.count(Items.STICK));
                    helper.assertTrue(storage.count(Items.OAK_LOG) == 1,
                            "only one log is needed; got " + storage.count(Items.OAK_LOG));
                    helper.assertTrue(storage.count(Items.OAK_PLANKS) == 2,
                            "the 2 surplus planks return to storage; got "
                                    + storage.count(Items.OAK_PLANKS));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void craft_partialScalesDownAndReportsIt(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final var opHolder = new java.util.concurrent.atomic.AtomicReference<
                dev.jstech.computers.crafting.NetworkCraftOperation>();
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.OAK_LOG, 1);
                    net.cc.loadPattern(planksPattern(4));
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    // A strict request for 16 planks is impossible with one log...
                    helper.assertTrue(net.mainframe.submitNetworkCraft(
                                    storageKey(Items.OAK_PLANKS), 16, false, "test") == null,
                            "an infeasible strict CRAFT must be rejected");
                    // ...but the partial path crafts as far as the ingredients reach.
                    opHolder.set(net.mainframe.submitNetworkCraft(
                            storageKey(Items.OAK_PLANKS), 16, true, "test"));
                    helper.assertTrue(opHolder.get() != null, "the partial CRAFT must be accepted");
                })
                .thenExecuteAfter(40, () -> {
                    final var op = opHolder.get();
                    helper.assertTrue(op.isDone(), "the partial CRAFT must settle");
                    helper.assertTrue(op.craftStatus()
                                    == dev.jstech.computers.operation.payload
                                    .OperationRecord.STATUS_PARTIAL,
                            "a scaled-down craft settles as COMPLETED_PARTIAL");
                    helper.assertTrue(op.delivered() == 4, "one log yields 4 planks; got " + op.delivered());
                    helper.assertTrue(net.storage(helper).count(Items.OAK_PLANKS) == 4,
                            "the 4 planks land in storage");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void craft_drawsIngredientsAcrossTwoServersAndReleasesLocks(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    TestWorldBuilder.mountDefaultServer(net.rack, 1);
                    net.cc.loadPattern(planksPattern(4));
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    /*
                     * One log on each server, so an 8-plank craft (2 logs) must draw from both, the case
                     * where the lock-order server and the drained server once diverged and left reservations.
                     */
                    net.rack.getServerStorage(0).insert(Items.OAK_LOG, 1);
                    net.rack.getServerStorage(1).insert(Items.OAK_LOG, 1);
                })
                .thenExecuteAfter(SETTLE + 2, () -> helper.assertTrue(
                        net.mainframe.submitNetworkCraft(storageKey(Items.OAK_PLANKS), 8, false, "test") != null,
                        "the two-server craft must be accepted"))
                .thenExecuteAfter(40, () -> {
                    final var storage = net.storage(helper);
                    helper.assertTrue(storage.count(Items.OAK_PLANKS) == 8,
                            "8 planks crafted from logs on two servers; got " + storage.count(Items.OAK_PLANKS));
                    helper.assertTrue(storage.count(Items.OAK_LOG) == 0,
                            "both logs are consumed; got " + storage.count(Items.OAK_LOG));
                    helper.assertTrue(net.mainframe.networkIndex().activeLockCount() == 0,
                            "the craft releases every ingredient reservation");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void craft_withoutPatternIsRejected(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> helper.assertTrue(
                        net.mainframe.submitNetworkCraft(
                                storageKey(Items.PISTON), 1, true, "test") == null,
                        "no pattern on the network produces pistons"))
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void serverRack_faceReflectsInstalledServers(final GameTestHelper helper) {
        final BlockPos rack = new BlockPos(2, 2, 2);
        final net.minecraft.core.Direction facing = net.minecraft.core.Direction.NORTH;
        helper.setBlock(rack, dev.jstech.computers.ComputingModule.SERVER_RACK.get()
                .defaultBlockState().setValue(
                        net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, facing));
        ((dev.jstech.computers.block.ServerRackBlock)
                dev.jstech.computers.ComputingModule.SERVER_RACK.get())
                .setPlacedBy(helper.getLevel(), helper.absolutePos(rack),
                        helper.getBlockState(rack), null, ItemStack.EMPTY);
        if (!(helper.getBlockEntity(rack)
                instanceof dev.jstech.computers.blockentity.ServerRackBlockEntity rackBe)) {
            throw new IllegalStateException("no server rack at " + rack);
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    /*
                     * Each visual bay block covers two rack-unit rows (bay b = rows 2b and 2b+1),
                     * so servers in U0, U2 and U4 light the controller, second column and upper bay.
                     */
                    rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
                    rackBe.getServers().setStackInSlot(2, ComputingModule.defaultServer());
                    rackBe.getServers().setStackInSlot(4, ComputingModule.defaultServer());
                })
                .thenExecuteAfter(SETTLE, () -> {
                    final var bays = dev.jstech.computers.block.ServerRackBlock.BAYS;
                    helper.assertTrue(helper.getBlockState(rack).getValue(bays) == 3,
                            "the controller bay lights up for its server");
                    final BlockPos second = new BlockPos(
                            dev.jstech.computers.block.ServerRackStructure
                                    .bayBlockPos(rack, facing, 1, 0));
                    helper.assertTrue(helper.getBlockState(second).getValue(bays) == 3,
                            "the second column lights up for its server");
                    final BlockPos upper = new BlockPos(
                            dev.jstech.computers.block.ServerRackStructure
                                    .bayBlockPos(rack, facing, 0, 1));
                    helper.assertTrue(helper.getBlockState(upper).getValue(bays) == 3,
                            "the upper bay lights up for its server");
                    rackBe.getServers().setStackInSlot(2, ItemStack.EMPTY);
                })
                .thenExecuteAfter(SETTLE, () -> {
                    final var bays = dev.jstech.computers.block.ServerRackBlock.BAYS;
                    final BlockPos second = new BlockPos(
                            dev.jstech.computers.block.ServerRackStructure
                                    .bayBlockPos(rack, facing, 1, 0));
                    helper.assertTrue(helper.getBlockState(second).getValue(bays) == 0,
                            "pulling a Server empties its bay on the face");
                    helper.assertTrue(helper.getBlockState(rack).getValue(bays) == 3,
                            "the controller bay keeps its own server");
                })
                .thenSucceed();
    }

    // Supercomputer: Phi slots and parallel orchestration

    @GameTest(template = ARENA)
    public static void cluster_surveyAssignsSlotsAndBudget(final GameTestHelper helper) {
        final BlockPos hub = new BlockPos(2, 2, 2);
        // Three nodes in a row east of the interface: slots 1, 2, 3 by discovery order.
        placeCluster(helper, hub, 3);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    if (!(helper.getBlockEntity(hub)
                            instanceof dev.jstech.computers.blockentity
                                    .HbwInterfaceBlockEntity be)) {
                        throw new IllegalStateException("no hbw interface");
                    }
                    // 5100s fit slots 1-2 (8 + 16); the third 5100 is under-rated for slot 3.
                    helper.assertTrue(be.parallelCrafts() == 24,
                            "two rated slots give 24; got " + be.parallelCrafts());
                    final var slots = be.clusterSlots();
                    helper.assertTrue(slots.size() == 3, "three nodes surveyed");
                    helper.assertTrue(slots.get(2).code()
                                    == dev.jstech.computers.blockentity
                                    .HbwInterfaceBlockEntity.SLOT_UNDER_RATED,
                            "a 5100 in slot 3 is flagged under-rated, never crashes");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void supercomputer_unlocksParallelCrafting(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final var first = new java.util.concurrent.atomic.AtomicReference<
                dev.jstech.computers.crafting.NetworkCraftOperation>();
        final var second = new java.util.concurrent.atomic.AtomicReference<
                dev.jstech.computers.crafting.NetworkCraftOperation>();
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.OAK_LOG, 8000);
                    net.cc.loadPattern(planksPattern(4));
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    // No cluster yet: two long crafts, and the second must wait its turn.
                    first.set(net.mainframe.submitNetworkCraft(
                            storageKey(Items.OAK_PLANKS), 12000, false, "test"));
                    second.set(net.mainframe.submitNetworkCraft(
                            storageKey(Items.OAK_PLANKS), 12000, false, "test"));
                    helper.assertTrue(first.get() != null && second.get() != null,
                            "both CRAFTs must be accepted");
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertFalse(first.get().isWaiting(),
                            "the first craft claims the computer");
                    helper.assertTrue(second.get().isWaiting(),
                            "without a Supercomputer the second craft waits in line");
                    first.get().abandon();
                    second.get().abandon();
                })
                .thenExecuteAfter(SETTLE, () -> {
                    /*
                     * Raise a cluster on the backbone: interface against the HBW cable,
                     * one rated node behind it.
                     */
                    placeCluster(helper, new BlockPos(2, 2, 3), 1);
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    /*
                     * Smaller than phase one: the first run consumed some logs before being
                     * abandoned, and BOTH locks must still be fully coverable at once.
                     */
                    first.set(net.mainframe.submitNetworkCraft(
                            storageKey(Items.OAK_PLANKS), 8000, false, "test"));
                    second.set(net.mainframe.submitNetworkCraft(
                            storageKey(Items.OAK_PLANKS), 8000, false, "test"));
                    helper.assertTrue(first.get() != null && second.get() != null,
                            "both CRAFTs must be accepted with the Supercomputer");
                })
                .thenExecuteAfter(6, () -> {
                    helper.assertFalse(first.get().isWaiting(), "first craft runs under the cluster");
                    helper.assertFalse(second.get().isWaiting(),
                            "the Supercomputer cluster runs both crafts in parallel");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void supercomputer_fansOneCraftAcrossComputers(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final BlockPos hub = new BlockPos(2, 2, 3);
        final BlockPos cc2Pos = new BlockPos(4, 2, 1);
        final var opHolder = new java.util.concurrent.atomic.AtomicReference<
                dev.jstech.computers.crafting.NetworkCraftOperation>();
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.OAK_LOG, 8000);
                    net.cc.loadPattern(planksPattern(4));
                    placeSecondCraftingComputer(helper, cc2Pos).loadPattern(planksPattern(4));
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    // Both crafting computers must have joined the same network, both holding the pattern.
                    helper.assertTrue(net.mainframe.craftingComputerPositions().size() >= 2,
                            "both crafting computers join the network; got "
                                    + net.mainframe.craftingComputerPositions().size());
                    placeCluster(helper, hub, 1);
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    // The cluster must be online with room for at least two parallel crafts before we submit.
                    if (!(helper.getBlockEntity(hub)
                            instanceof dev.jstech.computers.blockentity
                                    .HbwInterfaceBlockEntity sc) || !sc.clusterOnline()
                            || sc.parallelCrafts() < 2) {
                        helper.fail("the supercomputer cluster is not online with >=2 slots");
                    }
                    helper.assertTrue(net.mainframe.supercomputerPositions().size() >= 1,
                            "the supercomputer is registered on the network");
                    opHolder.set(net.mainframe.submitNetworkCraft(
                            storageKey(Items.OAK_PLANKS), 24000, false, "test"));
                    helper.assertTrue(opHolder.get() != null, "the large craft is accepted");
                })
                .thenExecuteAfter(4, () -> {
                    /*
                     * The single request fanned out: it claimed one computer per supercomputer slot. The executor
                     * list persists after the craft settles, so this is robust to the craft finishing fast.
                     */
                    helper.assertTrue(opHolder.get().executorCount() >= 2,
                            "one large craft fans out across both crafting computers; executors="
                                    + opHolder.get().executorCount());
                })
                .thenSucceed();
    }

    private static CraftingComputerBlockEntity placeSecondCraftingComputer(
            final GameTestHelper helper, final BlockPos pos) {
        helper.setBlock(pos, ComputingModule.CRAFTING_COMPUTER.get());
        faceRearTowardCable(helper, pos);
        if (!(helper.getBlockEntity(pos) instanceof CraftingComputerBlockEntity ccBe)) {
            throw new IllegalStateException("no second crafting computer");
        }
        final var hw = ccBe.getHardware();
        hw.setStackInSlot(CraftingComputerBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(ComputingModule.MOTHERBOARD_ATX_P.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.CPU_SLOT,
                new ItemStack(ComputingModule.CPU_ASCENT_965.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.RAM_SLOTS_START,
                new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.PCIE_SLOTS_START,
                new ItemStack(ComputingModule.CRAFTING_CARD_T2.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.PSU_SLOT,
                new ItemStack(ComputingModule.PSU_650G.get()));
        ccBe.togglePower();
        return ccBe;
    }

    /**
     * A cluster the way a player wires one: the HBW Interface, a run of high-compute cable east of it,
     * and one Supercomputer Rack hanging off each cable block, each rack seating one node. Racks are
     * leaves on the fabric, so they sit beside the cable run rather than in it.
     */
    private static void placeCluster(final GameTestHelper helper, final BlockPos hub, final int nodes) {
        helper.setBlock(hub, ComputingModule.HBW_INTERFACE.get());
        for (int i = 1; i <= nodes; i++) {
            final BlockPos cable = hub.east(i);
            helper.setBlock(cable, ComputingModule.HPC_CABLE.get());
            /*
             * Above the cable, not beside it: the fixtures' computers and cables occupy the row in
             * front, and a rack dropped there would overwrite them.
             */
            final BlockPos rackPos = cable.above();
            helper.setBlock(rackPos, ComputingModule.SUPERCOMPUTER_RACK.get());
            if (helper.getBlockEntity(rackPos)
                    instanceof dev.jstech.computers.blockentity.ServerRackBlockEntity rack) {
                rack.getServers().setStackInSlot(0, ComputingModule.defaultSupercomputerNode());
            }
        }
    }

    // Network fixture: Mainframe + Server (storage) + Crafting Computer

    /**
     * The assembled test network, with handles on the parts the assertions need.
     */
    /** The recipes that make one result come in a stable order: the machine ones as the ROM holds them, then the bench ones. */
    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void recipesFor_listsMachineRecipesThenBenchPatternsForTheResult(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final var ingot = storageKey(Items.IRON_INGOT);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(net.cc.loadMachineRecipe(dev.jstech.computers.crafting.NetworkRecipe.ofProcessing(
                            smelt(Items.RAW_IRON, Items.IRON_INGOT, "minecraft:furnace", 200).withName("Blast", ""))),
                            "the processing recipe loads");
                    helper.assertTrue(net.cc.loadMachineRecipe(dev.jstech.computers.crafting.NetworkRecipe.ofMultiStage(
                            new dev.jstech.computers.crafting.MultiStagePattern(List.of(
                                    dev.jstech.computers.crafting.MultiStagePattern.Stage.proc(
                                            smelt(Items.RAW_IRON, Items.IRON_INGOT, "minecraft:blast_furnace", 100))))
                                    .withName("Iron line", ""))),
                            "the multi-stage recipe loads");
                    helper.assertTrue(net.cc.loadPattern(nuggetsToIngot()), "the bench pattern loads");
                    helper.assertTrue(net.cc.loadPattern(new dev.jstech.computers.crafting.CraftingPattern(
                            grid(Items.OAK_LOG), new ItemStack(Items.OAK_PLANKS, 4))), "an unrelated bench pattern loads");
                })
                .thenExecuteAfter(SETTLE, () -> {
                    final var recipes = net.mainframe.recipesFor(ingot);
                    helper.assertTrue(recipes.size() == 3, "three recipes make the ingot; got " + recipes.size());
                    helper.assertTrue(recipes.get(0).proc().isPresent() && recipes.get(0).displayName().equals("Blast"),
                            "the processing recipe comes first; got " + recipes.get(0).displayName());
                    helper.assertTrue(recipes.get(1).multi().isPresent() && recipes.get(1).displayName().equals("Iron line"),
                            "the multi-stage recipe comes second; got " + recipes.get(1).displayName());
                    helper.assertTrue(recipes.get(2).bench().isPresent(), "the bench pattern comes last");
                    helper.assertTrue(net.mainframe.recipesFor(storageKey(Items.OAK_PLANKS)).size() == 1,
                            "the planks have their one bench pattern");
                    helper.assertTrue(net.mainframe.recipesFor(storageKey(Items.DIAMOND)).isEmpty(),
                            "nothing makes a diamond");
                })
                .thenSucceed();
    }

    /** A craft request that names a recipe runs that recipe: the pipeline, the machine, or the bench pattern's plan. */
    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void submitCraftRequest_runsTheRecipeThePlayerPicked(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final var ingot = storageKey(Items.IRON_INGOT);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.RAW_IRON, 16);
                    net.seed(helper, Items.IRON_NUGGET, 18);
                    net.cc.loadMachineRecipe(dev.jstech.computers.crafting.NetworkRecipe.ofProcessing(
                            smelt(Items.RAW_IRON, Items.IRON_INGOT, "minecraft:furnace", 200).withName("Blast", "")));
                    net.cc.loadMachineRecipe(dev.jstech.computers.crafting.NetworkRecipe.ofMultiStage(
                            new dev.jstech.computers.crafting.MultiStagePattern(List.of(
                                    dev.jstech.computers.crafting.MultiStagePattern.Stage.proc(
                                            smelt(Items.RAW_IRON, Items.IRON_INGOT, "minecraft:blast_furnace", 100))))
                                    .withName("Iron line", "")));
                    net.cc.loadPattern(nuggetsToIngot());
                })
                .thenExecuteAfter(SETTLE, () -> {
                    final var multi = net.mainframe.submitCraftRequest(ingot, 1, false, "test", null, 1);
                    helper.assertTrue(multi instanceof dev.jstech.computers.crafting.NetworkMultiStageOperation,
                            "index 1 runs the pipeline; got " + multi);
                    final var machine = net.mainframe.submitCraftRequest(ingot, 1, false, "test", null, 0);
                    helper.assertTrue(machine instanceof dev.jstech.computers.crafting.NetworkProcessingOperation,
                            "index 0 runs the machine; got " + machine);
                    final var bench = net.mainframe.submitCraftRequest(ingot, 1, false, "test", null, 2);
                    helper.assertTrue(bench instanceof dev.jstech.computers.crafting.PendingCraftOperation,
                            "index 2 plans the bench pattern; got " + bench);
                    final var auto = net.mainframe.submitCraftRequest(ingot, 1, false, "test", null, 7);
                    helper.assertTrue(auto instanceof dev.jstech.computers.crafting.NetworkProcessingOperation,
                            "an index past the list is the machine's own choice, the first machine recipe; got " + auto);
                })
                // The bench plan runs on the Crafting Computer: the nuggets become an ingot without any furnace.
                .thenExecuteAfter(40, () -> helper.assertTrue(net.storage(helper).count(ingot) >= 1,
                        "the bench recipe picked must craft the ingot from nuggets; ingots="
                                + net.storage(helper).count(ingot)))
                .thenSucceed();
    }

    private static dev.jstech.computers.crafting.ProcessingPattern smelt(
            final net.minecraft.world.item.Item in, final net.minecraft.world.item.Item out,
            final String machineType, final int ticks) {
        return new dev.jstech.computers.crafting.ProcessingPattern(
                List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(storageKey(in), 1L)),
                List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(storageKey(out), 1L, 100)),
                machineType, ticks);
    }

    private static dev.jstech.computers.crafting.CraftingPattern nuggetsToIngot() {
        final java.util.List<ItemStack> grid = new java.util.ArrayList<>();
        for (int i = 0; i < 9; i++) {
            grid.add(new ItemStack(Items.IRON_NUGGET));
        }
        return new dev.jstech.computers.crafting.CraftingPattern(grid, new ItemStack(Items.IRON_INGOT));
    }

    private static java.util.List<ItemStack> grid(final net.minecraft.world.item.Item first) {
        final java.util.List<ItemStack> grid = new java.util.ArrayList<>();
        grid.add(new ItemStack(first));
        for (int i = 1; i < 9; i++) {
            grid.add(ItemStack.EMPTY);
        }
        return grid;
    }

    private record Network(
            dev.jstech.computers.blockentity.MainframeBlockEntity mainframe,
            dev.jstech.computers.blockentity.ServerRackBlockEntity rack,
            CraftingComputerBlockEntity cc) {

        void seed(final GameTestHelper helper, final net.minecraft.world.item.Item item, final int count) {
            rack.getServerStorage(0).insert(item, count);
        }

        dev.jstech.computers.operation.NetworkStorage storage(final GameTestHelper helper) {
            return dev.jstech.computers.operation.NetworkStorage.of(
                    helper.getLevel(), mainframe.networkUuid());
        }
    }

    private static Network buildCraftingNetwork(final GameTestHelper helper) {
        final TestWorldBuilder.CraftingNetwork net = TestWorldBuilder.forGameTest(helper).buildCraftingNetwork();
        return new Network(net.mainframe(), net.rack(), net.cc());
    }

    private static dev.jstech.computers.storage.StorageKey storageKey(
            final net.minecraft.world.item.Item item) {
        return dev.jstech.computers.storage.StorageKey.of(item);
    }

    // Pattern fixtures

    /**
     * Turns a just-placed computer so its rear (its only data port) meets an adjacent horizontal
     * cable, since computers now connect through the back face alone.
     */
    private static void faceRearTowardCable(final GameTestHelper helper, final BlockPos pos) {
        TestWorldBuilder.forGameTest(helper).faceRearTowardCable(pos);
    }

    private static CraftingPattern planksPattern(final int count) {
        final List<ItemStack> grid = emptyGrid();
        grid.set(0, new ItemStack(Items.OAK_LOG));
        return new CraftingPattern(grid, new ItemStack(Items.OAK_PLANKS, count));
    }

    private static CraftingPattern sticksPattern() {
        final List<ItemStack> grid = emptyGrid();
        grid.set(0, new ItemStack(Items.OAK_PLANKS));
        grid.set(3, new ItemStack(Items.OAK_PLANKS));
        return new CraftingPattern(grid, new ItemStack(Items.STICK, 4));
    }

    private static List<ItemStack> emptyGrid() {
        final List<ItemStack> grid = new ArrayList<>(CraftingPattern.GRID_SIZE);
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            grid.add(ItemStack.EMPTY);
        }
        return grid;
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void processing_deliversInputsToTheMachine(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        // The Crafting Computer sits at (5,2,2); wire a crafting cable -> switch, with a Compressor on a switch face.
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos machine = new BlockPos(5, 2, 5);
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machine, dev.jstech.industrial.IndustrialModule.COMPRESSOR.get());
        final String machineType = BuiltInRegistries.BLOCK.getKey(
                dev.jstech.industrial.IndustrialModule.COMPRESSOR.get()).toString();

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () ->
                        // Cobblestone has no compressor recipe, so it stays put and we can observe the delivery.
                        net.seed(helper, Items.COBBLESTONE, 64))
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var pattern = new dev.jstech.computers.crafting.ProcessingPattern(
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.COBBLESTONE), 1L)),
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.STONE), 1L, 100)),
                            machineType, 200);
                    helper.assertTrue(net.mainframe.submitNetworkProcessing(pattern, 4, "test") != null,
                            "the processing operation is accepted");
                })
                .thenExecuteAfter(15, () -> {
                    if (!(helper.getBlockEntity(machine)
                            instanceof dev.jstech.industrial.blockentity
                                    .CompressorBlockEntity compressor)) {
                        helper.fail("no Compressor block entity");
                        return;
                    }
                    final var inv = compressor.getInventory();
                    boolean fed = false;
                    for (int s = 0; s < inv.getSlots(); s++) {
                        if (!inv.getStackInSlot(s).isEmpty() && inv.getStackInSlot(s).is(Items.COBBLESTONE)) {
                            fed = true;
                        }
                    }
                    helper.assertTrue(fed, "the engine delivered the input from the network into the machine");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void processing_collectsOutputsIntoTheNetwork(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos machine = new BlockPos(5, 2, 5);
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machine, dev.jstech.industrial.IndustrialModule.COMPRESSOR.get());
        final String machineType = BuiltInRegistries.BLOCK.getKey(
                dev.jstech.industrial.IndustrialModule.COMPRESSOR.get()).toString();

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.COBBLESTONE, 64);
                    // Pre-place the declared output in the machine's output slot so the engine can pull it.
                    if (helper.getBlockEntity(machine)
                            instanceof dev.jstech.industrial.blockentity
                                    .CompressorBlockEntity compressor) {
                        compressor.getInventory().setStackInSlot(1, new ItemStack(Items.STONE, 8));
                    }
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var pattern = new dev.jstech.computers.crafting.ProcessingPattern(
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.COBBLESTONE), 1L)),
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.STONE), 1L, 100)),
                            machineType, 200);
                    helper.assertTrue(net.mainframe.submitNetworkProcessing(pattern, 1, "test") != null,
                            "the processing operation is accepted");
                })
                .thenExecuteAfter(10, () -> {
                    final long inNetwork = net.storage(helper).count(
                            dev.jstech.computers.storage.StorageKey.of(Items.STONE));
                    helper.assertTrue(inNetwork > 0,
                            "the engine collected the machine's output into the network; net stone=" + inNetwork);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void multiStage_runsItsProcessingStage(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos machine = new BlockPos(5, 2, 5);
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machine, dev.jstech.industrial.IndustrialModule.COMPRESSOR.get());
        final String machineType = BuiltInRegistries.BLOCK.getKey(
                dev.jstech.industrial.IndustrialModule.COMPRESSOR.get()).toString();

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.COBBLESTONE, 64);
                    if (helper.getBlockEntity(machine)
                            instanceof dev.jstech.industrial.blockentity
                                    .CompressorBlockEntity compressor) {
                        compressor.getInventory().setStackInSlot(1, new ItemStack(Items.STONE, 8));
                    }
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var proc = new dev.jstech.computers.crafting.ProcessingPattern(
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.COBBLESTONE), 1L)),
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.STONE), 1L, 100)),
                            machineType, 200);
                    final var multi = new dev.jstech.computers.crafting.MultiStagePattern(
                            List.of(dev.jstech.computers.crafting.MultiStagePattern.Stage.proc(proc)));
                    helper.assertTrue(net.mainframe.submitNetworkMultiStage(multi, 1, "test") != null,
                            "the multi-stage operation is accepted");
                })
                .thenExecuteAfter(14, () -> {
                    final long inNetwork = net.storage(helper).count(
                            dev.jstech.computers.storage.StorageKey.of(Items.STONE));
                    helper.assertTrue(inNetwork > 0,
                            "the multi-stage ran its processing stage and the output reached the network; stone="
                                    + inNetwork);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void craftRequest_runsAMultiStageRecipeTheRecursivePlannerCannotSee(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos machine = new BlockPos(5, 2, 5);
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machine, dev.jstech.industrial.IndustrialModule.COMPRESSOR.get());
        final String machineType = BuiltInRegistries.BLOCK.getKey(
                dev.jstech.industrial.IndustrialModule.COMPRESSOR.get()).toString();
        final var stone = dev.jstech.computers.storage.StorageKey.of(Items.STONE);

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.COBBLESTONE, 64);
                    // Pre-place the declared output so the run has something to collect.
                    if (helper.getBlockEntity(machine)
                            instanceof dev.jstech.industrial.blockentity
                                    .CompressorBlockEntity compressor) {
                        compressor.getInventory().setStackInSlot(1, new ItemStack(Items.STONE, 8));
                    }
                    /*
                     * Load ONLY a multi-stage recipe for stone. The recursive craft planner unwraps processing
                     * patterns but never multi-stage ones, so it is blind to this recipe, which is why the CLI
                     * and IQL, before they shared the terminal's entry point, could not craft it.
                     */
                    final var proc = new dev.jstech.computers.crafting.ProcessingPattern(
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.COBBLESTONE), 1L)),
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(
                                    stone, 1L, 100)),
                            machineType, 200);
                    final var multi = new dev.jstech.computers.crafting.MultiStagePattern(
                            List.of(dev.jstech.computers.crafting.MultiStagePattern.Stage.proc(proc)));
                    net.cc.loadMachineRecipe(
                            dev.jstech.computers.crafting.NetworkRecipe.ofMultiStage(multi));
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    // The old recursive-plan path cannot make stone: no bench or processing pattern produces it.
                    helper.assertTrue(net.mainframe.submitNetworkCraft(stone, 1, true, "test") == null,
                            "the recursive planner must be blind to a multi-stage-only recipe");
                    /*
                     * The shared entry point the CLI/IQL, terminal and Network Interactor all route through finds
                     * the recipe by its result and runs the pipeline.
                     */
                    helper.assertTrue(net.mainframe.submitCraftRequest(stone, 1, true, "test (Shell)", null) != null,
                            "the shared craft entry point must run the multi-stage recipe");
                })
                .thenExecuteAfter(14, () -> helper.assertTrue(net.storage(helper).count(stone) > 0,
                        "the multi-stage recipe ran through the shared entry point; net stone="
                                + net.storage(helper).count(stone)))
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void processing_unknownMachineTimesOutAndConservesInputs(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final java.util.concurrent.atomic.AtomicReference<
                dev.jstech.computers.crafting.NetworkProcessingOperation> op =
                new java.util.concurrent.atomic.AtomicReference<>();
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> net.seed(helper, Items.COBBLESTONE, 16))
                .thenExecuteAfter(SETTLE + 2, () -> {
                    // No switch or machine exists for this type: the op must time out gracefully, not hang.
                    final var pattern = new dev.jstech.computers.crafting.ProcessingPattern(
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.COBBLESTONE), 1L)),
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.STONE), 1L, 100)),
                            "jsc:does_not_exist", 5);
                    op.set(net.mainframe.submitNetworkProcessing(pattern, 4, "test"));
                    helper.assertTrue(op.get() != null, "the operation is accepted");
                })
                .thenExecuteAfter(20, () -> {
                    helper.assertTrue(op.get().isDone(), "the op timed out instead of hanging on a missing machine");
                    helper.assertTrue(net.storage(helper).count(
                                    dev.jstech.computers.storage.StorageKey.of(Items.COBBLESTONE)) >= 16,
                            "no machine ran, so the inputs are conserved in the network");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void processing_lockedMachineIsNotFed(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos machine = new BlockPos(5, 2, 5);
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machine, dev.jstech.industrial.IndustrialModule.COMPRESSOR.get());
        final String machineType = BuiltInRegistries.BLOCK.getKey(
                dev.jstech.industrial.IndustrialModule.COMPRESSOR.get()).toString();

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.COBBLESTONE, 64);
                    if (helper.getBlockEntity(new BlockPos(5, 2, 2))
                            instanceof dev.jstech.computers.blockentity.CraftingComputerBlockEntity cc) {
                        cc.setMachineConfig(machineType, new dev.jstech.computers.blockentity
                                .CraftingComputerBlockEntity.MachineConfig(1, true, false)); // locked = paused
                    }
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var pattern = new dev.jstech.computers.crafting.ProcessingPattern(
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.COBBLESTONE), 1L)),
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.STONE), 1L, 100)),
                            machineType, 200);
                    helper.assertTrue(net.mainframe.submitNetworkProcessing(pattern, 4, "test") != null, "accepted");
                })
                .thenExecuteAfter(15, () -> {
                    if (!(helper.getBlockEntity(machine)
                            instanceof dev.jstech.industrial.blockentity
                                    .CompressorBlockEntity compressor)) {
                        helper.fail("no Compressor");
                        return;
                    }
                    boolean fed = false;
                    final var inv = compressor.getInventory();
                    for (int s = 0; s < inv.getSlots(); s++) {
                        if (inv.getStackInSlot(s).is(Items.COBBLESTONE)) {
                            fed = true;
                        }
                    }
                    helper.assertFalse(fed, "a locked machine must not be fed");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void processing_reachesCompletedStatus(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos machine = new BlockPos(5, 2, 5);
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machine, dev.jstech.industrial.IndustrialModule.COMPRESSOR.get());
        final String machineType = BuiltInRegistries.BLOCK.getKey(
                dev.jstech.industrial.IndustrialModule.COMPRESSOR.get()).toString();
        final java.util.concurrent.atomic.AtomicReference<
                dev.jstech.computers.crafting.NetworkProcessingOperation> op =
                new java.util.concurrent.atomic.AtomicReference<>();
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.COBBLESTONE, 64);
                    if (helper.getBlockEntity(machine)
                            instanceof dev.jstech.industrial.blockentity
                                    .CompressorBlockEntity compressor) {
                        compressor.getInventory().setStackInSlot(1, new ItemStack(Items.STONE, 16));
                    }
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var pattern = new dev.jstech.computers.crafting.ProcessingPattern(
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.COBBLESTONE), 1L)),
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.STONE), 1L, 100)),
                            machineType, 200);
                    op.set(net.mainframe.submitNetworkProcessing(pattern, 1, "test"));
                    final byte live = op.get().liveRecord().status();
                    helper.assertTrue(live == dev.jstech.computers.operation.payload
                                    .OperationRecord.STATUS_WAITING
                            || live == dev.jstech.computers.operation.payload
                                    .OperationRecord.STATUS_PROCESSING,
                            "the live record is in-flight before completion; got " + live);
                })
                .thenExecuteAfter(12, () -> {
                    helper.assertTrue(op.get().isDone(), "the op finished");
                    helper.assertTrue(op.get().toRecord().status() == dev.jstech.computers
                                    .operation.payload.OperationRecord.STATUS_COMPLETED,
                            "status is COMPLETED after collecting the requested output");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void network_aggregatesMachineRecipes(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    if (helper.getBlockEntity(new BlockPos(5, 2, 2))
                            instanceof dev.jstech.computers.blockentity.CraftingComputerBlockEntity cc) {
                        cc.loadMachineRecipe(dev.jstech.computers.crafting.NetworkRecipe
                                .ofProcessing(procPattern("jsindustrial:macerator")));
                        cc.loadMachineRecipe(dev.jstech.computers.crafting.NetworkRecipe
                                .ofProcessing(procPattern("jsindustrial:compressor")));
                    }
                })
                .thenExecuteAfter(2, () -> helper.assertTrue(net.mainframe.networkMachineRecipes().size() == 2,
                        "the Mainframe aggregates the running computer's machine recipes; got "
                                + net.mainframe.networkMachineRecipes().size()))
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void processing_rejectsNonPositiveQuantity(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(net.mainframe.submitNetworkProcessing(procPattern("jsc:x"), 0, "t") == null,
                            "quantity 0 is rejected");
                    helper.assertTrue(net.mainframe.submitNetworkProcessing(procPattern("jsc:x"), -10, "t") == null,
                            "a negative quantity is rejected");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void processing_maxJobsCapsConcurrentOpsPerMachine(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos machine = new BlockPos(5, 2, 5);
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machine, dev.jstech.industrial.IndustrialModule.COMPRESSOR.get());
        final String machineType = BuiltInRegistries.BLOCK.getKey(
                dev.jstech.industrial.IndustrialModule.COMPRESSOR.get()).toString();
        final java.util.concurrent.atomic.AtomicReference<
                dev.jstech.computers.crafting.NetworkProcessingOperation> op1 =
                new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<
                dev.jstech.computers.crafting.NetworkProcessingOperation> op2 =
                new java.util.concurrent.atomic.AtomicReference<>();
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> net.seed(helper, Items.COBBLESTONE, 256))
                .thenExecuteAfter(SETTLE + 2, () -> {
                    /*
                     * Cobblestone has no compressor recipe, so neither op completes, they just contend; with the
                     * default maxJobs=1 only one may run on the machine at a time.
                     */
                    op1.set(net.mainframe.submitNetworkProcessing(cobblePattern(machineType), 999, "a"));
                    op2.set(net.mainframe.submitNetworkProcessing(cobblePattern(machineType), 999, "b"));
                })
                .thenExecuteAfter(10, () -> {
                    int active = 0;
                    if (!op1.get().isWaiting() && !op1.get().isDone()) {
                        active++;
                    }
                    if (!op2.get().isWaiting() && !op2.get().isDone()) {
                        active++;
                    }
                    helper.assertTrue(active <= 1,
                            "maxJobs=1 keeps at most one op active on the machine; active=" + active);
                    helper.assertTrue(op1.get().isWaiting() || op2.get().isWaiting(),
                            "the op over the concurrency cap is WAITING");
                })
                .thenSucceed();
    }

    private static dev.jstech.computers.crafting.ProcessingPattern cobblePattern(
            final String machineType) {
        return new dev.jstech.computers.crafting.ProcessingPattern(
                List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(
                        dev.jstech.computers.storage.StorageKey.of(Items.COBBLESTONE), 1L)),
                List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(
                        dev.jstech.computers.storage.StorageKey.of(Items.STONE), 1L, 100)),
                machineType, 200);
    }

    private static dev.jstech.computers.crafting.ProcessingPattern procPattern(
            final String machineType) {
        return new dev.jstech.computers.crafting.ProcessingPattern(
                List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(
                        dev.jstech.computers.storage.StorageKey.of(Items.IRON_INGOT), 1L)),
                List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(
                        dev.jstech.computers.storage.StorageKey.of(Items.COPPER_INGOT), 1L, 100)),
                machineType, 200);
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void processing_malformedPatternSettlesFailed(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var key = dev.jstech.computers.storage.StorageKey.of(Items.COBBLESTONE);
                    final var stone = dev.jstech.computers.storage.StorageKey.of(Items.STONE);
                    // No outputs => no result key => the op must settle FAILED at construction, never hang.
                    final var noOut = new dev.jstech.computers.crafting.ProcessingPattern(
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(
                                    key, 1L)),
                            List.of(), "jsc:x", 200);
                    final var op = net.mainframe.submitNetworkProcessing(noOut, 1, "test");
                    helper.assertTrue(op != null && op.isDone(), "a pattern with no outputs settles immediately");
                    helper.assertTrue(op.toRecord().status()
                            == dev.jstech.computers.operation.payload.OperationRecord.STATUS_FAILED,
                            "and its status is FAILED");
                    // No inputs => also FAILED.
                    final var noIn = new dev.jstech.computers.crafting.ProcessingPattern(
                            List.of(),
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(
                                    stone, 1L, 100)),
                            "jsc:x", 200);
                    final var op2 = net.mainframe.submitNetworkProcessing(noIn, 1, "test");
                    helper.assertTrue(op2 != null && op2.isDone()
                            && op2.toRecord().status() == dev.jstech.computers.operation.payload
                                    .OperationRecord.STATUS_FAILED, "a pattern with no inputs is FAILED too");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void processing_conservesItemsAcrossTheCraft(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos machine = new BlockPos(5, 2, 5);
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machine, dev.jstech.industrial.IndustrialModule.COMPRESSOR.get());
        final String machineType = BuiltInRegistries.BLOCK.getKey(
                dev.jstech.industrial.IndustrialModule.COMPRESSOR.get()).toString();
        final long[] before = new long[2];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.COBBLESTONE, 200);
                    if (helper.getBlockEntity(machine)
                            instanceof dev.jstech.industrial.blockentity
                                    .CompressorBlockEntity compressor) {
                        compressor.getInventory().setStackInSlot(1, new ItemStack(Items.STONE, 64));
                    }
                    before[0] = totalOf(helper, net, machine, Items.COBBLESTONE);
                    before[1] = totalOf(helper, net, machine, Items.STONE);
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var pattern = new dev.jstech.computers.crafting.ProcessingPattern(
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.COBBLESTONE), 1L)),
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.STONE), 1L, 100)),
                            machineType, 200);
                    net.mainframe.submitNetworkProcessing(pattern, 32, "conserve");
                })
                .thenExecuteAfter(40, () -> {
                    // The engine only MOVES items (network -> machine -> network); nothing is created or destroyed.
                    final long cobbleAfter = totalOf(helper, net, machine, Items.COBBLESTONE);
                    final long stoneAfter = totalOf(helper, net, machine, Items.STONE);
                    helper.assertTrue(cobbleAfter == before[0],
                            "cobblestone conserved: " + before[0] + " -> " + cobbleAfter);
                    helper.assertTrue(stoneAfter == before[1],
                            "stone conserved: " + before[1] + " -> " + stoneAfter);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void networkStorage_conservesFluidsThroughInsertAndSelect(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var storage = net.storage(helper);
                    final var water = dev.jstech.computers.storage.StorageKey.of(
                            new FluidStack(Fluids.WATER, 1));
                    final long inserted = storage.insert(water, 8000L);
                    helper.assertTrue(inserted > 0, "the network accepts fluid into its mB-eq capacity");
                    helper.assertTrue(storage.count(water) == inserted, "the inserted fluid is counted exactly");
                    final FluidTank tank = new FluidTank(1_000_000);
                    final var port = new dev.jstech.computers.storage.ExternalDataPort(null, tank);
                    final long moved = storage.select(water, inserted, port);
                    helper.assertTrue(moved == inserted, "all the fluid moves out of the network");
                    helper.assertTrue(storage.count(water) == 0, "the network fluid is fully drained");
                    helper.assertTrue(tank.getFluidAmount() == inserted,
                            "the sink holds exactly what left, fluid conserved (" + inserted + " mB)");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void processing_conservesItemsOnTimeout(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos machine = new BlockPos(5, 2, 5);
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machine, dev.jstech.industrial.IndustrialModule.COMPRESSOR.get());
        final String machineType = BuiltInRegistries.BLOCK.getKey(
                dev.jstech.industrial.IndustrialModule.COMPRESSOR.get()).toString();
        final long[] before = new long[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.COBBLESTONE, 128);
                    before[0] = totalOf(helper, net, machine, Items.COBBLESTONE);
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    /*
                     * No output ever appears (the machine has no cobblestone recipe) and the timeout is short, so
                     * the op times out after feeding some inputs. None of those inputs may be lost.
                     */
                    final var pattern = new dev.jstech.computers.crafting.ProcessingPattern(
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.COBBLESTONE), 1L)),
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.STONE), 1L, 100)),
                            machineType, 6);
                    net.mainframe.submitNetworkProcessing(pattern, 64, "timeout");
                })
                .thenExecuteAfter(40, () -> {
                    final long after = totalOf(helper, net, machine, Items.COBBLESTONE);
                    helper.assertTrue(after == before[0],
                            "no cobblestone is lost on timeout: " + before[0] + " -> " + after);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void processing_manyConcurrentOpsConserveAndDontCrash(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos machine = new BlockPos(5, 2, 5);
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machine, dev.jstech.industrial.IndustrialModule.COMPRESSOR.get());
        final String machineType = BuiltInRegistries.BLOCK.getKey(
                dev.jstech.industrial.IndustrialModule.COMPRESSOR.get()).toString();
        final long[] before = new long[2];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.COBBLESTONE, 1000);
                    if (helper.getBlockEntity(machine)
                            instanceof dev.jstech.industrial.blockentity
                                    .CompressorBlockEntity compressor) {
                        compressor.getInventory().setStackInSlot(1, new ItemStack(Items.STONE, 64));
                    }
                    before[0] = totalOf(helper, net, machine, Items.COBBLESTONE);
                    before[1] = totalOf(helper, net, machine, Items.STONE);
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    /*
                     * Fire 50 ops at the single machine at once. maxJobs caps the active one; the rest WAIT.
                     * The queue must not crash (the snapshot-iteration fix) and nothing may be created or lost.
                     */
                    for (int i = 0; i < 50; i++) {
                        net.mainframe.submitNetworkProcessing(cobblePattern(machineType), 4, "op" + i);
                    }
                })
                .thenExecuteAfter(80, () -> {
                    final long cobbleAfter = totalOf(helper, net, machine, Items.COBBLESTONE);
                    final long stoneAfter = totalOf(helper, net, machine, Items.STONE);
                    helper.assertTrue(cobbleAfter == before[0],
                            "cobblestone conserved under 50 concurrent ops: " + before[0] + " -> " + cobbleAfter);
                    helper.assertTrue(stoneAfter == before[1],
                            "stone conserved under 50 concurrent ops: " + before[1] + " -> " + stoneAfter);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void processing_conservesItemsOnAbandon(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos machine = new BlockPos(5, 2, 5);
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machine, dev.jstech.industrial.IndustrialModule.COMPRESSOR.get());
        final String machineType = BuiltInRegistries.BLOCK.getKey(
                dev.jstech.industrial.IndustrialModule.COMPRESSOR.get()).toString();
        final java.util.concurrent.atomic.AtomicReference<
                dev.jstech.computers.crafting.NetworkProcessingOperation> op =
                new java.util.concurrent.atomic.AtomicReference<>();
        final long[] before = new long[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.COBBLESTONE, 128);
                    before[0] = totalOf(helper, net, machine, Items.COBBLESTONE);
                })
                .thenExecuteAfter(SETTLE + 2, () ->
                        op.set(net.mainframe.submitNetworkProcessing(cobblePattern(machineType), 64, "abandon")))
                .thenExecuteAfter(8, () -> op.get().abandon())
                .thenExecuteAfter(8, () -> {
                    helper.assertTrue(op.get().isDone(), "an abandoned op settles");
                    final long after = totalOf(helper, net, machine, Items.COBBLESTONE);
                    helper.assertTrue(after == before[0],
                            "no cobblestone lost on abandon mid-craft: " + before[0] + " -> " + after);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void processing_conservesItemsThroughPowerCycle(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos machine = new BlockPos(5, 2, 5);
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machine, dev.jstech.industrial.IndustrialModule.COMPRESSOR.get());
        final String machineType = BuiltInRegistries.BLOCK.getKey(
                dev.jstech.industrial.IndustrialModule.COMPRESSOR.get()).toString();
        final long[] before = new long[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.COBBLESTONE, 128);
                    if (helper.getBlockEntity(machine)
                            instanceof dev.jstech.industrial.blockentity
                                    .CompressorBlockEntity compressor) {
                        compressor.getInventory().setStackInSlot(1, new ItemStack(Items.STONE, 16));
                    }
                    before[0] = totalOf(helper, net, machine, Items.COBBLESTONE);
                })
                .thenExecuteAfter(SETTLE + 2, () ->
                        net.mainframe.submitNetworkProcessing(cobblePattern(machineType), 8, "power"))
                .thenExecuteAfter(6, () -> net.mainframe.togglePower())  // cut power mid-craft
                .thenExecuteAfter(12, () -> net.mainframe.togglePower()) // power back on
                .thenExecuteAfter(15, () -> {
                    final long after = totalOf(helper, net, machine, Items.COBBLESTONE);
                    helper.assertTrue(after == before[0],
                            "no cobblestone lost through a power-cycle mid-craft: " + before[0] + " -> " + after);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void processing_repeatedSubmitsAreIndependent(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var op1 = net.mainframe.submitNetworkProcessing(procPattern("jsc:x"), 4, "a");
                    final var op2 = net.mainframe.submitNetworkProcessing(procPattern("jsc:x"), 4, "b");
                    final var op3 = net.mainframe.submitNetworkProcessing(procPattern("jsc:x"), 4, "c");
                    helper.assertTrue(op1 != null && op2 != null && op3 != null,
                            "every submit is accepted");
                    helper.assertTrue(op1 != op2 && op2 != op3 && op1 != op3,
                            "repeated identical submits create independent operations, no aliasing");
                    helper.assertTrue(!op1.operationId().equals(op2.operationId())
                                    && !op2.operationId().equals(op3.operationId()),
                            "each operation gets a distinct id");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
    public static void mainframe_operationsStateSurvivesReload(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos machine = new BlockPos(5, 2, 5);
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machine, dev.jstech.industrial.IndustrialModule.COMPRESSOR.get());
        final String machineType = BuiltInRegistries.BLOCK.getKey(
                dev.jstech.industrial.IndustrialModule.COMPRESSOR.get()).toString();
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.COBBLESTONE, 64);
                    if (helper.getBlockEntity(machine)
                            instanceof dev.jstech.industrial.blockentity
                                    .CompressorBlockEntity compressor) {
                        compressor.getInventory().setStackInSlot(1, new ItemStack(Items.STONE, 16));
                    }
                })
                .thenExecuteAfter(SETTLE + 2, () ->
                        net.mainframe.submitNetworkProcessing(cobblePattern(machineType), 1, "reload"))
                .thenExecuteAfter(15, () -> {
                    final long completedBefore = net.mainframe.completedOps();
                    final int logBefore = net.mainframe.recentOperations().size();
                    final var reg = helper.getLevel().registryAccess();
                    final var saved = net.mainframe.saveWithFullMetadata(reg);
                    net.mainframe.loadWithComponents(saved, reg);
                    helper.assertTrue(net.mainframe.completedOps() == completedBefore,
                            "the completed-ops total survives a reload: " + completedBefore + " -> "
                                    + net.mainframe.completedOps());
                    helper.assertTrue(net.mainframe.recentOperations().size() == logBefore,
                            "the operations log survives a reload (" + logBefore + " entries)");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void networkStorage_conservesLargeItemLoadAtScale(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.IRON_INGOT, 30_000);
                    final var storage = net.storage(helper);
                    final var iron = dev.jstech.computers.storage.StorageKey.of(Items.IRON_INGOT);
                    final long total = storage.count(iron);
                    helper.assertTrue(total > 0, "the network holds a large item load (" + total + ")");
                    final ItemStackHandler sink = new ItemStackHandler(1024);
                    final var port = new dev.jstech.computers.storage.ExternalDataPort(sink, null);
                    final long moved = storage.select(iron, total, port);
                    helper.assertTrue(moved == total, "the whole load moves out");
                    helper.assertTrue(storage.count(iron) == 0, "the network is fully drained, nothing stuck");
                    long inSink = 0;
                    for (int s = 0; s < sink.getSlots(); s++) {
                        if (sink.getStackInSlot(s).is(Items.IRON_INGOT)) {
                            inSink += sink.getStackInSlot(s).getCount();
                        }
                    }
                    helper.assertTrue(inSink == total,
                            "the sink holds exactly the load, items conserved at scale (" + total + ")");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void networkStorage_conservesLargeFluidLoadAtScale(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var storage = net.storage(helper);
                    final var water = dev.jstech.computers.storage.StorageKey.of(
                            new FluidStack(Fluids.WATER, 1));
                    final long inserted = storage.insert(water, 500_000L);
                    helper.assertTrue(inserted > 0, "the network accepts a large fluid load (" + inserted + " mB)");
                    helper.assertTrue(storage.count(water) == inserted, "the whole load is counted exactly");
                    final FluidTank tank = new FluidTank(4_000_000);
                    final var port = new dev.jstech.computers.storage.ExternalDataPort(null, tank);
                    final long moved = storage.select(water, inserted, port);
                    helper.assertTrue(moved == inserted, "the whole large load moves out in one go");
                    helper.assertTrue(storage.count(water) == 0, "the network is fully drained, nothing stuck");
                    helper.assertTrue(tank.getFluidAmount() == inserted,
                            "the sink holds exactly the load, conserved at scale (" + inserted + " mB)");
                })
                .thenSucceed();
    }

    /** Total of an item across the whole system: network storage plus every slot of the machine. */
    private static long totalOf(final GameTestHelper helper, final Network net, final BlockPos machine,
                                final net.minecraft.world.item.Item item) {
        long total = net.storage(helper).count(
                dev.jstech.computers.storage.StorageKey.of(item));
        if (helper.getBlockEntity(machine)
                instanceof dev.jstech.industrial.blockentity.CompressorBlockEntity compressor) {
            final var inv = compressor.getInventory();
            for (int s = 0; s < inv.getSlots(); s++) {
                if (inv.getStackInSlot(s).is(item)) {
                    total += inv.getStackInSlot(s).getCount();
                }
            }
        }
        return total;
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void processing_survivesMachineRemovedMidCraft(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos machine = new BlockPos(5, 2, 5);
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machine, dev.jstech.industrial.IndustrialModule.COMPRESSOR.get());
        final String machineType = BuiltInRegistries.BLOCK.getKey(
                dev.jstech.industrial.IndustrialModule.COMPRESSOR.get()).toString();
        final java.util.concurrent.atomic.AtomicReference<
                dev.jstech.computers.crafting.NetworkProcessingOperation> op =
                new java.util.concurrent.atomic.AtomicReference<>();
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> net.seed(helper, Items.COBBLESTONE, 64))
                .thenExecuteAfter(SETTLE + 2, () -> {
                    // Short timeout so the op resolves quickly once its machine disappears.
                    final var pattern = new dev.jstech.computers.crafting.ProcessingPattern(
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.COBBLESTONE), 1L)),
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.STONE), 1L, 100)),
                            machineType, 8);
                    op.set(net.mainframe.submitNetworkProcessing(pattern, 64, "test"));
                })
                .thenExecuteAfter(4, () -> helper.setBlock(machine, Blocks.AIR))
                .thenExecuteAfter(20, () -> helper.assertTrue(op.get().isDone(),
                        "the op settles gracefully when its machine is removed, not hanging or crashing"))
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void processing_remoteMachineIsDeclaredAndDrivenThroughBuses(final GameTestHelper helper) {
        /*
         * The machine does NOT touch the switch at all: it hangs off the crafting cable, reached only through
         * a Crafting Input Bus (feeding, from above) and a Crafting Receiving Bus (collecting, from below).
         * The switch must discover it over the cable and the engine must drive it end to end.
         */
        final Network net = buildCraftingNetwork(helper);
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos cable2 = new BlockPos(5, 2, 5);   // continues past the switch
        final BlockPos cable3 = new BlockPos(5, 2, 6);
        final BlockPos furnace = new BlockPos(6, 2, 6);  // beside the far cable, never touching the switch
        final BlockPos cableAbove = new BlockPos(6, 3, 6);
        final BlockPos cableBelow = new BlockPos(6, 1, 6);
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(cable2, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(cable3, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(furnace, Blocks.FURNACE);
        helper.setBlock(cableAbove, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(cableBelow, ComputingModule.CRAFTING_CABLE.get());
        // Bridge cables so the bus cables are part of the switch's cable run (a continuous circuit).
        helper.setBlock(new BlockPos(5, 3, 6), ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(new BlockPos(5, 1, 6), ComputingModule.CRAFTING_CABLE.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    if (helper.getBlockEntity(cableAbove) instanceof DataCableBlockEntity c) {
                        c.addPart(Direction.DOWN,
                                new dev.jstech.computers.block.part.InputBusPart());
                    }
                    if (helper.getBlockEntity(cableBelow) instanceof DataCableBlockEntity c) {
                        c.addPart(Direction.UP,
                                new dev.jstech.computers.block.part.ReceivingBusPart());
                    }
                    net.seed(helper, Items.RAW_COPPER, 32);
                    if (helper.getBlockEntity(furnace) instanceof FurnaceBlockEntity fb) {
                        fb.setItem(2, new ItemStack(Items.COPPER_INGOT, 8));
                    }
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var swBe = (dev.jstech.computers.blockentity
                            .CraftingSwitchBlockEntity) helper.getBlockEntity(sw);
                    final boolean found = swBe.declaredMachines().stream()
                            .anyMatch(m -> m.machineType().equals("minecraft:furnace"));
                    helper.assertTrue(found, "the switch must discover the remote furnace through its buses");
                    helper.assertTrue(swBe.busMachineLines().stream()
                                    .anyMatch(l -> l.blockName().contains("Furnace")
                                            && l.machinePos().equals(helper.absolutePos(furnace))
                                            && l.switchFace() == Direction.SOUTH.get3DDataValue()),
                            "the discovered machine must carry its position and the switch face it hangs from");
                    final var pattern = new dev.jstech.computers.crafting.ProcessingPattern(
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.RAW_COPPER), 1L)),
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.COPPER_INGOT), 1L, 100)),
                            "minecraft:furnace", 200);
                    net.mainframe.submitNetworkProcessing(pattern, 16, "remote");
                })
                .thenExecuteAfter(40, () -> {
                    final var fb = (FurnaceBlockEntity) helper.getBlockEntity(furnace);
                    helper.assertTrue(fb != null && fb.getItem(0).is(Items.RAW_COPPER),
                            "the Input Bus must feed the remote furnace's input slot");
                    helper.assertTrue(fb != null && fb.getItem(2).isEmpty(),
                            "the Receiving Bus must collect the remote furnace's output");
                    final long stored = net.storage(helper)
                            .count(dev.jstech.computers.storage.StorageKey.of(Items.COPPER_INGOT));
                    helper.assertTrue(stored >= 8,
                            "collected ingots must land in network storage, got " + stored);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void processing_sidedMachineRoutesThroughInputAndReceivingBuses(final GameTestHelper helper) {
        /*
         * A vanilla furnace is a sided machine: raw items only enter through the TOP and products only leave
         * through the BOTTOM, and the side face the switch touches accepts nothing. The crafting buses must carry
         * the I/O: an Input Bus on a cable above feeds it, a Receiving Bus on a cable below collects from it.
         */
        final Network net = buildCraftingNetwork(helper);
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos furnace = new BlockPos(5, 2, 5); // the switch touches a SIDE face, which accepts nothing
        final BlockPos cableAbove = new BlockPos(5, 3, 5); // Input Bus faces the furnace TOP (its input face)
        final BlockPos cableBelow = new BlockPos(5, 1, 5); // Receiving Bus faces its BOTTOM (its output face)
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(furnace, Blocks.FURNACE);
        helper.setBlock(cableAbove, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(cableBelow, ComputingModule.CRAFTING_CABLE.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    if (helper.getBlockEntity(cableAbove) instanceof DataCableBlockEntity c) {
                        c.addPart(Direction.DOWN,
                                new dev.jstech.computers.block.part.InputBusPart());
                    }
                    if (helper.getBlockEntity(cableBelow) instanceof DataCableBlockEntity c) {
                        c.addPart(Direction.UP,
                                new dev.jstech.computers.block.part.ReceivingBusPart());
                    }
                    net.seed(helper, Items.RAW_IRON, 32);
                    /*
                     * Pre-load finished ingots in the furnace's OUTPUT slot: collecting them proves the
                     * Receiving Bus path without waiting out a real 200-tick smelt.
                     */
                    if (helper.getBlockEntity(furnace) instanceof FurnaceBlockEntity fb) {
                        fb.setItem(2, new ItemStack(Items.IRON_INGOT, 8));
                    }
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var pattern = new dev.jstech.computers.crafting.ProcessingPattern(
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.RAW_IRON), 1L)),
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(
                                    dev.jstech.computers.storage.StorageKey.of(Items.IRON_INGOT), 1L, 100)),
                            "minecraft:furnace", 200);
                    // Request more than the pre-loaded ingots so the op keeps feeding after collecting them.
                    net.mainframe.submitNetworkProcessing(pattern, 16, "sided");
                })
                .thenExecuteAfter(40, () -> {
                    final var fb = (FurnaceBlockEntity) helper.getBlockEntity(furnace);
                    helper.assertTrue(fb != null && fb.getItem(0).is(Items.RAW_IRON),
                            "the Input Bus above must have fed raw iron into the furnace's top-only input slot");
                    helper.assertTrue(fb != null && fb.getItem(2).isEmpty(),
                            "the Receiving Bus below must have collected the finished ingots");
                    final long ingotsStored = net.storage(helper)
                            .count(dev.jstech.computers.storage.StorageKey.of(Items.IRON_INGOT));
                    helper.assertTrue(ingotsStored >= 8,
                            "the collected ingots must land in the network storage, got " + ingotsStored);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void machineCategory_genericPatternMatchesTaggedFace(final GameTestHelper helper) {
        // Categories are dynamic: every installed recipe type is one.
        final java.util.List<String> categories =
                dev.jstech.computers.crafting.MachineCategory.categoryIds();
        helper.assertTrue(categories.contains("minecraft:smelting"),
                "the dynamic category list must contain minecraft:smelting");
        helper.assertTrue(categories.contains("minecraft:blasting"),
                "the dynamic category list must contain minecraft:blasting");

        // A furnace on a switch face tagged "minecraft:smelting" serves any generic:minecraft:smelting pattern.
        final BlockPos switchPos = new BlockPos(2, 2, 2);
        helper.setBlock(switchPos, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(switchPos.north(), Blocks.FURNACE);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final var sw = (dev.jstech.computers.blockentity.CraftingSwitchBlockEntity)
                            helper.getBlockEntity(switchPos);
                    sw.setFaceCategory(net.minecraft.core.Direction.NORTH, "minecraft:smelting");
                })
                .thenExecuteAfter(SETTLE, () -> {
                    final var sw = (dev.jstech.computers.blockentity.CraftingSwitchBlockEntity)
                            helper.getBlockEntity(switchPos);
                    final var machines = sw.declaredMachines();
                    helper.assertFalse(machines.isEmpty(), "the tagged furnace face must be declared");
                    final var furnace = machines.stream()
                            .filter(m -> m.machineType().equals("minecraft:furnace")).findFirst().orElse(null);
                    helper.assertTrue(furnace != null, "the declared machine must be the furnace");
                    helper.assertTrue("minecraft:smelting".equals(furnace.category()),
                            "the declared machine must carry the face's category");
                    helper.assertTrue(dev.jstech.computers.crafting.NetworkProcessingOperation
                                    .machineMatches(furnace, "generic:minecraft:smelting"),
                            "a generic smelting pattern must match the tagged face");
                    helper.assertFalse(dev.jstech.computers.crafting.NetworkProcessingOperation
                                    .machineMatches(furnace, "generic:minecraft:blasting"),
                            "a generic blasting pattern must not match a smelting-tagged face");
                    helper.assertTrue(dev.jstech.computers.crafting.NetworkProcessingOperation
                                    .machineMatches(furnace, "minecraft:furnace"),
                            "concrete block-id matching must keep working on a tagged face");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void machineCatalog_listsVanillaMachinesAndSkipsNonMachines(final GameTestHelper helper) {
        final java.util.Set<String> ids = new java.util.HashSet<>();
        for (final net.minecraft.resources.ResourceLocation id
                : dev.jstech.computers.crafting.MachineCatalog.machineIds()) {
            ids.add(id.toString());
        }
        // Real processing machines must be present, both vanilla and this mod's own.
        for (final String required : new String[]{
                "minecraft:furnace", "minecraft:blast_furnace", "minecraft:smoker", "minecraft:brewing_stand",
                "minecraft:crafter", "minecraft:hopper", "jsindustrial:macerator", "jsindustrial:compressor"}) {
            helper.assertTrue(ids.contains(required), "machine catalog must contain " + required);
        }
        // Decoration, structure, portals and plain storage must be filtered out.
        for (final String excluded : new String[]{
                "minecraft:chest", "minecraft:ender_chest", "minecraft:barrel", "minecraft:enchanting_table",
                "minecraft:end_portal", "minecraft:end_gateway", "minecraft:lectern", "minecraft:jukebox"}) {
            helper.assertFalse(ids.contains(excluded), "machine catalog must not contain " + excluded);
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void multiStage_mixedPipelineRunsProcThenBench(final GameTestHelper helper) {
        /*
         * A two-stage pipeline mixing both stage kinds: a processing stage (compressor: cobblestone -> stone)
         * followed by a bench stage (stone -> stone button). The processing output must flow through network
         * storage into the bench stage, and the whole pipeline must settle COMPLETED.
         */
        final Network net = buildCraftingNetwork(helper);
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos machine = new BlockPos(5, 2, 5);
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machine, dev.jstech.industrial.IndustrialModule.COMPRESSOR.get());
        final String machineType = BuiltInRegistries.BLOCK.getKey(
                dev.jstech.industrial.IndustrialModule.COMPRESSOR.get()).toString();
        final var opHolder = new java.util.concurrent.atomic.AtomicReference<
                dev.jstech.computers.crafting.NetworkMultiStageOperation>();

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.COBBLESTONE, 64);
                    // Pre-place finished stone in the compressor's output so stage 1 can collect it.
                    if (helper.getBlockEntity(machine)
                            instanceof dev.jstech.industrial.blockentity
                                    .CompressorBlockEntity compressor) {
                        compressor.getInventory().setStackInSlot(1, new ItemStack(Items.STONE, 8));
                    }
                    /*
                     * Deliberately do NOT load the bench pattern into the Recipe ROM: a multi-stage's
                     * bench stage must run from the pattern embedded in the stage itself.
                     */
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var proc = new dev.jstech.computers.crafting.ProcessingPattern(
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(
                                    storageKey(Items.COBBLESTONE), 1L)),
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(
                                    storageKey(Items.STONE), 1L, 100)),
                            machineType, 200);
                    final var multi = new dev.jstech.computers.crafting.MultiStagePattern(
                            List.of(dev.jstech.computers.crafting.MultiStagePattern.Stage.proc(proc),
                                    dev.jstech.computers.crafting.MultiStagePattern.Stage
                                            .bench(stoneButtonPattern())));
                    opHolder.set(net.mainframe.submitNetworkMultiStage(multi, 1, "test"));
                    helper.assertTrue(opHolder.get() != null, "the mixed multi-stage operation is accepted");
                })
                /*
                 * The bench-craft tests alone allow 40 ticks; this window covers the processing stage,
                 * the pipeline handoff, and the timed bench craft.
                 */
                .thenExecuteAfter(80, () -> {
                    helper.assertTrue(opHolder.get().isDone(), "the mixed pipeline must settle");
                    helper.assertTrue(opHolder.get().toRecord().status()
                                    == dev.jstech.computers.operation.payload
                                            .OperationRecord.STATUS_COMPLETED,
                            "the mixed pipeline must settle COMPLETED; status="
                                    + opHolder.get().toRecord().status());
                    final long buttons = net.storage(helper).count(storageKey(Items.STONE_BUTTON));
                    helper.assertTrue(buttons >= 1,
                            "the bench stage must craft the button from the processing stage's output; got "
                                    + buttons);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void multiStage_failedStageFailsThePipelineAndConserves(final GameTestHelper helper) {
        /*
         * Stage 1 targets a machine that does not exist, so it times out FAILED. The pipeline must fail with
         * it, stage 2 must never run, and the network's inputs must be conserved.
         */
        final Network net = buildCraftingNetwork(helper);
        final var opHolder = new java.util.concurrent.atomic.AtomicReference<
                dev.jstech.computers.crafting.NetworkMultiStageOperation>();

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> net.seed(helper, Items.COBBLESTONE, 16))
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var proc = new dev.jstech.computers.crafting.ProcessingPattern(
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(
                                    storageKey(Items.COBBLESTONE), 1L)),
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(
                                    storageKey(Items.STONE), 1L, 100)),
                            "jsc:does_not_exist", 5);
                    final var multi = new dev.jstech.computers.crafting.MultiStagePattern(
                            List.of(dev.jstech.computers.crafting.MultiStagePattern.Stage.proc(proc),
                                    dev.jstech.computers.crafting.MultiStagePattern.Stage
                                            .bench(stoneButtonPattern())));
                    opHolder.set(net.mainframe.submitNetworkMultiStage(multi, 1, "test"));
                    helper.assertTrue(opHolder.get() != null, "the operation is accepted");
                })
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(opHolder.get().isDone(), "the pipeline must settle instead of hanging");
                    helper.assertTrue(opHolder.get().toRecord().status()
                                    == dev.jstech.computers.operation.payload
                                            .OperationRecord.STATUS_FAILED,
                            "a failed stage must fail the whole pipeline; status="
                                    + opHolder.get().toRecord().status());
                    helper.assertTrue(net.storage(helper).count(storageKey(Items.COBBLESTONE)) >= 16,
                            "no machine ran, so the pipeline's inputs are conserved");
                    helper.assertTrue(net.storage(helper).count(storageKey(Items.STONE_BUTTON)) == 0,
                            "the bench stage after the failed stage must never run");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void processing_fillModeFeedsManyLotsPerCycle(final GameTestHelper helper) {
        /*
         * The Machines tab's One/Fill toggle: with feedMax on, one feed cycle keeps delivering lots until the
         * machine is full, instead of the default single lot every cooldown. After a few ticks the machine
         * must hold far more input than the One mode's one-lot-per-4-ticks pace could ever deliver.
         */
        final Network net = buildCraftingNetwork(helper);
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos machine = new BlockPos(5, 2, 5);
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machine, dev.jstech.industrial.IndustrialModule.COMPRESSOR.get());
        final String machineType = BuiltInRegistries.BLOCK.getKey(
                dev.jstech.industrial.IndustrialModule.COMPRESSOR.get()).toString();

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.COBBLESTONE, 64);
                    net.cc.setMachineConfig(machineType, new dev.jstech.computers.blockentity
                            .CraftingComputerBlockEntity.MachineConfig(1, false, true)); // feedMax = Fill
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    // Cobblestone has no compressor recipe, so everything delivered stays in the input slot.
                    final var pattern = new dev.jstech.computers.crafting.ProcessingPattern(
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(
                                    storageKey(Items.COBBLESTONE), 1L)),
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(
                                    storageKey(Items.STONE), 1L, 100)),
                            machineType, 200);
                    // Feeding never exceeds the demand, so ask for enough to let Fill mode show its cadence.
                    helper.assertTrue(net.mainframe.submitNetworkProcessing(pattern, 64, "fill") != null,
                            "the processing operation is accepted");
                })
                .thenExecuteAfter(8, () -> {
                    if (!(helper.getBlockEntity(machine)
                            instanceof dev.jstech.industrial.blockentity
                                    .CompressorBlockEntity compressor)) {
                        helper.fail("no Compressor block entity");
                        return;
                    }
                    long delivered = 0;
                    final var inv = compressor.getInventory();
                    for (int s = 0; s < inv.getSlots(); s++) {
                        if (inv.getStackInSlot(s).is(Items.COBBLESTONE)) {
                            delivered += inv.getStackInSlot(s).getCount();
                        }
                    }
                    // One mode delivers a single lot every 4 ticks, so 8 ticks could never exceed ~3 items.
                    helper.assertTrue(delivered >= 16,
                            "Fill mode must deliver many lots per feed cycle; delivered=" + delivered);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void machineCategory_genericPatternDrivesRemoteBusMachine(final GameTestHelper helper) {
        /*
         * A remote machine discovered over the crafting cable inherits its origin face's category, so a
         * generic pattern must resolve it and drive the whole craft end to end: feed, collect, storage.
         */
        final Network net = buildCraftingNetwork(helper);
        final BlockPos cable = new BlockPos(5, 2, 3);
        final BlockPos sw = new BlockPos(5, 2, 4);
        final BlockPos cable2 = new BlockPos(5, 2, 5);
        final BlockPos cable3 = new BlockPos(5, 2, 6);
        final BlockPos furnace = new BlockPos(6, 2, 6);
        final BlockPos cableAbove = new BlockPos(6, 3, 6);
        final BlockPos cableBelow = new BlockPos(6, 1, 6);
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(cable2, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(cable3, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(furnace, Blocks.FURNACE);
        helper.setBlock(cableAbove, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(cableBelow, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(new BlockPos(5, 3, 6), ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(new BlockPos(5, 1, 6), ComputingModule.CRAFTING_CABLE.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    if (helper.getBlockEntity(cableAbove) instanceof DataCableBlockEntity c) {
                        c.addPart(Direction.DOWN,
                                new dev.jstech.computers.block.part.InputBusPart());
                    }
                    if (helper.getBlockEntity(cableBelow) instanceof DataCableBlockEntity c) {
                        c.addPart(Direction.UP,
                                new dev.jstech.computers.block.part.ReceivingBusPart());
                    }
                    // The remote furnace hangs off the run leaving the switch's SOUTH face; tag that face.
                    if (helper.getBlockEntity(sw) instanceof dev.jstech.computers.blockentity
                            .CraftingSwitchBlockEntity swBe) {
                        swBe.setFaceCategory(Direction.SOUTH, "minecraft:smelting");
                    }
                    net.seed(helper, Items.RAW_COPPER, 32);
                    if (helper.getBlockEntity(furnace) instanceof FurnaceBlockEntity fb) {
                        fb.setItem(2, new ItemStack(Items.COPPER_INGOT, 8));
                    }
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final var swBe = (dev.jstech.computers.blockentity
                            .CraftingSwitchBlockEntity) helper.getBlockEntity(sw);
                    final var remote = swBe.declaredMachines().stream()
                            .filter(m -> m.machineType().equals("minecraft:furnace")).findFirst().orElse(null);
                    helper.assertTrue(remote != null, "the remote furnace must be declared through its buses");
                    helper.assertTrue("minecraft:smelting".equals(remote.category()),
                            "the remote machine must inherit its origin face's category; got '"
                                    + remote.category() + "'");
                    // The survey must also reach the GUI: the update tag carries the bus machine lines.
                    final var tag = swBe.getUpdateTag(helper.getLevel().registryAccess());
                    helper.assertTrue(tag.contains("BusMachineLines")
                                    && !tag.getList("BusMachineLines", Tag.TAG_COMPOUND).isEmpty(),
                            "the update tag must carry the discovered bus machines for the switch GUI");
                    final var pattern = new dev.jstech.computers.crafting.ProcessingPattern(
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingInput(
                                    storageKey(Items.RAW_COPPER), 1L)),
                            List.of(new dev.jstech.computers.crafting.ProcessingPattern.ProcessingOutput(
                                    storageKey(Items.COPPER_INGOT), 1L, 100)),
                            "generic:minecraft:smelting", 200);
                    helper.assertTrue(net.mainframe.submitNetworkProcessing(pattern, 16, "generic") != null,
                            "the generic processing operation is accepted");
                })
                .thenExecuteAfter(40, () -> {
                    final var fb = (FurnaceBlockEntity) helper.getBlockEntity(furnace);
                    helper.assertTrue(fb != null && fb.getItem(0).is(Items.RAW_COPPER),
                            "the generic pattern must feed the remote furnace through the Input Bus");
                    helper.assertTrue(fb != null && fb.getItem(2).isEmpty(),
                            "the Receiving Bus must collect the remote furnace's output");
                    final long stored = net.storage(helper).count(storageKey(Items.COPPER_INGOT));
                    helper.assertTrue(stored >= 8,
                            "collected ingots must land in network storage, got " + stored);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void journey_encodeLoadAndCraftAcrossTheFullChain(final GameTestHelper helper) {
        /*
         * The player's whole path, server-side: encode a recipe onto a floppy at the Pattern Encoder, carry
         * the floppy to a drive linked to the Crafting Computer, read and load it into the Recipe ROM (the
         * exact steps the Crafting Manager's Load button runs), then request the craft through the same
         * entry point the terminal's request popup uses, and watch the result land in network storage.
         */
        final Network net = buildCraftingNetwork(helper);
        final BlockPos encoderPos = new BlockPos(5, 2, 1); // adjacent to the Crafting Computer at (5,2,2)
        final BlockPos drivePos = new BlockPos(5, 2, 3); // adjacent to the Crafting Computer at (5,2,2)
        helper.setBlock(encoderPos, ComputingModule.PATTERN_ENCODER.get());
        helper.setBlock(drivePos, ComputingModule.DVD_DRIVE.get());
        if (!(helper.getBlockEntity(encoderPos) instanceof PatternEncoderBlockEntity encoder)) {
            throw new IllegalStateException("no pattern encoder at " + encoderPos);
        }
        if (!(helper.getBlockEntity(drivePos)
                instanceof dev.jstech.computers.os.media.MediaReaderBlockEntity drive)) {
            throw new IllegalStateException("no media reader at " + drivePos);
        }
        encoder.media().setStackInSlot(0, new ItemStack(ComputingModule.DVD_RW.get()));

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    /*
                     * 1) Author the pattern on the computer's workbench and send it to the linked encoder, exactly
                     *    as the Studio's Burn button does.
                     */
                    helper.assertTrue(encoder.ownerPos() != null && encoder.ownerPos().equals(net.cc.getBlockPos()),
                            "the encoder must link to the adjacent Crafting Computer; got " + encoder.ownerPos());
                    final var studio = net.cc.studio();
                    studio.setGhost(0, new ItemStack(Items.OAK_LOG));
                    studio.refreshPreview(helper.getLevel());
                    final var content = studio.serialize(
                            dev.jstech.computers.crafting.PatternWorkbench.Kind.BENCH,
                            helper.getLevel().registryAccess());
                    helper.assertTrue(content.isPresent() && encoder.queueBurn("oak_planks", content.get()),
                            "the bench draft is sent to the encoder");
                    // The encoder's own bay panel must stay open for a player standing at it.
                    final var player = helper.makeMockPlayer(GameType.CREATIVE);
                    final BlockPos absolute = helper.absolutePos(encoderPos);
                    player.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
                    final var encoderMenu = new dev.jstech.computers.menu.PatternEncoderMenu(
                            1, player.getInventory(), encoder);
                    helper.assertTrue(encoderMenu.stillValid(player), "the Pattern Encoder menu stays open");
                })
                .thenExecuteAfter(120, () -> {
                    helper.assertTrue(encoder.completed() == 1, "the encoder burned the file; completed=" + encoder.completed());
                    helper.assertFalse(encoder.locked(), "the bay is free once the job is over");
                    // 2) Carry the disc over: out of the encoder, into the drive next to the computer.
                    final ItemStack disc = encoder.ejectMedia();
                    helper.assertFalse(disc.isEmpty(), "the disc comes out of the encoder");
                    drive.mediaSlot().setStackInSlot(0, disc);
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    /*
                     * 3) The drive auto-links to the adjacent Crafting Computer over the peripheral system;
                     *    this link is what lets the Crafting Manager list the medium as a volume.
                     */
                    helper.assertTrue(drive.ownerPos() != null
                                    && drive.ownerPos().equals(helper.absolutePos(new BlockPos(5, 2, 2))),
                            "the DVD drive must auto-link to the Crafting Computer; got " + drive.ownerPos());
                    helper.assertTrue(net.cc.linkedEndpoints().contains(helper.absolutePos(drivePos).asLong()),
                            "the Crafting Computer must list the drive as a linked endpoint");
                    // 4) Load the .craft from the linked medium into the Recipe ROM (the Load button's steps).
                    final ItemStack media = drive.mediaSlot().getStackInSlot(0);
                    final List<DiskFilesystem.FileEntry> files =
                            DiskFilesystem.list(media, "", FilesystemKind.HIERARCHICAL);
                    String craftPath = null;
                    for (final DiskFilesystem.FileEntry e : files) {
                        if (e.type() == FileType.CRAFT) {
                            craftPath = e.path();
                        }
                    }
                    helper.assertTrue(craftPath != null, "the carried disc still holds the .craft file");
                    final var content = DiskFilesystem.read(media, craftPath);
                    final var parsed = CraftFile.parse(content.orElse(""), helper.getLevel().registryAccess());
                    helper.assertTrue(parsed.isPresent(), "the .craft parses back into a pattern");
                    helper.assertTrue(net.cc.loadPattern(parsed.get()), "the pattern loads into the Recipe ROM");
                    // The computer's menu must stay open for the player working at it.
                    final var player = helper.makeMockPlayer(GameType.CREATIVE);
                    final BlockPos absolute = helper.absolutePos(new BlockPos(5, 2, 2));
                    player.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
                    final var ccMenu = new dev.jstech.computers.menu.CraftingComputerMenu(
                            1, player.getInventory(), net.cc);
                    helper.assertTrue(ccMenu.stillValid(player), "the Crafting Computer menu stays open");
                    /*
                     * Stock the ingredients now: the craft planner reads the incremental network index,
                     * which needs a tick to absorb a direct store write before the request is planned.
                     */
                    net.seed(helper, Items.OAK_LOG, 8);
                })
                .thenExecuteAfter(SETTLE, () -> {
                    // 5) Request the craft the way the terminal's request popup does.
                    helper.assertTrue(net.mainframe.submitNetworkCraft(
                                    storageKey(Items.OAK_PLANKS), 4, true, "test (Interactor)") != null,
                            "the craft request is accepted");
                })
                .thenExecuteAfter(20, () -> {
                    final long planks = net.storage(helper).count(storageKey(Items.OAK_PLANKS));
                    helper.assertTrue(planks >= 4,
                            "the crafted planks must land in network storage; got " + planks);
                })
                .thenSucceed();
    }

    private static CraftingPattern stoneButtonPattern() {
        final List<ItemStack> grid = emptyGrid();
        grid.set(0, new ItemStack(Items.STONE));
        return new CraftingPattern(grid, new ItemStack(Items.STONE_BUTTON));
    }
}
