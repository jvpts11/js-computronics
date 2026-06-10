/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the player clicked {@code key} in a Monitor terminal's Network tab and the request popup wants the per-Server breakdown for that exact data type (item or fluid, with components).
 */
public record RequestServerBreakdownPayload(BlockPos monitorPos, BlockPos hostPos, StorageKey key)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestServerBreakdownPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "request_server_breakdown"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestServerBreakdownPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestServerBreakdownPayload::monitorPos,
                    BlockPos.STREAM_CODEC, RequestServerBreakdownPayload::hostPos,
                    StorageKey.STREAM_CODEC, RequestServerBreakdownPayload::key,
                    RequestServerBreakdownPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestServerBreakdownPayload> type() {
        return TYPE;
    }
}
