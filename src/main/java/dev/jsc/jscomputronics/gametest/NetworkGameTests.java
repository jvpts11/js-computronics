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
import dev.jsc.jscomputronics.common.network.FailoverRole;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.persistence.NetworkRegistrySavedData;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NetworkUuidState;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.MainframeBlock;
import dev.jsc.jscomputronics.module.computing.block.MainframePartBlock;
import dev.jsc.jscomputronics.module.computing.block.MainframeStructure;
import dev.jsc.jscomputronics.module.computing.block.MonitorBlock;
import dev.jsc.jscomputronics.module.computing.block.ServerRackBlock;
import dev.jsc.jscomputronics.module.computing.block.ServerRackPartBlock;
import dev.jsc.jscomputronics.module.computing.block.ServerRackStructure;
import dev.jsc.jscomputronics.module.computing.block.SupercomputerNodeBlock;
import dev.jsc.jscomputronics.module.computing.block.SupercomputerNodePartBlock;
import dev.jsc.jscomputronics.module.computing.blockentity.SupercomputerNodeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MonitorBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.PersonalComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.DatacenterStationBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRouterBlockEntity;
import dev.jsc.jscomputronics.module.computing.datacenter.DatacenterSection;
import dev.jsc.jscomputronics.module.computing.datacenter.LoadBalanceMode;
import dev.jsc.jscomputronics.module.computing.datacenter.LoadBalancer;
import dev.jsc.jscomputronics.module.computing.storage.ServerStore;
import dev.jsc.jscomputronics.module.computing.block.part.ExportBusPart;
import dev.jsc.jscomputronics.module.computing.block.part.ImportBusPart;
import dev.jsc.jscomputronics.module.computing.blockentity.DataCableBlockEntity;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord;
import dev.jsc.jscomputronics.testkit.TestWorldBuilder;
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
    public static void cablePlaced_extendsNetworkUuid(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos c1 = new BlockPos(2, 2, 2);
        final BlockPos c2 = new BlockPos(3, 2, 2);
        final BlockPos c3 = new BlockPos(4, 2, 2);
        placeRunningMainframe(helper, m);
        helper.setBlock(c1, ComputingModule.HBW_CABLE.get());
        helper.setBlock(c2, ComputingModule.HBW_CABLE.get());
        final NetworkUuid[] uuid = new NetworkUuid[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(networkOf(helper, c2).isPresent(), "c2 is networked before extending");
                    uuid[0] = networkOf(helper, c2).orElseThrow();
                    helper.assertTrue(networkOf(helper, c3).isEmpty(), "c3 is not placed yet");
                })
                .thenExecute(() -> helper.setBlock(c3, ComputingModule.HBW_CABLE.get())) // extend at runtime
                .thenExecuteAfter(SETTLE, () ->
                        helper.assertTrue(networkOf(helper, c3).equals(Optional.of(uuid[0])),
                                "a cable placed onto a live network joins it and inherits the UUID"))
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void cableReplaced_rejoinsSeveredFragment(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos c1 = new BlockPos(2, 2, 2);
        final BlockPos c2 = new BlockPos(3, 2, 2);
        final BlockPos c3 = new BlockPos(4, 2, 2);
        placeRunningMainframe(helper, m);
        helper.setBlock(c1, ComputingModule.HBW_CABLE.get());
        helper.setBlock(c2, ComputingModule.HBW_CABLE.get());
        helper.setBlock(c3, ComputingModule.HBW_CABLE.get());
        final NetworkUuid[] uuid = new NetworkUuid[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> uuid[0] = networkOf(helper, c3).orElseThrow())
                .thenExecute(() -> helper.setBlock(c2, Blocks.AIR)) // sever -> c3 fragment goes network-less
                .thenExecuteAfter(SETTLE, () ->
                        helper.assertTrue(networkOf(helper, c3).isEmpty(), "severed far cable loses the network"))
                .thenExecute(() -> helper.setBlock(c2, ComputingModule.HBW_CABLE.get())) // re-place the bridge
                .thenExecuteAfter(SETTLE, () ->
                        helper.assertTrue(networkOf(helper, c3).equals(Optional.of(uuid[0])),
                                "re-placing the cable rejoins the fragment and restores its UUID"))
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void mainframeDestroyed_orphansNetworkAndReAdopts(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos c1 = new BlockPos(2, 2, 2);
        final BlockPos c2 = new BlockPos(3, 2, 2);
        placeRunningMainframe(helper, m);
        helper.setBlock(c1, ComputingModule.HBW_CABLE.get());
        helper.setBlock(c2, ComputingModule.HBW_CABLE.get());
        final NetworkUuid[] uuid = new NetworkUuid[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final Optional<NetworkUuid> net = networkOf(helper, c1);
                    helper.assertTrue(net.isPresent(),
                            "cables should carry the mainframe's network while it runs");
                    uuid[0] = net.get();
                    helper.assertTrue(registryState(helper, uuid[0]) == NetworkUuidState.ACTIVE,
                            "a running network is ACTIVE");
                })
                .thenExecute(() -> helper.setBlock(m, Blocks.AIR)) // destroy the mainframe
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(networkOf(helper, c1).equals(Optional.of(uuid[0])),
                            "an orphaned network keeps its UUID on the cables, not erased");
                    helper.assertTrue(networkOf(helper, c2).equals(Optional.of(uuid[0])),
                            "the whole orphaned segment keeps the same UUID");
                    helper.assertTrue(registryState(helper, uuid[0]) == NetworkUuidState.ORPHANED,
                            "destroying the only Mainframe marks the network ORPHANED");
                })
                .thenExecute(() -> placeRunningMainframe(helper, m)) // a replacement on the same topology
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(networkOf(helper, c1).equals(Optional.of(uuid[0])),
                            "a new Mainframe re-adopts the orphaned UUID, not a fresh one");
                    helper.assertTrue(registryState(helper, uuid[0]) == NetworkUuidState.ACTIVE,
                            "re-adoption brings the network back to ACTIVE");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 220)
    public static void failover_standbyJoinsPrimaryAndTakesOver(final GameTestHelper helper) {
        final BlockPos primaryPos = new BlockPos(1, 2, 2);
        final BlockPos standbyPos = new BlockPos(5, 2, 2);
        final MainframeBlockEntity primary = placeRunningMainframe(helper, primaryPos); // Failover OFF
        final MainframeBlockEntity standby = placeRunningMainframe(helper, standbyPos);
        standby.toggleFailover(); // ON -> a standby that joins the primary's network
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(4, 2, 2), ComputingModule.HBW_CABLE.get());
        final NetworkUuid[] uuid = new NetworkUuid[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertFalse(primary.hasNetworkConflict(), "primary + standby must not conflict");
                    helper.assertFalse(standby.hasNetworkConflict(), "primary + standby must not conflict");
                    uuid[0] = primary.networkUuid();
                    helper.assertTrue(uuid[0] != null, "the primary owns a network");
                    helper.assertTrue(uuid[0].equals(standby.networkUuid()),
                            "the standby joins the primary's network, not its own");
                    helper.assertTrue(standby.failoverRole() == FailoverRole.PASSIVE,
                            "the standby stands by while the primary runs; got " + standby.failoverRole());
                })
                .thenExecute(() -> helper.setBlock(primaryPos, Blocks.AIR)) // destroy the primary
                // The standby takes over only after the takeover delay (60 ticks); wait it out.
                .thenExecuteAfter(70, () -> {
                    helper.assertTrue(standby.failoverRole() == FailoverRole.ACTIVE,
                            "the standby promotes to run the orphaned network; got " + standby.failoverRole());
                    helper.assertTrue(uuid[0].equals(standby.networkUuid()),
                            "the promoted standby keeps the SAME network UUID, never a fresh one");
                    helper.assertTrue(registryState(helper, uuid[0]) == NetworkUuidState.ACTIVE,
                            "the network is ACTIVE again under the promoted standby");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void failover_enablingErasesOwnNetwork(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos c = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m); // Failover OFF, a primary
        helper.setBlock(c, ComputingModule.HBW_CABLE.get());
        final NetworkUuid[] uuid = new NetworkUuid[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    uuid[0] = networkOf(helper, c).orElse(null);
                    helper.assertTrue(uuid[0] != null, "the primary owns a network on its cable");
                    helper.assertTrue(registryState(helper, uuid[0]) == NetworkUuidState.ACTIVE, "and it is ACTIVE");
                })
                .thenExecute(mainframe::toggleFailover) // become a standby
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(networkOf(helper, c).isEmpty(),
                            "enabling Failover erases the owned network from the cable");
                    helper.assertTrue(mainframe.networkUuid() == null,
                            "a standby with no primary owns no network");
                    helper.assertTrue(registryState(helper, uuid[0]) == null,
                            "the erased network is dropped from the registry");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void failover_lastMemberOutOrphansNotGhostActive(final GameTestHelper helper) {
        final BlockPos primaryPos = new BlockPos(1, 2, 2);
        final BlockPos standbyPos = new BlockPos(5, 2, 2);
        final MainframeBlockEntity primary = placeRunningMainframe(helper, primaryPos);
        final MainframeBlockEntity standby = placeRunningMainframe(helper, standbyPos);
        standby.toggleFailover();
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(4, 2, 2), ComputingModule.HBW_CABLE.get());
        final NetworkUuid[] uuid = new NetworkUuid[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    uuid[0] = primary.networkUuid();
                    helper.assertTrue(uuid[0] != null, "the primary forms a network");
                    helper.assertTrue(registryState(helper, uuid[0]) == NetworkUuidState.ACTIVE, "running network is ACTIVE");
                })
                .thenExecute(() -> helper.setBlock(primaryPos, Blocks.AIR)) // destroy the primary first
                // ...then the standby too, BEFORE it can promote (well under the 60-tick takeover delay)
                .thenExecuteAfter(2, () -> helper.setBlock(standbyPos, Blocks.AIR))
                .thenExecuteAfter(SETTLE, () -> helper.assertTrue(
                        registryState(helper, uuid[0]) == NetworkUuidState.ORPHANED,
                        "with the last Mainframe gone the network must be ORPHANED, not a ghost ACTIVE; got "
                                + registryState(helper, uuid[0])))
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

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void pcPrivateStorage_isInvisibleUntilPublished(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos eth = new BlockPos(4, 2, 2);
        final BlockPos pcPos = new BlockPos(5, 2, 2);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(router, ComputingModule.PERSONAL_ROUTER.get());
        helper.setBlock(eth, ComputingModule.ETHERNET_CABLE.get());
        final PersonalComputerBlockEntity pc = placeRunningPC(helper, pcPos);
        // Install the smallest disk (2000-item capacity) and seed it with 100 cobblestone, all private
        // by default (0 permille). Public storage is a capacity-fraction budget: a permille of 15 on a
        // 2000-item disk publishes a 30-item budget, so 30 of the 100 become public, 70 stay private.
        pc.getHardware().setStackInSlot(PersonalComputerBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.GB_500)));
        final java.util.function.IntConsumer publish = permille ->
                pc.setDiskPrivacy(0, permille);
        final ItemStackHandler dest = new ItemStackHandler(9);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(pc.networkUuid() != null, "the PC should be on the network");
                    helper.assertTrue(pc.networkUuid().equals(mainframe.networkUuid()),
                            "the PC shares the mainframe network");
                    pc.localStore().insert(StorageKey.of(Items.COBBLESTONE), 100);
                })
                // Default-private: the network must not see any of the PC's storage.
                .thenExecuteAfter(4, () -> {
                    helper.assertTrue(mainframe.networkIndex().available(Items.COBBLESTONE) == 0,
                            "a default-private PC disk must be invisible to the index; got "
                                    + mainframe.networkIndex().available(Items.COBBLESTONE));
                    final var op = mainframe.submitNetworkSelect(Items.COBBLESTONE, 100, port(dest), "test");
                    helper.assertTrue(op == null || op.isDone(),
                            "a SELECT against an all-private PC finds nothing to pull");
                })
                .thenExecuteAfter(4, () -> helper.assertTrue(countIn(dest, Items.COBBLESTONE) == 0,
                        "nothing should have moved while the PC is fully private"))
                // Publish a 30-item budget (15 permille of the 2000-item disk): 30 of the 100 become
                // public; the other 70 stay private.
                .thenExecute(() -> publish.accept(15))
                .thenExecuteAfter(4, () -> {
                    helper.assertTrue(mainframe.networkIndex().available(Items.COBBLESTONE) == 30,
                            "publishing a 30-item budget exposes 30 of the 100 to the index; got "
                                    + mainframe.networkIndex().available(Items.COBBLESTONE));
                    mainframe.submitNetworkSelect(Items.COBBLESTONE, 100, port(dest), "test");
                })
                // A SELECT for 100 pulls only the 30 public; the 70 private remain on the PC's disk.
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(countIn(dest, Items.COBBLESTONE) == 30,
                            "only the published 30 are SELECT-able; got " + countIn(dest, Items.COBBLESTONE));
                    helper.assertTrue(pc.localStore().count(StorageKey.of(Items.COBBLESTONE)) == 70,
                            "the private remainder stays on the PC; got "
                                    + pc.localStore().count(StorageKey.of(Items.COBBLESTONE)));
                })
                // The public budget is a standing ceiling: 30 of the remaining 70 are public again, so
                // a repeat pull yields another 30 and never reaches the private floor.
                .thenExecuteAfter(4, () -> helper.assertTrue(
                        mainframe.networkIndex().available(Items.COBBLESTONE) == 30,
                        "the budget re-exposes 30 of the remaining 70; got "
                                + mainframe.networkIndex().available(Items.COBBLESTONE)))
                // Fully private again: no part of the PC is visible, the whole 70 is protected.
                .thenExecute(() -> publish.accept(0))
                .thenExecuteAfter(4, () -> helper.assertTrue(
                        mainframe.networkIndex().available(Items.COBBLESTONE) == 0,
                        "setting the disk back to private hides all of it again; got "
                                + mainframe.networkIndex().available(Items.COBBLESTONE)))
                .thenSucceed();
    }

    private static int countIn(final ItemStackHandler handler, final net.minecraft.world.item.Item item) {
        int total = 0;
        for (int i = 0; i < handler.getSlots(); i++) {
            if (handler.getStackInSlot(i).getItem() == item) {
                total += handler.getStackInSlot(i).getCount();
            }
        }
        return total;
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

    // Era Personal Computers (Vintage / Legacy) — the right-click assembly GUI must open exactly
    // like the Standard one. The three blocks share PersonalComputerBlockEntity, the menu and the
    // screen; the era blocks only override era() and codec(). These tests open the menu through the
    // real interaction path and verify the server keeps it open (stillValid), which is what actually
    // decides whether the player sees the GUI.

    @GameTest(template = ARENA)
    public static void standardPc_rightClickOpensAndKeepsMenu(final GameTestHelper helper) {
        assertPcMenuOpens(helper, ComputingModule.PERSONAL_COMPUTER.get(),
                ComputingModule.PERSONAL_COMPUTER_ITEM.get());
    }

    @GameTest(template = ARENA)
    public static void vintagePc_rightClickOpensAndKeepsMenu(final GameTestHelper helper) {
        assertPcMenuOpens(helper, ComputingModule.VINTAGE_PERSONAL_COMPUTER.get(),
                ComputingModule.VINTAGE_PERSONAL_COMPUTER_ITEM.get());
    }

    @GameTest(template = ARENA)
    public static void legacyPc_rightClickOpensAndKeepsMenu(final GameTestHelper helper) {
        assertPcMenuOpens(helper, ComputingModule.LEGACY_PERSONAL_COMPUTER.get(),
                ComputingModule.LEGACY_PERSONAL_COMPUTER_ITEM.get());
    }

    /**
     * Places the given Personal Computer block, verifies its block entity and menu construction, then
     * checks the very test the server runs each tick to decide whether to keep the GUI open:
     * {@link net.minecraft.world.inventory.AbstractContainerMenu#stillValid}. A menu whose
     * {@code stillValid} returns false is closed by the server on the next tick, which is exactly the
     * "the GUI never opens" symptom for the player.
     */
    private static void assertPcMenuOpens(final GameTestHelper helper,
                                          final net.minecraft.world.level.block.Block block,
                                          final net.minecraft.world.item.Item blockItem) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, block);

        // The block entity must be created for the era blocks too, or useWithoutItem's instanceof
        // check fails silently and no menu ever opens.
        if (!(helper.getBlockEntity(pos) instanceof PersonalComputerBlockEntity be)) {
            helper.fail("no PersonalComputerBlockEntity at " + pos + " for block " + block + " ("
                    + blockItem + ")");
            return;
        }

        // A plain mock player (no networking, so opening menus broadcasts nothing) standing on the
        // block, well inside the interaction reach stillValid also checks.
        final net.minecraft.world.entity.player.Player player =
                helper.makeMockPlayer(net.minecraft.world.level.GameType.CREATIVE);
        final BlockPos absolute = helper.absolutePos(pos);
        player.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);

        // Menu construction must succeed for an era PC: it reads the shared block entity.
        final dev.jsc.jscomputronics.module.computing.menu.PersonalComputerMenu menu =
                new dev.jsc.jscomputronics.module.computing.menu.PersonalComputerMenu(
                        1, player.getInventory(), be);
        helper.assertFalse(menu.slots.isEmpty(), "the PC menu must build its slots for " + block);

        // The block's own name must resolve without throwing (useWithoutItem uses it as the title).
        helper.assertTrue(block.getName() != null, "block name must resolve for " + block);

        // The decisive check: the server validates the open menu against the block at the position
        // every tick. If stillValid is false the menu is closed at once, so the player never sees it.
        helper.assertTrue(menu.stillValid(player),
                "the Personal Computer menu must stay valid for " + block
                        + "; a menu that is not stillValid is closed immediately, so the GUI never opens");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void craftingComputer_assemblesAndCanCraft(final GameTestHelper helper) {
        final BlockPos cc = new BlockPos(2, 2, 2);
        final CraftingComputerBlockEntity computer = placeRunningCraftingComputer(helper, cc);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(computer.buildValid(), "ATX build with a Crafting Card should be valid");
                    helper.assertTrue(computer.isRunning(), "Crafting Computer should be running after power-on");
                    helper.assertTrue(computer.craftingCardFactor() > 0.0,
                            "an installed Crafting Card gives a non-zero factor");
                    helper.assertTrue(computer.craftingThroughput() > 0,
                            "a running Crafting Computer reports crafting throughput");
                    helper.assertTrue(computer.canCraft(), "a powered Crafting Computer with a card can craft");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void craftingComputer_withoutCardCannotCraft(final GameTestHelper helper) {
        final BlockPos cc = new BlockPos(2, 2, 2);
        helper.setBlock(cc, ComputingModule.CRAFTING_COMPUTER.get());
        if (!(helper.getBlockEntity(cc) instanceof CraftingComputerBlockEntity computer)) {
            throw new IllegalStateException("no crafting computer at " + cc);
        }
        final ItemStackHandler hw = computer.getHardware();
        hw.setStackInSlot(CraftingComputerBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(ComputingModule.MOTHERBOARD_ATX_P.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.CPU_SLOT,
                new ItemStack(ComputingModule.CPU_ASCENT_965.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.RAM_SLOTS_START,
                new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.PSU_SLOT,
                new ItemStack(ComputingModule.PSU_650G.get()));
        computer.togglePower();
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(computer.isRunning(), "the computer still powers on without a card");
                    helper.assertTrue(computer.craftingCardFactor() == 0.0,
                            "no Crafting Card means a zero crafting factor");
                    helper.assertFalse(computer.canCraft(), "without a card the computer cannot craft");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void craftingComputer_joinsMainframeNetworkAsNode(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos eth = new BlockPos(4, 2, 2);
        final BlockPos cc = new BlockPos(5, 2, 2);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(router, ComputingModule.PERSONAL_ROUTER.get());
        helper.setBlock(eth, ComputingModule.ETHERNET_CABLE.get());
        final CraftingComputerBlockEntity computer = placeRunningCraftingComputer(helper, cc);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(computer.networkUuid() != null, "Crafting Computer should be on a network");
                    helper.assertTrue(mainframe.networkUuid() != null, "mainframe should own a network");
                    helper.assertTrue(computer.networkUuid().equals(mainframe.networkUuid()),
                            "Crafting Computer must share the mainframe's network through the router");
                    helper.assertTrue(NetworkSystem.get(helper.getLevel())
                                    .craftingComputersOf(mainframe.networkUuid()).size() == 1,
                            "the Crafting Computer registers as a network node");
                })
                .thenExecute(() -> helper.destroyBlock(cc))
                .thenExecuteAfter(SETTLE, () -> helper.assertTrue(
                        NetworkSystem.get(helper.getLevel())
                                .craftingComputersOf(mainframe.networkUuid()).isEmpty(),
                        "breaking the Crafting Computer unregisters its node"))
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
        // Replace the disk installed by the setup helper with a larger one.
        be.getInventory().setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1)));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // The installed OS (mc_net, 512-item footprint) reserves part of the disk;
                    // the usable storage is the raw disk capacity minus the OS reservation.
                    final long osFootprint = be.reservedByOs();
                    final long expected = DiskSize.TB_1.capacityItems() - osFootprint;
                    helper.assertTrue(
                            be.storageItems() == expected,
                            "build should report the installed disk's capacity net of the OS footprint"
                                    + " (raw=" + DiskSize.TB_1.capacityItems()
                                    + " footprint=" + osFootprint
                                    + " expected=" + expected
                                    + "); got " + be.storageItems());
                })
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
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST)); // cables attach through the rear (west side here)
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
            new BlockPos(2, 3, 2), // against the REAR face of part (3,3,2) — parts only
            new BlockPos(2, 2, 2), // claimed by the mainframe
        };
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        for (final BlockPos c : cables) {
            helper.setBlock(c, ComputingModule.HBW_CABLE.get());
        }
        // Place the controller and drive its self-assembly so all 11 parts exist.
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST)); // cables attach through the rear (west side here)
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

    // Supercomputer Node (shares the Server Rack footprint; rides the shared multiblock lifecycle)

    @GameTest(template = ARENA)
    public static void supercomputerNode_formsAndDissolves(final GameTestHelper helper) {
        final BlockPos controller = new BlockPos(4, 2, 2); // footprint x[4,5] y[2,4] z[2,3]
        final BlockPos part = new BlockPos(4, 3, 2);       // a part, one up from the controller
        final Direction facing = Direction.EAST;           // cables attach through the rear (west side here)
        helper.setBlock(controller, ComputingModule.SUPERCOMPUTER_NODE.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, facing));
        // Drive the self-assembly the way item placement would, so all 11 parts exist.
        ((SupercomputerNodeBlock) ComputingModule.SUPERCOMPUTER_NODE.get()).setPlacedBy(
                helper.getLevel(), helper.absolutePos(controller),
                helper.getBlockState(controller), null, ItemStack.EMPTY);

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> helper.assertTrue(
                        helper.getBlockState(part).getBlock() instanceof SupercomputerNodePartBlock,
                        "placing the node must raise its structural parts"))
                .thenExecute(() -> helper.setBlock(part, Blocks.AIR)) // break one part
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(helper.getBlockState(controller).isAir(),
                            "breaking a part must take the controller down too");
                    helper.assertTrue(helper.getBlockState(part).isAir(),
                            "the broken part must be gone");
                    helper.assertTrue(helper.getBlockState(new BlockPos(5, 4, 3)).isAir(),
                            "the whole cabinet must dissolve, including the far-top-back corner");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void supercomputerNode_teardownDropsNothing(final GameTestHelper helper) {
        final BlockPos controller = new BlockPos(4, 2, 2);
        final Direction facing = Direction.EAST;
        helper.setBlock(controller, ComputingModule.SUPERCOMPUTER_NODE.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, facing));
        ((SupercomputerNodeBlock) ComputingModule.SUPERCOMPUTER_NODE.get()).setPlacedBy(
                helper.getLevel(), helper.absolutePos(controller),
                helper.getBlockState(controller), null, ItemStack.EMPTY);
        if (helper.getBlockEntity(controller) instanceof SupercomputerNodeBlockEntity node) {
            node.getHardware().setStackInSlot(0, new ItemStack(Items.IRON_INGOT)); // installed hardware
        } else {
            helper.fail("no supercomputer node block entity placed");
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(2, () -> helper.setBlock(
                        ServerRackStructure.partPositions(controller, facing).get(0), Blocks.AIR))
                .thenExecuteAfter(2, () -> helper.assertTrue(droppedItems(helper, controller) == 0,
                        "dissolving the node must not drop the node or its hardware (creative-safe teardown)"))
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void networkStorage_queriesSelectsAndInserts(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos rack = new BlockPos(3, 2, 2);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST)); // cables attach through the rear (west side here)
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
                    final long moved = ns.select(Items.COBBLESTONE, 30, new dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort(dest, null));
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
    public static void manualLock_makesConcurrentSelectWaitUntilUnlocked(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos rack = new BlockPos(3, 2, 2);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST)); // cables attach through the rear (west side here)
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        final StorageKey cobble = StorageKey.of(Items.COBBLESTONE);
        final dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation[] op =
                new dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation[1];
        final ItemStackHandler dest = new ItemStackHandler(9);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(mainframe.networkUuid() != null, "mainframe owns a network");
                    rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 100);
                })
                // Let the Mainframe's incremental ANALYZE index the freshly seeded items.
                .thenExecuteAfter(3, () -> {
                    final long held = mainframe.lockType(cobble, Long.MAX_VALUE, null);
                    helper.assertTrue(held == 100, "LOCK must hold all 100 cobblestone; got " + held);
                    op[0] = mainframe.submitNetworkSelect(Items.COBBLESTONE, 50,
                            new dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort(dest, null), "test");
                    helper.assertTrue(op[0] != null, "Mainframe should dispatch the SELECT");
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(op[0].isWaiting(), "the SELECT must WAIT while the type is locked");
                    helper.assertFalse(op[0].isDone(), "a waiting SELECT is not done");
                    final long released = mainframe.unlockType(cobble);
                    helper.assertTrue(released == 100, "UNLOCK must release the 100 held; got " + released);
                })
                .thenExecuteAfter(12, () -> {
                    helper.assertTrue(op[0].isDone(), "the SELECT must finish once the lock is released");
                    helper.assertTrue(op[0].status() == OperationRecord.STATUS_COMPLETED,
                            "the SELECT must complete fully after unlock; status " + op[0].status());
                    final NetworkStorage ns = NetworkStorage.of(helper.getLevel(), mainframe.networkUuid());
                    helper.assertTrue(ns.count(Items.COBBLESTONE) == 50,
                            "the network must hold 50 after the SELECT; got " + ns.count(Items.COBBLESTONE));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void poweringOffMidOperation_recordsDiscardedInTheLog(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos rack = new BlockPos(3, 2, 2);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST)); // cables attach through the rear (west side here)
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        final StorageKey cobble = StorageKey.of(Items.COBBLESTONE);
        final dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation[] op =
                new dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation[1];
        final ItemStackHandler dest = new ItemStackHandler(9);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertTrue(mainframe.networkUuid() != null, "mainframe owns a network");
                    rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 100);
                })
                // Let the incremental ANALYZE index the seeded items, then hold the type so the
                // SELECT can never finish: it stays in-flight (WAITING) until we power off.
                .thenExecuteAfter(3, () -> {
                    final long held = mainframe.lockType(cobble, Long.MAX_VALUE, null);
                    helper.assertTrue(held == 100, "LOCK must hold all 100 cobblestone; got " + held);
                    op[0] = mainframe.submitNetworkSelect(Items.COBBLESTONE, 50,
                            new dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort(dest, null), "test");
                    helper.assertTrue(op[0] != null, "Mainframe should dispatch the SELECT");
                })
                .thenExecuteAfter(5, () -> {
                    helper.assertTrue(op[0].isWaiting(), "the SELECT must be in-flight (WAITING) before power-off");
                    mainframe.togglePower(); // power off with an Operation still in flight
                })
                // Powering off makes the next tick run closeDispatch(), which abandons every
                // in-flight Operation and records it so the log keeps a trace instead of losing it.
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertFalse(mainframe.isRunning(), "the mainframe is powered off");
                    final boolean discarded = mainframe.recentOperations().stream()
                            .anyMatch(r -> r.status() == OperationRecord.STATUS_DISCARDED);
                    helper.assertTrue(discarded,
                            "an Operation abandoned by power-off must be logged as DISCARDED, not vanish");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void chunkUnload_unregistersHousedServers(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos rack = new BlockPos(3, 2, 2);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST));
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final NetworkUuid net = mainframe.networkUuid();
                    helper.assertTrue(NetworkSystem.get(helper.getLevel()).serversOf(net).size() == 1,
                            "the rack's server is registered before the unload");
                    // A chunk unload removes the block entity without firing the block's onRemove.
                    rackBe.setRemoved();
                    helper.assertTrue(NetworkSystem.get(helper.getLevel()).serversOf(net).isEmpty(),
                            "setRemoved (the chunk-unload path) must unregister the housed servers");
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
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST)); // cables attach through the rear (west side here)
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
                    final int stored = ns.insert(new ItemStack(Items.COBBLESTONE, 2_500));
                    helper.assertTrue(stored == 2_000,
                            "INSERT must cap at the 2,000-item disk capacity; stored " + stored);
                    helper.assertTrue(ns.count(Items.COBBLESTONE) == 2_000,
                            "network should hold exactly 2,000; got " + ns.count(Items.COBBLESTONE));
                })
                .thenSucceed();
    }

    private static ItemStack bigDiskServer() {
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
                new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_8)));
        hw.set(dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler.DISK_START + 1,
                new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_8)));
        final ItemStack server = new ItemStack(ComputingModule.SERVER.get());
        server.set(ComputingModule.SERVER_HARDWARE.get(),
                net.minecraft.world.item.component.ItemContainerContents.fromItems(hw));
        return server;
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
                new ItemStack(ComputingModule.disk(StorageTier.SSD, DiskSize.GB_500)));
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
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST)); // cables attach through the rear (west side here)
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    final NetworkUuid net = mainframe.networkUuid();
                    helper.assertTrue(net != null, "mainframe owns a network");
                    final var op = mainframe.submitNetworkInsert(Items.COBBLESTONE, 40, "test");
                    helper.assertTrue(op != null, "Mainframe should dispatch the INSERT Operation");
                })
                // The timed Operation streams over a few ticks (disk latency, then the write).
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
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(router, ComputingModule.PERSONAL_ROUTER.get());
        helper.setBlock(eth, ComputingModule.ETHERNET_CABLE.get());
        final PersonalComputerBlockEntity computer = placeRunningPC(helper, pc);
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.SOUTH)); // rear faces the cable to the north
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
                    final long moved = ns.select(Items.COBBLESTONE, 100, new dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort(dest, null));
                    helper.assertTrue(moved == 100, "SELECT should move 100 via the PC's network; got " + moved);
                    helper.assertTrue(ns.count(Items.COBBLESTONE) == 100,
                            "network should have 100 left after SELECT");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void commandPrompt_runsAgainstTheNetwork(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos eth = new BlockPos(4, 2, 2);
        final BlockPos pc = new BlockPos(5, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(router, ComputingModule.PERSONAL_ROUTER.get());
        helper.setBlock(eth, ComputingModule.ETHERNET_CABLE.get());
        final PersonalComputerBlockEntity computer = placeRunningPC(helper, pc);
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 200);
                    final var cli = new dev.jsc.jscomputronics.module.computing.program.ServerCliComputer(
                            (dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost) computer,
                            helper.getLevel());
                    final var shell = dev.jsc.jscomputronics.module.computing.program.cli.CliCommands.newShell(50);

                    helper.assertTrue(cliContains(shell.run("whoami", cli), "Personal Computer"),
                            "whoami should report the computer kind");
                    helper.assertTrue(cliContains(shell.run("status", cli), "ONLINE"),
                            "status should report the running computer as online");
                    helper.assertTrue(cliContains(shell.run("operation query items", cli), "cobblestone"),
                            "operation query items should list the network's cobblestone");
                    helper.assertTrue(
                            cliContains(shell.run("operation query items WHERE name contains diamond", cli), "no rows"),
                            "operation query with a non-matching filter should say so");
                    helper.assertTrue(cliContains(shell.run("operation select 50 cobblestone", cli), "SELECT queued"),
                            "operation select should queue an operation through the network");
                    helper.assertTrue(cliContains(shell.run("operation select 50 not_a_real_item", cli), "unknown item"),
                            "operation select of an unknown item should be reported, not crash");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void nms_runsParsedIqlAgainstTheNetwork(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos eth = new BlockPos(4, 2, 2);
        final BlockPos pc = new BlockPos(5, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3);
        placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(router, ComputingModule.PERSONAL_ROUTER.get());
        helper.setBlock(eth, ComputingModule.ETHERNET_CABLE.get());
        final PersonalComputerBlockEntity computer = placeRunningPC(helper, pc);
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 200);
                    final var cli = new dev.jsc.jscomputronics.module.computing.program.ServerCliComputer(
                            (dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost) computer,
                            helper.getLevel());
                    final var read = dev.jsc.jscomputronics.module.computing.program.iql.IqlParser.tryParse(
                            "QUERY items");
                    helper.assertTrue(read.ok(), "the read statement must parse");
                    helper.assertFalse(cli.queryObject("items", null, "", 64).isEmpty(),
                            "QUERY items must return the network's rows");
                    helper.assertFalse(cli.queryObject("servers", null, "", 64).isEmpty(),
                            "QUERY servers must list the rack's server");

                    final var selectAll = dev.jsc.jscomputronics.module.computing.program.iql.IqlParser.tryParse(
                            "SELECT *");
                    helper.assertTrue(selectAll.ok(), "SELECT * must parse");
                    helper.assertTrue(cli.execute(selectAll.operation()).ok(),
                            "SELECT * must queue an extraction for every item type");

                    final var pull = dev.jsc.jscomputronics.module.computing.program.iql.IqlParser.tryParse(
                            "SELECT 50 cobblestone");
                    helper.assertTrue(pull.ok(), "the pull statement must parse");
                    helper.assertTrue(cli.execute(pull.operation()).ok(),
                            "executing the pull must queue an operation");

                    // The Object Explorer snapshot must mirror the real network, not a static example tree.
                    final var schema = dev.jsc.jscomputronics.module.computing.operation.payload.ComputingPayloads
                            .nmsSchema(helper.getLevel(),
                                    (dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost) computer);
                    helper.assertTrue(schema.networkLabel().startsWith("jsc-net-"),
                            "the Object Explorer must show the real network label");
                    helper.assertFalse(schema.servers().isEmpty(),
                            "the Object Explorer must list the rack's real server");
                    helper.assertTrue(schema.itemTypes() >= 1,
                            "the Object Explorer must count the network's item types");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void iqlEngine_storesAndRunsSavedObjects(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos eth = new BlockPos(4, 2, 2);
        final BlockPos pc = new BlockPos(5, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(router, ComputingModule.PERSONAL_ROUTER.get());
        helper.setBlock(eth, ComputingModule.ETHERNET_CABLE.get());
        final PersonalComputerBlockEntity computer = placeRunningPC(helper, pc);
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        final var viewType = dev.jsc.jscomputronics.module.computing.program.iql.IqlDefinition.ObjectType.VIEW;
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 200);
                    final var cli = new dev.jsc.jscomputronics.module.computing.program.ServerCliComputer(
                            (dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost) computer,
                            helper.getLevel());
                    // 'install iqlengine' (the normal install command) installs the Engine service on the Mainframe.
                    helper.assertTrue(cli.install("iqlengine").ok(),
                            "'install iqlengine' must install the Engine on the Mainframe");
                    helper.assertTrue(mainframe.isIqlEngineInstalled(),
                            "the Engine must be installed after 'install iqlengine'");
                    helper.assertTrue(cli.iqlEngineInstalled(),
                            "the computer must report the Engine installed");
                    final var engine = new dev.jsc.jscomputronics.module.computing.program.IqlEngine(
                            mainframe, cli, 64);

                    helper.assertTrue(engine.run("CREATE VIEW stock AS QUERY items").ok(),
                            "CREATE VIEW must succeed");
                    helper.assertTrue(mainframe.iqlCatalog().contains(viewType, "stock"),
                            "the catalog must hold the created view");

                    // The NMS Object Explorer snapshot must reflect the real catalog, not mock examples.
                    final var schema = dev.jsc.jscomputronics.module.computing.operation.payload.ComputingPayloads
                            .nmsSchema(helper.getLevel(),
                                    (dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost) computer);
                    helper.assertTrue(schema.engine().views().contains("stock"),
                            "the NMS Object Explorer must list the created view");
                    helper.assertTrue("running".equals(schema.engine().state()),
                            "the NMS must show the Engine as running");

                    final var query = engine.run("QUERY stock");
                    helper.assertTrue(query.ok(), "QUERY <view> must run the saved query: " + query.message());
                    helper.assertFalse(query.rows().isEmpty(), "QUERY <view> must return the network's rows");

                    // QUERY * returns every item; the full WHERE really filters by qty now (cobblestone = 200).
                    helper.assertFalse(engine.run("QUERY *").rows().isEmpty(), "QUERY * must return all items");
                    helper.assertFalse(engine.run("QUERY items WHERE qty > 100").rows().isEmpty(),
                            "WHERE qty > 100 must keep the 200 cobblestone");
                    helper.assertTrue(engine.run("QUERY items WHERE qty > 1000").rows().isEmpty(),
                            "WHERE qty > 1000 must filter out the 200 cobblestone");

                    helper.assertTrue(engine.run("CREATE PROCEDURE refresh AS { QUERY items; QUERY servers }").ok(),
                            "CREATE PROCEDURE must succeed");
                    helper.assertTrue(engine.run("EXEC refresh").ok(),
                            "EXEC must run the procedure's statements in order");

                    // Gate: a stopped Engine rejects definitions; ad-hoc actions are unaffected.
                    mainframe.setIqlEngineRunning(false);
                    helper.assertFalse(engine.run("CREATE VIEW v2 AS QUERY items").ok(),
                            "a CREATE must fail when the Engine is stopped");
                    mainframe.setIqlEngineRunning(true);

                    helper.assertTrue(engine.run("DROP VIEW stock").ok(), "DROP VIEW must succeed");
                    helper.assertFalse(mainframe.iqlCatalog().contains(viewType, "stock"),
                            "the view must be gone after DROP");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void iqlJobAgent_firesScheduledJobs(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 8, () -> {
                    rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 1_000_000L);
                    mainframe.installIqlEngine();
                    final var engine = new dev.jsc.jscomputronics.module.computing.program.IqlEngine(mainframe,
                            new dev.jsc.jscomputronics.module.computing.program.ServerCliComputer(
                                    mainframe, helper.getLevel()), 64);
                    // EVERY 1t: the agent (evaluating every 10 ticks) fires this within a couple of evaluations.
                    helper.assertTrue(engine.run("CREATE JOB drainer AS DROP 100 cobblestone EVERY 1t").ok(),
                            "CREATE JOB must succeed");
                })
                .thenExecuteAfter(60, () -> helper.assertTrue(
                        mainframe.completedOps() > 0 || !mainframe.recentOperations().isEmpty(),
                        "the EVERY job must have fired its DROP operation by now"))
                .thenSucceed();
    }

    private static boolean cliContains(
            final dev.jsc.jscomputronics.module.computing.program.cli.CliShell.Response response,
            final String needle) {
        final String lower = needle.toLowerCase(java.util.Locale.ROOT);
        return response.lines().stream()
                .anyMatch(line -> line.text().toLowerCase(java.util.Locale.ROOT).contains(lower));
    }

    @GameTest(template = ARENA)
    public static void terminalSelect_landsInPcLocalStorage(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos eth = new BlockPos(4, 2, 2);
        final BlockPos pc = new BlockPos(5, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(router, ComputingModule.PERSONAL_ROUTER.get());
        helper.setBlock(eth, ComputingModule.ETHERNET_CABLE.get());
        final PersonalComputerBlockEntity computer = placeRunningPC(helper, pc);
        // A disk gives the PC local storage, so a SELECT lands there.
        computer.getHardware().setStackInSlot(PersonalComputerBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1)));
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.SOUTH)); // rear faces the cable to the north
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 200);
                    helper.assertTrue(computer.networkUuid() != null, "PC must be on the network");
                })
                // Let the Mainframe's in-RAM catalog pick up the freshly seeded items before the SELECT
                // locks against it (the terminal only ever lets a player pick an already-indexed item).
                .thenExecuteAfter(2, () -> {
                    // The terminal SELECT-to-storage resolves the computer's local storage as the
                    // destination; route the timed pull straight into it.
                    final var op = mainframe.submitNetworkSelect(Items.COBBLESTONE, 50,
                            computer.localStorage(), "storage", null);
                    helper.assertTrue(op != null, "Mainframe should dispatch the SELECT-to-storage Operation");
                })
                .thenExecuteAfter(8, () -> {
                    final long inStorage = computer.localStore().count(StorageKey.of(Items.COBBLESTONE));
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
    public static void localStorage_cappedByDiskCapacity(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos eth = new BlockPos(4, 2, 2);
        final BlockPos pc = new BlockPos(5, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(router, ComputingModule.PERSONAL_ROUTER.get());
        helper.setBlock(eth, ComputingModule.ETHERNET_CABLE.get());
        final PersonalComputerBlockEntity computer = placeRunningPC(helper, pc);
        // A 500 GB disk holds 2,000 items — the SELECT below asks for more than that.
        computer.getHardware().setStackInSlot(PersonalComputerBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.GB_500)));
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.SOUTH)); // rear faces the cable to the north
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 2_500);
                    helper.assertTrue(computer.networkUuid() != null, "PC must be on the network");
                })
                .thenExecuteAfter(2, () -> {
                    // Ask for more than the 2,000-item disk can hold.
                    final var op = mainframe.submitNetworkSelect(Items.COBBLESTONE, 2_500,
                            computer.localStorage(), "storage", null);
                    helper.assertTrue(op != null, "Mainframe should dispatch the SELECT");
                })
                .thenExecuteAfter(8, () -> {
                    final long inStorage = computer.localStore().count(StorageKey.of(Items.COBBLESTONE));
                    final long inNet = NetworkStorage.of(helper.getLevel(), mainframe.networkUuid())
                            .count(Items.COBBLESTONE);
                    helper.assertTrue(inStorage == 2_000,
                            "local storage must cap at the 2,000-item disk capacity; got " + inStorage);
                    helper.assertTrue(inStorage + inNet == 2_500,
                            "the rest must stay in the network, nothing lost; storage=" + inStorage + " net=" + inNet);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void terminalInsert_movesIntoNetwork(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbw = new BlockPos(2, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbw, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.SOUTH)); // rear faces the cable to the north
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    final NetworkUuid net = mainframe.networkUuid();
                    helper.assertTrue(net != null, "mainframe must own a network");
                    final var op = mainframe.submitNetworkInsert(Items.COBBLESTONE, 64, "terminal");
                    helper.assertTrue(op != null, "Mainframe should dispatch the terminal INSERT Operation");
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

    @GameTest(template = ARENA)
    public static void installedHardware_persistsAcrossReload(final GameTestHelper helper) {
        final BlockPos a = new BlockPos(2, 2, 2);
        final MainframeBlockEntity be = placeRunningMainframe(helper, a);
        // Add an extra CPU and a disk on top of the valid build, so a wrong/changed hardware NBT key
        // (the Mainframe persists under "Inventory", not the base's "Hardware") would be caught here:
        // the reloaded build would silently lose these components.
        be.getInventory().setStackInSlot(MainframeBlockEntity.CPU_SLOTS_START + 1,
                new ItemStack(ComputingModule.CPU_SERVO_2620.get()));
        be.getInventory().setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1)));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final long capacityBefore = be.capacity();
                    final long storageBefore = be.storageItems();
                    // The OS (installed by the setup helper) reserves part of the disk; the usable storage is
                    // the raw disk capacity minus the OS footprint.
                    final long expectedStorage = DiskSize.TB_1.capacityItems() - be.reservedByOs();
                    helper.assertTrue(storageBefore == expectedStorage,
                            "build should report the disk's capacity net of the OS footprint before reload; got "
                                    + storageBefore + " expected " + expectedStorage);

                    final var registries = helper.getLevel().registryAccess();
                    final net.minecraft.nbt.CompoundTag saved = be.saveWithFullMetadata(registries);
                    final var reloaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                            helper.absolutePos(a), be.getBlockState(), saved, registries);
                    helper.assertTrue(reloaded instanceof MainframeBlockEntity, "reloaded BE should be a Mainframe");
                    final MainframeBlockEntity loaded = (MainframeBlockEntity) reloaded;

                    helper.assertTrue(loaded.getInventory()
                                    .getStackInSlot(MainframeBlockEntity.DISK_SLOTS_START).getItem()
                                    instanceof dev.jsc.jscomputronics.module.computing.item.DiskItem,
                            "the installed disk must survive reload under the preserved NBT key");
                    helper.assertTrue(loaded.getInventory()
                                    .getStackInSlot(MainframeBlockEntity.CPU_SLOTS_START + 1).getItem()
                                    instanceof dev.jsc.jscomputronics.module.computing.item.CpuItem,
                            "the second CPU must survive reload under the preserved NBT key");
                    helper.assertTrue(loaded.storageItems() == storageBefore,
                            "reloaded build must report the same storage capacity; got " + loaded.storageItems()
                                    + " expected " + storageBefore);
                    helper.assertTrue(loaded.capacity() == capacityBefore,
                            "reloaded build must report the same orchestration capacity; got " + loaded.capacity()
                                    + " expected " + capacityBefore);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void importBus_movesChestItemsIntoNetwork(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(4, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.SOUTH)); // rear faces the cable to the north
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());

        // Import Bus part on the east face of the cable end, facing a barrel of cobblestone.
        final BlockPos cableEnd = new BlockPos(4, 2, 2);
        if (helper.getBlockEntity(cableEnd) instanceof DataCableBlockEntity cable) {
            cable.addPart(Direction.EAST, new ImportBusPart());
        }
        final BlockPos barrel = new BlockPos(5, 2, 2);
        helper.setBlock(barrel, net.minecraft.world.level.block.Blocks.BARREL);
        if (helper.getBlockEntity(barrel) instanceof net.minecraft.world.Container container) {
            container.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        }

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 50, () -> {
                    final NetworkUuid net = mainframe.networkUuid();
                    final long held = NetworkStorage.of(helper.getLevel(), net).count(Items.COBBLESTONE);
                    helper.assertTrue(held > 0, "import bus must move items into the network; got " + held);
                    final var log = mainframe.recentOperations();
                    helper.assertTrue(!log.isEmpty() && log.get(0).type() == OperationRecord.TYPE_INSERT,
                            "the import should be logged as an INSERT");
                    helper.assertTrue(!log.get(0).moves().isEmpty()
                                    && log.get(0).moves().get(0).from().equals("import"),
                            "provenance should read 'import'");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void importBus_flushConservation(final GameTestHelper helper) {
        // Break the cable while items are in the "flushed" state (extracted from source,
        // INSERT dispatched but not yet settled) and verify nothing is silently discarded.
        // Items must be conserved: either in network storage (if INSERT completed first)
        // or dropped as entities at the cable position (if INSERT was still in flight).
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(4, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.SOUTH));
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack at " + rack);
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());

        final BlockPos cableEnd = new BlockPos(4, 2, 2);
        if (helper.getBlockEntity(cableEnd) instanceof DataCableBlockEntity cable) {
            cable.addPart(Direction.EAST, new ImportBusPart());
        }
        final BlockPos barrel = new BlockPos(5, 2, 2);
        helper.setBlock(barrel, Blocks.BARREL);
        if (helper.getBlockEntity(barrel) instanceof net.minecraft.world.Container container) {
            container.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        }

        // After SETTLE the network is live and extraction starts. FLUSH_TICKS (20) later the bus
        // dispatches an INSERT; the HDD's disk-seek latency (10 ticks) means the INSERT is still
        // in flight at tick SETTLE+22. Breaking the cable at that point exercises the dropContents
        // path for in-flight flushes — items must not vanish.
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 22, () -> helper.destroyBlock(cableEnd))
                .thenExecuteAfter(SETTLE, () -> {
                    final long inNetwork = mainframe.networkUuid() != null
                            ? NetworkStorage.of(helper.getLevel(), mainframe.networkUuid()).count(Items.COBBLESTONE)
                            : 0L;
                    final long inWorld = helper.getLevel().getEntitiesOfClass(
                                    net.minecraft.world.entity.item.ItemEntity.class,
                                    new net.minecraft.world.phys.AABB(helper.absolutePos(cableEnd)).inflate(6.0))
                            .stream()
                            .filter(e -> e.getItem().is(Items.COBBLESTONE))
                            .mapToLong(e -> e.getItem().getCount())
                            .sum();
                    helper.assertTrue(inNetwork + inWorld > 0L,
                            "cobblestone must not be silently discarded: inNetwork=" + inNetwork
                                    + " inWorld=" + inWorld);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void exportBus_movesNetworkItemsIntoChest(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(4, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.SOUTH)); // rear faces the cable to the north
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());

        // Export Bus part on the east face of the cable end, facing a barrel.
        final BlockPos cableEnd = new BlockPos(4, 2, 2);
        if (helper.getBlockEntity(cableEnd) instanceof DataCableBlockEntity cable) {
            cable.addPart(Direction.EAST, new ExportBusPart());
        }
        final BlockPos barrel = new BlockPos(5, 2, 2);
        helper.setBlock(barrel, net.minecraft.world.level.block.Blocks.BARREL);

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 200);
                    if (helper.getBlockEntity(cableEnd) instanceof DataCableBlockEntity cable
                            && cable.getPart(Direction.EAST) instanceof ExportBusPart bus) {
                        bus.setFilter(new ItemStack(Items.COBBLESTONE));
                    }
                })
                .thenExecuteAfter(50, () -> {
                    long inBarrel = 0L;
                    if (helper.getBlockEntity(barrel) instanceof net.minecraft.world.Container container) {
                        for (int i = 0; i < container.getContainerSize(); i++) {
                            if (container.getItem(i).is(Items.COBBLESTONE)) {
                                inBarrel += container.getItem(i).getCount();
                            }
                        }
                    }
                    helper.assertTrue(inBarrel > 0, "export bus must move items into the chest; got " + inBarrel);
                    final var log = mainframe.recentOperations();
                    helper.assertTrue(!log.isEmpty() && log.get(0).type() == OperationRecord.TYPE_DELETE,
                            "the export should be logged as a DELETE");
                    helper.assertTrue(!log.get(0).moves().isEmpty()
                                    && log.get(0).moves().get(0).to().equals("export"),
                            "provenance should go to 'export'");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void importBus_filterImportsOnlyThatType(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(4, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        final BlockPos cableEnd = new BlockPos(4, 2, 2);
        if (helper.getBlockEntity(cableEnd) instanceof DataCableBlockEntity cable) {
            final ImportBusPart bus = new ImportBusPart();
            cable.addPart(Direction.EAST, bus);
            bus.setFilter(new ItemStack(Items.COBBLESTONE)); // import only cobblestone, leave the dirt
        }
        final BlockPos barrel = new BlockPos(5, 2, 2);
        helper.setBlock(barrel, net.minecraft.world.level.block.Blocks.BARREL);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    if (helper.getBlockEntity(barrel) instanceof net.minecraft.world.Container c) {
                        c.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                        c.setItem(1, new ItemStack(Items.DIRT, 64));
                    }
                })
                .thenExecuteAfter(80, () -> {
                    final long cobble = NetworkStorage.of(helper.getLevel(), mainframe.networkUuid())
                            .count(Items.COBBLESTONE);
                    final long dirt = NetworkStorage.of(helper.getLevel(), mainframe.networkUuid())
                            .count(Items.DIRT);
                    helper.assertTrue(cobble > 0L, "the filtered import must pull cobblestone; got " + cobble);
                    helper.assertTrue(dirt == 0L, "the filter must leave dirt in the barrel; net dirt=" + dirt);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void iql_deleteToNamedBusExports(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(4, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        final BlockPos cableEnd = new BlockPos(4, 2, 2);
        if (helper.getBlockEntity(cableEnd) instanceof DataCableBlockEntity cable) {
            final ExportBusPart bus = new ExportBusPart();
            cable.addPart(Direction.EAST, bus);
            bus.setName("out"); // no filter, so it never auto-exports; the query drives it by name
        }
        final BlockPos barrel = new BlockPos(5, 2, 2);
        helper.setBlock(barrel, net.minecraft.world.level.block.Blocks.BARREL);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () ->
                        rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 200))
                .thenExecuteAfter(10, () -> {
                    // Run the export only after the network has indexed the server's stock.
                    final var engine = new dev.jsc.jscomputronics.module.computing.program.IqlEngine(mainframe,
                            new dev.jsc.jscomputronics.module.computing.program.ServerCliComputer(mainframe,
                                    helper.getLevel()), 64);
                    final var outcome = engine.run("DELETE cobblestone TO out");
                    helper.assertTrue(outcome.ok(), "DELETE TO a named bus should be accepted: " + outcome.message());
                })
                .thenExecuteAfter(40, () -> {
                    long inBarrel = 0L;
                    if (helper.getBlockEntity(barrel) instanceof net.minecraft.world.Container c) {
                        for (int i = 0; i < c.getContainerSize(); i++) {
                            if (c.getItem(i).is(Items.COBBLESTONE)) {
                                inBarrel += c.getItem(i).getCount();
                            }
                        }
                    }
                    helper.assertTrue(inBarrel > 0L, "DELETE TO a named bus must export into its inventory; got " + inBarrel);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void iql_insertFromNamedBusImports(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(4, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        final BlockPos cableEnd = new BlockPos(4, 2, 2);
        if (helper.getBlockEntity(cableEnd) instanceof DataCableBlockEntity cable) {
            final ImportBusPart bus = new ImportBusPart();
            cable.addPart(Direction.EAST, bus);
            bus.setName("in");
            bus.toggleMode(); // redstone mode: with no signal it never auto-imports, so the query drives it
        }
        final BlockPos barrel = new BlockPos(5, 2, 2);
        helper.setBlock(barrel, net.minecraft.world.level.block.Blocks.BARREL);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    if (helper.getBlockEntity(barrel) instanceof net.minecraft.world.Container c) {
                        c.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
                    }
                    final var engine = new dev.jsc.jscomputronics.module.computing.program.IqlEngine(mainframe,
                            new dev.jsc.jscomputronics.module.computing.program.ServerCliComputer(mainframe,
                                    helper.getLevel()), 64);
                    final var outcome = engine.run("INSERT cobblestone FROM in");
                    helper.assertTrue(outcome.ok(), "INSERT FROM a named bus should be accepted: " + outcome.message());
                })
                .thenExecuteAfter(80, () -> {
                    final long net = NetworkStorage.of(helper.getLevel(), mainframe.networkUuid())
                            .count(Items.COBBLESTONE);
                    helper.assertTrue(net > 0L, "INSERT FROM a named bus must import into the network; got " + net);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void job_pausePreventsFiringUntilRestart(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING, Direction.SOUTH));
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 1000);
                    mainframe.installIqlEngine();
                    final var engine = new dev.jsc.jscomputronics.module.computing.program.IqlEngine(mainframe,
                            new dev.jsc.jscomputronics.module.computing.program.ServerCliComputer(mainframe,
                                    helper.getLevel()), 64);
                    engine.run("CREATE JOB killer AS DROP 64 cobblestone EVERY 5t");
                    mainframe.pauseJob("killer"); // paused from the start, so it must never fire
                })
                .thenExecuteAfter(40, () -> {
                    final long left = NetworkStorage.of(helper.getLevel(), mainframe.networkUuid())
                            .count(Items.COBBLESTONE);
                    helper.assertTrue(left == 1000L, "a paused job must not fire; cobblestone left=" + left);
                    mainframe.restartJob("killer"); // resume + re-arm
                })
                .thenExecuteAfter(40, () -> {
                    final long left = NetworkStorage.of(helper.getLevel(), mainframe.networkUuid())
                            .count(Items.COBBLESTONE);
                    helper.assertTrue(left < 1000L, "a restarted job must fire again; cobblestone left=" + left);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void nmsScript_travelsInSchemaSnapshot(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, new BlockPos(1, 2, 2));
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    mainframe.installIqlEngine();
                    mainframe.setSavedScript("QUERY items WHERE qty > 10");
                    final var schema = dev.jsc.jscomputronics.module.computing.operation.payload.ComputingPayloads
                            .nmsSchema(helper.getLevel(), mainframe);
                    helper.assertTrue("QUERY items WHERE qty > 10".equals(schema.engine().script()),
                            "the saved script must travel in the schema snapshot; got: '"
                                    + schema.engine().script() + "'");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void insert_abandonsCleanlyOnPowerOff(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(4, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.SOUTH)); // rear faces the cable to the north
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, bigDiskServer());

        // A large INSERT: far beyond one tick of the server's RAM-bounded write rate, so it is
        // certainly still in flight when power is cut a few ticks in.
        final long demand = 30_000L;
        final java.util.concurrent.atomic.AtomicReference<
                dev.jsc.jscomputronics.module.computing.operation.NetworkInsertOperation> opBox =
                new java.util.concurrent.atomic.AtomicReference<>();

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final var op = mainframe.submitNetworkInsert(Items.COBBLESTONE, demand, "test");
                    helper.assertTrue(op != null, "the INSERT should dispatch on a running mainframe");
                    opBox.set(op);
                })
                .thenExecuteAfter(6, () -> {
                    final var op = opBox.get();
                    helper.assertFalse(op.isDone(), "the INSERT should still be in flight before power-off");
                    helper.assertTrue(op.writtenTotal() > 0L,
                            "it should have written some before power-off; written=" + op.writtenTotal());
                    mainframe.togglePower(); // power off mid-flight -> closeDispatch must abandon it
                })
                .thenExecuteAfter(SETTLE, () -> {
                    final var op = opBox.get();
                    helper.assertFalse(mainframe.isRunning(), "mainframe should be powered off");
                    helper.assertTrue(op.isDone(), "a power-off must settle the in-flight INSERT (no wedge)");
                    final long written = op.writtenTotal();
                    helper.assertTrue(written > 0L && written < demand,
                            "it was abandoned mid-flight; written=" + written);
                    helper.assertTrue(op.leftover() == demand - written,
                            "leftover must account for every unwritten item; leftover=" + op.leftover()
                                    + " written=" + written);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void select_abortsWhenDestinationGone(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(4, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.SOUTH)); // rear faces the cable to the north
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());

        // A large pull into a roomy sink, so it spans many ticks (it cannot finish before we cut it).
        final long seeded = 8_000L;
        final ItemStackHandler dest = new ItemStackHandler(1000);
        final java.util.concurrent.atomic.AtomicBoolean gone = new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.concurrent.atomic.AtomicReference<
                dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation> opBox =
                new java.util.concurrent.atomic.AtomicReference<>();

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> rackBe.getServerStorage(0).insert(Items.COBBLESTONE, seeded))
                // Let the in-RAM catalog see the seeded items before the SELECT locks against it.
                .thenExecuteAfter(2, () -> {
                    final var op = mainframe.submitNetworkSelect(
                            Items.COBBLESTONE, seeded, new dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort(dest, null), "test", null);
                    helper.assertTrue(op != null, "the SELECT should dispatch on a running mainframe");
                    op.abortWhen(gone::get);
                    opBox.set(op);
                })
                .thenExecuteAfter(3, () -> {
                    helper.assertFalse(opBox.get().isDone(), "the SELECT should still be pulling before its sink is gone");
                    gone.set(true); // the destination vanished (the requesting player logged out)
                })
                .thenExecuteAfter(2, () -> {
                    final var op = opBox.get();
                    helper.assertTrue(op.isDone(), "the SELECT must settle once its destination is gone");
                    long inDest = 0L;
                    for (int i = 0; i < dest.getSlots(); i++) {
                        if (dest.getStackInSlot(i).is(Items.COBBLESTONE)) {
                            inDest += dest.getStackInSlot(i).getCount();
                        }
                    }
                    final long inNet = NetworkStorage.of(helper.getLevel(), mainframe.networkUuid())
                            .count(Items.COBBLESTONE);
                    helper.assertTrue(inDest > 0L && inDest < seeded,
                            "it moved some but not all before aborting; inDest=" + inDest);
                    helper.assertTrue(inDest + inNet == seeded,
                            "no items lost or created on abort; dest=" + inDest + " net=" + inNet);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void activeOperations_reportLiveProgress(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(4, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.SOUTH)); // rear faces the cable to the north
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertFalse(mainframe.hasActiveOperations(), "idle before any submit");
                    helper.assertTrue(mainframe.submitNetworkInsert(Items.COBBLESTONE, 200_000L, "test") != null,
                            "the large INSERT should dispatch");
                })
                .thenExecuteAfter(4, () -> {
                    helper.assertTrue(mainframe.hasActiveOperations(), "an Operation should be in flight");
                    final var live = mainframe.activeOperationRecords();
                    helper.assertTrue(live.size() == 1, "exactly one in-flight Operation; got " + live.size());
                    final var rec = live.get(0);
                    helper.assertTrue(rec.status() == OperationRecord.STATUS_PROCESSING,
                            "an in-flight Operation reads PROCESSING");
                    helper.assertTrue(rec.requested() == 200_000L, "requested is the demand");
                    helper.assertTrue(rec.moved() > 0L && rec.moved() < 200_000L,
                            "moved reflects live progress; got " + rec.moved());
                    helper.assertTrue(rec.icon().is(Items.COBBLESTONE), "the icon is the moved item");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void localStorage_travelsWithDisk(final GameTestHelper helper) {
        final BlockPos pc = new BlockPos(2, 2, 2);
        final PersonalComputerBlockEntity computer = placeRunningPC(helper, pc);
        computer.getHardware().setStackInSlot(PersonalComputerBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1)));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(computer.localStore().insert(StorageKey.of(Items.COBBLESTONE), 100) == 100L,
                            "should store 100 in local storage");
                    helper.assertTrue(computer.localStore().used() == 100L, "local storage holds 100");
                    // Pull the disk out of the computer.
                    final ItemStack disk = computer.getHardware().extractItem(
                            PersonalComputerBlockEntity.DISK_SLOTS_START, 1, false);
                    helper.assertFalse(disk.isEmpty(), "the disk should come out");
                    // The items travel WITH the disk; local storage is empty without it.
                    final var contents = disk.get(ComputingModule.DISK_STORAGE.get());
                    helper.assertTrue(contents != null && contents.count(Items.COBBLESTONE) == 100L,
                            "the pulled disk must carry its 100 cobblestone");
                    helper.assertTrue(computer.localStore().used() == 0L,
                            "local storage is empty once the disk is removed; got " + computer.localStore().used());
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void networkStorage_preservesComponents(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(4, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.SOUTH)); // rear faces the cable to the north
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());

        final ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,
                net.minecraft.network.chat.Component.literal("Excalibur"));

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    final NetworkUuid net = mainframe.networkUuid();
                    final NetworkStorage ns = NetworkStorage.of(helper.getLevel(), net);
                    helper.assertTrue(ns.insert(named.copyWithCount(1)) == 1, "the named sword should store");
                    ns.insert(new ItemStack(Items.DIAMOND_SWORD, 1)); // a plain one too
                })
                .thenExecuteAfter(2, () -> {
                    final NetworkUuid net = mainframe.networkUuid();
                    final NetworkStorage ns = NetworkStorage.of(helper.getLevel(), net);
                    // The named and the plain sword are distinct keys, counted separately.
                    helper.assertTrue(ns.count(StorageKey.of(named)) == 1L,
                            "the named variant is its own key; got " + ns.count(StorageKey.of(named)));
                    helper.assertTrue(ns.count(Items.DIAMOND_SWORD) == 2L,
                            "two swords total across variants; got " + ns.count(Items.DIAMOND_SWORD));
                    // Pull the named one back out and confirm it kept its custom name.
                    final ItemStackHandler dest = new ItemStackHandler(4);
                    helper.assertTrue(ns.select(StorageKey.of(named), 1, new dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort(dest, null)) == 1L, "named SELECT moves 1");
                    final ItemStack out = dest.getStackInSlot(0);
                    helper.assertTrue(ItemStack.isSameItemSameComponents(out, named),
                            "the pulled sword must keep its components; got '" + out.getHoverName().getString() + "'");
                    helper.assertTrue(ns.count(Items.DIAMOND_SWORD) == 1L, "only the plain sword remains");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void cablePart_survivesReload(final GameTestHelper helper) {
        final BlockPos cablePos = new BlockPos(2, 2, 2);
        helper.setBlock(cablePos, ComputingModule.HBW_CABLE.get());
        if (!(helper.getBlockEntity(cablePos) instanceof DataCableBlockEntity cable)) {
            helper.fail("no cable block entity");
            return;
        }
        final ExportBusPart part = new ExportBusPart();
        cable.addPart(Direction.EAST, part);
        part.setFilter(new ItemStack(Items.COBBLESTONE));
        part.adjustMin(5);
        part.adjustMax(20);

        final var registries = helper.getLevel().registryAccess();
        final net.minecraft.nbt.CompoundTag saved = cable.saveWithFullMetadata(registries);
        final var reloaded = net.minecraft.world.level.block.entity.BlockEntity.loadStatic(
                helper.absolutePos(cablePos), cable.getBlockState(), saved, registries);
        helper.assertTrue(reloaded instanceof DataCableBlockEntity, "reloaded BE should be a cable");

        final DataCableBlockEntity back = (DataCableBlockEntity) reloaded;
        helper.assertTrue(back.hasPart(Direction.EAST), "the part must survive on the same face");
        helper.assertTrue(!back.hasPart(Direction.WEST), "no part should appear on an empty face");
        helper.assertTrue(back.getPart(Direction.EAST) instanceof ExportBusPart, "the part type must persist");
        final ExportBusPart reloadedPart = (ExportBusPart) back.getPart(Direction.EAST);
        helper.assertTrue(reloadedPart.filterItem() == Items.COBBLESTONE, "the filter must persist");
        helper.assertTrue(reloadedPart.getDataAccess().get(0) == 5, "min must persist");
        helper.assertTrue(reloadedPart.getDataAccess().get(1) == 20, "max must persist");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void networkIndex_catalogsServersAndLocks(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.SOUTH)); // rear faces the cable to the north
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 40))
                .thenExecuteAfter(4, () -> {
                    final long stored = rackBe.getServerStorage(0).count(Items.COBBLESTONE);
                    helper.assertTrue(stored > 0L, "the server should hold cobblestone; got " + stored);
                    final var index = mainframe.networkIndex();
                    helper.assertTrue(index.available(Items.COBBLESTONE) == stored,
                            "index must catalog the stored amount; got " + index.available(Items.COBBLESTONE)
                                    + " vs " + stored);

                    final java.util.UUID op = new java.util.UUID(0L, 7L);
                    final long want = stored / 2L;
                    final var plan = index.lock(op, Items.COBBLESTONE, want);
                    helper.assertTrue(plan.allocated() == want, "lock should reserve " + want);
                    helper.assertTrue(index.available(Items.COBBLESTONE) == stored - want,
                            "locked items must drop availability");

                    index.unlock(op);
                    helper.assertTrue(index.available(Items.COBBLESTONE) == stored,
                            "unlock must restore availability");
                    helper.assertTrue(!index.isLocked(op), "no lock should remain after unlock");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void networkSelect_movesItemsOverTimeAndUnlocks(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.SOUTH)); // rear faces the cable to the north
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        final BlockPos barrel = new BlockPos(4, 2, 2);
        helper.setBlock(barrel, net.minecraft.world.level.block.Blocks.BARREL);

        final long[] stored = {0L};
        final dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation[] op = {null};

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 40))
                .thenExecuteAfter(4, () -> {
                    stored[0] = rackBe.getServerStorage(0).count(Items.COBBLESTONE);
                    helper.assertTrue(stored[0] > 0L, "the server should hold cobblestone");
                    final var dest = helper.getLevel().getCapability(
                            net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                            helper.absolutePos(barrel), null);
                    helper.assertTrue(dest != null, "the barrel must expose an item handler");
                    op[0] = mainframe.submitNetworkSelect(Items.COBBLESTONE, stored[0], new dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort(dest, null), "select");
                    helper.assertTrue(op[0] != null, "the SELECT must be accepted");
                })
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(op[0].isDone(), "the SELECT must finish");
                    helper.assertTrue(op[0].status() == OperationRecord.STATUS_COMPLETED,
                            "the SELECT must complete fully; status " + op[0].status());
                    long inBarrel = 0L;
                    if (helper.getBlockEntity(barrel) instanceof net.minecraft.world.Container container) {
                        for (int i = 0; i < container.getContainerSize(); i++) {
                            if (container.getItem(i).is(Items.COBBLESTONE)) {
                                inBarrel += container.getItem(i).getCount();
                            }
                        }
                    }
                    helper.assertTrue(inBarrel == stored[0],
                            "every selected item must reach the barrel; got " + inBarrel + " of " + stored[0]);
                    helper.assertTrue(rackBe.getServerStorage(0).count(Items.COBBLESTONE) == 0L,
                            "the items must have left the server");
                    helper.assertTrue(!mainframe.networkIndex().isLocked(op[0].operationId()),
                            "the lock must be released on completion");
                    final var log = mainframe.recentOperations();
                    helper.assertTrue(!log.isEmpty() && log.get(0).type() == OperationRecord.TYPE_SELECT,
                            "the SELECT must be logged");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void networkInsert_writesItemsIntoServersOverTime(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.SOUTH)); // rear faces the cable to the north
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());

        final dev.jsc.jscomputronics.module.computing.operation.NetworkInsertOperation[] op = {null};

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () ->
                        op[0] = mainframe.submitNetworkInsert(Items.COBBLESTONE, 40, "you"))
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(op[0] != null && op[0].isDone(), "the INSERT must finish");
                    helper.assertTrue(op[0].status() == OperationRecord.STATUS_COMPLETED,
                            "the INSERT must store everything; status " + op[0].status());
                    helper.assertTrue(op[0].writtenTotal() == 40L,
                            "40 items must be written; got " + op[0].writtenTotal());
                    helper.assertTrue(rackBe.getServerStorage(0).count(Items.COBBLESTONE) == 40L,
                            "the server must hold the inserted items");
                    final var log = mainframe.recentOperations();
                    helper.assertTrue(!log.isEmpty() && log.get(0).type() == OperationRecord.TYPE_INSERT,
                            "the INSERT must be logged");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void networkInsert_failsAndReportsLeftoverWhenNetworkFull(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.SOUTH)); // rear faces the cable to the north
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    final var store = rackBe.getServerStorage(0);
                    store.insert(Items.COBBLESTONE, store.free()); // fill the only server to capacity
                    helper.assertTrue(store.free() == 0L, "the server must be full for this test");

                    final var op = mainframe.submitNetworkInsert(Items.DIRT, 16, "you");
                    helper.assertTrue(op != null && op.isDone(),
                            "an INSERT into a full network finishes immediately");
                    helper.assertTrue(op.status() == OperationRecord.STATUS_FAILED,
                            "nothing fit, so it FAILED; status " + op.status());
                    helper.assertTrue(op.writtenTotal() == 0L, "nothing was written");
                    helper.assertTrue(op.leftover() == 16L,
                            "the whole request is leftover; got " + op.leftover());
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void server_withoutCpu_servesNoNetworkStorage(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.SOUTH)); // rear faces the cable to the north
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.cpulessServer());

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 20))
                .thenExecuteAfter(6, () -> {
                    helper.assertTrue(mainframe.networkIndex().available(Items.COBBLESTONE) == 0L,
                            "a CPU-less Server must not appear in the network index");
                    final var net = mainframe.networkUuid();
                    helper.assertTrue(NetworkStorage.of(helper.getLevel(), net).count(Items.COBBLESTONE) == 0L,
                            "and it serves no storage to the network");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void networkDelete_pullsItemsOutAndLogsDelete(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos rack = new BlockPos(2, 2, 3); // behind the cable (rear-only connection)
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.SOUTH)); // rear faces the cable to the north
        if (!(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("no server rack");
            return;
        }
        rackBe.getServers().setStackInSlot(0, ComputingModule.defaultServer());
        final BlockPos barrel = new BlockPos(4, 2, 2);
        helper.setBlock(barrel, net.minecraft.world.level.block.Blocks.BARREL);

        final long[] stored = {0L};
        final dev.jsc.jscomputronics.module.computing.operation.NetworkSelectOperation[] op = {null};

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 32))
                .thenExecuteAfter(4, () -> {
                    stored[0] = rackBe.getServerStorage(0).count(Items.COBBLESTONE);
                    final var dest = helper.getLevel().getCapability(
                            net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                            helper.absolutePos(barrel), null);
                    op[0] = mainframe.submitNetworkDelete(Items.COBBLESTONE, stored[0], new dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort(dest, null), "export");
                    helper.assertTrue(op[0] != null, "the DELETE must be accepted");
                })
                .thenExecuteAfter(30, () -> {
                    helper.assertTrue(op[0].isDone() && op[0].status() == OperationRecord.STATUS_COMPLETED,
                            "the DELETE must complete");
                    helper.assertTrue(rackBe.getServerStorage(0).count(Items.COBBLESTONE) == 0L,
                            "the items must leave the network");
                    final var log = mainframe.recentOperations();
                    helper.assertTrue(!log.isEmpty() && log.get(0).type() == OperationRecord.TYPE_DELETE,
                            "it must be logged as a DELETE, not a SELECT");
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

    // Package-private so the performance benchmarks (PerformanceGameTests) can reuse the same powered-up
    // Mainframe setup without duplicating the hardware-install plumbing.
    static MainframeBlockEntity placeRunningMainframe(final GameTestHelper helper, final BlockPos relative) {
        return TestWorldBuilder.forGameTest(helper).placeRunningMainframe(relative);
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

    // Server Router (topology element: bridges faces, sections racks per face)

    @GameTest(template = ARENA)
    public static void serverRouter_bridgesNetworkAcrossFaces(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbwA = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos hbwB = new BlockPos(4, 2, 2);
        final BlockPos rack = new BlockPos(5, 2, 2);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbwA, ComputingModule.HBW_CABLE.get());
        helper.setBlock(router, ComputingModule.SERVER_ROUTER.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST)); // back (the uplink) faces the Mainframe cable to the west
        helper.setBlock(hbwB, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST)); // cables attach through the rear (west side here)
        seedServer(helper, rack);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    final NetworkUuid net = mainframe.networkUuid();
                    helper.assertTrue(net != null, "mainframe owns a network");
                    helper.assertTrue(sameNetwork(helper, hbwA, hbwB),
                            "the Server Router bridges the cables on its two faces into one network");
                    helper.assertTrue(networkOf(helper, router).map(net::equals).orElse(false),
                            "the router sits on the mainframe's network");
                    helper.assertTrue(NetworkSystem.get(helper.getLevel()).serversOf(net).size() == 1,
                            "the rack behind the router registers its Server on the mainframe network");
                    helper.assertTrue(NetworkSystem.get(helper.getLevel()).routersOf(net).size() == 1,
                            "the router registers itself as a topology element on the network");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void serverRouter_groupsRacksIntoSections(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbwIn = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos hbwEast = new BlockPos(4, 2, 2);
        final BlockPos rackEast = new BlockPos(5, 2, 2);
        final BlockPos hbwSouth = new BlockPos(3, 2, 3);
        final BlockPos rackSouth = new BlockPos(3, 2, 4);
        placeRunningMainframe(helper, m);
        helper.setBlock(hbwIn, ComputingModule.HBW_CABLE.get());
        helper.setBlock(router, ComputingModule.SERVER_ROUTER.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST)); // back (the uplink) faces the Mainframe cable to the west
        helper.setBlock(hbwEast, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rackEast, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST)); // rear faces the section cable to the west
        helper.setBlock(hbwSouth, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rackSouth, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.SOUTH)); // rear faces the section cable to the north
        seedServer(helper, rackEast);
        seedServer(helper, rackSouth);
        if (!(helper.getBlockEntity(router) instanceof ServerRouterBlockEntity routerBe)) {
            helper.fail("no server router");
            return;
        }
        helper.startSequence()
                // Let the racks register their Servers, then force a fresh topology compute.
                .thenExecuteAfter(SETTLE + 4, routerBe::recomputeNow)
                .thenExecute(() -> {
                    helper.assertTrue(routerBe.inputFace() == Direction.WEST,
                            "the back face is the dedicated uplink; got " + routerBe.inputFace());
                    final java.util.List<DatacenterSection> sections = routerBe.sections();
                    helper.assertTrue(sections.size() == 2,
                            "two output faces with racks form two sections; got " + sections.size());
                    for (final DatacenterSection section : sections) {
                        helper.assertTrue(section.rackCount() == 1,
                                "each section has one rack; got " + section.rackCount());
                        helper.assertTrue(section.serverCount() == 1,
                                "each section has one Server; got " + section.serverCount());
                    }
                    final java.util.Set<Direction> faces = new java.util.HashSet<>();
                    for (final DatacenterSection section : sections) {
                        faces.add(section.face());
                    }
                    helper.assertTrue(faces.contains(Direction.EAST) && faces.contains(Direction.SOUTH),
                            "sections hang off the EAST and SOUTH output faces; got " + faces);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void serverRouter_removalSplitsNetworkAndUnregisters(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbwA = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos hbwB = new BlockPos(4, 2, 2);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbwA, ComputingModule.HBW_CABLE.get());
        helper.setBlock(router, ComputingModule.SERVER_ROUTER.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST)); // back (the uplink) faces the Mainframe cable to the west
        helper.setBlock(hbwB, ComputingModule.HBW_CABLE.get());
        final NetworkUuid[] net = new NetworkUuid[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    net[0] = mainframe.networkUuid();
                    helper.assertTrue(net[0] != null, "mainframe owns a network");
                    helper.assertTrue(sameNetwork(helper, hbwA, hbwB),
                            "the router bridges the two cable runs while present");
                    helper.assertTrue(NetworkSystem.get(helper.getLevel()).routersOf(net[0]).size() == 1,
                            "the router is registered while present");
                })
                .thenExecute(() -> helper.setBlock(router, Blocks.AIR)) // remove the router
                .thenExecuteAfter(SETTLE + 2, () -> {
                    helper.assertFalse(sameNetwork(helper, hbwA, hbwB),
                            "removing the router splits its two cable runs apart");
                    helper.assertTrue(networkOf(helper, hbwB).isEmpty(),
                            "the far run, cut off from the Mainframe, becomes network-less");
                    helper.assertTrue(NetworkSystem.get(helper.getLevel()).routersOf(net[0]).isEmpty(),
                            "the removed router unregisters itself from the network");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void loadBalancer_roundRobinSpreadsAcrossServers(final GameTestHelper helper) {
        final BlockPos rackA = new BlockPos(2, 2, 2);
        final BlockPos rackB = new BlockPos(4, 2, 2);
        helper.setBlock(rackA, ComputingModule.SERVER_RACK.get());
        helper.setBlock(rackB, ComputingModule.SERVER_RACK.get());
        seedServer(helper, rackA);
        seedServer(helper, rackB);
        if (!(helper.getBlockEntity(rackA) instanceof ServerRackBlockEntity a)
                || !(helper.getBlockEntity(rackB) instanceof ServerRackBlockEntity b)) {
            helper.fail("no server racks");
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerStore sa = a.getServerStorage(0);
                    final ServerStore sb = b.getServerStorage(0);
                    final long stored = LoadBalancer.insert(java.util.List.of(sa, sb),
                            StorageKey.of(Items.COBBLESTONE), 128L, LoadBalanceMode.ROUND_ROBIN);
                    helper.assertTrue(stored == 128L, "round-robin should store all 128; got " + stored);
                    helper.assertTrue(sa.count(Items.COBBLESTONE) == 64L && sb.count(Items.COBBLESTONE) == 64L,
                            "round-robin should spread evenly (64/64); got "
                                    + sa.count(Items.COBBLESTONE) + "/" + sb.count(Items.COBBLESTONE));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void datacenterStation_bindsSectionAndSeesServers(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbwIn = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos hbwEast = new BlockPos(4, 2, 2);
        final BlockPos rack = new BlockPos(5, 2, 2);
        final BlockPos station = new BlockPos(2, 3, 2); // on top of the input cable, on the network
        placeRunningMainframe(helper, m);
        helper.setBlock(hbwIn, ComputingModule.HBW_CABLE.get());
        helper.setBlock(router, ComputingModule.SERVER_ROUTER.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST)); // back (the uplink) faces the Mainframe cable to the west
        helper.setBlock(hbwEast, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST)); // cables attach through the rear (west side here)
        seedServer(helper, rack);
        helper.setBlock(station, ComputingModule.DATACENTER_STATION.get());
        if (!(helper.getBlockEntity(station) instanceof DatacenterStationBlockEntity st)) {
            helper.fail("no datacenter station");
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    st.ensureBound();
                    helper.assertTrue(st.network() != null, "the Station reads its segment's network");
                    helper.assertTrue(st.section() != null, "the Station binds to the router's section");
                    helper.assertTrue(st.sectionServers().size() == 1,
                            "the bound section sees its one Server; got " + st.sectionServers().size());
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void datacenterStation_bindsTheBranchItSitsOn(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbwIn = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos hbwEast = new BlockPos(4, 2, 2);
        final BlockPos rackEast = new BlockPos(5, 2, 2);
        final BlockPos hbwSouth = new BlockPos(3, 2, 3);
        final BlockPos rackSouth = new BlockPos(3, 2, 4);
        final BlockPos station = new BlockPos(4, 3, 2); // touching the EAST branch cable
        placeRunningMainframe(helper, m);
        helper.setBlock(hbwIn, ComputingModule.HBW_CABLE.get());
        helper.setBlock(router, ComputingModule.SERVER_ROUTER.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST)); // back (the uplink) faces the Mainframe cable to the west
        helper.setBlock(hbwEast, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rackEast, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST)); // rear faces the section cable to the west
        helper.setBlock(hbwSouth, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rackSouth, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.SOUTH)); // rear faces the section cable to the north
        seedServer(helper, rackEast);
        seedServer(helper, rackSouth);
        helper.setBlock(station, ComputingModule.DATACENTER_STATION.get());
        if (!(helper.getBlockEntity(station) instanceof DatacenterStationBlockEntity st)) {
            helper.fail("no datacenter station");
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    st.ensureBound();
                    final DatacenterSection bound = st.section();
                    helper.assertTrue(bound != null, "the Station binds a section");
                    helper.assertTrue(bound.face() == Direction.EAST,
                            "the Station must bind the branch it is cabled into (EAST); got " + bound.face());
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void datacenterStation_sectionScopedSelectPulls(final GameTestHelper helper) {
        final BlockPos m = new BlockPos(1, 2, 2);
        final BlockPos hbwIn = new BlockPos(2, 2, 2);
        final BlockPos router = new BlockPos(3, 2, 2);
        final BlockPos hbwEast = new BlockPos(4, 2, 2);
        final BlockPos rack = new BlockPos(5, 2, 2);
        final BlockPos station = new BlockPos(2, 3, 2);
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, m);
        helper.setBlock(hbwIn, ComputingModule.HBW_CABLE.get());
        helper.setBlock(router, ComputingModule.SERVER_ROUTER.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST)); // back (the uplink) faces the Mainframe cable to the west
        helper.setBlock(hbwEast, ComputingModule.HBW_CABLE.get());
        helper.setBlock(rack, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST)); // cables attach through the rear (west side here)
        seedServer(helper, rack);
        helper.setBlock(station, ComputingModule.DATACENTER_STATION.get());
        if (!(helper.getBlockEntity(station) instanceof DatacenterStationBlockEntity st)
                || !(helper.getBlockEntity(rack) instanceof ServerRackBlockEntity rackBe)) {
            helper.fail("missing station or rack");
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    rackBe.getServerStorage(0).insert(Items.COBBLESTONE, 100);
                    st.ensureBound();
                })
                .thenExecuteAfter(2, () -> {
                    final java.util.Set<dev.jsc.jscomputronics.common.uuid.NodeUuid> sources =
                            new java.util.HashSet<>(st.sectionServers());
                    helper.assertTrue(!sources.isEmpty(), "the section resolves its Servers");
                    final ItemStackHandler dest = new ItemStackHandler(9);
                    final var op = mainframe.submitNetworkSelect(StorageKey.of(Items.COBBLESTONE), 40L,
                            new dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort(dest, null),
                            "datacenter", sources);
                    helper.assertTrue(op != null, "the section-scoped SELECT is dispatched");
                })
                .thenExecuteAfter(10, () -> {
                    final long left = NetworkStorage.of(helper.getLevel(), mainframe.networkUuid())
                            .count(Items.COBBLESTONE);
                    helper.assertTrue(left == 60L,
                            "the section-scoped SELECT pulled 40 from the section; left " + left);
                })
                .thenSucceed();
    }

    // Operation scheduling (parallel queues), LOCK contention, index maintenance

    @GameTest(template = ARENA, timeoutTicks = 140)
    public static void operationQueue_excessOpsStayPendingThenRun(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = storageNetwork(helper);
        final ItemStackHandler fullDest = fullHandler();
        final ItemStackHandler goodDest = new ItemStackHandler(9);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    seededRack(helper).getServerStorage(0).insert(Items.COBBLESTONE, 100);
                })
                .thenExecuteAfter(2, () -> {
                    // op1 occupies the single queue and stalls against the full destination.
                    helper.assertTrue(mainframe.submitNetworkSelect(Items.COBBLESTONE, 40,
                            port(fullDest), "full") != null, "op1 dispatched");
                    // op2 is ready (60 unlocked cobblestone cover its 30) but has no free queue.
                    helper.assertTrue(mainframe.submitNetworkSelect(Items.COBBLESTONE, 30,
                            port(goodDest), "good") != null, "op2 dispatched");
                })
                .thenExecuteAfter(6, () -> {
                    final var records = mainframe.activeOperationRecords();
                    helper.assertTrue(records.size() == 2, "both ops in flight; got " + records.size());
                    helper.assertTrue(records.get(0).status() == OperationRecord.STATUS_PROCESSING,
                            "op1 holds the queue (PROCESSING); got " + records.get(0).status());
                    helper.assertTrue(!records.get(0).subs().isEmpty(),
                            "the streaming op exposes its SubOperation rows");
                    helper.assertTrue(records.get(1).status() == OperationRecord.STATUS_PENDING,
                            "op2 queues behind the single queue (PENDING); got " + records.get(1).status());
                })
                .thenExecuteAfter(90, () -> {
                    final long left = NetworkStorage.of(helper.getLevel(), mainframe.networkUuid())
                            .count(Items.COBBLESTONE);
                    helper.assertTrue(left == 70,
                            "op1 moved nothing (full dest) and op2 moved its 30 after promotion; left " + left);
                    final var log = mainframe.recentOperations();
                    helper.assertTrue(log.size() >= 2, "both ops logged; got " + log.size());
                    helper.assertTrue(log.get(0).status() == OperationRecord.STATUS_COMPLETED
                                    && log.get(0).moved() == 30,
                            "op2 completed its 30 after the queue freed");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 140)
    public static void lockContention_waitsThenAcquiresWhenLockFrees(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = storageNetwork(helper);
        final ItemStackHandler fullDest = fullHandler();
        final ItemStackHandler goodDest = new ItemStackHandler(9);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () ->
                        seededRack(helper).getServerStorage(0).insert(Items.COBBLESTONE, 100))
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(mainframe.submitNetworkSelect(Items.COBBLESTONE, 100,
                            port(fullDest), "full") != null, "the lock holder dispatched");
                    helper.assertTrue(mainframe.submitNetworkSelect(Items.COBBLESTONE, 20,
                            port(goodDest), "good") != null, "the contender dispatched");
                })
                .thenExecuteAfter(6, () -> {
                    final var records = mainframe.activeOperationRecords();
                    helper.assertTrue(records.size() == 2, "both ops in flight; got " + records.size());
                    helper.assertTrue(records.get(1).status() == OperationRecord.STATUS_WAITING,
                            "the contender WAITs on the holder's lock; got " + records.get(1).status());
                })
                .thenExecuteAfter(90, () -> {
                    final long left = NetworkStorage.of(helper.getLevel(), mainframe.networkUuid())
                            .count(Items.COBBLESTONE);
                    helper.assertTrue(left == 80,
                            "the contender acquired the freed lock and moved its 20; left " + left);
                    helper.assertTrue(mainframe.recentOperations().get(0).status()
                                    == OperationRecord.STATUS_COMPLETED,
                            "the contender completed after acquiring");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void lockContention_timesOutAsResourceLocked(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = storageNetwork(helper);
        final ItemStackHandler fullDest = fullHandler();
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () ->
                        seededRack(helper).getServerStorage(0).insert(Items.COBBLESTONE, 100))
                .thenExecuteAfter(2, () -> helper.assertTrue(
                        mainframe.submitNetworkSelect(Items.COBBLESTONE, 100, port(fullDest), "full") != null,
                        "the lock holder dispatched"))
                .thenExecuteAfter(4, () -> {
                    // All 100 are locked by the stalled holder: this contender starts WAITING.
                    final var contender = new dev.jsc.jscomputronics.module.computing.operation
                            .NetworkSelectOperation(helper.getLevel(), mainframe.networkUuid(),
                            StorageKey.of(Items.COBBLESTONE), 50, port(new ItemStackHandler(9)), "test",
                            OperationRecord.TYPE_SELECT, java.util.UUID.randomUUID(),
                            mainframe.networkIndex(), null, null, 3);
                    helper.assertTrue(contender.isWaiting(), "the contender starts WAITING");
                    for (int i = 0; i < 5; i++) {
                        contender.tick(1_000L); // retries past its 3-tick timeout
                    }
                    helper.assertTrue(contender.isDone(), "the wait timed out");
                    helper.assertTrue(contender.status() == OperationRecord.STATUS_RESOURCE_LOCKED,
                            "timeout settles as RESOURCE_LOCKED; got " + contender.status());
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void analyzeIncremental_tracksDirectStoreWrites(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = storageNetwork(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () ->
                        seededRack(helper).getServerStorage(0).insert(Items.COBBLESTONE, 30))
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(mainframe.networkIndex().available(Items.COBBLESTONE) == 30,
                            "the catalog sees the direct insert; got "
                                    + mainframe.networkIndex().available(Items.COBBLESTONE));
                    seededRack(helper).getServerStorage(0).extract(Items.COBBLESTONE, 10);
                })
                .thenExecuteAfter(2, () -> helper.assertTrue(
                        mainframe.networkIndex().available(Items.COBBLESTONE) == 20,
                        "the catalog sees the direct extract; got "
                                + mainframe.networkIndex().available(Items.COBBLESTONE)))
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void vacuum_freesGhostEntries(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = storageNetwork(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () ->
                        seededRack(helper).getServerStorage(0).insert(Items.COBBLESTONE, 50))
                .thenExecuteAfter(2, () -> {
                    final NetworkUuid net = mainframe.networkUuid();
                    helper.assertTrue(mainframe.networkIndex().available(Items.COBBLESTONE) == 50,
                            "catalog populated before the ghost");
                    final var system = NetworkSystem.get(helper.getLevel());
                    final var node = system.serversOf(net).get(0).nodeUuid();
                    // Unregister the server: its catalog rows are now ghosts (same tick, no re-scan yet).
                    system.unregisterServer(net, node);
                    final int freed = mainframe.networkIndex().vacuum(helper.getLevel(), net);
                    helper.assertTrue(freed >= 1, "vacuum frees the ghost rows; freed " + freed);
                    helper.assertTrue(mainframe.networkIndex().available(Items.COBBLESTONE) == 0,
                            "the ghost no longer answers queries");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void drop_typeDestroysOnlyTargetType(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = storageNetwork(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    final ServerStore store = seededRack(helper).getServerStorage(0);
                    store.insert(Items.COBBLESTONE, 50);
                    store.insert(Items.DIRT, 30);
                })
                .thenExecuteAfter(2, () -> {
                    final long destroyed = mainframe.networkIndex().dropType(helper.getLevel(),
                            mainframe.networkUuid(), StorageKey.of(Items.COBBLESTONE), null);
                    helper.assertTrue(destroyed == 50, "dropType destroys all 50 cobblestone; got " + destroyed);
                    final ServerStore store = seededRack(helper).getServerStorage(0);
                    helper.assertTrue(store.count(Items.COBBLESTONE) == 0, "the dropped type is gone");
                    helper.assertTrue(store.count(Items.DIRT) == 30, "every other type is untouched");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void drop_allWipesNetworkAndIndexReflectsIt(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = storageNetwork(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    final ServerStore store = seededRack(helper).getServerStorage(0);
                    store.insert(Items.COBBLESTONE, 50);
                    store.insert(Items.DIRT, 30);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(mainframe.networkIndex().catalogSize() == 2,
                            "two types catalogued before the wipe; got " + mainframe.networkIndex().catalogSize());
                    final long destroyed = mainframe.networkIndex().dropAll(helper.getLevel(), mainframe.networkUuid());
                    helper.assertTrue(destroyed == 80, "dropAll destroys all 80 units; got " + destroyed);
                    helper.assertTrue(seededRack(helper).getServerStorage(0).used() == 0, "the server is emptied");
                })
                // The per-tick ANALYZE drops the now-empty rows from the catalog.
                .thenExecuteAfter(2, () -> helper.assertTrue(mainframe.networkIndex().catalogSize() == 0,
                        "the index reflects the wiped network; got " + mainframe.networkIndex().catalogSize()))
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void maintenance_indexStatsReflectNetwork(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = storageNetwork(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    final ServerStore store = seededRack(helper).getServerStorage(0);
                    store.insert(Items.COBBLESTONE, 50);
                    store.insert(Items.DIRT, 30);
                })
                .thenExecuteAfter(2, () -> {
                    final var index = mainframe.networkIndex();
                    helper.assertTrue(index.catalogSize() == 2, "2 types; got " + index.catalogSize());
                    helper.assertTrue(index.indexedServerCount() == 1, "1 server; got " + index.indexedServerCount());
                    helper.assertTrue(index.activeLockCount() == 0, "no locks idle; got " + index.activeLockCount());
                    helper.assertTrue(mainframe.indexedTypes() == 2 && mainframe.indexedServers() == 1,
                            "the host exposes the same stats to the terminal");
                })
                .thenSucceed();
    }

    private static MainframeBlockEntity storageNetwork(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = placeRunningMainframe(helper, new BlockPos(1, 2, 2));
        helper.setBlock(new BlockPos(2, 2, 2), ComputingModule.HBW_CABLE.get());
        helper.setBlock(new BlockPos(3, 2, 2), ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        Direction.EAST)); // cables attach through the rear (west side here)
        seedServer(helper, new BlockPos(3, 2, 2));
        return mainframe;
    }

    private static ServerRackBlockEntity seededRack(final GameTestHelper helper) {
        if (helper.getBlockEntity(new BlockPos(3, 2, 2)) instanceof ServerRackBlockEntity rack) {
            return rack;
        }
        throw new IllegalStateException("no rack at (3,2,2)");
    }

    private static ItemStackHandler fullHandler() {
        final ItemStackHandler handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, new ItemStack(Items.STICK, 64));
        return handler;
    }

    private static dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort port(
            final ItemStackHandler handler) {
        return new dev.jsc.jscomputronics.module.computing.storage.ExternalDataPort(handler, null);
    }

    private static void seedServer(final GameTestHelper helper, final BlockPos rack) {
        TestWorldBuilder.forGameTest(helper).seedServer(rack);
    }

    @GameTest(template = ARENA)
    public static void computer_acceptsCableOnRearFaceOnly(final GameTestHelper helper) {
        final BlockPos pc = new BlockPos(2, 2, 2);
        helper.setBlock(pc, ComputingModule.PERSONAL_COMPUTER.get().defaultBlockState()
                .setValue(net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING,
                        net.minecraft.core.Direction.NORTH));
        final net.minecraft.world.level.block.state.BlockState state = helper.getBlockState(pc);
        final dev.jsc.jscomputronics.common.network.DataNetworkConnectable block =
                (dev.jsc.jscomputronics.common.network.DataNetworkConnectable) state.getBlock();
        // A north-facing computer's rear is south: only that face takes a cable.
        helper.assertTrue(block.connectsOnFace(state, net.minecraft.core.Direction.SOUTH),
                "the rear (south) face must accept a cable");
        helper.assertFalse(block.connectsOnFace(state, net.minecraft.core.Direction.NORTH),
                "the front must reject a cable");
        helper.assertFalse(block.connectsOnFace(state, net.minecraft.core.Direction.EAST),
                "a side must reject a cable");
        helper.assertFalse(block.connectsOnFace(state, net.minecraft.core.Direction.UP),
                "the top must reject a cable");
        // The Mainframe is the exception: it still takes a cable on any face.
        final BlockPos mf = new BlockPos(4, 2, 2);
        helper.setBlock(mf, ComputingModule.MAINFRAME.get());
        final net.minecraft.world.level.block.state.BlockState mfState = helper.getBlockState(mf);
        if (mfState.getBlock() instanceof dev.jsc.jscomputronics.common.network.DataNetworkConnectable mainframe) {
            helper.assertTrue(mainframe.connectsOnFace(mfState, net.minecraft.core.Direction.EAST),
                    "the Mainframe accepts a cable on any face");
            helper.assertTrue(mainframe.connectsOnFace(mfState, net.minecraft.core.Direction.UP),
                    "the Mainframe accepts a cable on any face");
        }
        helper.succeed();
    }

    /**
     * Turns a just-placed computer so its rear (its only data port) meets an adjacent horizontal
     * cable. Computers now connect through the back face alone, so a test that drops one beside a
     * cable must orient it; this keeps the fixtures declaring "computer next to cable" working.
     */
    private static void faceRearTowardCable(final GameTestHelper helper, final BlockPos pos) {
        TestWorldBuilder.forGameTest(helper).faceRearTowardCable(pos);
    }

    private static PersonalComputerBlockEntity placeRunningPC(final GameTestHelper helper, final BlockPos relative) {
        return TestWorldBuilder.forGameTest(helper).placeRunningPersonalComputer(relative);
    }

    // Package-private so the performance benchmarks can reuse the powered-up Crafting Computer setup.
    static CraftingComputerBlockEntity placeRunningCraftingComputer(
            final GameTestHelper helper, final BlockPos relative) {
        return TestWorldBuilder.forGameTest(helper).placeRunningCraftingComputer(relative);
    }

    private static void installValidBuild(final MainframeBlockEntity be) {
        TestWorldBuilder.installMainframeBuild(be);
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

    private static NetworkUuidState registryState(final GameTestHelper helper, final NetworkUuid uuid) {
        return NetworkRegistrySavedData.get(helper.getLevel()).networkState(uuid);
    }
}
