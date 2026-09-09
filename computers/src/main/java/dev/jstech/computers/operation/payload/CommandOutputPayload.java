/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.operation.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server to client: the styled output of one Command Prompt line, plus whether the console should be
 * cleared before printing it (the {@code clear} command). Each line carries its text and a style
 * ordinal the client maps to a colour.
 *
 * <p>{@code replaceLast} says the lines redraw over the last one printed rather than follow it, the way
 * a progress bar at a real terminal grows on the same line instead of filling the screen with copies.
 */
public record CommandOutputPayload(boolean clear, String prompt, List<WireLine> lines,
                                   String editor, String editorPath, boolean replaceLast)
        implements CustomPacketPayload {

    public static final int MAX_LINES = 256;

    /** A reply that only printed, which is what nearly every command does. */
    public CommandOutputPayload(final boolean clear, final String prompt, final List<WireLine> lines) {
        this(clear, prompt, lines, "", "", false);
    }

    /** A reply that hands the terminal to an editor. */
    public CommandOutputPayload(final boolean clear, final String prompt, final List<WireLine> lines,
                                final String editor, final String editorPath) {
        this(clear, prompt, lines, editor, editorPath, false);
    }

    /** Whether the machine gave the terminal to an editor. */
    public boolean handsOver() {
        return !this.editor.isEmpty() && !this.editorPath.isEmpty();
    }

    public static final CustomPacketPayload.Type<CommandOutputPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "command_output"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CommandOutputPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, CommandOutputPayload::clear,
                    ByteBufCodecs.stringUtf8(256), CommandOutputPayload::prompt,
                    WireLine.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES)), CommandOutputPayload::lines,
                    ByteBufCodecs.stringUtf8(32), CommandOutputPayload::editor,
                    ByteBufCodecs.stringUtf8(160), CommandOutputPayload::editorPath,
                    ByteBufCodecs.BOOL, CommandOutputPayload::replaceLast,
                    CommandOutputPayload::new);

    @Override
    public CustomPacketPayload.Type<CommandOutputPayload> type() {
        return TYPE;
    }

    /** One console line on the wire: its text and the ordinal of its {@code CliStyle}. */
    public record WireLine(String text, int style) {

        public static final StreamCodec<RegistryFriendlyByteBuf, WireLine> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.stringUtf8(512), WireLine::text,
                        ByteBufCodecs.VAR_INT, WireLine::style,
                        WireLine::new);
    }
}
