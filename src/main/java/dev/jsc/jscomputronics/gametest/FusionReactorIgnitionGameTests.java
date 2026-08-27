/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.gametest;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.storage.ChemicalBridges;
import dev.jsc.jscomputronics.module.computing.storage.ChemicalPort;
import dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * The reactor running on network data: D-T fuel held by the network fills a Hohlraum through a chemical tank
 * and feeds a reactor port, a Laser Amplifier fires into the Laser Focus Matrix until the plasma reaches
 * ignition, and Mekanism must report the reactor burning and eating the fuel the network gave it. The shell is
 * given (crafting it is proven elsewhere); what is under test is the fuel path and the ignition.
 */
@GameTestHolder(JsComputronics.MODID)
@PrefixGameTestTemplate(false)
public final class FusionReactorIgnitionGameTests {

    private FusionReactorIgnitionGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = MekanismRig.SETTLE;
    private static final BlockPos SHELL_MIN = new BlockPos(9, 2, 9);
    private static final BlockPos CONTROLLER = SHELL_MIN.offset(2, 4, 2);
    private static final BlockPos MATRIX = SHELL_MIN.offset(2, 1, 0);      // north face, lower arm
    private static final BlockPos NORTH_PORT = SHELL_MIN.offset(2, 2, 0);  // north face centre
    private static final BlockPos AMPLIFIER = SHELL_MIN.offset(2, 1, -2);  // two blocks north of the matrix
    private static final BlockPos LASER = SHELL_MIN.offset(2, 1, -4);      // two blocks north of the amplifier
    private static final BlockPos TANK = new BlockPos(7, 2, 6);
    private static final ResourceLocation FUEL = MekanismRig.generators("fusion_fuel");
    private static final ResourceLocation HOHLRAUM = MekanismRig.generators("hohlraum");

    private static Block block(final ResourceLocation id) {
        return Block.byItem(MekanismRig.item(id));
    }

    /** The shell block for one cell, or null: frames on the ring and edge middles, casing on the plus cells. */
    private static Block shellBlockAt(final int x, final int y, final int z) {
        final boolean ex = x == 0 || x == 4;
        final boolean ey = y == 0 || y == 4;
        final boolean ez = z == 0 || z == 4;
        final int extremes = (ex ? 1 : 0) + (ey ? 1 : 0) + (ez ? 1 : 0);
        if (extremes == 3 || extremes == 0) {
            return null;
        }
        if (extremes == 2) {
            final int along = !ex ? x : !ey ? y : z;
            return along == 2 ? block(MekanismRig.generators("fusion_reactor_frame")) : null;
        }
        final int u = ex ? y : x;
        final int v = ez ? y : z;
        final boolean ring = (u == 1 || u == 3) && (v == 1 || v == 3);
        final boolean plus = (u == 2 && v >= 1 && v <= 3) || (v == 2 && u >= 1 && u <= 3);
        if (ring) {
            return block(MekanismRig.generators("fusion_reactor_frame"));
        }
        if (!plus) {
            return null;
        }
        final boolean centre = u == 2 && v == 2;
        if (ey) {
            return centre && y == 4 ? block(MekanismRig.generators("fusion_reactor_controller"))
                    : centre ? block(MekanismRig.generators("fusion_reactor_frame")) : block(MekanismRig.generators("reactor_glass"));
        }
        if (centre) {
            return block(MekanismRig.generators("fusion_reactor_port"));
        }
        if (ez && z == 0 && y == 1 && x == 2) {
            return block(MekanismRig.generators("laser_focus_matrix"));
        }
        return block(MekanismRig.generators("fusion_reactor_frame"));
    }

