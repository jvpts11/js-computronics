/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.blockentity;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.JsComputers;
import dev.jstech.computers.block.DataCableBlock;
import dev.jstech.computers.datacenter.DatacenterSection;
import dev.jstech.computers.datacenter.LoadBalanceMode;
import dev.jstech.core.network.ConnectivityIndex;
import dev.jstech.core.network.INetworkBridge;
import dev.jstech.core.network.NetworkSystem;
import dev.jstech.core.network.ServerNode;
import dev.jstech.core.network.ServerRouterElement;
import dev.jstech.core.tier.IndustrialTier;
import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.core.uuid.NodeUuid;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BlockEntity backing the Server Router, the first network topology element.
 */
public class ServerRouterBlockEntity extends BlockEntity {

    public static final IndustrialTier TIER = IndustrialTier.T3;

    private static final int RECOMPUTE_INTERVAL = 20;
    private static final int WARN_INTERVAL = 200;

    // Synced summary (server -> client) for the config GUI; section rows are flattened into the array.
    public static final int DATA_INPUT_FACE = 0;      // input face 3D value, or -1 if none
    public static final int DATA_MANAGED_RACKS = 1;
    public static final int DATA_MAX_RACKS = 2;
    public static final int DATA_OVER_CAPACITY = 3;   // 0 or 1
    public static final int DATA_SECTION_COUNT = 4;
    public static final int DATA_SECTION_BASE = 5;
    public static final int DATA_PER_SECTION = 4;     // face 3D value, racks, servers, mode ordinal
    public static final int MAX_SECTIONS = 5;         // the 6 faces minus the one input face
    public static final int DATA_COUNT = DATA_SECTION_BASE + MAX_SECTIONS * DATA_PER_SECTION;

    private final SimpleContainerData data = new SimpleContainerData(DATA_COUNT);

    private String customName = "";
    private final Map<Direction, LoadBalanceMode> loadBalanceModes = new EnumMap<>(Direction.class);
    // Player-given section names by face, shown by the Cluster Manager instead of "Router · EAST".
    private final Map<Direction, String> sectionNames = new EnumMap<>(Direction.class);
    // Where the next spreading write on each face starts, so round-robin actually takes turns.
    private final Map<Direction, Integer> balanceCursors = new EnumMap<>(Direction.class);

    @Nullable
    private NetworkUuid registeredNetwork;

    @Nullable
    private Direction inputFace;
    private List<DatacenterSection> sections = List.of();
    private Set<Long> unmanagedRacks = Set.of();
    private boolean overCapacity;
    private int recomputeCooldown;
    private int warnCooldown;

