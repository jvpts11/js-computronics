/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the Command Prompt for the host at {@code hostPos} opened and wants its persisted history and the command list.
 */
public record RequestConsoleInitPayload(BlockPos hostPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestConsoleInitPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "request_console_init"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestConsoleInitPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestConsoleInitPayload::hostPos,
                    RequestConsoleInitPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestConsoleInitPayload> type() {
        return TYPE;
    }
}
