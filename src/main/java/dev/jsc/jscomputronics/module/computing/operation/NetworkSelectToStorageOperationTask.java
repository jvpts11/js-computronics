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
import dev.jsc.jscomputronics.module.computing.terminal.ComputerTerminalHost;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A SELECT dispatched from a Monitor terminal: it pulls up to {@code quantity} of {@code item} out of the network and lands it in the requesting computer's local storage.
 */
public final class NetworkSelectToStorageOperationTask implements OperationTask {

    private static final double INVENTORY_REACH = 10.0;

    private final ServerLevel level;
    private final NetworkUuid network;
    private final Item item;
    private final long quantity;
    private final BlockPos hostPos;
    @Nullable
    private final ServerPlayer player;
    @Nullable
    private final Set<NodeUuid> sources;

    public NetworkSelectToStorageOperationTask(final ServerLevel level, final NetworkUuid network,
                                               final Item item, final long quantity, final BlockPos hostPos,
                                               @Nullable final ServerPlayer player,
                                               @Nullable final Set<NodeUuid> sources) {
        this.level = level;
        this.network = network;
        this.item = item;
        this.quantity = quantity;
        this.hostPos = hostPos.immutable();
        this.player = player;
        this.sources = sources;
    }

    @Override
    public OperationResult run(final OperationContext context) {
        if (quantity <= 0) {
            return OperationResult.success();
        }
        context.onMainThread(() -> {
            final Dest dest = resolveDestination();
            final List<OperationRecord.MoveRow> moves = new ArrayList<>();
            long moved = 0L;
            if (dest != null) {
                final Map<NodeUuid, Long> pulled = NetworkStorage.of(level, network)
                        .selectBreakdown(item, quantity, dest.handler(), sources);
                for (final Map.Entry<NodeUuid, Long> e : pulled.entrySet()) {
                    moves.add(new OperationRecord.MoveRow(
                            "SRV-" + shortId(e.getKey().asString()), e.getValue(), dest.label()));
                    moved += e.getValue();
                }
            }
            final byte status = moved == 0L ? OperationRecord.STATUS_FAILED
                    : moved >= quantity ? OperationRecord.STATUS_COMPLETED : OperationRecord.STATUS_PARTIAL;
            logToMainframe(OperationRecord.TYPE_SELECT, new ItemStack(item), quantity, moved, status, moves);
            ComputingPayloads.sendSnapshot(player, level, network);
        });
        return OperationResult.success();
    }

    /**
     * A resolved destination: where the SELECT lands, plus its provenance label.
     */
    private record Dest(IItemHandler handler, String label) {
    }

    @Nullable
    private Dest resolveDestination() {
        if (level.getBlockEntity(hostPos) instanceof ComputerTerminalHost host
                && host.usableStorageSlots() > 0) {
            return new Dest(host.localStorage(), "storage");
        }
        if (player != null
                && player.distanceToSqr(hostPos.getX() + 0.5, hostPos.getY() + 0.5, hostPos.getZ() + 0.5)
                <= INVENTORY_REACH * INVENTORY_REACH) {
            return new Dest(new PlayerMainInvWrapper(player.getInventory()), "inventory");
        }
        return null;
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
