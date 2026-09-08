/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.program.cli;

/**
 * The visual weight of a console line. The shell and commands tag every line with one of these; the client maps them to the terminal palette, so the pure-logic side never mentions a colour value.
 */
public enum CliStyle {
    PLAIN,
    PROMPT,
    OK,
    ERROR,
    WARN,
    INFO,
    DIM,
    ACCENT,
    HEADER,
    /*
     * The terminal's extended palette (appended so wire ordinals stay stable): brand-tinted colors a
     * program can paint with, the way a real terminal exposes its 16-color set. screenfetch keys each
     * distribution's logo to one of these.
     */
    ORANGE,
    MAGENTA,
    BLUE,
    CYAN,
    PURPLE
}
