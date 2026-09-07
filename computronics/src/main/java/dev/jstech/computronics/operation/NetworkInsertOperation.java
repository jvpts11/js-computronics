/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation;

import dev.jstech.computronics.blockentity.ServerRackBlockEntity;
import dev.jstech.computronics.hardware.StorageTier;
import dev.jstech.computronics.operation.index.Allocation;
import dev.jstech.computronics.operation.index.ItemLocation;
import dev.jstech.computronics.operation.index.StorageAllocator;
import dev.jstech.computronics.operation.payload.OperationRecord;
import dev.jstech.computronics.storage.ServerStore;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.core.network.NetworkSystem;
import dev.jstech.core.operation.ILatencyScheduler;
import dev.jstech.core.uuid.NetworkUuid;
import dev.jstech.core.uuid.NodeUuid;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A multi-tick INSERT: writes an item into the network's servers over time — the inverse of a SELECT. It fills the fastest-tier servers first, up to each server's free space.
 */
public final class NetworkInsertOperation extends AbstractTransferOperation {

    private final String sourceLabel;
    private final UUID operationId = UUID.randomUUID();

    public NetworkInsertOperation(final ServerLevel level, final NetworkUuid network, final StorageKey key,
                                  final long demand, final String sourceLabel, final NetworkIndex index,
                                  @Nullable final ILatencyScheduler scheduler) {
        super(level, network, key, demand);
        this.sourceLabel = sourceLabel;

        // Choose where to write: fill the fastest-tier servers first, up to each server's free space.
        final long unitWeight = key.weight(1L);
        final List<ItemLocation> free = new ArrayList<>();
        final Map<NodeUuid, StorageTier> tiers = new HashMap<>();
        final Map<NodeUuid, Integer> ramLatencies = new HashMap<>();
        for (final ItemLocation room : index.freeSpace(level, network)) {
            final long roomNative = room.quantity() / unitWeight;
            if (roomNative > 0L) {
                free.add(room.withQuantity(roomNative));
                tiers.put(room.server(), room.tier());
                ramLatencies.put(room.server(), NetworkIndex.serverRamLatencyTicks(level, room.server()));
            }
        }
        final Allocation plan = StorageAllocator.allocate(free, demand);
        index.reserveRoom(plan.perServer(), unitWeight);
        plan.perServer().forEach((server, quantity) ->
                addSource(server, quantity, tiers.getOrDefault(server, StorageTier.HDD),
                        ramLatencies.getOrDefault(server, 0), scheduler));
        buildProgress();
        if (sourcesEmpty()) {
            finish(); // the network is full — nothing written
        }
    }

    @Override
    public void tick(final long throughputBudget) {
        if (settled()) {
            return;
        }
        runTransferTick(throughputBudget);
    }

    @Override
    protected long moveFromSource(final NodeUuid server, final long planned) {
        final ServerStore store = storeOf(server);
        return store == null ? 0L : store.insert(key, planned);
    }

    @Nullable
    private ServerStore storeOf(final NodeUuid server) {
        return NetworkSystem.get(level).locationOf(server)
                .map(loc -> level.getBlockEntity(BlockPos.of(loc.rackPos())) instanceof ServerRackBlockEntity rack
                        ? rack.getServerStorage(loc.slot()) : null)
                .orElse(null);
    }

    @Override
    protected void finish() {
        markSettled(movedTotal >= demand ? OperationRecord.STATUS_COMPLETED
                : movedTotal > 0L ? OperationRecord.STATUS_PARTIAL : OperationRecord.STATUS_FAILED);
    }

    public NetworkInsertOperation onSettle(final Runnable callback) {
        setOnSettle(callback);
        return this;
    }

    public long writtenTotal() {
        return movedTotal;
    }

    public long leftover() {
        return Math.max(0L, demand - movedTotal);
    }

    @Override
    public UUID operationId() {
        return operationId;
    }

    @Override
    public String typeId() {
        return ComputingOperations.INSERT;
    }

    @Override
    public OperationRecord toRecord() {
        return buildRecord(status(), false);
    }

    @Override
    public OperationRecord liveRecord() {
        return buildRecord(settled() ? status() : OperationRecord.STATUS_PROCESSING, true);
    }

    private OperationRecord buildRecord(final byte recordStatus, final boolean includeSubs) {
        final List<OperationRecord.MoveRow> moves = new ArrayList<>();
        movedPerServer.forEach((server, written) ->
                moves.add(new OperationRecord.MoveRow(sourceLabel, written, "SRV-" + shortId(server.asString()))));
        final List<OperationRecord.SubRow> subs = includeSubs ? subRows() : List.of();
        return new OperationRecord(operationId, OperationRecord.TYPE_INSERT, key, demand, movedTotal,
                recordStatus, priority(), List.copyOf(moves), subs);
    }
}
