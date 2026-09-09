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
 * What the machine's shell said back to a terminal window on a desktop.
 *
 * <p>A terminal window is a session of its own: the reply to a line typed in one carries that
 * window's session number, so a second terminal on the same desktop does not see it. A session of
 * zero is nobody's in particular, which is how the lines a machine prints on its own (a program's
 * output, a setup's progress) reach every terminal looking at it.
 *
 * <p>{@code replaceLast} says the lines redraw over the last one printed rather than follow it, the way
 * a progress bar at a real terminal grows on the same line instead of filling the screen with copies.
 * {@code informational} says the lines are all there is to it: they neither give the prompt back nor
 * take it away, so a bar drawn while a program has the terminal leaves the program in front.
 */
public record DesktopShellOutputPayload(boolean clear, boolean busy, String prompt, List<WireLine> lines,
                                        String editor, String editorPath, int session, boolean replaceLast,
                                        boolean informational)
        implements CustomPacketPayload {

    public static final int MAX_LINES = 256;

    /** A reply that only printed, which is what nearly every command does. */
    public DesktopShellOutputPayload(final boolean clear, final boolean busy, final String prompt,
                                     final List<WireLine> lines) {
        this(clear, busy, prompt, lines, "", "", 0, false, false);
    }

    /** A reply that only printed, to the session that asked. */
    public DesktopShellOutputPayload(final boolean clear, final boolean busy, final String prompt,
                                     final List<WireLine> lines, final int session) {
        this(clear, busy, prompt, lines, "", "", session, false, false);
    }

    /** A reply that hands the terminal to an editor, to the session that asked. */
    public DesktopShellOutputPayload(final boolean clear, final boolean busy, final String prompt,
                                     final List<WireLine> lines, final String editor, final String editorPath,
                                     final int session) {
        this(clear, busy, prompt, lines, editor, editorPath, session, false, false);
    }

    /** A reply that hands the terminal to an editor, for every window. */
    public DesktopShellOutputPayload(final boolean clear, final boolean busy, final String prompt,
                                     final List<WireLine> lines, final String editor, final String editorPath) {
        this(clear, busy, prompt, lines, editor, editorPath, 0, false, false);
    }

    /** Lines the machine prints on its own, for every terminal, drawn over the last line or after it. */
    public static DesktopShellOutputPayload informational(final List<WireLine> lines, final boolean replaceLast) {
        return new DesktopShellOutputPayload(false, false, "", lines, "", "", 0, replaceLast, true);
    }

    /** Whether the machine gave the terminal to an editor. */
    public boolean handsOver() {
        return !this.editor.isEmpty() && !this.editorPath.isEmpty();
    }

    public static final CustomPacketPayload.Type<DesktopShellOutputPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath("jsc", "desktop_shell_output"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<WireLine>> LINES_CODEC =
            WireLine.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_LINES));

    /* Nine fields is past what the composite form takes, so the two halves are written by hand. */
    public static final StreamCodec<RegistryFriendlyByteBuf, DesktopShellOutputPayload> STREAM_CODEC =
            StreamCodec.of(DesktopShellOutputPayload::write, DesktopShellOutputPayload::read);

    private static void write(final RegistryFriendlyByteBuf buf, final DesktopShellOutputPayload payload) {
        ByteBufCodecs.BOOL.encode(buf, payload.clear());
        ByteBufCodecs.BOOL.encode(buf, payload.busy());
        ByteBufCodecs.stringUtf8(256).encode(buf, payload.prompt());
        LINES_CODEC.encode(buf, payload.lines());
        ByteBufCodecs.stringUtf8(32).encode(buf, payload.editor());
        ByteBufCodecs.stringUtf8(160).encode(buf, payload.editorPath());
        ByteBufCodecs.VAR_INT.encode(buf, payload.session());
        ByteBufCodecs.BOOL.encode(buf, payload.replaceLast());
        ByteBufCodecs.BOOL.encode(buf, payload.informational());
    }

    private static DesktopShellOutputPayload read(final RegistryFriendlyByteBuf buf) {
        final boolean clear = ByteBufCodecs.BOOL.decode(buf);
        final boolean busy = ByteBufCodecs.BOOL.decode(buf);
        final String prompt = ByteBufCodecs.stringUtf8(256).decode(buf);
        final List<WireLine> lines = LINES_CODEC.decode(buf);
        final String editor = ByteBufCodecs.stringUtf8(32).decode(buf);
        final String editorPath = ByteBufCodecs.stringUtf8(160).decode(buf);
        final int session = ByteBufCodecs.VAR_INT.decode(buf);
        final boolean replaceLast = ByteBufCodecs.BOOL.decode(buf);
        final boolean informational = ByteBufCodecs.BOOL.decode(buf);
        return new DesktopShellOutputPayload(clear, busy, prompt, lines, editor, editorPath, session, replaceLast,
                informational);
    }

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
