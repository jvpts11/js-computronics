/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the network's recent Operations log (newest first), sent when a Monitor terminal opens the Operations tab so it can list them with provenance.
 */
public record OperationsLogPayload(List<OperationRecord> operations) implements CustomPacketPayload {

    public static final int MAX = 32;

    public static final CustomPacketPayload.Type<OperationsLogPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "operations_log"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OperationsLogPayload> STREAM_CODEC =
            OperationRecord.STREAM_CODEC.apply(ByteBufCodecs.list(MAX))
                    .map(OperationsLogPayload::new, OperationsLogPayload::operations);

    @Override
    public CustomPacketPayload.Type<OperationsLogPayload> type() {
        return TYPE;
    }
}
