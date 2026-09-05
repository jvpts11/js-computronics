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
 * Client to server: the desktop Shell window ran a command {@code line} against the computer at
 * {@code hostPos}. The server executes it through the same CLI as the Command Prompt and replies with a
 * {@link DesktopShellOutputPayload}.
 */
public record DesktopShellRunPayload(BlockPos hostPos, String line) implements CustomPacketPayload {

    public static final int MAX_LEN = 512;

    public static final CustomPacketPayload.Type<DesktopShellRunPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "desktop_shell_run"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DesktopShellRunPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DesktopShellRunPayload::hostPos,
                    ByteBufCodecs.stringUtf8(MAX_LEN), DesktopShellRunPayload::line,
                    DesktopShellRunPayload::new);

    @Override
    public CustomPacketPayload.Type<DesktopShellRunPayload> type() {
        return TYPE;
    }
}
