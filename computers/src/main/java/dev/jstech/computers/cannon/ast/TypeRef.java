/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.ast;

import java.util.List;
import java.util.Objects;

/**
 * A type as it was written, before anything has been resolved.
 *
 * <p>The front end only records the shape: the name, the arguments a built-in collection was given,
 * and how many pairs of brackets followed it. Whether the name exists, and whether a collection was
 * given the right number of arguments, is settled once the symbol table exists.
 */
public record TypeRef(String name, List<TypeRef> arguments, int arrayRank, int line, int column) implements INode {

    public TypeRef {
        Objects.requireNonNull(name, "name");
        arguments = List.copyOf(arguments);
        if (arrayRank < 0) {
            throw new IllegalArgumentException("array rank cannot be negative: " + arrayRank);
        }
    }

    /** A plain named type with no arguments and no brackets. */
    public static TypeRef named(final String name, final int line, final int column) {
        return new TypeRef(name, List.of(), 0, line, column);
    }

    /** Whether this is an array of something. */
    public boolean isArray() {
        return this.arrayRank > 0;
    }

    /** The same type with one fewer pair of brackets, for reading an array's element type. */
    public TypeRef elementType() {
        if (this.arrayRank == 0) {
            throw new IllegalStateException(this.name + " is not an array");
        }
        return new TypeRef(this.name, this.arguments, this.arrayRank - 1, this.line, this.column);
    }

    /** How the type was written, for a diagnostic to quote back. */
    public String describe() {
        final StringBuilder text = new StringBuilder(this.name);
        if (!this.arguments.isEmpty()) {
            text.append('<');
            for (int i = 0; i < this.arguments.size(); i++) {
                text.append(i > 0 ? ", " : "").append(this.arguments.get(i).describe());
            }
            text.append('>');
        }
        text.append("[]".repeat(this.arrayRank));
        return text.toString();
    }
}
