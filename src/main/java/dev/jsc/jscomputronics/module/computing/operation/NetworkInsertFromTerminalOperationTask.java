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
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.operation.payload.ComputingPayloads;
import dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * An INSERT dispatched from a Monitor terminal: it pushes a stack (already taken off the player's cursor or an inventory slot and carried "in flight") into the network's Server storage, recording which Servers accepted it for provenance.
 */
public final class NetworkInsertFromTerminalOperationTask implements OperationTask {

    private final ServerLevel level;
    private final NetworkUuid network;
    private final ItemStack payload;
    @Nullable
    private final ServerPlayer player;

    public NetworkInsertFromTerminalOperationTask(final ServerLevel level, final NetworkUuid network,
                                                  final ItemStack payload, @Nullable final ServerPlayer player) {
        this.level = level;
        this.network = network;
        this.payload = payload;
        this.player = player;
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
                moves.add(new OperationRecord.MoveRow(
                        "inventory", e.getValue(), "SRV-" + shortId(e.getKey().asString())));
                stored += e.getValue();
            }
            final long leftover = requested - stored;
            if (leftover > 0L && player != null) {
                player.getInventory().placeItemBackInInventory(payload.copyWithCount((int) leftover));
            }
            final byte status = stored == 0L ? OperationRecord.STATUS_FAILED
                    : stored >= requested ? OperationRecord.STATUS_COMPLETED : OperationRecord.STATUS_PARTIAL;
            logToMainframe(OperationRecord.TYPE_INSERT, payload.copyWithCount(1), requested, stored, status, moves);
            ComputingPayloads.sendSnapshot(player, level, network);
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
