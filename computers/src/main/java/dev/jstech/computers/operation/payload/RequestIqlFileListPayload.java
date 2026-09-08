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
 * Client to server: request the list of {@code .iql} files on the Mainframe's system disk (or the
 * Mainframe reachable through the host PC). The server replies with an {@link IqlFileListPayload}.
 *
 * <p>Sent when the NMS player opens the File menu's Open submenu or clicks the refresh button in
 * the file picker dialog.
 *
 * @param hostPos the position of the host computer (Mainframe or PC on the same network)
 */
public record RequestIqlFileListPayload(BlockPos hostPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestIqlFileListPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "request_iql_file_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestIqlFileListPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestIqlFileListPayload::hostPos,
                    RequestIqlFileListPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestIqlFileListPayload> type() {
        return TYPE;
    }
}
