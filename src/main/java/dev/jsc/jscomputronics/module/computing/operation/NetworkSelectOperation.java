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
import dev.jsc.jscomputronics.module.computing.storage.DataSink;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

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

    public static final int DEFAULT_WAIT_TIMEOUT_TICKS = 1200;

    private final ServerLevel level;
    private final NetworkUuid network;
    private final StorageKey key;
    private final long demand;
    private final DataSink destination;
    private final String destinationLabel;
    private final byte recordType;
    private final UUID operationId;
    private final NetworkIndex index;
    private final java.util.Set<NodeUuid> sourceFilter;
    private final int waitTimeoutTicks;

    private final List<Source> sources = new ArrayList<>();
    private OperationProgress progress;
    private final Map<NodeUuid, Long> movedPerServer = new HashMap<>();

    private long movedTotal;
    private int stalledTicks;
    private boolean done;
    private boolean waiting;
    private int waitTicks;
    private boolean timedOut;
    private byte status = OperationRecord.STATUS_PARTIAL;
    private Runnable onSettle;
    private java.util.function.BooleanSupplier abortWhen;

    /**
     * One SubOperation: a source server's timed transfer, with its own identity and the per-tick cap the server's hardware imposes.
     */
    private record Source(UUID subId, NodeUuid server, TransferState state, long hardwareCap) {
    }

    public NetworkSelectOperation(final ServerLevel level, final NetworkUuid network, final StorageKey key,
                                  final long demand, final DataSink destination,
                                  final String destinationLabel, final byte recordType,
                                  final UUID operationId, final NetworkIndex index,
                                  final java.util.Set<NodeUuid> sourceFilter) {
        this(level, network, key, demand, destination, destinationLabel, recordType, operationId, index,
                sourceFilter, DEFAULT_WAIT_TIMEOUT_TICKS);
    }

    public NetworkSelectOperation(final ServerLevel level, final NetworkUuid network, final StorageKey key,
                                  final long demand, final DataSink destination,
                                  final String destinationLabel, final byte recordType,
                                  final UUID operationId, final NetworkIndex index,
                                  final java.util.Set<NodeUuid> sourceFilter, final int waitTimeoutTicks) {
        this.level = level;
        this.network = network;
        this.key = key;
        this.demand = demand;
        this.destination = destination;
        this.destinationLabel = destinationLabel;
        this.recordType = recordType;
        this.operationId = operationId;
        this.index = index;
        this.sourceFilter = sourceFilter;
        this.waitTimeoutTicks = waitTimeoutTicks;

        // The tier of each server holding the item (for read latency), captured BEFORE locking —
        // after the lock, the net-of-locks location view no longer shows what we reserved.
        final Map<NodeUuid, StorageTier> tiers = captureTiers();
        // Reserve the items and split the reservation into one SubOperation per server. A non-null
        // sourceFilter restricts the pull to the picked servers (the terminal's source picker).
        final Allocation plan = index.lock(operationId, key, demand, sourceFilter);
        if (!plan.covers(demand) && index.grossAvailable(key, sourceFilter) >= demand) {
            // The items physically exist but another Operation holds (part of) them LOCKed. Hold
            index.unlock(operationId);
            waiting = true;
        } else {
            buildSources(plan, tiers);
            if (sources.isEmpty()) {
                finish(); // nothing to serve — settles immediately as FAILED
            }
        }
    }

    private Map<NodeUuid, StorageTier> captureTiers() {
        final Map<NodeUuid, StorageTier> tiers = new HashMap<>();
        for (final ItemLocation location : index.locations(key)) {
            tiers.put(location.server(), location.tier());
        }
        return tiers;
    }

    private void buildSources(final Allocation plan, final Map<NodeUuid, StorageTier> tiers) {
        plan.perServer().forEach((server, quantity) -> {
            final StorageTier tier = tiers.getOrDefault(server, StorageTier.HDD);
            sources.add(new Source(UUID.randomUUID(), server,
                    new TransferState(quantity, tier.latencyTicks()),
                    NetworkIndex.serverThroughputCap(level, server)));
        });
        this.progress = new OperationProgress(sources.stream().map(Source::state).toList());
    }

    public void tick(final long throughputBudget) {
        if (done) {
            return;
        }
        // Stop before moving anything more once the destination is dead — a player who logged out
        if (abortWhen != null && abortWhen.getAsBoolean()) {
            finish();
            return;
        }
        if (waiting) {
            retryLock();
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
            // The server streams at the slower of its orchestration share and its own hardware.
            final long planned = source.state().planTick(Math.min(shares[i], source.hardwareCap()));
            if (wasWaiting && planned == 0L) {
                waiting = true; // no progress this tick only because of read latency
            }
            if (planned <= 0L) {
                continue;
            }
            final long moved = storage.selectBreakdown(key, planned, destination, Set.of(source.server()))
                    .getOrDefault(source.server(), 0L);
            source.state().commit(moved);
            if (moved > 0L) {
                movedTotal += moved;
                movedPerServer.merge(source.server(), moved, Long::sum);
                // The moved items have left the server, so drop them from the lock: this keeps the
                // catalog from reading as over-locked to other concurrent Operations.
                index.release(operationId, key, source.server(), moved);
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

    private void retryLock() {
        if (++waitTicks > waitTimeoutTicks) {
            timedOut = true;
            finish();
            return;
        }
        final Map<NodeUuid, StorageTier> tiers = captureTiers();
        final long gross = index.grossAvailable(key, sourceFilter);
        final Allocation plan = index.lock(operationId, key, demand, sourceFilter);
        if (plan.covers(demand) || gross < demand) {
            // Fully covered — or the contended items have left the network entirely, so full
            // coverage is no longer possible and the pull proceeds with what physically remains.
            waiting = false;
            buildSources(plan, tiers);
            if (sources.isEmpty()) {
                finish();
            }
        } else {
            index.unlock(operationId); // hold nothing while waiting (no deadlock between waiters)
        }
    }

    private void finish() {
        if (done) {
            return;
        }
        done = true;
        waiting = false;
        index.unlock(operationId);
        status = timedOut ? OperationRecord.STATUS_RESOURCE_LOCKED
                : movedTotal >= demand ? OperationRecord.STATUS_COMPLETED
                : movedTotal > 0L ? OperationRecord.STATUS_PARTIAL : OperationRecord.STATUS_FAILED;
        if (onSettle != null) {
            onSettle.run();
        }
    }

    @Override
    public boolean isWaiting() {
        return waiting && !done;
    }

    public boolean isDone() {
        return done;
    }

    public NetworkSelectOperation onSettle(final Runnable callback) {
        this.onSettle = callback;
        if (done && callback != null) {
            callback.run();
        }
        return this;
    }

    public NetworkSelectOperation abortWhen(final java.util.function.BooleanSupplier predicate) {
        this.abortWhen = predicate;
        return this;
    }

    @Override
    public void abandon() {
        // Settling releases the lock and fixes the status from what was already moved; the holder
        // then sees isDone() and recovers (the moved items already left for the destination).
        finish();
    }

    public UUID operationId() {
        return operationId;
    }

    public byte status() {
        return status;
    }

    public int percent() {
        return progress == null ? 0 : progress.percent();
    }

    public OperationRecord toRecord() {
        return buildRecord(status, false);
    }

    @Override
    public OperationRecord liveRecord() {
        final byte liveStatus = done ? status
                : waiting ? OperationRecord.STATUS_WAITING : OperationRecord.STATUS_PROCESSING;
        return buildRecord(liveStatus, true);
    }

    private OperationRecord buildRecord(final byte recordStatus, final boolean includeSubs) {
        final List<OperationRecord.MoveRow> moves = new ArrayList<>();
        movedPerServer.forEach((server, moved) ->
                moves.add(new OperationRecord.MoveRow("SRV-" + shortId(server.asString()), moved, destinationLabel)));
        final List<OperationRecord.SubRow> subs = includeSubs ? subRows() : List.of();
        return new OperationRecord(recordType, key, demand, movedTotal,
                recordStatus, List.copyOf(moves), subs);
    }

    private List<OperationRecord.SubRow> subRows() {
        final List<OperationRecord.SubRow> subs = new ArrayList<>(Math.min(sources.size(),
                OperationRecord.MAX_SUBS));
        for (final Source source : sources) {
            if (subs.size() >= OperationRecord.MAX_SUBS) {
                break;
            }
            final byte state = source.state().isComplete() ? OperationRecord.SubRow.SUB_COMPLETED
                    : source.state().waitingOnLatency() ? OperationRecord.SubRow.SUB_READING
                    : OperationRecord.SubRow.SUB_STREAMING;
            subs.add(new OperationRecord.SubRow("SRV-" + shortId(source.server().asString()),
                    source.state().total(), source.state().moved(), state));
        }
        return subs;
    }

    private static String shortId(final String uuid) {
        return uuid.length() >= 6 ? uuid.substring(0, 6) : uuid;
    }
}
