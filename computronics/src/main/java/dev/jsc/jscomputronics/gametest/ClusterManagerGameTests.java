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
import dev.jstech.core.network.NetworkSystem;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.HardwareItems;
import dev.jsc.jscomputronics.module.computing.blockentity.ClusterManagementComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.ClusterManagementComputerBlockEntity.ClusterRef;
import dev.jsc.jscomputronics.module.computing.blockentity.ClusterManagementComputerBlockEntity.InstallJob;
import dev.jsc.jscomputronics.module.computing.blockentity.ClusterManagementComputerBlockEntity.JobKind;
import dev.jsc.jscomputronics.module.computing.blockentity.ClusterManagementComputerBlockEntity.NodeRef;
import dev.jsc.jscomputronics.module.computing.blockentity.ClusterManagementComputerBlockEntity.SectionRef;
import dev.jsc.jscomputronics.module.computing.blockentity.HbwInterfaceBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRouterBlockEntity;
import dev.jsc.jscomputronics.module.computing.item.ServerHardwareHandler;
import dev.jsc.jscomputronics.module.computing.item.ServerItem;
import dev.jsc.jscomputronics.module.computing.operation.payload.ClusterManagerStatePayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.ComputingPayloads;
import dev.jsc.jscomputronics.module.computing.os.OsHost;
import dev.jsc.jscomputronics.module.computing.os.media.MediaItem;
import dev.jsc.jscomputronics.module.computing.os.media.MediaKind;
import dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlockEntity;
import dev.jsc.jscomputronics.module.computing.program.ServerCliComputer;
import dev.jsc.jscomputronics.module.computing.program.cli.CliCommands;
import dev.jsc.jscomputronics.module.computing.program.cli.CliLine;
import dev.jsc.jscomputronics.module.computing.program.cli.CliShell;
import dev.jsc.jscomputronics.module.computing.rack.RackChassis;
import dev.jsc.jscomputronics.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The Cluster Management Computer runs the racks as one machine, and only with a Cluster Interface
 * Card: it reaches every supercomputer and datacenter section on its network, writes a system or a
 * program to their nodes a few at a time over real time, and switches their bays in bulk. Every
 * action goes through the same per-node hosts a player reaches rack by rack, so what the manager
 * does by itself must equal what a player could do by hand — a shortcut, never a loophole.
 */
@GameTestHolder(JsComputronics.MODID)
@PrefixGameTestTemplate(false)
public final class ClusterManagerGameTests {

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;
    /** A bulk install on the nodes the tests seat takes a few hundred ticks; this leaves ample margin. */
    private static final int INSTALL_TIMEOUT = 1600;

    // The backbone: a running Mainframe and two bandwidth cables, the manager on the second one.
    private static final BlockPos MAINFRAME = new BlockPos(1, 2, 3);
    private static final BlockPos CABLE_A = new BlockPos(2, 2, 3);
    private static final BlockPos CABLE_B = new BlockPos(3, 2, 3);
    private static final BlockPos MANAGER = new BlockPos(3, 2, 2);
    private static final BlockPos READER_EAST = new BlockPos(4, 2, 2);
    private static final BlockPos READER_NORTH = new BlockPos(3, 2, 1);
    // A supercomputer on the backbone: its interface on the second cable, a fabric cable, a cabinet above it.
    private static final BlockPos HUB = new BlockPos(3, 2, 4);
    private static final BlockPos FABRIC = new BlockPos(4, 2, 4);
    private static final BlockPos NODE_RACK = new BlockPos(4, 3, 4);
    // A datacenter section on the backbone: a router uplinked to the second cable, one section to its east.
    private static final BlockPos ROUTER = new BlockPos(4, 2, 3);
    private static final BlockPos SECTION_CABLE = new BlockPos(5, 2, 3);
    private static final BlockPos SERVER_RACK = new BlockPos(6, 2, 3);

