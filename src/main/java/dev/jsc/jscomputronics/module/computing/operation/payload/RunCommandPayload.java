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
 * Client to server: a command line typed into the Command Prompt running against the computer at {@code hostPos}, opened from the Monitor at {@code monitorPos}. The server runs it through the shell and replies with a {@link CommandOutputPayload}.
 */
public record RunCommandPayload(BlockPos monitorPos, BlockPos hostPos, String line)
        implements CustomPacketPayload {

    public static final int MAX_LEN = 256;

    public static final CustomPacketPayload.Type<RunCommandPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "run_command"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RunCommandPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RunCommandPayload::monitorPos,
                    BlockPos.STREAM_CODEC, RunCommandPayload::hostPos,
                    ByteBufCodecs.stringUtf8(MAX_LEN), RunCommandPayload::line,
                    RunCommandPayload::new);

    @Override
    public CustomPacketPayload.Type<RunCommandPayload> type() {
        return TYPE;
    }
}
