/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.blockentity;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.block.DataCableBlock;
import dev.jstech.computers.hardware.PhiCoprocessorSpec;
import dev.jstech.computers.item.PhiCoprocessorItem;
import dev.jstech.computers.item.ServerHardwareHandler;
import dev.jstech.computers.item.ServerItem;
import dev.jstech.computers.rack.RackChassis;
import dev.jstech.core.network.NetworkSystem;
import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.core.uuid.NodeUuid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The HBW Interface: the uplink of a Supercomputer cluster.
 */
public class HbwInterfaceBlockEntity extends BlockEntity {

    public static final int SLOT_NO_NODE = 0;
    public static final int SLOT_EMPTY = 1;
    public static final int SLOT_UNDER_RATED = 2;
    public static final int SLOT_OFFLINE = 3;
    public static final int SLOT_OK_BASE = 4;

    /**
     * One surveyed cluster slot: the rack the node sits in, its row, and what it contributes.
     */
    public record ClusterSlot(BlockPos node, int row, int code, long crafts) {
    }

    /** A node found on the fabric: the cabinet it is mounted in and the unit row it occupies. */
    public record NodeRef(BlockPos rack, int row) {
    }

    /*
     * Every node on the fabric, in slot order, including those past the six rated slots. The rated
     * slots decide crafting; this full list is what a console needs to install and control them all.
     */
    private List<NodeRef> nodes = List.of();

    /** All nodes seated on this fabric, rack by rack, top unit first; the first six are the rated slots. */
    public List<NodeRef> clusterNodes() {
        return nodes;
    }

    private NodeUuid nodeUuid;
    private NetworkUuid networkUuid;
    private NetworkUuid registeredNetwork;
    // The player's name for the cluster; empty means the manager numbers it (SC-1, SC-2 ...).
    private String customName = "";

    private List<ClusterSlot> slots = List.of();
    private int unslottedNodes;
    private long parallelCrafts;

    // operationId -> number of parallel slots it holds; one craft can hold several so it can fan out across CCs.
    private final java.util.Map<UUID, Integer> activeCraftSlots = new java.util.LinkedHashMap<>();

