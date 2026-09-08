/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.ast;

import dev.jstech.computers.cannon.lex.TokenKind;
import java.util.List;

/**
 * Every expression the language has.
 *
 * <p>The hierarchy is closed, so a later stage that switches over it is told by the compiler when a
 * form is missing. Precedence and associativity are already resolved here: the shape of the tree is
 * the meaning, and nothing downstream re-reads the operators to work out grouping.
 */
public sealed interface IExpr extends INode {

    /** A literal value, already converted from its text by the lexer. */
    record Literal(TokenKind kind, Object value, int line, int column) implements IExpr {
    }

    /** A bare name: a local, a parameter, a field or a type used as a receiver. */
    record Name(String identifier, int line, int column) implements IExpr {
    }

    /** The instance the method was called on. */
    record This(int line, int column) implements IExpr {
    }

    /** The base class of the instance the method was called on. */
    record Base(int line, int column) implements IExpr {
    }

    /** One operand, before it ({@code !x}) or after it ({@code x++}). */
    record Unary(Operator operator, IExpr operand, boolean postfix, int line, int column) implements IExpr {
    }

    /** Two operands. */
    record Binary(Operator operator, IExpr left, IExpr right, int line, int column) implements IExpr {
    }

    /** An assignment; {@link Operator#ASSIGN} is the plain form, any other operator is a compound one. */
    record Assign(IExpr target, Operator operator, IExpr value, int line, int column) implements IExpr {
    }

    /** The three-part conditional. */
    record Conditional(IExpr condition, IExpr whenTrue, IExpr whenFalse, int line, int column) implements IExpr {
    }

    /** A call of whatever the callee turns out to be: a method, a delegate or an event. */
    record Call(IExpr callee, List<IExpr> arguments, int line, int column) implements IExpr {
    }

    /** Reaching a member through a dot. */
    record Member(IExpr target, String name, int line, int column) implements IExpr {
    }

    /** Reaching an element through brackets: an array, a list or a map. */
    record Index(IExpr target, IExpr index, int line, int column) implements IExpr {
    }

    /** Allocating an object. */
    record New(TypeRef type, List<IExpr> arguments, int line, int column) implements IExpr {
    }

    /** Allocating an array of a fixed length. */
    record NewArray(TypeRef elementType, IExpr length, int line, int column) implements IExpr {
    }

    /** A written conversion. */
    record Cast(TypeRef type, IExpr value, int line, int column) implements IExpr {
    }

    /**
     * A runtime type question: {@code x is T} asks and gives a bool, {@code x as T} converts and
     * gives null when it cannot.
     */
    record TypeTest(IExpr value, TypeRef type, boolean conversion, int line, int column) implements IExpr {
    }

    /**
     * A lambda. Exactly one of {@code body} and {@code block} is set: the arrow form carries an
     * expression, the braced form carries statements.
     */
    record Lambda(List<IDecl.Parameter> parameters, IExpr body, IStmt.Block block, int line, int column)
            implements IExpr {
    }

    /**
     * A place handed to a method for it to fill in. Only an argument can be one of these.
     *
     * <p>{@code type} is null when the argument names something that already exists, and set when
     * the variable is declared right there in the call; written as {@code var}, the variable takes
     * whatever the method fills in.
     */
    record OutArgument(TypeRef type, String name, int line, int column) implements IExpr {
    }
}
