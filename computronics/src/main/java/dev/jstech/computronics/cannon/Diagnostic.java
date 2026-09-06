/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon;

import java.util.Objects;

/**
 * One message from the compiler about one place in the source.
 *
 * <p>Line and column are both one-based, because they are read by a person counting lines in an
 * editor, not by a machine indexing an array.
 */
public record Diagnostic(String file, int line, int column, Severity severity, String code, String message) {

    /** How much a diagnostic matters: a program with an error does not compile, one with a warning does. */
    public enum Severity {
        ERROR("error"),
        WARNING("warning");

        private final String label;

        Severity(final String label) {
            this.label = label;
        }

        /** The word this severity shows as in a diagnostic line. */
        public String label() {
            return this.label;
        }
    }

    public Diagnostic {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(message, "message");
        if (line < 1 || column < 1) {
            throw new IllegalArgumentException("line and column are one-based: " + line + "," + column);
        }
    }

    /** Whether this diagnostic stops the compilation. */
    public boolean isError() {
        return this.severity == Severity.ERROR;
    }

    /**
     * The one-line form the compiler prints and the IDEs parse, for example
     * {@code Monitor.can(12,9): error C2001: expected ';' but found '}'}.
     */
    public String format() {
        return this.file + "(" + this.line + "," + this.column + "): "
                + this.severity.label() + " " + this.code + ": " + this.message;
    }

    @Override
    public String toString() {
        return this.format();
    }
}
