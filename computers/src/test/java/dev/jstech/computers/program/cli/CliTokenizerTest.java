/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.program.cli;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CliTokenizerTest {

    @Test
    void tokenize_splitsOnRunsOfWhitespace() {
        assertEquals(List.of("select", "64", "iron_ingot"),
                CliTokenizer.tokenize("  select   64\tiron_ingot "));
    }

    @Test
    void tokenize_keepsQuotedTextAsOneToken() {
        assertEquals(List.of("select", "4", "oak planks"),
                CliTokenizer.tokenize("select 4 \"oak planks\""));
    }

    @Test
    void tokenize_doubleQuotesPreserveApostrophesAndSpaces() {
        assertEquals(List.of("echo", "it's fine"),
                CliTokenizer.tokenize("echo \"it's fine\""));
    }

    @Test
    void tokenize_singleQuotesAlsoGroup() {
        assertEquals(List.of("find", "redstone block"),
                CliTokenizer.tokenize("find 'redstone block'"));
    }

    @Test
    void tokenize_emptyLineYieldsNoTokens() {
        assertTrue(CliTokenizer.tokenize("   ").isEmpty());
        assertTrue(CliTokenizer.tokenize("").isEmpty());
        assertTrue(CliTokenizer.tokenize(null).isEmpty());
    }
}
