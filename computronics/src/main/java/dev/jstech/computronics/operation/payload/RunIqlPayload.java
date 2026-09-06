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
 * Client to server: an IQL statement typed into the Network Management Studio for the computer at
 * {@code hostPos}, to be parsed and run against the network. The server replies with an
 * {@link IqlResultPayload}.
 */
public record RunIqlPayload(BlockPos monitorPos, BlockPos hostPos, String statement)
        implements CustomPacketPayload {

    public static final int MAX_LEN = 512;

    public static final CustomPacketPayload.Type<RunIqlPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "run_iql"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RunIqlPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RunIqlPayload::monitorPos,
                    BlockPos.STREAM_CODEC, RunIqlPayload::hostPos,
                    ByteBufCodecs.stringUtf8(MAX_LEN), RunIqlPayload::statement,
                    RunIqlPayload::new);

    @Override
    public CustomPacketPayload.Type<RunIqlPayload> type() {
        return TYPE;
    }
}
