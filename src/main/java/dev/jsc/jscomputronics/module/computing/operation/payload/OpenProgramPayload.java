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
 * Client to server: launch a program for the computer at {@code hostPos} from the Monitor at {@code monitorPos} — used by the terminal's Console tab to open the Command Prompt without sneaking.
 */
public record OpenProgramPayload(BlockPos monitorPos, BlockPos hostPos, String programId)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenProgramPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "open_program"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenProgramPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, OpenProgramPayload::monitorPos,
                    BlockPos.STREAM_CODEC, OpenProgramPayload::hostPos,
                    ByteBufCodecs.stringUtf8(64), OpenProgramPayload::programId,
                    OpenProgramPayload::new);

    @Override
    public CustomPacketPayload.Type<OpenProgramPayload> type() {
        return TYPE;
    }
}
