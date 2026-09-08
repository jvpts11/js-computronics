/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.block.part.InputBusPart;
import dev.jstech.computers.block.part.ReceivingBusPart;
import dev.jstech.computers.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computers.blockentity.DataCableBlockEntity;
import dev.jstech.computers.blockentity.HbwInterfaceBlockEntity;
import dev.jstech.computers.blockentity.MainframeBlockEntity;
import dev.jstech.computers.crafting.CraftingPattern;
import dev.jstech.computers.crafting.NetworkRecipe;
import dev.jstech.computers.crafting.ProcessingPattern;
import dev.jstech.computers.operation.INetworkOperation;
import dev.jstech.computers.operation.NetworkStorage;
import dev.jstech.computers.operation.payload.OperationRecord;
import dev.jstech.computers.storage.StorageKey;
import dev.jstech.tests.JsTests;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything at once: two Mekanism machines on the crafting run, two Crafting Computers, a Supercomputer
 * cluster and a Mainframe with a GPU serve three requests together: frames through the infuser, iron dust
 * through the crusher and a large bench craft fanned out across the computers. Both machines must work at the
 * same time, every request must complete, and the stock must balance to the unit.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class MekanismParallelLoadGameTests {

    private MekanismParallelLoadGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = MekanismRig.SETTLE;
    private static final ResourceLocation INFUSER = MekanismRig.mek("metallurgic_infuser");
    private static final ResourceLocation CRUSHER = MekanismRig.mek("crusher");
    // The second machine continues the crafting run two blocks further south, with its own buses.
    private static final BlockPos MACHINE_2 = new BlockPos(6, 2, 9);
    private static final BlockPos CABLE_2_WEST = new BlockPos(5, 2, 9);
    private static final BlockPos CABLE_2_ABOVE = new BlockPos(6, 3, 9);
    private static final BlockPos HUB = new BlockPos(2, 2, 3);
    private static final BlockPos SECOND_COMPUTER = new BlockPos(4, 2, 1);

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
            grid.set(corner, new ItemStack(MekanismRig.item(MekanismRig.mek("alloy_atomic"))));
        }
        for (final int edge : new int[]{1, 3, 5, 7}) {
            grid.set(edge, new ItemStack(MekanismRig.item(MekanismRig.mek("pellet_polonium"))));
        }
        grid.set(4, new ItemStack(MekanismRig.item(MekanismRig.mek("steel_casing"))));
        return new CraftingPattern(grid, new ItemStack(MekanismRig.item(MekanismRig.generators("fusion_reactor_frame")), 4));
    }

    private static CraftingPattern planksPattern() {
        final List<ItemStack> grid = new ArrayList<>(CraftingPattern.GRID_SIZE);
        for (int i = 0; i < CraftingPattern.GRID_SIZE; i++) {
            grid.add(ItemStack.EMPTY);
        }
        grid.set(0, new ItemStack(Items.OAK_LOG));
        return new CraftingPattern(grid, new ItemStack(Items.OAK_PLANKS, 4));
    }

    private static final BlockPos CABLE = HUB.east();
    private static final BlockPos NODE_RACK = CABLE.above(); // the row in front belongs to the rig

    /** Builds the hub, a cable, and one Supercomputer Rack seating a node with a Phi; leaves its bay OFF. */
    private static void placeClusterOffline(final TestWorldBuilder world) {
        world.setBlock(HUB, ComputingModule.HBW_INTERFACE.get());
        world.setBlock(CABLE, ComputingModule.HPC_CABLE.get());
        world.setBlock(NODE_RACK, ComputingModule.SUPERCOMPUTER_RACK.get());
        final var rack = world.blockEntity(NODE_RACK,
                dev.jstech.computers.blockentity.ServerRackBlockEntity.class);
        rack.getServers().setStackInSlot(0, ComputingModule.defaultSupercomputerNode());
        rack.toggleBayPower(0); // bays start on; the offline fixture wants the node dark
    }

    private static void powerCluster(final TestWorldBuilder world) {
        world.blockEntity(NODE_RACK,
                dev.jstech.computers.blockentity.ServerRackBlockEntity.class).toggleBayPower(0);
    }

    private static void placeCluster(final TestWorldBuilder world) {
        placeClusterOffline(world);
        powerCluster(world);
    }

    private static void loadAlloyChain(final GameTestHelper helper, final MekanismRig.Rig rig) {
        final CraftingComputerBlockEntity cc = rig.net().cc();
        helper.assertTrue(cc.loadPattern(framePattern()), "the frame pattern must load");
        helper.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(infuse(
                StorageKey.of(Items.COPPER_INGOT), StorageKey.of(Items.REDSTONE), 1, MekanismRig.itemKey(MekanismRig.mek("alloy_infused"))))), "infused loads");
        helper.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(infuse(
                MekanismRig.itemKey(MekanismRig.mek("alloy_infused")), MekanismRig.itemKey(MekanismRig.mek("dust_diamond")), 2, MekanismRig.itemKey(MekanismRig.mek("alloy_reinforced"))))), "reinforced loads");
        helper.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(infuse(
                MekanismRig.itemKey(MekanismRig.mek("alloy_reinforced")), MekanismRig.itemKey(MekanismRig.mek("dust_refined_obsidian")), 4, MekanismRig.itemKey(MekanismRig.mek("alloy_atomic"))))), "atomic loads");
        rig.net().seed(Items.COPPER_INGOT, 4);
        rig.net().seed(Items.REDSTONE, 4);
        rig.net().seed(MekanismRig.item(MekanismRig.mek("dust_diamond")), 8);
        rig.net().seed(MekanismRig.item(MekanismRig.mek("dust_refined_obsidian")), 16);
        rig.net().seed(MekanismRig.item(MekanismRig.mek("pellet_polonium")), 4);
        rig.net().seed(MekanismRig.item(MekanismRig.mek("steel_casing")), 1);
    }

    @GameTest(template = ARENA, timeoutTicks = 6000)
    public static void clusterComingOnlineMidCraft_freesItsSlotWhileWaitingOnAMachine(final GameTestHelper helper) {
        /*
         * Regression for the exclusiveClaim latch: a craft that starts with no cluster (exclusive claim) and then
         * sees the cluster come online mid-flight must switch to fan-out AND still release the cluster slot while
         * it waits on a later machine step, so a second request can use the cluster.
         */
        final MekanismRig.Rig rig = MekanismRig.build(helper, INFUSER);
        final TestWorldBuilder world = rig.world();
        final StorageKey frame = MekanismRig.itemKey(MekanismRig.generators("fusion_reactor_frame"));
        placeClusterOffline(world); // present but OFF at submit → the craft claims a single computer exclusively
        final INetworkOperation[] op = new INetworkOperation[1];
        /*
         * The longest run of consecutive ticks the craft held a cluster slot after the cluster came online. With
         * the stale-latch bug, a fanned-out craft never releases the slot, so it stays held for a whole machine
         * step (dozens of ticks); with the fix it is taken and freed within one tick per machine step.
         */
        final int[] maxHeld = {0};
        final int[] held = {0};
        final boolean[] clusterWasOnlineMidCraft = {false};
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    MekanismRig.mountBuses(helper);
                    MekanismRig.mountBottomInputBus(helper);
                    loadAlloyChain(helper, rig);
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(!world.blockEntity(HUB, HbwInterfaceBlockEntity.class).clusterOnline(),
                            "the cluster must be offline when the craft is submitted");
                    op[0] = rig.net().mainframe().submitNetworkCraft(frame, 4, false, "reactor", null);
                    helper.assertTrue(op[0] != null, "the frame craft is planned");
                })
                // The first machine step is running exclusively; now bring the cluster online with steps to go.
                .thenExecuteAfter(30, () -> {
                    MekanismRig.power(helper);
                    powerCluster(world);
                })
                .thenExecuteAfter(SETTLE, () -> {
                    clusterWasOnlineMidCraft[0] = world.blockEntity(HUB, HbwInterfaceBlockEntity.class).clusterOnline()
                            && !op[0].isDone();
                })
                .thenWaitUntil(() -> {
                    MekanismRig.power(helper);
                    final HbwInterfaceBlockEntity sc = world.blockEntity(HUB, HbwInterfaceBlockEntity.class);
                    if (sc.clusterOnline() && !op[0].isDone() && sc.craftSlotsInUse() > 0) {
                        held[0]++;
                        maxHeld[0] = Math.max(maxHeld[0], held[0]);
                    } else {
                        held[0] = 0;
                    }
                    helper.assertTrue(op[0].isDone(), "the craft is still running");
                })
                .thenExecute(() -> {
                    helper.assertTrue(op[0].toRecord().status() == OperationRecord.STATUS_COMPLETED,
                            "the craft must complete; status=" + op[0].toRecord().status());
                    helper.assertTrue(rig.net().storage(helper.getLevel()).count(frame) == 4, "four frames must be made");
                    helper.assertTrue(clusterWasOnlineMidCraft[0],
                            "the cluster must have come online while the craft still had machine steps to run");
                    /*
                     * The final bench step legitimately holds the slot for a few ticks; a stale exclusiveClaim
                     * latch would instead pin it across a whole ~200-tick machine step. 20 separates the two.
                     */
                    helper.assertTrue(maxHeld[0] <= 20,
                            "a fanned-out craft must not hold a cluster slot across a machine-step wait; held for " + maxHeld[0] + " ticks");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 9000)
    public static void everythingAtOnce_twoMachinesTwoComputersAndAClusterAllDeliver(final GameTestHelper helper) {
        final MekanismRig.Rig rig = MekanismRig.build(helper, INFUSER);
        final TestWorldBuilder world = rig.world();
        final StorageKey frame = MekanismRig.itemKey(MekanismRig.generators("fusion_reactor_frame"));
        final StorageKey dust = MekanismRig.itemKey(MekanismRig.mek("dust_iron"));
        final StorageKey infused = MekanismRig.itemKey(MekanismRig.mek("alloy_infused"));
        final INetworkOperation[] ops = new INetworkOperation[3];
        // The second machine and its buses, further down the run.
        world.setBlock(new BlockPos(5, 2, 8), ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(CABLE_2_WEST, ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(new BlockPos(5, 3, 9), ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(CABLE_2_ABOVE, ComputingModule.CRAFTING_CABLE.get());
        world.placeFromItem(MACHINE_2, net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(CRUSHER));
        // A second Crafting Computer on the data network, and the Supercomputer cluster.
        final CraftingComputerBlockEntity cc2 = world.placeRunningCraftingComputer(SECOND_COMPUTER);
        world.faceRearTowardCable(SECOND_COMPUTER);
        placeCluster(world);
        rig.net().mainframe().getInventory().setStackInSlot(MainframeBlockEntity.GPU_SLOTS_START,
                new ItemStack(ComputingModule.GPU_HD_7970.get()));
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    MekanismRig.mountBuses(helper);
                    MekanismRig.mountBottomInputBus(helper);
                    if (world.getBlockEntity(CABLE_2_ABOVE) instanceof DataCableBlockEntity cable) {
                        cable.addPart(Direction.DOWN, new InputBusPart());
                    }
                    if (world.getBlockEntity(CABLE_2_WEST) instanceof DataCableBlockEntity cable) {
                        cable.addPart(Direction.EAST, new ReceivingBusPart());
                    }
                    // Stock: two frame kits, sixteen iron ingots, six hundred logs.
                    rig.net().seed(Items.COPPER_INGOT, 8);
                    rig.net().seed(Items.REDSTONE, 8);
                    rig.net().seed(MekanismRig.item(MekanismRig.mek("dust_diamond")), 16);
                    rig.net().seed(MekanismRig.item(MekanismRig.mek("dust_refined_obsidian")), 32);
                    rig.net().seed(MekanismRig.item(MekanismRig.mek("pellet_polonium")), 8);
                    rig.net().seed(MekanismRig.item(MekanismRig.mek("steel_casing")), 2);
                    rig.net().seed(Items.IRON_INGOT, 16);
                    rig.net().seed(Items.OAK_LOG, 600);
                    for (final CraftingComputerBlockEntity cc : new CraftingComputerBlockEntity[]{rig.net().cc(), cc2}) {
                        helper.assertTrue(cc.loadPattern(framePattern()) && cc.loadPattern(planksPattern()), "bench patterns load");
                        helper.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(infuse(StorageKey.of(Items.COPPER_INGOT),
                                StorageKey.of(Items.REDSTONE), 1, infused))), "infused loads");
                        helper.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(infuse(infused,
                                MekanismRig.itemKey(MekanismRig.mek("dust_diamond")), 2, MekanismRig.itemKey(MekanismRig.mek("alloy_reinforced"))))), "reinforced loads");
                        helper.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(infuse(MekanismRig.itemKey(MekanismRig.mek("alloy_reinforced")),
                                MekanismRig.itemKey(MekanismRig.mek("dust_refined_obsidian")), 4, MekanismRig.itemKey(MekanismRig.mek("alloy_atomic"))))), "atomic loads");
                        helper.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(new ProcessingPattern(
                                List.of(new ProcessingPattern.ProcessingInput(StorageKey.of(Items.IRON_INGOT), 1)),
                                List.of(new ProcessingPattern.ProcessingOutput(dust, 1, 100)), CRUSHER.toString(), 400))), "crushing loads");
                    }
                })
                .thenExecuteAfter(SETTLE + 4, () -> {
                    final MainframeBlockEntity mainframe = rig.net().mainframe();
                    helper.assertTrue(mainframe.parallelQueues() == 2, "the GPU must give two queues");
                    helper.assertTrue(mainframe.craftingComputerPositions().size() >= 2, "both computers must be on the network");
                    final HbwInterfaceBlockEntity sc = world.blockEntity(HUB, HbwInterfaceBlockEntity.class);
                    helper.assertTrue(sc.clusterOnline() && sc.parallelCrafts() >= 2, "the cluster must be online with room for two crafts");
                    ops[0] = mainframe.submitNetworkCraft(frame, 8, false, "frames", null);
                    ops[1] = mainframe.submitNetworkCraft(dust, 16, false, "dust", null);
                    ops[2] = mainframe.submitNetworkCraft(StorageKey.of(Items.OAK_PLANKS), 2400, false, "planks", null);
                    helper.assertTrue(ops[0] != null && ops[1] != null && ops[2] != null, "all three requests must be planned");
                })
                .thenExecuteAfter(60, () -> {
                    MekanismRig.power(helper);
                    MekanismRig.power(helper.getLevel(), helper.absolutePos(MACHINE_2));
                    final List<OperationRecord> records = rig.net().mainframe().activeOperationRecords();
                    // Machine steps are the records without sub-rows (the crafts that own them carry the rows).
                    final long running = records.stream()
                            .filter(r -> r.subs().isEmpty() && (infused.equals(r.key()) || dust.equals(r.key()))
                                    && r.status() == OperationRecord.STATUS_PROCESSING)
                            .count();
                    helper.assertTrue(running == 2, "both machines must be working at once on their own queues; active=" + records);
                    /*
                     * Crafts waiting on their machines hold no cluster slot: the planks are done, the other
                     * two are parked on the infuser and the crusher, so the cluster is free for the next request.
                     */
                    helper.assertTrue(world.blockEntity(HUB, HbwInterfaceBlockEntity.class).craftSlotsInUse() == 0,
                            "a craft waiting on a machine step must not hold cluster slots; in use="
                                    + world.blockEntity(HUB, HbwInterfaceBlockEntity.class).craftSlotsInUse());
                })
                .thenWaitUntil(() -> {
                    MekanismRig.power(helper);
                    MekanismRig.power(helper.getLevel(), helper.absolutePos(MACHINE_2));
                    helper.assertTrue(ops[0].isDone() && ops[1].isDone() && ops[2].isDone(),
                            "still running: " + rig.net().mainframe().activeOperationRecords());
                })
                .thenExecute(() -> {
                    for (final INetworkOperation op : ops) {
                        helper.assertTrue(op.toRecord().status() == OperationRecord.STATUS_COMPLETED,
                                "every request must complete; " + op.toRecord());
                    }
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    helper.assertTrue(storage.count(frame) == 8, "eight frames; got " + storage.count(frame));
                    helper.assertTrue(storage.count(dust) == 16, "sixteen iron dust; got " + storage.count(dust));
                    helper.assertTrue(storage.count(Items.OAK_PLANKS) == 2400, "2 400 planks; got " + storage.count(Items.OAK_PLANKS));
                    helper.assertTrue(storage.count(Items.COPPER_INGOT) == 0 && storage.count(Items.IRON_INGOT) == 0
                                    && storage.count(Items.OAK_LOG) == 0 && storage.count(infused) == 0,
                            "the raw stock must be spent to the unit; stock=" + storage.query());
                })
                .thenSucceed();
    }
}
