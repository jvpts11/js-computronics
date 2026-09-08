/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client to server: a task manager stops an Operation in flight. The server answers with a fresh
 * {@link ActiveOperationsPayload} and {@link OperationsLogPayload}, where the Operation now reads DISCARDED.
 *
 * @param host        the computer the desktop is bound to
 * @param monitorPos  the monitor used, validated against the player's reach
 * @param operationId the live Operation, as reported by {@link OperationRecord#id()}
 */
public record CancelOperationPayload(BlockPos host, BlockPos monitorPos, UUID operationId)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CancelOperationPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "cancel_operation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CancelOperationPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CancelOperationPayload::host,
                    BlockPos.STREAM_CODEC, CancelOperationPayload::monitorPos,
                    UUIDUtil.STREAM_CODEC, CancelOperationPayload::operationId,
                    CancelOperationPayload::new);

    @Override
    public CustomPacketPayload.Type<CancelOperationPayload> type() {
        return TYPE;
    }
}
