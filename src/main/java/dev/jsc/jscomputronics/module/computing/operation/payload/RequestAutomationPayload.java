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
 * Client to server: the Automation Manager asks for the engine status and the saved job list. The server
 * replies with an {@link AutomationPayload}. Proximity + monitor-link gated like the Network Interactor.
 */
public record RequestAutomationPayload(BlockPos host, BlockPos monitorPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestAutomationPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "request_automation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestAutomationPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestAutomationPayload::host,
                    BlockPos.STREAM_CODEC, RequestAutomationPayload::monitorPos,
                    RequestAutomationPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestAutomationPayload> type() {
        return TYPE;
    }
}
