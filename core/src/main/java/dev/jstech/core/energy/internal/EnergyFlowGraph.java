/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.energy.internal;

import dev.jstech.core.energy.IEnergyCable;
import dev.jstech.core.energy.IEnergyNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Internal structure that runs one supply→demand BFS tick.
 */
public final class EnergyFlowGraph {

    private final Map<Long, IEnergyNode> nodes;
    private final Map<Long, IEnergyCable> cables;
    private final Map<Long, List<Long>> adjacency;

    public EnergyFlowGraph(
            final Map<Long, IEnergyNode> nodes,
            final Map<Long, IEnergyCable> cables,
            final Map<Long, List<Long>> adjacency) {
        this.nodes = Map.copyOf(nodes);
        this.cables = Map.copyOf(cables);
        this.adjacency = Map.copyOf(adjacency);
    }

    /**
     * Raw result of one distribution: used internally by {@link EnergyNetwork} to build the {@link dev.jstech.core.energy.EnergyDistributionResult} exposed to the caller.
     */
    public record FlowResult(
            long totalSupply,
            long totalDemand,
            long totalDelivered,
            Map<Long, Long> consumerDelivered,
            Map<Long, Long> cableUsage) {
    }

    public FlowResult distribute() {
        // 1-2: collect reported supply and demand.
        final Map<Long, Long> reportedSupply = new LinkedHashMap<>();
        final Map<Long, Long> reportedDemand = new LinkedHashMap<>();
        // Iterate in natural Long order for determinism.
        nodes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    final long pos = e.getKey();
                    final IEnergyNode node = e.getValue();
                    if (node.role().canSupply()) {
                        final long s = Math.max(0L, node.supply());
                        if (s > 0) {
                            reportedSupply.put(pos, s);
                        }
                    }
                    if (node.role().canConsume()) {
                        final long d = Math.max(0L, node.demand());
                        if (d > 0) {
                            reportedDemand.put(pos, d);
                        }
                    }
                });

        long totalSupply = 0L;
        for (final Long v : reportedSupply.values()) {
            totalSupply += v;
        }
        long totalDemand = 0L;
        for (final Long v : reportedDemand.values()) {
            totalDemand += v;
        }

        // Empty case.
        if (totalSupply == 0L || totalDemand == 0L) {
            return new FlowResult(totalSupply, totalDemand, 0L,
                    Map.of(), Map.of());
        }

        // 3: remaining throughput per cable (Long.MAX_VALUE for T7).
        final Map<Long, Long> remainingThroughput = new HashMap<>();
        for (final Map.Entry<Long, IEnergyCable> e : cables.entrySet()) {
            remainingThroughput.put(e.getKey(), e.getValue().maxThroughput());
        }

        // Remaining demand per consumer — updated as flow accumulates.
        final Map<Long, Long> remainingDemand = new LinkedHashMap<>(reportedDemand);

        // Accumulated result.
        final Map<Long, Long> consumerDelivered = new LinkedHashMap<>();
        final Map<Long, Long> cableUsage = new HashMap<>();
        long totalDelivered = 0L;

        // 4: iterate generators in deterministic order.
        for (final Map.Entry<Long, Long> gen : reportedSupply.entrySet()) {
            final long generatorPos = gen.getKey();
            final long generatorSupply = gen.getValue();
            if (generatorSupply <= 0) {
                continue;
            }

            // 4.1: BFS from the generator, recording paths.
            final Map<Long, List<Long>> pathsToConsumers =
                    bfsPaths(generatorPos, remainingDemand.keySet());

            if (pathsToConsumers.isEmpty()) {
                continue;
            }

            // 4.2: for each reached consumer, compute cap = min remaining
            final Map<Long, Long> consumerCap = new LinkedHashMap<>();
            final Map<Long, Long> consumerCurrentDemand = new LinkedHashMap<>();
            for (final Map.Entry<Long, List<Long>> p : pathsToConsumers.entrySet()) {
                final long consumerPos = p.getKey();
                final long pathCap = minThroughputAlong(p.getValue(),
                        remainingThroughput);
                if (pathCap <= 0) {
                    continue;
                }
                final long demandLeft = remainingDemand.getOrDefault(consumerPos, 0L);
                if (demandLeft <= 0) {
                    continue;
                }
                consumerCap.put(consumerPos, pathCap);
                consumerCurrentDemand.put(consumerPos, demandLeft);
            }

            if (consumerCurrentDemand.isEmpty()) {
                continue;
            }

            // 4.3: split supply proportionally.
            final Map<Long, Long> allocations = ProportionalSplitter.split(
                    generatorSupply, consumerCurrentDemand, consumerCap);

            if (allocations.isEmpty()) {
                continue;
            }

            // 4.4: apply flow.
            long deliveredByThisGenerator = 0L;
            for (final Map.Entry<Long, Long> a : allocations.entrySet()) {
                final long consumerPos = a.getKey();
                final List<Long> path = pathsToConsumers.get(consumerPos);
                // Clamp to the LIVE remaining throughput along this path. The proportional split was computed
                // from per-consumer caps snapshotted before any flow was applied; when two or more consumers
                // share a bottleneck cable those caps overlap, so the split can sum to more than the cable can
                // carry. Applying each consumer's flow in order against the cable's live remaining capacity
                // keeps the total through any shared cable within its rated throughput — no FE is created.
                final long amount = Math.min(a.getValue(), minThroughputAlong(path, remainingThroughput));
                if (amount <= 0) {
                    continue;
                }

                // Subtract throughput from path cables.
                for (final Long step : path) {
                    if (cables.containsKey(step)) {
                        final long current = remainingThroughput.get(step);
                        if (current != Long.MAX_VALUE) {
                            remainingThroughput.put(step, current - amount);
                        }
                        cableUsage.merge(step, amount, Long::sum);
                    }
                }

                // Update remaining demand and result.
                remainingDemand.merge(consumerPos, -amount, Long::sum);
                consumerDelivered.merge(consumerPos, amount, Long::sum);
                deliveredByThisGenerator += amount;
            }
            totalDelivered += deliveredByThisGenerator;

            // Notify generator.
            nodes.get(generatorPos).onSupplied(deliveredByThisGenerator);
        }

        // 5: notify consumers.
        for (final Map.Entry<Long, Long> e : consumerDelivered.entrySet()) {
            nodes.get(e.getKey()).onConsumed(e.getValue());
        }

        return new FlowResult(
                totalSupply, totalDemand, totalDelivered,
                Map.copyOf(consumerDelivered),
                Map.copyOf(cableUsage));
    }

    private Map<Long, List<Long>> bfsPaths(
            final long source,
            final Set<Long> consumerPositions) {

        final Map<Long, List<Long>> result = new LinkedHashMap<>();
        if (consumerPositions.isEmpty()) {
            return result;
        }

        // Classic BFS: visited + queue + parent map for path reconstruction.
        final Set<Long> visited = new HashSet<>();
        final Deque<Long> queue = new ArrayDeque<>();
        final Map<Long, Long> parent = new HashMap<>();

        visited.add(source);
        queue.add(source);

        while (!queue.isEmpty()) {
            final long current = queue.pollFirst();

            // If this is a consumer (and not the source), reconstruct path.
            if (current != source && consumerPositions.contains(current)) {
                result.put(current, reconstructCablePath(parent, source, current));
            }

            // Don't traverse through nodes (nodes are leaves from the flow
            if (current != source && nodes.containsKey(current)) {
                continue;
            }

            final List<Long> neighbors = adjacency.getOrDefault(current, List.of());
            for (final Long next : neighbors) {
                if (visited.add(next)) {
                    parent.put(next, current);
                    queue.addLast(next);
                }
            }
        }

        return result;
    }

    private List<Long> reconstructCablePath(
            final Map<Long, Long> parent,
            final long source,
            final long consumer) {

        final ArrayList<Long> reversedPath = new ArrayList<>();
        long current = parent.get(consumer);
        while (current != source) {
            if (cables.containsKey(current)) {
                reversedPath.add(current);
            }
            current = parent.get(current);
        }
        Collections.reverse(reversedPath);
        return List.copyOf(reversedPath);
    }

    private long minThroughputAlong(
            final List<Long> path,
            final Map<Long, Long> remainingThroughput) {
        if (path.isEmpty()) {
            // A direct node-to-node connection with no cable in between is not a valid power path: treat it
            // as non-traversable rather than an unlimited pipe (the MAX_VALUE sentinel) so a future direct
            // wire can never bypass cable-tier throughput limits and silently create energy.
            return 0L;
        }
        long min = Long.MAX_VALUE;
        for (final Long cablePos : path) {
            final long t = remainingThroughput.getOrDefault(cablePos, 0L);
            if (t < min) {
                min = t;
            }
        }
        return min;
    }
}
