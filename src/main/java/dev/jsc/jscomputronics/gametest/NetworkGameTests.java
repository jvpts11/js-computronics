/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.gametest;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.Optional;

/**
 * In-world integration tests for the data network (conflict detection, cable connectivity) and the Mainframe's Operation dispatch (the virtual-thread runtime).
 */
@GameTestHolder(JsComputronics.MODID)
@PrefixGameTestTemplate(false)
public final class NetworkGameTests {

    private NetworkGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    // Conflict detection (the self-healing redesign)

    @GameTest(template = ARENA)
    public static void standaloneMainframe_ownsNetworkWithoutConflict(final GameTestHelper helper) {
        final BlockPos a = new BlockPos(2, 2, 2);
        final MainframeBlockEntity beA = placeRunningMainframe(helper, a);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(beA.isRunning(), "mainframe should be running");
                    helper.assertTrue(beA.networkUuid() != null, "standalone mainframe should own a network");
                    helper.assertFalse(beA.hasNetworkConflict(), "standalone mainframe has no conflict");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void twoMainframesOnOneSegment_bothConflict(final GameTestHelper helper) {
        final BlockPos a = new BlockPos(2, 2, 2);
        final BlockPos cable = new BlockPos(3, 2, 2);
        final BlockPos b = new BlockPos(4, 2, 2);
        final MainframeBlockEntity beA = placeRunningMainframe(helper, a);
        helper.setBlock(cable, ComputingModule.ETHERNET_CABLE.get());
        final MainframeBlockEntity beB = placeRunningMainframe(helper, b);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(beA.hasNetworkConflict(), "A should detect the conflict");
                    helper.assertTrue(beB.hasNetworkConflict(), "B should detect the conflict");
                    helper.assertTrue(beA.networkUuid() == null, "A in conflict owns no network");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void conflictClears_whenSecondMainframeDestroyed(final GameTestHelper helper) {
        final BlockPos a = new BlockPos(2, 2, 2);
        final BlockPos cable = new BlockPos(3, 2, 2);
        final BlockPos b = new BlockPos(4, 2, 2);
        final MainframeBlockEntity beA = placeRunningMainframe(helper, a);
        helper.setBlock(cable, ComputingModule.ETHERNET_CABLE.get());
        placeRunningMainframe(helper, b);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () ->
                        helper.assertTrue(beA.hasNetworkConflict(), "A should be in conflict first"))
                .thenExecute(() -> helper.setBlock(b, Blocks.AIR)) // destroy B
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertFalse(beA.hasNetworkConflict(), "A conflict must clear after B is destroyed");
                    helper.assertTrue(beA.networkUuid() != null, "A must reclaim its network");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void conflictClears_whenSecondMainframePoweredOff(final GameTestHelper helper) {
        final BlockPos a = new BlockPos(2, 2, 2);
        final BlockPos cable = new BlockPos(3, 2, 2);
        final BlockPos b = new BlockPos(4, 2, 2);
        final MainframeBlockEntity beA = placeRunningMainframe(helper, a);
        helper.setBlock(cable, ComputingModule.ETHERNET_CABLE.get());
        final MainframeBlockEntity beB = placeRunningMainframe(helper, b);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () ->
                        helper.assertTrue(beA.hasNetworkConflict(), "A should be in conflict first"))
                .thenExecute(beB::togglePower) // power off B
                .thenExecuteAfter(SETTLE, () ->
                        helper.assertFalse(beA.hasNetworkConflict(), "A conflict must clear after B powers off"))
                .thenSucceed();
    }

    // Cable connectivity (the sever bug)

    @GameTest(template = ARENA)
    public static void cableSever_dropsFarFragmentFromNetwork(final GameTestHelper helper) {
        final BlockPos a = new BlockPos(1, 2, 2);
        final BlockPos c1 = new BlockPos(2, 2, 2);
        final BlockPos c2 = new BlockPos(3, 2, 2);
        final BlockPos c3 = new BlockPos(4, 2, 2);
        placeRunningMainframe(helper, a);
        helper.setBlock(c1, ComputingModule.ETHERNET_CABLE.get());
        helper.setBlock(c2, ComputingModule.ETHERNET_CABLE.get());
        helper.setBlock(c3, ComputingModule.ETHERNET_CABLE.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(sameNetwork(helper, c1, c3), "c1 and c3 should start on one network");
                    helper.assertTrue(networkOf(helper, c3).isPresent(), "c3 should start networked");
                })
                .thenExecute(() -> helper.setBlock(c2, Blocks.AIR)) // sever the segment
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertFalse(sameNetwork(helper, c1, c3), "c1 and c3 must split into two networks");
                    helper.assertTrue(networkOf(helper, c1).isPresent(), "c1 (next to mainframe) keeps a network");
                    helper.assertTrue(networkOf(helper, c3).isEmpty(), "severed far cable c3 must be network-less");
                })
                .thenSucceed();
    }

    // Operation dispatch (the virtual-thread runtime on the Mainframe)

    @GameTest(template = ARENA)
    public static void dispatch_completesSubmittedOperations(final GameTestHelper helper) {
        final BlockPos a = new BlockPos(2, 2, 2);
        final MainframeBlockEntity be = placeRunningMainframe(helper, a);
        final int n = 6;
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(be.isRunning(), "mainframe should be running");
                    final int submitted = be.submitSelfTest(n, 50_000);
                    helper.assertTrue(submitted == n, "should submit " + n + " ops, got " + submitted);
                })
                .thenExecuteAfter(40, () -> {
                    helper.assertTrue(be.completedOps() == n,
                            "all " + n + " ops should complete; done=" + be.completedOps());
                    helper.assertTrue(be.pendingOps() == 0, "no ops should remain pending");
                    helper.assertTrue(be.runningOps() == 0, "no ops should still be running");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void dispatch_closesOnPowerOff(final GameTestHelper helper) {
        final BlockPos a = new BlockPos(2, 2, 2);
        final MainframeBlockEntity be = placeRunningMainframe(helper, a);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> be.submitSelfTest(4, 50_000))
                .thenExecute(be::togglePower) // power off
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertFalse(be.isRunning(), "mainframe should be stopped");
                    helper.assertTrue(be.pendingOps() == 0, "stopped dispatcher reports no pending");
                    helper.assertTrue(be.runningOps() == 0, "stopped dispatcher reports nothing running");
                })
                .thenSucceed();
    }

    // Helpers

    private static MainframeBlockEntity placeRunningMainframe(final GameTestHelper helper, final BlockPos relative) {
        helper.setBlock(relative, ComputingModule.MAINFRAME.get());
        final MainframeBlockEntity be = mainframeAt(helper, relative);
        installValidBuild(be);
        be.togglePower(); // valid build never powers on by itself
        return be;
    }

    private static MainframeBlockEntity mainframeAt(final GameTestHelper helper, final BlockPos relative) {
        if (helper.getBlockEntity(relative) instanceof MainframeBlockEntity be) {
            return be;
        }
        throw new IllegalStateException("no mainframe at " + relative);
    }

    private static void installValidBuild(final MainframeBlockEntity be) {
        final ItemStackHandler inv = be.getInventory();
        inv.setStackInSlot(MainframeBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(ComputingModule.MOTHERBOARD_MTX_P.get()));
        inv.setStackInSlot(MainframeBlockEntity.CPU_SLOTS_START,
                new ItemStack(ComputingModule.CPU_SERVO_2620.get()));
        inv.setStackInSlot(MainframeBlockEntity.RAM_SLOTS_START,
                new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        inv.setStackInSlot(MainframeBlockEntity.PSU_SLOT,
                new ItemStack(ComputingModule.PSU_650G.get()));
    }

    private static Optional<NetworkUuid> networkOf(final GameTestHelper helper, final BlockPos relative) {
        final ServerLevel level = helper.getLevel();
        return NetworkSystem.get(level).connectivity().networkOf(helper.absolutePos(relative).asLong());
    }

    private static boolean sameNetwork(final GameTestHelper helper, final BlockPos a, final BlockPos b) {
        final ServerLevel level = helper.getLevel();
        return NetworkSystem.get(level).connectivity().inSameNetwork(
                helper.absolutePos(a).asLong(), helper.absolutePos(b).asLong());
    }
}
