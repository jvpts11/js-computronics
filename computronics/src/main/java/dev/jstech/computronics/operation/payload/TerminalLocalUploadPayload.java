/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import dev.jstech.computronics.storage.StorageKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: UPLOAD up to {@code quantity} of {@code key}'s exact data type (item OR fluid, with components) from the computer's local storage into the network (a timed INSERT routed through the Mainframe).
 */
public record TerminalLocalUploadPayload(BlockPos monitorPos, BlockPos hostPos, StorageKey key, long quantity)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TerminalLocalUploadPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "terminal_local_upload"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalLocalUploadPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TerminalLocalUploadPayload::monitorPos,
                    BlockPos.STREAM_CODEC, TerminalLocalUploadPayload::hostPos,
                    StorageKey.STREAM_CODEC, TerminalLocalUploadPayload::key,
                    ByteBufCodecs.VAR_LONG, TerminalLocalUploadPayload::quantity,
                    TerminalLocalUploadPayload::new);

    @Override
    public CustomPacketPayload.Type<TerminalLocalUploadPayload> type() {
        return TYPE;
    }
}
