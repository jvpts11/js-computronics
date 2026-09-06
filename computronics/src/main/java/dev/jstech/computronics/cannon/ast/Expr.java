/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.ast;

import dev.jstech.computronics.cannon.lex.TokenKind;
import java.util.List;

/**
 * Every expression the language has.
 *
 * <p>The hierarchy is closed, so a later stage that switches over it is told by the compiler when a
 * form is missing. Precedence and associativity are already resolved here: the shape of the tree is
 * the meaning, and nothing downstream re-reads the operators to work out grouping.
 */
public sealed interface Expr extends Node {

    /** A literal value, already converted from its text by the lexer. */
    record Literal(TokenKind kind, Object value, int line, int column) implements Expr {
    }

    /** A bare name: a local, a parameter, a field or a type used as a receiver. */
    record Name(String identifier, int line, int column) implements Expr {
    }

    /** The instance the method was called on. */
    record This(int line, int column) implements Expr {
    }

    /** The base class of the instance the method was called on. */
    record Base(int line, int column) implements Expr {
    }

    /** One operand, before it ({@code !x}) or after it ({@code x++}). */
    record Unary(Operator operator, Expr operand, boolean postfix, int line, int column) implements Expr {
    }

    /** Two operands. */
    record Binary(Operator operator, Expr left, Expr right, int line, int column) implements Expr {
    }

    /** An assignment; {@link Operator#ASSIGN} is the plain form, any other operator is a compound one. */
    record Assign(Expr target, Operator operator, Expr value, int line, int column) implements Expr {
    }

    /** The three-part conditional. */
    record Conditional(Expr condition, Expr whenTrue, Expr whenFalse, int line, int column) implements Expr {
    }

    /** A call of whatever the callee turns out to be: a method, a delegate or an event. */
    record Call(Expr callee, List<Expr> arguments, int line, int column) implements Expr {
    }

    /** Reaching a member through a dot. */
    record Member(Expr target, String name, int line, int column) implements Expr {
    }

    /** Reaching an element through brackets: an array, a list or a map. */
    record Index(Expr target, Expr index, int line, int column) implements Expr {
    }

    /** Allocating an object. */
    record New(TypeRef type, List<Expr> arguments, int line, int column) implements Expr {
    }

    /** Allocating an array of a fixed length. */
    record NewArray(TypeRef elementType, Expr length, int line, int column) implements Expr {
    }

    /** A written conversion. */
    record Cast(TypeRef type, Expr value, int line, int column) implements Expr {
    }

    /**
     * A runtime type question: {@code x is T} asks and gives a bool, {@code x as T} converts and
     * gives null when it cannot.
     */
    record TypeTest(Expr value, TypeRef type, boolean conversion, int line, int column) implements Expr {
    }

    /**
     * A lambda. Exactly one of {@code body} and {@code block} is set: the arrow form carries an
     * expression, the braced form carries statements.
     */
    record Lambda(List<Decl.Parameter> parameters, Expr body, Stmt.Block block, int line, int column)
            implements Expr {
    }
}
