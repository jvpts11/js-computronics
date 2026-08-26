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
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingSwitchBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.DataCableBlockEntity;
import dev.jsc.jscomputronics.module.computing.crafting.NetworkProcessingOperation;
import dev.jsc.jscomputronics.module.computing.crafting.ProcessingPattern;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.storage.ChemicalBridges;
import dev.jsc.jscomputronics.module.computing.storage.ChemicalPort;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Real Mekanism machines driven by the network through a Crafting Switch and its buses, with chemicals handled
 * as ordinary data: water becomes oxygen in an Electrolytic Separator, and oxygen plus raw ore becomes clumps
 * in a Purification Chamber. Every machine is placed the way a player places it (so it keeps its factory side
 * configuration) and powered through the plain FE capability on its back face, as any generator would.
 */
@GameTestHolder(JsComputronics.MODID)
@PrefixGameTestTemplate(false)
public final class MekanismProcessingGameTests {

    private MekanismProcessingGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;
    private static final ResourceLocation SEPARATOR = mek("electrolytic_separator");
    private static final ResourceLocation PURIFICATION_CHAMBER = mek("purification_chamber");
    private static final ResourceLocation OXYGEN = mek("oxygen");
    private static final ResourceLocation HYDROGEN = mek("hydrogen");
    private static final ResourceLocation CLUMP_IRON = mek("clump_iron");

    // The crafting run leaves the Crafting Computer (5,2,2) southward along x=5 through the switch at (5,2,4).
    // The machine stands east of the run's last cable: its top is under a cable carrying the Input Bus (every
    // Mekanism machine takes inputs on its top), its right face — west, for the factory north orientation — is
    // the output face and touches the run cable carrying the Receiving Bus; energy comes in through its back.
    private static final BlockPos SWITCH = new BlockPos(5, 2, 4);
    private static final BlockPos MACHINE = new BlockPos(6, 2, 7);
    private static final BlockPos CABLE_WEST = new BlockPos(5, 2, 7);
    private static final BlockPos CABLE_ABOVE = new BlockPos(6, 3, 7);
    private static final BlockPos CABLE_EAST = new BlockPos(7, 2, 7);

    private static ResourceLocation mek(final String path) {
        return ResourceLocation.fromNamespaceAndPath("mekanism", path);
    }

    private record Rig(TestWorldBuilder world, TestWorldBuilder.CraftingNetwork net) {
    }

