/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation;

import dev.jstech.computronics.operation.payload.ComputingPayloads;
import dev.jstech.core.operation.OperationContext;
import dev.jstech.core.operation.OperationResult;
import dev.jstech.core.operation.OperationTask;
import dev.jstech.core.uuid.NetworkUuid;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * A QUERY Operation dispatched by the network's Mainframe: it reads the network's aggregate item view and pushes a fresh snapshot to the requesting player's Network tab.
 */
public final class NetworkQueryOperationTask implements OperationTask {

    private final ServerLevel level;
    private final NetworkUuid network;
    @Nullable
    private final ServerPlayer player;

    public NetworkQueryOperationTask(final ServerLevel level, final NetworkUuid network,
                                     @Nullable final ServerPlayer player) {
        this.level = level;
        this.network = network;
        this.player = player;
    }

    @Override
    public OperationResult run(final OperationContext context) {
        if (player == null) {
            return OperationResult.success();
        }
        context.onMainThread(() -> ComputingPayloads.sendSnapshot(player, level, network));
        return OperationResult.success();
    }
}
