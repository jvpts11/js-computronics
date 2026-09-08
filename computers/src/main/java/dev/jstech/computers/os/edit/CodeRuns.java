/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.os.edit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns the pieces a language says its source is made of into stretches an editor can paint, one row at
 * a time.
 *
 * <p>A language hands back pieces scattered over the whole file, counted in lines and columns, and says
 * nothing about the spaces between them. A screen is drawn a row at a time and every character on the
 * row needs a colour, so the work of clipping a piece to its row, filling the gaps and putting the
 * result in reading order happens once, here, where it can be tested. Every editor in the mod draws
 * through this, and so does any editor an addon writes for its own language.
 *
 * <p>{@link Ink} says the same things as the language registry's own {@code Kind}. It is declared again
 * rather than reused because that one is on a contract that reaches into the world, and this arithmetic
 * is worth keeping free of the game so it can be exercised without one.
 */
public final class CodeRuns {

    /** What a stretch of source is, as far as painting it goes. */
    public enum Ink {
        /** Anything the language did not claim: spaces, and whatever it failed to read. */
        PLAIN,
        KEYWORD,
        NAME,
        TEXT,
        NUMBER,
        COMMENT,
        SYMBOL
    }

    /** A piece as the language reports it: line and column from one, length in characters. */
    public record Span(int line, int column, int length, Ink ink) {
    }

    /** A stretch of one row, ready to paint: start from zero, length in characters. */
    public record Run(int start, int length, Ink ink) {
    }

    private CodeRuns() {
    }

    /**
     * The runs of every row of {@code lines}, in order, each row covered end to end with no gaps and no
     * overlaps.
     *
     * <p>A span that reaches past the end of its row is cut at the end, and a span on a row that does
     * not exist is dropped. Where two spans want the same characters the one that starts earlier keeps
     * them and the later one colours only what is left over; on a tie the longer one goes first. An
     * empty row comes back as an empty list rather than a run of nothing, so a caller drawing it has
     * nothing to do.
     */
    public static List<List<Run>> byLine(final List<String> lines, final List<Span> spans) {
        final List<List<Span>> perLine = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            perLine.add(new ArrayList<>());
        }
        for (final Span span : spans) {
            final int index = span.line() - 1;
            if (index < 0 || index >= lines.size() || span.length() <= 0 || span.column() < 1) {
                continue;
            }
            perLine.get(index).add(span);
        }

        final List<List<Run>> out = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            out.add(runsOf(lines.get(i).length(), perLine.get(i)));
        }
        return out;
    }

    /** The runs of one row of {@code width} characters, given the spans that claim parts of it. */
    private static List<Run> runsOf(final int width, final List<Span> spans) {
        final List<Run> runs = new ArrayList<>();
        if (width <= 0) {
            return runs;
        }
        spans.sort(Comparator.comparingInt(Span::column).thenComparing(Comparator.comparingInt(Span::length).reversed()));

        int at = 0;
        for (final Span span : spans) {
            final int start = span.column() - 1;
            final int end = Math.min(width, start + span.length());
            if (start >= width || end <= at) {
                continue;
            }
            if (start > at) {
                runs.add(new Run(at, start - at, Ink.PLAIN));
                at = start;
            }
            if (end > at) {
                runs.add(new Run(at, end - at, span.ink()));
                at = end;
            }
        }
        if (at < width) {
            runs.add(new Run(at, width - at, Ink.PLAIN));
        }
        return runs;
    }
}
