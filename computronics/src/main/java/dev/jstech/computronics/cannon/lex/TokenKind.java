/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.lex;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Every kind of token the language has.
 *
 * <p>The keyword set is closed on purpose: a word that is not here is an identifier, so a program
 * never stops compiling because a later version of the language claimed one of the player's names.
 * Each kind carries how it is written, which is what a diagnostic quotes back.
 */
public enum TokenKind {

    INT_LITERAL("an integer"),
    LONG_LITERAL("a long integer"),
    FLOAT_LITERAL("a float"),
    DOUBLE_LITERAL("a double"),
    STRING_LITERAL("a string"),
    CHAR_LITERAL("a character"),
    IDENTIFIER("a name"),

    CLASS("class", true),
    INTERFACE("interface", true),
    ENUM("enum", true),
    DELEGATE("delegate", true),
    EVENT("event", true),
    NEW("new", true),
    DISPOSE("dispose", true),
    THIS("this", true),
    BASE("base", true),
    STATIC("static", true),
    PUBLIC("public", true),
    PRIVATE("private", true),
    PROTECTED("protected", true),
    READONLY("readonly", true),
    IF("if", true),
    ELSE("else", true),
    WHILE("while", true),
    FOR("for", true),
    FOREACH("foreach", true),
    IN("in", true),
    OUT("out", true),
    DO("do", true),
    SWITCH("switch", true),
    CASE("case", true),
    DEFAULT("default", true),
    BREAK("break", true),
    CONTINUE("continue", true),
    RETURN("return", true),
    VOID("void", true),
    VAR("var", true),
    NULL("null", true),
    TRUE("true", true),
    FALSE("false", true),
    IS("is", true),
    AS("as", true),
    INT("int", true),
    LONG("long", true),
    FLOAT("float", true),
    DOUBLE("double", true),
    BOOL("bool", true),
    STRING("string", true),
    CHAR("char", true),
    OBJECT("object", true),

    LEFT_BRACE("{"),
    RIGHT_BRACE("}"),
    LEFT_PAREN("("),
    RIGHT_PAREN(")"),
    LEFT_BRACKET("["),
    RIGHT_BRACKET("]"),
    SEMICOLON(";"),
    COMMA(","),
    DOT("."),
    COLON(":"),
    QUESTION("?"),
    ARROW("=>"),

    ASSIGN("="),
    PLUS("+"),
    MINUS("-"),
    STAR("*"),
    SLASH("/"),
    PERCENT("%"),
    PLUS_PLUS("++"),
    MINUS_MINUS("--"),
    PLUS_ASSIGN("+="),
    MINUS_ASSIGN("-="),
    STAR_ASSIGN("*="),
    SLASH_ASSIGN("/="),
    PERCENT_ASSIGN("%="),
    AMPERSAND_ASSIGN("&="),
    PIPE_ASSIGN("|="),
    CARET_ASSIGN("^="),
    SHIFT_LEFT_ASSIGN("<<="),
    SHIFT_RIGHT_ASSIGN(">>="),
    EQUAL("=="),
    NOT_EQUAL("!="),
    LESS("<"),
    LESS_EQUAL("<="),
    GREATER(">"),
    GREATER_EQUAL(">="),
    AND_AND("&&"),
    OR_OR("||"),
    NOT("!"),
    AMPERSAND("&"),
    PIPE("|"),
    CARET("^"),
    TILDE("~"),
    SHIFT_LEFT("<<"),
    SHIFT_RIGHT(">>"),

    END_OF_FILE("the end of the file");

    private static final Map<String, TokenKind> KEYWORDS;

    static {
        final Map<String, TokenKind> keywords = new HashMap<>();
        for (final TokenKind kind : values()) {
            if (kind.keyword) {
                keywords.put(kind.text, kind);
            }
        }
        KEYWORDS = Collections.unmodifiableMap(keywords);
    }

    private final String text;
    private final boolean keyword;

    TokenKind(final String text) {
        this(text, false);
    }

    TokenKind(final String text, final boolean keyword) {
        this.text = text;
        this.keyword = keyword;
    }

    /** The kind of the reserved word spelled {@code word}, or {@code null} if it is a plain name. */
    public static TokenKind keyword(final String word) {
        return KEYWORDS.get(word);
    }

    /** How this kind is written, for the punctuation and the keywords; a description for the rest. */
    public String text() {
        return this.text;
    }

    /** Whether this kind is one of the language's reserved words. */
    public boolean isKeyword() {
        return this.keyword;
    }

    /** How a diagnostic names this kind: quoted when it has one spelling, plain when it is a class. */
    public String describe() {
        return this.keyword || !Character.isLetter(this.text.charAt(0)) ? "'" + this.text + "'" : this.text;
    }
}
