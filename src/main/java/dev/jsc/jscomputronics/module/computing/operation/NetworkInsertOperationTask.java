/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation;

import dev.jsc.jscomputronics.common.operation.OperationContext;
import dev.jsc.jscomputronics.common.operation.OperationResult;
import dev.jsc.jscomputronics.common.operation.OperationTask;
import dev.jsc.jscomputronics.common.uuid.NetworkUuid;
import dev.jsc.jscomputronics.module.computing.operation.payload.ComputingPayloads;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * An INSERT Operation dispatched by the network's Mainframe: it deposits a stack (already taken off the requesting player's cursor and carried "in flight") into the network's Server storage.
 */
public final class NetworkInsertOperationTask implements OperationTask {

    private final ServerLevel level;
    private final NetworkUuid network;
    private final ItemStack payload;
    @Nullable
    private final ServerPlayer player;

    public NetworkInsertOperationTask(final ServerLevel level, final NetworkUuid network,
                                      final ItemStack payload, @Nullable final ServerPlayer player) {
        this.level = level;
        this.network = network;
        this.payload = payload;
        this.player = player;
    }

    @Override
    public OperationResult run(final OperationContext context) {
        context.onMainThread(() -> {
            final int stored = NetworkStorage.of(level, network).insert(payload);
            final int leftover = payload.getCount() - stored;
            if (leftover > 0 && player != null) {
                player.getInventory().placeItemBackInInventory(payload.copyWithCount(leftover));
            }
            ComputingPayloads.sendSnapshot(player, level, network);
        });
        return OperationResult.success();
    }
}
