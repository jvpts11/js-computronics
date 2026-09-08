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
import dev.jstech.core.client.gui.logic.TextDocument;
import dev.jstech.core.language.IProgrammingLanguage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * An editor that has taken over a terminal.
 *
 * <p>It draws no window and owns no chrome: it is handed the glass a terminal was using and paints
 * rows of text on it, which is what a program that runs at a prompt gets. That is the whole reason it
 * exists, because it means a machine with no desktop, or one reached over ssh, can still be programmed.
 *
 * <p>What the keys mean is somebody else's answer. This holds the text, the view and the message line,
 * and the two editors that use it differ only in how they read a keyboard.
 */
public final class TtyEditor {

    private static final int LINE_H = 9;
    private static final int PAD = 3;

    /** How an editor reads the keyboard. */
    public interface IKeys {

        /** A key was pressed; true if it meant something. */
        boolean key(TtyEditor editor, int key, int modifiers);

        /** A character was typed; true if it went into the text. */
        boolean typed(TtyEditor editor, char c);

        /** What the line at the bottom says: the mode, the file, whatever the editor wants there. */
        String status(TtyEditor editor);
    }

    /** What an editor asked the terminal to do for it. */
    public interface IHost {

        /** Put the text back on the disk under that name. */
        void save(String path, String text);

        /** Give the terminal back; the editor is finished with it. */
        void quit();
    }

    private final String path;
    private final TextDocument doc = new TextDocument();
    private final IKeys keys;
    private final IHost host;

    private int scroll;
    private boolean dirty;
    private String message = "";

    /** Whatever the flavour of editor wants to remember between keys, such as a pending command. */
    private String pending = "";

    /** The colouring of the open file, read again only when the text changes. */
    private List<List<CodeRuns.Run>> cached = List.of();
    private String colouredText;

    public TtyEditor(final String path, final String text, final IKeys keys, final IHost host) {
        this.path = path;
        this.keys = keys;
        this.host = host;
        this.doc.setText(text);
    }

    /* What it is holding */

    /** The file it is editing. */
    public String path() {
        return this.path;
    }

    /** Just the name, which is what a status line shows. */
    public String name() {
        final int slash = this.path.lastIndexOf('/');
        return slash >= 0 && slash < this.path.length() - 1 ? this.path.substring(slash + 1) : this.path;
    }

    /** The text as it stands. */
    public String text() {
        return this.doc.text();
    }

    /** The document, for a flavour that moves the caret its own way. */
    public TextDocument document() {
        return this.doc;
    }

    /** Whether it has been changed since it was last written. */
    public boolean dirty() {
        return this.dirty;
    }

    /** Says the text changed, which is what makes a colouring stale and a quit refusable. */
    public void touched() {
        this.dirty = true;
    }

    /** What the editor wants said at the bottom, beside whatever the flavour puts there. */
    public String message() {
        return this.message;
    }

    /** Says something at the bottom. */
    public void say(final String text) {
        this.message = text == null ? "" : text;
    }

    /** Whatever the flavour is in the middle of, such as a half-typed command. */
    public String pending() {
        return this.pending;
    }

    /** Remembers what the flavour is in the middle of. */
    public void setPending(final String value) {
        this.pending = value == null ? "" : value;
    }

    /* What it can be asked to do */

    /** Puts the text back on the disk. */
    public void save() {
        this.host.save(this.path, this.doc.text());
        this.dirty = false;
        say("\"" + name() + "\" written");
    }

    /** Gives the terminal back. */
    public void quit() {
        this.host.quit();
    }

    /* Drawing */

