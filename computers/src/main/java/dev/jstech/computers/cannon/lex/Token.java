/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.lex;

import java.util.Objects;

/**
 * One token, with where it was written and, for a literal, what it means.
 *
 * <p>{@code text} is what the player typed, so a diagnostic can quote it back exactly; {@code value}
 * is the literal already turned into a number, a string or a character, so nothing downstream parses
 * text a second time. Line and column are one-based.
 */
public record Token(TokenKind kind, String text, Object value, int line, int column) {

    public Token {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(text, "text");
    }

    /** A token that carries no value of its own: punctuation, a keyword or a name. */
    public static Token of(final TokenKind kind, final String text, final int line, final int column) {
        return new Token(kind, text, null, line, column);
    }

    /** Whether this token is of that kind. */
    public boolean is(final TokenKind other) {
        return this.kind == other;
    }

    /** How a diagnostic names this token: the exact spelling for anything a player wrote. */
    public String describe() {
        return this.kind == TokenKind.END_OF_FILE ? this.kind.describe() : "'" + this.text + "'";
    }
}
