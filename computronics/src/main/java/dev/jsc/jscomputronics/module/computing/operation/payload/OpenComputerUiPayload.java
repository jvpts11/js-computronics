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
 * Server to client: open the client-only firmware setup screen on the monitor the player just used.
 * The server chooses to send this because the client does not have the host's boot state, and only the
 * firmware setup (no OS) is a client-only screen. The desktop, terminal and network GUIs all open as
 * server-side container menus and never use this payload.
 *
 * @param host         the computer block position
 * @param monitorPos   the monitor the player used
 * @param firmwareKind the {@code FirmwareKind} ordinal to render
 * @param name         the host's display name
 */
public record OpenComputerUiPayload(BlockPos host, BlockPos monitorPos, int firmwareKind,
                                    String name) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<OpenComputerUiPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "open_computer_ui"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenComputerUiPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, OpenComputerUiPayload::host,
                    BlockPos.STREAM_CODEC, OpenComputerUiPayload::monitorPos,
                    ByteBufCodecs.VAR_INT, OpenComputerUiPayload::firmwareKind,
                    ByteBufCodecs.STRING_UTF8, OpenComputerUiPayload::name,
                    OpenComputerUiPayload::new);

    @Override
    public CustomPacketPayload.Type<OpenComputerUiPayload> type() {
        return TYPE;
    }
}
