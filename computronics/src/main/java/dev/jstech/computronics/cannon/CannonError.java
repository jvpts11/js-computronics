/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon;

/**
 * Every message the front end can produce, with the code a player quotes when asking for help.
 *
 * <p>The numbering is by stage, so a code says where the compiler gave up: C1xxx while reading the
 * characters, C2xxx while reading the grammar. Later stages take the ranges above them.
 */
public enum CannonError {

    UNTERMINATED_STRING("C1001", "the string was never closed before the end of the line"),
    UNTERMINATED_COMMENT("C1002", "the comment was never closed before the end of the file"),
    UNEXPECTED_CHARACTER("C1003", "'%s' does not begin anything the language knows"),
    MALFORMED_NUMBER("C1004", "'%s' is not a number the language can read"),
    INVALID_CHARACTER_LITERAL("C1005", "a character literal holds exactly one character"),
    UNKNOWN_ESCAPE("C1006", "'\\%s' is not an escape the language knows"),

    EXPECTED_TOKEN("C2001", "expected %s but found %s"),
    EXPECTED_TYPE("C2002", "expected a type but found %s"),
    EXPECTED_EXPRESSION("C2003", "expected an expression but found %s"),
    EXPECTED_MEMBER("C2004", "expected a field, a method, a property or an event but found %s"),
    EXPECTED_TYPE_DECLARATION("C2005", "expected a class, an interface, an enum or a delegate but found %s"),
    NOT_A_STATEMENT("C2006", "only a call, an assignment, an increment, a decrement or a new object "
            + "can be used as a statement"),
    DUPLICATE_MODIFIER("C2007", "'%s' was given twice"),
    INVALID_ASSIGNMENT_TARGET("C2008", "the left side of an assignment must be a variable, a field, "
            + "a property or an element");

    private final String code;
    private final String template;

    CannonError(final String code, final String template) {
        this.code = code;
        this.template = template;
    }

    /** The code as it appears in a diagnostic, for example {@code C2001}. */
    public String code() {
        return this.code;
    }

    /** The message with its placeholders filled in. */
    public String message(final Object... arguments) {
        return arguments.length == 0 ? this.template : String.format(this.template, arguments);
    }
}
