/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.program;

import java.util.Locale;

/**
 * A pure recursive-descent evaluator for the Scientific Calculator. It parses and evaluates an infix
 * expression with the usual precedence: {@code + -} (lowest), {@code * / %}, unary {@code + -}, {@code ^}
 * (right-associative power), then postfix {@code !} (factorial) and primaries. Juxtaposition is implicit
 * multiplication, so button-built strings like {@code 2pi}, {@code 2(3)}, and {@code sin(1)cos(1)} evaluate.
 *
 * <p>Supported functions (each requires parentheses): {@code sin cos tan asin acos atan sqrt ln log exp
 * abs}; constants {@code pi} and {@code e}. Trigonometric input and inverse-trig output honour the
 * {@code degrees} flag. Any malformed or out-of-domain expression throws {@link CalcException}; this class
 * touches no Minecraft type so it is unit-tested directly.
 */
public final class CalcEngine {

    private CalcEngine() {
    }

    /** Thrown when an expression cannot be parsed or is outside a function's domain. */
    public static final class CalcException extends RuntimeException {
        public CalcException(final String message) {
            super(message);
        }
    }

    /** Evaluates {@code expr}, interpreting trig arguments in degrees when {@code degrees} is true. */
    public static double evaluate(final String expr, final boolean degrees) {
        final Parser parser = new Parser(expr, degrees);
        final double value = parser.parseExpr();
        parser.skipWs();
        if (!parser.atEnd()) {
            throw new CalcException("Unexpected '" + parser.peek() + "'");
        }
        return value;
    }

    private static final class Parser {
        private final String s;
        private final boolean degrees;
        private int pos;

        Parser(final String s, final boolean degrees) {
            this.s = s;
            this.degrees = degrees;
        }

        // expr := term (('+' | '-') term)*
        private double parseExpr() {
            double v = parseTerm();
            while (true) {
                skipWs();
                final char c = peek();
                if (c == '+') {
                    pos++;
                    v += parseTerm();
                } else if (c == '-') {
                    pos++;
                    v -= parseTerm();
                } else {
                    return v;
                }
            }
        }

        // term := unary (('*' | '/' | '%' | juxtaposition) unary)*
        private double parseTerm() {
            double v = parseUnary();
            while (true) {
                skipWs();
                final char c = peek();
                if (c == '*') {
                    pos++;
                    v *= parseUnary();
                } else if (c == '/') {
                    pos++;
                    final double d = parseUnary();
                    if (d == 0.0) {
                        throw new CalcException("Division by zero");
                    }
                    v /= d;
                } else if (c == '%') {
                    pos++;
                    final double d = parseUnary();
                    if (d == 0.0) {
                        throw new CalcException("Division by zero");
                    }
                    v %= d;
                } else if (isFactorStart(c)) {
                    // Implicit multiplication: "2pi", "2(3)", "sin(1)cos(1)".
                    v *= parseUnary();
                } else {
                    return v;
                }
            }
        }

        // unary := ('+' | '-') unary | power
        private double parseUnary() {
            skipWs();
            final char c = peek();
            if (c == '-') {
                pos++;
                return -parseUnary();
            }
            if (c == '+') {
                pos++;
                return parseUnary();
            }
            return parsePower();
        }

        // power := postfix ('^' unary)?   (right-associative, and the exponent may carry its own sign)
        private double parsePower() {
            final double base = parsePostfix();
            skipWs();
            if (peek() == '^') {
                pos++;
                return Math.pow(base, parseUnary());
            }
            return base;
        }

        // postfix := primary ('!')*
        private double parsePostfix() {
            double v = parsePrimary();
            while (true) {
                skipWs();
                if (peek() == '!') {
                    pos++;
                    v = factorial(v);
                } else {
                    return v;
                }
            }
        }

