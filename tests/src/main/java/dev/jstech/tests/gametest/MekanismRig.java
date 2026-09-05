/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.block.part.InputBusPart;
import dev.jstech.computronics.block.part.ReceivingBusPart;
import dev.jstech.computronics.blockentity.CraftingSwitchBlockEntity;
import dev.jstech.computronics.blockentity.DataCableBlockEntity;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * One Mekanism machine hung off the crafting switch through buses, for the machine GameTests and client tests.
 * The crafting run leaves the Crafting Computer (5,2,2) southward along x=5 through the switch at (5,2,4); the
 * machine stands east of the run's last cable. Mekanism machines take inputs on their top and, for the "extra"
 * slot, their bottom; they give outputs on their right face (west, for the factory north orientation) and, for
 * two-output machines, their left face (east) too; energy goes in through any face. Cables reach every one of
 * those faces so a bus can be mounted against each: the Input Bus above (and below), the Receiving Bus west (and
 * east). Machines are placed the way a player places them, so their factory side configuration applies.
 */
public final class MekanismRig {

    private MekanismRig() {
    }

    public static final int SETTLE = 4;
    public static final BlockPos SWITCH = new BlockPos(5, 2, 4);
    public static final BlockPos MACHINE = new BlockPos(6, 2, 7);
    public static final BlockPos CABLE_WEST = new BlockPos(5, 2, 7);
    public static final BlockPos CABLE_ABOVE = new BlockPos(6, 3, 7);
    public static final BlockPos CABLE_BELOW = new BlockPos(6, 1, 7);
    public static final BlockPos CABLE_EAST = new BlockPos(7, 2, 7);
    public static final BlockPos CABLE_NORTH = new BlockPos(6, 2, 6);

    // A second machine of the same kind, further south, for tests that need two physical machines (concurrency
    // scales with the machines present). Its buses hang from B_ABOVE (top input), B_BELOW (bottom extra) and
    // B_RUN (right/west receiving); the run links back to the first machine's cables at (5,2,8).
    public static final BlockPos MACHINE_B = new BlockPos(6, 2, 10);
    public static final BlockPos B_RUN = new BlockPos(5, 2, 10);
    public static final BlockPos B_ABOVE = new BlockPos(6, 3, 10);
    public static final BlockPos B_BELOW = new BlockPos(6, 1, 10);

    public record Rig(TestWorldBuilder world, TestWorldBuilder.CraftingNetwork net) {
    }

    public static ResourceLocation mek(final String path) {
        return ResourceLocation.fromNamespaceAndPath("mekanism", path);
    }

    public static ResourceLocation generators(final String path) {
        return ResourceLocation.fromNamespaceAndPath("mekanismgenerators", path);
    }

    public static Item item(final ResourceLocation id) {
        return BuiltInRegistries.ITEM.get(id);
    }

    public static StorageKey itemKey(final ResourceLocation id) {
        return StorageKey.of(item(id));
    }

    public static StorageKey water() {
        return StorageKey.of(new FluidStack(Fluids.WATER, 1));
    }