    public ServerRouterBlockEntity(final BlockPos pos, final BlockState state) {
        super(ComputingModule.SERVER_ROUTER_BE.get(), pos, state);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            final ConnectivityIndex index = NetworkSystem.get(serverLevel).connectivity();
            final long encodedPos = worldPosition.asLong();
            if (!index.contains(encodedPos)) {
                index.onCablePlaced(encodedPos, bridgeNeighbors(serverLevel));
            }
        }
    }

    private Set<Long> bridgeNeighbors(final ServerLevel serverLevel) {
        final Set<Long> neighbors = new HashSet<>();
        for (final Direction direction : Direction.values()) {
            final BlockPos neighborPos = worldPosition.relative(direction);
            final var block = serverLevel.getBlockState(neighborPos).getBlock();
            if (block instanceof DataCableBlock || block instanceof INetworkBridge) {
                neighbors.add(neighborPos.asLong());
            }
        }
        return neighbors;
    }

    public static void serverTick(final Level level, final BlockPos pos,
                                  final BlockState state, final ServerRouterBlockEntity be) {
        if (level instanceof ServerLevel serverLevel) {
            be.tick(serverLevel);
        }
    }

    private void tick(final ServerLevel level) {
        final NetworkSystem system = NetworkSystem.get(level);
        final ConnectivityIndex index = system.connectivity();
        final long encodedPos = worldPosition.asLong();
        // Re-register after a chunk reload, mirroring the cable's self-healing index discipline.
        if (!index.contains(encodedPos)) {
            index.onCablePlaced(encodedPos, bridgeNeighbors(level));
        }

        final NetworkUuid network = index.networkOf(encodedPos).orElse(null);
        if (network == null) {
            // Off-network (no UUID yet, or the network collapsed): drop registration and any topology.
            if (registeredNetwork != null) {
                system.unregisterRouter(registeredNetwork, encodedPos);
                registeredNetwork = null;
            }
            sections = List.of();
            unmanagedRacks = Set.of();
            inputFace = null;
            overCapacity = false;
            writeData();
            return;
        }
        if (registeredNetwork != null && !registeredNetwork.equals(network)) {
            system.unregisterRouter(registeredNetwork, encodedPos);
        }
        system.registerRouter(new ServerRouterElement(network, encodedPos, TIER));
        registeredNetwork = network;

        if (--recomputeCooldown <= 0) {
            recomputeCooldown = RECOMPUTE_INTERVAL;
            recomputeSections(level, system, network);
        }
        if (overCapacity && --warnCooldown <= 0) {
            warnCooldown = WARN_INTERVAL;
            JsComputers.LOGGER.warn(
                    "Server Router at {} manages {} racks but its tier budget is {}; {} rack(s) left unmanaged.",
                    worldPosition, managedRackCount(), maxRacks(), unmanagedRacks.size());
        }
    }

    private void recomputeSections(final ServerLevel level, final NetworkSystem system, final NetworkUuid network) {
        final ConnectivityIndex index = system.connectivity();
        final Set<Long> blocked = Set.of(worldPosition.asLong());
        final List<DatacenterSection> found = new ArrayList<>();
        final Set<Set<Long>> seenRackSets = new HashSet<>();
        /*
         * The back face is the dedicated uplink to the Mainframe; every other face is a
         * potential datacenter section.
         */
        final Direction uplink = getBlockState().getBlock()
                instanceof dev.jstech.computers.block.ServerRouterBlock
                ? getBlockState().getValue(
                        net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING).getOpposite()
                : null;

        for (final Direction face : Direction.values()) {
            if (face == uplink) {
                continue; // the uplink side is never a datacenter section
            }
            final long neighbor = worldPosition.relative(face).asLong();
            if (!index.contains(neighbor)) {
                continue; // no cable on this face
            }
            final Set<Long> branchCables = index.reachableFrom(neighbor, blocked);
            if (branchCables.isEmpty()) {
                continue;
            }
            final BranchScan scan = scanBranch(level, branchCables);
            if (scan.hasMainframe) {
                continue; // a section branch must not loop back to the Mainframe (miswired), ignore
            }
            if (scan.rackControllers.isEmpty()) {
                continue; // an empty branch carries no datacenter
            }
            if (!seenRackSets.add(scan.rackControllers)) {
                continue; // a looped topology surfaced the same racks on two faces, count once
            }

            final List<NodeUuid> servers = new ArrayList<>();
            long storage = 0L;
            for (final ServerNode server : system.serversOf(network)) {
                final var location = system.locationOf(server.nodeUuid());
                if (location.isPresent() && scan.rackControllers.contains(location.get().rackPos())) {
                    servers.add(server.nodeUuid());
                    storage += server.storageItems();
                }
            }
            found.add(new DatacenterSection(face, new LinkedHashSet<>(scan.rackControllers), servers, storage));
        }

        // Capacity is a single budget across every section: walk the racks in section order and mark
        final int budget = maxRacks();
        final Set<Long> unmanaged = new LinkedHashSet<>();
        int rackCount = 0;
        for (final DatacenterSection section : found) {
            for (final long rack : section.rackPositions()) {
                rackCount++;
                if (rackCount > budget) {
                    unmanaged.add(rack);
                }
            }
        }

        this.sections = List.copyOf(found);
        this.unmanagedRacks = Set.copyOf(unmanaged);
        this.inputFace = uplink;
        this.overCapacity = !unmanaged.isEmpty();
        for (final DatacenterSection section : found) {
            loadBalanceModes.putIfAbsent(section.face(), LoadBalanceMode.ROUND_ROBIN);
        }
        writeData();
    }

    private BranchScan scanBranch(final ServerLevel level, final Set<Long> branchCables) {
        final Set<Long> racks = new LinkedHashSet<>();
        boolean hasMainframe = false;
        for (final long cablePos : branchCables) {
            final BlockPos cable = BlockPos.of(cablePos);
            for (final Direction direction : Direction.values()) {
                final BlockEntity neighbor = level.getBlockEntity(cable.relative(direction));
                /*
                 * A datacenter is made of Server Racks. A Supercomputer Rack lives on the compute fabric
                 * behind its HBW Interface and is never a section member, even if the branch walk
                 * happens to reach it through that fabric.
                 */
                if (neighbor instanceof ServerRackBlockEntity rack) {
                    if (rack.rackType() != dev.jstech.computers.rack.RackChassis.RackType.SUPERCOMPUTER) {
                        racks.add(rack.getBlockPos().asLong());
                    }
                } else if (neighbor instanceof ServerRackPartBlockEntity part && part.controllerPos() != null) {
                    if (level.getBlockEntity(part.controllerPos()) instanceof ServerRackBlockEntity controller
                            && controller.rackType()
                                    != dev.jstech.computers.rack.RackChassis.RackType.SUPERCOMPUTER) {
                        racks.add(part.controllerPos().asLong());
                    }
                } else if (neighbor instanceof MainframeBlockEntity || neighbor instanceof MainframePartBlockEntity) {
                    hasMainframe = true;
                }
            }
        }
        return new BranchScan(racks, hasMainframe);
    }

    /**
     * Result of walking one router branch: the racks it contains and whether it reaches the Mainframe.
     */
    private record BranchScan(Set<Long> rackControllers, boolean hasMainframe) {
    }

    public void recomputeNow() {
        if (level instanceof ServerLevel serverLevel && registeredNetwork != null) {
            recomputeSections(serverLevel, NetworkSystem.get(serverLevel), registeredNetwork);
            recomputeCooldown = RECOMPUTE_INTERVAL;
        }
    }

    public void onBroken(final ServerLevel level) {
        if (registeredNetwork != null) {
            NetworkSystem.get(level).unregisterRouter(registeredNetwork, worldPosition.asLong());
            registeredNetwork = null;
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        /*
         * Also unregister on chunk unload, not just on destruction (the block's onRemove), so the router
         * never lingers in the still-loaded per-level network. onBroken is idempotent.
         */
        if (level instanceof ServerLevel serverLevel) {
            onBroken(serverLevel);
        }
    }

    // Accessors for the config GUI and the Datacenter Station

    public ContainerData getDataAccess() {
        return data;
    }

    private void writeData() {
        data.set(DATA_INPUT_FACE, inputFace == null ? -1 : inputFace.get3DDataValue());
        data.set(DATA_MANAGED_RACKS, managedRackCount());
        data.set(DATA_MAX_RACKS, maxRacks());
        data.set(DATA_OVER_CAPACITY, overCapacity ? 1 : 0);
        data.set(DATA_SECTION_COUNT, Math.min(sections.size(), MAX_SECTIONS));
        for (int i = 0; i < MAX_SECTIONS; i++) {
            final int base = DATA_SECTION_BASE + i * DATA_PER_SECTION;
            if (i < sections.size()) {
                final DatacenterSection section = sections.get(i);
                data.set(base, section.face().get3DDataValue());
                data.set(base + 1, section.rackCount());
                data.set(base + 2, section.serverCount());
                data.set(base + 3, loadBalanceMode(section.face()).ordinal());
            } else {
                data.set(base, -1);
                data.set(base + 1, 0);
                data.set(base + 2, 0);
                data.set(base + 3, 0);
            }
        }
    }

    public String customName() {
        return customName;
    }

    public void setCustomName(@Nullable final String name) {
        this.customName = name == null ? "" : name;
        setChanged();
    }

    /**
     * Which server a spreading write on {@code face} should start from, advancing the rotation by one.
     * Kept per face and written with the router, so successive writes really do take turns.
     */
    public int nextBalanceStart(final Direction face) {
        final int start = balanceCursors.getOrDefault(face, 0);
        balanceCursors.put(face, start == Integer.MAX_VALUE ? 0 : start + 1);
        setChanged();
        return start;
    }

    /** The player's name for the section on {@code face}, or empty when it goes by the router and face. */
    public String sectionName(final Direction face) {
        return sectionNames.getOrDefault(face, "");
    }

    public void setSectionName(final Direction face, @Nullable final String name) {
        if (name == null || name.isEmpty()) {
            sectionNames.remove(face);
        } else {
            sectionNames.put(face, name);
        }
        setChanged();
    }

    public List<DatacenterSection> sections() {
        return sections;
    }

    @Nullable
    public Direction inputFace() {
        return inputFace;
    }

    public Set<Long> unmanagedRacks() {
        return unmanagedRacks;
    }

    public boolean isOverCapacity() {
        return overCapacity;
    }

    public int maxRacks() {
        return ServerRouterElement.maxRacksFor(TIER);
    }

    public int managedRackCount() {
        int count = 0;
        for (final DatacenterSection section : sections) {
            count += section.rackCount();
        }
        return count;
    }

    public LoadBalanceMode loadBalanceMode(final Direction face) {
        return loadBalanceModes.getOrDefault(face, LoadBalanceMode.ROUND_ROBIN);
    }

    public void cycleLoadBalanceMode(final Direction face) {
        loadBalanceModes.put(face, loadBalanceMode(face).next());
        setChanged();
        writeData();
    }

    // Persistence

    @Override
    protected void saveAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!customName.isEmpty()) {
            tag.putString("CustomName", customName);
        }
        final CompoundTag names = new CompoundTag();
        sectionNames.forEach((face, name) -> names.putString(face.getName(), name));
        if (!names.isEmpty()) {
            tag.put("SectionNames", names);
        }
        final CompoundTag cursors = new CompoundTag();
        balanceCursors.forEach((face, cursor) -> cursors.putInt(face.getName(), cursor));
        if (!cursors.isEmpty()) {
            tag.put("BalanceCursors", cursors);
        }
        final CompoundTag modes = new CompoundTag();
        for (final Map.Entry<Direction, LoadBalanceMode> entry : loadBalanceModes.entrySet()) {
            modes.putByte(entry.getKey().getName(), (byte) entry.getValue().ordinal());
        }
        if (!modes.isEmpty()) {
            tag.put("LoadBalance", modes);
        }
    }

    @Override
    protected void loadAdditional(final CompoundTag tag, final HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        customName = tag.getString("CustomName");
        sectionNames.clear();
        if (tag.contains("SectionNames")) {
            final CompoundTag names = tag.getCompound("SectionNames");
            for (final Direction direction : Direction.values()) {
                if (names.contains(direction.getName())) {
                    sectionNames.put(direction, names.getString(direction.getName()));
                }
            }
        }
        balanceCursors.clear();
        if (tag.contains("BalanceCursors")) {
            final CompoundTag cursors = tag.getCompound("BalanceCursors");
            for (final Direction direction : Direction.values()) {
                if (cursors.contains(direction.getName())) {
                    balanceCursors.put(direction, cursors.getInt(direction.getName()));
                }
            }
        }
        loadBalanceModes.clear();
        if (tag.contains("LoadBalance")) {
            final CompoundTag modes = tag.getCompound("LoadBalance");
            for (final Direction direction : Direction.values()) {
                if (modes.contains(direction.getName())) {
                    final int ordinal = modes.getByte(direction.getName()) & 0xFF;
                    final LoadBalanceMode[] values = LoadBalanceMode.values();
                    if (ordinal < values.length) {
                        loadBalanceModes.put(direction, values[ordinal]);
                    }
                }
            }
        }
    }
}
