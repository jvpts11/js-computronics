/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import dev.jstech.core.client.gui.logic.TextEditState;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * One line of a terminal: what is being typed after a prompt, submitted with Enter and recalled with the
 * arrow keys through the lines typed before. The caret moves within the line the way it does at any prompt:
 * Left and Right by a character, Home and End to the ends, Ctrl with Left or Right by a word. While it has
 * the keyboard and nothing is typed yet, it can show the last line the command produced instead of an empty
 * prompt. It looks like a terminal on every desktop: light text on a dark strip, not the skin's field.
 */
public final class CommandLine extends UiComponent {

    private static final int BACKGROUND = 0xFF101820;
    private static final int PROMPT = 0xFF40C060;

    private final int maxLength;
    private final java.util.function.Consumer<String> onSubmit;
    private final TextEditState input;
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private Supplier<String> prompt = () -> ">";
    private Supplier<String> idleText = () -> "";
    private IntSupplier idleColor = () -> PROMPT;
    private int background = BACKGROUND;
    private int textColor = PROMPT;

    public CommandLine(final int maxLength, final java.util.function.Consumer<String> onSubmit) {
        this.maxLength = Math.max(1, maxLength);
        this.onSubmit = onSubmit;
        this.input = new TextEditState(this.maxLength);
    }

    /** The line shown instead of an empty prompt, with its colour; empty text shows the prompt. */
    public CommandLine setIdle(final Supplier<String> text, final IntSupplier color) {
        idleText = text;
        idleColor = color;
        return this;
    }

    /** The prompt in front of what is typed: a shell's current directory, a bare {@code >} by default. */
    public CommandLine setPrompt(final Supplier<String> value) {
        prompt = value;
        return this;
    }

    /** The strip's colours, for a terminal that tints its console to the desktop it runs on. */
    public CommandLine setStyle(final int backgroundArgb, final int textArgb) {
        background = backgroundArgb;
        textColor = textArgb;
        return this;
    }

    /** What is typed so far. */
    public String input() {
        return input.edit();
    }

    /** Where the caret is in what is typed, counted in characters from the start. */
    public int caret() {
        return input.caret();
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        g.fill(x(), y(), right(), bottom(), background);
        final String idle = idleText.get();
        if (isFocused() && input.edit().isEmpty() && !idle.isEmpty()) {
            g.drawString(ctx.font(), Texts.trim(ctx.font(), idle, width() - 6), x() + 3, y() + 2, idleColor.getAsInt(), false);
            return;
        }
        /*
         * The caret takes the room of one character so the line can be scrolled to keep it in view even
         * when it sits at the very end, which is where it is most of the time.
         */
        final String full = prompt.get() + " " + input.edit() + " ";
        final int caretAt = prompt.get().length() + 1 + input.caret();
        final String shown = Texts.tail(ctx.font(), full, width() - 6);
        final int dropped = full.length() - shown.length();
        g.drawString(ctx.font(), shown, x() + 3, y() + 2, textColor, false);
        if (isFocused()) {
            final int visibleCaret = Math.max(0, Math.min(shown.length(), caretAt - dropped));
            final int cx = x() + 3 + ctx.font().width(shown.substring(0, visibleCaret));
            g.drawString(ctx.font(), "_", cx, y() + 2, textColor, false);
        }
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        return true;
    }

    @Override
    public boolean charTyped(final char c) {
        if (!isFocused() || c < 32 || c == 127) {
            return false;
        }
        input.type(c);
        return true;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (!isFocused()) {
            return false;
        }
        final boolean control = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> submit();
            case GLFW.GLFW_KEY_BACKSPACE -> input.backspace();
            case GLFW.GLFW_KEY_DELETE -> input.delete();
            case GLFW.GLFW_KEY_LEFT -> {
                if (control) {
                    input.wordLeft();
                } else {
                    input.left();
                }
            }
            case GLFW.GLFW_KEY_RIGHT -> {
                if (control) {
                    input.wordRight();
                } else {
                    input.right();
                }
            }
            case GLFW.GLFW_KEY_HOME -> input.home();
            case GLFW.GLFW_KEY_END -> input.end();
            case GLFW.GLFW_KEY_UP -> recall(-1);
            case GLFW.GLFW_KEY_DOWN -> recall(1);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void submit() {
        final String line = input.edit().trim();
        input.sync("");
        historyIndex = -1;
        if (line.isEmpty()) {
            return;
        }
        if (history.isEmpty() || !history.get(history.size() - 1).equals(line)) {
            history.add(line);
        }
        onSubmit.accept(line);
    }

    private void recall(final int direction) {
        if (history.isEmpty()) {
            return;
        }
        if (historyIndex == -1) {
            historyIndex = history.size();
        }
        historyIndex = Math.max(0, Math.min(history.size(), historyIndex + direction));
        if (historyIndex >= history.size()) {
            historyIndex = -1;
            input.sync("");
        } else {
            input.sync(history.get(historyIndex));
        }
    }
}
