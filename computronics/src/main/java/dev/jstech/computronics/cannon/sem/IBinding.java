/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.sem;

/**
 * What a name in the source turned out to be.
 *
 * <p>A name is one of three things and never a fourth: something stored in the running method, a
 * member of a type, or the name of a type itself. The stage that emits the assembly reads this to
 * know whether to load a slot, a field or nothing at all.
 */
public sealed interface IBinding {

    /** The type of the value the name stands for; for a type name, the type itself. */
    ITypeSymbol type();

    /** A local or a parameter of the method being checked. */
    record Variable(String name, ITypeSymbol type, boolean parameter) implements IBinding {
    }

    /** A field, a property, an event or a method of a type, with its arguments already filled in. */
    record Member(IMemberSymbol member, ITypeSymbol type) implements IBinding {
    }

    /** A type used where a receiver goes, as in a call of one of its static members. */
    record TypeName(ITypeSymbol type) implements IBinding {
    }
}
