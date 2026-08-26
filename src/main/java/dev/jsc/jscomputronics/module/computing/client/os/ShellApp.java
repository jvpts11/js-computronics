/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import dev.jsc.jscomputronics.module.computing.operation.payload.DesktopShellOutputPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.DesktopShellRunPayload;
import dev.jsc.jscomputronics.module.computing.program.cli.CliStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A terminal window for the desktop: the same CLI as the Command Prompt (dir, type, write, del, run,
 * iql, operation, ...) inside a Panes window. This is how a graphical OS, whose monitor never shows the
 * Command Prompt directly, still reaches the filesystem and the network from the keyboard.
 *
 * <p>A typed line is echoed and sent to the server with {@link DesktopShellRunPayload}; the styled reply
 * routes back through {@link #accept}. Input is single-line with Up/Down history, like a real shell.
 */
public final class ShellApp implements DesktopApp {

    private static final int LINE_H = 9;
    private static final int PAD = 3;
    private static final int MAX_SCROLLBACK = 256;

    private final BlockPos host;
    private final Deque<Line> scrollback = new ArrayDeque<>();
    private final StringBuilder input = new StringBuilder();
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private int scrollOffset;
    private OsSkin skin = OsSkin.fallback();

    private static ShellApp active;

    private record Line(String text, int color) {
    }

    public ShellApp(final BlockPos host) {
        this.host = host;
        active = this;
        push("J's Computronics Shell", colorOf(CliStyle.ACCENT.ordinal()));
        push("type a command and press ENTER", colorOf(CliStyle.DIM.ordinal()));
    }

    /** Routes a server output reply to the open Shell window. */
    public static void accept(final DesktopShellOutputPayload payload) {
        if (active == null) {
            return;
        }
        if (payload.clear()) {
            active.scrollback.clear();
        }
        for (final DesktopShellOutputPayload.WireLine line : payload.lines()) {
            active.push(line.text(), colorOf(line.style()));
        }
    }

    private void push(final String text, final int color) {
        scrollback.addLast(new Line(text, color));
        while (scrollback.size() > MAX_SCROLLBACK) {
            scrollback.removeFirst();
        }
    }

    @Override
    public String title() {
        return "Shell";
    }

    @Override
    public int defaultWidth() {
        return 286;
    }

    @Override
    public int defaultHeight() {
        return 176;
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
    }

    /** The console background — kept dark like a real terminal, tinted to the OS (DOS black / XP navy / 11 grey). */
    private int consoleBg() {
        return switch (skin.form()) {
            case BEVEL -> 0xFF000000;
            case LUNA -> 0xFF0A1A30;
            case FLAT -> 0xFF1E1F23;
        };
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        g.fill(x, y, x + width, y + height, consoleBg());

        // No own scissor here: app content is drawn inside the desktop's translate, where enableScissor (which
        // takes absolute coordinates) would clip the wrong region and hide the text. The desktop's own scissor
        // already keeps content on the screen; lines are wrapped server-side to the console width.
        final int inputY = y + height - LINE_H;
        final int visible = Math.max(1, (height - PAD - LINE_H - 2) / LINE_H);
        final List<Line> all = new ArrayList<>(scrollback);
        final int total = all.size();
        final int maxScroll = Math.max(0, total - visible);
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
        final int end = total - scrollOffset;
        final int start = Math.max(0, end - visible);
        int row = y + PAD;
        for (int i = start; i < end; i++) {
            g.drawString(font, all.get(i).text(), x + PAD, row, all.get(i).color(), false);
            row += LINE_H;
        }
        if (scrollOffset > 0) {
            final String tag = "scrolled +" + scrollOffset;
            g.drawString(font, tag, x + width - font.width(tag) - 3, inputY, 0xFF5A6678, false);
        }
        g.drawString(font, "jsc> " + input + "_", x + PAD, inputY, 0xFFCDD6E2, false);
    }

    @Override
    public boolean charTyped(final char c) {
        if (c >= 32 && c != 127 && input.length() < DesktopShellRunPayload.MAX_LEN - 1) {
            input.append(c);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        switch (key) {
            case 257, 335 -> { // Enter / numpad Enter
                submit();
                return true;
            }
            case 259 -> { // Backspace
                if (input.length() > 0) {
                    input.deleteCharAt(input.length() - 1);
                }
                return true;
            }
            case 265 -> { // Up — older history
                recall(-1);
                return true;
            }
            case 264 -> { // Down — newer history
                recall(1);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        scrollOffset = Math.max(0, scrollOffset + (delta > 0 ? 1 : -1));
        return true;
    }

    private void submit() {
        final String line = input.toString().trim();
        input.setLength(0);
        historyIndex = -1;
        scrollOffset = 0;
        push("jsc> " + line, colorOf(CliStyle.PROMPT.ordinal()));
        if (line.isEmpty()) {
            return;
        }
        if (history.isEmpty() || !history.get(history.size() - 1).equals(line)) {
            history.add(line);
        }
        PacketDistributor.sendToServer(new DesktopShellRunPayload(host, line));
    }

    private void recall(final int direction) {
        if (history.isEmpty()) {
            return;
        }
        if (historyIndex == -1) {
            historyIndex = history.size();
        }
        historyIndex = Math.max(0, Math.min(history.size(), historyIndex + direction));
        input.setLength(0);
        if (historyIndex >= history.size()) {
            historyIndex = -1;
        } else {
            input.append(history.get(historyIndex));
        }
    }

    private static int colorOf(final int ordinal) {
        final CliStyle[] values = CliStyle.values();
        final CliStyle style = ordinal >= 0 && ordinal < values.length ? values[ordinal] : CliStyle.PLAIN;
        return switch (style) {
            case PROMPT -> 0xFFCDD6E2;
            case ACCENT, HEADER -> 0xFF39D6C4;
            case OK -> 0xFF5FE07A;
            case ERROR -> 0xFFEF6A5A;
            case WARN -> 0xFFF0B23A;
            case INFO -> 0xFF2AA7E0;
            case DIM -> 0xFF7D8A9C;
            default -> 0xFFCDD6E2;
        };
    }
}
