/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * One line of a terminal: what is being typed after a prompt, submitted with Enter and recalled with the
 * arrow keys through the lines typed before. While it has the keyboard and nothing is typed yet, it can show
 * the last line the command produced instead of an empty prompt. It looks like a terminal on every desktop:
 * light text on a dark strip, not the skin's field.
 */
public final class CommandLine extends UiComponent {

    private static final int BACKGROUND = 0xFF101820;
    private static final int PROMPT = 0xFF40C060;

    private final int maxLength;
    private final java.util.function.Consumer<String> onSubmit;
    private final StringBuilder input = new StringBuilder();
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private Supplier<String> idleText = () -> "";
    private IntSupplier idleColor = () -> PROMPT;

    public CommandLine(final int maxLength, final java.util.function.Consumer<String> onSubmit) {
        this.maxLength = Math.max(1, maxLength);
        this.onSubmit = onSubmit;
    }

    /** The line shown instead of an empty prompt, with its colour; empty text shows the prompt. */
    public CommandLine setIdle(final Supplier<String> text, final IntSupplier color) {
        idleText = text;
        idleColor = color;
        return this;
    }

    /** What is typed so far. */
    public String input() {
        return input.toString();
    }

    @Override
    public boolean focusable() {
        return true;
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        g.fill(x(), y(), right(), bottom(), BACKGROUND);
        final String idle = idleText.get();
        if (isFocused() && input.isEmpty() && !idle.isEmpty()) {
            g.drawString(ctx.font(), Texts.trim(ctx.font(), idle, width() - 6), x() + 3, y() + 2, idleColor.getAsInt(), false);
            return;
        }
        final String line = "> " + input + (isFocused() ? "_" : "");
        g.drawString(ctx.font(), Texts.tail(ctx.font(), line, width() - 6), x() + 3, y() + 2, PROMPT, false);
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
        if (input.length() < maxLength) {
            input.append(c);
        }
        return true;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (!isFocused()) {
            return false;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> submit();
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (!input.isEmpty()) {
                    input.deleteCharAt(input.length() - 1);
                }
            }
            case GLFW.GLFW_KEY_UP -> recall(-1);
            case GLFW.GLFW_KEY_DOWN -> recall(1);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void submit() {
        final String line = input.toString().trim();
        input.setLength(0);
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
        input.setLength(0);
        if (historyIndex >= history.size()) {
            historyIndex = -1;
        } else {
            input.append(history.get(historyIndex));
        }
    }
}
