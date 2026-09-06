/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.network;

import dev.jstech.core.registry.CoreAttachments;
import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.core.uuid.NodeUuid;

import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Central facade for spatial connectivity, UUID lookup, and orchestration capacity queries on a J's Computronics network.
 */
public final class NetworkSystem {

    private final ConnectivityIndex connectivity = new ConnectivityIndex();

    private final java.util.Map<NetworkUuid, MainframeNode> mainframesByNetwork = new java.util.HashMap<>();

    private final java.util.Map<NetworkUuid, Long> mainframePosByNetwork = new java.util.HashMap<>();

    private final NodeRegistry<NodeUuid, SubframeNode> subframes = new NodeRegistry<>();

    private final NodeRegistry<NodeUuid, ServerNode> servers = new NodeRegistry<>();

    private final java.util.Map<NodeUuid, ServerLocation> serverLocations = new java.util.HashMap<>();

    private final NodeRegistry<NodeUuid, PersonalComputerNode> personalComputers = new NodeRegistry<>();

    private final NodeRegistry<NodeUuid, CraftingComputerNode> craftingComputers = new NodeRegistry<>();

    private final NodeRegistry<NodeUuid, SupercomputerNode> supercomputers = new NodeRegistry<>();

    private final NodeRegistry<Long, ServerRouterElement> routers = new NodeRegistry<>();

    // Every per-network / per-node registry, collected so clear() resets them all together and a newly
    // added one can never be left out again (crafting and supercomputer nodes once were).
    private final List<java.util.Map<?, ?>> registries = List.of(
            mainframesByNetwork, mainframePosByNetwork, serverLocations);
    private final List<NodeRegistry<?, ?>> nodeRegistries = List.of(
            subframes, servers, personalComputers, craftingComputers, supercomputers, routers);

    /**
     * A Personal Computer attached to a network: a Category-C node that issues, but never orchestrates, Operations.
     */
    public record PersonalComputerNode(NodeUuid nodeUuid, NetworkUuid networkUuid, long capacity, long pos) {
    }

    /**
     * A Crafting Computer attached to a network: a Category-C node that executes recipes.
     */
    public record CraftingComputerNode(NodeUuid nodeUuid, NetworkUuid networkUuid, long capacity, long pos) {
    }

    /**
     * A Supercomputer attached to a network: the orchestration upgrade that runs the network's CRAFT Operations in parallel.
     */
    public record SupercomputerNode(NodeUuid nodeUuid, NetworkUuid networkUuid, long parallelCrafts, long pos) {
    }

    /**
     * The Rack block and internal slot that house a Server, for storage resolution.
     */
    public record ServerLocation(long rackPos, int slot) {
    }

    // Per-level acquisition (Phase 1+)

    public static NetworkSystem get(final ServerLevel level) {
        return level.getData(CoreAttachments.NETWORK_SYSTEM.get());
    }

    // ConnectivityIndex facade — works in Phase 0

    public ConnectivityIndex connectivity() {
        return connectivity;
    }

    public Optional<NetworkUuid> networkOf(long encodedPos) {
        return connectivity.networkOf(encodedPos);
    }

    public boolean inSameNetwork(long a, long b) {
        return connectivity.inSameNetwork(a, b);
    }

    // Mainframe / Subframe registry — works in Phase 0 with snapshots

    public void registerMainframe(MainframeNode mainframe) {
        java.util.Objects.requireNonNull(mainframe, "mainframe must not be null");
        mainframesByNetwork.put(mainframe.networkUuid(), mainframe);
    }

    public void recordMainframePosition(NetworkUuid network, long pos) {
        mainframePosByNetwork.put(network, pos);
    }

    public Optional<Long> mainframePositionOf(NetworkUuid network) {
        return Optional.ofNullable(mainframePosByNetwork.get(network));
    }

    public void unregisterMainframe(NetworkUuid network, NodeUuid node) {
        final MainframeNode current = mainframesByNetwork.get(network);
        if (current != null && current.nodeUuid().equals(node)) {
            mainframesByNetwork.remove(network);
            mainframePosByNetwork.remove(network);
        }
    }

    public void registerSubframe(SubframeNode subframe) {
        java.util.Objects.requireNonNull(subframe, "subframe must not be null");
        // Idempotent by node UUID: a Subframe re-registering each tick from tickNode() never duplicates.
        subframes.register(subframe.networkUuid(), subframe.nodeUuid(), subframe);
    }

    public void unregisterSubframe(NetworkUuid network, NodeUuid node) {
        subframes.unregister(network, node);
    }

    public Optional<MainframeNode> mainframeOf(NetworkUuid networkUuid) {
        return Optional.ofNullable(mainframesByNetwork.get(networkUuid));
    }

    public List<SubframeNode> subframesOf(NetworkUuid networkUuid) {
        return subframes.of(networkUuid);
    }

