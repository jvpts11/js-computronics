/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.sem;

import dev.jstech.computers.cannon.Shape;
import dev.jstech.computers.cannon.ast.IExpr;
import dev.jstech.computers.cannon.ast.INode;
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

    private final Map<IExpr, ITypeSymbol> types = new IdentityHashMap<>();
    private final Map<IExpr, IBinding> bindings = new IdentityHashMap<>();
    private final Map<IExpr, IMemberSymbol> calls = new IdentityHashMap<>();
    private final Map<INode, IBinding.Variable> places = new IdentityHashMap<>();
    private final List<NamedType> declared = new ArrayList<>();
    private NamedType entryPoint;
    private Shape shape = Shape.SCRIPT;

    /** Records what an expression's type is. */
    public void setType(final IExpr expression, final ITypeSymbol type) {
        this.types.put(expression, type == null ? ITypeSymbol.Special.ERROR : type);
    }

    /** The type of an expression, or null if it was never checked. */
    public ITypeSymbol typeOf(final IExpr expression) {
        return this.types.get(expression);
    }

    /** Records what a name turned out to be. */
    public void setBinding(final IExpr expression, final IBinding binding) {
        this.bindings.put(expression, binding);
    }

    /** What a name turned out to be, or null. */
    public IBinding bindingOf(final IExpr expression) {
        return this.bindings.get(expression);
    }

    /** Records which method or constructor a call resolved to. */
    public void setCall(final IExpr expression, final IMemberSymbol member) {
        this.calls.put(expression, member);
    }

    /** The method or constructor a call resolved to, or null. */
    public IMemberSymbol callOf(final IExpr expression) {
        return this.calls.get(expression);
    }

    /**
     * Records where a variable comes into being: a parameter, a local, the name a foreach walks with,
     * or a variable declared at a call that fills it in.
     *
     * <p>The stage that emits the assembly needs this to number the places a method keeps its values
     * in. Every mention of the name binds to the same variable this records, so numbering the
     * declarations is enough to number every use.
     */
    public void setDeclared(final INode site, final IBinding.Variable variable) {
        this.places.put(site, variable);
    }

    /** The variable a declaration site brings into being, or null. */
    public IBinding.Variable declaredAt(final INode site) {
        return this.places.get(site);
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

    /** The class the runtime starts the program from, or null when there is not exactly one. */
    public NamedType entryPoint() {
        return this.entryPoint;
    }

    /** Whether that class is a program that runs at a terminal or one that stays up. */
    public Shape shape() {
        return this.shape;
    }

    /** Records the class the runtime starts from, and which kind of program it makes. */
    public void setEntryPoint(final NamedType entryPoint, final Shape shape) {
        this.entryPoint = entryPoint;
        this.shape = shape;
    }
}
