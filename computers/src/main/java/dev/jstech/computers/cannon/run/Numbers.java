/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.run;

import dev.jstech.computers.cannon.asm.Opcode;

/**
 * Arithmetic on the values a running program holds.
 *
 * <p>The compiler already put a conversion wherever one was needed, so the two sides of an operation
 * arrive as the same kind of number and nothing here has to widen anything. What it does have to do
 * is give back the same kind it was given, so an int divided by an int stays an int, whole and
 * rounded towards zero, as the language says.
 */
public final class Numbers {

    private Numbers() {
    }

    /** Applies a two-sided operation. */
    public static Object apply(final Opcode opcode, final Object left, final Object right, final int line) {
        if (left instanceof Boolean first && right instanceof Boolean second) {
            return switch (opcode) {
                case AND -> first && second;
                case OR -> first || second;
                default -> first ^ second;
            };
        }
        if (left instanceof Double || right instanceof Double) {
            return real(opcode, toDouble(left), toDouble(right), line);
        }
        if (left instanceof Float || right instanceof Float) {
            return (float) real(opcode, toDouble(left), toDouble(right), line);
        }
        if (left instanceof Long || right instanceof Long) {
            return whole(opcode, toLong(left), toLong(right), line);
        }
        return (int) whole(opcode, toLong(left), toLong(right), line);
    }

    private static double real(final Opcode opcode, final double left, final double right, final int line) {
        return switch (opcode) {
            case ADD -> left + right;
            case SUB -> left - right;
            case MUL -> left * right;
            case DIV -> divide(left, right, line);
            case REM -> remainder(left, right, line);
            default -> throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line,
                    opcode.text() + " does not apply to a real number");
        };
    }

    private static long whole(final Opcode opcode, final long left, final long right, final int line) {
        return switch (opcode) {
            case ADD -> left + right;
            case SUB -> left - right;
            case MUL -> left * right;
            case DIV -> divideWhole(left, right, line);
            case REM -> remainderWhole(left, right, line);
            case AND -> left & right;
            case OR -> left | right;
            case XOR -> left ^ right;
            case SHL -> left << right;
            default -> left >> right;
        };
    }

    private static double divide(final double left, final double right, final int line) {
        if (right == 0) {
            throw new Halt(Halt.Reason.DIVIDE_BY_ZERO, line, "divided by zero");
        }
        return left / right;
    }

    private static double remainder(final double left, final double right, final int line) {
        if (right == 0) {
            throw new Halt(Halt.Reason.DIVIDE_BY_ZERO, line, "took the remainder of a division by zero");
        }
        return left % right;
    }

    private static long divideWhole(final long left, final long right, final int line) {
        if (right == 0) {
            throw new Halt(Halt.Reason.DIVIDE_BY_ZERO, line, "divided by zero");
        }
        return left / right;
    }

    private static long remainderWhole(final long left, final long right, final int line) {
        if (right == 0) {
            throw new Halt(Halt.Reason.DIVIDE_BY_ZERO, line, "took the remainder of a division by zero");
        }
        return left % right;
    }

    /** Which of two numbers is the greater, as a comparison gives back. */
    public static int compare(final Object left, final Object right) {
        if (left instanceof Double || right instanceof Double
                || left instanceof Float || right instanceof Float) {
            return Double.compare(toDouble(left), toDouble(right));
        }
        return Long.compare(toLong(left), toLong(right));
    }

    /** The same number with its sign turned round. */
    public static Object negate(final Object value) {
        if (value instanceof Double real) {
            return -real;
        }
        if (value instanceof Float real) {
            return -real;
        }
        if (value instanceof Long whole) {
            return -whole;
        }
        return -toInt(value);
    }

    /** Every bit of a whole number turned round. */
    public static Object complement(final Object value) {
        if (value instanceof Long whole) {
            return ~whole;
        }
        return ~toInt(value);
    }

    /** The value as a whole number that fits in four bytes. */
    public static int toInt(final Object value) {
        if (value instanceof Character letter) {
            return letter;
        }
        return value instanceof Number number ? number.intValue() : 0;
    }

    /** The value as a whole number that fits in eight. */
    public static long toLong(final Object value) {
        if (value instanceof Character letter) {
            return letter;
        }
        return value instanceof Number number ? number.longValue() : 0;
    }

    /** The value as a four-byte real. */
    public static float toFloat(final Object value) {
        if (value instanceof Character letter) {
            return letter;
        }
        return value instanceof Number number ? number.floatValue() : 0;
    }

    /** The value as an eight-byte real. */
    public static double toDouble(final Object value) {
        if (value instanceof Character letter) {
            return letter;
        }
        return value instanceof Number number ? number.doubleValue() : 0;
    }
}
