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
 * A plain multi-line text editor: rows of text in a field, a caret that the arrows move and a click
 * lands, Enter and Backspace splitting and joining lines, and the view scrolling both ways to keep the
 * caret in view. A thin bar down the right edge shows where the view is when there are more rows than
 * fit, and one along the bottom when a row is wider than the field; Shift with the wheel slides the rows
 * sideways.
 */
public final class TextArea extends UiComponent {

    private static final int LINE_H = 9;
    private static final int INSET = 3;
    private static final int BAR = 3;
    private static final int BAR_TRACK = 0x30808080;
    private static final int BAR_THUMB = 0xA0909090;

    private final TextDocument doc = new TextDocument();
    private int scroll;
    /** How far the rows are slid to the left, in pixels, so a long line can be read to its end. */
    private int shift;
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
        shift = 0;
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

    private int room() {
        return width() - 2 * INSET - BAR;
    }

    private int widest(final Font font) {
        int widest = 0;
        for (int i = 0; i < doc.lineCount(); i++) {
            widest = Math.max(widest, font.width(doc.line(i)));
        }
        return widest;
    }

    private void followCaretAcross(final Font font) {
        final String line = doc.line(doc.cursorLine());
        final int caretX = font.width(line.substring(0, Math.min(doc.cursorCol(), line.length())));
        final int room = Math.max(8, room());
        if (isFocused()) {
            if (caretX - shift < 0) {
                shift = caretX;
            } else if (caretX - shift > room - 2) {
                shift = caretX - room + 2;
            }
        }
        shift = Math.max(0, Math.min(Math.max(0, widest(font) + 2 - room), shift));
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        lastFont = ctx.font();
        final boolean focused = isFocused();
        ctx.skin().field(g, x(), y(), width(), height(), focused);
        followCaret();
        followCaretAcross(ctx.font());
        final int visible = visibleLines();
        Draw.pushScissor(g, x() + 1, y() + 1, right() - 1, bottom() - 1);
        int ry = y() + 1;
        for (int i = scroll; i < doc.lineCount() && i - scroll < visible; i++) {
            final String text = doc.line(i);
            g.drawString(ctx.font(), text, x() + INSET - shift, ry + 1, ctx.skin().text(), false);
            if (focused && i == doc.cursorLine()) {
                final int cx = x() + INSET - shift
                        + ctx.font().width(text.substring(0, Math.min(doc.cursorCol(), text.length())));
                g.fill(cx, ry, cx + 1, ry + LINE_H, ctx.skin().text());
            }
            ry += LINE_H;
        }
        Draw.popScissor(g);
        drawBars(g, ctx.font());
    }

    private void drawBars(final GuiGraphics g, final Font font) {
        final int rows = doc.lineCount();
        final int visible = visibleLines();
        if (rows > visible) {
            final int trackH = height() - 2 - BAR;
            final int thumbH = Math.max(6, trackH * visible / rows);
            final int thumbY = y() + 1 + (trackH - thumbH) * scroll / Math.max(1, rows - visible);
            g.fill(right() - 1 - BAR, y() + 1, right() - 1, y() + 1 + trackH, BAR_TRACK);
            g.fill(right() - 1 - BAR, thumbY, right() - 1, thumbY + thumbH, BAR_THUMB);
        }
        final int room = Math.max(8, room());
        final int widest = widest(font) + 2;
        if (widest > room) {
            final int trackX = x() + 1;
            final int trackW = width() - 2 - BAR;
            final int thumbW = Math.max(6, trackW * room / widest);
            final int thumbX = trackX + (trackW - thumbW) * shift / Math.max(1, widest - room);
            g.fill(trackX, bottom() - 1 - BAR, trackX + trackW, bottom() - 1, BAR_TRACK);
            g.fill(thumbX, bottom() - 1 - BAR, thumbX + thumbW, bottom() - 1, BAR_THUMB);
        }
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        // The click puts the caret on the line it landed on, at the nearest character boundary.
        final int line = scroll + (int) Math.floor((my - y() - 1) / (double) LINE_H);
        if (line >= 0 && line < doc.lineCount() && lastFont != null) {
            final String text = doc.line(line);
            final int target = (int) mx - (x() + INSET - shift);
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
            case GLFW.GLFW_KEY_DELETE -> {
                doc.delete();
                onEdit.run();
            }
            case GLFW.GLFW_KEY_LEFT -> doc.left();
            case GLFW.GLFW_KEY_RIGHT -> doc.right();
            case GLFW.GLFW_KEY_UP -> doc.up();
            case GLFW.GLFW_KEY_DOWN -> doc.down();
            case GLFW.GLFW_KEY_HOME -> doc.home(false);
            case GLFW.GLFW_KEY_END -> doc.end(false);
            default -> {
                // A focused editor eats every other key so nothing behind it reacts to typing.
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(final double mx, final double my, final double delta) {
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown() && lastFont != null) {
            final int room = Math.max(8, room());
            final int max = Math.max(0, widest(lastFont) + 2 - room);
            shift = Math.max(0, Math.min(max, shift - (int) Math.signum(delta) * 24));
            return true;
        }
        final int max = Math.max(0, doc.lineCount() - visibleLines());
        if (max == 0) {
            return false;
        }
        scroll = Math.max(0, Math.min(max, scroll + (delta > 0 ? -1 : 1)));
        return true;
    }
}
