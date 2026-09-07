/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.crafting;

import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.operation.ComputingOperations;
import dev.jstech.computronics.operation.INetworkOperation;
import dev.jstech.computronics.operation.payload.OperationRecord;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.core.operation.OperationDispatch;
import dev.jstech.core.operation.OperationPriority;
import dev.jstech.core.operation.IOperationResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A craft request whose plan is still being worked out. Planning a recursive craft is CPU work over
 * immutable inputs (the patterns, the machines, a stock snapshot), so it runs on a virtual thread while the
 * request already shows in the task list as a pending craft. When the plan lands on the main thread the
 * real {@link NetworkCraftOperation} takes over (carrying the level and the settle callback this
 * placeholder collected meanwhile) and the placeholder steps aside without a log entry of its own. A
 * request nothing can make settles FAILED here, and one cancelled while planning settles DISCARDED.
 */
public final class PendingCraftOperation implements INetworkOperation {

    private final MainframeBlockEntity mainframe;
    private final StorageKey key;
    private final long demand;
    private final boolean partial;
    private final String label;
    private final UUID operationId = UUID.randomUUID();
    private OperationPriority priority = OperationPriority.DEFAULT;
    @Nullable
    private Runnable onSettle;
    private boolean done;
    private boolean cancelled;
    private boolean failed;
    @Nullable
    private NetworkCraftOperation delivered;

    public PendingCraftOperation(final MainframeBlockEntity mainframe, final StorageKey key, final long demand,
                                 final boolean partial, final String label) {
        this.mainframe = mainframe;
        this.key = key;
        this.demand = demand;
        this.partial = partial;
        this.label = label;
    }

    /**
     * Plans on a virtual thread from the inputs captured on the main thread, then hands the plan back to the
     * main thread, where the real craft is submitted. Nothing here touches the world off the main thread.
     */
    public void start(final OperationDispatch dispatch, final List<CraftingPattern> patterns,
                      final List<ProcessingPattern> machines, final Map<StorageKey, Long> stock) {
        dispatch.submit(context -> {
            final CraftPlanning.Planned planned = CraftPlanning.plan(key, demand, partial, patterns, machines, stock);
            context.onMainThread(() -> deliver(planned));
            return IOperationResult.success();
        }, priority);
    }

    private void deliver(@Nullable final CraftPlanning.Planned planned) {
        if (done) {
            return; // cancelled or abandoned while the plan was being made: the plan is dropped
        }
        if (planned == null) {
            failed = true;
            settle();
            return;
        }
        final NetworkCraftOperation craft = mainframe.submitPlannedCraft(key, demand, planned.plan(), label, null);
        if (craft == null) {
            failed = true;
            settle();
            return;
        }
        craft.setPriority(priority);
        if (onSettle != null) {
            craft.onSettle(onSettle);
        }
        delivered = craft;
        settle();
    }

    private void settle() {
        if (done) {
            return;
        }
        done = true;
        // The real craft fires the callback when it settles; without one, this request is over now.
        if (delivered == null && onSettle != null) {
            onSettle.run();
        }
    }

    /** Fires when the request settles, or when the craft it turned into does. */
    public PendingCraftOperation onSettle(final Runnable callback) {
        if (delivered != null) {
            delivered.onSettle(callback);
        } else if (done) {
            callback.run();
        } else {
            this.onSettle = callback;
        }
        return this;
    }

    /** The craft this request became once its plan landed, or null while planning or after a failure. */
    @Nullable
    public NetworkCraftOperation delivered() {
        return delivered;
    }

    @Override
    public UUID operationId() {
        return operationId;
    }

    @Override
    public String typeId() {
        return ComputingOperations.CRAFT;
    }

    @Override
    public void tick(final long throughputBudget) {
        // Nothing to do: the plan arrives through the dispatcher's main-thread hand-off.
    }

    @Override
    public boolean isDone() {
        return done;
    }

    @Override
    public boolean isWaiting() {
        return !done; // holds no queue slot while planning
    }

    @Override
    public boolean silent() {
        return delivered != null; // the craft it became carries the log entry
    }

    @Override
    public void abandon() {
        settle();
    }

    @Override
    public void cancel() {
        if (done) {
            return;
        }
        cancelled = true;
        settle();
    }

    @Override
    public OperationPriority priority() {
        return priority;
    }

    @Override
    public void setPriority(final OperationPriority priority) {
        this.priority = Objects.requireNonNull(priority, "priority");
        if (delivered != null) {
            delivered.setPriority(priority);
        }
    }

    @Override
    public OperationRecord toRecord() {
        final byte status = cancelled ? OperationRecord.STATUS_DISCARDED
                : failed ? OperationRecord.STATUS_FAILED
                : delivered != null ? OperationRecord.STATUS_COMPLETED
                : OperationRecord.STATUS_PENDING;
        return new OperationRecord(operationId, OperationRecord.TYPE_CRAFT, key, demand, 0L, status, priority,
                List.of(), List.of());
    }

    @Override
    public OperationRecord liveRecord() {
        return toRecord();
    }
}