    public HbwInterfaceBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.HBW_INTERFACE_BE.get(), pos, state);
    }

    public static void serverTick(final Level level, final BlockPos pos,
                                  final BlockState state, final HbwInterfaceBlockEntity be) {
        if (level instanceof ServerLevel serverLevel) {
            be.tickCluster(serverLevel);
        }
    }

    private void tickCluster(final ServerLevel serverLevel) {
        survey(serverLevel);
        final NetworkSystem system = NetworkSystem.get(serverLevel);
        /*
         * The interface is on the network whenever its uplink cable is, crafts or no crafts: a cluster with
         * no rated node still shows up in the Cluster Manager, where the player can see what it lacks.
         * Crafting itself still waits for clusterOnline().
         */
        final long cable = adjacentHbwCable(serverLevel);
        final NetworkUuid resolved = cable == Long.MIN_VALUE ? null
                : system.connectivity().networkOf(cable).orElse(null);
        if (registeredNetwork != null && !registeredNetwork.equals(resolved)) {
            system.unregisterSupercomputer(registeredNetwork, nodeUuid());
            registeredNetwork = null;
        }
        networkUuid = resolved;
        if (resolved != null) {
            system.registerSupercomputer(new NetworkSystem.SupercomputerNode(
                    nodeUuid(), resolved, parallelCrafts, worldPosition.asLong()));
            registeredNetwork = resolved;
        }
    }

    private void survey(final ServerLevel serverLevel) {
        final List<NodeRef> discovered = new ArrayList<>();
        final Set<BlockPos> seenControllers = new HashSet<>();
        final Set<BlockPos> visited = new HashSet<>();
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(worldPosition);
        visited.add(worldPosition);
        int steps = 0;
        while (!queue.isEmpty() && steps++ < 256) {
            final BlockPos current = queue.poll();
            for (final Direction direction : Direction.values()) {
                final BlockPos neighbor = current.relative(direction);
                if (!visited.add(neighbor)) {
                    continue;
                }
                final BlockState state = serverLevel.getBlockState(neighbor);
                // The fabric is the high-compute cable only. A cabinet is a leaf on it, not a conduit.
                if (state.getBlock() instanceof DataCableBlock cable
                        && cable.tier() == dev.jstech.core.network.DataTier.HPC) {
                    queue.add(neighbor);
                    continue;
                }
                /*
                 * A Supercomputer Rack on the fabric: every node mounted in it, in rack order, is a
                 * candidate slot. Cabinets are taken in discovery order, so slot numbering is stable
                 * for a given build and does not shuffle between surveys.
                 */
                final ServerRackBlockEntity rack = cabinetAt(serverLevel, neighbor);
                if (rack != null && rack.rackType() == RackChassis.RackType.SUPERCOMPUTER
                        && seenControllers.add(rack.getBlockPos())) {
                    /*
                     * The cabinet's link light: this survey already walks the whole fabric every tick, so
                     * the cabinet is told here rather than walking it again itself.
                     */
                    rack.noteFabricUplink(serverLevel.getGameTime(), networkUuid != null);
                    for (final int row : rack.computerSlots()) {
                        if (ServerItem.chassisOf(rack.getServers().getStackInSlot(row))
                                == RackChassis.SUPERCOMPUTER_NODE) {
                            discovered.add(new NodeRef(rack.getBlockPos(), row));
                        }
                    }
                }
            }
        }
        final List<ClusterSlot> surveyed = new ArrayList<>(PhiCoprocessorSpec.SLOT_COUNT);
        long budget = 0;
        for (int i = 0; i < Math.min(discovered.size(), PhiCoprocessorSpec.SLOT_COUNT); i++) {
            final NodeRef node = discovered.get(i);
            int code = SLOT_EMPTY;
            long crafts = 0;
            if (serverLevel.getBlockEntity(node.rack()) instanceof ServerRackBlockEntity rack) {
                final ItemStack server = rack.getServers().getStackInSlot(node.row());
                final PhiCoprocessorItem phi = installedPhi(server);
                if (phi == null) {
                    code = SLOT_EMPTY;
                } else if (!phi.spec().fitsSlot(i)) {
                    code = SLOT_UNDER_RATED;
                } else if (!rack.bayPowerOn(node.row()) || ServerItem.build(server) == null) {
                    code = SLOT_OFFLINE; // a node is a real computer: assembled + its bay switched on, or inert
                } else {
                    code = SLOT_OK_BASE + modelIndex(phi.spec());
                    crafts = PhiCoprocessorSpec.craftsForSlot(i);
                    budget += crafts;
                }
            }
            surveyed.add(new ClusterSlot(node.rack(), node.row(), code, crafts));
        }
        this.slots = List.copyOf(surveyed);
        this.nodes = List.copyOf(discovered);
        this.unslottedNodes = Math.max(0, discovered.size() - PhiCoprocessorSpec.SLOT_COUNT);
        this.parallelCrafts = budget;
    }

    /** The rack a block belongs to (the controller itself or any part of the cabinet), or null. */
    @Nullable
    private static ServerRackBlockEntity cabinetAt(final ServerLevel level, final BlockPos pos) {
        final BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof ServerRackBlockEntity rack) {
            return rack;
        }
        if (be instanceof ServerRackPartBlockEntity part && part.controllerPos() != null
                && level.getBlockEntity(part.controllerPos()) instanceof ServerRackBlockEntity rack) {
            return rack;
        }
        return null;
    }

    /**
     * The crafting co-processor seated in a node's expansion slot, or null. A node's hardware is a data
     * component whose container is only as long as what was written to it, so the slot range is
     * checked against the container's real size rather than indexed blindly.
     */
    @Nullable
    public static PhiCoprocessorItem installedPhi(final ItemStack server) {
        final ItemContainerContents parts = ServerItem.hardware(server);
        final int end = Math.min(parts.getSlots(), ServerHardwareHandler.GPU_START + ServerHardwareHandler.GPU);
        for (int slot = ServerHardwareHandler.GPU_START; slot < end; slot++) {
            if (parts.getStackInSlot(slot).getItem() instanceof PhiCoprocessorItem phi) {
                return phi;
            }
        }
        return null;
    }

    public static int modelIndex(final PhiCoprocessorSpec spec) {
        return switch (spec.maxSlot()) {
            case 2 -> 0;
            case 3 -> 1;
            case 4 -> 2;
            default -> 3;
        };
    }

    private long adjacentHbwCable(final ServerLevel serverLevel) {
        for (final Direction direction : Direction.values()) {
            final BlockPos neighbor = worldPosition.relative(direction);
            if (serverLevel.getBlockState(neighbor).getBlock() instanceof DataCableBlock cable
                    && cable.tier() == dev.jstech.core.network.DataTier.T2_HBW) {
                return neighbor.asLong();
            }
        }
        return Long.MIN_VALUE;
    }

    public NodeUuid nodeUuid() {
        if (nodeUuid == null) {
            nodeUuid = new NodeUuid(UUID.randomUUID());
            setChanged();
        }
        return nodeUuid;
    }

    @Nullable
    public NetworkUuid networkUuid() {
        return networkUuid;
    }

    public boolean clusterOnline() {
        return networkUuid != null && parallelCrafts > 0;
    }

    public long parallelCrafts() {
        return parallelCrafts;
    }

    public List<ClusterSlot> clusterSlots() {
        return slots;
    }

    public int unslottedNodes() {
        return unslottedNodes;
    }

    public int craftSlotsInUse() {
        int sum = 0;
        for (final int held : activeCraftSlots.values()) {
            sum += held;
        }
        return sum;
    }

    /**
     * Grants up to {@code wanted} parallel craft slots to {@code operationId}, capped by the cluster's free
     * capacity, and returns how many were granted (0 when the cluster is offline or has no free slots). One
     * operation may hold several slots so a single large craft can fan out across that many crafting computers
     * at once.
     */
    public int acquireCraftSlots(final UUID operationId, final int wanted) {
        if (!clusterOnline() || wanted <= 0) {
            return 0;
        }
        final int free = (int) Math.max(0L, parallelCrafts - craftSlotsInUse());
        final int grant = Math.min(wanted, free);
        if (grant > 0) {
            activeCraftSlots.merge(operationId, grant, Integer::sum);
        }
        return grant;
    }

    public boolean tryAcquireCraftSlot(final UUID operationId) {
        return activeCraftSlots.containsKey(operationId) || acquireCraftSlots(operationId, 1) > 0;
    }

    /** The crafts holding slots here right now, by operation id, for the console's queue view. */
    public java.util.Map<UUID, Integer> heldSlots() {
        return java.util.Map.copyOf(activeCraftSlots);
    }

    public void releaseCraftSlot(final UUID operationId) {
        activeCraftSlots.remove(operationId);
    }

    public void onBroken(final ServerLevel serverLevel) {
        if (registeredNetwork != null) {
            NetworkSystem.get(serverLevel).unregisterSupercomputer(registeredNetwork, nodeUuid());
            registeredNetwork = null;
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        /*
         * Unregister the cluster on chunk unload too, not just on destruction (the block's onRemove),
         * so the Supercomputer never lingers in the still-loaded per-level network. onBroken is idempotent.
         */
        if (level instanceof ServerLevel serverLevel) {
            onBroken(serverLevel);
        }
    }

    public String customName() {
        return customName;
    }

    public void setCustomName(@Nullable final String name) {
        this.customName = name == null ? "" : name;
        setChanged();
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("NodeUuid")) {
            nodeUuid = new NodeUuid(tag.getUUID("NodeUuid"));
        }
        customName = tag.getString("CustomName");
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (nodeUuid != null) {
            tag.putUUID("NodeUuid", nodeUuid.value());
        }
        if (!customName.isEmpty()) {
            tag.putString("CustomName", customName);
        }
    }
}
