/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.edit;

import java.util.ArrayList;
import java.util.List;

/**
 * Where the comments are in a piece of Cannon source.
 *
 * <p>The lexer swallows comments the way it swallows spaces, because nothing downstream of it cares
 * where they were. An editor is the one thing that does: it has to paint them, and it has to paint them
 * in text that does not compile, which is most of what it is ever handed. So the editor path finds them
 * here instead, over the raw text, and the compiler's own reading is left alone.
 *
 * <p>A block comment that runs over several lines comes back as one span per line, because a screen is
 * drawn a line at a time and an editor should never have to work out which part of a span is on the row
 * it is painting.
 */
public final class CommentSpans {

    /** A stretch of one line, counted the way the compiler counts: line and column from one. */
    public record Span(int line, int column, int length) {
    }

    private CommentSpans() {
    }

    /**
     * Every comment in {@code text}, in reading order.
     *
     * <p>Quoting is honoured, so a {@code //} inside a string is text and not the start of a comment;
     * an unterminated block comment runs to the end of the file, which is what the player sees while
     * they are still typing it.
     */
    public static List<Span> find(final String text) {
        final List<Span> spans = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return spans;
        }
        int line = 1;
        int column = 1;
        int i = 0;
        while (i < text.length()) {
            final char c = text.charAt(i);
            if (c == '\n') {
                line++;
                column = 1;
                i++;
            } else if (c == '"' || c == '\'') {
                final int after = skipLiteral(text, i, c);
                column += after - i;
                i = after;
            } else if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '/') {
                int end = i;
                while (end < text.length() && text.charAt(end) != '\n') {
                    end++;
                }
                spans.add(new Span(line, column, end - i));
                column += end - i;
                i = end;
            } else if (c == '/' && i + 1 < text.length() && text.charAt(i + 1) == '*') {
                int at = i + 2;
                int startColumn = column;
                int runStart = i;
                while (at < text.length()) {
                    if (text.charAt(at) == '*' && at + 1 < text.length() && text.charAt(at + 1) == '/') {
                        at += 2;
                        break;
                    }
                    if (text.charAt(at) == '\n') {
                        spans.add(new Span(line, startColumn, at - runStart));
                        line++;
                        startColumn = 1;
                        runStart = at + 1;
                    }
                    at++;
                }
                if (at > runStart) {
                    spans.add(new Span(line, startColumn, Math.min(at, text.length()) - runStart));
                }
                column = startColumn + (Math.min(at, text.length()) - runStart);
                i = at;
            } else {
                column++;
                i++;
            }
        }
        return spans;
    }

    /**
     * The index just past the literal that opens at {@code start}, or past the end of the line when the
     * quote is never closed. A literal never spans lines in this language, so a runaway quote costs the
     * rest of its line and no more.
     */
    private static int skipLiteral(final String text, final int start, final char quote) {
        int at = start + 1;
        while (at < text.length()) {
            final char c = text.charAt(at);
            if (c == '\n') {
                return at;
            }
            if (c == '\\' && at + 1 < text.length() && text.charAt(at + 1) != '\n') {
                at += 2;
                continue;
            }
            at++;
            if (c == quote) {
                return at;
            }
        }
        return at;
    }
}
