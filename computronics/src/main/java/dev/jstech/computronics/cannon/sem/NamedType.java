/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.sem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A type with a name: a class, an interface, an enum or a delegate, declared in source or built in.
 *
 * <p>This is the one symbol that is filled in rather than built complete. Types refer to each other
 * in circles, so every name is declared first and only then given its base, its interfaces and its
 * members; a symbol that had to be complete at birth could never describe a class holding a field of
 * its own type.
 */
public final class NamedType implements ITypeSymbol {

    /** Which of the four kinds of named type this is. */
    public enum Kind {
        CLASS,
        INTERFACE,
        ENUM,
        DELEGATE
    }

    private final String name;
    private final Kind kind;
    private final List<String> typeParameters;
    private final boolean builtIn;
    private final List<IMemberSymbol> members = new ArrayList<>();
    private final List<NamedType> interfaces = new ArrayList<>();
    private NamedType base;
    private IMemberSymbol.MethodSymbol invoke;

    public NamedType(final String name, final Kind kind, final List<String> typeParameters, final boolean builtIn) {
        this.name = name;
        this.kind = kind;
        this.typeParameters = List.copyOf(typeParameters);
        this.builtIn = builtIn;
    }

    /** A type with no arguments of its own. */
    public static NamedType of(final String name, final Kind kind, final boolean builtIn) {
        return new NamedType(name, kind, List.of(), builtIn);
    }

    /** What the type is called. */
    public String name() {
        return this.name;
    }

    @Override
    public String describe() {
        return this.name;
    }

    /** Which of the four kinds this is. */
    public Kind kind() {
        return this.kind;
    }

    /** The names of the arguments this type takes, empty for everything but the built-in collections. */
    public List<String> typeParameters() {
        return this.typeParameters;
    }

    /** Whether the language provides this type rather than the player's source. */
    public boolean isBuiltIn() {
        return this.builtIn;
    }

    /** The class this one extends, or null. */
    public NamedType base() {
        return this.base;
    }

    /** Sets the class this one extends. Called once, while the types are being filled in. */
    public void setBase(final NamedType base) {
        this.base = base;
    }

    /** The interfaces this type was declared with, not counting those its base already carries. */
    public List<NamedType> interfaces() {
        return Collections.unmodifiableList(this.interfaces);
    }

    /** Adds an interface while the types are being filled in. */
    public void addInterface(final NamedType type) {
        this.interfaces.add(type);
    }

    /** Everything declared directly on this type. */
    public List<IMemberSymbol> members() {
        return Collections.unmodifiableList(this.members);
    }

    /** Adds a member while the types are being filled in. */
    public void addMember(final IMemberSymbol member) {
        this.members.add(member);
    }

    /** For a delegate, the shape of the method it stands for. */
    public IMemberSymbol.MethodSymbol invoke() {
        return this.invoke;
    }

    /** Sets a delegate's shape while the types are being filled in. */
    public void setInvoke(final IMemberSymbol.MethodSymbol invoke) {
        this.invoke = invoke;
    }

    /** Everything declared here and on every type above it, nearest first. */
    public List<IMemberSymbol> allMembers() {
        final List<IMemberSymbol> all = new ArrayList<>(this.members);
        for (NamedType above = this.base; above != null; above = above.base) {
            all.addAll(above.members);
        }
        for (final NamedType face : this.interfaces) {
            all.addAll(face.allMembers());
        }
        return all;
    }

    /** Whether this type is, or descends from, {@code other}. */
    public boolean isOrDescendsFrom(final NamedType other) {
        for (NamedType above = this; above != null; above = above.base) {
            if (above == other) {
                return true;
            }
            for (final NamedType face : above.interfaces) {
                if (face == other || face.isOrDescendsFrom(other)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
