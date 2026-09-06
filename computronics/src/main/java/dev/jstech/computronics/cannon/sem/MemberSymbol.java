/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.sem;

import dev.jstech.computronics.cannon.ast.Decl;
import java.util.List;
import java.util.Set;

/**
 * Something declared inside a type: a field, a method, a constructor, a property or an event.
 *
 * <p>Each one knows the type it belongs to, so a lookup that walked up through the base classes can
 * still say where what it found was written.
 */
public sealed interface MemberSymbol {

    /** What the member is called; a constructor is called by its type's name. */
    String name();

    /** The type that declares it. */
    NamedType owner();

    /** The words it was declared with. */
    Set<Decl.Modifier> modifiers();

    /** Whether it belongs to the type rather than to an instance of it. */
    default boolean isStatic() {
        return this.modifiers().contains(Decl.Modifier.STATIC);
    }

    /** One parameter of a method or a constructor. */
    record ParameterSymbol(String name, TypeSymbol type) {
    }

    /** A field. */
    record FieldSymbol(NamedType owner, String name, TypeSymbol type, Set<Decl.Modifier> modifiers)
            implements MemberSymbol {

        /** Whether it may only be written where it is declared or in a constructor. */
        public boolean isReadOnly() {
            return this.modifiers.contains(Decl.Modifier.READONLY);
        }
    }

    /** A method, including a delegate's invoke shape. */
    record MethodSymbol(NamedType owner, String name, TypeSymbol returnType, List<ParameterSymbol> parameters,
                        Set<Decl.Modifier> modifiers) implements MemberSymbol {

        public MethodSymbol {
            parameters = List.copyOf(parameters);
        }

        /** How a diagnostic writes the method, with the types it takes. */
        public String describe() {
            final StringBuilder text = new StringBuilder(this.name).append('(');
            for (int i = 0; i < this.parameters.size(); i++) {
                text.append(i > 0 ? ", " : "").append(this.parameters.get(i).type().describe());
            }
            return text.append(')').toString();
        }
    }

    /** A constructor. */
    record ConstructorSymbol(NamedType owner, List<ParameterSymbol> parameters, Set<Decl.Modifier> modifiers)
            implements MemberSymbol {

        public ConstructorSymbol {
            parameters = List.copyOf(parameters);
        }

        @Override
        public String name() {
            return this.owner.name();
        }
    }

    /** A property in the short form, with whichever halves it was given. */
    record PropertySymbol(NamedType owner, String name, TypeSymbol type, boolean readable, boolean writable,
                          Set<Decl.Modifier> modifiers, Set<Decl.Modifier> setterModifiers)
            implements MemberSymbol {
    }

    /** An event, whose type is always a delegate. */
    record EventSymbol(NamedType owner, String name, NamedType delegateType, Set<Decl.Modifier> modifiers)
            implements MemberSymbol {
    }
}
