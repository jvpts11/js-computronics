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
 * Client to server: a Monitor terminal asked to INSERT items into the network.
 */
public record TerminalInsertPayload(BlockPos monitorPos, BlockPos hostPos, int slotIndex)
        implements CustomPacketPayload {

    public static final int CURSOR = -1;

    public static final int CURSOR_ONE = -2;

    public static final CustomPacketPayload.Type<TerminalInsertPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "terminal_insert"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalInsertPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TerminalInsertPayload::monitorPos,
                    BlockPos.STREAM_CODEC, TerminalInsertPayload::hostPos,
                    ByteBufCodecs.VAR_INT, TerminalInsertPayload::slotIndex,
                    TerminalInsertPayload::new);

    @Override
    public CustomPacketPayload.Type<TerminalInsertPayload> type() {
        return TYPE;
    }
}
