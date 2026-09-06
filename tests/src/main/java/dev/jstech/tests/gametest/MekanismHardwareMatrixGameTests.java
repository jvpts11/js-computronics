/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.crafting.CraftingPattern;
import dev.jstech.computronics.crafting.NetworkRecipe;
import dev.jstech.computronics.crafting.ProcessingPattern;
import dev.jstech.computronics.operation.NetworkOperation;
import dev.jstech.computronics.operation.NetworkStorage;
import dev.jstech.computronics.operation.payload.OperationRecord;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.tests.JsTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * The frame craft under different hardware and under things going wrong: a Mainframe with one queue against
 * one with a GPU, the Crafting Computer switched off mid-craft, the machine broken mid-step, and a Mainframe
 * that is not running. Failures must be visible in the operation log and never lose items.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class MekanismHardwareMatrixGameTests {

    private MekanismHardwareMatrixGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = MekanismRig.SETTLE;
    private static final ResourceLocation INFUSER = MekanismRig.mek("metallurgic_infuser");
    private static final ResourceLocation ALLOY_INFUSED = MekanismRig.mek("alloy_infused");
    private static final ResourceLocation ALLOY_REINFORCED = MekanismRig.mek("alloy_reinforced");
    private static final ResourceLocation ALLOY_ATOMIC = MekanismRig.mek("alloy_atomic");
    private static final ResourceLocation DUST_DIAMOND = MekanismRig.mek("dust_diamond");
    private static final ResourceLocation DUST_REFINED_OBSIDIAN = MekanismRig.mek("dust_refined_obsidian");
    private static final ResourceLocation PELLET_POLONIUM = MekanismRig.mek("pellet_polonium");
    private static final ResourceLocation STEEL_CASING = MekanismRig.mek("steel_casing");
    private static final ResourceLocation FRAME = MekanismRig.generators("fusion_reactor_frame");

    private static ProcessingPattern infuse(final StorageKey in, final StorageKey extra, final long extraCount, final StorageKey out) {
        return new ProcessingPattern(
                List.of(new ProcessingPattern.ProcessingInput(in, 1), new ProcessingPattern.ProcessingInput(extra, extraCount)),
                List.of(new ProcessingPattern.ProcessingOutput(out, 1, 100)),
                INFUSER.toString(), 400);
    }

    private static CraftingPattern framePattern() {
        final List<ItemStack> grid = new ArrayList<>(CraftingPattern.GRID_SIZE);
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            grid.add(ItemStack.EMPTY);
        }
        for (final int corner : new int[]{0, 2, 6, 8}) {
            grid.set(corner, new ItemStack(MekanismRig.item(ALLOY_ATOMIC)));
        }
        for (final int edge : new int[]{1, 3, 5, 7}) {
            grid.set(edge, new ItemStack(MekanismRig.item(PELLET_POLONIUM)));
        }
        grid.set(4, new ItemStack(MekanismRig.item(STEEL_CASING)));
        return new CraftingPattern(grid, new ItemStack(MekanismRig.item(FRAME), 4));
    }

    /** Buses, the flat patterns in the ROM and raw stock for {@code kits} runs of four frames. */
    private static void prepare(final GameTestHelper helper, final MekanismRig.Rig rig, final int kits) {
        MekanismRig.mountBuses(helper);
        MekanismRig.mountBottomInputBus(helper);
        rig.net().seed(Items.COPPER_INGOT, 4 * kits);
        rig.net().seed(Items.REDSTONE, 4 * kits);
        rig.net().seed(MekanismRig.item(DUST_DIAMOND), 8 * kits);
        rig.net().seed(MekanismRig.item(DUST_REFINED_OBSIDIAN), 16 * kits);
        rig.net().seed(MekanismRig.item(PELLET_POLONIUM), 4 * kits);
        rig.net().seed(MekanismRig.item(STEEL_CASING), kits);
        final CraftingComputerBlockEntity cc = rig.net().cc();
        helper.assertTrue(cc.loadPattern(framePattern()), "the frame pattern must load into the ROM");
        helper.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(infuse(
                StorageKey.of(Items.COPPER_INGOT), StorageKey.of(Items.REDSTONE), 1, MekanismRig.itemKey(ALLOY_INFUSED)))), "infused loads");
        helper.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(infuse(
                MekanismRig.itemKey(ALLOY_INFUSED), MekanismRig.itemKey(DUST_DIAMOND), 2, MekanismRig.itemKey(ALLOY_REINFORCED)))), "reinforced loads");
        helper.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(infuse(
                MekanismRig.itemKey(ALLOY_REINFORCED), MekanismRig.itemKey(DUST_REFINED_OBSIDIAN), 4, MekanismRig.itemKey(ALLOY_ATOMIC)))), "atomic loads");
    }

    private static long count(final List<OperationRecord> records, final StorageKey key, final byte status) {
        return records.stream().filter(r -> key.equals(r.key()) && r.status() == status).count();
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void oneQueue_stillRunsBothMachineCraftsAndTheirStages(final GameTestHelper helper) {
        // One CPU, no GPU: one Mainframe queue. Crafting is a subnet independent of the Mainframe's operation
        // queues, so the single queue does NOT gate crafts or their machine stages. Two machine crafts on one
        // Crafting Computer both run (each parks on its machine while the other takes the computer for bench work),
        // and each launches its own machine step, gated by the Crafting Computer's threads (T2 card = 2) and the
        // machines present, not by the queue. With two infusers, both machine steps run at once even on one queue.
        // (The Mainframe queue count is for distinct operations and throughput; a Supercomputer is what scales
        // distinct crafts across computers — see supercomputer_unlocksParallelCrafting.)
        final MekanismRig.Rig rig = MekanismRig.build(helper, INFUSER);
        final StorageKey frame = MekanismRig.itemKey(FRAME);
        final StorageKey infused = MekanismRig.itemKey(ALLOY_INFUSED);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    MekanismRig.placeSecondMachine(rig.world(), INFUSER);
                    MekanismRig.mountSecondMachineBuses(rig.world(), null, null); // same recipe on both: unfiltered
                    prepare(helper, rig, 2);
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(rig.net().mainframe().parallelQueues() == 1, "the test Mainframe must have one queue");
                    helper.assertTrue(rig.net().mainframe().submitNetworkCraft(frame, 4, false, "one", null) != null, "craft one is planned");
                    helper.assertTrue(rig.net().mainframe().submitNetworkCraft(frame, 4, false, "two", null) != null, "craft two is planned");
                })
                .thenExecuteAfter(60, () -> {
                    MekanismRig.power(helper);
                    MekanismRig.power(helper.getLevel(), helper.absolutePos(MekanismRig.MACHINE_B));
                    final List<OperationRecord> records = rig.net().mainframe().activeOperationRecords();
                    helper.assertTrue(count(records, frame, OperationRecord.STATUS_PROCESSING) == 2,
                            "both crafts run on one queue: crafting is a subnet, not gated by the Mainframe queue; active=" + records);
                    helper.assertTrue(count(records, infused, OperationRecord.STATUS_PROCESSING) == 2,
                            "both machine steps run at once on the two infusers, gated by the computer's threads not the queue; active=" + records);
                    helper.assertTrue(count(records, infused, OperationRecord.STATUS_PENDING) == 0,
                            "no machine step is held PENDING by the single queue; active=" + records);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void gpu_doesNotChangeMachineCraftConcurrency(final GameTestHelper helper) {
        // The same two crafts and two infusers, now on a Mainframe with a GPU (two queues). Because crafting is a
        // subnet independent of the Mainframe queues, the extra queue changes nothing for the crafts: both still
        // run and both machine steps run at once, exactly as on one queue (see oneQueue_stillRunsBothMachineCrafts
        // AndTheirStages). The GPU adds a queue for distinct operations and throughput, not for crafts or stages.
        final MekanismRig.Rig rig = MekanismRig.build(helper, INFUSER);
        final StorageKey frame = MekanismRig.itemKey(FRAME);
        final StorageKey infused = MekanismRig.itemKey(ALLOY_INFUSED);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    MekanismRig.placeSecondMachine(rig.world(), INFUSER);
                    MekanismRig.mountSecondMachineBuses(rig.world(), null, null); // same recipe on both: unfiltered
                    prepare(helper, rig, 2);
                    rig.net().mainframe().getInventory().setStackInSlot(MainframeBlockEntity.GPU_SLOTS_START,
                            new ItemStack(ComputingModule.GPU_HD_7970.get()));
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(rig.net().mainframe().parallelQueues() == 2,
                            "a GPU must add a queue; queues=" + rig.net().mainframe().parallelQueues());
                    helper.assertTrue(rig.net().mainframe().submitNetworkCraft(frame, 4, false, "one", null) != null, "craft one is planned");
                    helper.assertTrue(rig.net().mainframe().submitNetworkCraft(frame, 4, false, "two", null) != null, "craft two is planned");
                })
                .thenExecuteAfter(60, () -> {
                    MekanismRig.power(helper);
                    MekanismRig.power(helper.getLevel(), helper.absolutePos(MekanismRig.MACHINE_B));
                    final List<OperationRecord> records = rig.net().mainframe().activeOperationRecords();
                    helper.assertTrue(count(records, infused, OperationRecord.STATUS_PROCESSING) == 2,
                            "both machine steps must run, one per queue on its own machine; active=" + records);
                    helper.assertTrue(count(records, frame, OperationRecord.STATUS_PENDING) == 0,
                            "no craft may be left PENDING with two queues; active=" + records);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 1500)
    public static void gpuPulledMidCraft_craftSurvivesAndCompletes(final GameTestHelper helper) {
        // Hot-swapping the GPU changes the Mainframe's queue count. Resizing the dispatcher must happen IN PLACE:
        // rebuilding it would abandon every in-flight Operation, so pulling a GPU mid-craft would discard the craft
        // and strand its machines (the receiving buses stop pulling). The craft must survive the pull and finish.
        final MekanismRig.Rig rig = MekanismRig.build(helper, INFUSER);
        final StorageKey infused = MekanismRig.itemKey(ALLOY_INFUSED);
        final NetworkOperation[] op = new NetworkOperation[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    prepare(helper, rig, 1);
                    rig.net().mainframe().getInventory().setStackInSlot(MainframeBlockEntity.GPU_SLOTS_START,
                            new ItemStack(ComputingModule.GPU_HD_7970.get()));
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(rig.net().mainframe().parallelQueues() == 2, "the GPU must give two queues");
                    op[0] = rig.net().mainframe().submitNetworkCraft(infused, 4, false, "battery", null);
                    helper.assertTrue(op[0] != null, "the infused-alloy craft is planned");
                })
                .thenExecuteAfter(30, () -> {
                    MekanismRig.power(helper);
                    helper.assertTrue(rig.net().storage(helper.getLevel()).count(Items.COPPER_INGOT) == 0,
                            "the machine step must have taken the copper before the GPU is pulled");
                    // Pull the GPU mid-craft: the queue count drops 2 -> 1. This must not discard the craft.
                    rig.net().mainframe().getInventory().setStackInSlot(MainframeBlockEntity.GPU_SLOTS_START, ItemStack.EMPTY);
                    helper.assertTrue(rig.net().mainframe().parallelQueues() == 1, "pulling the GPU must drop to one queue");
                })
                .thenExecuteAfter(4, () -> helper.assertTrue(!op[0].isDone(),
                        "the craft must survive the GPU hot-swap, not be discarded: "
                                + rig.net().mainframe().activeOperationRecords()))
                .thenWaitUntil(() -> {
                    MekanismRig.power(helper);
                    helper.assertTrue(op[0].isDone(), "the craft must still finish after the GPU was pulled: "
                            + rig.net().mainframe().activeOperationRecords());
                })
                .thenExecute(() -> {
                    helper.assertTrue(op[0].toRecord().status() == OperationRecord.STATUS_COMPLETED,
                            "the craft must complete, not settle as discarded; status=" + op[0].toRecord().status());
                    helper.assertTrue(rig.net().storage(helper.getLevel()).count(infused) == 4,
                            "the four infused alloys must be delivered; got " + rig.net().storage(helper.getLevel()).count(infused));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 1500)
    public static void craftingComputerOffMidCraft_settlesVisiblyAndLosesNothing(final GameTestHelper helper) {
        final MekanismRig.Rig rig = MekanismRig.build(helper, INFUSER);
        final StorageKey frame = MekanismRig.itemKey(FRAME);
        final StorageKey infused = MekanismRig.itemKey(ALLOY_INFUSED);
        final NetworkOperation[] op = new NetworkOperation[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> prepare(helper, rig, 1))
                .thenExecuteAfter(SETTLE + 2, () -> {
                    op[0] = rig.net().mainframe().submitNetworkCraft(frame, 4, false, "battery", null);
                    helper.assertTrue(op[0] != null, "the craft is planned");
                })
                .thenExecuteAfter(30, () -> {
                    MekanismRig.power(helper);
                    helper.assertTrue(rig.net().storage(helper.getLevel()).count(Items.COPPER_INGOT) == 0,
                            "the machine step must have taken the copper before the computer goes off");
                    rig.net().cc().togglePower();
                    helper.assertTrue(!rig.net().cc().isRunning(), "the Crafting Computer must be off");
                })
                .thenWaitUntil(() -> {
                    MekanismRig.power(helper);
                    helper.assertTrue(op[0].isDone(), "the craft must settle once its computer is gone");
                })
                .thenExecute(() -> helper.assertTrue(op[0].toRecord().status() != OperationRecord.STATUS_COMPLETED,
                        "the craft must settle as a visible failure, not COMPLETED; status=" + op[0].toRecord().status()))
                // The machine step runs on regardless: its alloys reach storage and nothing is lost.
                .thenWaitUntil(() -> {
                    MekanismRig.power(helper);
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    helper.assertTrue(storage.count(infused) == 4, "the four infused alloys must still be delivered; got "
                            + storage.count(infused) + " active=" + rig.net().mainframe().activeOperationRecords());
                })
                .thenExecute(() -> {
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    helper.assertTrue(storage.count(frame) == 0, "no frames could be made");
                    helper.assertTrue(storage.count(MekanismRig.itemKey(DUST_DIAMOND)) == 8
                                    && storage.count(MekanismRig.itemKey(DUST_REFINED_OBSIDIAN)) == 16
                                    && storage.count(MekanismRig.itemKey(PELLET_POLONIUM)) == 4
                                    && storage.count(MekanismRig.itemKey(STEEL_CASING)) == 1,
                            "every other raw material must be untouched; stock=" + storage.query());
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 2000)
    public static void machineBrokenMidStep_craftFailsVisiblyAndTheMachineKeepsItsContents(final GameTestHelper helper) {
        final MekanismRig.Rig rig = MekanismRig.build(helper, INFUSER);
        final StorageKey frame = MekanismRig.itemKey(FRAME);
        final NetworkOperation[] op = new NetworkOperation[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> prepare(helper, rig, 1))
                .thenExecuteAfter(SETTLE + 2, () -> {
                    op[0] = rig.net().mainframe().submitNetworkCraft(frame, 4, false, "battery", null);
                    helper.assertTrue(op[0] != null, "the craft is planned");
                })
                .thenExecuteAfter(30, () -> {
                    MekanismRig.power(helper);
                    helper.assertTrue(rig.net().storage(helper.getLevel()).count(Items.COPPER_INGOT) == 0,
                            "the machine step must have taken the copper before the machine breaks");
                    helper.getLevel().destroyBlock(helper.absolutePos(MekanismRig.MACHINE), true);
                })
                .thenWaitUntil(() -> helper.assertTrue(op[0].isDone(), "the craft must settle once its machine is gone: "
                        + rig.net().mainframe().activeOperationRecords()))
                .thenExecute(() -> {
                    helper.assertTrue(op[0].toRecord().status() != OperationRecord.STATUS_COMPLETED,
                            "the craft must settle as a visible failure; status=" + op[0].toRecord().status());
                    // The machine's contents left with the machine (as drops around it), not into the void.
                    final List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                            AABB.encapsulatingFullBlocks(helper.absolutePos(MekanismRig.MACHINE.offset(-2, -1, -2)),
                                    helper.absolutePos(MekanismRig.MACHINE.offset(2, 2, 2))));
                    helper.assertTrue(!drops.isEmpty(), "breaking the machine must drop something where it stood");
                    helper.assertTrue(rig.net().storage(helper.getLevel()).count(MekanismRig.itemKey(PELLET_POLONIUM)) == 4,
                            "the bench ingredients must still be in the network");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void mainframeOff_refusesTheRequest(final GameTestHelper helper) {
        final MekanismRig.Rig rig = MekanismRig.build(helper, INFUSER);
        final StorageKey frame = MekanismRig.itemKey(FRAME);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> prepare(helper, rig, 1))
                .thenExecuteAfter(SETTLE + 2, () -> {
                    rig.net().mainframe().togglePower();
                    helper.assertTrue(!rig.net().mainframe().isRunning(), "the Mainframe must be off");
                })
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(rig.net().mainframe().submitNetworkCraft(frame, 4, false, "battery", null) == null,
                            "a Mainframe that is not running must refuse the request");
                    final var cli = new dev.jstech.computronics.program.ServerCliComputer(
                            (dev.jstech.computronics.terminal.ComputerTerminalHost) rig.net().cc(), helper.getLevel());
                    final var response = dev.jstech.computronics.program.cli.CliCommands.newShell(50)
                            .run("operation craft 4 " + FRAME, cli);
                    final String text = String.join(" | ", response.lines().stream().map(l -> l.text()).toList());
                    helper.assertTrue(!text.contains("CRAFT queued"), "the shell must not queue a craft without a running Mainframe; got " + text);
                    helper.assertTrue(text.toLowerCase().contains("mainframe") || text.toLowerCase().contains("no pattern"),
                            "the shell must say why; got " + text);
                })
                .thenSucceed();
    }
}
