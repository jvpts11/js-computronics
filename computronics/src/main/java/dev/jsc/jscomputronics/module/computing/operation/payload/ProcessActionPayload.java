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
 * Client to server: a task-manager action on one process of the host computer, addressed by the host's
 * position. For the service kind: Start / Stop / Restart the IQL Engine; for the job kind: End (pause,
 * resumable) / Restart (re-arm the trigger) the named job.
 */
public record ProcessActionPayload(BlockPos hostPos, int kind, String name, int action)
        implements CustomPacketPayload {

    public static final int ACTION_START = 0;
    public static final int ACTION_STOP = 1;
    public static final int ACTION_RESTART = 2;
    public static final int ACTION_END = 3;

    public static final CustomPacketPayload.Type<ProcessActionPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "process_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ProcessActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ProcessActionPayload::hostPos,
                    ByteBufCodecs.VAR_INT, ProcessActionPayload::kind,
                    ByteBufCodecs.stringUtf8(48), ProcessActionPayload::name,
                    ByteBufCodecs.VAR_INT, ProcessActionPayload::action,
                    ProcessActionPayload::new);

    @Override
    public CustomPacketPayload.Type<ProcessActionPayload> type() {
        return TYPE;
    }
}
