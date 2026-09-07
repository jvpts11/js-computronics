/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.sem;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The names in scope while a method body is checked, one link per pair of braces.
 *
 * <p>A name may not be declared again while an outer one of the same name is still visible. Allowing
 * it would let an inner block quietly take over a name the surrounding code was still using, which
 * is exactly the mistake that is hardest to see when reading the program back.
 */
public final class Scope {

    private final Scope parent;
    private final Map<String, IBinding.Variable> variables = new LinkedHashMap<>();

    public Scope(final Scope parent) {
        this.parent = parent;
    }

    /** The variable of that name, from here outwards, or null. */
    public IBinding.Variable lookup(final String name) {
        for (Scope scope = this; scope != null; scope = scope.parent) {
            final IBinding.Variable found = scope.variables.get(name);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** Declares a variable here. Returns false when the name is already visible. */
    public boolean declare(final IBinding.Variable variable) {
        if (this.lookup(variable.name()) != null) {
            return false;
        }
        this.variables.put(variable.name(), variable);
        return true;
    }
}
