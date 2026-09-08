/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.ast;

import java.util.List;

/**
 * Every statement the language has.
 *
 * <p>Like the expressions, the hierarchy is closed so the stages after this one cannot quietly
 * forget a form. A statement that failed to parse is not represented at all: the parser reports it
 * and skips to the next one, so a tree never holds a hole a later stage would have to guard against.
 */
public sealed interface IStmt extends INode {

    /** A braced group with its own scope. */
    record Block(List<IStmt> statements, int line, int column) implements IStmt {
    }

    /** A conditional; {@code otherwise} is null when there is no else. */
    record If(IExpr condition, IStmt then, IStmt otherwise, int line, int column) implements IStmt {
    }

    /** A loop that tests before each pass. */
    record While(IExpr condition, IStmt body, int line, int column) implements IStmt {
    }

    /** A loop that tests after each pass. */
    record DoWhile(IStmt body, IExpr condition, int line, int column) implements IStmt {
    }

    /** The three-part loop; the condition is null when it was left out, which means forever. */
    record For(List<IStmt> initializers, IExpr condition, List<IExpr> updates, IStmt body, int line, int column)
            implements IStmt {
    }

    /** A loop over a collection or an array. */
    record ForEach(TypeRef type, String name, IExpr source, IStmt body, int line, int column) implements IStmt {
    }

    /** One group of labels and the statements they share. */
    record SwitchSection(List<IExpr> labels, boolean fallback, List<IStmt> statements, int line, int column)
            implements INode {
    }

    /** A choice between sections. */
    record Switch(IExpr value, List<SwitchSection> sections, int line, int column) implements IStmt {
    }

    /** Leaves the innermost loop or switch section. */
    record Break(int line, int column) implements IStmt {
    }

    /** Starts the innermost loop's next pass. */
    record Continue(int line, int column) implements IStmt {
    }

    /** Leaves the method; {@code value} is null in a method that returns nothing. */
    record Return(IExpr value, int line, int column) implements IStmt {
    }

    /** A local variable; {@code initializer} is null when it was declared without one. */
    record LocalDecl(TypeRef type, String name, IExpr initializer, int line, int column) implements IStmt {
    }

    /** An expression evaluated for what it does rather than for its value. */
    record ExprStmt(IExpr expression, int line, int column) implements IStmt {
    }

    /** Frees an object and leaves the reference null. */
    record Dispose(IExpr target, int line, int column) implements IStmt {
    }

    /** A lone semicolon. */
    record Empty(int line, int column) implements IStmt {
    }
}
