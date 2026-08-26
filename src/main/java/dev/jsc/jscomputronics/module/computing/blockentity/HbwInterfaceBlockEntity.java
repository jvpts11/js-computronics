/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.blockentity;

import dev.jsc.jscomputronics.common.hardware.PhiCoprocessorSpec;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.block.DataCableBlock;
import dev.jsc.jscomputronics.module.computing.block.SupercomputerNodeBlock;
import dev.jsc.jscomputronics.module.computing.item.PhiCoprocessorItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
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
     * One surveyed cluster slot: the node position and what it contributes.
     */
    public record ClusterSlot(BlockPos node, int code, long crafts) {
    }

    private NodeUuid nodeUuid;
    private NetworkUuid networkUuid;
    private NetworkUuid registeredNetwork;

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
        NetworkUuid resolved = null;
        if (parallelCrafts > 0) {
            final long cable = adjacentHbwCable(serverLevel);
            resolved = cable == Long.MIN_VALUE ? null
                    : system.connectivity().networkOf(cable).orElse(null);
        }
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
        final List<BlockPos> discovered = new ArrayList<>();
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
                // The fabric: HPC cables plus the node towers themselves.
                if (state.getBlock() instanceof DataCableBlock cable
                        && cable.tier() == dev.jsc.jscomputronics.common.network.DataTier.HPC) {
                    queue.add(neighbor);
                    continue;
                }
                BlockPos controller = null;
                if (state.getBlock() instanceof SupercomputerNodeBlock) {
                    controller = neighbor;
                } else if (state.getBlock()
                        instanceof dev.jsc.jscomputronics.module.computing.block.SupercomputerNodePartBlock) {
                    controller = dev.jsc.jscomputronics.module.computing.block.SupercomputerNodePartBlock
                            .controllerOf(serverLevel, neighbor);
                }
                if (controller != null) {
                    queue.add(neighbor);
                    if (seenControllers.add(controller)) {
                        discovered.add(controller);
                    }
                }
            }
        }
        final List<ClusterSlot> surveyed = new ArrayList<>(PhiCoprocessorSpec.SLOT_COUNT);
        long budget = 0;
        for (int i = 0; i < Math.min(discovered.size(), PhiCoprocessorSpec.SLOT_COUNT); i++) {
            final BlockPos node = discovered.get(i);
            int code = SLOT_EMPTY;
            long crafts = 0;
            if (serverLevel.getBlockEntity(node) instanceof SupercomputerNodeBlockEntity nodeBe) {
                final PhiCoprocessorItem phi = nodeBe.installedPhi();
                if (phi == null) {
                    code = SLOT_EMPTY;
                } else if (!phi.spec().fitsSlot(i)) {
                    code = SLOT_UNDER_RATED;
                } else if (!nodeBe.isRunning()) {
                    code = SLOT_OFFLINE; // a node is a real computer: assembled + powered, or inert
                } else {
                    code = SLOT_OK_BASE + modelIndex(phi.spec());
                    crafts = PhiCoprocessorSpec.craftsForSlot(i);
                    budget += crafts;
                }
            }
            surveyed.add(new ClusterSlot(node, code, crafts));
        }
        this.slots = List.copyOf(surveyed);
        this.unslottedNodes = Math.max(0, discovered.size() - PhiCoprocessorSpec.SLOT_COUNT);
        this.parallelCrafts = budget;
    }

    private static int modelIndex(final PhiCoprocessorSpec spec) {
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
                    && cable.tier() == dev.jsc.jscomputronics.common.network.DataTier.T2_HBW) {
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
        // Unregister the cluster on chunk unload too, not just on destruction (the block's onRemove),
        // so the Supercomputer never lingers in the still-loaded per-level network. onBroken is idempotent.
        if (level instanceof ServerLevel serverLevel) {
            onBroken(serverLevel);
        }
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("NodeUuid")) {
            nodeUuid = new NodeUuid(tag.getUUID("NodeUuid"));
        }
    }

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (nodeUuid != null) {
            tag.putUUID("NodeUuid", nodeUuid.value());
        }
    }
}