        private double parsePrimary() {
            skipWs();
            final char c = peek();
            if (c == '(') {
                pos++;
                final double v = parseExpr();
                skipWs();
                if (peek() != ')') {
                    throw new CalcException("Expected ')'");
                }
                pos++;
                return v;
            }
            if (Character.isLetter(c)) {
                return parseIdentifier();
            }
            if (Character.isDigit(c) || c == '.') {
                return parseNumber();
            }
            throw new CalcException(atEnd() ? "Unexpected end of expression" : "Unexpected '" + c + "'");
        }

        private double parseIdentifier() {
            final int start = pos;
            while (pos < s.length() && Character.isLetterOrDigit(s.charAt(pos))) {
                pos++;
            }
            final String id = s.substring(start, pos).toLowerCase(Locale.ROOT);
            switch (id) {
                case "pi":
                    return Math.PI;
                case "e":
                    return Math.E;
                default:
                    break;
            }
            skipWs();
            if (peek() != '(') {
                throw new CalcException("Unknown name '" + id + "'");
            }
            pos++;
            final double arg = parseExpr();
            skipWs();
            if (peek() != ')') {
                throw new CalcException("Expected ')'");
            }
            pos++;
            return applyFunction(id, arg);
        }

        private double applyFunction(final String id, final double x) {
            switch (id) {
                case "sin":
                    return Math.sin(toRadians(x));
                case "cos":
                    return Math.cos(toRadians(x));
                case "tan":
                    return Math.tan(toRadians(x));
                case "asin":
                    if (x < -1.0 || x > 1.0) {
                        throw new CalcException("asin domain");
                    }
                    return fromRadians(Math.asin(x));
                case "acos":
                    if (x < -1.0 || x > 1.0) {
                        throw new CalcException("acos domain");
                    }
                    return fromRadians(Math.acos(x));
                case "atan":
                    return fromRadians(Math.atan(x));
                case "sqrt":
                    if (x < 0.0) {
                        throw new CalcException("sqrt of a negative number");
                    }
                    return Math.sqrt(x);
                case "ln":
                    if (x <= 0.0) {
                        throw new CalcException("ln domain");
                    }
                    return Math.log(x);
                case "log":
                    if (x <= 0.0) {
                        throw new CalcException("log domain");
                    }
                    return Math.log10(x);
                case "exp":
                    return Math.exp(x);
                case "abs":
                    return Math.abs(x);
                default:
                    throw new CalcException("Unknown function '" + id + "'");
            }
        }

        private double toRadians(final double x) {
            return degrees ? Math.toRadians(x) : x;
        }

        private double fromRadians(final double x) {
            return degrees ? Math.toDegrees(x) : x;
        }

        private double parseNumber() {
            final int start = pos;
            boolean dot = false;
            while (pos < s.length()) {
                final char c = s.charAt(pos);
                if (Character.isDigit(c)) {
                    pos++;
                } else if (c == '.' && !dot) {
                    dot = true;
                    pos++;
                } else {
                    break;
                }
            }
            final String num = s.substring(start, pos);
            if (num.isEmpty() || ".".equals(num)) {
                throw new CalcException("Bad number");
            }
            return Double.parseDouble(num);
        }

        private static double factorial(final double v) {
            if (v < 0.0 || v != Math.floor(v)) {
                throw new CalcException("Factorial needs a non-negative integer");
            }
            if (v > 170.0) {
                throw new CalcException("Factorial too large");
            }
            double r = 1.0;
            for (int i = 2; i <= (int) v; i++) {
                r *= i;
            }
            return r;
        }

        private static boolean isFactorStart(final char c) {
            return Character.isDigit(c) || c == '.' || c == '(' || Character.isLetter(c);
        }

        boolean atEnd() {
            return pos >= s.length();
        }

        char peek() {
            return pos < s.length() ? s.charAt(pos) : '\0';
        }

        void skipWs() {
            while (pos < s.length() && s.charAt(pos) == ' ') {
                pos++;
            }
        }
    }
}
