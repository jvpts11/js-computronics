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
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.items.wrapper.PlayerMainInvWrapper;
import org.jetbrains.annotations.Nullable;

/**
 * A SELECT Operation dispatched by the network's Mainframe: it pulls up to {@code quantity} of {@code item} out of the network's Server storage into the requesting player's inventory.
 */
public final class NetworkSelectOperationTask implements OperationTask {

    private final ServerLevel level;
    private final NetworkUuid network;
    private final Item item;
    private final long quantity;
    @Nullable
    private final ServerPlayer player;

    public NetworkSelectOperationTask(final ServerLevel level, final NetworkUuid network,
                                      final Item item, final long quantity, @Nullable final ServerPlayer player) {
        this.level = level;
        this.network = network;
        this.item = item;
        this.quantity = quantity;
        this.player = player;
    }

    @Override
    public OperationResult run(final OperationContext context) {
        if (player == null || quantity <= 0) {
            return OperationResult.success();
        }
        context.onMainThread(() -> {
            NetworkStorage.of(level, network).select(item, quantity, new PlayerMainInvWrapper(player.getInventory()));
            ComputingPayloads.sendSnapshot(player, level, network);
        });
        return OperationResult.success();
    }
}
