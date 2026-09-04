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
 * Client to server: Remote Control asks for something. {@code action} is either a request for the
 * reachable-host list or a take-over of the machine at {@code targetPos} — the graphical route to a
 * headless server, for players who would rather not live in a shell.
 */
public record RemoteControlPayload(BlockPos hostPos, BlockPos monitorPos, long targetPos, int action)
        implements CustomPacketPayload {

    /** Asks the server for the machines this computer can take over. */
    public static final int ACTION_LIST = 0;
    /** Opens the machine at {@code targetPos} on this monitor, as if sitting at it. */
    public static final int ACTION_CONNECT = 1;

    public static final CustomPacketPayload.Type<RemoteControlPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "remote_control"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteControlPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RemoteControlPayload::hostPos,
                    BlockPos.STREAM_CODEC, RemoteControlPayload::monitorPos,
                    ByteBufCodecs.VAR_LONG, RemoteControlPayload::targetPos,
                    ByteBufCodecs.VAR_INT, RemoteControlPayload::action,
                    RemoteControlPayload::new);

    @Override
    public CustomPacketPayload.Type<RemoteControlPayload> type() {
        return TYPE;
    }
}
