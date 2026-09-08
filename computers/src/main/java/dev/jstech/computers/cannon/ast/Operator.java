/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.ast;

/**
 * What an operator node does, apart from how it was written.
 *
 * <p>The parser resolves the spelling once: a minus in front of a value is {@link #NEGATE} and a
 * minus between two is {@link #SUBTRACT}, and a compound assignment keeps the arithmetic operator
 * beside {@link IExpr.Assign}, so no later stage has to read the source text again.
 */
public enum Operator {

    NOT("!"),
    NEGATE("-"),
    PLUS("+"),
    COMPLEMENT("~"),
    INCREMENT("++"),
    DECREMENT("--"),

    ADD("+"),
    SUBTRACT("-"),
    MULTIPLY("*"),
    DIVIDE("/"),
    REMAINDER("%"),

    EQUAL("=="),
    NOT_EQUAL("!="),
    LESS("<"),
    LESS_EQUAL("<="),
    GREATER(">"),
    GREATER_EQUAL(">="),

    AND("&&"),
    OR("||"),
    BIT_AND("&"),
    BIT_OR("|"),
    BIT_XOR("^"),
    SHIFT_LEFT("<<"),
    SHIFT_RIGHT(">>"),

    ASSIGN("=");

    private final String text;

    Operator(final String text) {
        this.text = text;
    }

    /** How the operator is written in source. */
    public String text() {
        return this.text;
    }
}
