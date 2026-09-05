/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the Network Interactor's Operations tab asks for the network's recent and active
 * Operations. The server replies with an {@link OperationsLogPayload} and an {@link ActiveOperationsPayload}.
 *
 * @param host       the computer the desktop is bound to
 * @param monitorPos the monitor used, validated against the player's reach
 */
public record RequestNiOperationsPayload(BlockPos host, BlockPos monitorPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestNiOperationsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "request_ni_operations"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestNiOperationsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestNiOperationsPayload::host,
                    BlockPos.STREAM_CODEC, RequestNiOperationsPayload::monitorPos,
                    RequestNiOperationsPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestNiOperationsPayload> type() {
        return TYPE;
    }
}