    private static Object multiblock(final BlockEntity reactorBlock) {
        try {
            return reactorBlock.getClass().getMethod("getMultiblock").invoke(reactorBlock);
        } catch (final ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Object call(final Object target, final String method, final Object... args) {
        if (target == null) {
            return null;
        }
        try {
            for (final Method m : target.getClass().getMethods()) {
                if (m.getName().equals(method) && m.getParameterCount() == args.length) {
                    return m.invoke(target, args);
                }
            }
        } catch (final ReflectiveOperationException | RuntimeException e) {
            return null;
        }
        return null;
    }

    /**
     * Delivers {@code energy} joules to the Laser Focus Matrix the way an amplifier's beam does, through the
     * laser-receptor capability Mekanism registers for it (looked up by reflection so this test class loads
     * without Mekanism on the classpath).
     */
    private static boolean laserShot(final GameTestHelper helper, final long energy) {
        try {
            final Object capability = Class.forName("mekanism.common.capabilities.Capabilities").getField("LASER_RECEPTOR").get(null);
            final Object receptor = helper.getLevel().getCapability(
                    (net.neoforged.neoforge.capabilities.BlockCapability<?, Direction>) capability,
                    helper.absolutePos(MATRIX), Direction.NORTH);
            if (receptor == null) {
                return false;
            }
            receptor.getClass().getMethod("receiveLaserEnergy", long.class).invoke(receptor, energy);
            return true;
        } catch (final ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static boolean flag(final Object target, final String method) {
        return Boolean.TRUE.equals(call(target, method));
    }

    private static double number(final Object target, final String method, final Object... args) {
        final Object value = call(target, method, args);
        return value instanceof Number n ? n.doubleValue() : Double.NaN;
    }

    @GameTest(template = ARENA, timeoutTicks = 3000)
    public static void reactor_ignitesOnNetworkFuelAndAHohlraumFilledFromIt(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        final TestWorldBuilder.CraftingNetwork net = world.buildCraftingNetwork();
        final StorageKey fuel = StorageKey.chemical(FUEL);
        for (int x = 0; x < 5; x++) {
            for (int y = 0; y < 5; y++) {
                for (int z = 0; z < 5; z++) {
                    final Block block = shellBlockAt(x, y, z);
                    if (block != null) {
                        world.placeFromItem(SHELL_MIN.offset(x, y, z), block);
                    }
                }
            }
        }
        // Laser -> Laser Amplifier -> Laser Focus Matrix, all in a line pointing south at the reactor.
        for (final BlockPos pos : new BlockPos[]{LASER, AMPLIFIER}) {
            world.placeFromItem(pos, block(MekanismRig.mek(pos.equals(LASER) ? "laser" : "laser_amplifier")));
            final BlockState state = world.getBlockState(pos);
            if (state.hasProperty(BlockStateProperties.FACING)) {
                world.setBlock(pos, state.setValue(BlockStateProperties.FACING, Direction.SOUTH));
            }
        }
        world.placeFromItem(TANK, block(MekanismRig.mek("basic_chemical_tank")));
        final ItemStack[] hohlraum = {ItemStack.EMPTY};
        final double[] plasmaBefore = {0.0};
        final int[] sustained = {0};
        final long[] fedTotal = {0};
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(flag(multiblock(helper.getBlockEntity(CONTROLLER)), "isFormed"),
                        "Mekanism must report the reactor formed"))
                .thenExecuteAfter(SETTLE, () -> {
                    final NetworkStorage storage = net.storage(helper.getLevel());
                    helper.assertTrue(storage.insert(fuel, 2000) == 2000, "2 000 mB of D-T fuel must go in as data");
                    // Network -> chemical tank: the tank will fill the Hohlraum from it.
                    final Optional<ChemicalPort> tankPort = ChemicalBridges.portFor(helper.getLevel(), helper.absolutePos(TANK), Direction.UP);
                    helper.assertTrue(tankPort.isPresent(), "the tank must expose a chemical port");
                    final long toTank = storage.select(fuel, 1000, new ExternalDataPort(null, null, tankPort.get()));
                    helper.assertTrue(toTank == 1000, "1 000 mB of fuel must reach the tank; got " + toTank);
                    final IItemHandler tankItems = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(TANK), Direction.UP);
                    helper.assertTrue(tankItems != null, "the tank must expose its item slots on top");
                    final ItemStack left = ItemHandlerHelper.insertItem(tankItems, new ItemStack(MekanismRig.item(HOHLRAUM)), false);
                    helper.assertTrue(left.isEmpty(), "the tank must take the empty Hohlraum into its fill slot");
                })
                // The tank drains 10 mB into the Hohlraum: full when the tank is down to 990.
                .thenWaitUntil(() -> {
                    final Optional<ChemicalPort> tankPort = ChemicalBridges.portFor(helper.getLevel(), helper.absolutePos(TANK), Direction.UP);
                    helper.assertTrue(tankPort.isPresent() && tankPort.get().count(FUEL) <= 990,
                            "the tank must fill the Hohlraum; tank holds " + tankPort.map(p -> p.count(FUEL)).orElse(-1L));
                })
                .thenExecuteAfter(SETTLE, () -> {
                    final IItemHandler tankItems = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(TANK), Direction.UP);
                    for (int slot = 0; tankItems != null && slot < tankItems.getSlots() && hohlraum[0].isEmpty(); slot++) {
                        if (tankItems.getStackInSlot(slot).is(MekanismRig.item(HOHLRAUM))) {
                            hohlraum[0] = tankItems.extractItem(slot, 1, false);
                        }
                    }
                    helper.assertTrue(!hohlraum[0].isEmpty(), "the filled Hohlraum must come back out of the tank");
                    final IItemHandler reactorItems = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, helper.absolutePos(CONTROLLER), Direction.UP);
                    helper.assertTrue(reactorItems != null, "the formed reactor must expose its Hohlraum slot at the controller");
                    final ItemStack left = ItemHandlerHelper.insertItem(reactorItems, hohlraum[0], false);
                    helper.assertTrue(left.isEmpty(), "the reactor must take the filled Hohlraum");
                    helper.assertTrue(ChemicalBridges.portFor(helper.getLevel(), helper.absolutePos(NORTH_PORT), Direction.NORTH).isPresent(),
                            "the reactor port must expose a chemical port on its outer face");
                })
                // The physical chain: the Laser (fed FE on its back) fires into the amplifier, which fires
                // into the matrix; the plasma must warm up from the ambient 300 K.
                .thenExecuteAfter(SETTLE, () -> plasmaBefore[0] = number(multiblock(helper.getBlockEntity(CONTROLLER)), "getPlasmaTemp"))
                .thenWaitUntil(() -> {
                    final IEnergyStorage fe = helper.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, helper.absolutePos(LASER), Direction.NORTH);
                    helper.assertTrue(fe != null && fe.receiveEnergy(Integer.MAX_VALUE, false) >= 0, "the Laser must take FE on its back");
                    final Object data = multiblock(helper.getBlockEntity(CONTROLLER));
                    helper.assertTrue(number(data, "getPlasmaTemp") > plasmaBefore[0] + 1000.0,
                            "the laser chain must heat the plasma; plasma=" + number(data, "getPlasmaTemp") + " before=" + plasmaBefore[0]);
                })
                // Ignition needs about 1E10 J; a Laser gives 10 kJ per tick, so the rest of the shot goes in the
                // way the amplifier's beam delivers it: through the matrix's laser-receptor capability.
                .thenWaitUntil(() -> {
                    final Object data = multiblock(helper.getBlockEntity(CONTROLLER));
                    if (!flag(data, "isBurning")) {
                        helper.assertTrue(laserShot(helper, 5_000_000_000L), "the matrix must expose Mekanism's laser receptor");
                    }
                    helper.assertTrue(flag(data, "isBurning"), "waiting for ignition: plasma=" + number(data, "getPlasmaTemp")
                            + " ignition=" + number(data, "getIgnitionTemperature", false));
                })
                // Burning, the plasma eats whatever fuel the tank holds above ignition heat, so the network
                // meters it in: two millibuckets a tick through the port, like an injection rate, for 40 ticks.
                .thenWaitUntil(() -> {
                    final Object data = multiblock(helper.getBlockEntity(CONTROLLER));
                    helper.assertTrue(flag(data, "isBurning"), "the reactor must keep burning on metered network fuel; tick " + sustained[0]
                            + " plasma=" + number(data, "getPlasmaTemp"));
                    final Optional<ChemicalPort> port = ChemicalBridges.portFor(helper.getLevel(), helper.absolutePos(NORTH_PORT), Direction.NORTH);
                    final long fed = port.isEmpty() ? 0 : net.storage(helper.getLevel()).select(fuel, 2, new ExternalDataPort(null, null, port.get()));
                    fedTotal[0] += fed;
                    helper.assertTrue(fed == 2, "the port must take the network's 2 mB every tick; took " + fed);
                    helper.assertTrue(++sustained[0] >= 40, "sustaining");
                })
                .thenExecute(() -> {
                    final Object data = multiblock(helper.getBlockEntity(CONTROLLER));
                    helper.assertTrue(flag(data, "isBurning"), "the reactor must still be burning after 40 metered ticks");
                    helper.assertTrue(number(data, "getPlasmaTemp") > number(data, "getIgnitionTemperature", false),
                            "the plasma must sit above ignition; plasma=" + number(data, "getPlasmaTemp"));
                    helper.assertTrue(net.storage(helper.getLevel()).count(fuel) == 1000 - fedTotal[0],
                            "the network must hold exactly what it did not meter out; got " + net.storage(helper.getLevel()).count(fuel)
                                    + " fed=" + fedTotal[0]);
                })
                .thenSucceed();
    }
}