    /**
     * Paints the editor over the terminal's glass.
     *
     * <p>The bottom row is the status line, the way every editor that runs at a prompt reserves one, and
     * everything above it is the file.
     */
    public void render(final GuiGraphics g, final Font font, final int x, final int y,
                       final int width, final int height, final InkPalette palette) {
        g.fill(x, y, x + width, y + height, palette.ground());
        final int rows = Math.max(1, (height - PAD - LINE_H) / LINE_H);
        followCaret(rows);

        final List<List<CodeRuns.Run>> runs = runs();
        Draw.pushScissor(g, x, y, x + width, y + height);
        int ry = y + PAD;
        for (int i = this.scroll; i < this.doc.lineCount() && i - this.scroll < rows; i++) {
            drawLine(g, font, this.doc.line(i), i < runs.size() ? runs.get(i) : List.of(),
                    x + PAD, ry, palette);
            if (i == this.doc.cursorLine()) {
                final String line = this.doc.line(i);
                final int col = Math.min(this.doc.cursorCol(), line.length());
                final int cx = x + PAD + font.width(line.substring(0, col));
                g.fill(cx, ry - 1, cx + font.width("m"), ry + LINE_H - 1, 0x66CDD6E2);
            }
            ry += LINE_H;
        }
        /*
         * The rows past the end of the file are marked, the way a terminal editor does, so the end of a
         * short file is not mistaken for a screen of blank lines that are really there.
         */
        for (int i = this.doc.lineCount() - this.scroll; i < rows; i++) {
            g.drawString(font, "~", x + PAD, y + PAD + i * LINE_H, palette.gutterText(), false);
        }
        Draw.popScissor(g);
        drawStatus(g, font, x, y + height - LINE_H, width, palette);
    }

    private void drawStatus(final GuiGraphics g, final Font font, final int x, final int y,
                            final int width, final InkPalette palette) {
        g.fill(x, y, x + width, y + LINE_H, palette.gutter());
        final String left = this.keys.status(this);
        g.drawString(font, font.plainSubstrByWidth(left, width - 60), x + PAD, y, palette.plain(), false);
        final String where = (this.doc.cursorLine() + 1) + "," + (this.doc.cursorCol() + 1);
        g.drawString(font, where, x + width - font.width(where) - PAD, y, palette.gutterText(), false);
    }

    private void drawLine(final GuiGraphics g, final Font font, final String line,
                          final List<CodeRuns.Run> runs, final int startX, final int textY,
                          final InkPalette palette) {
        if (runs.isEmpty()) {
            g.drawString(font, line, startX, textY, palette.plain(), false);
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
            g.drawString(font, piece, rx, textY, palette.of(run.ink()), false);
            rx += font.width(piece);
        }
    }

    /** The rows, coloured by whichever language claims the file, or plain when none does. */
    private List<List<CodeRuns.Run>> runs() {
        final String text = this.doc.text();
        if (text.equals(this.colouredText)) {
            return this.cached;
        }
        final List<String> lines = new ArrayList<>(this.doc.lineCount());
        for (int i = 0; i < this.doc.lineCount(); i++) {
            lines.add(this.doc.line(i));
        }
        final IProgrammingLanguage language = CodeWorkspace.languageOf(this.path);
        if (language == null) {
            this.cached = List.of();
        } else {
            final List<CodeRuns.Span> spans = new ArrayList<>();
            for (final IProgrammingLanguage.Token token : language.tokenize(text)) {
                spans.add(new CodeRuns.Span(token.line(), token.column(), token.length(),
                        switch (token.kind()) {
                            case KEYWORD -> CodeRuns.Ink.KEYWORD;
                            case NAME -> CodeRuns.Ink.NAME;
                            case TEXT -> CodeRuns.Ink.TEXT;
                            case NUMBER -> CodeRuns.Ink.NUMBER;
                            case COMMENT -> CodeRuns.Ink.COMMENT;
                            case SYMBOL -> CodeRuns.Ink.SYMBOL;
                        }));
            }
            this.cached = CodeRuns.byLine(lines, spans);
        }
        this.colouredText = text;
        return this.cached;
    }

    private void followCaret(final int rows) {
        if (this.doc.cursorLine() < this.scroll) {
            this.scroll = this.doc.cursorLine();
        } else if (this.doc.cursorLine() >= this.scroll + rows) {
            this.scroll = this.doc.cursorLine() - rows + 1;
        }
        this.scroll = Math.max(0, Math.min(Math.max(0, this.doc.lineCount() - rows), this.scroll));
    }

    /* Input */

    /** Hands a key to whichever flavour of editor this is. */
    public boolean keyPressed(final int key, final int modifiers) {
        return this.keys.key(this, key, modifiers);
    }

    /** Hands a character to it. */
    public boolean charTyped(final char c) {
        return this.keys.typed(this, c);
    }

    /** Moves the view without moving the caret, which is what a wheel does. */
    public boolean scrolled(final double delta) {
        this.scroll = Math.max(0, this.scroll - (int) Math.signum(delta) * 3);
        return true;
    }
}
