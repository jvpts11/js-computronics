/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: live snapshot records of the network's in-flight Operations, pushed while a terminal is open so the Task Manager can show each one streaming with a progress bar (and the SubOperation popup can read its per-server moves).
 */
public record ActiveOperationsPayload(List<OperationRecord> operations) implements CustomPacketPayload {

    public static final int MAX = 64;

    public static final CustomPacketPayload.Type<ActiveOperationsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "active_operations"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ActiveOperationsPayload> STREAM_CODEC =
            OperationRecord.STREAM_CODEC.apply(ByteBufCodecs.list(MAX))
                    .map(ActiveOperationsPayload::new, ActiveOperationsPayload::operations);

    @Override
    public CustomPacketPayload.Type<ActiveOperationsPayload> type() {
        return TYPE;
    }
}
