/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: a terminal window on the desktop ran a command {@code line} against the computer
 * at {@code hostPos}. The server executes it through the same CLI as the Command Prompt and replies
 * with a {@link DesktopShellOutputPayload} to the same {@code session}.
 *
 * <p>The session is the window's own number: two terminals open on one desktop are two shells, each
 * with its own directory and its own replies, the way two terminal windows on a real machine are.
 * Zero is a caller with no window of its own.
 */
public record DesktopShellRunPayload(BlockPos hostPos, String line, int session) implements CustomPacketPayload {

    public static final int MAX_LEN = 512;

    /** A line from something that is not a terminal window, such as the Network Interactor's box. */
    public DesktopShellRunPayload(final BlockPos hostPos, final String line) {
        this(hostPos, line, 0);
    }

    public static final CustomPacketPayload.Type<DesktopShellRunPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "desktop_shell_run"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DesktopShellRunPayload> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, DesktopShellRunPayload::hostPos,
                    ByteBufCodecs.stringUtf8(MAX_LEN), DesktopShellRunPayload::line,
                    ByteBufCodecs.VAR_INT, DesktopShellRunPayload::session,
                    DesktopShellRunPayload::new);

    @Override
    public CustomPacketPayload.Type<DesktopShellRunPayload> type() {
        return TYPE;
    }
}
