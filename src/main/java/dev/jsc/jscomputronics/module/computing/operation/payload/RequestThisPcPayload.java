/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.operation.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the "This PC" app wants the disks installed in the computer at {@code hostPos},
 * the removable media in its linked drives, and the set of installed programs. The server replies
 * with a {@link ThisPcPayload}.
 */
public record RequestThisPcPayload(BlockPos hostPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestThisPcPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "request_this_pc"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestThisPcPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestThisPcPayload::hostPos,
                    RequestThisPcPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestThisPcPayload> type() {
        return TYPE;
    }
}