    private static final ResourceLocation DEBIAN = ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "debian");
    private static final ResourceLocation MINESWEEPER =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "minesweeper");
    private static final ResourceLocation MC_DOS = ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "mc_dos");

    private ClusterManagerGameTests() {
    }

    // ---- fixtures ----

    /**
     * Places the backbone and the manager on it — rear to the cable, the same consumer build a PC runs on,
     * {@code card} in its first expansion slot (none when empty) — and powers it on.
     */
    private static ClusterManagementComputerBlockEntity placeBackbone(final GameTestHelper helper,
                                                                       final ItemStack card) {
        return placeBackbone(helper, card, null);
    }

    /** As above, with a disk and the system {@code os} installed before power-on so the manager boots into it. */
    private static ClusterManagementComputerBlockEntity placeBackbone(final GameTestHelper helper,
                                                                       final ItemStack card,
                                                                       final ResourceLocation os) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        world.placeRunningMainframe(MAINFRAME);
        helper.setBlock(CABLE_A, ComputingModule.HBW_CABLE.get());
        helper.setBlock(CABLE_B, ComputingModule.HBW_CABLE.get());
        helper.setBlock(MANAGER, ComputingModule.CLUSTER_MANAGEMENT_COMPUTER.get());
        world.faceRearTowardCable(MANAGER);
        final ClusterManagementComputerBlockEntity manager = managerAt(helper);
        final ItemStackHandler hardware = manager.getHardware();
        hardware.setStackInSlot(ClusterManagementComputerBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(ComputingModule.MOTHERBOARD_ATX_P.get()));
        hardware.setStackInSlot(ClusterManagementComputerBlockEntity.CPU_SLOT,
                new ItemStack(ComputingModule.CPU_ASCENT_965.get()));
        hardware.setStackInSlot(ClusterManagementComputerBlockEntity.RAM_SLOTS_START,
                new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        hardware.setStackInSlot(ClusterManagementComputerBlockEntity.PSU_SLOT,
                new ItemStack(ComputingModule.PSU_650G.get()));
        if (!card.isEmpty()) {
            hardware.setStackInSlot(ClusterManagementComputerBlockEntity.PCIE_SLOTS_START, card);
        }
        if (os != null) {
            hardware.setStackInSlot(ClusterManagementComputerBlockEntity.DISK_SLOTS_START,
                    new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
            if (!manager.installOs(os)) {
                throw new IllegalStateException("could not install " + os + " on the manager");
            }
        }
        manager.togglePower();
        return manager;
    }

    private static ItemStack fabricHostAdapter() {
        return new ItemStack(ComputingModule.FABRIC_HOST_ADAPTER.get());
    }

    /** A supercomputer on the backbone with two nodes seated (rows 0 and 2), each with a drive to install to. */
    private static ServerRackBlockEntity placeSupercomputer(final GameTestHelper helper) {
        helper.setBlock(HUB, ComputingModule.HBW_INTERFACE.get());
        helper.setBlock(FABRIC, ComputingModule.HPC_CABLE.get());
        helper.setBlock(NODE_RACK, ComputingModule.SUPERCOMPUTER_RACK.get());
        final ServerRackBlockEntity rack = rackAt(helper, NODE_RACK);
        for (final int row : new int[] {0, 2}) {
            rack.getServers().setStackInSlot(row, ComputingModule.defaultSupercomputerNode());
            if (!rack.insertDrive(row, new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1)))) {
                throw new IllegalStateException("the node in row " + row + " took no drive");
            }
        }
        return rack;
    }

    /** A datacenter section on the backbone: the router's east branch, with a cabinet holding {@code servers}. */
    private static ServerRackBlockEntity placeDatacenter(final GameTestHelper helper, final int servers) {
        helper.setBlock(ROUTER, ComputingModule.SERVER_ROUTER.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST)); // back (the uplink) meets the backbone
        helper.setBlock(SECTION_CABLE, ComputingModule.HBW_CABLE.get());
        helper.setBlock(SERVER_RACK, ComputingModule.SERVER_RACK.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST)); // rear meets the section cable
        final ServerRackBlockEntity rack = rackAt(helper, SERVER_RACK);
        mountServers(rack, servers);
        return rack;
    }

    private static void mountServers(final ServerRackBlockEntity rack, final int servers) {
        for (int row = 0; row < servers; row++) {
            TestWorldBuilder.mountDefaultServer(rack, row);
        }
    }

    /** Places a reader beside the manager, links it as a peripheral, and loads a disc of the given kind. */
    private static void linkReaderWithDisc(final GameTestHelper helper, final BlockPos readerPos,
                                           final MediaKind kind, final ResourceLocation payload) {
        helper.setBlock(readerPos, ComputingModule.CD_DRIVE.get());
        if (!(helper.getBlockEntity(readerPos) instanceof MediaReaderBlockEntity reader)) {
            throw new IllegalStateException("no reader at " + readerPos);
        }
        final ClusterManagementComputerBlockEntity manager = managerAt(helper);
        manager.peripheralEndpoints().add(helper.absolutePos(readerPos).asLong());
        reader.onOwnerLinked(helper.absolutePos(MANAGER).asLong());
        final ItemStack disc = new ItemStack(ComputingModule.CD_ROM.get());
        MediaItem.setKind(disc, kind);
        MediaItem.setPayload(disc, payload);
        reader.mediaSlot().setStackInSlot(0, disc);
    }

    private static ClusterManagementComputerBlockEntity managerAt(final GameTestHelper helper) {
        if (!(helper.getBlockEntity(MANAGER) instanceof ClusterManagementComputerBlockEntity manager)) {
            throw new IllegalStateException("no Cluster Management Computer at " + MANAGER);
        }
        return manager;
    }

    private static ServerRackBlockEntity rackAt(final GameTestHelper helper, final BlockPos pos) {
        if (!(helper.getBlockEntity(pos) instanceof ServerRackBlockEntity rack)) {
            throw new IllegalStateException("no rack at " + pos);
        }
        return rack;
    }

    /** Takes the coprocessor out of the node seated in {@code row}, leaving the rest of the machine intact. */
    private static void pullCoprocessor(final ServerRackBlockEntity rack, final int row) {
        final ItemStack node = rack.getServers().getStackInSlot(row);
        final ItemContainerContents current = ServerItem.hardware(node);
        final NonNullList<ItemStack> parts = NonNullList.withSize(ServerHardwareHandler.SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < ServerHardwareHandler.SLOTS && i < current.getSlots(); i++) {
            parts.set(i, current.getStackInSlot(i).copy());
        }
        parts.set(ServerHardwareHandler.GPU_START, ItemStack.EMPTY);
        node.set(ComputingModule.SERVER_HARDWARE.get(), ItemContainerContents.fromItems(parts));
        rack.setChanged();
    }

    private static ClusterRef supercomputerRef(final ClusterManagementComputerBlockEntity manager) {
        final List<HbwInterfaceBlockEntity> hubs = manager.supercomputers();
        if (hubs.isEmpty()) {
            throw new IllegalStateException("the manager's network shows no supercomputer");
        }
        return new ClusterRef(RackChassis.RackType.SUPERCOMPUTER, hubs.get(0).getBlockPos(), ClusterRef.NO_FACE);
    }

    private static ClusterRef sectionRef(final GameTestHelper helper) {
        return new ClusterRef(RackChassis.RackType.SERVER, helper.absolutePos(ROUTER), Direction.EAST);
    }

    private static Set<Integer> rowsOf(final List<NodeRef> nodes) {
        return nodes.stream().map(NodeRef::row).collect(Collectors.toSet());
    }

    private static int nodesRunning(final ServerRackBlockEntity rack, final int rows, final ResourceLocation os) {
        int count = 0;
        for (int row = 0; row < rows; row++) {
            if (os.equals(rack.unitHost(row).installedOsId())) {
                count++;
            }
        }
        return count;
    }

    // ---- the card ----

    @GameTest(template = ARENA)
    public static void manager_isAnOrdinaryComputerWithoutTheCard(final GameTestHelper helper) {
        final ClusterManagementComputerBlockEntity manager = placeBackbone(helper, ItemStack.EMPTY);
        placeSupercomputer(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    helper.assertTrue(manager.isRunning(), "the manager boots like any computer");
                    helper.assertTrue(manager.networkUuid() != null, "it joins the network through its rear port");
                    helper.assertTrue(manager.clusterCard() == null && !manager.managerReady()
                                    && manager.parallelLanes() == 0,
                            "without a card there is nothing to manage with");
                    helper.assertTrue(manager.supercomputers().size() == 1,
                            "the network still shows the supercomputer; got " + manager.supercomputers().size());
                    final ClusterRef ref = supercomputerRef(manager);
                    helper.assertTrue(!manager.reaches(RackChassis.RackType.SUPERCOMPUTER)
                                    && manager.powerAll(ref, false) == 0,
                            "without a card the bays are out of reach");
                    helper.assertTrue(manager.startJob(ref, JobKind.SYSTEM).equals("no cluster interface card"),
                            "a job needs the card first");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void manager_refusesASecondClusterCard(final GameTestHelper helper) {
        helper.setBlock(MANAGER, ComputingModule.CLUSTER_MANAGEMENT_COMPUTER.get());
        final ClusterManagementComputerBlockEntity manager = managerAt(helper);
        final int first = ClusterManagementComputerBlockEntity.PCIE_SLOTS_START;
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(manager.isValidForSlot(first, fabricHostAdapter()),
                            "an empty machine takes a cluster card in an expansion slot");
                    manager.getHardware().setStackInSlot(first, fabricHostAdapter());
                    helper.assertTrue(!manager.isValidForSlot(first + 1, fabricHostAdapter())
                                    && !manager.isValidForSlot(first + 1,
                                            new ItemStack(ComputingModule.MANAGEMENT_NIC.get())),
                            "a second cluster card of any model is refused: one per machine");
                    helper.assertTrue(manager.isValidForSlot(first, fabricHostAdapter()),
                            "the slot holding the card still accepts a card (a swap)");
                })
                .thenSucceed();
    }

    // ---- reach over the network ----

    @GameTest(template = ARENA)
    public static void manager_seesTheSupercomputerOverTheNetwork(final GameTestHelper helper) {
        final ClusterManagementComputerBlockEntity manager = placeBackbone(helper, fabricHostAdapter());
        final ServerRackBlockEntity rack = placeSupercomputer(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    helper.assertTrue(manager.managerReady(), "running with a card: ready to manage");
                    helper.assertTrue(manager.reaches(RackChassis.RackType.SUPERCOMPUTER)
                                    && manager.reaches(RackChassis.RackType.SERVER),
                            "the Fabric Host Adapter reaches every kind of cabinet");
                    helper.assertTrue(manager.parallelLanes() >= 1 && manager.parallelLanes() <= 4,
                            "the adapter's four lanes, cut by the slot's bandwidth, never below one; got "
                                    + manager.parallelLanes());
                    final List<HbwInterfaceBlockEntity> hubs = manager.supercomputers();
                    helper.assertTrue(hubs.size() == 1 && hubs.get(0).getBlockPos().equals(helper.absolutePos(HUB)),
                            "the interface on the backbone is the network's one supercomputer");
                    helper.assertTrue(hubs.get(0).clusterOnline(), "the cluster is online on the network");
                    final ClusterRef ref = supercomputerRef(manager);
                    final List<NodeRef> nodes = manager.nodesOf(ref);
                    helper.assertTrue(nodes.size() == 2 && rowsOf(nodes).equals(Set.of(0, 2))
                                    && nodes.get(0).rack().equals(helper.absolutePos(NODE_RACK)),
                            "the two seated nodes are the cluster's rows 0 and 2");
                    final var hosts = manager.hostsOf(ref);
                    helper.assertTrue(hosts.size() == 2
                                    && hosts.containsKey(new NodeRef(helper.absolutePos(NODE_RACK), 0))
                                    && hosts.containsKey(new NodeRef(helper.absolutePos(NODE_RACK), 2)),
                            "each node is addressed as a host of its own, keyed by rack row");
                    helper.assertTrue(hosts.get(new NodeRef(helper.absolutePos(NODE_RACK), 0)).installedOsId() == null
                                    && rack.unitHost(0).installedOsId() == null,
                            "the host is the rack's own unit host (a fresh node, no system yet)");
                    helper.assertTrue(manager.datacenterSections().isEmpty(), "no router, no sections");
                    helper.assertTrue(manager.medium(MediaKind.OS_INSTALL) == null,
                            "no linked reader means no install medium");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void manager_addressesTheDatacenterSectionAndItsBays(final GameTestHelper helper) {
        final ClusterManagementComputerBlockEntity manager = placeBackbone(helper, fabricHostAdapter());
        final ServerRackBlockEntity rack = placeDatacenter(helper, 1);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    final List<SectionRef> sections = manager.datacenterSections();
                    helper.assertTrue(sections.size() == 1, "the router's one cabled face is one section; got "
                            + sections.size());
                    final SectionRef section = sections.get(0);
                    helper.assertTrue(section.routerPos().equals(helper.absolutePos(ROUTER))
                                    && section.face() == Direction.EAST && section.label().equals("Router · EAST"),
                            "the section carries its router, face and label; got " + section.label());
                    helper.assertTrue(manager.sectionAt(helper.absolutePos(ROUTER), Direction.EAST) != null
                                    && manager.sectionAt(helper.absolutePos(ROUTER), Direction.SOUTH) == null,
                            "sections are addressed by router and face");
                    helper.assertTrue(manager.supercomputers().isEmpty(), "no interface, no supercomputer");
                    final ClusterRef ref = sectionRef(helper);
                    final List<NodeRef> nodes = manager.nodesOf(ref);
                    helper.assertTrue(nodes.size() == 1 && nodes.get(0).rack().equals(helper.absolutePos(SERVER_RACK))
                                    && nodes.get(0).row() == 0,
                            "the section's server is found in its cabinet row");
                    helper.assertTrue(rack.bayPowerOn(0), "the bay starts on");
                    helper.assertTrue(manager.powerAll(ref, false) == 1, "power-all-off flips the one bay");
                    helper.assertTrue(!rack.bayPowerOn(0), "the bay is off");
                    helper.assertTrue(manager.powerAll(ref, false) == 0, "an off section has nothing left to switch");
                    // The off bay has left the network, but the manager still lists it — or it could never
                    // switch it back on from here.
                    helper.assertTrue(manager.nodesOf(ref).size() == 1,
                            "an off bay stays in the section's node list; got " + manager.nodesOf(ref).size());
                    helper.assertTrue(manager.toggleNode(helper.absolutePos(SERVER_RACK), 0) && rack.bayPowerOn(0),
                            "a single node answers to its cabinet and row");
                    helper.assertTrue(!manager.toggleNode(helper.absolutePos(SERVER_RACK), 5),
                            "an empty row is not a node to switch");
                })
                .thenSucceed();
    }

    /**
     * A supercomputer cabinet answers to the high-compute fabric only. A data cable on its port neither
     * connects (the cable draws no link into it) nor puts its nodes on the data network as servers; the
     * fabric, once its HBW Interface is on the network, is what lights the cabinet's link.
     */
    @GameTest(template = ARENA)
    public static void supercomputerRack_takesTheFabricNotTheDataCable(final GameTestHelper helper) {
        placeBackbone(helper, fabricHostAdapter());
        // A data cable run east of the backbone, ending at the rear of a supercomputer cabinet.
        helper.setBlock(ROUTER, ComputingModule.HBW_CABLE.get());
        helper.setBlock(SECTION_CABLE, ComputingModule.HBW_CABLE.get());
        helper.setBlock(SERVER_RACK, ComputingModule.SUPERCOMPUTER_RACK.get().defaultBlockState()
                .setValue(HorizontalDirectionalBlock.FACING, Direction.EAST));
        final ServerRackBlockEntity onData = rackAt(helper, SERVER_RACK);
        onData.getServers().setStackInSlot(0, ComputingModule.defaultSupercomputerNode());
        // And the real thing: a cabinet on the fabric behind an interface.
        final ServerRackBlockEntity onFabric = placeSupercomputer(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    if (!(helper.getBlockEntity(MAINFRAME) instanceof MainframeBlockEntity mainframe)) {
                        throw new IllegalStateException("no Mainframe at " + MAINFRAME);
                    }
                    helper.assertTrue(!helper.getBlockState(SECTION_CABLE).getValue(PipeBlock.EAST),
                            "the data cable draws no connection into a compute cabinet");
                    helper.assertTrue(NetworkSystem.get(helper.getLevel()).serversOf(mainframe.networkUuid()).isEmpty(),
                            "a node in a cabinet on a data cable is not a server on the network");
                    helper.assertTrue(onData.getDataAccess().get(ServerRackBlockEntity.DATA_LINKED) == 0,
                            "the data cable does not light the compute cabinet's link");
                    helper.assertTrue(onFabric.getDataAccess().get(ServerRackBlockEntity.DATA_LINKED) == 1,
                            "the fabric behind a networked interface does");
                })
                .thenSucceed();
    }

    /**
     * A section and a supercomputer go by the names the player gives them: a section's lives on its router,
     * per face; a supercomputer's on its interface. Both are written with their blocks, the shell finds a
     * cluster by its new name, and an empty name goes back to the default.
     */
    @GameTest(template = ARENA)
    public static void manager_callsClustersByThePlayersNames(final GameTestHelper helper) {
        final ClusterManagementComputerBlockEntity manager = placeBackbone(helper, fabricHostAdapter(), MC_DOS);
        placeDatacenter(helper, 1);
        placeSupercomputer(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    if (!(helper.getBlockEntity(ROUTER) instanceof ServerRouterBlockEntity router)) {
                        throw new IllegalStateException("no router at " + ROUTER);
                    }
                    final HbwInterfaceBlockEntity hub = manager.supercomputers().get(0);
                    helper.assertTrue(manager.datacenterSections().get(0).label().equals("Router · EAST")
                                    && ClusterManagementComputerBlockEntity.supercomputerName(hub, 0).equals("SC-1"),
                            "unnamed clusters go by router and face, or by number");
                    router.setSectionName(Direction.EAST, "Cold aisle");
                    hub.setCustomName("Kraken");
                    helper.assertTrue(manager.datacenterSections().get(0).label().equals("Cold aisle"),
                            "the section takes the player's name");
                    helper.assertTrue(ClusterManagementComputerBlockEntity.supercomputerName(hub, 0).equals("Kraken"),
                            "the supercomputer takes the player's name");
                    final CliShell shell = CliCommands.newShell(52);
                    final ServerCliComputer cli = new ServerCliComputer(manager, helper.getLevel());
                    final String listed = text(shell.run("cluster list", cli));
                    helper.assertTrue(listed.contains("Kraken") && listed.contains("Cold aisle"),
                            "the shell lists clusters by their names; got " + listed);
                    helper.assertTrue(text(shell.run("cluster nodes Kraken", cli)).contains("U1"),
                            "the shell finds a supercomputer by its name");
                    final var registries = helper.getLevel().registryAccess();
                    helper.assertTrue("Cold aisle".equals(router.saveWithoutMetadata(registries)
                                    .getCompound("SectionNames").getString("east"))
                                    && "Kraken".equals(hub.saveWithoutMetadata(registries).getString("CustomName")),
                            "both names are written with their blocks");
                    router.setSectionName(Direction.EAST, "");
                    helper.assertTrue(manager.datacenterSections().get(0).label().equals("Router · EAST"),
                            "an empty name goes back to the default");
                })
                .thenSucceed();
    }

    /**
     * The manager has to say what each machine is doing, not just what is on its disk: a bay the player
     * switched off, a machine with no system yet, and one that is up all look the same on a disk listing.
     */
    @GameTest(template = ARENA)
    public static void manager_reportsWhatEachMachineIsDoing(final GameTestHelper helper) {
        final ClusterManagementComputerBlockEntity manager = placeBackbone(helper, fabricHostAdapter());
        final ServerRackBlockEntity rack = placeDatacenter(helper, 2);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    helper.assertTrue(rack.unitHost(0).installOs(MC_DOS), "the first server takes a system");
                    rack.toggleBayPower(1); // the second is switched off at the bay
                })
                .thenExecuteAfter(4, () -> {
                    final var state = ComputingPayloads.buildClusterManagerState(manager, helper.getLevel(),
                            ClusterManagerStatePayload.KIND_DATACENTER, 0, "");
                    final List<ClusterManagerStatePayload.WireNode> nodes = state.detail().nodes();
                    helper.assertTrue(nodes.size() == 2, "both servers are listed; got " + nodes.size());
                    helper.assertTrue(nodes.get(0).state() == ClusterManagerStatePayload.STATE_ONLINE,
                            "a powered server with a system reads ONLINE; got " + nodes.get(0).state());
                    helper.assertTrue(nodes.get(1).state() == ClusterManagerStatePayload.STATE_BAY_OFF,
                            "a server whose bay is off reads BAY OFF; got " + nodes.get(1).state());
                    rack.toggleBayPower(1);
                })
                .thenExecuteAfter(4, () -> {
                    final var state = ComputingPayloads.buildClusterManagerState(manager, helper.getLevel(),
                            ClusterManagerStatePayload.KIND_DATACENTER, 0, "");
                    helper.assertTrue(state.detail().nodes().get(1).state()
                                    == ClusterManagerStatePayload.STATE_NO_SYSTEM,
                            "switched back on with an empty disk it reads NO SYSTEM; got "
                                    + state.detail().nodes().get(1).state());
                })
                .thenSucceed();
    }

    /**
     * A supercomputer node is only worth a cluster slot with a coprocessor in it. The manager must name that
     * — a node seated, powered and systemless otherwise looks the same as one that is pulling its weight.
     */
    @GameTest(template = ARENA)
    public static void manager_namesASupercomputerNodeWithoutItsCoprocessor(final GameTestHelper helper) {
        final ClusterManagementComputerBlockEntity manager = placeBackbone(helper, fabricHostAdapter());
        final ServerRackBlockEntity rack = placeSupercomputer(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    final var state = ComputingPayloads.buildClusterManagerState(manager, helper.getLevel(),
                            ClusterManagerStatePayload.KIND_SUPERCOMPUTER, 0, "");
                    final List<ClusterManagerStatePayload.WireNode> nodes = state.detail().nodes();
                    helper.assertTrue(!nodes.isEmpty(), "the cluster lists its nodes");
                    helper.assertTrue(nodes.get(0).state() == ClusterManagerStatePayload.STATE_NO_SYSTEM,
                            "a seated node with its coprocessor and no system reads NO SYSTEM; got "
                                    + nodes.get(0).state());
                    pullCoprocessor(rack, 0); // the node keeps its slot but stops counting for crafts
                })
                .thenExecuteAfter(6, () -> {
                    final var state = ComputingPayloads.buildClusterManagerState(manager, helper.getLevel(),
                            ClusterManagerStatePayload.KIND_SUPERCOMPUTER, 0, "");
                    helper.assertTrue(state.detail().nodes().get(0).state()
                                    == ClusterManagerStatePayload.STATE_NO_COPROCESSOR,
                            "without a coprocessor the node reads NO PHI; got "
                                    + state.detail().nodes().get(0).state());
                })
                .thenSucceed();
    }

    // ---- install jobs ----

    @GameTest(template = ARENA, timeoutTicks = INSTALL_TIMEOUT)
    public static void manager_installsTheSystemOnEveryNodeOverTime(final GameTestHelper helper) {
        final ClusterManagementComputerBlockEntity manager = placeBackbone(helper, fabricHostAdapter());
        final ServerRackBlockEntity rack = placeSupercomputer(helper);
        linkReaderWithDisc(helper, READER_EAST, MediaKind.OS_INSTALL, DEBIAN);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    final var medium = manager.medium(MediaKind.OS_INSTALL);
                    helper.assertTrue(medium != null && medium.id().equals(DEBIAN) && medium.label().equals("Debian"),
                            "the manager reads the system disc from its linked reader");
                    final ClusterRef ref = supercomputerRef(manager);
                    final String status = manager.startJob(ref, JobKind.SYSTEM);
                    helper.assertTrue(status.equals("installing Debian on 2 nodes"), "the job starts; got " + status);
                    helper.assertTrue(manager.job() != null && manager.job().total() == 2, "two nodes to write");
                    helper.assertTrue(manager.startJob(ref, JobKind.SYSTEM).equals("a job is already running"),
                            "one job at a time");
                })
                .thenExecuteAfter(2, () -> {
                    final InstallJob job = manager.job();
                    helper.assertTrue(job != null, "the job is still running");
                    final int lanes = Math.min(2, manager.parallelLanes());
                    helper.assertTrue(job.lanes().size() == lanes && job.queued() == 2 - lanes,
                            "the card's lanes fill first, the rest wait; lanes=" + job.lanes().size()
                                    + " queued=" + job.queued());
                    final var lane = job.lanes().get(0);
                    helper.assertTrue(lane.ticksTotal() >= 60 && lane.ticksTotal() <= 1200 && lane.permille() < 1000,
                            "writing takes real, bounded time; total=" + lane.ticksTotal());
                    helper.assertTrue(rack.unitHost(0).installedOsId() == null
                                    && rack.unitHost(2).installedOsId() == null,
                            "nothing lands before a lane finishes");
                })
                .thenWaitUntil(() -> helper.assertTrue(manager.job() == null, "the job finishes"))
                .thenExecute(() -> {
                    helper.assertTrue(DEBIAN.equals(rack.unitHost(0).installedOsId())
                                    && DEBIAN.equals(rack.unitHost(2).installedOsId()),
                            "each node's drive now carries the system");
                    helper.assertTrue(manager.lastJobSummary().equals("done: Debian on 2 nodes"),
                            "the summary counts both; got " + manager.lastJobSummary());
                    // A second pass brings the cluster to the system again: nodes already on it are left alone.
                    helper.assertTrue(manager.startJob(supercomputerRef(manager), JobKind.SYSTEM)
                            .startsWith("installing"), "a second pass starts");
                })
                .thenWaitUntil(() -> helper.assertTrue(manager.job() == null, "the second pass finishes"))
                .thenExecute(() -> helper.assertTrue(
                        manager.lastJobSummary().equals("done: Debian on 0 nodes, 2 skipped"),
                        "nodes already running the system are skipped; got " + manager.lastJobSummary()))
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = INSTALL_TIMEOUT)
    public static void manager_programNeedsASystemUnderneath(final GameTestHelper helper) {
        final ClusterManagementComputerBlockEntity manager = placeBackbone(helper, fabricHostAdapter());
        final ServerRackBlockEntity rack = placeSupercomputer(helper);
        linkReaderWithDisc(helper, READER_EAST, MediaKind.OS_INSTALL, DEBIAN);
        linkReaderWithDisc(helper, READER_NORTH, MediaKind.PROGRAM_INSTALL, MINESWEEPER);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    final var program = manager.medium(MediaKind.PROGRAM_INSTALL);
                    helper.assertTrue(program != null && program.label().equals("Minesweeper"),
                            "the program disc is read from the second linked reader");
                    // The system lands on one node by hand; the program must reach it and skip the bare one.
                    helper.assertTrue(rack.unitHost(0).installOs(DEBIAN), "the first node takes the system");
                    final String status = manager.startJob(supercomputerRef(manager), JobKind.PROGRAM);
                    helper.assertTrue(status.equals("installing Minesweeper on 2 nodes"), "the job starts; got " + status);
                })
                .thenWaitUntil(() -> helper.assertTrue(manager.job() == null, "the job finishes"))
                .thenExecute(() -> {
                    final OsHost first = rack.unitHost(0);
                    helper.assertTrue(first.console() != null && first.console().isInstalled(MINESWEEPER.toString()),
                            "the node with a system has the program");
                    helper.assertTrue(rack.unitHost(2).installedOsId() == null, "the bare node is untouched");
                    helper.assertTrue(manager.lastJobSummary().equals("done: Minesweeper on 1 node, 1 skipped"),
                            "one written, one skipped for having no system; got " + manager.lastJobSummary());
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void manager_skipsNodesWhoseBayIsOff(final GameTestHelper helper) {
        final ClusterManagementComputerBlockEntity manager = placeBackbone(helper, fabricHostAdapter());
        placeDatacenter(helper, 1);
        linkReaderWithDisc(helper, READER_EAST, MediaKind.OS_INSTALL, DEBIAN);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 6, () -> {
                    final ClusterRef ref = sectionRef(helper);
                    helper.assertTrue(manager.powerAll(ref, false) == 1, "the server's bay goes off");
                    final String status = manager.startJob(ref, JobKind.SYSTEM);
                    helper.assertTrue(status.equals("installing Debian on 1 node"), "the job starts; got " + status);
                })
                .thenExecuteAfter(2, () -> {
                    helper.assertTrue(manager.job() == null, "a node with its bay off is skipped at once");
                    helper.assertTrue(manager.lastJobSummary().equals("done: Debian on 0 nodes, 1 skipped"),
                            "the summary says so; got " + manager.lastJobSummary());
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = INSTALL_TIMEOUT)
    public static void manager_cancelKeepsTheNodesAlreadyBeingWritten(final GameTestHelper helper) {
        final ClusterManagementComputerBlockEntity manager = placeBackbone(helper, fabricHostAdapter());
        final ServerRackBlockEntity rack = placeDatacenter(helper, 0);
        linkReaderWithDisc(helper, READER_EAST, MediaKind.OS_INSTALL, DEBIAN);
        final int[] lanes = new int[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    // One more server than the card writes at once, so exactly one waits in the queue.
                    lanes[0] = manager.parallelLanes();
                    helper.assertTrue(lanes[0] >= 1, "the card gives at least one lane");
                    mountServers(rack, lanes[0] + 1);
                })
                .thenExecuteAfter(SETTLE + 6, () -> {
                    final ClusterRef ref = sectionRef(helper);
                    helper.assertTrue(manager.nodesOf(ref).size() == lanes[0] + 1,
                            "every server registers in the section; got " + manager.nodesOf(ref).size());
                    final String status = manager.startJob(ref, JobKind.SYSTEM);
                    helper.assertTrue(status.startsWith("installing Debian on " + (lanes[0] + 1)),
                            "the job starts; got " + status);
                })
                .thenExecuteAfter(2, () -> {
                    final InstallJob job = manager.job();
                    helper.assertTrue(job != null && job.lanes().size() == lanes[0] && job.queued() == 1,
                            "the lanes are full and one server waits");
                    helper.assertTrue(manager.cancelJob(), "the job takes the cancel");
                    helper.assertTrue(job.cancelled() && job.queued() == 0 && job.lanes().size() == lanes[0],
                            "cancelling empties the queue and leaves the lanes to finish");
                })
                .thenWaitUntil(() -> helper.assertTrue(manager.job() == null, "the lanes finish"))
                .thenExecute(() -> {
                    helper.assertTrue(nodesRunning(rack, lanes[0] + 1, DEBIAN) == lanes[0],
                            "the servers being written got the system; the queued one did not");
                    helper.assertTrue(manager.lastJobSummary().equals("cancelled: Debian on " + lanes[0]
                                    + " node" + (lanes[0] == 1 ? "" : "s")),
                            "the summary says cancelled; got " + manager.lastJobSummary());
                    helper.assertTrue(!manager.cancelJob(), "nothing left to cancel");
                })
                .thenSucceed();
    }

    // ---- reach is the card's, not the network's ----

    /**
     * The Vintage manager on the backbone: an AT board (the era's PCI bus, Socket 7) with the era's
     * Serial Console Card, powered on.
     */
    private static ClusterManagementComputerBlockEntity placeVintageManagerOnBackbone(final GameTestHelper helper) {
        final TestWorldBuilder world = TestWorldBuilder.forGameTest(helper);
        world.placeRunningMainframe(MAINFRAME);
        helper.setBlock(CABLE_A, ComputingModule.HBW_CABLE.get());
        helper.setBlock(CABLE_B, ComputingModule.HBW_CABLE.get());
        helper.setBlock(MANAGER, ComputingModule.VINTAGE_CLUSTER_MANAGEMENT_COMPUTER.get());
        world.faceRearTowardCable(MANAGER);
        final ClusterManagementComputerBlockEntity manager = managerAt(helper);
        final ItemStackHandler hardware = manager.getHardware();
        hardware.setStackInSlot(ClusterManagementComputerBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(HardwareItems.MOTHERBOARD_AT_VINTAGE.get()));
        hardware.setStackInSlot(ClusterManagementComputerBlockEntity.CPU_SLOT,
                new ItemStack(HardwareItems.CPU_VELOCION_K6_II.get()));
        hardware.setStackInSlot(ClusterManagementComputerBlockEntity.RAM_SLOTS_START,
                new ItemStack(HardwareItems.RAM_SIMM_4.get()));
        hardware.setStackInSlot(ClusterManagementComputerBlockEntity.PSU_SLOT,
                new ItemStack(HardwareItems.PSU_300B.get()));
        hardware.setStackInSlot(ClusterManagementComputerBlockEntity.PCIE_SLOTS_START,
                new ItemStack(ComputingModule.SERIAL_CONSOLE_CARD.get()));
        manager.togglePower();
        return manager;
    }

    @GameTest(template = ARENA)
    public static void manager_reachIsTheCardsNotTheNetworks(final GameTestHelper helper) {
        final ClusterManagementComputerBlockEntity manager = placeVintageManagerOnBackbone(helper);
        final ServerRackBlockEntity rack = placeSupercomputer(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    helper.assertTrue(manager.managerReady(),
                            "a Vintage manager runs on AT hardware with the era's serial card");
                    helper.assertTrue(manager.reaches(RackChassis.RackType.SERVER)
                                    && !manager.reaches(RackChassis.RackType.SUPERCOMPUTER),
                            "the Serial Console Card drives rack servers, not fabrics");
                    helper.assertTrue(manager.parallelLanes() == 1, "and one node at a time; got "
                            + manager.parallelLanes());
                    helper.assertTrue(manager.supercomputers().size() == 1,
                            "the network shows the supercomputer whatever the card reaches");
                    final ClusterRef ref = supercomputerRef(manager);
                    helper.assertTrue(manager.powerAll(ref, false) == 0 && rack.bayPowerOn(0) && rack.bayPowerOn(2),
                            "out of the card's reach the bays stay untouched");
                    helper.assertTrue(!manager.toggleNode(helper.absolutePos(NODE_RACK), 0) && rack.bayPowerOn(0),
                            "a single node out of reach does not answer either");
                    helper.assertTrue(manager.startJob(ref, JobKind.SYSTEM).equals("this card does not reach that cluster"),
                            "a job on a cluster out of reach is refused up front");
                })
                .thenSucceed();
    }

    // ---- the shell ----

    @GameTest(template = ARENA)
    public static void clusterCommand_drivesTheClustersFromTheManagersShellOnly(final GameTestHelper helper) {
        final ClusterManagementComputerBlockEntity manager = placeBackbone(helper, fabricHostAdapter(), MC_DOS);
        final ServerRackBlockEntity rack = placeSupercomputer(helper);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    final CliShell shell = CliCommands.newShell(52);
                    final ServerCliComputer cli = new ServerCliComputer(manager, helper.getLevel());
                    final String listed = text(shell.run("cluster list", cli));
                    helper.assertTrue(listed.contains("supercomputer") && listed.contains("SC-1")
                                    && listed.contains("online"),
                            "'cluster list' names the supercomputer and its state; got " + listed);
                    final String nodes = text(shell.run("cluster nodes SC-1", cli));
                    helper.assertTrue(nodes.contains("U1") && nodes.contains("U3") && nodes.contains("none"),
                            "'cluster nodes' lists each seated node with its unit and system; got " + nodes);
                    final String off = text(shell.run("cluster power SC-1 off", cli));
                    helper.assertTrue(off.contains("2 bays switched off") && !rack.bayPowerOn(0) && !rack.bayPowerOn(2),
                            "'cluster power <name> off' switches every bay; got " + off);
                    final String one = text(shell.run("cluster power SC-1 R1:U3 on", cli));
                    helper.assertTrue(one.contains("switched on") && rack.bayPowerOn(2) && !rack.bayPowerOn(0),
                            "a rack:unit address switches one bay; got " + one);
                    final String status = text(shell.run("cluster status", cli));
                    helper.assertTrue(status.contains("no job running"),
                            "'cluster status' with nothing running says so; got " + status);
                    final String install = text(shell.run("cluster install SC-1 system", cli));
                    helper.assertTrue(install.contains("no system disc"),
                            "an install with no disc says why; got " + install);
                    // On any other computer the command does not exist.
                    if (!(helper.getBlockEntity(MAINFRAME) instanceof MainframeBlockEntity mainframe)) {
                        throw new IllegalStateException("no Mainframe at " + MAINFRAME);
                    }
                    final String elsewhere = text(shell.run("cluster list",
                            new ServerCliComputer(mainframe, helper.getLevel())));
                    helper.assertTrue(elsewhere.contains("command not found: cluster"),
                            "another machine's shell has no 'cluster'; got " + elsewhere);
                })
                .thenSucceed();
    }

    private static String text(final CliShell.Response response) {
        return response.lines().stream().map(CliLine::text).collect(Collectors.joining("\n"));
    }
}
