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
 * Client to server: the Frames desktop wants the listing of its desktop folder
 * ({@code Users/Public/Desktop}) on the system disk of the computer at {@code hostPos}. The server
 * replies with a {@link DesktopFilesPayload}. This is a separate channel from the Files app's
 * {@link RequestDiskFilesPayload} so the two listings do not collide.
 */
public record RequestDesktopFilesPayload(BlockPos hostPos) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestDesktopFilesPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath("jsc", "request_desktop_files"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestDesktopFilesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestDesktopFilesPayload::hostPos,
                    RequestDesktopFilesPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestDesktopFilesPayload> type() {
        return TYPE;
    }
}
