/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.gametest;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.crafting.CraftingPattern;
import dev.jsc.jscomputronics.module.computing.crafting.MultiStagePattern;
import dev.jsc.jscomputronics.module.computing.crafting.NetworkProcessingOperation;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern;
import dev.jsc.jscomputronics.module.computing.operation.NetworkOperation;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
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
 * The Mekanism machine classes the Fusion Reactor build needs, each driven by the network through the crafting
 * switch: the Metallurgic Infuser and the Osmium Compressor (an "extra" slot fed through the bottom face), the
 * Crusher (the plain electric family), and finally the whole alloy chain as one multi-stage pipeline ending in
 * Fusion Reactor Frames on the bench.
 */
@GameTestHolder(JsComputronics.MODID)
@PrefixGameTestTemplate(false)
public final class MekanismMachineParkGameTests {

    private MekanismMachineParkGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = MekanismRig.SETTLE;
    private static final ResourceLocation INFUSER = MekanismRig.mek("metallurgic_infuser");
    private static final ResourceLocation COMPRESSOR = MekanismRig.mek("osmium_compressor");
    private static final ResourceLocation CRUSHER = MekanismRig.mek("crusher");
    private static final ResourceLocation ALLOY_INFUSED = MekanismRig.mek("alloy_infused");
    private static final ResourceLocation ALLOY_REINFORCED = MekanismRig.mek("alloy_reinforced");
    private static final ResourceLocation ALLOY_ATOMIC = MekanismRig.mek("alloy_atomic");
    private static final ResourceLocation DUST_DIAMOND = MekanismRig.mek("dust_diamond");
    private static final ResourceLocation DUST_REFINED_OBSIDIAN = MekanismRig.mek("dust_refined_obsidian");
    private static final ResourceLocation INGOT_REFINED_OBSIDIAN = MekanismRig.mek("ingot_refined_obsidian");
    private static final ResourceLocation INGOT_OSMIUM = MekanismRig.mek("ingot_osmium");
    private static final ResourceLocation DUST_IRON = MekanismRig.mek("dust_iron");
    private static final ResourceLocation PELLET_POLONIUM = MekanismRig.mek("pellet_polonium");
    private static final ResourceLocation STEEL_CASING = MekanismRig.mek("steel_casing");
    private static final ResourceLocation FRAME = MekanismRig.generators("fusion_reactor_frame");

    /** Copper + 10 mB of redstone (one redstone dust in the extra slot) -> Infused Alloy. */
    private static ProcessingPattern infusedAlloy() {
        return new ProcessingPattern(
                List.of(new ProcessingPattern.ProcessingInput(StorageKey.of(Items.COPPER_INGOT), 1),
                        new ProcessingPattern.ProcessingInput(StorageKey.of(Items.REDSTONE), 1)),
                List.of(new ProcessingPattern.ProcessingOutput(MekanismRig.itemKey(ALLOY_INFUSED), 1, 100)),
                INFUSER.toString(), 400);
    }

    /** Infused Alloy + 20 mB of diamond (two diamond dusts) -> Reinforced Alloy. */
    private static ProcessingPattern reinforcedAlloy() {
        return new ProcessingPattern(
                List.of(new ProcessingPattern.ProcessingInput(MekanismRig.itemKey(ALLOY_INFUSED), 1),
                        new ProcessingPattern.ProcessingInput(MekanismRig.itemKey(DUST_DIAMOND), 2)),
                List.of(new ProcessingPattern.ProcessingOutput(MekanismRig.itemKey(ALLOY_REINFORCED), 1, 100)),
                INFUSER.toString(), 400);
    }

    /** Reinforced Alloy + 40 mB of refined obsidian (four dusts) -> Atomic Alloy. */
    private static ProcessingPattern atomicAlloy() {
        return new ProcessingPattern(
                List.of(new ProcessingPattern.ProcessingInput(MekanismRig.itemKey(ALLOY_REINFORCED), 1),
                        new ProcessingPattern.ProcessingInput(MekanismRig.itemKey(DUST_REFINED_OBSIDIAN), 4)),
                List.of(new ProcessingPattern.ProcessingOutput(MekanismRig.itemKey(ALLOY_ATOMIC), 1, 100)),
                INFUSER.toString(), 400);
    }

    /** A#A / #X# / A#A: 4 Atomic Alloy + 4 Polonium Pellet + Steel Casing -> 4 Fusion Reactor Frames. */
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

