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
import dev.jsc.jscomputronics.module.computing.block.part.InputBusPart;
import dev.jsc.jscomputronics.module.computing.block.part.ReceivingBusPart;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.DataCableBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.HbwInterfaceBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.SupercomputerNodeBlockEntity;
import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import dev.jsc.jscomputronics.module.computing.crafting.NetworkRecipe;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern;
import dev.jsc.jscomputronics.module.computing.operation.NetworkOperation;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.testkit.TestWorldBuilder;
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
 * cluster and a Mainframe with a GPU serve three requests together — frames through the infuser, iron dust
 * through the crusher and a large bench craft fanned out across the computers. Both machines must work at the
 * same time, every request must complete, and the stock must balance to the unit.
 */
@GameTestHolder(JsComputronics.MODID)
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

    private static void placeCluster(final TestWorldBuilder world) {
        world.setBlock(HUB, ComputingModule.HBW_INTERFACE.get());
        final BlockPos nodePos = HUB.east();
        world.setBlock(nodePos, ComputingModule.SUPERCOMPUTER_NODE.get());
        final SupercomputerNodeBlockEntity node = world.blockEntity(nodePos, SupercomputerNodeBlockEntity.class);
        final var hw = node.getHardware();
        hw.setStackInSlot(SupercomputerNodeBlockEntity.MOTHERBOARD_SLOT, new ItemStack(ComputingModule.MOTHERBOARD_EEB_P.get()));
        hw.setStackInSlot(SupercomputerNodeBlockEntity.CPU_SLOT, new ItemStack(ComputingModule.CPU_SERVO_2620.get()));
        hw.setStackInSlot(SupercomputerNodeBlockEntity.RAM_SLOTS_START, new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        hw.setStackInSlot(SupercomputerNodeBlockEntity.PHI_SLOT, new ItemStack(ComputingModule.PHI_5100.get()));
        hw.setStackInSlot(SupercomputerNodeBlockEntity.PSU_SLOT, new ItemStack(ComputingModule.PSU_650G.get()));
        node.togglePower();
    }

    @GameTest(template = ARENA, timeoutTicks = 9000)
    public static void everythingAtOnce_twoMachinesTwoComputersAndAClusterAllDeliver(final GameTestHelper helper) {
        final MekanismRig.Rig rig = MekanismRig.build(helper, INFUSER);
        final TestWorldBuilder world = rig.world();
        final StorageKey frame = MekanismRig.itemKey(MekanismRig.generators("fusion_reactor_frame"));
        final StorageKey dust = MekanismRig.itemKey(MekanismRig.mek("dust_iron"));
        final StorageKey infused = MekanismRig.itemKey(MekanismRig.mek("alloy_infused"));
        final NetworkOperation[] ops = new NetworkOperation[3];
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
                })
                .thenWaitUntil(() -> {
                    MekanismRig.power(helper);
                    MekanismRig.power(helper.getLevel(), helper.absolutePos(MACHINE_2));
                    helper.assertTrue(ops[0].isDone() && ops[1].isDone() && ops[2].isDone(),
                            "still running: " + rig.net().mainframe().activeOperationRecords());
                })
                .thenExecute(() -> {
                    for (final NetworkOperation op : ops) {
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
