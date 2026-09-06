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
 * Client to server: the Files explorer (or the desktop) is creating a new directory at {@code path}
 * on the system disk of the computer at {@code hostPos}. Only a hierarchical filesystem (a desktop
 * OS) supports real folders; the server rejects the request otherwise.
 */
public record MkdirPayload(BlockPos hostPos, String path) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MkdirPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "mkdir"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MkdirPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, MkdirPayload::hostPos,
                    ByteBufCodecs.stringUtf8(160), MkdirPayload::path,
                    MkdirPayload::new);

    @Override
    public CustomPacketPayload.Type<MkdirPayload> type() {
        return TYPE;
    }
}
