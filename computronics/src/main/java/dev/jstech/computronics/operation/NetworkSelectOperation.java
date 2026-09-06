/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation;

import dev.jstech.computronics.hardware.StorageTier;
import dev.jstech.computronics.operation.index.Allocation;
import dev.jstech.computronics.operation.index.ItemLocation;
import dev.jstech.computronics.operation.payload.OperationRecord;
import dev.jstech.computronics.storage.DataSink;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.core.operation.LatencyScheduler;
import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.core.uuid.NodeUuid;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * A multi-tick SELECT: pulls an item out of the network's servers into a destination over time. It reserves the items first and, if another Operation already holds them LOCKed, waits (holding nothing) until they free up.
 */
public final class NetworkSelectOperation extends AbstractTransferOperation {

    /** The design default of the WAITING timeout; the live value comes from the balance config. */
    public static final int DEFAULT_WAIT_TIMEOUT_TICKS =
            dev.jstech.core.operation.OperationBalance.DEFAULT_WAITING_TIMEOUT_TICKS;

    private final DataSink destination;
    private final String destinationLabel;
    private final byte recordType;
    private final UUID operationId;
    private final NetworkIndex index;
    @Nullable
    private final LatencyScheduler scheduler;
    private final Set<NodeUuid> sourceFilter;
    private final int waitTimeoutTicks;

    @Nullable
    private NetworkStorage tickStorage;
    private boolean waiting;
    private int waitTicks;
    private boolean timedOut;
    @Nullable
    private BooleanSupplier abortWhen;

    public NetworkSelectOperation(final ServerLevel level, final NetworkUuid network, final StorageKey key,
                                  final long demand, final DataSink destination,
                                  final String destinationLabel, final byte recordType,
                                  final UUID operationId, final NetworkIndex index,
                                  @Nullable final LatencyScheduler scheduler,
                                  final Set<NodeUuid> sourceFilter) {
        this(level, network, key, demand, destination, destinationLabel, recordType, operationId, index,
                scheduler, sourceFilter, dev.jstech.core.operation.OperationBalance.waitingTimeoutTicks());
    }

