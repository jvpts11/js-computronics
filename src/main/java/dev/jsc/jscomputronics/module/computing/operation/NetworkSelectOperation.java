/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation;

import dev.jsc.jscomputronics.common.hardware.StorageTier;
import dev.jsc.jscomputronics.common.operation.index.Allocation;
import dev.jsc.jscomputronics.common.operation.index.ItemLocation;
import dev.jsc.jscomputronics.common.operation.exec.EqualShare;
import dev.jsc.jscomputronics.common.operation.exec.OperationProgress;
import dev.jsc.jscomputronics.common.operation.exec.TransferState;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * A multi-tick SELECT: pulls an item out of the network's servers into a destination over time.
 */
public final class NetworkSelectOperation implements NetworkOperation {

    private static final int STALL_LIMIT = 40;

    private final ServerLevel level;
    private final NetworkUuid network;
    private final Item item;
    private final long demand;
    private final IItemHandler destination;
    private final String destinationLabel;
    private final byte recordType;
    private final UUID operationId;
    private final NetworkIndex index;

    private final List<Source> sources = new ArrayList<>();
    private final OperationProgress progress;
    private final Map<NodeUuid, Long> movedPerServer = new HashMap<>();

    private long movedTotal;
    private int stalledTicks;
    private boolean done;
    private byte status = OperationRecord.STATUS_PARTIAL;

    /**
     * One source server's timed transfer.
     */
    private record Source(NodeUuid server, TransferState state) {
    }

    public NetworkSelectOperation(final ServerLevel level, final NetworkUuid network, final Item item,
                                  final long demand, final IItemHandler destination,
                                  final String destinationLabel, final byte recordType,
                                  final UUID operationId, final NetworkIndex index) {
        this.level = level;
        this.network = network;
        this.item = item;
        this.demand = demand;
        this.destination = destination;
        this.destinationLabel = destinationLabel;
        this.recordType = recordType;
        this.operationId = operationId;
        this.index = index;

        // The tier of each server holding the item (for read latency), captured before locking.
        final Map<NodeUuid, StorageTier> tiers = new HashMap<>();
        for (final ItemLocation location : index.locations(item)) {
            tiers.put(location.server(), location.tier());
        }
        // Reserve the items and split the reservation into one SubOperation per server.
        final Allocation plan = index.lock(operationId, item, demand);
        plan.perServer().forEach((server, quantity) -> {
            final StorageTier tier = tiers.getOrDefault(server, StorageTier.HDD);
            sources.add(new Source(server, new TransferState(quantity, tier.latencyTicks())));
        });
        this.progress = new OperationProgress(sources.stream().map(Source::state).toList());
        if (sources.isEmpty()) {
            finish(); // nothing to serve — settles immediately as FAILED
        }
    }

    public void tick(final long throughputBudget) {
        if (done) {
            return;
        }
        // Split this Operation's grant exactly across its sources (no floor), so the sources never
        // move more than the Mainframe budgeted — a source that gets 0 simply waits for a later tick.
        final long[] shares = EqualShare.split(throughputBudget, sources.size());
        final NetworkStorage storage = NetworkStorage.of(level, network);
        boolean movedAny = false;
        boolean waiting = false;

        for (int i = 0; i < sources.size(); i++) {
            final Source source = sources.get(i);
            final boolean wasWaiting = source.state().waitingOnLatency();
            final long planned = source.state().planTick(shares[i]);
            if (wasWaiting && planned == 0L) {
                waiting = true; // no progress this tick only because of read latency
            }
            if (planned <= 0L) {
                continue;
            }
            final long moved = storage.selectBreakdown(item, planned, destination, Set.of(source.server()))
                    .getOrDefault(source.server(), 0L);
            source.state().commit(moved);
            if (moved > 0L) {
                movedTotal += moved;
                movedPerServer.merge(source.server(), moved, Long::sum);
                // The moved items have left the server, so drop them from the lock: this keeps the
                // catalog from reading as over-locked to other concurrent Operations.
                index.release(operationId, item, source.server(), moved);
                movedAny = true;
            }
        }

        if (progress.isComplete()) {
            finish();
        } else if (!movedAny && !waiting && ++stalledTicks >= STALL_LIMIT) {
            // No progress and nothing left to wait for (e.g. a full destination): settle.
            finish();
        } else if (movedAny) {
            stalledTicks = 0;
        }
    }

    private void finish() {
        if (done) {
            return;
        }
        done = true;
        index.unlock(operationId);
        status = movedTotal >= demand ? OperationRecord.STATUS_COMPLETED
                : movedTotal > 0L ? OperationRecord.STATUS_PARTIAL : OperationRecord.STATUS_FAILED;
    }

    public boolean isDone() {
        return done;
    }

    public UUID operationId() {
        return operationId;
    }

    public byte status() {
        return status;
    }

    public int percent() {
        return progress.percent();
    }

    public OperationRecord toRecord() {
        final List<OperationRecord.MoveRow> moves = new ArrayList<>();
        movedPerServer.forEach((server, moved) ->
                moves.add(new OperationRecord.MoveRow("SRV-" + shortId(server.asString()), moved, destinationLabel)));
        return new OperationRecord(recordType, new ItemStack(item), demand, movedTotal,
                status, List.copyOf(moves));
    }

    private static String shortId(final String uuid) {
        return uuid.length() >= 6 ? uuid.substring(0, 6) : uuid;
    }
}
