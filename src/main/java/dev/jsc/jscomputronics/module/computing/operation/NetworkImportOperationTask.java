/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation;

import dev.jsc.jscomputronics.common.network.NetworkSystem;
import dev.jsc.jscomputronics.common.operation.OperationContext;
import dev.jsc.jscomputronics.common.operation.OperationResult;
import dev.jsc.jscomputronics.common.operation.OperationTask;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.common.uuid.NodeUuid;
import dev.jsc.jscomputronics.module.computing.block.part.ImportBusPart;
import dev.jsc.jscomputronics.module.computing.blockentity.DataCableBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An INSERT dispatched by an Import Bus: it pushes a buffered stack (already taken off the bus, "in flight") into the network's Server storage, records which Servers accepted it for provenance, and returns whatever the network could not hold to the bus so it retries on the next flush.
 */
public final class NetworkImportOperationTask implements OperationTask {

    private final ServerLevel level;
    private final NetworkUuid network;
    private final ItemStack payload;
    private final BlockPos cablePos;
    private final Direction face;

    public NetworkImportOperationTask(final ServerLevel level, final NetworkUuid network,
                                      final ItemStack payload, final BlockPos cablePos, final Direction face) {
        this.level = level;
        this.network = network;
        this.payload = payload;
        this.cablePos = cablePos.immutable();
        this.face = face;
    }

    @Override
    public OperationResult run(final OperationContext context) {
        final int requested = payload.getCount();
        if (requested <= 0) {
            return OperationResult.success();
        }
        context.onMainThread(() -> {
            final Map<NodeUuid, Long> placed = NetworkStorage.of(level, network).insertBreakdown(payload);
            final List<OperationRecord.MoveRow> moves = new ArrayList<>();
            long stored = 0L;
            for (final Map.Entry<NodeUuid, Long> e : placed.entrySet()) {
                moves.add(new OperationRecord.MoveRow("import", e.getValue(), "SRV-" + shortId(e.getKey().asString())));
                stored += e.getValue();
            }
            final long leftover = requested - stored;
            if (level.getBlockEntity(cablePos) instanceof DataCableBlockEntity cable
                    && cable.getPart(face) instanceof ImportBusPart bus) {
                bus.onImportComplete(leftover > 0L ? payload.copyWithCount((int) leftover) : ItemStack.EMPTY);
            }
            final byte status = stored == 0L ? OperationRecord.STATUS_FAILED
                    : stored >= requested ? OperationRecord.STATUS_COMPLETED : OperationRecord.STATUS_PARTIAL;
            logToMainframe(OperationRecord.TYPE_INSERT, payload.copyWithCount(1), requested, stored, status, moves);
        });
        return OperationResult.success();
    }

    private void logToMainframe(final byte type, final ItemStack icon, final long requested,
                                final long moved, final byte status, final List<OperationRecord.MoveRow> moves) {
        NetworkSystem.get(level).mainframePositionOf(network).ifPresent(pos -> {
            if (level.getBlockEntity(BlockPos.of(pos)) instanceof MainframeBlockEntity mainframe) {
                mainframe.recordOperation(type, icon, requested, moved, status, moves);
            }
        });
    }

    private static String shortId(final String uuid) {
        return uuid.length() >= 6 ? uuid.substring(0, 6) : uuid;
    }
}
