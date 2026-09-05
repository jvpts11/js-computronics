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
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the Files app on a desktop wants the listing of directory {@code dir} on the
 * system disk of the computer at {@code hostPos}. The server replies with a {@link DiskFilesPayload}.
 */
public record RequestDiskFilesPayload(BlockPos hostPos, String dir) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RequestDiskFilesPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "request_disk_files"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RequestDiskFilesPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RequestDiskFilesPayload::hostPos,
                    ByteBufCodecs.stringUtf8(128), RequestDiskFilesPayload::dir,
                    RequestDiskFilesPayload::new);

    @Override
    public CustomPacketPayload.Type<RequestDiskFilesPayload> type() {
        return TYPE;
    }
}
