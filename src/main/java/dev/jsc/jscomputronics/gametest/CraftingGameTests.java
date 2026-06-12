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
import dev.jsc.jscomputronics.module.computing.blockentity.PatternEncoderBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.PatternReaderBlockEntity;
import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import dev.jsc.jscomputronics.module.computing.item.PatternDiscItem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * GameTests for the autocrafting pattern chain: the Pattern Encoder resolving and writing recipes onto media, rewritable-media erase cycles, the Pattern Reader copying patterns into an adjacent Crafting Computer's Recipe ROM (dedupe + hard cap), and ROM persistence through NBT.
 */
@GameTestHolder(JsComputronics.MODID)
@PrefixGameTestTemplate(false)
public final class CraftingGameTests {

    private CraftingGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    @GameTest(template = ARENA)
    public static void patternEncoder_writesResolvedRecipe(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.PATTERN_ENCODER.get());
        if (!(helper.getBlockEntity(pos) instanceof PatternEncoderBlockEntity encoder)) {
            throw new IllegalStateException("no pattern encoder at " + pos);
        }
        encoder.media().setStackInSlot(0, new ItemStack(ComputingModule.PATTERN_DISC.get()));

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // Empty grid: nothing to write.
                    helper.assertFalse(encoder.writePattern(), "an empty grid must not write a pattern");
                    // One oak log resolves to four planks via the vanilla recipe book.
                    encoder.setGhost(0, new ItemStack(Items.OAK_LOG));
                    helper.assertTrue(encoder.writePattern(), "a resolvable recipe must write");
                    final List<CraftingPattern> patterns =
                            PatternDiscItem.patterns(encoder.media().getStackInSlot(0));
                    helper.assertTrue(patterns.size() == 1, "the disc should hold one pattern");
                    helper.assertTrue(patterns.get(0).result().is(Items.OAK_PLANKS),
                            "the pattern result must be the recipe output");
                    helper.assertTrue(patterns.get(0).result().getCount() == 4,
                            "one log yields four planks");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void patternEncoder_eraseSpendsOneCycle(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.PATTERN_ENCODER.get());
        if (!(helper.getBlockEntity(pos) instanceof PatternEncoderBlockEntity encoder)) {
            throw new IllegalStateException("no pattern encoder at " + pos);
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // Write-once media never erases.
                    final ItemStack writeOnce = new ItemStack(ComputingModule.PATTERN_DISC.get());
                    PatternDiscItem.append(writeOnce, planksPattern(1));
                    encoder.media().setStackInSlot(0, writeOnce);
                    helper.assertFalse(encoder.eraseMedia(), "write-once media must not erase");

                    // Rewritable media erases and spends one cycle.
                    final ItemStack rw = new ItemStack(ComputingModule.PATTERN_DISC_RW.get());
                    PatternDiscItem.append(rw, planksPattern(1));
                    encoder.media().setStackInSlot(0, rw);
                    final int before = PatternDiscItem.cyclesLeft(encoder.media().getStackInSlot(0));
                    helper.assertTrue(encoder.eraseMedia(), "rewritable media erases");
                    final ItemStack after = encoder.media().getStackInSlot(0);
                    helper.assertTrue(PatternDiscItem.patterns(after).isEmpty(), "erase clears all patterns");
                    helper.assertTrue(PatternDiscItem.cyclesLeft(after) == before - 1,
                            "erase spends exactly one cycle");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void patternReader_copiesIntoAdjacentRomWithDedupe(final GameTestHelper helper) {
        final BlockPos cc = new BlockPos(2, 2, 2);
        final BlockPos reader = new BlockPos(3, 2, 2);
        helper.setBlock(cc, ComputingModule.CRAFTING_COMPUTER.get());
        helper.setBlock(reader, ComputingModule.PATTERN_READER.get());
        if (!(helper.getBlockEntity(cc) instanceof CraftingComputerBlockEntity computer)
                || !(helper.getBlockEntity(reader) instanceof PatternReaderBlockEntity readerBe)) {
            throw new IllegalStateException("missing crafting computer or pattern reader");
        }
        final ItemStack disc = new ItemStack(ComputingModule.PATTERN_DISC.get());
        PatternDiscItem.append(disc, planksPattern(1));
        PatternDiscItem.append(disc, sticksPattern());
        readerBe.media().setStackInSlot(0, disc);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(readerBe.adjacentComputer() == computer,
                            "the reader must find the computer it touches");
                    helper.assertTrue(readerBe.loadAll() == 2, "both patterns load on the first pass");
                    helper.assertTrue(computer.romUsed() == 2, "ROM holds two patterns");
                    helper.assertTrue(readerBe.loadAll() == 0, "the second pass is all duplicates");
                    helper.assertTrue(computer.romUsed() == 2, "duplicates never inflate the ROM");
                    helper.assertTrue(PatternDiscItem.patterns(readerBe.media().getStackInSlot(0)).size() == 2,
                            "loading copies - the disc is never altered");
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

    // CRAFT engine — end to end over a real network

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
                dev.jsc.jscomputronics.module.computing.crafting.NetworkCraftOperation>();
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
                                    == dev.jsc.jscomputronics.module.computing.operation.payload
                                    .OperationRecord.STATUS_PARTIAL,
                            "a scaled-down craft settles as COMPLETED_PARTIAL");
                    helper.assertTrue(op.delivered() == 4, "one log yields 4 planks; got " + op.delivered());
                    helper.assertTrue(net.storage(helper).count(Items.OAK_PLANKS) == 4,
                            "the 4 planks land in storage");
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
        helper.setBlock(rack, dev.jsc.jscomputronics.module.computing.ComputingModule.SERVER_RACK.get()
                .defaultBlockState().setValue(
                        net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, facing));
        ((dev.jsc.jscomputronics.module.computing.block.ServerRackBlock)
                dev.jsc.jscomputronics.module.computing.ComputingModule.SERVER_RACK.get())
                .setPlacedBy(helper.getLevel(), helper.absolutePos(rack),
                        helper.getBlockState(rack), null, ItemStack.EMPTY);
        if (!(helper.getBlockEntity(rack)
                instanceof dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity rackBe)) {
            throw new IllegalStateException("no server rack at " + rack);
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // One server per bay block now: slot 0 = controller, slot 1 = second column,
                    // slot 2 = the block above the controller.
                    rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
                    rackBe.getServers().setStackInSlot(1, ComputingModule.defaultServer());
                    rackBe.getServers().setStackInSlot(2, ComputingModule.defaultServer());
                })
                .thenExecuteAfter(SETTLE, () -> {
                    final var bays = dev.jsc.jscomputronics.module.computing.block.ServerRackBlock.BAYS;
                    helper.assertTrue(helper.getBlockState(rack).getValue(bays) == 3,
                            "the controller bay lights up for its server");
                    final BlockPos second = new BlockPos(
                            dev.jsc.jscomputronics.module.computing.block.ServerRackStructure
                                    .bayBlockPos(rack, facing, 1, 0));
                    helper.assertTrue(helper.getBlockState(second).getValue(bays) == 3,
                            "the second column lights up for its server");
                    final BlockPos upper = new BlockPos(
                            dev.jsc.jscomputronics.module.computing.block.ServerRackStructure
                                    .bayBlockPos(rack, facing, 0, 1));
                    helper.assertTrue(helper.getBlockState(upper).getValue(bays) == 3,
                            "the upper bay lights up for its server");
                    rackBe.getServers().setStackInSlot(1, ItemStack.EMPTY);
                })
                .thenExecuteAfter(SETTLE, () -> {
                    final var bays = dev.jsc.jscomputronics.module.computing.block.ServerRackBlock.BAYS;
                    final BlockPos second = new BlockPos(
                            dev.jsc.jscomputronics.module.computing.block.ServerRackStructure
                                    .bayBlockPos(rack, facing, 1, 0));
                    helper.assertTrue(helper.getBlockState(second).getValue(bays) == 0,
                            "pulling a Server empties its bay on the face");
                    helper.assertTrue(helper.getBlockState(rack).getValue(bays) == 3,
                            "the controller bay keeps its own server");
                })
                .thenSucceed();
    }

    // Supercomputer — Phi slots and parallel orchestration

    @GameTest(template = ARENA)
    public static void cluster_surveyAssignsSlotsAndBudget(final GameTestHelper helper) {
        final BlockPos hub = new BlockPos(2, 2, 2);
        // Three nodes in a row east of the interface: slots 1, 2, 3 by discovery order.
        placeCluster(helper, hub, 3);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    if (!(helper.getBlockEntity(hub)
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity
                                    .HbwInterfaceBlockEntity be)) {
                        throw new IllegalStateException("no hbw interface");
                    }
                    // 5100s fit slots 1-2 (8 + 16); the third 5100 is under-rated for slot 3.
                    helper.assertTrue(be.parallelCrafts() == 24,
                            "two rated slots give 24; got " + be.parallelCrafts());
                    final var slots = be.clusterSlots();
                    helper.assertTrue(slots.size() == 3, "three nodes surveyed");
                    helper.assertTrue(slots.get(2).code()
                                    == dev.jsc.jscomputronics.module.computing.blockentity
                                    .HbwInterfaceBlockEntity.SLOT_UNDER_RATED,
                            "a 5100 in slot 3 is flagged under-rated, never crashes");
                    // The console finds the interface through the node chain.
                    final BlockPos consolePos = new BlockPos(6, 2, 2);
                    helper.setBlock(consolePos, ComputingModule.SUPERCOMPUTER_CONSOLE.get());
                    if (helper.getBlockEntity(consolePos)
                            instanceof dev.jsc.jscomputronics.module.computing.blockentity
                                    .SupercomputerConsoleBlockEntity console) {
                        helper.assertTrue(console.findInterface() == be,
                                "the console walks the nodes to the interface");
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void supercomputer_unlocksParallelCrafting(final GameTestHelper helper) {
        final Network net = buildCraftingNetwork(helper);
        final var first = new java.util.concurrent.atomic.AtomicReference<
                dev.jsc.jscomputronics.module.computing.crafting.NetworkCraftOperation>();
        final var second = new java.util.concurrent.atomic.AtomicReference<
                dev.jsc.jscomputronics.module.computing.crafting.NetworkCraftOperation>();
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    net.seed(helper, Items.OAK_LOG, 8000);
                    net.cc.loadPattern(planksPattern(4));
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    // No cluster yet: two long crafts — the second must wait its turn.
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
                    // Raise a cluster on the backbone: interface against the HBW cable,
                    // one rated node behind it.
                    placeCluster(helper, new BlockPos(2, 2, 3), 1);
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    // Smaller than phase one: the first run consumed some logs before being
                    // abandoned, and BOTH locks must still be fully coverable at once.
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

    private static void placeCluster(final GameTestHelper helper, final BlockPos hub, final int nodes) {
        helper.setBlock(hub, ComputingModule.HBW_INTERFACE.get());
        for (int i = 1; i <= nodes; i++) {
            final BlockPos pos = hub.east(i);
            helper.setBlock(pos, ComputingModule.SUPERCOMPUTER_NODE.get());
            if (helper.getBlockEntity(pos)
                    instanceof dev.jsc.jscomputronics.module.computing.blockentity
                            .SupercomputerNodeBlockEntity node) {
                final var hw = node.getHardware();
                hw.setStackInSlot(dev.jsc.jscomputronics.module.computing.blockentity
                        .SupercomputerNodeBlockEntity.MOTHERBOARD_SLOT,
                        new ItemStack(ComputingModule.MOTHERBOARD_EEB_P.get()));
                hw.setStackInSlot(dev.jsc.jscomputronics.module.computing.blockentity
                        .SupercomputerNodeBlockEntity.CPU_SLOT,
                        new ItemStack(ComputingModule.CPU_SERVO_2620.get()));
                hw.setStackInSlot(dev.jsc.jscomputronics.module.computing.blockentity
                        .SupercomputerNodeBlockEntity.RAM_SLOTS_START,
                        new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
                hw.setStackInSlot(dev.jsc.jscomputronics.module.computing.blockentity
                        .SupercomputerNodeBlockEntity.PHI_SLOT,
                        new ItemStack(ComputingModule.PHI_5100.get()));
                hw.setStackInSlot(dev.jsc.jscomputronics.module.computing.blockentity
                        .SupercomputerNodeBlockEntity.PSU_SLOT,
                        new ItemStack(ComputingModule.PSU_650G.get()));
                node.togglePower();
            }
        }
    }

    // Network fixture: Mainframe + Server (storage) + Crafting Computer

    /**
     * The assembled test network, with handles on the parts the assertions need.
     */
    private record Network(
            dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity mainframe,
            dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity rack,
            CraftingComputerBlockEntity cc) {

        void seed(final GameTestHelper helper, final net.minecraft.world.item.Item item, final int count) {
            rack.getServerStorage(0).insert(item, count);
        }

        dev.jsc.jscomputronics.module.computing.operation.NetworkStorage storage(final GameTestHelper helper) {
            return dev.jsc.jscomputronics.module.computing.operation.NetworkStorage.of(
                    helper.getLevel(), mainframe.networkUuid());
        }
    }

    private static Network buildCraftingNetwork(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 1);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos eth = new BlockPos(4, 2, 2);
        final BlockPos cc = new BlockPos(5, 2, 2);

        helper.setBlock(m, ComputingModule.MAINFRAME.get());
        if (!(helper.getBlockEntity(m)
                instanceof dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity mainframe)) {
            throw new IllegalStateException("no mainframe");
        }
        final var inv = mainframe.getInventory();
        inv.setStackInSlot(dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity
                .MOTHERBOARD_SLOT, new ItemStack(ComputingModule.MOTHERBOARD_MTX_P.get()));
        inv.setStackInSlot(dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity
                .CPU_SLOTS_START, new ItemStack(ComputingModule.CPU_SERVO_2620.get()));
        inv.setStackInSlot(dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity
                .RAM_SLOTS_START, new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        inv.setStackInSlot(dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity
                .PSU_SLOT, new ItemStack(ComputingModule.PSU_650G.get()));
        mainframe.togglePower();

        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get());
        if (!(helper.getBlockEntity(rack)
                instanceof dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity rackBe)) {
            throw new IllegalStateException("no server rack");
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());

        helper.setBlock(router, ComputingModule.PERSONAL_ROUTER.get());
        helper.setBlock(eth, ComputingModule.ETHERNET_CABLE.get());

        helper.setBlock(cc, ComputingModule.CRAFTING_COMPUTER.get());
        if (!(helper.getBlockEntity(cc) instanceof CraftingComputerBlockEntity ccBe)) {
            throw new IllegalStateException("no crafting computer");
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

        return new Network(mainframe, rackBe, ccBe);
    }

    private static dev.jsc.jscomputronics.module.computing.storage.StorageKey storageKey(
            final net.minecraft.world.item.Item item) {
        return dev.jsc.jscomputronics.module.computing.storage.StorageKey.of(item);
    }

    // Pattern fixtures

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
}
