/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.program.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * The buffer a command writes its result into. Style helpers keep command code terse and consistent ({@code out.ok(...)}, {@code out.error(...)}), and {@link #row} formats a two-column line so listings line up in the monospace console.
 */
public final class CliOutput {

    private final List<CliLine> lines = new ArrayList<>();
    private final int width;

    public CliOutput() {
        this(52);
    }

    /**
     * @param width the console's character width, used to right-align the value column in {@link #row}
     */
    public CliOutput(final int width) {
        this.width = Math.max(16, width);
    }

    public void line(final String text) {
        lines.add(new CliLine(text, CliStyle.PLAIN));
    }

    public void styled(final String text, final CliStyle style) {
        lines.add(new CliLine(text, style));
    }

    public void ok(final String text) {
        styled(text, CliStyle.OK);
    }

    public void error(final String text) {
        styled(text, CliStyle.ERROR);
    }

    public void warn(final String text) {
        styled(text, CliStyle.WARN);
    }

    public void info(final String text) {
        styled(text, CliStyle.INFO);
    }

    public void dim(final String text) {
        styled(text, CliStyle.DIM);
    }

    public void accent(final String text) {
        styled(text, CliStyle.ACCENT);
    }

    public void header(final String text) {
        styled(text, CliStyle.HEADER);
    }

    public void blank() {
        line("");
    }

    /**
     * A left label and a right value packed onto one line, the value pushed to the console's right
     * edge with dots filling the gap, so columns in a listing line up without a real table widget.
     */
    public void row(final String label, final String value) {
        final int gap = width - label.length() - value.length();
        final StringBuilder sb = new StringBuilder(label);
        if (gap >= 2) {
            sb.append(' ');
            sb.append(".".repeat(gap - 2));
            sb.append(' ');
        } else {
            sb.append("  ");
        }
        sb.append(value);
        line(sb.toString());
    }

    /**
     * A name and what it does, in two columns: the dots run from the name up to {@code column}, so every
     * description begins in the same place however long the names are.
     *
     * <p>This is not {@link #row}, and the difference matters. A row pushes its value to the right edge,
     * which is right for a figure being read off a listing and wrong for a list of commands: a long
     * description leaves no room for dots at all, so some lines get them and some do not, and each
     * description starts somewhere different. Lining the descriptions up is the whole job here.
     */
    public void entry(final String name, final String description, final int column) {
        final StringBuilder text = new StringBuilder(name);
        if (name.length() + 2 <= column) {
            text.append(' ');
            text.append(".".repeat(column - name.length() - 2));
            text.append(' ');
        } else {
            // A name past the column keeps a single space, so it reads as one entry rather than two words.
            text.append(' ');
        }
        text.append(description);
        line(text.toString());
    }

    public List<CliLine> lines() {
        return List.copyOf(lines);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }
}
