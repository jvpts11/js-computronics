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
 * Client to server: the name typed in a Personal Computer's assembly GUI.
 */
public record RenamePcPayload(BlockPos pcPos, String name) implements CustomPacketPayload {

    public static final int MAX_LEN = 32;

    public static final CustomPacketPayload.Type<RenamePcPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "rename_pc"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RenamePcPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RenamePcPayload::pcPos,
                    ByteBufCodecs.stringUtf8(MAX_LEN), RenamePcPayload::name,
                    RenamePcPayload::new);

    @Override
    public CustomPacketPayload.Type<RenamePcPayload> type() {
        return TYPE;
    }
}
