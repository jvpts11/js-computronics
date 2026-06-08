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
 * Client to server: a Storage tab asked to DEPOSIT items into the computer's local storage.
 */
public record TerminalLocalDepositPayload(BlockPos monitorPos, BlockPos hostPos, int slotIndex)
        implements CustomPacketPayload {

    public static final int CURSOR = -1;

    public static final int CURSOR_ONE = -2;

    public static final CustomPacketPayload.Type<TerminalLocalDepositPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "terminal_local_deposit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalLocalDepositPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, TerminalLocalDepositPayload::monitorPos,
                    BlockPos.STREAM_CODEC, TerminalLocalDepositPayload::hostPos,
                    ByteBufCodecs.VAR_INT, TerminalLocalDepositPayload::slotIndex,
                    TerminalLocalDepositPayload::new);

    @Override
    public CustomPacketPayload.Type<TerminalLocalDepositPayload> type() {
        return TYPE;
    }
}