    /** Builds the crafting network and the machine rig in {@code world}; fails loudly if the machine is missing. */
    public static TestWorldBuilder.CraftingNetwork place(final TestWorldBuilder world, final ResourceLocation machineId) {
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        world.setBlock(new BlockPos(5, 2, 3), ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(SWITCH, ComputingModule.CRAFTING_SWITCH.get());
        for (int z = 5; z <= 7; z++) {
            world.setBlock(new BlockPos(5, 2, z), ComputingModule.CRAFTING_CABLE.get());
        }
        // Spurs over and under the machine, each continuing to its far (east) face.
        world.setBlock(new BlockPos(5, 3, 7), ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(CABLE_ABOVE, ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(new BlockPos(7, 3, 7), ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(CABLE_EAST, ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(new BlockPos(5, 1, 7), ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(CABLE_BELOW, ComputingModule.CRAFTING_CABLE.get());
        // And one against the front (north) face, off the run's (5,2,6), for machines that output forward.
        world.setBlock(CABLE_NORTH, ComputingModule.CRAFTING_CABLE.get());
        final Block machine = BuiltInRegistries.BLOCK.get(machineId);
        if (machine == null || machine == Blocks.AIR) {
            throw new IllegalStateException(machineId + " must exist on the dev runtime");
        }
        world.placeFromItem(MACHINE, machine);
        final Direction facing = world.getBlockState(MACHINE)
                .getOptionalValue(BlockStateProperties.HORIZONTAL_FACING).orElse(Direction.NORTH);
        if (facing != Direction.NORTH) {
            throw new IllegalStateException("the rig's face math assumes the factory orientation (north); got " + facing);
        }
        return net;
    }

    public static Rig build(final GameTestHelper helper, final ResourceLocation machineId) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        return new Rig(world, place(world, machineId));
    }

    /** Input Bus against the top, Receiving Bus against the right (west) face. */
    public static void mountBuses(final TestWorldBuilder world) {
        if (world.getBlockEntity(CABLE_ABOVE) instanceof DataCableBlockEntity cable) {
            cable.addPart(Direction.DOWN, new InputBusPart());
        }
        if (world.getBlockEntity(CABLE_WEST) instanceof DataCableBlockEntity cable) {
            cable.addPart(Direction.EAST, new ReceivingBusPart());
        }
    }

    public static void mountBuses(final GameTestHelper helper) {
        mountBuses(TestWorldBuilder.forGameTest(helper));
    }

    /** A second Input Bus against the bottom: the "extra" slot of infusers and compressors lives there. */
    public static void mountBottomInputBus(final TestWorldBuilder world) {
        if (world.getBlockEntity(CABLE_BELOW) instanceof DataCableBlockEntity cable) {
            cable.addPart(Direction.UP, new InputBusPart());
        }
    }

    public static void mountBottomInputBus(final GameTestHelper helper) {
        mountBottomInputBus(TestWorldBuilder.forGameTest(helper));
    }

    /** Places a second machine of {@code machineId} south of the first, its cables linked to the run. */
    public static void placeSecondMachine(final TestWorldBuilder world, final ResourceLocation machineId) {
        world.setBlock(new BlockPos(5, 2, 8), ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(new BlockPos(5, 2, 9), ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(B_RUN, ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(new BlockPos(5, 3, 10), ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(B_ABOVE, ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(new BlockPos(5, 1, 10), ComputingModule.CRAFTING_CABLE.get());
        world.setBlock(B_BELOW, ComputingModule.CRAFTING_CABLE.get());
        world.placeFromItem(MACHINE_B, BuiltInRegistries.BLOCK.get(machineId));
    }

    /**
     * Mounts the second machine's buses: a top Input Bus (main input), a bottom Input Bus (the "extra" slot), and
     * a right (west) Receiving Bus. A null filter leaves that bus unfiltered (a wildcard); a non-null one restricts
     * it, which is how two same-type machines are told apart so each stage of a chain routes to its own machine.
     */
    public static void mountSecondMachineBuses(final TestWorldBuilder world,
                                               final net.minecraft.world.item.ItemStack topFilter,
                                               final net.minecraft.world.item.ItemStack bottomFilter) {
        if (world.getBlockEntity(B_ABOVE) instanceof DataCableBlockEntity cable) {
            final InputBusPart top = new InputBusPart();
            cable.addPart(Direction.DOWN, top);
            if (topFilter != null) {
                top.setFilter(topFilter);
            }
        }
        if (world.getBlockEntity(B_BELOW) instanceof DataCableBlockEntity cable) {
            final InputBusPart bottom = new InputBusPart();
            cable.addPart(Direction.UP, bottom);
            if (bottomFilter != null) {
                bottom.setFilter(bottomFilter);
            }
        }
        if (world.getBlockEntity(B_RUN) instanceof DataCableBlockEntity cable) {
            cable.addPart(Direction.EAST, new ReceivingBusPart());
        }
    }

    /** A second Receiving Bus against the left (east) face, for machines that output on both sides. */
    public static void mountLeftReceivingBus(final TestWorldBuilder world) {
        if (world.getBlockEntity(CABLE_EAST) instanceof DataCableBlockEntity cable) {
            cable.addPart(Direction.WEST, new ReceivingBusPart());
        }
    }

    public static void mountLeftReceivingBus(final GameTestHelper helper) {
        mountLeftReceivingBus(TestWorldBuilder.forGameTest(helper));
    }

    /** An Input Bus against the left (east) face: the first input of two-input machines. */
    public static void mountLeftInputBus(final TestWorldBuilder world) {
        if (world.getBlockEntity(CABLE_EAST) instanceof DataCableBlockEntity cable) {
            cable.addPart(Direction.WEST, new InputBusPart());
        }
    }

    /** A Receiving Bus against the front (north) face: where two-input machines give their output. */
    public static void mountFrontReceivingBus(final TestWorldBuilder world) {
        if (world.getBlockEntity(CABLE_NORTH) instanceof DataCableBlockEntity cable) {
            cable.addPart(Direction.SOUTH, new ReceivingBusPart());
        }
    }

    /**
     * Tops the machine's buffer up through the FE capability on its back face (a creative cube's role); returns
     * false when the machine exposes no FE there.
     */
    public static boolean power(final ServerLevel level, final BlockPos machine) {
        final IEnergyStorage fe = level.getCapability(Capabilities.EnergyStorage.BLOCK, machine, Direction.SOUTH);
        if (fe == null) {
            return false;
        }
        fe.receiveEnergy(Integer.MAX_VALUE, false);
        return true;
    }

    public static void power(final GameTestHelper helper) {
        helper.assertTrue(power(helper.getLevel(), helper.absolutePos(MACHINE)), "the machine must expose FE on its back face");
    }

    public static boolean discovered(final TestWorldBuilder world, final ResourceLocation machineId) {
        return world.getBlockEntity(SWITCH) instanceof CraftingSwitchBlockEntity sw
                && sw.declaredMachines().stream().anyMatch(m -> m.machineType().equals(machineId.toString()));
    }

    public static void assertDiscovered(final GameTestHelper helper, final ResourceLocation machineId) {
        helper.assertTrue(discovered(TestWorldBuilder.forGameTest(helper), machineId),
                "the switch must discover " + machineId + " through its buses");
    }
}
