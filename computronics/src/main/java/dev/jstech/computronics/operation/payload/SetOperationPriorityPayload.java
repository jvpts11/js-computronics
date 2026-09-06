/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import dev.jstech.core.operation.OperationPriority;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client to server: a task manager changes the scheduling level of an Operation in flight. The server answers
 * with a fresh {@link ActiveOperationsPayload} so the view shows the new level and the slot it now holds.
 *
 * @param host        the computer the desktop is bound to
 * @param monitorPos  the monitor used, validated against the player's reach
 * @param operationId the live Operation, as reported by {@link OperationRecord#id()}
 * @param priority    the level to schedule it at from the next tick on
 */
public record SetOperationPriorityPayload(BlockPos host, BlockPos monitorPos, UUID operationId,
                                          OperationPriority priority) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SetOperationPriorityPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "set_operation_priority"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetOperationPriorityPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, SetOperationPriorityPayload::host,
                    BlockPos.STREAM_CODEC, SetOperationPriorityPayload::monitorPos,
                    UUIDUtil.STREAM_CODEC, SetOperationPriorityPayload::operationId,
                    NiGridClickPayload.PRIORITY_CODEC, SetOperationPriorityPayload::priority,
                    SetOperationPriorityPayload::new);

    @Override
    public CustomPacketPayload.Type<SetOperationPriorityPayload> type() {
        return TYPE;
    }
}