    private static Rig rig(final GameTestHelper helper, final ResourceLocation machineId) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        world.setBlock(new BlockPos(5, 2, 3), ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(SWITCH, ComputingModule.CRAFTING_SWITCH.get());
        for (int z = 5; z <= 7; z++) {
            world.setBlock(new BlockPos(5, 2, z), ComputingModule.CRAFTING_CABLE.get());
        }
        world.setBlock(new BlockPos(5, 3, 7), ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(CABLE_ABOVE, ComputingModule.CRAFTING_CABLE.get());
        // A spur over the machine down to its east (left) face, for machines that output on both sides.
        world.setBlock(new BlockPos(7, 3, 7), ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(CABLE_EAST, ComputingModule.CRAFTING_CABLE.get());
        final Block machine = BuiltInRegistries.BLOCK.get(machineId);
        helper.assertTrue(machine != null && machine != Blocks.AIR, machineId + " must exist on the dev runtime");
        world.placeFromItem(MACHINE, machine);
        final Direction facing = world.getBlockState(MACHINE)
                .getOptionalValue(BlockStateProperties.HORIZONTAL_FACING).orElse(Direction.NORTH);
        helper.assertTrue(facing == Direction.NORTH, "the rig's face math assumes the factory orientation (north); got " + facing);
        return new Rig(world, net);
    }

    /** Tops the machine's buffer up through the FE capability on its back face (a creative cube's role). */
    private static void power(final GameTestHelper helper) {
        final IEnergyStorage fe = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK,
                helper.absolutePos(MACHINE), Direction.SOUTH);
        helper.assertTrue(fe != null, "the machine must expose FE on its back face");
        fe.receiveEnergy(Integer.MAX_VALUE, false);
    }

    private static void mountBuses(final GameTestHelper helper) {
        if (helper.getBlockEntity(CABLE_ABOVE) instanceof DataCableBlockEntity cable) {
            cable.addPart(Direction.DOWN, new InputBusPart());
        }
        if (helper.getBlockEntity(CABLE_WEST) instanceof DataCableBlockEntity cable) {
            cable.addPart(Direction.EAST, new ReceivingBusPart());
        }
    }

    /** A second Receiving Bus against the machine's east (left) face. */
    private static void mountLeftReceivingBus(final GameTestHelper helper) {
        if (helper.getBlockEntity(CABLE_EAST) instanceof DataCableBlockEntity cable) {
            cable.addPart(Direction.WEST, new ReceivingBusPart());
        }
    }

    private static void assertDiscovered(final GameTestHelper helper, final ResourceLocation machineId) {
        final CraftingSwitchBlockEntity sw = (CraftingSwitchBlockEntity) helper.getBlockEntity(SWITCH);
        helper.assertTrue(sw != null && sw.declaredMachines().stream()
                        .anyMatch(m -> m.machineType().equals(machineId.toString())),
                "the switch must discover " + machineId + " through its buses");
    }

    private static StorageKey water() {
        return StorageKey.of(new FluidStack(Fluids.WATER, 1));
    }

    @GameTest(template = ARENA, timeoutTicks = 500)
    public static void separator_collectsBothGasesThroughOneBusPerOutputFace(final GameTestHelper helper) {
        final Rig rig = rig(helper, SEPARATOR);
        final StorageKey oxygen = StorageKey.chemical(OXYGEN);
        final StorageKey hydrogen = StorageKey.chemical(HYDROGEN);
        final NetworkProcessingOperation[] op = new NetworkProcessingOperation[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    mountBuses(helper);
                    mountLeftReceivingBus(helper);
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
                    power(helper);
                    helper.assertTrue(op[0].isDone(), "the separator is still working");
                })
                .thenExecute(() -> {
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    helper.assertTrue(op[0].produced() >= 200, "the request must be met; produced " + op[0].produced());
                    helper.assertTrue(storage.count(oxygen) >= 200, "oxygen must reach the network; got " + storage.count(oxygen));
                    // With a bus on the hydrogen face too, the by-product is data as well - nothing stays behind.
                    helper.assertTrue(storage.count(hydrogen) == 400,
                            "the hydrogen must reach the network through its own bus; got " + storage.count(hydrogen));
                    final Optional<ChemicalPort> left = ChemicalBridges.portFor(helper.getLevel(), helper.absolutePos(MACHINE), Direction.EAST);
                    helper.assertTrue(left.isPresent() && left.get().count(HYDROGEN) == 0,
                            "the separator must be drained of hydrogen; holds " + left.map(p -> p.count(HYDROGEN)).orElse(-1L));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 500)
    public static void separator_turnsNetworkWaterIntoOxygenData(final GameTestHelper helper) {
        final Rig rig = rig(helper, SEPARATOR);
        final StorageKey oxygen = StorageKey.chemical(OXYGEN);
        final StorageKey hydrogen = StorageKey.chemical(HYDROGEN);
        final NetworkProcessingOperation[] op = new NetworkProcessingOperation[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> mountBuses(helper))
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    helper.assertTrue(storage.insert(water(), 4000) == 4000, "4 000 mB of water must go in as data");
                    assertDiscovered(helper, SEPARATOR);
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
                    power(helper);
                    helper.assertTrue(!op[0].isWaiting() && !op[0].isDone(), "the operation must have resolved the separator; waiting="
                            + op[0].isWaiting() + " done=" + op[0].isDone() + " status=" + op[0].toRecord().status());
                    final var tank = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                            helper.absolutePos(MACHINE), Direction.UP);
                    helper.assertTrue(tank != null && tank.getFluidInTank(0).getAmount() > 0,
                            "the Input Bus must have fed water into the separator; tank=" + (tank == null ? "none" : tank.getFluidInTank(0)));
                })
                .thenWaitUntil(() -> {
                    power(helper);
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
                    final Optional<ChemicalPort> left = ChemicalBridges.portFor(helper.getLevel(), helper.absolutePos(MACHINE), Direction.EAST);
                    helper.assertTrue(left.isPresent() && left.get().count(HYDROGEN) == 400,
                            "the separator must hold the 400 mB of hydrogen it made; got "
                                    + left.map(p -> p.count(HYDROGEN)).orElse(-1L));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 700)
    public static void purificationChamber_consumesOxygenDataWithRawIron(final GameTestHelper helper) {
        final Rig rig = rig(helper, PURIFICATION_CHAMBER);
        final StorageKey oxygen = StorageKey.chemical(OXYGEN);
        final StorageKey clump = StorageKey.of(BuiltInRegistries.ITEM.get(CLUMP_IRON));
        final NetworkProcessingOperation[] op = new NetworkProcessingOperation[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> mountBuses(helper))
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final NetworkStorage storage = rig.net().storage(helper.getLevel());
                    rig.net().seed(Items.RAW_IRON, 8);
                    helper.assertTrue(storage.insert(oxygen, 2000) == 2000, "2 000 mB of oxygen must go in as data");
                    assertDiscovered(helper, PURIFICATION_CHAMBER);
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
                    power(helper);
                    helper.assertTrue(!op[0].isWaiting() && !op[0].isDone(), "the operation must have resolved the chamber; waiting="
                            + op[0].isWaiting() + " done=" + op[0].isDone() + " status=" + op[0].toRecord().status());
                    final Optional<ChemicalPort> top = ChemicalBridges.portFor(helper.getLevel(), helper.absolutePos(MACHINE), Direction.UP);
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
                    power(helper);
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
}