    public void registerServer(ServerNode server) {
        java.util.Objects.requireNonNull(server, "server must not be null");
        // Idempotent by node UUID: a Rack re-registering each tick never duplicates, and an unchanged
        // snapshot leaves every reader's list untouched.
        servers.register(server.networkUuid(), server.nodeUuid(), server);
    }

    public void registerServer(ServerNode server, long rackPos, int slot) {
        registerServer(server);
        serverLocations.put(server.nodeUuid(), new ServerLocation(rackPos, slot));
    }

    public Optional<ServerLocation> locationOf(NodeUuid node) {
        return Optional.ofNullable(serverLocations.get(node));
    }

    public void registerPersonalComputer(PersonalComputerNode pc) {
        java.util.Objects.requireNonNull(pc, "pc must not be null");
        personalComputers.register(pc.networkUuid(), pc.nodeUuid(), pc);
    }

    public void unregisterPersonalComputer(NetworkUuid network, NodeUuid node) {
        personalComputers.unregister(network, node);
    }

    public java.util.List<PersonalComputerNode> personalComputersOf(NetworkUuid networkUuid) {
        return personalComputers.of(networkUuid);
    }

    public void registerCraftingComputer(CraftingComputerNode computer) {
        java.util.Objects.requireNonNull(computer, "computer must not be null");
        craftingComputers.register(computer.networkUuid(), computer.nodeUuid(), computer);
    }

    public void unregisterCraftingComputer(NetworkUuid network, NodeUuid node) {
        craftingComputers.unregister(network, node);
    }

    public java.util.List<CraftingComputerNode> craftingComputersOf(NetworkUuid networkUuid) {
        return craftingComputers.of(networkUuid);
    }

    public void registerSupercomputer(SupercomputerNode supercomputer) {
        java.util.Objects.requireNonNull(supercomputer, "supercomputer must not be null");
        supercomputers.register(supercomputer.networkUuid(), supercomputer.nodeUuid(), supercomputer);
    }

    public void unregisterSupercomputer(NetworkUuid network, NodeUuid node) {
        supercomputers.unregister(network, node);
    }

    public java.util.List<SupercomputerNode> supercomputersOf(NetworkUuid networkUuid) {
        return supercomputers.of(networkUuid);
    }

    public void unregisterServer(NetworkUuid network, NodeUuid node) {
        servers.unregister(network, node);
        serverLocations.remove(node);
    }

    public java.util.List<ServerNode> serversOf(NetworkUuid networkUuid) {
        return servers.of(networkUuid);
    }

    /** The storage of every server on the network, in items, as the racks registered it. */
    public long totalStorageItemsOf(NetworkUuid networkUuid) {
        long total = 0L;
        for (var server : serversOf(networkUuid)) {
            total += server.storageItems();
        }
        return total;
    }

    public long totalOrchestrationCapacityOf(NetworkUuid networkUuid) {
        long total = mainframeOf(networkUuid)
                .map(MainframeNode::contributedCapacity)
                .orElse(0L);
        for (var subframe : subframesOf(networkUuid)) {
            total += subframe.contributedCapacity();
        }
        return total;
    }

    /** The capacity the network's active Subframes lend their Mainframe, without the Mainframe's own. */
    public long subframeCapacityOf(NetworkUuid networkUuid) {
        long total = 0L;
        for (var subframe : subframesOf(networkUuid)) {
            total += subframe.contributedCapacity();
        }
        return total;
    }

    /** The dispatch queues the network's active Subframes add to their Mainframe (their GPUs). */
    public int subframeQueuesOf(NetworkUuid networkUuid) {
        int total = 0;
        for (var subframe : subframesOf(networkUuid)) {
            total += subframe.contributedQueues();
        }
        return total;
    }

    // Server Router (topology element) registry

    public void registerRouter(final ServerRouterElement router) {
        java.util.Objects.requireNonNull(router, "router must not be null");
        routers.register(router.networkUuid(), router.pos(), router);
    }

    public void unregisterRouter(final NetworkUuid network, final long pos) {
        routers.unregister(network, pos);
    }

    public java.util.List<ServerRouterElement> routersOf(final NetworkUuid networkUuid) {
        return routers.of(networkUuid);
    }

    // Phase 1+ stubs — depend on runtime topology / BlockEntities

    public Optional<NodeUuid> nodeByPosition(long encodedPos) {
        throw new UnsupportedOperationException(
                "nodeByPosition requires runtime BlockEntity lookup, "
                        + "deferred to Phase 1+.");
    }

    public Optional<MainframeNode> failoverPartnerOf(NetworkUuid networkUuid) {
        throw new UnsupportedOperationException(
                "failoverPartnerOf resolves a partner via cross-Mainframe lookup, "
                        + "deferred to Phase 1+.");
    }

    public void clear() {
        connectivity.clear();
        registries.forEach(java.util.Map::clear);
        nodeRegistries.forEach(NodeRegistry::clear);
    }
}
