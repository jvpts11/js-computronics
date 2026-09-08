/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.operation.payload.ComputingPayloads;
import dev.jstech.computronics.operation.payload.DesktopShellOutputPayload;
import dev.jstech.computronics.operation.payload.DesktopShellRunPayload;
import dev.jstech.computronics.program.cli.CliStyle;
import dev.jstech.core.client.gui.component.CommandLine;
import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.UiContext;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * A view of a computer's console: the scrollback above, the command line below, and the machine's own
 * shell behind both.
 *
 * <p>A computer has one console, not one per window. This is a window onto it, so the terminal window
 * and the terminal panel inside an editor are two of these and both see what the machine said. What
 * one of them types the other watches happen, which is what having a single console means.
 */
public final class ShellView extends Panel {

    private static final int LINE_H = 9;
    private static final int PAD = 3;
    private static final int MAX_SCROLLBACK = 256;
    private static final int INPUT_TEXT = 0xFFCDD6E2;
    private static final int TAG_COLOR = 0xFF5A6678;

    /** One line of the scrollback, in the colour the shell styled it. */
    private record Line(String text, int color) {
    }

    private final BlockPos host;
    /*
     * The console's ground follows the system it runs on, and which system that is is a question only
     * this mod's skin answers; the toolkit's own context knows the shared look and not the form.
     */
    private OsSkin osSkin = OsSkin.fallback();
    private final Deque<Line> scrollback = new ArrayDeque<>();
    private final ListView<Line> output;
    private final Label scrolledTag;
    private final CommandLine console;

    /** How many lines up from the bottom the output is scrolled. */
    private int scrollOffset;
    /** The prompt, synced from the server after each command so it tracks the current directory. */
    private String prompt;
    /**
     * Whether a program has the terminal.
     *
     * <p>While it does there is no prompt and nothing can be typed: the keyboard belongs to the program,
     * which listens for one thing, the ask to stop.
     */
    private boolean busy;

    /**
     * A view of the console of the computer at {@code host}.
     *
     * @param posix  whether the machine speaks bash rather than the DOS prompt
     * @param banner whether to greet the player, which the terminal window does and a panel inside
     *               another program does not
     */
    public ShellView(final BlockPos host, final boolean posix, final boolean banner) {
        this.host = host;
        if (posix) {
            /*
             * A real Linux terminal opens on a bare prompt; the empty round trip below replaces this
             * placeholder with the server's user@host one.
             */
            this.prompt = "$";
        } else {
            this.prompt = "C:\\>";
            if (banner) {
                push("J's Computronics Shell", colorOf(CliStyle.ACCENT.ordinal()));
                push("type a command and press ENTER", colorOf(CliStyle.DIM.ordinal()));
            }
        }
        this.output = add(new ListView<Line>(() -> this.wrapCache, LINE_H, this::renderLine));
        this.scrolledTag = add(new Label(() -> this.scrollOffset > 0 ? "scrolled +" + this.scrollOffset : "")
                .setColor(TAG_COLOR).setAlign(Label.Align.RIGHT));
        /*
         * No prompt is drawn while a program is running, because on a real terminal there is none: the
         * program has the screen until it returns.
         */
        this.console = add(new CommandLine(DesktopShellRunPayload.MAX_LEN - 1, this::submit)
                .setPrompt(() -> this.busy ? "" : this.prompt));
        focus(this.console);
        ShellViews.register(this);
        // Sync the real prompt (and any pending build notices) before the player types anything.
        PacketDistributor.sendToServer(new DesktopShellRunPayload(host, ""));
    }

    /** Stops the machine's console being drawn into a view nobody is looking at. */
    public void release() {
        ShellViews.forget(this);
        CodeFileReplies.forget(this.opening);
    }

    /**
     * The editor that has taken this terminal, or null when the prompt has it.
     *
     * <p>A terminal editor is not a window: it is handed the glass the terminal was using, which is the
     * whole reason a machine with no desktop can still be programmed.
     */
    private TtyEditor editor;

    /** Whoever is waiting for the file an editor was asked to open. */
    private final CodeFileReplies.IReader opening = new CodeFileReplies.IReader() {
        @Override
        public void onContent(final String path, final String content, final boolean exists) {
            ShellView.this.editor = new TtyEditor(path, content,
                    ShellView.this.flavour, ShellView.this.terminalHost);
            if (!exists) {
                ShellView.this.editor.say("\"" + ShellView.this.editor.name() + "\" [New]");
            }
        }
    };

    /** How the editor being opened reads a keyboard, set just before the file is asked for. */
    private TtyEditor.IKeys flavour;

