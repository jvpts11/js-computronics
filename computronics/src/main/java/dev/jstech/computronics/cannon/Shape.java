/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon;

/**
 * What kind of program a listing is.
 *
 * <p>The two are as different as a program and a service are on any real machine, and the difference is
 * decided by what the source says rather than by how it is run: a class that implements the script
 * interface is one, a class with a static entry method is the other.
 */
public enum Shape {

    /**
     * A program that runs at a terminal.
     *
     * <p>It starts at its entry method, holds the prompt while it runs, writes what it prints to the
     * terminal that started it, and is gone when it returns.
     */
    CONSOLE("console"),

    /**
     * A program that stays up.
     *
     * <p>It is set up once, called again every tick for as long as the machine is on, told when it is
     * being stopped, and comes back with the machine after a reload.
     */
    SCRIPT("script");

    /** The word the assembly writes on its start line. */
    private final String written;

    Shape(final String written) {
        this.written = written;
    }

    /** How it is written in a listing. */
    public String written() {
        return this.written;
    }

    /** The shape that word names, or {@link #SCRIPT} for anything unrecognised. */
    public static Shape of(final String written) {
        return CONSOLE.written.equals(written) ? CONSOLE : SCRIPT;
    }
}
