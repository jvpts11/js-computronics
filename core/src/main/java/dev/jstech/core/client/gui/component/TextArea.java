/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Core.
 */
package dev.jstech.core.client.gui.component;

import dev.jstech.core.client.gui.logic.TextDocument;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * A multi-line text editor: lines typed into a field, a caret moved by the arrow keys or put where a click
 * lands, Enter and Backspace splitting and joining lines, and the view scrolling to keep the caret in
 * sight. The text lives in a {@link TextDocument}; the area only draws it and turns keys into edits. While
 * it has the keyboard it takes every key, so nothing behind it reacts to typing.
 */
public final class TextArea extends UiComponent {

    private static final int LINE_H = 9;
    private static final int INSET = 3;

    private final TextDocument doc = new TextDocument();
    private int scroll;
    private Runnable onEdit = () -> { };
    @Nullable
    private Font lastFont;

    public TextDocument document() {
        return doc;
    }

    public String text() {
        return doc.text();
    }

    public TextArea setText(final String value) {
        doc.setText(value);
        scroll = 0;
        return this;
    }

    /** Fires after every edit, for an owner that tracks unsaved changes. */
    public TextArea setOnEdit(final Runnable action) {
        onEdit = action;
        return this;
    }

    @Override
    public boolean focusable() {
        return true;
    }

    /** How many lines fit in the bounds. */
    public int visibleLines() {
        return Math.max(1, (height() - 2) / LINE_H);
    }

    private void followCaret() {
        final int visible = visibleLines();
        if (doc.cursorLine() < scroll) {
            scroll = doc.cursorLine();
        } else if (doc.cursorLine() >= scroll + visible) {
            scroll = doc.cursorLine() - visible + 1;
        }
        scroll = Math.max(0, Math.min(Math.max(0, doc.lineCount() - visible), scroll));
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        lastFont = ctx.font();
        final boolean focused = isFocused();
        ctx.skin().field(g, x(), y(), width(), height(), focused);
        followCaret();
        final int visible = visibleLines();
        Draw.pushScissor(g, x() + 1, y() + 1, right() - 1, bottom() - 1);
        int ry = y() + 1;
        for (int i = scroll; i < doc.lineCount() && i - scroll < visible; i++) {
            final String text = doc.line(i);
            g.drawString(ctx.font(), text, x() + INSET, ry + 1, ctx.skin().text(), false);
            if (focused && i == doc.cursorLine()) {
                final int cx = x() + INSET + ctx.font().width(text.substring(0, Math.min(doc.cursorCol(), text.length())));
                g.fill(cx, ry, cx + 1, ry + LINE_H, ctx.skin().text());
            }
            ry += LINE_H;
        }
        Draw.popScissor(g);
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        // The click puts the caret on the line it landed on, at the nearest character boundary.
        final int line = scroll + (int) Math.floor((my - y() - 1) / (double) LINE_H);
        if (line >= 0 && line < doc.lineCount() && lastFont != null) {
            final String text = doc.line(line);
            final int target = (int) mx - (x() + INSET);
            int col = 0;
            while (col < text.length() && lastFont.width(text.substring(0, col + 1)) - lastFont.width(text.substring(col, col + 1)) / 2 <= target) {
                col++;
            }
            doc.setCursor(line, col);
        }
        return true;
    }

    @Override
    public boolean charTyped(final char c) {
        if (!isFocused()) {
            return false;
        }
        if (c >= 32 && c != 127) {
            doc.insert(c);
            onEdit.run();
        }
        return true;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (!isFocused()) {
            return false;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                doc.newline();
                onEdit.run();
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                doc.backspace();
                onEdit.run();
            }
            case GLFW.GLFW_KEY_LEFT -> doc.left();
            case GLFW.GLFW_KEY_RIGHT -> doc.right();
            case GLFW.GLFW_KEY_UP -> doc.up();
            case GLFW.GLFW_KEY_DOWN -> doc.down();
            default -> {
                // A focused editor eats every other key so nothing behind it reacts to typing.
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(final double mx, final double my, final double delta) {
        final int max = Math.max(0, doc.lineCount() - visibleLines());
        if (max == 0) {
            return false;
        }
        scroll = Math.max(0, Math.min(max, scroll + (delta > 0 ? -1 : 1)));
        return true;
    }
}
