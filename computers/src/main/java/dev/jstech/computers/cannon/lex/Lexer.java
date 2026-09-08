/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.lex;

import dev.jstech.computers.cannon.CannonError;
import dev.jstech.computers.cannon.DiagnosticBag;
import dev.jstech.computers.cannon.SourceFile;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns source text into tokens.
 *
 * <p>It never throws and never stops early: a character it cannot read becomes a diagnostic and the
 * scan carries on, so one stray symbol does not hide the twenty real mistakes after it. The list it
 * returns always ends with the end-of-file token, which is what lets the parser look ahead without
 * checking bounds.
 */
public final class Lexer {

    private final SourceFile source;
    private final DiagnosticBag diagnostics;

    private int index;
    private int line = 1;
    private int column = 1;

    public Lexer(final SourceFile source, final DiagnosticBag diagnostics) {
        this.source = source;
        this.diagnostics = diagnostics;
    }

    /** Reads the whole file. The last token is always {@link TokenKind#END_OF_FILE}. */
    public List<Token> tokenize() {
        final List<Token> tokens = new ArrayList<>();
        while (true) {
            this.skipTrivia();
            if (this.index >= this.source.length()) {
                break;
            }
            final Token token = this.scanToken();
            if (token != null) {
                tokens.add(token);
            }
        }
        tokens.add(Token.of(TokenKind.END_OF_FILE, "", this.line, this.column));
        return tokens;
    }

