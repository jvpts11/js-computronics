/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.sem;

import dev.jstech.computronics.cannon.ast.Expr;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the checker learned: the types a program declares, and what every expression in it turned out
 * to be.
 *
 * <p>The tree stays exactly as it was parsed and the answers are kept beside it, keyed by the node
 * itself. The stage that emits the assembly walks the same tree and asks this model what each node
 * meant, so nothing has to be worked out twice and the tree never has to be rebuilt.
 */
public final class SemanticModel {

    private final Map<Expr, TypeSymbol> types = new IdentityHashMap<>();
    private final Map<Expr, Binding> bindings = new IdentityHashMap<>();
    private final Map<Expr, MemberSymbol> calls = new IdentityHashMap<>();
    private final List<NamedType> declared = new ArrayList<>();
    private NamedType entryPoint;

    /** Records what an expression's type is. */
    public void setType(final Expr expression, final TypeSymbol type) {
        this.types.put(expression, type == null ? TypeSymbol.Special.ERROR : type);
    }

    /** The type of an expression, or null if it was never checked. */
    public TypeSymbol typeOf(final Expr expression) {
        return this.types.get(expression);
    }

    /** Records what a name turned out to be. */
    public void setBinding(final Expr expression, final Binding binding) {
        this.bindings.put(expression, binding);
    }

    /** What a name turned out to be, or null. */
    public Binding bindingOf(final Expr expression) {
        return this.bindings.get(expression);
    }

    /** Records which method or constructor a call resolved to. */
    public void setCall(final Expr expression, final MemberSymbol member) {
        this.calls.put(expression, member);
    }

    /** The method or constructor a call resolved to, or null. */
    public MemberSymbol callOf(final Expr expression) {
        return this.calls.get(expression);
    }

    /** Records a type the program declares. */
    public void addDeclared(final NamedType type) {
        this.declared.add(type);
    }

    /** The types the program declares, in the order they were written. */
    public List<NamedType> declaredTypes() {
        return List.copyOf(this.declared);
    }

    /** The declared type of that name, or null. */
    public NamedType declaredType(final String name) {
        for (final NamedType type : this.declared) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return null;
    }

    /** The class the runtime instantiates to start the program, or null when there is not exactly one. */
    public NamedType entryPoint() {
        return this.entryPoint;
    }

    /** Records the class the runtime starts from. */
    public void setEntryPoint(final NamedType entryPoint) {
        this.entryPoint = entryPoint;
    }
}
