/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jsc.jscomputronics.common.hardware.ClusterInterfaceCardSpec;
import dev.jsc.jscomputronics.common.hardware.ComputerBuild;
import dev.jsc.jscomputronics.common.hardware.ExpansionCardKind;
import dev.jsc.jscomputronics.common.hardware.ExpansionCardSpec;
import dev.jsc.jscomputronics.common.hardware.FormFactor;
import dev.jsc.jscomputronics.common.hardware.PcieGeneration;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.tier.HardwareEra;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.ClusterManagementComputerBlock;
import dev.jsc.jscomputronics.module.computing.datacenter.DatacenterSection;
import dev.jsc.jscomputronics.module.computing.item.ClusterInterfaceCardItem;
import dev.jsc.jscomputronics.module.computing.item.MotherboardItem;
import dev.jsc.jscomputronics.module.computing.item.ServerItem;
import dev.jsc.jscomputronics.module.computing.os.OsDef;
import dev.jsc.jscomputronics.module.computing.os.OsGating;
import dev.jsc.jscomputronics.module.computing.os.OsHost;
import dev.jsc.jscomputronics.module.computing.os.OsRegistry;
import dev.jsc.jscomputronics.module.computing.os.ProgramSpec;
import dev.jsc.jscomputronics.module.computing.os.media.MediaKind;
import dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlockEntity;
import dev.jsc.jscomputronics.module.computing.rack.RackChassis;
import dev.jsc.jscomputronics.module.computing.storage.DataSink;
import dev.jsc.jscomputronics.module.computing.storage.LocalStore;
import dev.jsc.jscomputronics.module.computing.storage.StoreSink;
import dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Cluster Management Computer's machine: an ordinary computer (board, CPU, RAM, disks, a GPU to sit
 * at it) that, with a Cluster Interface Card, reaches every cluster on its network — each HBW Interface
 * is a supercomputer, each router section a datacenter — and drives their nodes as one machine: bulk
 * installs that run as timed jobs, bay power in bulk or one by one, the craft queues. Everything it does
 * goes through the same per-node hosts a player reaches rack by rack, with the same gates: a shortcut,
 * never a loophole. A cluster works without one.
 */
public class ClusterManagementComputerBlockEntity extends AbstractComputerBlockEntity implements ComputerTerminalHost {

    // The same consumer/workstation slot layout as a Personal Computer.
    public static final int MOTHERBOARD_SLOT = 0;
    public static final int CPU_SLOT = 1;
    public static final int RAM_SLOTS_START = 2;
    public static final int RAM_SLOTS = 4;
    public static final int PCIE_SLOTS_START = 6;
    public static final int PCIE_SLOTS = 4;
    public static final int PSU_SLOT = 10;
    public static final int DISK_SLOTS_START = 11;
    public static final int DISK_SLOTS = 2;
    public static final int HARDWARE_SLOTS = 13;
    public static final int STORAGE_SLOTS = 18;

    private static final ComputerHardwareLayout LAYOUT = new ComputerHardwareLayout(
            MOTHERBOARD_SLOT, CPU_SLOT, 1, RAM_SLOTS_START, RAM_SLOTS,
            PCIE_SLOTS_START, PCIE_SLOTS, PSU_SLOT, DISK_SLOTS_START, DISK_SLOTS, HARDWARE_SLOTS);

    /** How long writing a system to a node takes on a 2 GHz node; scaled by the node's own CPU. */
    private static final int INSTALL_TICKS_AT_2GHZ = 280;
    private static final int INSTALL_TICKS_MIN = 60;
    private static final int INSTALL_TICKS_MAX = 1200;

