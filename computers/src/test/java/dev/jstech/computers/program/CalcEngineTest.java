/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.program;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CalcEngineTest {

    private static final double EPS = 1e-9;

    private static double eval(final String expr) {
        return CalcEngine.evaluate(expr, false);
    }

    @Test
    void evaluate_addsAndSubtractsLeftToRight() {
        assertEquals(6.0, eval("1 + 2 + 3"), EPS);
        assertEquals(0.0, eval("10 - 4 - 6"), EPS);
    }

    @Test
    void evaluate_honoursMultiplicationBeforeAddition() {
        assertEquals(14.0, eval("2 + 3 * 4"), EPS);
    }

    @Test
    void evaluate_parenthesesOverridePrecedence() {
        assertEquals(20.0, eval("(2 + 3) * 4"), EPS);
    }

    @Test
    void evaluate_divisionAndModulo() {
        assertEquals(2.5, eval("5 / 2"), EPS);
        assertEquals(1.0, eval("7 % 3"), EPS);
    }

    @Test
    void evaluate_powerIsRightAssociative() {
        assertEquals(512.0, eval("2 ^ 3 ^ 2"), EPS);
    }

    @Test
    void evaluate_unaryMinusBindsLooserThanPower() {
        assertEquals(-4.0, eval("-2 ^ 2"), EPS);
    }

    @Test
    void evaluate_implicitMultiplicationJuxtaposition() {
        assertEquals(2 * Math.PI, eval("2pi"), EPS);
        assertEquals(6.0, eval("2(3)"), EPS);
        assertEquals(6.0, eval("(2)(3)"), EPS);
    }

    @Test
    void evaluate_constants() {
        assertEquals(Math.PI, eval("pi"), EPS);
        assertEquals(Math.E, eval("e"), EPS);
    }

    @Test
    void evaluate_functionsInRadiansByDefault() {
        assertEquals(1.0, eval("sin(pi / 2)"), EPS);
        assertEquals(0.0, eval("cos(pi / 2)"), 1e-9);
        assertEquals(2.0, eval("sqrt(4)"), EPS);
        assertEquals(1.0, eval("ln(e)"), EPS);
        assertEquals(3.0, eval("log(1000)"), EPS);
    }

    @Test
    void evaluate_trigInDegreesWhenFlagSet() {
        assertEquals(1.0, CalcEngine.evaluate("sin(90)", true), EPS);
        assertEquals(45.0, CalcEngine.evaluate("atan(1)", true), EPS);
    }

    @Test
    void evaluate_factorialPostfix() {
        assertEquals(120.0, eval("5!"), EPS);
        assertEquals(720.0, eval("3! !"), EPS); // (3!)! = 6! = 720
    }

    @Test
    void evaluate_ignoresWhitespace() {
        assertEquals(7.0, eval("  1   +   2*3  "), EPS);
    }

    @Test
    void evaluate_decimalAndLeadingDot() {
        assertEquals(0.5, eval(".5"), EPS);
        assertEquals(3.14, eval("3.14"), EPS);
    }

    @Test
    void evaluate_divisionByZeroThrows() {
        assertThrows(CalcEngine.CalcException.class, () -> eval("1 / 0"));
    }

    @Test
    void evaluate_sqrtOfNegativeThrows() {
        assertThrows(CalcEngine.CalcException.class, () -> eval("sqrt(-1)"));
    }

    @Test
    void evaluate_lnDomainThrows() {
        assertThrows(CalcEngine.CalcException.class, () -> eval("ln(0)"));
    }

    @Test
    void evaluate_unbalancedParenthesesThrows() {
        assertThrows(CalcEngine.CalcException.class, () -> eval("(1 + 2"));
    }

    @Test
    void evaluate_unknownNameThrows() {
        assertThrows(CalcEngine.CalcException.class, () -> eval("foo(2)"));
    }

    @Test
    void evaluate_trailingGarbageThrows() {
        assertThrows(CalcEngine.CalcException.class, () -> eval("2 +"));
        assertThrows(CalcEngine.CalcException.class, () -> eval("* 2"));
    }

    @Test
    void evaluate_factorialOfNonIntegerThrows() {
        assertThrows(CalcEngine.CalcException.class, () -> eval("2.5!"));
    }
}
