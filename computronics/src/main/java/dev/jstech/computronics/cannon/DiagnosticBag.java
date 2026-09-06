/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Collects what the compiler has to say about one file, in the order the reader wants it: by
 * position, so the first complaint is about the first mistake.
 *
 * <p>A cap keeps a file of nonsense from producing thousands of messages. Once it is reached the bag
 * stops recording and says so once, which is what makes the console readable after a paste gone wrong.
 */
public final class DiagnosticBag {

    /** Past this many, further messages are almost always the same mistake echoing. */
    public static final int MAX_DIAGNOSTICS = 100;

    private final String file;
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private boolean capped;

    public DiagnosticBag(final String file) {
        this.file = file;
    }

    /** Records an error at that position; the compilation will not produce a program. */
    public void error(final int line, final int column, final CannonError error, final Object... arguments) {
        this.add(Diagnostic.Severity.ERROR, line, column, error, arguments);
    }

    /** Records a warning at that position; the compilation still produces a program. */
    public void warning(final int line, final int column, final CannonError error, final Object... arguments) {
        this.add(Diagnostic.Severity.WARNING, line, column, error, arguments);
    }

    private void add(final Diagnostic.Severity severity, final int line, final int column,
                     final CannonError error, final Object... arguments) {
        if (this.diagnostics.size() >= MAX_DIAGNOSTICS) {
            this.capped = true;
            return;
        }
        this.diagnostics.add(new Diagnostic(this.file, line, column, severity,
                error.code(), error.message(arguments)));
    }

    /** Whether anything recorded here stops the compilation. */
    public boolean hasErrors() {
        return this.diagnostics.stream().anyMatch(Diagnostic::isError);
    }

    /** Whether messages were dropped because the cap was reached. */
    public boolean wasCapped() {
        return this.capped;
    }

    /** How many messages are held. */
    public int size() {
        return this.diagnostics.size();
    }

    /** Everything recorded, earliest position first, in the order it was reported within a position. */
    public List<Diagnostic> sorted() {
        final List<Diagnostic> copy = new ArrayList<>(this.diagnostics);
        copy.sort(Comparator.comparingInt(Diagnostic::line).thenComparingInt(Diagnostic::column));
        return Collections.unmodifiableList(copy);
    }
}
