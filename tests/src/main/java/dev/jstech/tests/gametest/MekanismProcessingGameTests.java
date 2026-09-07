/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.crafting.NetworkProcessingOperation;
import dev.jstech.computronics.crafting.ProcessingPattern;
import dev.jstech.computronics.operation.NetworkStorage;
import dev.jstech.computronics.operation.payload.OperationRecord;
import dev.jstech.computronics.storage.ChemicalBridges;
import dev.jstech.computronics.storage.IChemicalPort;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.tests.JsTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Real Mekanism machines driven by the network through a Crafting Switch and its buses, with chemicals handled
 * as ordinary data: water becomes oxygen in an Electrolytic Separator, and oxygen plus raw ore becomes clumps
 * in a Purification Chamber. The rig is {@link MekanismRig}.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class MekanismProcessingGameTests {

    private MekanismProcessingGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = MekanismRig.SETTLE;
    private static final BlockPos MACHINE = MekanismRig.MACHINE;
    private static final ResourceLocation SEPARATOR = MekanismRig.mek("electrolytic_separator");
    private static final ResourceLocation PURIFICATION_CHAMBER = MekanismRig.mek("purification_chamber");
    private static final ResourceLocation OXYGEN = MekanismRig.mek("oxygen");
    private static final ResourceLocation HYDROGEN = MekanismRig.mek("hydrogen");
    private static final ResourceLocation CLUMP_IRON = MekanismRig.mek("clump_iron");

    private static StorageKey water() {
        return MekanismRig.water();
    }

    @GameTest(template = ARENA, timeoutTicks = 500)
    public static void separator_collectsBothGasesThroughOneBusPerOutputFace(final GameTestHelper helper) {
        final MekanismRig.Rig rig = MekanismRig.build(helper, SEPARATOR);
        final StorageKey oxygen = StorageKey.chemical(OXYGEN);
        final StorageKey hydrogen = StorageKey.chemical(HYDROGEN);
        final NetworkProcessingOperation[] op = new NetworkProcessingOperation[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    MekanismRig.mountBuses(helper);
                    MekanismRig.mountLeftReceivingBus(helper);
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    storage.insert(water(), 4000);
                    final ProcessingPattern pattern = new ProcessingPattern(
                            List.of(new ProcessingPattern.ProcessingInput(water(), 200)),
                            List.of(new ProcessingPattern.ProcessingOutput(oxygen, 100, 100),
                                    new ProcessingPattern.ProcessingOutput(hydrogen, 200, 100)),
                            SEPARATOR.toString(), 200);
                    op[0] = rig.net().mainframe().submitNetworkProcessing(pattern, 200, "battery");
                    helper.assertTrue(op[0] != null, "the Mainframe must accept the processing operation");
                })
                .thenWaitUntil(() -> {
                    MekanismRig.power(helper);
                    helper.assertTrue(op[0].isDone(), "the separator is still working");
                })
                .thenExecute(() -> {
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    helper.assertTrue(op[0].produced() >= 200, "the request must be met; produced " + op[0].produced());
                    helper.assertTrue(storage.count(oxygen) >= 200, "oxygen must reach the network; got " + storage.count(oxygen));
                    // With a bus on the hydrogen face too, the by-product is data as well - nothing stays behind.
                    helper.assertTrue(storage.count(hydrogen) == 400,
                            "the hydrogen must reach the network through its own bus; got " + storage.count(hydrogen));
                    final Optional<IChemicalPort> left = ChemicalBridges.portFor(helper.getLevel(), helper.absolutePos(MACHINE), Direction.EAST);
                    helper.assertTrue(left.isPresent() && left.get().count(HYDROGEN) == 0,
                            "the separator must be drained of hydrogen; holds " + left.map(p -> p.count(HYDROGEN)).orElse(-1L));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 500)
    public static void separator_turnsNetworkWaterIntoOxygenData(final GameTestHelper helper) {
        final MekanismRig.Rig rig = MekanismRig.build(helper, SEPARATOR);
        final StorageKey oxygen = StorageKey.chemical(OXYGEN);
        final StorageKey hydrogen = StorageKey.chemical(HYDROGEN);
        final NetworkProcessingOperation[] op = new NetworkProcessingOperation[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> MekanismRig.mountBuses(helper))
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    helper.assertTrue(storage.insert(water(), 4000) == 4000, "4 000 mB of water must go in as data");
                    MekanismRig.assertDiscovered(helper, SEPARATOR);
                    // One lot: 200 mB of water splits into 200 mB of hydrogen and 100 mB of oxygen.
                    final ProcessingPattern pattern = new ProcessingPattern(
                            List.of(new ProcessingPattern.ProcessingInput(water(), 200)),
                            List.of(new ProcessingPattern.ProcessingOutput(oxygen, 100, 100),
                                    new ProcessingPattern.ProcessingOutput(hydrogen, 200, 100)),
                            SEPARATOR.toString(), 200);
                    op[0] = rig.net().mainframe().submitNetworkProcessing(pattern, 200, "battery");
                    helper.assertTrue(op[0] != null, "the Mainframe must accept the processing operation");
                })
                .thenExecuteAfter(20, () -> {
                    MekanismRig.power(helper);
                    helper.assertTrue(!op[0].isWaiting() && !op[0].isDone(), "the operation must have resolved the separator; waiting="
                            + op[0].isWaiting() + " done=" + op[0].isDone() + " status=" + op[0].toRecord().status());
                    final var tank = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                            helper.absolutePos(MACHINE), Direction.UP);
                    helper.assertTrue(tank != null && tank.getFluidInTank(0).getAmount() > 0,
                            "the Input Bus must have fed water into the separator; tank=" + (tank == null ? "none" : tank.getFluidInTank(0)));
                })
                .thenWaitUntil(() -> {
                    MekanismRig.power(helper);
                    helper.assertTrue(op[0].isDone(), "the separator is still working");
                })
                .thenExecute(() -> {
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    helper.assertTrue(op[0].produced() >= 200, "the request must be met; produced " + op[0].produced()
                            + " status=" + op[0].toRecord().status() + " oxygenInNetwork=" + storage.count(oxygen));
                    helper.assertTrue(storage.count(oxygen) >= 200,
                            "the oxygen must land in the network as data; got " + storage.count(oxygen));
                    // Exactly the two lots the request needed left the network: feeding is bounded by demand.
                    helper.assertTrue(storage.count(water()) == 3600,
                            "two lots (400 mB) of water must have been fed, no more; left " + storage.count(water()));
                    // Hydrogen leaves through the other output face, which carries no bus: it stays in the machine.
                    helper.assertTrue(storage.count(hydrogen) == 0, "no hydrogen must reach the network without a bus on its face");
                    final Optional<IChemicalPort> left = ChemicalBridges.portFor(helper.getLevel(), helper.absolutePos(MACHINE), Direction.EAST);
                    helper.assertTrue(left.isPresent() && left.get().count(HYDROGEN) == 400,
                            "the separator must hold the 400 mB of hydrogen it made; got "
                                    + left.map(p -> p.count(HYDROGEN)).orElse(-1L));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 700)
    public static void purificationChamber_consumesOxygenDataWithRawIron(final GameTestHelper helper) {
        final MekanismRig.Rig rig = MekanismRig.build(helper, PURIFICATION_CHAMBER);
        final StorageKey oxygen = StorageKey.chemical(OXYGEN);
        final StorageKey clump = StorageKey.of(BuiltInRegistries.ITEM.get(CLUMP_IRON));
        final NetworkProcessingOperation[] op = new NetworkProcessingOperation[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> MekanismRig.mountBuses(helper))
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    rig.net().seed(Items.RAW_IRON, 8);
                    helper.assertTrue(storage.insert(oxygen, 2000) == 2000, "2 000 mB of oxygen must go in as data");
                    MekanismRig.assertDiscovered(helper, PURIFICATION_CHAMBER);
                    // One lot: a raw iron and the 200 mB of oxygen one purification burns become two clumps.
                    final ProcessingPattern pattern = new ProcessingPattern(
                            List.of(new ProcessingPattern.ProcessingInput(StorageKey.of(Items.RAW_IRON), 1),
                                    new ProcessingPattern.ProcessingInput(oxygen, 200)),
                            List.of(new ProcessingPattern.ProcessingOutput(clump, 2, 100)),
                            PURIFICATION_CHAMBER.toString(), 400);
                    op[0] = rig.net().mainframe().submitNetworkProcessing(pattern, 4, "battery");
                    helper.assertTrue(op[0] != null, "the Mainframe must accept the processing operation");
                })
                .thenExecuteAfter(20, () -> {
                    MekanismRig.power(helper);
                    helper.assertTrue(!op[0].isWaiting() && !op[0].isDone(), "the operation must have resolved the chamber; waiting="
                            + op[0].isWaiting() + " done=" + op[0].isDone() + " status=" + op[0].toRecord().status());
                    final Optional<IChemicalPort> top = ChemicalBridges.portFor(helper.getLevel(), helper.absolutePos(MACHINE), Direction.UP);
                    final var items = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                            helper.absolutePos(MACHINE), Direction.UP);
                    helper.assertTrue(top.isPresent() && top.get().count(OXYGEN) > 0,
                            "the Input Bus must have fed oxygen into the chamber; got " + top.map(t -> t.count(OXYGEN)).orElse(-1L));
                    boolean rawIronInside = false;
                    for (int slot = 0; items != null && slot < items.getSlots(); slot++) {
                        rawIronInside |= items.getStackInSlot(slot).is(Items.RAW_IRON);
                    }
                    helper.assertTrue(rawIronInside, "the Input Bus must have fed raw iron into the chamber");
                })
                .thenWaitUntil(() -> {
                    MekanismRig.power(helper);
                    helper.assertTrue(op[0].isDone(), "the chamber is still working");
                })
                .thenExecute(() -> {
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    helper.assertTrue(op[0].produced() >= 4, "the request must be met; produced " + op[0].produced()
                            + " status=" + op[0].toRecord().status() + " clumpsInNetwork=" + storage.count(clump));
                    helper.assertTrue(storage.count(clump) >= 4, "the clumps must land in the network; got " + storage.count(clump));
                    helper.assertTrue(storage.count(Items.RAW_IRON) == 6,
                            "two raw iron must have been fed, no more; left " + storage.count(Items.RAW_IRON));
                    helper.assertTrue(storage.count(oxygen) <= 1600,
                            "the chamber must have taken the oxygen for two runs; left " + storage.count(oxygen));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 600)
    public static void chemicalInfuser_makesFusionFuelFromDeuteriumAndTritiumData(final GameTestHelper helper) {
        // The reactor's fuel as data: deuterium and tritium held by the network go into the Chemical Infuser
        // through one bus on each side face (the machine takes a different input on each), and the D-T fuel
        // comes back through the bus on its front.
        final MekanismRig.Rig rig = MekanismRig.build(helper, MekanismRig.mek("chemical_infuser"));
        final StorageKey deuterium = StorageKey.chemical(MekanismRig.generators("deuterium"));
        final StorageKey tritium = StorageKey.chemical(MekanismRig.generators("tritium"));
        final StorageKey fuel = StorageKey.chemical(MekanismRig.generators("fusion_fuel"));
        final NetworkProcessingOperation[] op = new NetworkProcessingOperation[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final dev.jstech.tests.testkit.TestWorldBuilder world = rig.world();
                    // Right (west) face: the run cable already touches it; left (east) and front (north) spurs.
                    if (world.getBlockEntity(MekanismRig.CABLE_WEST) instanceof dev.jstech.computronics.blockentity.DataCableBlockEntity cable) {
                        cable.addPart(Direction.EAST, new dev.jstech.computronics.block.part.InputBusPart());
                    }
                    MekanismRig.mountLeftInputBus(world);
                    MekanismRig.mountFrontReceivingBus(world);
                })
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    helper.assertTrue(storage.insert(deuterium, 1000) == 1000 && storage.insert(tritium, 1000) == 1000,
                            "both fuel gases must go in as data");
                    MekanismRig.assertDiscovered(helper, MekanismRig.mek("chemical_infuser"));
                    // One lot: 100 mB of each gas make 200 mB of D-T fuel.
                    final ProcessingPattern pattern = new ProcessingPattern(
                            List.of(new ProcessingPattern.ProcessingInput(deuterium, 100),
                                    new ProcessingPattern.ProcessingInput(tritium, 100)),
                            List.of(new ProcessingPattern.ProcessingOutput(fuel, 200, 100)),
                            MekanismRig.mek("chemical_infuser").toString(), 200);
                    op[0] = rig.net().mainframe().submitNetworkProcessing(pattern, 600, "battery");
                    helper.assertTrue(op[0] != null, "the Mainframe must accept the processing operation");
                })
                .thenWaitUntil(() -> {
                    MekanismRig.power(helper);
                    helper.assertTrue(op[0].isDone(), "the infuser is still working: " + rig.net().mainframe().activeOperationRecords());
                })
                .thenExecute(() -> {
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    helper.assertTrue(op[0].produced() >= 600 && op[0].toRecord().status() == OperationRecord.STATUS_COMPLETED,
                            "600 mB of fuel must be made; produced " + op[0].produced() + " status=" + op[0].toRecord().status());
                    helper.assertTrue(storage.count(fuel) >= 600, "the D-T fuel must land in the network as data; got " + storage.count(fuel));
                    helper.assertTrue(storage.count(deuterium) == 700 && storage.count(tritium) == 700,
                            "three lots of each gas must have been fed, no more; D=" + storage.count(deuterium) + " T=" + storage.count(tritium));
                })
                .thenSucceed();
    }
}
