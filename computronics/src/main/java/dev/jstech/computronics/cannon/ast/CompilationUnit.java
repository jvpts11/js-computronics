/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.ast;

import java.util.List;
import java.util.Objects;

/**
 * One source file, parsed.
 *
 * <p>A file holds nothing but type declarations, which is the language's first rule: there is no
 * such thing as a loose statement at the top of a file.
 */
public record CompilationUnit(String file, List<IDecl.ITypeDecl> types) {

    public CompilationUnit {
        Objects.requireNonNull(file, "file");
        types = List.copyOf(types);
    }

    /** The declaration named {@code name}, or null if the file does not declare one. */
    public IDecl.ITypeDecl type(final String name) {
        for (final IDecl.ITypeDecl type : this.types) {
            if (type.name().equals(name)) {
                return type;
            }
        }
        return null;
    }
}