    private static void assertCompleted(final GameTestHelper helper, final NetworkOperation op, final String what) {
        helper.assertTrue(op.toRecord().status() == OperationRecord.STATUS_COMPLETED,
                what + " must complete; status=" + op.toRecord().status());
    }

    @GameTest(template = ARENA, timeoutTicks = 700)
    public static void infuser_takesTheInfusionItemThroughItsBottomExtraSlot(final GameTestHelper helper) {
        final MekanismRig.Rig rig = MekanismRig.build(helper, INFUSER);
        final StorageKey infused = MekanismRig.itemKey(ALLOY_INFUSED);
        final NetworkProcessingOperation[] op = new NetworkProcessingOperation[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    MekanismRig.mountBuses(helper);
                    MekanismRig.mountBottomInputBus(helper);
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    rig.net().seed(Items.COPPER_INGOT, 4);
                    rig.net().seed(Items.REDSTONE, 4);
                    MekanismRig.assertDiscovered(helper, INFUSER);
                    op[0] = rig.net().mainframe().submitNetworkProcessing(infusedAlloy(), 2, "battery");
                    helper.assertTrue(op[0] != null, "the Mainframe must accept the processing operation");
                })
                .thenWaitUntil(() -> {
                    MekanismRig.power(helper);
                    helper.assertTrue(op[0].isDone(), "the infuser is still working");
                })
                .thenExecute(() -> {
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    assertCompleted(helper, op[0], "the infusion");
                    helper.assertTrue(storage.count(infused) == 2, "two Infused Alloy must land in the network; got " + storage.count(infused));
                    helper.assertTrue(storage.count(Items.COPPER_INGOT) == 2 && storage.count(Items.REDSTONE) == 2,
                            "exactly two copper and two redstone must have been used; copper=" + storage.count(Items.COPPER_INGOT)
                                    + " redstone=" + storage.count(Items.REDSTONE));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 700)
    public static void compressor_takesOsmiumIngotsThroughItsBottomExtraSlot(final GameTestHelper helper) {
        final MekanismRig.Rig rig = MekanismRig.build(helper, COMPRESSOR);
        final StorageKey dust = MekanismRig.itemKey(DUST_REFINED_OBSIDIAN);
        final StorageKey osmium = MekanismRig.itemKey(INGOT_OSMIUM);
        final StorageKey ingot = MekanismRig.itemKey(INGOT_REFINED_OBSIDIAN);
        final NetworkProcessingOperation[] op = new NetworkProcessingOperation[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    MekanismRig.mountBuses(helper);
                    MekanismRig.mountBottomInputBus(helper);
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    storage.insert(dust, 3);
                    storage.insert(osmium, 3);
                    MekanismRig.assertDiscovered(helper, COMPRESSOR);
                    // One compression burns 200 mB of osmium: exactly one ingot in the extra slot.
                    final ProcessingPattern pattern = new ProcessingPattern(
                            List.of(new ProcessingPattern.ProcessingInput(dust, 1),
                                    new ProcessingPattern.ProcessingInput(osmium, 1)),
                            List.of(new ProcessingPattern.ProcessingOutput(ingot, 1, 100)),
                            COMPRESSOR.toString(), 400);
                    op[0] = rig.net().mainframe().submitNetworkProcessing(pattern, 2, "battery");
                    helper.assertTrue(op[0] != null, "the Mainframe must accept the processing operation");
                })
                .thenWaitUntil(() -> {
                    MekanismRig.power(helper);
                    helper.assertTrue(op[0].isDone(), "the compressor is still working");
                })
                .thenExecute(() -> {
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    assertCompleted(helper, op[0], "the compression");
                    helper.assertTrue(storage.count(ingot) == 2, "two Refined Obsidian ingots must land in the network; got " + storage.count(ingot));
                    helper.assertTrue(storage.count(dust) == 1 && storage.count(osmium) == 1,
                            "exactly two dusts and two osmium ingots must have been used; dust=" + storage.count(dust)
                                    + " osmium=" + storage.count(osmium));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 900)
    public static void crusher_turnsIngotsIntoDust(final GameTestHelper helper) {
        final MekanismRig.Rig rig = MekanismRig.build(helper, CRUSHER);
        final StorageKey dust = MekanismRig.itemKey(DUST_IRON);
        final NetworkProcessingOperation[] op = new NetworkProcessingOperation[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> MekanismRig.mountBuses(helper))
                .thenExecuteAfter(SETTLE + 2, () -> {
                    rig.net().seed(Items.IRON_INGOT, 5);
                    MekanismRig.assertDiscovered(helper, CRUSHER);
                    final ProcessingPattern pattern = new ProcessingPattern(
                            List.of(new ProcessingPattern.ProcessingInput(StorageKey.of(Items.IRON_INGOT), 1)),
                            List.of(new ProcessingPattern.ProcessingOutput(dust, 1, 100)),
                            CRUSHER.toString(), 400);
                    op[0] = rig.net().mainframe().submitNetworkProcessing(pattern, 3, "battery");
                    helper.assertTrue(op[0] != null, "the Mainframe must accept the processing operation");
                })
                .thenWaitUntil(() -> {
                    MekanismRig.power(helper);
                    helper.assertTrue(op[0].isDone(), "the crusher is still working");
                })
                .thenExecute(() -> {
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    assertCompleted(helper, op[0], "the crushing");
                    helper.assertTrue(storage.count(dust) == 3, "three iron dust must land in the network; got " + storage.count(dust));
                    helper.assertTrue(storage.count(Items.IRON_INGOT) == 2,
                            "exactly three ingots must have been used; left " + storage.count(Items.IRON_INGOT));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 4000)
    public static void pipeline_makesFusionReactorFramesFromRawMaterials(final GameTestHelper helper) {
        // Strategy B, "by stages": one multi-stage pattern walks copper up the alloy ladder in the infuser
        // (infused -> reinforced -> atomic) and ends on the bench with the frame recipe, from raw stock only.
        final MekanismRig.Rig rig = MekanismRig.build(helper, INFUSER);
        final StorageKey frame = MekanismRig.itemKey(FRAME);
        final NetworkOperation[] op = new NetworkOperation[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    MekanismRig.mountBuses(helper);
                    MekanismRig.mountBottomInputBus(helper);
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    rig.net().seed(Items.COPPER_INGOT, 4);
                    rig.net().seed(Items.REDSTONE, 4);
                    storage.insert(MekanismRig.itemKey(DUST_DIAMOND), 8);
                    storage.insert(MekanismRig.itemKey(DUST_REFINED_OBSIDIAN), 16);
                    storage.insert(MekanismRig.itemKey(PELLET_POLONIUM), 4);
                    storage.insert(MekanismRig.itemKey(STEEL_CASING), 1);
                    final MultiStagePattern pipeline = new MultiStagePattern(List.of(
                            MultiStagePattern.Stage.proc(infusedAlloy()),
                            MultiStagePattern.Stage.proc(reinforcedAlloy()),
                            MultiStagePattern.Stage.proc(atomicAlloy()),
                            MultiStagePattern.Stage.bench(framePattern())));
                    op[0] = rig.net().mainframe().submitNetworkMultiStage(pipeline, 4, "battery");
                    helper.assertTrue(op[0] != null, "the Mainframe must accept the pipeline");
                })
                .thenWaitUntil(() -> {
                    MekanismRig.power(helper);
                    helper.assertTrue(op[0].isDone(), "the pipeline is still running: "
                            + rig.net().mainframe().activeOperationRecords());
                })
                .thenExecute(() -> {
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    assertCompleted(helper, op[0], "the frame pipeline");
                    helper.assertTrue(storage.count(frame) == 4, "four Fusion Reactor Frames must land in the network; got "
                            + storage.count(frame));
                    // Everything seeded was sized for exactly one bench run: the raw stock must be spent to the unit.
                    for (final StorageKey raw : new StorageKey[]{StorageKey.of(Items.COPPER_INGOT), StorageKey.of(Items.REDSTONE),
                            MekanismRig.itemKey(DUST_DIAMOND), MekanismRig.itemKey(DUST_REFINED_OBSIDIAN),
                            MekanismRig.itemKey(PELLET_POLONIUM), MekanismRig.itemKey(STEEL_CASING),
                            MekanismRig.itemKey(ALLOY_INFUSED), MekanismRig.itemKey(ALLOY_REINFORCED), MekanismRig.itemKey(ALLOY_ATOMIC)}) {
                        helper.assertTrue(storage.count(raw) == 0, raw + " must be fully consumed; left " + storage.count(raw));
                    }
                })
                .thenSucceed();
    }
}
