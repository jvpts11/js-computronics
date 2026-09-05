/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.program.iql;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits IQL source text into tokens for the parser. Pure logic, no Minecraft, so it is unit-tested
 * directly.
 *
 * <p>It does more than split on whitespace: it isolates the structural pieces the grammar needs even
 * when they are glued to their operands.
 * <ul>
 *   <li>Comparison operators ({@code = != < > <= >=}) split off, so {@code qty<100} reads as three
 *       tokens.</li>
 *   <li>Grouping parentheses ({@code (a OR b)}) become their own tokens.</li>
 *   <li>A parenthesis glued to the right of an identifier is a function call and stays inside the word,
 *       so {@code qty(cobblestone)} is one token and is never confused with grouping.</li>
 *   <li>Double-quoted runs become a single string token, letting a value carry spaces.</li>
 * </ul>
 */
final class IqlLexer {

    private IqlLexer() {
    }

    /** The lexical category of a token; the parser dispatches on it. */
    enum Type { WORD, NUMBER, OPERATOR, STRING, LPAREN, RPAREN }

    /** One lexical unit: its category and the exact source text it covers. */
    record Token(Type type, String text) {
    }

    static List<Token> lex(final String input) {
        final List<Token> tokens = new ArrayList<>();
        final int n = input.length();
        int i = 0;
        while (i < n) {
            final char c = input.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
                continue;
            }
            if (c == '"') {
                final int start = i + 1;
                int j = start;
                while (j < n && input.charAt(j) != '"') {
                    j++;
                }
                tokens.add(new Token(Type.STRING, input.substring(start, j)));
                i = (j < n) ? j + 1 : j; // step past the closing quote when present
                continue;
            }
            if (c == '(') {
                tokens.add(new Token(Type.LPAREN, "("));
                i++;
                continue;
            }
            if (c == ')') {
                tokens.add(new Token(Type.RPAREN, ")"));
                i++;
                continue;
            }
            if (isOperatorChar(c)) {
                final int start = i;
                while (i < n && isOperatorChar(input.charAt(i))) {
                    i++;
                }
                tokens.add(new Token(Type.OPERATOR, input.substring(start, i)));
                continue;
            }
            i = readWord(input, i, tokens);
        }
        return tokens;
    }

    /**
     * Reads a word, number, or function call starting at {@code start}. A '(' that follows word
     * characters opens a function call and is consumed (balanced) into the word; a '(' with no word
     * before it is left for the caller to read as a structural {@code LPAREN}.
     */
    private static int readWord(final String input, final int start, final List<Token> tokens) {
        final int n = input.length();
        final StringBuilder sb = new StringBuilder();
        int i = start;
        while (i < n) {
            final char ch = input.charAt(i);
            if (Character.isWhitespace(ch) || isOperatorChar(ch) || ch == ')' || ch == '"') {
                break;
            }
            if (ch == '(') {
                if (sb.length() == 0) {
                    break; // structural '(' — the main loop reads it as LPAREN
                }
                i = consumeBalancedCall(input, i, sb);
                break; // a function call ends the word
            }
            sb.append(ch);
            i++;
        }
        if (sb.length() > 0) {
            tokens.add(new Token(classify(sb.toString()), sb.toString()));
        }
        return i;
    }

    /** Appends a balanced {@code (...)} run to {@code sb}, returning the index just past it. */
    private static int consumeBalancedCall(final String input, final int start, final StringBuilder sb) {
        final int n = input.length();
        int depth = 0;
        int i = start;
        while (i < n) {
            final char cc = input.charAt(i);
            sb.append(cc);
            i++;
            if (cc == '(') {
                depth++;
            } else if (cc == ')') {
                depth--;
                if (depth == 0) {
                    break;
                }
            }
        }
        return i;
    }

    private static boolean isOperatorChar(final char c) {
        return c == '=' || c == '!' || c == '<' || c == '>';
    }

    private static Type classify(final String text) {
        // A bare integer (optionally a percentage, e.g. "50%") is a number; everything else — item ids,
        // keywords, '*', function calls — is a word the parser interprets by position.
        return text.matches("-?\\d+%?") ? Type.NUMBER : Type.WORD;
    }
}
