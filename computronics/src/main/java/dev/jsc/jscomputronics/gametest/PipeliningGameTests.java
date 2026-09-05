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
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.crafting.NetworkRecipe;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern;
import dev.jsc.jscomputronics.module.computing.operation.NetworkOperation;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * The craft engine is concurrent by design: a chain of machine steps pipelines instead of running one stage
 * fully before the next. This mirrors the setup a player builds to speed up a chain — two Metallurgic Infusers
 * told apart by their Input Bus filters (one carries copper, the other the infused alloy the first makes) on a
 * Mainframe with a GPU (two queues). Requesting the end of the chain must run BOTH infusers at the same time:
 * the second stage starts as soon as the first has made one, not after the first has made them all.
 */
@GameTestHolder(JsComputronics.MODID)
@PrefixGameTestTemplate(false)
public final class PipeliningGameTests {

    private PipeliningGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = MekanismRig.SETTLE;
    private static final ResourceLocation INFUSER = MekanismRig.mek("metallurgic_infuser");
    private static final ResourceLocation ALLOY_INFUSED = MekanismRig.mek("alloy_infused");
    private static final ResourceLocation ALLOY_REINFORCED = MekanismRig.mek("alloy_reinforced");
    private static final ResourceLocation DUST_DIAMOND = MekanismRig.mek("dust_diamond");

    // A second infuser continues the chain two blocks south of the first, with its own buses.
    private static final BlockPos INFUSER_2 = new BlockPos(6, 2, 10);
    private static final BlockPos CABLE_2_RUN = new BlockPos(5, 2, 10);
    private static final BlockPos CABLE_2_LINK = new BlockPos(5, 2, 8);
    private static final BlockPos CABLE_2_ABOVE = new BlockPos(6, 3, 10);
    private static final BlockPos CABLE_2_ABOVE_LINK = new BlockPos(5, 3, 10);
    private static final BlockPos CABLE_2_BELOW = new BlockPos(6, 1, 10);
    private static final BlockPos CABLE_2_BELOW_LINK = new BlockPos(5, 1, 10);

    /** Copper + 10 mB of redstone -> Infused Alloy. */
    private static ProcessingPattern infusedAlloy() {
        return new ProcessingPattern(
                List.of(new ProcessingPattern.ProcessingInput(StorageKey.of(Items.COPPER_INGOT), 1),
                        new ProcessingPattern.ProcessingInput(StorageKey.of(Items.REDSTONE), 1)),
                List.of(new ProcessingPattern.ProcessingOutput(MekanismRig.itemKey(ALLOY_INFUSED), 1, 100)),
                INFUSER.toString(), 400);
    }

    /** Infused Alloy + 20 mB of diamond -> Reinforced Alloy. */
    private static ProcessingPattern reinforcedAlloy() {
        return new ProcessingPattern(
                List.of(new ProcessingPattern.ProcessingInput(MekanismRig.itemKey(ALLOY_INFUSED), 1),
                        new ProcessingPattern.ProcessingInput(MekanismRig.itemKey(DUST_DIAMOND), 2)),
                List.of(new ProcessingPattern.ProcessingOutput(MekanismRig.itemKey(ALLOY_REINFORCED), 1, 100)),
                INFUSER.toString(), 400);
    }

    /**
     * The first infuser's buses: a top Input Bus filtered to copper (its infusion redstone rides the unfiltered
     * bottom bus, mounted separately), and a Receiving Bus on its right (west) face for the infused alloy.
     */
    private static void wireFirstInfuser(final TestWorldBuilder world) {
        if (world.getBlockEntity(MekanismRig.CABLE_ABOVE) instanceof DataCableBlockEntity cable) {
            final InputBusPart top = new InputBusPart();
            cable.addPart(Direction.DOWN, top);
            top.setFilter(new ItemStack(Items.COPPER_INGOT));
        }
        if (world.getBlockEntity(MekanismRig.CABLE_WEST) instanceof DataCableBlockEntity cable) {
            cable.addPart(Direction.EAST, new ReceivingBusPart());
        }
    }