    /** What an editor running here can ask the terminal to do for it. */
    private final TtyEditor.IHost terminalHost = new TtyEditor.IHost() {
        @Override
        public void save(final String path, final String text) {
            PacketDistributor.sendToServer(
                    new dev.jstech.computronics.operation.payload.SaveFilePayload(
                            ShellView.this.host, path, text));
        }

        @Override
        public void quit() {
            ShellView.this.editor = null;
            /*
             * The prompt comes back where it was, so the machine is asked for it rather than guessed:
             * a program may have left the terminal somewhere else while the editor had it.
             */
            PacketDistributor.sendToServer(new DesktopShellRunPayload(ShellView.this.host, ""));
        }
    };

    /** Hands the terminal to an editor on {@code path}, which the machine is asked for. */
    public void openEditor(final String path, final TtyEditor.IKeys keys) {
        this.flavour = keys;
        CodeFileReplies.expectContent(this.opening, path);
        PacketDistributor.sendToServer(
                new dev.jstech.computronics.operation.payload.RequestFileContentPayload(this.host, path));
    }

    /** Whether an editor has this terminal. */
    public boolean editing() {
        return this.editor != null;
    }

    /** Everything the console has printed here, as one piece of text. */
    public String scrollbackText() {
        final StringBuilder text = new StringBuilder();
        for (final Line line : this.scrollback) {
            text.append(line.text()).append('\n');
        }
        return text.toString();
    }

    /** The system this view is running under, which decides the console's ground. */
    public ShellView setSkin(final OsSkin value) {
        this.osSkin = value == null ? OsSkin.fallback() : value;
        return this;
    }

    /** Whether a program currently has the terminal. */
    public boolean busy() {
        return this.busy;
    }

    /** Puts a line in this view's scrollback without asking the machine anything. */
    public void say(final String text, final CliStyle style) {
        push(text, colorOf(style.ordinal()));
    }

    /** Runs a line as though the player had typed it. */
    public void run(final String line) {
        submit(line);
    }

    /** Takes what the machine's console said. */
    void accept(final DesktopShellOutputPayload payload) {
        if (payload.clear()) {
            this.scrollback.clear();
            this.generation++;
        }
        for (final DesktopShellOutputPayload.WireLine line : payload.lines()) {
            push(line.text(), colorOf(line.style()));
        }
        // An empty prompt means "unchanged"; otherwise track the new current directory.
        if (!payload.prompt().isEmpty()) {
            this.prompt = payload.prompt();
        }
        this.busy = payload.busy();
        /*
         * The machine decided a command gives the terminal away, having checked that the editor is
         * installed. A terminal that has never heard of the one it named carries on with its prompt.
         */
        if (payload.handsOver()) {
            final TtyEditor.IKeys flavourAsked = TtyEditors.flavourOf(payload.editor());
            if (flavourAsked != null) {
                openEditor(payload.editorPath(), flavourAsked);
            }
        }
    }

    private void push(final String text, final int color) {
        this.scrollback.addLast(new Line(text, color));
        while (this.scrollback.size() > MAX_SCROLLBACK) {
            this.scrollback.removeFirst();
        }
        this.generation++;
    }

    /*
     * The view is freely resizable, so lines wrap at render time to the current width; the wrapped view
     * is cached per (width, scrollback generation) so a console nobody types into costs nothing a frame.
     */
    private int generation;
    private List<Line> wrapCache = List.of();
    private int wrapCacheW = -1;
    private int wrapCacheGen = -1;

