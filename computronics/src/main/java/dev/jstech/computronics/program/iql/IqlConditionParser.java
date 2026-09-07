/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program.iql;

import dev.jstech.computronics.program.iql.IIqlCondition.Op;
import dev.jstech.computronics.program.iql.IqlLexer.Token;
import dev.jstech.computronics.program.iql.IqlLexer.Type;
import java.util.List;
import java.util.Locale;

/**
 * Recursive-descent parser for the boolean tree behind a {@code WHERE} filter or an {@code IF} guard.
 * Pure logic, no Minecraft. It reads from a shared token list and cursor so the statement parser can
 * hand it the tokens after {@code WHERE}/{@code IF}, let it consume exactly the condition, and resume
 * where it stopped.
 *
 * <p>Precedence is the usual {@code NOT} &gt; {@code AND} &gt; {@code OR}; parentheses group. A leaf is
 * {@code field op value}, where {@code op} is a symbol ({@code = == != < > <= >=}) or a word
 * ({@code contains}/{@code has}/{@code like}). The parser stops at the first token that cannot extend
 * the condition (a trailing clause keyword such as {@code IF}/{@code ORDER}/{@code LIMIT}, or the end),
 * leaving the cursor on it.
 */
public final class IqlConditionParser {

    private final List<Token> tokens;
    private int pos;

    IqlConditionParser(final List<Token> tokens, final int start) {
        this.tokens = tokens;
        this.pos = start;
    }

    /** Where the cursor stopped, so the caller can resume after the condition. */
    int position() {
        return pos;
    }

    /** Lexes and parses a whole string as a single condition; errors if any token is left over. */
    public static IIqlCondition parse(final String text) {
        final List<Token> tokens = IqlLexer.lex(text);
        final IqlConditionParser parser = new IqlConditionParser(tokens, 0);
        final IIqlCondition condition = parser.parseCondition();
        if (parser.pos != tokens.size()) {
            throw new IllegalArgumentException("unexpected token: " + tokens.get(parser.pos).text());
        }
        return condition;
    }

    IIqlCondition parseCondition() {
        return parseOr();
    }

    private IIqlCondition parseOr() {
        IIqlCondition left = parseAnd();
        while (peekKeyword("OR")) {
            pos++;
            left = new IIqlCondition.Or(left, parseAnd());
        }
        return left;
    }

    private IIqlCondition parseAnd() {
        IIqlCondition left = parseNot();
        while (peekKeyword("AND")) {
            pos++;
            left = new IIqlCondition.And(left, parseNot());
        }
        return left;
    }

    private IIqlCondition parseNot() {
        if (peekKeyword("NOT")) {
            pos++;
            return new IIqlCondition.Not(parseNot());
        }
        return parsePrimary();
    }

    private IIqlCondition parsePrimary() {
        if (peekType(Type.LPAREN)) {
            pos++;
            final IIqlCondition inner = parseCondition();
            expectType(Type.RPAREN, "')'");
            return inner;
        }
        return parseComparison();
    }

    private IIqlCondition parseComparison() {
        final String field = expectType(Type.WORD, "a field name").text();
        final Op op = parseOperator();
        final String value = parseValue();
        return new IIqlCondition.Comparison(field, op, value);
    }

    private Op parseOperator() {
        if (pos >= tokens.size()) {
            throw new IllegalArgumentException("expected a comparison operator");
        }
        final Token token = tokens.get(pos);
        if (token.type() == Type.OPERATOR) {
            pos++;
            return symbolOperator(token.text());
        }
        if (token.type() == Type.WORD) {
            final Op word = wordOperator(token.text());
            if (word != null) {
                pos++;
                return word;
            }
        }
        throw new IllegalArgumentException("expected a comparison operator, got: " + token.text());
    }

    private static Op symbolOperator(final String symbol) {
        return switch (symbol) {
            case "=", "==" -> Op.EQ;
            case "!=" -> Op.NEQ;
            case "<" -> Op.LT;
            case ">" -> Op.GT;
            case "<=" -> Op.LTE;
            case ">=" -> Op.GTE;
            default -> throw new IllegalArgumentException("unknown operator: " + symbol);
        };
    }

    private static Op wordOperator(final String word) {
        return switch (word.toLowerCase(Locale.ROOT)) {
            case "contains" -> Op.CONTAINS;
            case "has" -> Op.HAS;
            case "like" -> Op.LIKE;
            default -> null;
        };
    }

    private String parseValue() {
        if (pos >= tokens.size()) {
            throw new IllegalArgumentException("expected a value");
        }
        final Token token = tokens.get(pos);
        if (token.type() == Type.WORD || token.type() == Type.NUMBER || token.type() == Type.STRING) {
            pos++;
            return token.text();
        }
        throw new IllegalArgumentException("expected a value, got: " + token.text());
    }

    private boolean peekKeyword(final String keyword) {
        return pos < tokens.size()
                && tokens.get(pos).type() == Type.WORD
                && tokens.get(pos).text().equalsIgnoreCase(keyword);
    }

    private boolean peekType(final Type type) {
        return pos < tokens.size() && tokens.get(pos).type() == type;
    }

    private Token expectType(final Type type, final String what) {
        if (pos >= tokens.size() || tokens.get(pos).type() != type) {
            throw new IllegalArgumentException("expected " + what);
        }
        return tokens.get(pos++);
    }
}