    // Whitespace and both comment forms carry no meaning, so they are consumed between tokens.
    private void skipTrivia() {
        while (this.index < this.source.length()) {
            final char c = this.peek();
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\f' || c == 0x0B) {
                this.advance();
            } else if (c == '/' && this.peek(1) == '/') {
                while (this.index < this.source.length() && this.peek() != '\n') {
                    this.advance();
                }
            } else if (c == '/' && this.peek(1) == '*') {
                this.skipBlockComment();
            } else {
                return;
            }
        }
    }

    private void skipBlockComment() {
        final int startLine = this.line;
        final int startColumn = this.column;
        this.advance();
        this.advance();
        while (true) {
            if (this.index >= this.source.length()) {
                this.diagnostics.error(startLine, startColumn, CannonError.UNTERMINATED_COMMENT);
                return;
            }
            if (this.peek() == '*' && this.peek(1) == '/') {
                this.advance();
                this.advance();
                return;
            }
            this.advance();
        }
    }

    private Token scanToken() {
        final int startIndex = this.index;
        final int startLine = this.line;
        final int startColumn = this.column;
        final char c = this.peek();

        if (Character.isLetter(c) || c == '_') {
            return this.scanWord(startIndex, startLine, startColumn);
        }
        if (Character.isDigit(c)) {
            return this.scanNumber(startIndex, startLine, startColumn);
        }
        if (c == '"') {
            return this.scanString(startLine, startColumn);
        }
        if (c == '\'') {
            return this.scanChar(startLine, startColumn);
        }
        return this.scanOperator(startIndex, startLine, startColumn);
    }

    private Token scanWord(final int startIndex, final int startLine, final int startColumn) {
        while (this.index < this.source.length()
                && (Character.isLetterOrDigit(this.peek()) || this.peek() == '_')) {
            this.advance();
        }
        final String text = this.textFrom(startIndex);
        final TokenKind keyword = TokenKind.keyword(text);
        if (keyword == null) {
            return Token.of(TokenKind.IDENTIFIER, text, startLine, startColumn);
        }
        if (keyword == TokenKind.TRUE || keyword == TokenKind.FALSE) {
            return new Token(keyword, text, keyword == TokenKind.TRUE, startLine, startColumn);
        }
        return Token.of(keyword, text, startLine, startColumn);
    }

    /*
     * A number is digits, an optional fractional part, and an optional suffix naming its type. A
     * letter that is not one of the suffixes is part of the mistake, so it is consumed with it and
     * the whole run is quoted back: "12abc" reads better than "12" followed by a stray name.
     */
    private Token scanNumber(final int startIndex, final int startLine, final int startColumn) {
        while (this.index < this.source.length() && Character.isDigit(this.peek())) {
            this.advance();
        }
        boolean real = false;
        if (this.peek() == '.' && Character.isDigit(this.peek(1))) {
            real = true;
            this.advance();
            while (this.index < this.source.length() && Character.isDigit(this.peek())) {
                this.advance();
            }
        }
        final String digits = this.textFrom(startIndex);
        char suffix = '\0';
        if (Character.isLetter(this.peek())) {
            suffix = this.peek();
            this.advance();
        }
        if (Character.isLetterOrDigit(this.peek()) || this.peek() == '_' || (this.peek() == '.' && !real)) {
            while (this.index < this.source.length()
                    && (Character.isLetterOrDigit(this.peek()) || this.peek() == '_' || this.peek() == '.')) {
                this.advance();
            }
            this.diagnostics.error(startLine, startColumn, CannonError.MALFORMED_NUMBER, this.textFrom(startIndex));
            return new Token(TokenKind.INT_LITERAL, this.textFrom(startIndex), 0, startLine, startColumn);
        }
        final String text = this.textFrom(startIndex);
        return this.numberToken(digits, suffix, real, text, startLine, startColumn);
    }

    private Token numberToken(final String digits, final char suffix, final boolean real,
                              final String text, final int startLine, final int startColumn) {
        try {
            switch (suffix) {
                case 'f':
                case 'F':
                    return new Token(TokenKind.FLOAT_LITERAL, text, Float.parseFloat(digits), startLine, startColumn);
                case 'd':
                case 'D':
                    return new Token(TokenKind.DOUBLE_LITERAL, text, Double.parseDouble(digits),
                            startLine, startColumn);
                case 'l':
                case 'L':
                    if (real) {
                        break;
                    }
                    return new Token(TokenKind.LONG_LITERAL, text, Long.parseLong(digits), startLine, startColumn);
                case '\0':
                    return real
                            ? new Token(TokenKind.DOUBLE_LITERAL, text, Double.parseDouble(digits),
                                    startLine, startColumn)
                            : new Token(TokenKind.INT_LITERAL, text, Integer.parseInt(digits),
                                    startLine, startColumn);
                default:
                    break;
            }
        } catch (final NumberFormatException tooBigForItsType) {
            this.diagnostics.error(startLine, startColumn, CannonError.MALFORMED_NUMBER, text);
            return new Token(TokenKind.INT_LITERAL, text, 0, startLine, startColumn);
        }
        this.diagnostics.error(startLine, startColumn, CannonError.MALFORMED_NUMBER, text);
        return new Token(TokenKind.INT_LITERAL, text, 0, startLine, startColumn);
    }

    private Token scanString(final int startLine, final int startColumn) {
        this.advance();
        final StringBuilder value = new StringBuilder();
        while (true) {
            if (this.index >= this.source.length() || this.peek() == '\n') {
                this.diagnostics.error(startLine, startColumn, CannonError.UNTERMINATED_STRING);
                break;
            }
            final char c = this.peek();
            if (c == '"') {
                this.advance();
                break;
            }
            value.append(c == '\\' ? this.scanEscape() : this.advance());
        }
        final String text = value.toString();
        return new Token(TokenKind.STRING_LITERAL, text, text, startLine, startColumn);
    }

    private Token scanChar(final int startLine, final int startColumn) {
        this.advance();
        if (this.index >= this.source.length() || this.peek() == '\'' || this.peek() == '\n') {
            if (this.peek() == '\'') {
                this.advance();
            }
            this.diagnostics.error(startLine, startColumn, CannonError.INVALID_CHARACTER_LITERAL);
            return new Token(TokenKind.CHAR_LITERAL, "", '\0', startLine, startColumn);
        }
        final char value = this.peek() == '\\' ? this.scanEscape() : this.advance();
        if (this.peek() == '\'') {
            this.advance();
        } else {
            while (this.index < this.source.length() && this.peek() != '\'' && this.peek() != '\n') {
                this.advance();
            }
            if (this.peek() == '\'') {
                this.advance();
            }
            this.diagnostics.error(startLine, startColumn, CannonError.INVALID_CHARACTER_LITERAL);
        }
        return new Token(TokenKind.CHAR_LITERAL, String.valueOf(value), value, startLine, startColumn);
    }

    private char scanEscape() {
        final int escapeLine = this.line;
        final int escapeColumn = this.column;
        this.advance();
        final char c = this.advance();
        switch (c) {
            case 'n':
                return '\n';
            case 't':
                return '\t';
            case 'r':
                return '\r';
            case '0':
                return '\0';
            case '\\':
                return '\\';
            case '"':
                return '"';
            case '\'':
                return '\'';
            default:
                this.diagnostics.error(escapeLine, escapeColumn, CannonError.UNKNOWN_ESCAPE, String.valueOf(c));
                return c;
        }
    }

    // Longest match wins, so "<<=" is never read as "<<" followed by "=".
    private Token scanOperator(final int startIndex, final int startLine, final int startColumn) {
        final char c = this.advance();
        final TokenKind kind;
        switch (c) {
            case '{': kind = TokenKind.LEFT_BRACE; break;
            case '}': kind = TokenKind.RIGHT_BRACE; break;
            case '(': kind = TokenKind.LEFT_PAREN; break;
            case ')': kind = TokenKind.RIGHT_PAREN; break;
            case '[': kind = TokenKind.LEFT_BRACKET; break;
            case ']': kind = TokenKind.RIGHT_BRACKET; break;
            case ';': kind = TokenKind.SEMICOLON; break;
            case ',': kind = TokenKind.COMMA; break;
            case '.': kind = TokenKind.DOT; break;
            case ':': kind = TokenKind.COLON; break;
            case '?': kind = TokenKind.QUESTION; break;
            case '~': kind = TokenKind.TILDE; break;
            case '=': kind = this.take('=') ? TokenKind.EQUAL
                    : this.take('>') ? TokenKind.ARROW : TokenKind.ASSIGN; break;
            case '!': kind = this.take('=') ? TokenKind.NOT_EQUAL : TokenKind.NOT; break;
            case '+': kind = this.take('+') ? TokenKind.PLUS_PLUS
                    : this.take('=') ? TokenKind.PLUS_ASSIGN : TokenKind.PLUS; break;
            case '-': kind = this.take('-') ? TokenKind.MINUS_MINUS
                    : this.take('=') ? TokenKind.MINUS_ASSIGN : TokenKind.MINUS; break;
            case '*': kind = this.take('=') ? TokenKind.STAR_ASSIGN : TokenKind.STAR; break;
            case '/': kind = this.take('=') ? TokenKind.SLASH_ASSIGN : TokenKind.SLASH; break;
            case '%': kind = this.take('=') ? TokenKind.PERCENT_ASSIGN : TokenKind.PERCENT; break;
            case '^': kind = this.take('=') ? TokenKind.CARET_ASSIGN : TokenKind.CARET; break;
            case '&': kind = this.take('&') ? TokenKind.AND_AND
                    : this.take('=') ? TokenKind.AMPERSAND_ASSIGN : TokenKind.AMPERSAND; break;
            case '|': kind = this.take('|') ? TokenKind.OR_OR
                    : this.take('=') ? TokenKind.PIPE_ASSIGN : TokenKind.PIPE; break;
            case '<': kind = this.shiftOrCompare(true); break;
            case '>': kind = this.shiftOrCompare(false); break;
            default:
                this.diagnostics.error(startLine, startColumn, CannonError.UNEXPECTED_CHARACTER,
                        String.valueOf(c));
                return null;
        }
        return Token.of(kind, this.textFrom(startIndex), startLine, startColumn);
    }

    private TokenKind shiftOrCompare(final boolean left) {
        final char same = left ? '<' : '>';
        if (this.take(same)) {
            if (this.take('=')) {
                return left ? TokenKind.SHIFT_LEFT_ASSIGN : TokenKind.SHIFT_RIGHT_ASSIGN;
            }
            return left ? TokenKind.SHIFT_LEFT : TokenKind.SHIFT_RIGHT;
        }
        if (this.take('=')) {
            return left ? TokenKind.LESS_EQUAL : TokenKind.GREATER_EQUAL;
        }
        return left ? TokenKind.LESS : TokenKind.GREATER;
    }

    private boolean take(final char expected) {
        if (this.peek() == expected) {
            this.advance();
            return true;
        }
        return false;
    }

    private String textFrom(final int startIndex) {
        return this.source.text().substring(startIndex, this.index);
    }

    private char peek() {
        return this.source.charAt(this.index);
    }

    private char peek(final int ahead) {
        return this.source.charAt(this.index + ahead);
    }

    private char advance() {
        final char c = this.source.charAt(this.index);
        this.index++;
        if (c == '\n') {
            this.line++;
            this.column = 1;
        } else {
            this.column++;
        }
        return c;
    }
}
