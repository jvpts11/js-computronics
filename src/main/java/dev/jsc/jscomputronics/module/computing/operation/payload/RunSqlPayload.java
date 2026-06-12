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
 * Client to server: a statement typed into the Network Management Studio for the computer at {@code hostPos}, to be parsed in the configured dialect and run against the network. The server replies with a {@link SqlResultPayload}.
 */
public record RunSqlPayload(BlockPos monitorPos, BlockPos hostPos, String sql) implements CustomPacketPayload {

    public static final int MAX_LEN = 512;

    public static final CustomPacketPayload.Type<RunSqlPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "run_sql"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RunSqlPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RunSqlPayload::monitorPos,
                    BlockPos.STREAM_CODEC, RunSqlPayload::hostPos,
                    ByteBufCodecs.stringUtf8(MAX_LEN), RunSqlPayload::sql,
                    RunSqlPayload::new);

    @Override
    public CustomPacketPayload.Type<RunSqlPayload> type() {
        return TYPE;
    }
}