    public NetworkSelectOperation(final ServerLevel level, final NetworkUuid network, final StorageKey key,
                                  final long demand, final DataSink destination,
                                  final String destinationLabel, final byte recordType,
                                  final UUID operationId, final NetworkIndex index,
                                  @Nullable final LatencyScheduler scheduler,
                                  final Set<NodeUuid> sourceFilter, final int waitTimeoutTicks) {
        super(level, network, key, demand);
        this.destination = destination;
        this.destinationLabel = destinationLabel;
        this.recordType = recordType;
        this.operationId = operationId;
        this.index = index;
        this.scheduler = scheduler;
        this.sourceFilter = sourceFilter;
        this.waitTimeoutTicks = waitTimeoutTicks;

        // Capture disk tiers and RAM latencies BEFORE locking — after the lock the net-of-locks
        // location view no longer shows the full picture of what we reserved.
        final Map<NodeUuid, StorageTier> tiers = captureTiers();
        final Map<NodeUuid, Integer> ramLatencies = captureRamLatencies();
        // Reserve the items and split the reservation into one SubOperation per server. A non-null
        // sourceFilter restricts the pull to the picked servers (the terminal's source picker).
        final Allocation plan = index.lock(operationId, key, demand, sourceFilter);
        if (!plan.covers(demand) && index.grossAvailable(key, sourceFilter) >= demand) {
            // The items physically exist but another Operation holds (part of) them LOCKed. Hold
            // nothing and wait for them to free up.
            index.unlock(operationId);
            waiting = true;
        } else {
            buildSources(plan, tiers, ramLatencies);
            if (sourcesEmpty()) {
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

    private Map<NodeUuid, Integer> captureRamLatencies() {
        final Map<NodeUuid, Integer> ramLatencies = new HashMap<>();
        for (final ItemLocation location : index.locations(key)) {
            ramLatencies.put(location.server(),
                    NetworkIndex.serverRamLatencyTicks(level, location.server()));
        }
        return ramLatencies;
    }

    private void buildSources(final Allocation plan, final Map<NodeUuid, StorageTier> tiers,
                              final Map<NodeUuid, Integer> ramLatencies) {
        plan.perServer().forEach((server, quantity) ->
                addSource(server, quantity, tiers.getOrDefault(server, StorageTier.HDD),
                        ramLatencies.getOrDefault(server, 0), scheduler));
        buildProgress();
    }

    @Override
    public void tick(final long throughputBudget) {
        if (settled()) {
            return;
        }
        // Stop before moving anything more once the destination is dead — a player who logged out
        // or a closed terminal can no longer receive items.
        if (abortWhen != null && abortWhen.getAsBoolean()) {
            finish();
            return;
        }
        if (waiting) {
            retryLock();
            return;
        }
        runTransferTick(throughputBudget);
    }

    @Override
    protected void onTickStart() {
        // Build the live network view once per tick; every source pulls from the same snapshot.
        tickStorage = NetworkStorage.of(level, network);
    }

    @Override
    protected long moveFromSource(final NodeUuid server, final long planned) {
        final long moved = tickStorage.pullFrom(server, key, planned, destination);
        if (moved > 0L) {
            // The moved items have left the server, so drop them from the lock: this keeps the
            // catalog from reading as over-locked to other concurrent Operations.
            index.release(operationId, key, server, moved);
        }
        return moved;
    }

    private void retryLock() {
        if (++waitTicks >= waitTimeoutTicks) {
            timedOut = true;
            finish();
            return;
        }
        final Map<NodeUuid, StorageTier> tiers = captureTiers();
        final Map<NodeUuid, Integer> ramLatencies = captureRamLatencies();
        final long gross = index.grossAvailable(key, sourceFilter);
        final Allocation plan = index.lock(operationId, key, demand, sourceFilter);
        if (plan.covers(demand) || gross < demand) {
            // Fully covered — or the contended items have left the network entirely, so full
            // coverage is no longer possible and the pull proceeds with what physically remains.
            waiting = false;
            buildSources(plan, tiers, ramLatencies);
            if (sourcesEmpty()) {
                finish();
            }
        } else {
            index.unlock(operationId); // hold nothing while waiting (no deadlock between waiters)
        }
    }

    @Override
    protected void finish() {
        if (settled()) {
            return;
        }
        waiting = false;
        index.unlock(operationId);
        markSettled(timedOut ? OperationRecord.STATUS_RESOURCE_LOCKED
                : movedTotal >= demand ? OperationRecord.STATUS_COMPLETED
                : movedTotal > 0L ? OperationRecord.STATUS_PARTIAL : OperationRecord.STATUS_FAILED);
    }

    @Override
    public boolean isWaiting() {
        return waiting && !settled();
    }

    public NetworkSelectOperation onSettle(final Runnable callback) {
        setOnSettle(callback);
        return this;
    }

    public NetworkSelectOperation abortWhen(final BooleanSupplier predicate) {
        this.abortWhen = predicate;
        return this;
    }

    @Override
    public UUID operationId() {
        return operationId;
    }

    @Override
    public String typeId() {
        return switch (recordType) {
            case OperationRecord.TYPE_MOVE -> ComputingOperations.MOVE;
            case OperationRecord.TYPE_DELETE -> ComputingOperations.DELETE;
            default -> ComputingOperations.SELECT;
        };
    }

    @Override
    public OperationRecord toRecord() {
        return buildRecord(status(), false);
    }

    @Override
    public OperationRecord liveRecord() {
        final byte liveStatus = settled() ? status()
                : waiting ? OperationRecord.STATUS_WAITING : OperationRecord.STATUS_PROCESSING;
        return buildRecord(liveStatus, true);
    }

    private OperationRecord buildRecord(final byte recordStatus, final boolean includeSubs) {
        final List<OperationRecord.MoveRow> moves = new ArrayList<>();
        movedPerServer.forEach((server, moved) ->
                moves.add(new OperationRecord.MoveRow("SRV-" + shortId(server.asString()), moved, destinationLabel)));
        final List<OperationRecord.SubRow> subs = includeSubs ? subRows() : List.of();
        return new OperationRecord(operationId, recordType, key, demand, movedTotal,
                recordStatus, priority(), List.copyOf(moves), subs);
    }
}
