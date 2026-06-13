/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation;

import dev.jsc.jscomputronics.common.hardware.StorageTier;
import dev.jsc.jscomputronics.common.operation.LatencyScheduler;
import dev.jsc.jscomputronics.common.operation.exec.EqualShare;
import dev.jsc.jscomputronics.common.operation.exec.OperationProgress;
import dev.jsc.jscomputronics.common.operation.exec.TransferState;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared engine for the timed transfer Operations (SELECT and INSERT). Both split a demand into one SubOperation per server, model each disk's read/write latency as a virtual thread that flips the SubOperation ready, then stream the item over time within the Mainframe's orchestration budget and each server's hardware cap.
 *
 * <p>Subclasses supply only how a single server is moved ({@link #moveFromSource}) and how the provenance record reads ({@link #toRecord()}/{@link #liveRecord()}); the latency build, the per-tick share-and-stall loop, the SubOperation rows and the settle bookkeeping live here so the two operations cannot drift apart.
 */
public abstract class AbstractTransferOperation implements NetworkOperation {

    protected static final int STALL_LIMIT = 40;

    protected final ServerLevel level;
    protected final NetworkUuid network;
    protected final StorageKey key;
    protected final long demand;

    protected final List<Source> sources = new ArrayList<>();
    protected final Map<NodeUuid, Long> movedPerServer = new HashMap<>();
    private OperationProgress progress;

    protected long movedTotal;
    private int stalledTicks;
    private boolean done;
    private byte status = OperationRecord.STATUS_PARTIAL;
    @Nullable
    private Runnable onSettle;

    /**
     * One SubOperation: a server's timed transfer, with its own per-tick hardware cap and a {@code ready} gate a virtual thread flips once the disk's read/write latency has elapsed, so several disks transfer in parallel, each parked on its own thread.
     */
    protected static final class Source {
        final NodeUuid server;
        final TransferState state;
        final long hardwareCap;
        volatile boolean ready;

        Source(final NodeUuid server, final TransferState state, final long hardwareCap,
               final boolean ready) {
            this.server = server;
            this.state = state;
            this.hardwareCap = hardwareCap;
            this.ready = ready;
        }
    }

    protected AbstractTransferOperation(final ServerLevel level, final NetworkUuid network,
                                        final StorageKey key, final long demand) {
        this.level = level;
        this.network = network;
        this.key = key;
        this.demand = demand;
    }

    /**
     * Adds one SubOperation for a server. With a scheduler the disk's latency is parked on a virtual thread that flips the source ready once it elapses (disks transfer in parallel); without one the latency is counted on the main thread, the directly-driven behavior.
     */
    protected final void addSource(final NodeUuid server, final long quantity, final StorageTier tier,
                                   @Nullable final LatencyScheduler scheduler) {
        final int latency = tier.latencyTicks();
        final long cap = NetworkIndex.serverThroughputCap(level, server);
        final Source source;
        if (scheduler != null) {
            source = new Source(server, new TransferState(quantity, 0), cap, false);
            scheduler.afterTicks(latency, () -> source.ready = true);
        } else {
            source = new Source(server, new TransferState(quantity, latency), cap, true);
        }
        sources.add(source);
    }

    /** Builds the progress aggregate over the SubOperations; call once after the sources are added. */
    protected final void buildProgress() {
        this.progress = new OperationProgress(sources.stream().map(s -> s.state).toList());
    }

    protected final boolean sourcesEmpty() {
        return sources.isEmpty();
    }

    protected final boolean settled() {
        return done;
    }

    /**
     * Advances every ready SubOperation by its equal share of the budget, capped by the server's hardware, then settles when the transfer completes or stalls with nothing left to wait for. Subclasses move a single server through {@link #moveFromSource}.
     */
    protected final void runTransferTick(final long throughputBudget) {
        onTickStart();
        final long[] shares = EqualShare.split(throughputBudget, sources.size());
        boolean movedAny = false;
        boolean waitingOnLatency = false;

        for (int i = 0; i < sources.size(); i++) {
            final Source source = sources.get(i);
            if (!source.ready) {
                waitingOnLatency = true; // the disk is still in its latency, parked on its own thread
                continue;
            }
            final boolean wasWaiting = source.state.waitingOnLatency();
            final long planned = source.state.planTick(Math.min(shares[i], source.hardwareCap));
            if (wasWaiting && planned == 0L) {
                waitingOnLatency = true; // no progress this tick only because of read/write latency
            }
            if (planned <= 0L) {
                continue;
            }
            final long moved = moveFromSource(source.server, planned);
            source.state.commit(moved);
            if (moved > 0L) {
                movedTotal += moved;
                movedPerServer.merge(source.server, moved, Long::sum);
                movedAny = true;
            }
        }

        if (progress.isComplete()) {
            finish();
        } else if (!movedAny && !waitingOnLatency && ++stalledTicks >= STALL_LIMIT) {
            finish();
        } else if (movedAny) {
            stalledTicks = 0;
        }
    }

    /** Hook run once at the start of each transfer tick, before the sources are advanced. */
    protected void onTickStart() {
    }

    /** Moves up to {@code planned} of the key through a single server, returning the amount moved. */
    protected abstract long moveFromSource(NodeUuid server, long planned);

    /** Settles the Operation; subclasses do their own cleanup, then call {@link #markSettled}. */
    protected abstract void finish();

    @Override
    public void abandon() {
        finish();
    }

    /** Records the final status, marks the Operation done and runs the settle callback exactly once. */
    protected final void markSettled(final byte finalStatus) {
        if (done) {
            return;
        }
        done = true;
        this.status = finalStatus;
        if (onSettle != null) {
            onSettle.run();
        }
    }

    /** Installs the settle callback, firing it immediately if the Operation has already settled. */
    protected final void setOnSettle(@Nullable final Runnable callback) {
        this.onSettle = callback;
        if (done && callback != null) {
            callback.run();
        }
    }

    @Override
    public boolean isDone() {
        return done;
    }

    public byte status() {
        return status;
    }

    public int percent() {
        return progress == null ? 0 : progress.percent();
    }

    /** The SubOperation rows for the live record: one per server, with its read/stream/done state. */
    protected final List<OperationRecord.SubRow> subRows() {
        final List<OperationRecord.SubRow> subs = new ArrayList<>(Math.min(sources.size(),
                OperationRecord.MAX_SUBS));
        for (final Source source : sources) {
            if (subs.size() >= OperationRecord.MAX_SUBS) {
                break;
            }
            final byte state = source.state.isComplete() ? OperationRecord.SubRow.SUB_COMPLETED
                    : (!source.ready || source.state.waitingOnLatency()) ? OperationRecord.SubRow.SUB_READING
                    : OperationRecord.SubRow.SUB_STREAMING;
            subs.add(new OperationRecord.SubRow("SRV-" + shortId(source.server.asString()),
                    source.state.total(), source.state.moved(), state));
        }
        return subs;
    }

    protected static String shortId(final String uuid) {
        return uuid.length() >= 6 ? uuid.substring(0, 6) : uuid;
    }
}
