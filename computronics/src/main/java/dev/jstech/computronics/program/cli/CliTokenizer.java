/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a command line into tokens on whitespace, honouring single and double quotes so an item name with spaces can be passed as one argument ({@code select 4 "oak planks"}). Pure and side-effect free.
 */
public final class CliTokenizer {

    private CliTokenizer() {
    }

    public static List<String> tokenize(final String line) {
        final List<String> tokens = new ArrayList<>();
        if (line == null) {
            return tokens;
        }
        final StringBuilder current = new StringBuilder();
        char quote = 0;
        boolean inToken = false;
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else {
                    current.append(c);
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
                inToken = true;
            } else if (Character.isWhitespace(c)) {
                if (inToken) {
                    tokens.add(current.toString());
                    current.setLength(0);
                    inToken = false;
                }
            } else {
                current.append(c);
                inToken = true;
            }
        }
        if (inToken) {
            tokens.add(current.toString());
        }
        return tokens;
    }
}
