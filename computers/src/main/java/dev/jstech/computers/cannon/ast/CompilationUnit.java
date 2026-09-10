/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One source file, read: what it brought in with {@code using}, and the types it declares, each with
 * the namespace it was declared in.
 *
 * <p>A file opens with its usings, then a namespace written either as a line on its own
 * ({@code namespace X;}) or as blocks around the types, and blocks may sit inside blocks, so two types
 * in one file may be in two namespaces. Every type is in one: the parser says so when it is not.
 *
 * @param file     the name the file is known by in diagnostics
 * @param usings   what the file brought in, in the order written
 * @param declared the declarations, in the order written, each with its namespace
 */
public record CompilationUnit(String file, List<Using> usings, List<Declared> declared) {

    /**
     * One using: a namespace whose every type may be named plainly ({@code using System.IO.*;}), or one
     * type that may ({@code using System.IO.Console;}).
     *
     * @param name   what was written, without the star
     * @param all    whether the star was there
     * @param line   where the using was written, for a complaint about it
     * @param column the column it starts at
     */
    public record Using(String name, boolean all, int line, int column) {

        public Using {
            Objects.requireNonNull(name, "name");
        }

        /** A using with nowhere in particular to point a complaint at. */
        public Using(final String name, final boolean all) {
            this(name, all, 1, 1);
        }

        /** How the using was written. */
        public String describe() {
            return this.all ? this.name + ".*" : this.name;
        }
    }

    /** One type and the namespace it was declared in. */
    public record Declared(String namespace, IDecl.ITypeDecl type) {

        public Declared {
            namespace = namespace == null ? "" : namespace;
            Objects.requireNonNull(type, "type");
        }
    }

    public CompilationUnit {
        Objects.requireNonNull(file, "file");
        usings = usings == null ? List.of() : List.copyOf(usings);
        declared = declared == null ? List.of() : List.copyOf(declared);
    }

    /** A file at the top, bringing nothing in, every type in {@code namespace}. */
    public CompilationUnit(final String file, final String namespace, final List<Using> usings,
                           final List<IDecl.ITypeDecl> types) {
        this(file, usings, inNamespace(namespace, types));
    }

    private static List<Declared> inNamespace(final String namespace, final List<IDecl.ITypeDecl> types) {
        final List<Declared> out = new ArrayList<>(types.size());
        for (final IDecl.ITypeDecl type : types) {
            out.add(new Declared(namespace, type));
        }
        return out;
    }

    /** The declarations, in the order written. */
    public List<IDecl.ITypeDecl> types() {
        final List<IDecl.ITypeDecl> out = new ArrayList<>(this.declared.size());
        for (final Declared one : this.declared) {
            out.add(one.type());
        }
        return out;
    }

    /** The namespace the first type was declared in, or empty; what a file has one of, this is it. */
    public String namespace() {
        return this.declared.isEmpty() ? "" : this.declared.getFirst().namespace();
    }

    /** The declaration called {@code name} in this file, or null when there is none. */
    public IDecl.ITypeDecl type(final String name) {
        for (final Declared one : this.declared) {
            if (one.type().name().equals(name)) {
                return one.type();
            }
        }
        return null;
    }
}
