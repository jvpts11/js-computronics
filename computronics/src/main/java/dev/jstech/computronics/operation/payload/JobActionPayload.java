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
 * Client to server: pause, resume, or delete a saved job by name from the Automation Manager.
 */
public record JobActionPayload(BlockPos host, BlockPos monitorPos, String name, int action)
        implements CustomPacketPayload {

    public static final int ACTION_PAUSE = 0;
    public static final int ACTION_RESUME = 1;
    public static final int ACTION_DELETE = 2;

    public static final CustomPacketPayload.Type<JobActionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "job_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JobActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, JobActionPayload::host,
                    BlockPos.STREAM_CODEC, JobActionPayload::monitorPos,
                    ByteBufCodecs.stringUtf8(64), JobActionPayload::name,
                    ByteBufCodecs.VAR_INT, JobActionPayload::action,
                    JobActionPayload::new);

    @Override
    public CustomPacketPayload.Type<JobActionPayload> type() {
        return TYPE;
    }
}
