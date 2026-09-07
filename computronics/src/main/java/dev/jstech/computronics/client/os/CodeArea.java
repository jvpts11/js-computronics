/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.os.edit.CodeRuns;
import dev.jstech.computronics.os.edit.InkPalette;
import dev.jstech.core.client.gui.component.Draw;
import dev.jstech.core.client.gui.component.UiComponent;
import dev.jstech.core.client.gui.component.UiContext;
import dev.jstech.core.client.gui.logic.TextDocument;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * The text area every editor in the mod writes code in: numbered rows, a colour per piece of source,
 * and a mark in the margin where the compiler complained.
 *
 * <p>It knows no language. Something else says how a row is coloured and what the marks are, and this
 * draws the answer, so the same component serves Cannon today and whatever a pack registers tomorrow.
 * Colouring is asked for only when the text has actually changed, because reading a whole program to
 * paint one frame of a window nobody typed into is work for nothing.
 */
public final class CodeArea extends UiComponent {

    private static final int LINE_H = 9;
    private static final int INSET = 3;
    private static final int GUTTER_PAD = 4;
    private static final int MARK_W = 5;

    /** Says how the rows of a document are coloured. */
    @FunctionalInterface
    public interface IColouring {

        /** The runs of every row, in order, as {@link CodeRuns#byLine} builds them. */
        List<List<CodeRuns.Run>> runsOf(List<String> lines);
    }

    /** Something the compiler said about a row, shown in the margin beside it. */
    public record Mark(int line, boolean error, String message) {
    }

    private final TextDocument doc = new TextDocument();
    private IColouring colouring = lines -> List.of();
    private List<Mark> marks = List.of();
    private InkPalette palette = InkPalette.LIGHT;
    private Runnable onEdit = () -> { };
    /*
     * A code area is used on its own, with no panel to hand the keyboard around, so it keeps its own
     * flag rather than the one a panel would set. Which window gets the keyboard is already the
     * desktop's decision, and it routes input to that window's app.
     */
    private boolean active = true;

    private int scroll;
    private Font lastFont;

    /** The colouring stands until the text changes; painting is not a reason to read the program again. */
    private List<List<CodeRuns.Run>> cached = List.of();
    private String colouredText;

    /** The document being edited, so an owner can read it or put a file in it. */
    public TextDocument document() {
        return this.doc;
    }

    /** The whole text. */
    public String text() {
        return this.doc.text();
    }

    /** Replaces the whole text and puts the view back at the top. */
    public CodeArea setText(final String value) {
        this.doc.setText(value);
        this.scroll = 0;
        this.colouredText = null;
        return this;
    }

    /** Says how to colour the rows. */
    public CodeArea setColouring(final IColouring value) {
        this.colouring = value == null ? lines -> List.of() : value;
        this.colouredText = null;
        return this;
    }

    /** What the compiler said, to show in the margin. */
    public CodeArea setMarks(final List<Mark> value) {
        this.marks = value == null ? List.of() : List.copyOf(value);
        return this;
    }

    /** What is drawn on: the colours follow the window's own ground. */
    public CodeArea setPalette(final InkPalette value) {
        this.palette = value == null ? InkPalette.LIGHT : value;
        return this;
    }

    /** Runs after every change the player makes, for an owner that recompiles as it is typed. */
    public CodeArea setOnEdit(final Runnable action) {
        this.onEdit = action == null ? () -> { } : action;
        return this;
    }

    /** Whether the keyboard is on this area: the caret shows and typing lands here. */
    public CodeArea setActive(final boolean value) {
        this.active = value;
        return this;
    }

    @Override
    public boolean focusable() {
        return true;
    }

    /** How many rows fit. */
    public int visibleLines() {
        return Math.max(1, (height() - 2) / LINE_H);
    }

    /** The width the numbers take, which is what the code is indented past. */
    private int gutterWidth(final Font font) {
        final int widest = font.width(String.valueOf(Math.max(1, this.doc.lineCount())));
        return MARK_W + GUTTER_PAD + widest + GUTTER_PAD;
    }

    private void followCaret() {
        final int visible = visibleLines();
        if (this.doc.cursorLine() < this.scroll) {
            this.scroll = this.doc.cursorLine();
        } else if (this.doc.cursorLine() >= this.scroll + visible) {
            this.scroll = this.doc.cursorLine() - visible + 1;
        }
        this.scroll = Math.max(0, Math.min(Math.max(0, this.doc.lineCount() - visible), this.scroll));
    }

    /** The rows, coloured, reading the program again only when it is not the one already coloured. */
    private List<List<CodeRuns.Run>> runs() {
        final String text = this.doc.text();
        if (!text.equals(this.colouredText)) {
            final List<String> lines = new ArrayList<>(this.doc.lineCount());
            for (int i = 0; i < this.doc.lineCount(); i++) {
                lines.add(this.doc.line(i));
            }
            this.cached = this.colouring.runsOf(lines);
            this.colouredText = text;
        }
        return this.cached;
    }

