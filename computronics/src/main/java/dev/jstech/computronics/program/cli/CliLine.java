/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.cli;

/**
 * One line of console output: its text and the style that colours it. Immutable, so a command's output can be streamed to the client and replayed without surprises.
 */
public record CliLine(String text, CliStyle style) {

    public CliLine {
        if (text == null) {
            text = "";
        }
        if (style == null) {
            style = CliStyle.PLAIN;
        }
    }

    public static CliLine plain(final String text) {
        return new CliLine(text, CliStyle.PLAIN);
    }
}
