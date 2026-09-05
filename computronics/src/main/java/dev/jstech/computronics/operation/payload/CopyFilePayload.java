/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: copy the file at {@code src} into the directory {@code destDir} on the computer at
 * {@code hostPos}, on the system disk or across to a removable medium. The source stays where it is.
 * A copy that would land on an existing name gets a numbered name instead of overwriting it. A
 * projected file (a {@code .dat} or an installer's files) has no bytes to copy and is refused.
 */
public record CopyFilePayload(BlockPos hostPos, String src, String destDir) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CopyFilePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "copy_file"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CopyFilePayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, CopyFilePayload::hostPos,
                    ByteBufCodecs.stringUtf8(160), CopyFilePayload::src,
                    ByteBufCodecs.stringUtf8(160), CopyFilePayload::destDir,
                    CopyFilePayload::new);

    @Override
    public CustomPacketPayload.Type<CopyFilePayload> type() {
        return TYPE;
    }
}
