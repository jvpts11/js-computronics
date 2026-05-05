/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.energy.internal;

import dev.jsc.jscomputronics.common.energy.EnergyCable;
import dev.jsc.jscomputronics.common.energy.EnergyDistributionResult;
import dev.jsc.jscomputronics.common.energy.EnergyNode;
import dev.jsc.jscomputronics.common.energy.internal.EnergyFlowGraph;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class EnergyNetwork {

    private final Map<Long, EnergyNode> nodes = new LinkedHashMap<>();
    private final Map<Long, EnergyCable> cables = new LinkedHashMap<>();
    private final Map<Long, List<Long>> adjacency = new HashMap<>();

    public void addNode(final long pos, final EnergyNode node) {
        if (nodes.containsKey(pos) || cables.containsKey(pos)) {
            throw new IllegalStateException(
                    "Position already occupied: " + pos);
        }
        nodes.put(pos, node);
        adjacency.computeIfAbsent(pos, k -> new ArrayList<>());
    }

    public void addCable(final long pos, final EnergyCable cable) {
        if (nodes.containsKey(pos) || cables.containsKey(pos)) {
            throw new IllegalStateException(
                    "Position already occupied: " + pos);
        }
        cables.put(pos, cable);
        adjacency.computeIfAbsent(pos, k -> new ArrayList<>());
    }

    public void connect(final long a, final long b) {
        requireMember(a);
        requireMember(b);
        adjacency.get(a).add(b);
        adjacency.get(b).add(a);
    }

    public boolean remove(final long pos) {
        final boolean wasNode = nodes.remove(pos) != null;
        final boolean wasCable = cables.remove(pos) != null;
        if (!wasNode && !wasCable) {
            return false;
        }
        // Remove connections in both directions.
        final List<Long> neighbors = adjacency.remove(pos);
        if (neighbors != null) {
            for (final Long n : neighbors) {
                final List<Long> reverse = adjacency.get(n);
                if (reverse != null) {
                    reverse.removeIf(x -> x == pos);
                }
            }
        }
        return true;
    }

    public EnergyDistributionResult tickDistribute() {
        if (nodes.isEmpty()) {
            return EnergyDistributionResult.empty();
        }
        // The flow graph takes immutable snapshots in its constructor,
        final EnergyFlowGraph graph = new EnergyFlowGraph(
                nodes, cables, adjacency);
        final EnergyFlowGraph.FlowResult flow = graph.distribute();

        final long unsatisfiedDemand = Math.max(
                0L, flow.totalDemand() - flow.totalDelivered());
        return new EnergyDistributionResult(
                flow.totalSupply(),
                flow.totalDemand(),
                flow.totalDelivered(),
                flow.consumerDelivered(),
                flow.cableUsage(),
                unsatisfiedDemand);
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int cableCount() {
        return cables.size();
    }

    public boolean contains(final long pos) {
        return nodes.containsKey(pos) || cables.containsKey(pos);
    }

    public List<Long> neighborsOf(final long pos) {
        final List<Long> n = adjacency.get(pos);
        return n == null ? List.of() : Collections.unmodifiableList(n);
    }

    private void requireMember(final long pos) {
        if (!nodes.containsKey(pos) && !cables.containsKey(pos)) {
            throw new IllegalArgumentException(
                    "Position is not a network member: " + pos);
        }
    }
}
