/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.operation.payload.DesktopShellOutputPayload;
import dev.jstech.computronics.operation.payload.DesktopShellRunPayload;
import dev.jstech.computronics.os.DesktopEnvironmentDef;
import dev.jstech.computronics.program.cli.CliStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A terminal window for the desktop: the same CLI as the Command Prompt (dir, type, write, del, run,
 * iql, operation, ...) inside a Frames window. This is how a graphical OS, whose monitor never shows the
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

    /** The shell prompt, synced from the server after each command so it tracks the current directory. */
    private String prompt;
    /** Whether the host desktop is a Linux one, so the window speaks bash instead of the DOS prompt. */
    private final boolean posix;
    /** The window title: the desktop environment's own terminal name (Konsole, Terminal, Megashell...). */
    private final String title;

    private static ShellApp active;

    private record Line(String text, int color) {
    }

    public ShellApp(final BlockPos host) {
        this(host, null);
    }

    public ShellApp(final BlockPos host, final ResourceLocation desktopId) {
        this.host = host;
        active = this;
        final DesktopEnvironmentDef chrome = desktopId == null
                ? null : dev.jstech.computronics.os.OsRegistry.getDesktop(desktopId);
        this.posix = chrome != null && switch (chrome.panelStyle()) {
            case KDE, GNOME, CINNAMON -> true;
            default -> false;
        };
        final dev.jstech.computronics.os.ProgramSpec promptSpec =
                dev.jstech.computronics.program.Programs.get(
                        dev.jstech.computronics.program.Programs.COMMAND_PROMPT);
        // Frames 11 ships its own modern shell ("Megashell"); every other desktop names the window after its
        // native terminal (Konsole on KDE, Terminal on GNOME/Cinnamon, Command Prompt on the older Frames).
        if (chrome != null && chrome.panelStyle() == dev.jstech.computronics.os.PanelStyle.FRAMES_11) {
            this.title = "Megashell";
        } else {
            this.title = chrome != null && promptSpec != null ? chrome.nameOf(promptSpec) : "Command Prompt";
        }
        if (posix) {
            // A real Linux terminal opens on a bare prompt; the immediate empty round-trip below replaces
            // this placeholder with the server's user@host one.
            this.prompt = "$";
        } else {
            this.prompt = "C:\\>";
            push("J's Computronics Shell", colorOf(CliStyle.ACCENT.ordinal()));
            push("type a command and press ENTER", colorOf(CliStyle.DIM.ordinal()));
        }
        // Sync the real prompt (and any pending build notices) before the player types anything.
        PacketDistributor.sendToServer(new DesktopShellRunPayload(host, ""));
    }

    /** Routes a server output reply to the open Shell window. */
    public static void accept(final DesktopShellOutputPayload payload) {
        if (active == null) {
            return;
        }
        if (payload.clear()) {
            active.scrollback.clear();
            active.generation++;
        }
        for (final DesktopShellOutputPayload.WireLine line : payload.lines()) {
            active.push(line.text(), colorOf(line.style()));
        }
        // An empty prompt means "unchanged"; otherwise track the new current directory.
        if (!payload.prompt().isEmpty()) {
            active.prompt = payload.prompt();
        }
        // Any command may have installed or removed a program (apt install, uninstall, ...): refresh the
        // desktop's launcher state so the change shows up without closing the monitor.
        DesktopScreen.refreshActive();
    }

    private void push(final String text, final int color) {
        scrollback.addLast(new Line(text, color));
        while (scrollback.size() > MAX_SCROLLBACK) {
            scrollback.removeFirst();
        }
        generation++;
    }

    // The window is freely resizable, so lines wrap at render time to the current content width; the
    // wrapped view is cached per (width, scrollback generation) so a static console costs nothing per frame.
    private int generation;
    private List<Line> wrapCache = List.of();
    private int wrapCacheW = -1;
    private int wrapCacheGen = -1;

    private List<Line> wrapped(final Font font, final int usableW) {
        if (wrapCacheW == usableW && wrapCacheGen == generation) {
            return wrapCache;
        }
        final List<Line> out = new ArrayList<>();
        for (final Line line : scrollback) {
            String rest = line.text();
            while (true) {
                if (font.width(rest) <= usableW) {
                    out.add(new Line(rest, line.color()));
                    break;
                }
                String piece = font.plainSubstrByWidth(rest, usableW);
                final int space = piece.lastIndexOf(' ');
                if (space > piece.length() / 2) {
                    piece = piece.substring(0, space);
                }
                if (piece.isEmpty()) {
                    out.add(new Line(rest, line.color()));
                    break;
                }
                out.add(new Line(piece, line.color()));
                rest = rest.substring(piece.length()).stripLeading();
                if (rest.isEmpty()) {
                    break;
                }
            }
        }
        wrapCache = out;
        wrapCacheW = usableW;
        wrapCacheGen = generation;
        return out;
    }

    @Override
    public String title() {
        return title;
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
            // The period Unix terminals were not pure black: xterm-era consoles carried a slight cast
            // from the desktop they ran on.
            case KDE2 -> 0xFF0C1420;
            case GNOME1 -> 0xFF1A141E;
        };
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        g.fill(x, y, x + width, y + height, consoleBg());

        // No own scissor here: app content is drawn inside the desktop's translate, where enableScissor (which
        // takes absolute coordinates) would clip the wrong region and hide the text. Lines wrap below to the
        // window's current width, so nothing leaks past the frame however the window is resized.
        final int inputY = y + height - LINE_H;
        final int visible = Math.max(1, (height - PAD - LINE_H - 2) / LINE_H);
        final List<Line> all = wrapped(font, Math.max(40, width - PAD * 2 - 2));
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
        g.drawString(font, prompt + " " + input + "_", x + PAD, inputY, 0xFFCDD6E2, false);
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
        push(prompt + " " + line, colorOf(CliStyle.PROMPT.ordinal()));
        if (line.isEmpty()) {
            return;
        }
        if (history.isEmpty() || !history.get(history.size() - 1).equals(line)) {
            history.add(line);
        }
        // "run/start/open <program>" launches a desktop window client-side (the server shell has no windows).
        final String[] parts = line.split("\\s+", 2);
        final String verb = parts[0].toLowerCase(java.util.Locale.ROOT);
        if (verb.equals("run") || verb.equals("start") || verb.equals("open")) {
            handleRun(parts.length > 1 ? parts[1].trim() : "");
            return;
        }
        PacketDistributor.sendToServer(new DesktopShellRunPayload(host, line));
    }

    /** Opens an installed program's window by name, or lists what can be opened. */
    private void handleRun(final String name) {
        final java.util.List<String> labels = DesktopScreen.openableLabels();
        if (name.isEmpty()) {
            push("Programs: " + String.join(", ", labels), 0xFFB7BCCB);
            push("Usage: run <program>", 0xFF7A8496);
            return;
        }
        final String norm = name.toLowerCase(java.util.Locale.ROOT).replace(" ", "");
        for (final String label : labels) {
            if (label.equalsIgnoreCase(name) || label.toLowerCase(java.util.Locale.ROOT).replace(" ", "").equals(norm)) {
                DesktopScreen.requestOpen(label);
                push("Opening " + label + "...", 0xFF8FE0A8);
                return;
            }
        }
        push("No such program: " + name + " (type 'run' to list them)", 0xFFE06A6A);
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
            // The extended palette: brand-tinted terminal colors (screenfetch logos and the like).
            case ORANGE -> 0xFFE95420;
            case MAGENTA -> 0xFFE0447C;
            case BLUE -> 0xFF5A8FD6;
            case CYAN -> 0xFF2FA6E8;
            case PURPLE -> 0xFF9E8FD6;
            default -> 0xFFCDD6E2;
        };
    }
}