    public ClusterManagementComputerBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.CLUSTER_MANAGEMENT_COMPUTER_BE.get(), pos, state, LAYOUT);
    }

    private HardwareEra blockEra() {
        return getBlockState().getBlock() instanceof ClusterManagementComputerBlock block
                ? block.era() : HardwareEra.STANDARD;
    }

    @Override
    protected Set<FormFactor> acceptedFormFactors() {
        // A workstation-class machine: Vintage on Baby-AT/AT, Legacy and Standard on ATX or EATX.
        return switch (blockEra()) {
            case VINTAGE -> Set.of(FormFactor.BABY_AT, FormFactor.AT);
            default -> Set.of(FormFactor.ATX, FormFactor.EATX);
        };
    }

    @Override
    protected HardwareEra requiredBoardEra() {
        return blockEra();
    }

    @Override
    public boolean isValidForSlot(final int slot, final ItemStack stack) {
        // One cluster card per machine: a second would add nothing, so the slot refuses it outright.
        if (stack.getItem() instanceof ClusterInterfaceCardItem
                && slot >= PCIE_SLOTS_START && slot < PCIE_SLOTS_START + PCIE_SLOTS) {
            for (int i = PCIE_SLOTS_START; i < PCIE_SLOTS_START + PCIE_SLOTS; i++) {
                if (i != slot && getHardware().getStackInSlot(i).getItem() instanceof ClusterInterfaceCardItem) {
                    return false;
                }
            }
        }
        return super.isValidForSlot(slot, stack);
    }

    public static void serverTick(final Level level, final BlockPos pos, final BlockState state,
                                  final ClusterManagementComputerBlockEntity be) {
        if (level instanceof ServerLevel serverLevel) {
            be.tickNode(serverLevel);
            be.tickJob();
        }
    }

    @Override
    protected void registerNode(final NetworkSystem system, final NetworkUuid network) {
        // On the network it is a computer with local storage, addressed like a PC.
        system.registerPersonalComputer(new NetworkSystem.PersonalComputerNode(
                nodeUuid(), network, capacity(), worldPosition.asLong()));
    }

    @Override
    protected void unregisterNode(final NetworkSystem system, final NetworkUuid network) {
        system.unregisterPersonalComputer(network, nodeUuid());
    }

    // ---- the card ----

    /** The installed Cluster Interface Card, or null. */
    @Nullable
    public ClusterInterfaceCardSpec clusterCard() {
        final ComputerBuild build = currentBuild();
        if (build == null) {
            return null;
        }
        for (final ExpansionCardSpec card : build.cardsOfKind(ExpansionCardKind.CLUSTER_INTERFACE)) {
            if (card instanceof ClusterInterfaceCardSpec spec) {
                return spec;
            }
        }
        return null;
    }

    /** Whether this machine can manage anything: running, with a card. */
    public boolean managerReady() {
        return isRunning() && clusterCard() != null;
    }

    /** Whether the card reaches cabinets of the given kind. */
    public boolean reaches(final RackChassis.RackType kind) {
        final ClusterInterfaceCardSpec card = clusterCard();
        return card != null && card.reach().covers(kind);
    }

    /**
     * How many nodes a bulk install writes at once: the card's lanes, cut by the bandwidth penalty of a
     * card sitting in an older slot than it was made for (the same rule the GPUs follow), never below one.
     */
    public int parallelLanes() {
        final ClusterInterfaceCardSpec card = clusterCard();
        if (card == null) {
            return 0;
        }
        double factor = 1.0;
        if (getHardware().getStackInSlot(MOTHERBOARD_SLOT).getItem() instanceof MotherboardItem board) {
            final PcieGeneration slot = board.spec().pcieGeneration();
            factor = card.bus().bandwidthFactorIn(slot);
        }
        return Math.max(1, (int) Math.floor(card.parallelNodes() * factor));
    }

    // ---- what the network reaches ----

    /** A cluster the machine can address: a supercomputer (its interface) or a datacenter section. */
    public record ClusterRef(RackChassis.RackType kind, BlockPos anchor, @Nullable Direction face) {
        public static final Direction NO_FACE = null;
    }

    /** A rack row holding a node or server. */
    public record NodeRef(BlockPos rack, int row) {
    }

    /** Every supercomputer interface on this machine's network, in registry order. */
    public List<HbwInterfaceBlockEntity> supercomputers() {
        final List<HbwInterfaceBlockEntity> hubs = new ArrayList<>();
        if (!(level instanceof ServerLevel serverLevel) || networkUuid() == null) {
            return hubs;
        }
        for (final NetworkSystem.SupercomputerNode node : NetworkSystem.get(serverLevel).supercomputersOf(networkUuid())) {
            if (serverLevel.getBlockEntity(BlockPos.of(node.pos())) instanceof HbwInterfaceBlockEntity hub) {
                hubs.add(hub);
            }
        }
        return hubs;
    }

    /** The name a supercomputer goes by: the player's, or its number in registry order (SC-1, SC-2 ...). */
    public static String supercomputerName(final HbwInterfaceBlockEntity hub, final int index) {
        return hub.customName().isEmpty() ? "SC-" + (index + 1) : hub.customName();
    }

    /** A datacenter section the machine can address, with the label the router gives it. */
    public record SectionRef(BlockPos routerPos, Direction face, String label, DatacenterSection section) {
    }

    // The tick the routers' section caches were last forced up to date; see datacenterSections().
    private long routersRefreshedAt = Long.MIN_VALUE;

    /** Every datacenter section on this machine's network, router by router. */
    public List<SectionRef> datacenterSections() {
        final List<SectionRef> refs = new ArrayList<>();
        if (!(level instanceof ServerLevel serverLevel) || networkUuid() == null) {
            return refs;
        }
        final NetworkSystem system = NetworkSystem.get(serverLevel);
        // A router rebuilds its sections on its own only about once a second. A manager acting on a
        // section must see the servers that registered this tick, not a second-old picture, so the
        // caches are forced up to date first — once per tick, however many times this is asked.
        final long now = serverLevel.getGameTime();
        final boolean refresh = now != routersRefreshedAt;
        routersRefreshedAt = now;
        for (final dev.jsc.jscomputronics.common.network.ServerRouterElement router : system.routersOf(networkUuid())) {
            if (serverLevel.getBlockEntity(BlockPos.of(router.pos())) instanceof ServerRouterBlockEntity routerBe) {
                if (refresh) {
                    routerBe.recomputeNow();
                }
                final String routerName = routerBe.customName().isEmpty() ? "Router" : routerBe.customName();
                for (final DatacenterSection section : routerBe.sections()) {
                    final String custom = routerBe.sectionName(section.face());
                    refs.add(new SectionRef(BlockPos.of(router.pos()), section.face(), custom.isEmpty()
                            ? routerName + " · " + section.face().getName().toUpperCase(Locale.ROOT) : custom, section));
                }
            }
        }
        return refs;
    }

    @Nullable
    public HbwInterfaceBlockEntity supercomputerAt(final BlockPos anchor) {
        return level != null && level.getBlockEntity(anchor) instanceof HbwInterfaceBlockEntity hub ? hub : null;
    }

    @Nullable
    public SectionRef sectionAt(final BlockPos routerPos, @Nullable final Direction face) {
        for (final SectionRef ref : datacenterSections()) {
            if (ref.routerPos().equals(routerPos) && ref.face() == face) {
                return ref;
            }
        }
        return null;
    }

    /** The rack rows a cluster is made of, in cluster order. */
    public List<NodeRef> nodesOf(final ClusterRef ref) {
        final List<NodeRef> nodes = new ArrayList<>();
        if (!(level instanceof ServerLevel serverLevel)) {
            return nodes;
        }
        if (ref.kind() == RackChassis.RackType.SUPERCOMPUTER) {
            final HbwInterfaceBlockEntity hub = supercomputerAt(ref.anchor());
            if (hub != null) {
                for (final HbwInterfaceBlockEntity.NodeRef node : hub.clusterNodes()) {
                    nodes.add(new NodeRef(node.rack(), node.row()));
                }
            }
            return nodes;
        }
        final SectionRef section = sectionAt(ref.anchor(), ref.face());
        if (section != null) {
            // Every seated server in the section's cabinets, switched on or off: a bay the manager powered
            // off has left the network, and must still be listed so the manager can power it back on.
            for (final long rackPos : section.section().rackPositions()) {
                if (serverLevel.getBlockEntity(BlockPos.of(rackPos)) instanceof ServerRackBlockEntity rack) {
                    for (final int row : rack.computerSlots()) {
                        nodes.add(new NodeRef(rack.getBlockPos(), row));
                    }
                }
            }
        }
        return nodes;
    }

    /** Each node of a cluster as a host of its own, keyed by rack row. */
    public Map<NodeRef, OsHost> hostsOf(final ClusterRef ref) {
        final Map<NodeRef, OsHost> hosts = new LinkedHashMap<>();
        for (final NodeRef node : nodesOf(ref)) {
            if (level != null && level.getBlockEntity(node.rack()) instanceof ServerRackBlockEntity rack) {
                hosts.put(node, rack.unitHost(node.row()));
            }
        }
        return hosts;
    }

    @Nullable
    private ServerRackBlockEntity rackAt(final BlockPos pos) {
        return level != null && level.getBlockEntity(pos) instanceof ServerRackBlockEntity rack ? rack : null;
    }

    // ---- install media: the discs in the readers linked to this machine ----

    public record Medium(ResourceLocation id, String label) {
    }

    @Nullable
    public Medium medium(final MediaKind kind) {
        if (level == null) {
            return null;
        }
        for (final long endpoint : linkedEndpoints()) {
            if (level.getBlockEntity(BlockPos.of(endpoint)) instanceof MediaReaderBlockEntity reader
                    && reader.insertedKind() == kind && reader.insertedPayload() != null) {
                final ResourceLocation id = reader.insertedPayload();
                return new Medium(id, labelFor(kind, id));
            }
        }
        return null;
    }

    private static String labelFor(final MediaKind kind, final ResourceLocation id) {
        if (kind == MediaKind.OS_INSTALL) {
            final OsDef os = OsRegistry.getOs(id);
            return os != null ? os.displayName() : id.getPath();
        }
        final ProgramSpec program = OsRegistry.getProgram(id);
        return program != null ? program.displayName() : id.getPath();
    }

    // ---- bulk power ----

    /** Switches every node's bay on or off; returns how many changed. */
    public int powerAll(final ClusterRef ref, final boolean on) {
        if (!reaches(ref.kind())) {
            return 0;
        }
        int changed = 0;
        for (final NodeRef node : nodesOf(ref)) {
            final ServerRackBlockEntity rack = rackAt(node.rack());
            if (rack != null && rack.bayPowerOn(node.row()) != on) {
                rack.toggleBayPower(node.row());
                changed++;
            }
        }
        return changed;
    }

    /** Flips one node's bay; the node must be a computer in a cabinet the card reaches. */
    public boolean toggleNode(final BlockPos rackPos, final int row) {
        final ServerRackBlockEntity rack = rackAt(rackPos);
        if (rack == null || !reaches(rack.rackType()) || !rack.computerSlots().contains(row)) {
            return false;
        }
        rack.toggleBayPower(row);
        return true;
    }

    // ---- the install job: timed, per node, cancellable ----

    public enum JobKind { SYSTEM, PROGRAM }

    /** One node being written right now. */
    public record Lane(NodeRef node, String name, int ticksTotal, int ticksLeft) {
        public int permille() {
            return ticksTotal <= 0 ? 1000 : (int) (1000L * (ticksTotal - ticksLeft) / ticksTotal);
        }
    }

    /** A bulk install in flight: what it writes, where, how far it got. Transient: a reload drops it. */
    public static final class InstallJob {
        final JobKind kind;
        final Medium medium;
        final ClusterRef cluster;
        final List<NodeRef> queue;
        final List<Lane> lanes = new ArrayList<>();
        final List<String> skippedNames = new ArrayList<>();
        int done;
        int total;
        boolean cancelled;
        int ticks;

        InstallJob(final JobKind kind, final Medium medium, final ClusterRef cluster, final List<NodeRef> targets) {
            this.kind = kind;
            this.medium = medium;
            this.cluster = cluster;
            this.queue = new ArrayList<>(targets);
            this.total = targets.size();
        }

        public JobKind kind() {
            return kind;
        }

        public Medium medium() {
            return medium;
        }

        public ClusterRef cluster() {
            return cluster;
        }

        public int done() {
            return done;
        }

        public int skipped() {
            return skippedNames.size();
        }

        public int queued() {
            return queue.size();
        }

        public int total() {
            return total;
        }

        public List<Lane> lanes() {
            return List.copyOf(lanes);
        }

        public boolean cancelled() {
            return cancelled;
        }

        public int elapsedTicks() {
            return ticks;
        }

        boolean finished() {
            return queue.isEmpty() && lanes.isEmpty();
        }
    }

    @Nullable
    private InstallJob job;
    private String lastJobSummary = "";

    @Nullable
    public InstallJob job() {
        return job;
    }

    /** The one-line outcome of the last job, for the status bar and the shell. */
    public String lastJobSummary() {
        return lastJobSummary;
    }

    /**
     * Starts a bulk install on a cluster from the medium in a linked reader. Returns a status line:
     * the job started, or why it did not.
     */
    public String startJob(final ClusterRef ref, final JobKind kind) {
        return startJob(ref, kind, null);
    }

    /**
     * Starts a bulk install on the given rows of a cluster (every row when {@code only} is null).
     * Returns a status line: the job started, or why it did not.
     */
    public String startJob(final ClusterRef ref, final JobKind kind, @Nullable final List<NodeRef> only) {
        if (!managerReady()) {
            return "no cluster interface card";
        }
        if (job != null) {
            return "a job is already running";
        }
        if (!reaches(ref.kind())) {
            return "this card does not reach that cluster";
        }
        final Medium medium = medium(kind == JobKind.SYSTEM ? MediaKind.OS_INSTALL : MediaKind.PROGRAM_INSTALL);
        if (medium == null) {
            return kind == JobKind.SYSTEM ? "no system disc in a linked reader" : "no program disc in a linked reader";
        }
        if (kind == JobKind.SYSTEM) {
            final OsDef os = OsRegistry.getOs(medium.id());
            if (os == null || os.installMode() != dev.jsc.jscomputronics.module.computing.os.InstallMode.GUIDED) {
                return "that system installs by hand from its own shell";
            }
        } else {
            final ProgramSpec program = OsRegistry.getProgram(medium.id());
            if (program == null || program.preinstalled()) {
                return "that disc carries nothing to install";
            }
        }
        final List<NodeRef> targets = only != null ? only : nodesOf(ref);
        if (targets.isEmpty()) {
            return "no nodes in that cluster";
        }
        job = new InstallJob(kind, medium, ref, targets);
        setChanged();
        return "installing " + medium.label() + " on " + targets.size() + " node" + (targets.size() == 1 ? "" : "s");
    }

    /** Stops the job after the nodes being written right now; nothing queued is touched. */
    public boolean cancelJob() {
        if (job == null) {
            return false;
        }
        job.cancelled = true;
        job.queue.clear();
        return true;
    }

    private void tickJob() {
        if (job == null) {
            return;
        }
        final InstallJob j = job;
        j.ticks++;
        // Fill the lanes the card allows, skipping nodes the install cannot apply to.
        while (j.lanes.size() < parallelLanes() && !j.queue.isEmpty()) {
            final NodeRef node = j.queue.remove(0);
            final ServerRackBlockEntity rack = rackAt(node.rack());
            if (rack == null) {
                j.skippedNames.add("missing rack");
                continue;
            }
            final OsHost host = rack.unitHost(node.row());
            final String name = nodeName(rack, node.row());
            final String skip = skipReason(j, host, rack, node.row());
            if (skip != null) {
                j.skippedNames.add(name + " (" + skip + ")");
                continue;
            }
            j.lanes.add(new Lane(node, name, installTicks(host), installTicks(host)));
        }
        // Advance every lane; a finished lane writes the install and restarts the node.
        for (int i = 0; i < j.lanes.size(); i++) {
            final Lane lane = j.lanes.get(i);
            final int left = lane.ticksLeft() - 1;
            if (left > 0) {
                j.lanes.set(i, new Lane(lane.node(), lane.name(), lane.ticksTotal(), left));
                continue;
            }
            j.lanes.remove(i--);
            final ServerRackBlockEntity rack = rackAt(lane.node().rack());
            if (rack != null && apply(j, rack.unitHost(lane.node().row()))) {
                j.done++;
            } else {
                j.skippedNames.add(lane.name() + " (changed while writing)");
            }
        }
        if (j.finished()) {
            lastJobSummary = (j.cancelled ? "cancelled: " : "done: ") + j.medium.label() + " on " + j.done
                    + " node" + (j.done == 1 ? "" : "s") + (j.skipped() > 0 ? ", " + j.skipped() + " skipped" : "");
            job = null;
        }
        setChanged();
    }

    /** Why a node is left out of a job, or null when it takes the install. */
    @Nullable
    private static String skipReason(final InstallJob j, final OsHost host, final ServerRackBlockEntity rack, final int row) {
        if (!rack.bayPowerOn(row)) {
            return "bay off";
        }
        if (j.kind == JobKind.SYSTEM) {
            // "To all" means bring every node to this system: one already running it is left alone.
            return j.medium.id().equals(host.installedOsId()) ? "already installed" : null;
        }
        final ProgramSpec program = OsRegistry.getProgram(j.medium.id());
        if (program == null) {
            return "unknown program";
        }
        if (host.installedOsId() == null || host.console() == null) {
            return "no system";
        }
        if (host.console().isInstalled(program.id().toString())) {
            return "already installed";
        }
        if (!OsRegistry.canInstallProgram(host.installedOsId(), program.id(), host.maxCpuMhz(),
                host.totalVramMb(), host.systemDiskFreeMb())) {
            return "does not meet the program's requirements";
        }
        final HardwareEra era = host.displayEra();
        if (program.minEra() != HardwareEra.VINTAGE && (era == null || !OsGating.canInstall(program.minEra(), era))) {
            return "too old for the program";
        }
        return null;
    }

    private static boolean apply(final InstallJob j, final OsHost host) {
        if (j.kind == JobKind.SYSTEM) {
            if (!host.installOs(j.medium.id())) {
                return false;
            }
            // The files are on the disk; the node restarts into them, the same rule a single machine's
            // installer enforces — a system is only running once it has booted.
            host.setNeedsPost(true);
            return true;
        }
        final ProgramSpec program = OsRegistry.getProgram(j.medium.id());
        if (program == null || host.console() == null || !host.console().install(program.id().toString())) {
            return false;
        }
        host.setChanged();
        return true;
    }

    /** Writing time on a node: fourteen seconds on a 2 GHz machine, longer on slower ones, bounded. */
    static int installTicks(final OsHost host) {
        final int mhz = Math.max(200, host.maxCpuMhz());
        return Math.max(INSTALL_TICKS_MIN, Math.min(INSTALL_TICKS_MAX, (int) (INSTALL_TICKS_AT_2GHZ * 2000L / mhz)));
    }

    /** The name a node shows: its custom name, or its kind. */
    public static String nodeName(final ServerRackBlockEntity rack, final int row) {
        final ItemStack stack = rack.getServers().getStackInSlot(row);
        final String custom = ServerItem.customName(stack);
        if (!custom.isEmpty()) {
            return custom;
        }
        final RackChassis chassis = ServerItem.chassisOf(stack);
        return chassis == RackChassis.SUPERCOMPUTER_NODE ? "node" : "server";
    }

    // ---- local storage, like a PC: what is on the installed disks ----

    @Override
    public LocalStore localStore() {
        final List<ItemStack> disks = new ArrayList<>(DISK_SLOTS);
        for (int i = 0; i < DISK_SLOTS; i++) {
            disks.add(getHardware().getStackInSlot(DISK_SLOTS_START + i));
        }
        return new LocalStore(disks, this::setChanged);
    }

    private long netStorageItems() {
        final ComputerBuild build = currentBuild();
        return build == null ? 0L : Math.max(0L, build.totalStorageItems() - reservedByOs());
    }

    @Override
    public int usableStorageSlots() {
        final long capacity = netStorageItems();
        return capacity <= 0 ? 0 : (int) Math.min(STORAGE_SLOTS, (capacity + 63) / 64);
    }

    @Override
    public long localStorageUsed() {
        return localStore().used();
    }

    @Override
    public long localStorageCapacity() {
        return netStorageItems();
    }

    @Override
    public DataSink localStorage() {
        return new StoreSink(localStore());
    }

    // ---- ComputerTerminalHost: the monitor's read-only view ----

    @Override
    public boolean computerRunning() {
        return isRunning();
    }

    @Override
    public boolean computerBuildValid() {
        return buildValid();
    }

    @Override
    public int networkLinkState() {
        return networkUuid() != null ? 1 : 0;
    }

    @Override
    public long orchestrationCapacity() {
        return capacity();
    }

    @Override
    public int computerQueues() {
        return 1;
    }

    @Override
    public boolean isMainframeHost() {
        return false;
    }

    @Override
    public long computerRamBuffer() {
        return ramBuffer();
    }

    @Override
    public int networkServerCount() {
        if (!(level instanceof ServerLevel serverLevel) || networkUuid() == null) {
            return 0;
        }
        return NetworkSystem.get(serverLevel).serversOf(networkUuid()).size();
    }

    // ---- screen sync ----

    public static final int DATA_RUNNING = 0;
    public static final int DATA_BUILD_VALID = 1;
    public static final int DATA_CAPACITY = 2;
    public static final int DATA_RAM_BUFFER = 3;
    public static final int DATA_AUTOSTART = 4;
    public static final int DATA_ON_NETWORK = 5;
    public static final int DATA_HAS_CARD = 6;
    public static final int DATA_SUPERCOMPUTERS = 7;
    public static final int DATA_DATACENTERS = 8;
    public static final int DATA_LANES = 9;
    public static final int DATA_MANAGER_INSTALLED = 10;
    public static final int DATA_COUNT = DATA_MANAGER_INSTALLED + 1;

    private final int[] clientData = new int[DATA_COUNT];

    private int computeData(final int index) {
        return switch (index) {
            case DATA_RUNNING -> isRunning() ? 1 : 0;
            case DATA_BUILD_VALID -> buildValid() ? 1 : 0;
            case DATA_CAPACITY -> (int) Math.min(Integer.MAX_VALUE, capacity());
            case DATA_RAM_BUFFER -> (int) Math.min(Integer.MAX_VALUE, ramBuffer());
            case DATA_AUTOSTART -> isAutoStart() ? 1 : 0;
            case DATA_ON_NETWORK -> networkUuid() != null ? 1 : 0;
            case DATA_HAS_CARD -> clusterCard() != null ? 1 : 0;
            case DATA_SUPERCOMPUTERS -> supercomputers().size();
            case DATA_DATACENTERS -> datacenterSections().size();
            case DATA_LANES -> parallelLanes();
            case DATA_MANAGER_INSTALLED -> console() != null
                    && console().isInstalled(dev.jsc.jscomputronics.module.computing.program.Programs.CLUSTER_MANAGER.toString())
                    ? 1 : 0;
            default -> 0;
        };
    }

    private final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(final int index) {
            if (level != null && level.isClientSide()) {
                return index >= 0 && index < clientData.length ? clientData[index] : 0;
            }
            return computeData(index);
        }

        @Override
        public void set(final int index, final int value) {
            if (index >= 0 && index < clientData.length) {
                clientData[index] = value;
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public ContainerData getDataAccess() {
        return dataAccess;
    }
}
