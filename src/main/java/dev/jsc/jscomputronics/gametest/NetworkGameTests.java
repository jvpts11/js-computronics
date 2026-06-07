/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.gametest;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.common.hardware.DiskSize;
import dev.jsc.jscomputronics.common.hardware.StorageTier;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.operation.OperationPriority;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.MainframeBlock;
import dev.jsc.jscomputronics.module.computing.block.MainframePartBlock;
import dev.jsc.jscomputronics.module.computing.block.MainframeStructure;
import dev.jsc.jscomputronics.module.computing.block.MonitorBlock;
import dev.jsc.jscomputronics.module.computing.block.ServerRackBlock;
import dev.jsc.jscomputronics.module.computing.block.ServerRackPartBlock;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MonitorBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.PersonalComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.storage.ServerStore;
import dev.jsc.jscomputronics.module.computing.operation.NetworkInsertFromTerminalOperationTask;
import dev.jsc.jscomputronics.module.computing.operation.NetworkInsertOperationTask;
import dev.jsc.jscomputronics.module.computing.operation.NetworkSelectToStorageOperationTask;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
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
        helper.setBlock(cable, ComputingModule.HBW_CABLE.get());
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
        helper.setBlock(cable, ComputingModule.HBW_CABLE.get());
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
        helper.setBlock(cable, ComputingModule.HBW_CABLE.get());
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
        helper.setBlock(c1, ComputingModule.HBW_CABLE.get());
        helper.setBlock(c2, ComputingModule.HBW_CABLE.get());
        helper.setBlock(c3, ComputingModule.HBW_CABLE.get());
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

    @GameTest(template = ARENA)
    public static void mainframeDestroyed_erasesNetworkFromCables(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos c1 = new BlockPos(2, 2, 2);
        final BlockPos c2 = new BlockPos(3, 2, 2);
        placeRunningMainframe(helper, m);
        helper.setBlock(c1, ComputingModule.HBW_CABLE.get());
        helper.setBlock(c2, ComputingModule.HBW_CABLE.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(networkOf(helper, c1).isPresent(),
                            "cables should carry the mainframe's network while it runs");
                    helper.assertTrue(networkOf(helper, c2).isPresent(),
                            "the whole cable run should be networked");
                })
                .thenExecute(() -> helper.setBlock(m, Blocks.AIR)) // destroy the mainframe
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(networkOf(helper, c1).isEmpty(),
                            "destroying the mainframe must erase the network from its cables");
                    helper.assertTrue(networkOf(helper, c2).isEmpty(),
                            "no cable may keep a network once its only mainframe is gone");
                })
                .thenSucceed();
    }

    // Personal Router (Ethernet <-> HBW bridge)

    @GameTest(template = ARENA)
    public static void personalRouter_bridgesEthernetAndHbw(final GameTestHelper helper) {
        final BlockPos eth = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos hbw = new BlockPos(4, 2, 2);
        helper.setBlock(eth, ComputingModule.ETHERNET_CABLE.get());
        helper.setBlock(router, ComputingModule.PERSONAL_ROUTER.get());
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> helper.assertTrue(sameNetwork(helper, eth, hbw),
                        "Ethernet and HBW should share one network through the router"))
                .thenExecute(() -> helper.setBlock(router, Blocks.AIR)) // remove the bridge
                .thenExecuteAfter(SETTLE, () -> helper.assertFalse(sameNetwork(helper, eth, hbw),
                        "removing the router must split Ethernet from HBW"))
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void ethernetAndHbw_doNotJoinDirectly(final GameTestHelper helper) {
        final BlockPos eth = new BlockPos(2, 2, 2);
        final BlockPos hbw = new BlockPos(3, 2, 2);
        helper.setBlock(eth, ComputingModule.ETHERNET_CABLE.get());
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> helper.assertFalse(sameNetwork(helper, eth, hbw),
                        "different cable tiers must not join without a router"))
                .thenSucceed();
    }

    // Personal Computer (assembly + passive network membership)

    @GameTest(template = ARENA)
    public static void personalComputer_assemblesAndPowers(final GameTestHelper helper) {
        final BlockPos pc = new BlockPos(2, 2, 2);
        final PersonalComputerBlockEntity computer = placeRunningPC(helper, pc);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(computer.buildValid(), "ATX build should be valid");
                    helper.assertTrue(computer.isRunning(), "PC should be running after power-on");
                    helper.assertTrue(computer.capacity() > 0, "running PC reports capacity");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void personalComputer_joinsMainframeNetworkThroughRouter(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos eth = new BlockPos(4, 2, 2);
        final BlockPos pc = new BlockPos(5, 2, 2);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(router, ComputingModule.PERSONAL_ROUTER.get());
        helper.setBlock(eth, ComputingModule.ETHERNET_CABLE.get());
        final PersonalComputerBlockEntity computer = placeRunningPC(helper, pc);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(computer.networkUuid() != null, "PC should be on a network");
                    helper.assertTrue(mainframe.networkUuid() != null, "mainframe should own a network");
                    helper.assertTrue(computer.networkUuid().equals(mainframe.networkUuid()),
                            "PC must share the mainframe's network through the router");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void personalComputer_ignoresHbwCable(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos pc = new BlockPos(3, 2, 2); // PC directly against an HBW cable
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        final PersonalComputerBlockEntity computer = placeRunningPC(helper, pc);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(mainframe.networkUuid() != null, "mainframe owns its network");
                    helper.assertTrue(computer.networkUuid() == null,
                            "a PC must not join via an HBW cable (Ethernet only)");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void mainframe_ignoresEthernetCable(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(2, 2, 2);
        final BlockPos eth = new BlockPos(3, 2, 2); // Mainframe directly against an Ethernet cable
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(eth, ComputingModule.ETHERNET_CABLE.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(mainframe.networkUuid() != null, "mainframe owns its native network");
                    helper.assertTrue(networkOf(helper, eth).isEmpty(),
                            "a Mainframe must not assign its UUID to an Ethernet cable");
                })
                .thenSucceed();
    }

    // Mainframe multiblock (3x2x2 self-assembly)

    @GameTest(template = ARENA)
    public static void mainframe_formsAndDissolves(final GameTestHelper helper) {
        final BlockPos controller = new BlockPos(4, 2, 4);
        final Direction facing = Direction.NORTH;
        helper.setBlock(controller, ComputingModule.MAINFRAME.get().defaultBlockState()
                .setValue(MainframeBlock.FACING, facing));
        // Drive the self-assembly the way item placement would.
        ((MainframeBlock) ComputingModule.MAINFRAME.get()).setPlacedBy(
                helper.getLevel(), helper.absolutePos(controller),
                helper.getBlockState(controller), null, ItemStack.EMPTY);

        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    int count = 0;
                    for (final BlockPos p : MainframeStructure.allPositions(controller, facing)) {
                        final var block = helper.getBlockState(p).getBlock();
                        if (block instanceof MainframeBlock || block instanceof MainframePartBlock) {
                            count++;
                        }
                    }
                    helper.assertTrue(count == MainframeStructure.BLOCK_COUNT,
                            "the 3x2x2 footprint should hold " + MainframeStructure.BLOCK_COUNT
                                    + " blocks, found " + count);
                })
                .thenExecute(() -> helper.setBlock(
                        MainframeStructure.partPositions(controller, facing).get(0), Blocks.AIR))
                .thenExecuteAfter(2, () -> {
                    int remaining = 0;
                    for (final BlockPos p : MainframeStructure.allPositions(controller, facing)) {
                        final var block = helper.getBlockState(p).getBlock();
                        if (block instanceof MainframeBlock || block instanceof MainframePartBlock) {
                            remaining++;
                        }
                    }
                    helper.assertTrue(remaining == 0,
                            "breaking one part must dissolve the whole structure, " + remaining + " left");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void mainframe_readsCableOnAnyPartFace(final GameTestHelper helper) {
        final BlockPos controller = new BlockPos(4, 2, 4);
        final Direction facing = Direction.NORTH;
        final MainframeBlockEntity be = formRunningMainframe(helper, controller, facing);
        // The far-right part sits two blocks from the controller; a cable on its
        // outward face is never adjacent to the controller itself.
        final BlockPos farPart = controller.relative(facing.getClockWise());
        final BlockPos cable = farPart.relative(facing.getClockWise());
        helper.setBlock(cable, ComputingModule.HBW_CABLE.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(be.networkUuid() != null, "controller must own a network");
                    helper.assertTrue(networkOf(helper, cable).map(be.networkUuid()::equals).orElse(false),
                            "a cable on a part face must carry the controller's network UUID");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void mainframe_centralColumnGetsCoreFace(final GameTestHelper helper) {
        final BlockPos controller = new BlockPos(4, 2, 4);
        final Direction facing = Direction.NORTH;
        formRunningMainframe(helper, controller, facing);
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    for (final BlockPos p : MainframeStructure.partPositions(controller, facing)) {
                        final boolean expected = MainframeStructure.isCentralColumn(controller, facing, p);
                        final var st = helper.getBlockState(p);
                        helper.assertTrue(st.getBlock() instanceof MainframePartBlock, "part missing at " + p);
                        helper.assertTrue(st.getValue(MainframePartBlock.CORE) == expected,
                                "core flag at " + p + " should be " + expected);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void mainframe_teardownDropsNothing(final GameTestHelper helper) {
        final BlockPos controller = new BlockPos(4, 2, 4);
        final Direction facing = Direction.NORTH;
        formRunningMainframe(helper, controller, facing); // installs a full hardware build
        helper.startSequence()
                .thenExecuteAfter(2, () -> helper.setBlock(
                        MainframeStructure.partPositions(controller, facing).get(0), Blocks.AIR))
                .thenExecuteAfter(2, () -> helper.assertTrue(droppedItems(helper, controller) == 0,
                        "dissolving the multiblock must not drop items (creative-safe teardown)"))
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void mainframe_buildPicksUpInstalledDisk(final GameTestHelper helper) {
        final BlockPos a = new BlockPos(2, 2, 2);
        final MainframeBlockEntity be = placeRunningMainframe(helper, a);
        be.getInventory().setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1)));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> helper.assertTrue(
                        be.storageItems() == DiskSize.TB_1.capacityItems(),
                        "build should report the installed disk's capacity; got " + be.storageItems()))
                .thenSucceed();
    }

    // Server Rack (Servers register as Category-C nodes on the network)

    @GameTest(template = ARENA)
    public static void serverRack_registersAndUnregistersServer(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos rack = new BlockPos(3, 2, 2);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get());
        if (helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe) {
            rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        } else {
            helper.fail("no server rack block entity placed");
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final NetworkUuid net = mainframe.networkUuid();
                    helper.assertTrue(net != null, "mainframe owns a network");
                    final var servers = NetworkSystem.get(helper.getLevel()).serversOf(net);
                    helper.assertTrue(servers.size() == 1,
                            "the rack's Server should register on the mainframe network; got " + servers.size());
                    helper.assertTrue(servers.get(0).storageMB() > 0,
                            "registered Server should report its disk storage");
                })
                .thenExecute(() -> helper.setBlock(rack, Blocks.AIR)) // break the rack
                .thenExecuteAfter(SETTLE, () -> {
                    final NetworkUuid net = mainframe.networkUuid();
                    helper.assertTrue(NetworkSystem.get(helper.getLevel()).serversOf(net).isEmpty(),
                            "breaking the rack must unregister its Server");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void serverRack_formsCabinetAndReadsCableOnPartFace(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos rack = new BlockPos(4, 2, 2);     // controller; footprint x[4,5] y[2,4] z[2,3]
        final BlockPos part = new BlockPos(4, 3, 2);     // a front part, one up from the controller
        // A cable run from the part's outward face to the mainframe — it touches the
        final BlockPos[] cables = {
            new BlockPos(3, 3, 2), // against the part's west face
            new BlockPos(2, 3, 2),
            new BlockPos(2, 2, 2), // claimed by the mainframe
        };
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        for (final BlockPos c : cables) {
            helper.setBlock(c, ComputingModule.HBW_CABLE.get());
        }
        // Place the controller and drive its self-assembly so all 11 parts exist.
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get());
        ((ServerRackBlock) ComputingModule.SERVER_RACK.get()).setPlacedBy(
                helper.getLevel(), helper.absolutePos(rack), helper.getBlockState(rack), null, ItemStack.EMPTY);
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(helper.getBlockState(part).getBlock() instanceof ServerRackPartBlock,
                            "placing the rack must raise its structural parts");
                    final NetworkUuid net = mainframe.networkUuid();
                    helper.assertTrue(net != null, "mainframe owns a network");
                    // The controller touches no cable; only a part does.
                    helper.assertTrue(NetworkSystem.get(helper.getLevel()).serversOf(net).size() == 1,
                            "a cable on a rack PART face must join the rack to the network");
                })
                .thenExecute(() -> helper.setBlock(part, Blocks.AIR)) // break one part
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(helper.getBlockState(rack).isAir(),
                            "breaking a part must take the controller down too");
                    helper.assertTrue(helper.getBlockState(part).isAir(),
                            "the broken part must be gone");
                    helper.assertTrue(helper.getBlockState(new BlockPos(5, 4, 3)).isAir(),
                            "the whole cabinet must dissolve, including the far-top-back corner");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void networkStorage_queriesSelectsAndInserts(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos rack = new BlockPos(3, 2, 2);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get());
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final NetworkUuid net = mainframe.networkUuid();
                    helper.assertTrue(net != null, "mainframe owns a network");

                    // Seed the Server with 100 cobblestone.
                    final ServerStore store = rackBe.getServerStorage(0);
                    store.insert(Items.COBBLESTONE, 100);

                    final NetworkStorage ns = NetworkStorage.of(helper.getLevel(), net);
                    helper.assertTrue(ns.count(Items.COBBLESTONE) == 100,
                            "QUERY: network holds 100 cobblestone; got "
                                    + ns.count(Items.COBBLESTONE));

                    // SELECT 30 into a destination handler.
                    final ItemStackHandler dest = new ItemStackHandler(9);
                    final long moved = ns.select(Items.COBBLESTONE, 30, dest);
                    helper.assertTrue(moved == 30, "SELECT should move 30; got " + moved);
                    helper.assertTrue(ns.count(Items.COBBLESTONE) == 70,
                            "network should have 70 after SELECT");

                    // INSERT 10 back.
                    final int inserted = ns.insert(new ItemStack(Items.COBBLESTONE, 10));
                    helper.assertTrue(inserted == 10, "INSERT should store 10; got " + inserted);
                    helper.assertTrue(ns.count(Items.COBBLESTONE) == 80,
                            "network should have 80 after INSERT");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void networkStorage_capsAtDiskCapacity(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos rack = new BlockPos(3, 2, 2);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get());
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, smallDiskServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final NetworkUuid net = mainframe.networkUuid();
                    helper.assertTrue(net != null, "mainframe owns a network");
                    final NetworkStorage ns = NetworkStorage.of(helper.getLevel(), net);
                    final int stored = ns.insert(new ItemStack(Items.COBBLESTONE, 572));
                    helper.assertTrue(stored == 50,
                            "INSERT must cap at the 50-item disk capacity; stored " + stored);
                    helper.assertTrue(ns.count(Items.COBBLESTONE) == 50,
                            "network should hold exactly 50; got " + ns.count(Items.COBBLESTONE));
                })
                .thenSucceed();
    }

    private static ItemStack smallDiskServer() {
        final net.minecraft.core.NonNullList<ItemStack> hw = net.minecraft.core.NonNullList.withSize(
                dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.SLOTS, ItemStack.EMPTY);
        hw.set(dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.MOBO,
                new ItemStack(ComputingModule.MOTHERBOARD_EEB_P.get()));
        hw.set(dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.CPU_START,
                new ItemStack(ComputingModule.CPU_SERVO_2620.get()));
        hw.set(dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.RAM_START,
                new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        hw.set(dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.PSU,
                new ItemStack(ComputingModule.PSU_650G.get()));
        hw.set(dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.DISK_START,
                new ItemStack(ComputingModule.disk(StorageTier.SSD, DiskSize.MB_200)));
        final ItemStack server = new ItemStack(ComputingModule.SERVER.get());
        server.set(ComputingModule.SERVER_HARDWARE.get(),
                net.minecraft.world.item.component.ItemContainerContents.fromItems(hw));
        return server;
    }

    @GameTest(template = ARENA)
    public static void networkOperation_insertDispatchedByMainframe(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos rack = new BlockPos(3, 2, 2);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get());
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final NetworkUuid net = mainframe.networkUuid();
                    helper.assertTrue(net != null, "mainframe owns a network");
                    final boolean accepted = mainframe.submitOperation(
                            new NetworkInsertOperationTask(helper.getLevel(), net,
                                    new ItemStack(Items.COBBLESTONE, 40), null),
                            OperationPriority.MEDIUM);
                    helper.assertTrue(accepted, "Mainframe should accept the INSERT Operation");
                })
                // The dispatcher needs a couple ticks: run the task on a virtual
                // thread, then drain its main-thread item move on a later tick.
                .thenExecuteAfter(8, () -> {
                    final NetworkStorage ns = NetworkStorage.of(helper.getLevel(), mainframe.networkUuid());
                    helper.assertTrue(ns.count(Items.COBBLESTONE) == 40,
                            "INSERT Operation should have stored 40 via the dispatcher; got "
                                    + ns.count(Items.COBBLESTONE));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void personalComputer_selectsItemsFromNetwork(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos eth = new BlockPos(4, 2, 2);
        final BlockPos pc = new BlockPos(5, 2, 2);
        final BlockPos rack = new BlockPos(2, 3, 2);
        placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(router, ComputingModule.PERSONAL_ROUTER.get());
        helper.setBlock(eth, ComputingModule.ETHERNET_CABLE.get());
        final PersonalComputerBlockEntity computer = placeRunningPC(helper, pc);
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get());
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    // Seed the Server with 200 cobblestone.
                    final ServerStore store = rackBe.getServerStorage(0);
                    store.insert(Items.COBBLESTONE, 200);

                    helper.assertTrue(computer.networkUuid() != null, "PC must be on the network");
                    // The PC resolves the SAME network as the server's Rack across the Router —
                    final NetworkUuid net = computer.networkUuid();
                    final NetworkStorage ns = NetworkStorage.of(helper.getLevel(), net);
                    helper.assertTrue(ns.count(Items.COBBLESTONE) == 200,
                            "the PC's network should see the server's 200 cobblestone; got "
                                    + ns.count(Items.COBBLESTONE));
                    final ItemStackHandler dest = new ItemStackHandler(9);
                    final long moved = ns.select(Items.COBBLESTONE, 100, dest);
                    helper.assertTrue(moved == 100, "SELECT should move 100 via the PC's network; got " + moved);
                    helper.assertTrue(ns.count(Items.COBBLESTONE) == 100,
                            "network should have 100 left after SELECT");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void terminalSelect_landsInPcLocalStorage(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos eth = new BlockPos(4, 2, 2);
        final BlockPos pc = new BlockPos(5, 2, 2);
        final BlockPos rack = new BlockPos(2, 3, 2);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(router, ComputingModule.PERSONAL_ROUTER.get());
        helper.setBlock(eth, ComputingModule.ETHERNET_CABLE.get());
        final PersonalComputerBlockEntity computer = placeRunningPC(helper, pc);
        // A disk gives the PC local storage, so a SELECT lands there.
        computer.getHardware().setStackInSlot(PersonalComputerBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1)));
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get());
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 200);
                    final NetworkUuid net = computer.networkUuid();
                    helper.assertTrue(net != null, "PC must be on the network");
                    final boolean accepted = mainframe.submitOperation(
                            new NetworkSelectToStorageOperationTask(helper.getLevel(), net, Items.COBBLESTONE, 50,
                                    helper.absolutePos(pc), null, null),
                            OperationPriority.MEDIUM);
                    helper.assertTrue(accepted, "Mainframe should accept the SELECT-to-storage Operation");
                })
                .thenExecuteAfter(8, () -> {
                    long inStorage = 0L;
                    final ItemStackHandler store = computer.getStorage();
                    for (int i = 0; i < store.getSlots(); i++) {
                        if (store.getStackInSlot(i).is(Items.COBBLESTONE)) {
                            inStorage += store.getStackInSlot(i).getCount();
                        }
                    }
                    helper.assertTrue(inStorage == 50,
                            "SELECT must land 50 cobblestone in the PC's local storage; got " + inStorage);
                    // The Operation is logged with provenance for the Operations tab.
                    final var log = mainframe.recentOperations();
                    helper.assertTrue(log.size() == 1, "the SELECT should be logged once; got " + log.size());
                    helper.assertTrue(log.get(0).moved() == 50,
                            "logged op should record 50 moved; got " + log.get(0).moved());
                    helper.assertTrue(!log.get(0).moves().isEmpty(),
                            "logged op should carry provenance moves (from which server)");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void terminalInsert_movesIntoNetwork(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos rack = new BlockPos(2, 3, 2);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get());
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    final NetworkUuid net = mainframe.networkUuid();
                    helper.assertTrue(net != null, "mainframe must own a network");
                    final boolean accepted = mainframe.submitOperation(
                            new NetworkInsertFromTerminalOperationTask(helper.getLevel(), net,
                                    new ItemStack(Items.COBBLESTONE, 64), null),
                            OperationPriority.MEDIUM);
                    helper.assertTrue(accepted, "Mainframe should accept the terminal INSERT Operation");
                })
                .thenExecuteAfter(8, () -> {
                    final NetworkUuid net = mainframe.networkUuid();
                    final long held = NetworkStorage.of(helper.getLevel(), net).count(Items.COBBLESTONE);
                    helper.assertTrue(held == 64,
                            "INSERT must deposit 64 cobblestone into the network; got " + held);
                    final var log = mainframe.recentOperations();
                    helper.assertTrue(log.size() == 1, "the INSERT should be logged once; got " + log.size());
                    helper.assertTrue(log.get(0).type() == OperationRecord.TYPE_INSERT,
                            "logged op should be an INSERT");
                    helper.assertTrue(log.get(0).moved() == 64,
                            "logged op should record 64 moved; got " + log.get(0).moved());
                    helper.assertTrue(!log.get(0).moves().isEmpty(),
                            "logged op should carry provenance moves (to which server)");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void operationLog_persistsAcrossReload(final GameTestHelper helper) {
        final BlockPos a = new BlockPos(2, 2, 2);
        final MainframeBlockEntity be = placeRunningMainframe(helper, a);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    be.recordOperation(OperationRecord.TYPE_INSERT, new ItemStack(Items.COBBLESTONE), 64, 64,
                            OperationRecord.STATUS_COMPLETED,
                            java.util.List.of(new OperationRecord.MoveRow("you", 64, "SRV-abc123")));
                    helper.assertTrue(be.recentOperations().size() == 1, "one op should be recorded");

                    final var registries = helper.getLevel().registryAccess();
                    final net.minecraft.nbt.CompoundTag saved = be.saveWithFullMetadata(registries);
                    final var reloaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                            helper.absolutePos(a), be.getBlockState(), saved, registries);
                    helper.assertTrue(reloaded instanceof MainframeBlockEntity, "reloaded BE should be a Mainframe");

                    final var log = ((MainframeBlockEntity) reloaded).recentOperations();
                    helper.assertTrue(log.size() == 1, "the op must survive reload; got " + log.size());
                    final OperationRecord rec = log.get(0);
                    helper.assertTrue(rec.type() == OperationRecord.TYPE_INSERT, "type must persist");
                    helper.assertTrue(rec.moved() == 64, "moved must persist; got " + rec.moved());
                    helper.assertTrue(rec.icon().is(Items.COBBLESTONE), "icon item must persist");
                    helper.assertTrue(!rec.moves().isEmpty() && rec.moves().get(0).to().equals("SRV-abc123"),
                            "provenance must persist");
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

    private static MainframeBlockEntity formRunningMainframe(final GameTestHelper helper,
                                                            final BlockPos controller, final Direction facing) {
        helper.setBlock(controller, ComputingModule.MAINFRAME.get().defaultBlockState()
                .setValue(MainframeBlock.FACING, facing));
        ((MainframeBlock) ComputingModule.MAINFRAME.get()).setPlacedBy(
                helper.getLevel(), helper.absolutePos(controller),
                helper.getBlockState(controller), null, ItemStack.EMPTY);
        final MainframeBlockEntity be = mainframeAt(helper, controller);
        installValidBuild(be);
        be.togglePower();
        return be;
    }

    private static MainframeBlockEntity mainframeAt(final GameTestHelper helper, final BlockPos relative) {
        if (helper.getBlockEntity(relative) instanceof MainframeBlockEntity be) {
            return be;
        }
        throw new IllegalStateException("no mainframe at " + relative);
    }

    @GameTest(template = ARENA)
    public static void monitor_autoLinksAndUnlinksOnCableBreak(final GameTestHelper helper) {
        final BlockPos pc = new BlockPos(1, 2, 2);
        final BlockPos cable = new BlockPos(2, 2, 2);
        final BlockPos mon = new BlockPos(3, 2, 2);
        final PersonalComputerBlockEntity computer = placeRunningPC(helper, pc);
        // A GPU lets the computer host up to 4 monitors.
        computer.getHardware().setStackInSlot(PersonalComputerBlockEntity.GPU_SLOTS_START,
                new ItemStack(ComputingModule.GPU_HD_7970.get()));
        helper.setBlock(cable, ComputingModule.PERIPHERAL_CABLE.get());
        helper.setBlock(mon, ComputingModule.MONITOR.get());
        if (!(helper.getBlockEntity(mon) instanceof MonitorBlockEntity monitor)) {
            helper.fail("no monitor");
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(25, () -> {
                    helper.assertTrue(monitor.ownerPos() != null
                                    && monitor.ownerPos().equals(helper.absolutePos(pc)),
                            "monitor should auto-link to the PC over the peripheral cable");
                    helper.assertTrue(computer.linkedEndpoints().contains(helper.absolutePos(mon).asLong()),
                            "PC should list the monitor as a linked endpoint");
                })
                .thenExecute(() -> helper.setBlock(cable, Blocks.AIR))
                .thenExecuteAfter(25, () -> {
                    helper.assertTrue(monitor.ownerPos() == null,
                            "monitor should unlink when the peripheral cable is cut");
                    helper.assertTrue(computer.linkedEndpoints().isEmpty(),
                            "PC should drop the endpoint after the cable is cut");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void monitor_screenLightsUpAfterLinkingAndDarkensOnCut(final GameTestHelper helper) {
        final BlockPos pc = new BlockPos(1, 2, 2);
        final BlockPos cable = new BlockPos(2, 2, 2);
        final BlockPos mon = new BlockPos(3, 2, 2);
        final PersonalComputerBlockEntity computer = placeRunningPC(helper, pc);
        computer.getHardware().setStackInSlot(PersonalComputerBlockEntity.GPU_SLOTS_START,
                new ItemStack(ComputingModule.GPU_HD_7970.get()));
        helper.setBlock(cable, ComputingModule.PERIPHERAL_CABLE.get());
        helper.setBlock(mon, ComputingModule.MONITOR.get());
        helper.startSequence()
                // The link is near-instant; the screen boots ~20 ticks later, so by 30 ticks it is lit.
                .thenExecuteAfter(30, () -> helper.assertTrue(
                        helper.getBlockState(mon).getValue(MonitorBlock.LIT),
                        "monitor screen should be lit after the boot delay once linked"))
                .thenExecute(() -> helper.setBlock(cable, Blocks.AIR))
                .thenExecuteAfter(SETTLE, () -> helper.assertFalse(
                        helper.getBlockState(mon).getValue(MonitorBlock.LIT),
                        "monitor screen should darken the moment the link is cut"))
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void monitor_linksThroughMainframePartFace(final GameTestHelper helper) {
        final BlockPos controller = new BlockPos(3, 2, 3);
        final Direction facing = Direction.NORTH;
        final MainframeBlockEntity be = formRunningMainframe(helper, controller, facing);
        // A GPU lets the Mainframe host monitors (maxEndpoints = GPUs * 4).
        be.getInventory().setStackInSlot(MainframeBlockEntity.GPU_SLOTS_START,
                new ItemStack(ComputingModule.GPU_HD_7970.get()));
        // A cable on the far side-column part's outward face never touches the controller.
        final BlockPos farPart = controller.relative(facing.getClockWise());
        final BlockPos cable = farPart.relative(facing.getClockWise());
        final BlockPos mon = cable.relative(facing.getClockWise());
        helper.setBlock(cable, ComputingModule.PERIPHERAL_CABLE.get());
        helper.setBlock(mon, ComputingModule.MONITOR.get());
        if (!(helper.getBlockEntity(mon) instanceof MonitorBlockEntity monitor)) {
            helper.fail("no monitor");
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> helper.assertTrue(
                        monitor.ownerPos() != null
                                && monitor.ownerPos().equals(helper.absolutePos(controller)),
                        "monitor must link to the Mainframe through a cable on a PART face"))
                .thenSucceed();
    }

    private static PersonalComputerBlockEntity placeRunningPC(final GameTestHelper helper, final BlockPos relative) {
        helper.setBlock(relative, ComputingModule.PERSONAL_COMPUTER.get());
        if (!(helper.getBlockEntity(relative) instanceof PersonalComputerBlockEntity be)) {
            throw new IllegalStateException("no personal computer at " + relative);
        }
        final ItemStackHandler hw = be.getHardware();
        hw.setStackInSlot(PersonalComputerBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(ComputingModule.MOTHERBOARD_ATX_P.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.CPU_SLOT,
                new ItemStack(ComputingModule.CPU_ASCENT_965.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.RAM_SLOTS_START,
                new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.PSU_SLOT,
                new ItemStack(ComputingModule.PSU_650G.get()));
        be.togglePower();
        return be;
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

    private static int droppedItems(final GameTestHelper helper, final BlockPos around) {
        final net.minecraft.world.phys.AABB box =
                new net.minecraft.world.phys.AABB(helper.absolutePos(around)).inflate(6.0);
        return helper.getLevel().getEntitiesOfClass(
                net.minecraft.world.entity.item.ItemEntity.class, box).size();
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
