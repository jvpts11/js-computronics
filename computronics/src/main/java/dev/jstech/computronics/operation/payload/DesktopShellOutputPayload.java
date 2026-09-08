/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the styled output of a command the desktop Shell ran. {@code clear} asks the shell
 * to wipe its scrollback first (the {@code cls} command); each {@link WireLine} is a text line plus the
 * ordinal of its CLI style for colouring.
 *
 * <p>{@code busy} says a program has the terminal: nothing can be typed until it returns, what it
 * prints keeps arriving, and the shell asks again until it is told otherwise.
 */
public record DesktopShellOutputPayload(boolean clear, boolean busy, String prompt, List<WireLine> lines,
                                        String editor, String editorPath) implements CustomPacketPayload {

    public static final int MAX_LINES = 256;

    /** A reply that only printed, which is what nearly every command does. */
    public DesktopShellOutputPayload(final boolean clear, final boolean busy, final String prompt,
                                     final List<WireLine> lines) {
        this(clear, busy, prompt, lines, "", "");
    }

    /** Whether the machine gave the terminal to an editor. */
    public boolean handsOver() {
        return !this.editor.isEmpty() && !this.editorPath.isEmpty();
    }

    public static final CustomPacketPayload.Type<DesktopShellOutputPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "desktop_shell_output"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DesktopShellOutputPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, DesktopShellOutputPayload::clear,
                    ByteBufCodecs.BOOL, DesktopShellOutputPayload::busy,
                    ByteBufCodecs.stringUtf8(256), DesktopShellOutputPayload::prompt,
                    WireLine.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)), DesktopShellOutputPayload::lines,
                    ByteBufCodecs.stringUtf8(32), DesktopShellOutputPayload::editor,
                    ByteBufCodecs.stringUtf8(160), DesktopShellOutputPayload::editorPath,
                    DesktopShellOutputPayload::new);

    @Override
    public CustomPacketPayload.Type<DesktopShellOutputPayload> type() {
        return TYPE;
    }

    /** One output line: its text and the ordinal of its {@code CliStyle} for colouring. */
    public record WireLine(String text, int style) {

        public static final StreamCodec<RegistryFriendlyByteBuf, WireLine> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(512), WireLine::text,
                        ByteBufCodecs.VAR_INT, WireLine::style,
                        WireLine::new);
    }
}
