/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.computers.cannon.lex.Lexer;
import dev.jstech.computers.cannon.lex.Token;
import dev.jstech.computers.cannon.lex.TokenKind;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LexerTest {

    private DiagnosticBag bag;

    @BeforeEach
    void setUp() {
        this.bag = new DiagnosticBag("Test.can");
    }

    private List<Token> scan(final String source) {
        return new Lexer(new SourceFile("Test.can", source), this.bag).tokenize();
    }

    private static List<TokenKind> kinds(final List<Token> tokens) {
        return tokens.stream().map(Token::kind).toList();
    }

    @Test
    void tokenize_alwaysEndsWithEndOfFile() {
        assertEquals(List.of(TokenKind.END_OF_FILE), kinds(this.scan("")));
        assertEquals(TokenKind.END_OF_FILE, this.scan("class C { }").getLast().kind());
    }

    @Test
    void tokenize_separatesKeywordsFromNames() {
        final List<Token> tokens = this.scan("class Monitor : IScript");
        assertEquals(List.of(TokenKind.CLASS, TokenKind.IDENTIFIER, TokenKind.COLON,
                TokenKind.IDENTIFIER, TokenKind.END_OF_FILE), kinds(tokens));
        assertEquals("Monitor", tokens.get(1).text());
    }

    @Test
    void tokenize_treatsAWordThatOnlyContainsAKeywordAsAName() {
        final List<Token> tokens = this.scan("classroom intern");
        assertEquals(List.of(TokenKind.IDENTIFIER, TokenKind.IDENTIFIER, TokenKind.END_OF_FILE), kinds(tokens));
    }

    @Test
    void tokenize_readsEachNumberFormAsItsOwnType() {
        final List<Token> tokens = this.scan("42 42L 1.5 1.5f 2.0d");
        assertEquals(List.of(TokenKind.INT_LITERAL, TokenKind.LONG_LITERAL, TokenKind.DOUBLE_LITERAL,
                TokenKind.FLOAT_LITERAL, TokenKind.DOUBLE_LITERAL, TokenKind.END_OF_FILE), kinds(tokens));
        assertEquals(42, tokens.get(0).value());
        assertEquals(42L, tokens.get(1).value());
        assertEquals(1.5d, tokens.get(2).value());
        assertEquals(1.5f, tokens.get(3).value());
        assertEquals(2.0d, tokens.get(4).value());
        assertFalse(this.bag.hasErrors());
    }

    @Test
    void tokenize_reportsANumberItCannotRead() {
        final List<Token> tokens = this.scan("12abc");
        assertEquals(TokenKind.INT_LITERAL, tokens.getFirst().kind());
        assertTrue(this.bag.hasErrors());
        assertEquals("C1004", this.bag.sorted().getFirst().code());
    }

    @Test
    void tokenize_reportsANumberTooBigForItsType() {
        this.scan("99999999999");
        assertTrue(this.bag.hasErrors());
        assertEquals("C1004", this.bag.sorted().getFirst().code());
    }

    @Test
    void tokenize_decodesStringEscapes() {
        final List<Token> tokens = this.scan("\"a\\nb\\\\c\\\"d\"");
        assertEquals(TokenKind.STRING_LITERAL, tokens.getFirst().kind());
        assertEquals("a\nb\\c\"d", tokens.getFirst().value());
        assertFalse(this.bag.hasErrors());
    }

    @Test
    void tokenize_reportsAnUnknownEscape() {
        this.scan("\"a\\qb\"");
        assertEquals("C1006", this.bag.sorted().getFirst().code());
    }

    @Test
    void tokenize_reportsAStringThatRunsPastItsLine() {
        final List<Token> tokens = this.scan("\"unclosed\nint x;");
        assertEquals("C1001", this.bag.sorted().getFirst().code());
        assertEquals(TokenKind.INT, tokens.get(1).kind());
    }

    @Test
    void tokenize_readsCharacterLiteralsAndReportsTheBadOnes() {
        assertEquals('a', this.scan("'a'").getFirst().value());
        assertEquals('\n', this.scan("'\\n'").getFirst().value());
        assertFalse(this.bag.hasErrors());
        this.scan("'ab'");
        assertEquals("C1005", this.bag.sorted().getFirst().code());
    }

    @Test
    void tokenize_skipsBothCommentForms() {
        final List<Token> tokens = this.scan("int /* held */ x; // the rest of the line\nint y;");
        assertEquals(List.of(TokenKind.INT, TokenKind.IDENTIFIER, TokenKind.SEMICOLON,
                TokenKind.INT, TokenKind.IDENTIFIER, TokenKind.SEMICOLON, TokenKind.END_OF_FILE), kinds(tokens));
        assertFalse(this.bag.hasErrors());
    }

    @Test
    void tokenize_reportsACommentThatWasNeverClosed() {
        this.scan("int x; /* on and on");
        assertEquals("C1002", this.bag.sorted().getFirst().code());
    }

    @Test
    void tokenize_takesTheLongestOperator() {
        final List<Token> tokens = this.scan(">>= >> >= > <<= << <= < == => = ++ += + && & || |");
        assertEquals(List.of(TokenKind.SHIFT_RIGHT_ASSIGN, TokenKind.SHIFT_RIGHT, TokenKind.GREATER_EQUAL,
                TokenKind.GREATER, TokenKind.SHIFT_LEFT_ASSIGN, TokenKind.SHIFT_LEFT, TokenKind.LESS_EQUAL,
                TokenKind.LESS, TokenKind.EQUAL, TokenKind.ARROW, TokenKind.ASSIGN, TokenKind.PLUS_PLUS,
                TokenKind.PLUS_ASSIGN, TokenKind.PLUS, TokenKind.AND_AND, TokenKind.AMPERSAND,
                TokenKind.OR_OR, TokenKind.PIPE, TokenKind.END_OF_FILE), kinds(tokens));
    }

    @Test
    void tokenize_countsLinesAndColumnsFromOne() {
        final List<Token> tokens = this.scan("class C\n{\n    int x;\n}");
        assertEquals(1, tokens.getFirst().line());
        assertEquals(1, tokens.getFirst().column());
        assertEquals(7, tokens.get(1).column());
        assertEquals(2, tokens.get(2).line());
        assertEquals(3, tokens.get(3).line());
        assertEquals(5, tokens.get(3).column());
    }

    @Test
    void tokenize_reportsAStrayCharacterAndCarriesOn() {
        final List<Token> tokens = this.scan("int $ x;");
        assertEquals("C1003", this.bag.sorted().getFirst().code());
        assertEquals(List.of(TokenKind.INT, TokenKind.IDENTIFIER, TokenKind.SEMICOLON,
                TokenKind.END_OF_FILE), kinds(tokens));
    }

    @Test
    void tokenize_readsAnInterpolatedStringIntoTextAndHoles() {
        final List<Token> tokens = this.scan("$\"Total: {count} of {a + b}!\"");
        assertEquals(TokenKind.INTERPOLATED_STRING, tokens.getFirst().kind());
        @SuppressWarnings("unchecked")
        final List<Object> parts = (List<Object>) tokens.getFirst().value();
        assertEquals(5, parts.size());
        assertEquals("Total: ", parts.get(0));
        assertEquals(new Lexer.Hole("count", 1, 11), parts.get(1));
        assertEquals(" of ", parts.get(2));
        assertEquals(new Lexer.Hole("a + b", 1, 22), parts.get(3));
        assertEquals("!", parts.get(4));
        assertEquals(List.of(new Lexer.Hole("x", 1, 4)), this.scan("$\"{x}\"").getFirst().value());
        assertEquals(List.of(""), this.scan("$\"\"").getFirst().value());
        assertEquals(List.of(), this.bag.sorted());
    }

    @Test
    void tokenize_treatsDoubledBracesInAnInterpolatedStringAsText() {
        final List<Token> tokens = this.scan("$\"{{x}} = {x}\"");
        @SuppressWarnings("unchecked")
        final List<Object> parts = (List<Object>) tokens.getFirst().value();
        assertEquals(List.of("{x} = ", new Lexer.Hole("x", 1, 12)), parts);
    }

    @Test
    void tokenize_readsNamespaceAndUsingAsKeywords() {
        assertEquals(List.of(TokenKind.USING, TokenKind.IDENTIFIER, TokenKind.SEMICOLON, TokenKind.NAMESPACE,
                TokenKind.IDENTIFIER, TokenKind.DOT, TokenKind.IDENTIFIER, TokenKind.SEMICOLON, TokenKind.END_OF_FILE),
                kinds(this.scan("using Tools; namespace Base.Lab;")));
    }

    @Test
    void tokenize_complainsOfAnInterpolatedStringThatNeverCloses() {
        this.scan("$\"open {x");
        assertEquals("C1001", this.bag.sorted().getFirst().code());
    }
}