    @Override
    public void render(final GuiGraphics g, final UiContext ctx) {
        this.lastFont = ctx.font();
        final boolean focused = this.active;
        followCaret();

        final int gutter = gutterWidth(ctx.font());
        g.fill(x(), y(), right(), bottom(), this.palette.ground());
        g.fill(x(), y(), x() + gutter, bottom(), this.palette.gutter());

        final List<List<CodeRuns.Run>> runs = runs();
        final int visible = visibleLines();
        Draw.pushScissor(g, x(), y(), right(), bottom());
        int ry = y() + 1;
        for (int i = this.scroll; i < this.doc.lineCount() && i - this.scroll < visible; i++) {
            final String line = this.doc.line(i);
            if (i == this.doc.cursorLine() && focused) {
                g.fill(x() + gutter, ry, right(), ry + LINE_H, this.palette.currentLine());
            }
            drawMark(g, i, ry);
            final String number = String.valueOf(i + 1);
            g.drawString(ctx.font(), number,
                    x() + gutter - GUTTER_PAD - ctx.font().width(number), ry + 1, this.palette.gutterText(), false);
            drawLine(g, ctx.font(), line, i < runs.size() ? runs.get(i) : List.of(), x() + gutter + INSET, ry + 1);
            if (focused && i == this.doc.cursorLine()) {
                final int col = Math.min(this.doc.cursorCol(), line.length());
                final int cx = x() + gutter + INSET + ctx.font().width(line.substring(0, col));
                g.fill(cx, ry, cx + 1, ry + LINE_H, this.palette.caret());
            }
            ry += LINE_H;
        }
        Draw.popScissor(g);
    }

    /** One row, run by run, each stretch in the colour its piece of source asked for. */
    private void drawLine(final GuiGraphics g, final Font font, final String line,
                          final List<CodeRuns.Run> runs, final int startX, final int textY) {
        if (runs.isEmpty()) {
            g.drawString(font, line, startX, textY, this.palette.plain(), false);
            return;
        }
        int rx = startX;
        for (final CodeRuns.Run run : runs) {
            final int from = Math.min(run.start(), line.length());
            final int to = Math.min(run.start() + run.length(), line.length());
            if (to <= from) {
                continue;
            }
            final String piece = line.substring(from, to);
            g.drawString(font, piece, rx, textY, this.palette.of(run.ink()), false);
            rx += font.width(piece);
        }
    }

    /** The margin beside a row: a square where the compiler complained, red for an error. */
    private void drawMark(final GuiGraphics g, final int line, final int rowY) {
        for (final Mark mark : this.marks) {
            if (mark.line() - 1 != line) {
                continue;
            }
            final int colour = mark.error() ? 0xFFC0392B : 0xFFD08A1E;
            g.fill(x() + 1, rowY + 2, x() + 1 + MARK_W - 1, rowY + 2 + MARK_W - 1, colour);
            return;
        }
    }

    /** What the compiler said about the row under the cursor, for the owner to show as a tooltip. */
    public String messageAt(final double mx, final double my) {
        if (this.lastFont == null || !contains(mx, my)) {
            return "";
        }
        final int line = this.scroll + (int) Math.floor((my - y() - 1) / (double) LINE_H);
        for (final Mark mark : this.marks) {
            if (mark.line() - 1 == line) {
                return mark.message();
            }
        }
        return "";
    }

    @Override
    public boolean mouseClicked(final double mx, final double my, final int button) {
        final int line = this.scroll + (int) Math.floor((my - y() - 1) / (double) LINE_H);
        if (line >= 0 && line < this.doc.lineCount() && this.lastFont != null) {
            final String text = this.doc.line(line);
            final int target = (int) mx - (x() + gutterWidth(this.lastFont) + INSET);
            int col = 0;
            while (col < text.length()
                    && this.lastFont.width(text.substring(0, col + 1))
                    - this.lastFont.width(text.substring(col, col + 1)) / 2 <= target) {
                col++;
            }
            this.doc.setCursor(line, col);
        }
        return true;
    }

    @Override
    public boolean charTyped(final char c) {
        if (!this.active) {
            return false;
        }
        if (c >= 32 && c != 127) {
            this.doc.insert(c);
            this.onEdit.run();
        }
        return true;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (!this.active) {
            return false;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                this.doc.newline();
                this.onEdit.run();
            }
            case GLFW.GLFW_KEY_BACKSPACE -> {
                this.doc.backspace();
                this.onEdit.run();
            }
            case GLFW.GLFW_KEY_TAB -> {
                /*
                 * Four spaces, not a tab character: the file is read back by a compiler that counts
                 * columns, and a column has to mean the same thing to it as it does on the screen.
                 */
                for (int i = 0; i < 4; i++) {
                    this.doc.insert(' ');
                }
                this.onEdit.run();
            }
            case GLFW.GLFW_KEY_LEFT -> this.doc.left();
            case GLFW.GLFW_KEY_RIGHT -> this.doc.right();
            case GLFW.GLFW_KEY_UP -> this.doc.up();
            case GLFW.GLFW_KEY_DOWN -> this.doc.down();
            case GLFW.GLFW_KEY_HOME -> this.doc.setCursor(this.doc.cursorLine(), 0);
            case GLFW.GLFW_KEY_END ->
                    this.doc.setCursor(this.doc.cursorLine(), this.doc.line(this.doc.cursorLine()).length());
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(final double mx, final double my, final double delta) {
        final int visible = visibleLines();
        this.scroll = Math.max(0, Math.min(Math.max(0, this.doc.lineCount() - visible),
                this.scroll - (int) Math.signum(delta) * 3));
        return true;
    }
}
