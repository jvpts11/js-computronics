/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.ast;

import java.util.List;

/**
 * Every statement the language has.
 *
 * <p>Like the expressions, the hierarchy is closed so the stages after this one cannot quietly
 * forget a form. A statement that failed to parse is not represented at all: the parser reports it
 * and skips to the next one, so a tree never holds a hole a later stage would have to guard against.
 */
public sealed interface Stmt extends Node {

    /** A braced group with its own scope. */
    record Block(List<Stmt> statements, int line, int column) implements Stmt {
    }

    /** A conditional; {@code otherwise} is null when there is no else. */
    record If(Expr condition, Stmt then, Stmt otherwise, int line, int column) implements Stmt {
    }

    /** A loop that tests before each pass. */
    record While(Expr condition, Stmt body, int line, int column) implements Stmt {
    }

    /** A loop that tests after each pass. */
    record DoWhile(Stmt body, Expr condition, int line, int column) implements Stmt {
    }

    /** The three-part loop; the condition is null when it was left out, which means forever. */
    record For(List<Stmt> initializers, Expr condition, List<Expr> updates, Stmt body, int line, int column)
            implements Stmt {
    }

    /** A loop over a collection or an array. */
    record ForEach(TypeRef type, String name, Expr source, Stmt body, int line, int column) implements Stmt {
    }

    /** One group of labels and the statements they share. */
    record SwitchSection(List<Expr> labels, boolean fallback, List<Stmt> statements, int line, int column)
            implements Node {
    }

    /** A choice between sections. */
    record Switch(Expr value, List<SwitchSection> sections, int line, int column) implements Stmt {
    }

    /** Leaves the innermost loop or switch section. */
    record Break(int line, int column) implements Stmt {
    }

    /** Starts the innermost loop's next pass. */
    record Continue(int line, int column) implements Stmt {
    }

    /** Leaves the method; {@code value} is null in a method that returns nothing. */
    record Return(Expr value, int line, int column) implements Stmt {
    }

    /** A local variable; {@code initializer} is null when it was declared without one. */
    record LocalDecl(TypeRef type, String name, Expr initializer, int line, int column) implements Stmt {
    }

    /** An expression evaluated for what it does rather than for its value. */
    record ExprStmt(Expr expression, int line, int column) implements Stmt {
    }

    /** Frees an object and leaves the reference null. */
    record Dispose(Expr target, int line, int column) implements Stmt {
    }

    /** A lone semicolon. */
    record Empty(int line, int column) implements Stmt {
    }
}
