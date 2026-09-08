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
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: show the power-on self-test on this monitor. The client plays the era-styled POST
 * (memory count, detected drives, "press DEL for setup") and answers with {@link PostCompletePayload} when
 * it finishes or when the player asks for the firmware setup.
 */
public record OpenPostPayload(BlockPos host, BlockPos monitorPos, int firmwareKind,
                              String name) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenPostPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "open_post"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPostPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, OpenPostPayload::host,
                    BlockPos.STREAM_CODEC, OpenPostPayload::monitorPos,
                    ByteBufCodecs.VAR_INT, OpenPostPayload::firmwareKind,
                    ByteBufCodecs.STRING_UTF8, OpenPostPayload::name,
                    OpenPostPayload::new);

    @Override
    public CustomPacketPayload.Type<OpenPostPayload> type() {
        return TYPE;
    }
}
