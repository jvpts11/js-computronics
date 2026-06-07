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
import dev.jsc.jscomputronics.module.computing.block.part.ExportBusPart;
import dev.jsc.jscomputronics.module.computing.blockentity.DataCableBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A DELETE dispatched by an Export Bus: it pulls up to {@code amount} of the filtered item out of the network and into the inventory the bus faces, recording which Servers it came from for provenance.
 */
public final class NetworkExportOperationTask implements OperationTask {

    private final ServerLevel level;
    private final NetworkUuid network;
    private final Item item;
    private final long amount;
    private final BlockPos cablePos;
    private final Direction face;

    public NetworkExportOperationTask(final ServerLevel level, final NetworkUuid network,
                                      final Item item, final long amount, final BlockPos cablePos,
                                      final Direction face) {
        this.level = level;
        this.network = network;
        this.item = item;
        this.amount = amount;
        this.cablePos = cablePos.immutable();
        this.face = face;
    }

    @Override
    public OperationResult run(final OperationContext context) {
        if (amount <= 0) {
            return OperationResult.success();
        }
        context.onMainThread(() -> {
            long moved = 0L;
            final List<OperationRecord.MoveRow> moves = new ArrayList<>();
            if (level.getBlockEntity(cablePos) instanceof DataCableBlockEntity cable
                    && cable.getPart(face) instanceof ExportBusPart bus) {
                final IItemHandler dest = cable.neighborHandler(face);
                if (dest != null) {
                    final Map<NodeUuid, Long> pulled = NetworkStorage.of(level, network)
                            .selectBreakdown(item, amount, dest, null);
                    for (final Map.Entry<NodeUuid, Long> e : pulled.entrySet()) {
                        moves.add(new OperationRecord.MoveRow("SRV-" + shortId(e.getKey().asString()),
                                e.getValue(), "export"));
                        moved += e.getValue();
                    }
                }
                bus.onExportComplete();
            }
            final byte status = moved == 0L ? OperationRecord.STATUS_FAILED
                    : moved >= amount ? OperationRecord.STATUS_COMPLETED : OperationRecord.STATUS_PARTIAL;
            logToMainframe(OperationRecord.TYPE_DELETE, new ItemStack(item), amount, moved, status, moves);
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