    /** The second infuser: top bus carries the infused alloy, bottom bus the diamond, right face gives it back. */
    private static void placeSecondInfuser(final TestWorldBuilder world) {
        world.setBlock(CABLE_2_LINK, ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(new BlockPos(5, 2, 9), ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(CABLE_2_RUN, ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(CABLE_2_ABOVE_LINK, ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(CABLE_2_ABOVE, ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(CABLE_2_BELOW_LINK, ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(CABLE_2_BELOW, ComputingModule.CRAFTING_CABLE.get());
        world.placeFromItem(INFUSER_2, BuiltInRegistries.BLOCK.get(INFUSER));
        if (world.getBlockEntity(CABLE_2_ABOVE) instanceof DataCableBlockEntity cable) {
            final InputBusPart top = new InputBusPart();
            cable.addPart(Direction.DOWN, top);
            top.setFilter(new ItemStack(MekanismRig.item(ALLOY_INFUSED)));
        }
        if (world.getBlockEntity(CABLE_2_BELOW) instanceof DataCableBlockEntity cable) {
            final InputBusPart bottom = new InputBusPart();
            cable.addPart(Direction.UP, bottom);
            bottom.setFilter(new ItemStack(MekanismRig.item(DUST_DIAMOND)));
        }
        if (world.getBlockEntity(CABLE_2_RUN) instanceof DataCableBlockEntity cable) {
            cable.addPart(Direction.EAST, new ReceivingBusPart());
        }
    }

    private static void powerBoth(final GameTestHelper helper) {
        MekanismRig.power(helper);
        MekanismRig.power(helper.getLevel(), helper.absolutePos(INFUSER_2));
    }

    private static long infuserStepsRunning(final MainframeBlockEntity mainframe) {
        final StorageKey infused = MekanismRig.itemKey(ALLOY_INFUSED);
        final StorageKey reinforced = MekanismRig.itemKey(ALLOY_REINFORCED);
        return mainframe.activeOperationRecords().stream()
                .filter(r -> r.subs().isEmpty() && (infused.equals(r.key()) || reinforced.equals(r.key()))
                        && r.status() == OperationRecord.STATUS_PROCESSING)
                .count();
    }

    @GameTest(template = ARENA, timeoutTicks = 2000)
    public static void twoStageChainRunsBothInfusersAtOnce(final GameTestHelper helper) {
        final MekanismRig.Rig rig = MekanismRig.build(helper, INFUSER);
        final TestWorldBuilder world = rig.world();
        placeSecondInfuser(world);
        rig.net().mainframe().getInventory().setStackInSlot(MainframeBlockEntity.GPU_SLOTS_START,
                new ItemStack(ComputingModule.GPU_HD_7970.get()));
        final StorageKey reinforced = MekanismRig.itemKey(ALLOY_REINFORCED);
        final StorageKey infused = MekanismRig.itemKey(ALLOY_INFUSED);
        final NetworkOperation[] op = new NetworkOperation[1];
        final boolean[] sawBothRunning = {false};
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    MekanismRig.mountBottomInputBus(world);   // the first infuser's redstone rides here
                    wireFirstInfuser(world);                  // copper-filtered top bus + west receiving bus
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    // Enough copper, redstone and diamond for four reinforced alloys, sized to the unit.
                    rig.net().seed(Items.COPPER_INGOT, 4);
                    rig.net().seed(Items.REDSTONE, 4);
                    rig.net().seed(MekanismRig.item(DUST_DIAMOND), 8);
                    final CraftingComputerBlockEntity cc = rig.net().cc();
                    helper.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(infusedAlloy())), "infused loads");
                    helper.assertTrue(cc.loadMachineRecipe(NetworkRecipe.ofProcessing(reinforcedAlloy())), "reinforced loads");
                    // No Max Jobs is set: with two physical infusers the engine runs two infuser jobs on its own
                    // (Auto = use every machine of the type). The filters route each stage to its own machine.
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(rig.net().mainframe().parallelQueues() == 2, "the GPU must give two queues");
                    op[0] = rig.net().mainframe().submitNetworkCraft(reinforced, 4, false, "chain", null);
                    helper.assertTrue(op[0] != null, "the reinforced-alloy chain must be planned");
                })
                .thenWaitUntil(() -> {
                    powerBoth(helper);
                    if (infuserStepsRunning(rig.net().mainframe()) >= 2) {
                        sawBothRunning[0] = true;
                    }
                    helper.assertTrue(op[0].isDone(), "the chain is still running: " + rig.net().mainframe().activeOperationRecords());
                })
                .thenExecute(() -> {
                    helper.assertTrue(op[0].toRecord().status() == OperationRecord.STATUS_COMPLETED,
                            "the chain must complete; status=" + op[0].toRecord().status());
                    helper.assertTrue(sawBothRunning[0],
                            "the two infuser stages must have run at the same time: the second must start before the first "
                                    + "has made every infused alloy (a pipelined chain, not one stage after another)");
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    helper.assertTrue(storage.count(reinforced) == 4, "four reinforced alloys; got " + storage.count(reinforced));
                    helper.assertTrue(storage.count(infused) == 0 && storage.count(Items.COPPER_INGOT) == 0
                                    && storage.count(Items.REDSTONE) == 0 && storage.count(MekanismRig.itemKey(DUST_DIAMOND)) == 0,
                            "every input must be spent to the unit; stock=" + storage.query());
                })
                .thenSucceed();
    }
}
