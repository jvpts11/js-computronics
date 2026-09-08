/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.run;

/**
 * The end of a process that did not finish on its own.
 *
 * <p>The language has no exceptions, so nothing here can be caught: a halt stops the process where
 * it stands, and the message is what the player reads in its console. That is the whole of the error
 * handling a v1 program has, which is why the message has to say enough to act on.
 */
public final class Halt extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** What went wrong, in the words the console uses. */
    public enum Reason {
        DIVIDE_BY_ZERO("divided by zero"),
        NO_OBJECT("reached into nothing"),
        USE_AFTER_DISPOSE("used after dispose"),
        OUT_OF_MEMORY("out of memory"),
        BAD_CAST("not of that type"),
        OUT_OF_RANGE("outside the collection"),
        NO_SUCH_MEMBER("no such member"),
        NO_NETWORK("no network");

        private final String text;

        Reason(final String text) {
            this.text = text;
        }

        /** How the reason reads on its own. */
        public String text() {
            return this.text;
        }
    }

    private final transient Reason reason;
    private final int line;

    public Halt(final Reason reason, final int line, final String message) {
        super(message);
        this.reason = reason;
        this.line = line;
    }

    /** Why the process stopped. */
    public Reason reason() {
        return this.reason;
    }

    /** The line of the assembly it stopped on, or 0 when it was not inside one. */
    public int line() {
        return this.line;
    }
}
