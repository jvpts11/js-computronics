/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.program.iql;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.jstech.computers.program.iql.IqlLexer.Token;
import dev.jstech.computers.program.iql.IqlLexer.Type;
import java.util.List;
import org.junit.jupiter.api.Test;

class IqlLexerTest {

    @Test
    void lex_splitsWordsAndNumbers() {
        final List<Token> tokens = IqlLexer.lex("SELECT 1000 cobblestone");
        assertEquals(List.of(
                new Token(Type.WORD, "SELECT"),
                new Token(Type.NUMBER, "1000"),
                new Token(Type.WORD, "cobblestone")), tokens);
    }

    @Test
    void lex_separatesOperatorGluedToOperands() {
        final List<Token> tokens = IqlLexer.lex("qty<100");
        assertEquals(List.of(
                new Token(Type.WORD, "qty"),
                new Token(Type.OPERATOR, "<"),
                new Token(Type.NUMBER, "100")), tokens);
    }

    @Test
    void lex_readsMultiCharOperators() {
        assertEquals(new Token(Type.OPERATOR, "<="), IqlLexer.lex("qty <= 5").get(1));
        assertEquals(new Token(Type.OPERATOR, "!="), IqlLexer.lex("qty != 5").get(1));
        assertEquals(new Token(Type.OPERATOR, ">="), IqlLexer.lex("qty >= 5").get(1));
        assertEquals(new Token(Type.OPERATOR, "=="), IqlLexer.lex("qty == 5").get(1));
    }

    @Test
    void lex_keepsFunctionCallAsOneWord() {
        final List<Token> tokens = IqlLexer.lex("qty(cobblestone) < 1");
        assertEquals(List.of(
                new Token(Type.WORD, "qty(cobblestone)"),
                new Token(Type.OPERATOR, "<"),
                new Token(Type.NUMBER, "1")), tokens);
    }

    @Test
    void lex_treatsBareParensAsStructural() {
        final List<Token> tokens = IqlLexer.lex("(qty < 1)");
        assertEquals(List.of(
                new Token(Type.LPAREN, "("),
                new Token(Type.WORD, "qty"),
                new Token(Type.OPERATOR, "<"),
                new Token(Type.NUMBER, "1"),
                new Token(Type.RPAREN, ")")), tokens);
    }

    @Test
    void lex_readsQuotedStringWithSpaces() {
        final List<Token> tokens = IqlLexer.lex("name contains \"rare ore\"");
        assertEquals(List.of(
                new Token(Type.WORD, "name"),
                new Token(Type.WORD, "contains"),
                new Token(Type.STRING, "rare ore")), tokens);
    }

    @Test
    void lex_classifiesPercentageAsNumber() {
        assertEquals(new Token(Type.NUMBER, "50%"), IqlLexer.lex("durability > 50%").get(2));
    }

    @Test
    void lex_keepsItemWildcardAsWord() {
        assertEquals(List.of(new Token(Type.WORD, "*")), IqlLexer.lex("*"));
    }

    @Test
    void lex_emptyInputYieldsNoTokens() {
        assertEquals(List.of(), IqlLexer.lex("   "));
    }
}