    private List<Line> wrapped(final Font font, final int usableW) {
        if (this.wrapCacheW == usableW && this.wrapCacheGen == this.generation) {
            return this.wrapCache;
        }
        final List<Line> out = new ArrayList<>();
        for (final Line line : this.scrollback) {
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
        this.wrapCache = out;
        this.wrapCacheW = usableW;
        this.wrapCacheGen = this.generation;
        return out;
    }

    /** The console ground, kept dark like a real terminal, tinted to the system it runs on. */
    public static int groundOf(final OsSkin skin) {
        return switch (skin.form()) {
            case BEVEL -> 0xFF000000;
            case LUNA -> 0xFF0A1A30;
            case FLAT -> 0xFF1E1F23;
            /*
             * The period Unix terminals were not pure black: xterm-era consoles carried a slight cast
             * from the desktop they ran on.
             */
            case KDE2 -> 0xFF0C1420;
            case GNOME1 -> 0xFF1A141E;
        };
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        if (this.editor != null) {
            // The editor has the glass: no scrollback, no prompt, exactly as at a real terminal.
            this.editor.render(g, ctx.font(), x(), y(), width(), height(),
                    dev.jstech.computronics.os.edit.InkPalette.DARK);
            return;
        }
        final int ground = groundOf(this.osSkin);
        g.fill(x(), y(), right(), bottom(), ground);
        // Lines wrap to the view's current width, so nothing leaks past the frame however it is resized.
        final List<Line> all = wrapped(ctx.font(), Math.max(40, width() - PAD * 2 - 2));
        final int inputY = bottom() - LINE_H;
        final int visible = Math.max(1, (height() - PAD - LINE_H - 2) / LINE_H);
        final int maxScroll = Math.max(0, all.size() - visible);
        this.scrollOffset = Math.min(this.scrollOffset, maxScroll);
        // The output is anchored to its bottom: the scroll offset counts lines up from the newest.
        this.output.setBounds(x() + PAD, y() + PAD, width() - PAD * 2, visible * LINE_H);
        this.output.setScroll(maxScroll - this.scrollOffset);
        this.scrolledTag.setBounds(x() + PAD, inputY, width() - PAD * 2 - 1, 8);
        this.console.setBounds(x(), inputY - 2, width(), LINE_H + 2);
        this.console.setStyle(ground, INPUT_TEXT);
        super.render(g, ctx);
    }

    private void renderLine(final GuiGraphics g, final UiContext ctx, final Line line, final int index,
                            final int x, final int y, final int w, final int h,
                            final boolean hovered, final boolean selected) {
        g.drawString(ctx.font(), line.text(), x, y, line.color(), false);
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        super.mouseClicked(mx, my, button);
        // Typing always goes to the command line: a click on the output must not take the keyboard away.
        focus(this.console);
        return true;
    }

    @Override
    public boolean charTyped(final char c) {
        if (this.editor != null) {
            return this.editor.charTyped(c);
        }
        // While a program has the terminal the keyboard is its, and it listens for one thing only.
        return this.busy || super.charTyped(c);
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (this.editor != null) {
            return this.editor.keyPressed(key, modifiers);
        }
        if (this.busy) {
            if (key == GLFW.GLFW_KEY_C && net.minecraft.client.gui.screens.Screen.hasControlDown()) {
                PacketDistributor.sendToServer(new DesktopShellRunPayload(this.host, ComputingPayloads.INTERRUPT));
            }
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(final double mx, final double my, final double delta) {
        if (this.editor != null) {
            return this.editor.scrolled(delta);
        }
        this.scrollOffset = Math.max(0, this.scrollOffset + (delta > 0 ? 1 : -1));
        return true;
    }

    private void submit(final String line) {
        this.scrollOffset = 0;
        push(this.prompt + " " + line, colorOf(CliStyle.PROMPT.ordinal()));
        final String[] parts = line.split("\\s+", 2);
        final String verb = parts[0].toLowerCase(Locale.ROOT);
        // "run/start/open <program>" launches a desktop window client-side (the server shell has no windows).
        if (verb.equals("run") || verb.equals("start") || verb.equals("open")) {
            handleRun(parts.length > 1 ? parts[1].trim() : "");
            return;
        }
        PacketDistributor.sendToServer(new DesktopShellRunPayload(this.host, line));
    }

    /** Opens an installed program's window by name, or lists what can be opened. */
    private void handleRun(final String name) {
        final List<String> labels = DesktopScreen.openableLabels();
        if (name.isEmpty()) {
            push("Programs: " + String.join(", ", labels), 0xFFB7BCCB);
            push("Usage: run <program>", 0xFF7A8496);
            return;
        }
        final String norm = name.toLowerCase(Locale.ROOT).replace(" ", "");
        for (final String label : labels) {
            if (label.equalsIgnoreCase(name) || label.toLowerCase(Locale.ROOT).replace(" ", "").equals(norm)) {
                DesktopScreen.requestOpen(label);
                push("Opening " + label + "...", 0xFF8FE0A8);
                return;
            }
        }
        push("No such program: " + name + " (type 'run' to list them)", 0xFFE06A6A);
    }

    static int colorOf(final int ordinal) {
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
            // The extended palette: brand-tinted terminal colours (screenfetch logos and the like).
            case ORANGE -> 0xFFE95420;
            case MAGENTA -> 0xFFE0447C;
            case BLUE -> 0xFF5A8FD6;
            case CYAN -> 0xFF2FA6E8;
            case PURPLE -> 0xFF9E8FD6;
            default -> 0xFFCDD6E2;
        };
    }
}
