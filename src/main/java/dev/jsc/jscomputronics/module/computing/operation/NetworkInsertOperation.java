/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation;

import dev.jsc.jscomputronics.common.hardware.StorageTier;
import dev.jsc.jscomputronics.common.operation.exec.EqualShare;
import dev.jsc.jscomputronics.common.operation.exec.OperationProgress;
import dev.jsc.jscomputronics.common.operation.exec.TransferState;
import dev.jsc.jscomputronics.common.operation.index.Allocation;
import dev.jsc.jscomputronics.common.operation.index.ItemLocation;
import dev.jsc.jscomputronics.common.operation.index.StorageAllocator;
import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord;
import dev.jsc.jscomputronics.module.computing.storage.ServerStore;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A multi-tick INSERT: writes an item into the network's servers over time — the inverse of a SELECT.
 */
public final class NetworkInsertOperation implements NetworkOperation {

    private static final int STALL_LIMIT = 40;

    private final ServerLevel level;
    private final NetworkUuid network;
    private final StorageKey key;
    private final long demand;
    private final String sourceLabel;

    private final List<Source> sources = new ArrayList<>();
    private final OperationProgress progress;
    private final Map<NodeUuid, Long> writtenPerServer = new HashMap<>();

    private long writtenTotal;
    private int stalledTicks;
    private boolean done;
    private byte status = OperationRecord.STATUS_PARTIAL;
    private Runnable onSettle;

    private record Source(NodeUuid server, TransferState state, long hardwareCap) {
    }

    public NetworkInsertOperation(final ServerLevel level, final NetworkUuid network, final StorageKey key,
                                  final long demand, final String sourceLabel, final NetworkIndex index) {
        this.level = level;
        this.network = network;
        this.key = key;
        this.demand = demand;
        this.sourceLabel = sourceLabel;

        // Choose where to write: fill the fastest-tier servers first, up to each server's free space.
        final long unitWeight = key.weight(1L);
        final List<ItemLocation> free = new ArrayList<>();
        final Map<NodeUuid, StorageTier> tiers = new HashMap<>();
        for (final ItemLocation room : index.freeSpace(level, network)) {
            final long roomNative = room.quantity() / unitWeight;
            if (roomNative > 0L) {
                free.add(room.withQuantity(roomNative));
                tiers.put(room.server(), room.tier());
            }
        }
        final Allocation plan = StorageAllocator.allocate(free, demand);
        plan.perServer().forEach((server, quantity) -> {
            final StorageTier tier = tiers.getOrDefault(server, StorageTier.HDD);
            sources.add(new Source(server, new TransferState(quantity, tier.latencyTicks()),
                    NetworkIndex.serverThroughputCap(level, server)));
        });
        this.progress = new OperationProgress(sources.stream().map(Source::state).toList());
        if (sources.isEmpty()) {
            finish(); // the network is full — nothing written
        }
    }

    @Override
    public void tick(final long throughputBudget) {
        if (done) {
            return;
        }
        final long[] shares = EqualShare.split(throughputBudget, sources.size());
        boolean movedAny = false;
        boolean waiting = false;

        for (int i = 0; i < sources.size(); i++) {
            final Source source = sources.get(i);
            final boolean wasWaiting = source.state().waitingOnLatency();
            // The server absorbs writes at the slower of its orchestration share and its hardware.
            final long planned = source.state().planTick(Math.min(shares[i], source.hardwareCap()));
            if (wasWaiting && planned == 0L) {
                waiting = true;
            }
            if (planned <= 0L) {
                continue;
            }
            final ServerStore store = storeOf(source.server());
            final long written = store == null ? 0L : store.insert(key, planned);
            source.state().commit(written);
            if (written > 0L) {
                writtenTotal += written;
                writtenPerServer.merge(source.server(), written, Long::sum);
                movedAny = true;
            }
        }

        if (progress.isComplete()) {
            finish();
        } else if (!movedAny && !waiting && ++stalledTicks >= STALL_LIMIT) {
            finish();
        } else if (movedAny) {
            stalledTicks = 0;
        }
    }

    private ServerStore storeOf(final NodeUuid server) {
        return NetworkSystem.get(level).locationOf(server)
                .map(loc -> level.getBlockEntity(BlockPos.of(loc.rackPos())) instanceof ServerRackBlockEntity rack
                        ? rack.getServerStorage(loc.slot()) : null)
                .orElse(null);
    }

    private void finish() {
        if (done) {
            return;
        }
        done = true;
        status = writtenTotal >= demand ? OperationRecord.STATUS_COMPLETED
                : writtenTotal > 0L ? OperationRecord.STATUS_PARTIAL : OperationRecord.STATUS_FAILED;
        if (onSettle != null) {
            onSettle.run();
        }
    }

    @Override
    public boolean isDone() {
        return done;
    }

    public NetworkInsertOperation onSettle(final Runnable callback) {
        this.onSettle = callback;
        if (done && callback != null) {
            callback.run();
        }
        return this;
    }

    @Override
    public void abandon() {
        // Settling fixes the status from what was already written; the holder then sees isDone() and
        // re-buffers the unwritten remainder reported by leftover(), so no buffered items are lost.
        finish();
    }

    public byte status() {
        return status;
    }

    public long writtenTotal() {
        return writtenTotal;
    }

    public long leftover() {
        return Math.max(0L, demand - writtenTotal);
    }

    @Override
    public OperationRecord toRecord() {
        return buildRecord(status);
    }

    @Override
    public OperationRecord liveRecord() {
        return buildRecord(done ? status : OperationRecord.STATUS_PROCESSING);
    }

    private OperationRecord buildRecord(final byte recordStatus) {
        final List<OperationRecord.MoveRow> moves = new ArrayList<>();
        writtenPerServer.forEach((server, written) ->
                moves.add(new OperationRecord.MoveRow(sourceLabel, written, "SRV-" + shortId(server.asString()))));
        return new OperationRecord(OperationRecord.TYPE_INSERT, key, demand, writtenTotal,
                recordStatus, List.copyOf(moves));
    }

    private static String shortId(final String uuid) {
        return uuid.length() >= 6 ? uuid.substring(0, 6) : uuid;
    }
}
