/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.cli;

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
    HEADER
}
