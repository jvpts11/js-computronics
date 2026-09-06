/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.sem;

import java.util.List;

/**
 * A type, once it has been resolved to the thing it names.
 *
 * <p>A type reference in the tree is text; a type symbol is the type itself, shared by every place
 * that mentions it. Two extra members exist so one mistake does not cascade into ten: {@link
 * Special#ERROR} stands for a type that could not be resolved and is convertible to and from
 * everything, and {@link Special#NULL} is the type of the null literal, which fits any reference.
 */
public sealed interface TypeSymbol
        permits TypeSymbol.Primitive, TypeSymbol.Special, TypeSymbol.ArrayType,
                TypeSymbol.GenericType, TypeSymbol.TypeParameter, NamedType {

    /** How the type is written, which is how a diagnostic quotes it. */
    String describe();

    /** The value types built into the language. */
    enum Primitive implements TypeSymbol {
        INT("int"),
        LONG("long"),
        FLOAT("float"),
        DOUBLE("double"),
        BOOL("bool"),
        CHAR("char"),
        VOID("void");

        private final String text;

        Primitive(final String text) {
            this.text = text;
        }

        @Override
        public String describe() {
            return this.text;
        }

        /** Whether arithmetic applies to this type. */
        public boolean isNumeric() {
            return this != BOOL && this != VOID;
        }

        /** The primitive written that way, or null. */
        public static Primitive written(final String text) {
            for (final Primitive primitive : values()) {
                if (primitive.text.equals(text)) {
                    return primitive;
                }
            }
            return null;
        }
    }

    /** The two types that exist for the compiler's own sake rather than the player's. */
    enum Special implements TypeSymbol {
        ERROR("?"),
        NULL("null");

        private final String text;

        Special(final String text) {
            this.text = text;
        }

        @Override
        public String describe() {
            return this.text;
        }
    }

    /** An array of something. */
    record ArrayType(TypeSymbol element) implements TypeSymbol {

        @Override
        public String describe() {
            return this.element.describe() + "[]";
        }
    }

    /** One of the built-in collections with its arguments filled in. */
    record GenericType(NamedType definition, List<TypeSymbol> arguments) implements TypeSymbol {

        public GenericType {
            arguments = List.copyOf(arguments);
        }

        @Override
        public String describe() {
            final StringBuilder text = new StringBuilder(this.definition.name()).append('<');
            for (int i = 0; i < this.arguments.size(); i++) {
                text.append(i > 0 ? ", " : "").append(this.arguments.get(i).describe());
            }
            return text.append('>').toString();
        }
    }

    /**
     * A stand-in for one of a built-in collection's arguments, as it appears in that collection's
     * own members before a use site says what it holds.
     */
    record TypeParameter(String name, int index) implements TypeSymbol {

        @Override
        public String describe() {
            return this.name;
        }
    }
}
