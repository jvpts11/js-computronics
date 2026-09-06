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
 * One unit of source text handed to the compiler, with the name it is reported under.
 *
 * <p>The name is what a player sees at the head of every diagnostic, so it is the file name as the
 * computer's own shell would print it ({@code Monitor.can}), not a path from the host machine.
 */
public record SourceFile(String name, String text) {

    public SourceFile {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(text, "text");
    }

    /** The number of characters in the source. */
    public int length() {
        return this.text.length();
    }

    /**
     * The character at {@code index}, or {@code '\0'} past the end, so a scanner can look ahead
     * without guarding every read.
     */
    public char charAt(final int index) {
        return index >= 0 && index < this.text.length() ? this.text.charAt(index) : '\0';
    }
}
