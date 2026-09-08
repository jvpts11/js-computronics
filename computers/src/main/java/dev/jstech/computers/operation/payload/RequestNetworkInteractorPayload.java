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
 * Client to server: the Network Interactor desktop app asks for a fresh network + local storage
 * snapshot for its host computer. The reply is a {@link NetworkInteractorPayload}. This is the
 * desktop equivalent of the terminal's storage sync, but routed to the app rather than a menu.
 *
 * @param host       the computer block the app is bound to
 * @param monitorPos the monitor the app is shown on, used to authenticate proximity + the host link
 */
public record RequestNetworkInteractorPayload(BlockPos host, BlockPos monitorPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestNetworkInteractorPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "request_network_interactor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestNetworkInteractorPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestNetworkInteractorPayload::host,
                    BlockPos.STREAM_CODEC, RequestNetworkInteractorPayload::monitorPos,
                    RequestNetworkInteractorPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestNetworkInteractorPayload> type() {
        return TYPE;
    }
}
