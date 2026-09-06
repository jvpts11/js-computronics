/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.ast;

import java.util.List;
import java.util.Set;

/**
 * Everything a program declares: the types at the top of a file and the members inside them.
 *
 * <p>The language requires all code to live in a class, so a file is a list of type declarations and
 * nothing else. A method with no body is recorded rather than rejected here, because whether that is
 * allowed depends on where it sits: it is how an interface declares one, and a mistake in a class.
 */
public sealed interface Decl extends Node {

    /** The access and storage words a declaration may carry. */
    enum Modifier {
        PUBLIC("public"),
        PRIVATE("private"),
        PROTECTED("protected"),
        STATIC("static"),
        READONLY("readonly");

        private final String text;

        Modifier(final String text) {
            this.text = text;
        }

        /** How the modifier is written. */
        public String text() {
            return this.text;
        }
    }

    /** What the declaration is called. */
    String name();

    /** The words it was declared with. */
    Set<Modifier> modifiers();

    /** A type declared at the top of a file. */
    sealed interface TypeDecl extends Decl {
    }

    /** A declaration inside a type. */
    sealed interface MemberDecl extends Decl {
    }

    /** One parameter of a method, a constructor, a delegate or a lambda. */
    record Parameter(TypeRef type, String name, int line, int column) implements Node {
    }

    /** A class, with the base type and interfaces it was written with. */
    record ClassDecl(Set<Modifier> modifiers, String name, List<TypeRef> bases, List<MemberDecl> members,
                     int line, int column) implements TypeDecl {
    }

    /** An interface and the methods it requires. */
    record InterfaceDecl(Set<Modifier> modifiers, String name, List<TypeRef> bases, List<MethodDecl> methods,
                         int line, int column) implements TypeDecl {
    }

    /** One name in an enum; {@code value} is null when the number was left to follow the previous one. */
    record EnumConstant(String name, Expr value, int line, int column) implements Node {
    }

    /** An enum, backed by int. */
    record EnumDecl(Set<Modifier> modifiers, String name, List<EnumConstant> constants, int line, int column)
            implements TypeDecl {
    }

    /** A delegate type: the shape of a method that can be handed around. */
    record DelegateDecl(Set<Modifier> modifiers, TypeRef returnType, String name, List<Parameter> parameters,
                        int line, int column) implements TypeDecl {
    }

    /** A field; {@code initializer} is null when it was declared without one. */
    record FieldDecl(Set<Modifier> modifiers, TypeRef type, String name, Expr initializer, int line, int column)
            implements MemberDecl {
    }

    /** A method; {@code body} is null when it was declared without one. */
    record MethodDecl(Set<Modifier> modifiers, TypeRef returnType, String name, List<Parameter> parameters,
                      Stmt.Block body, int line, int column) implements MemberDecl {
    }

    /** The call of another constructor that a constructor may start with. */
    record ConstructorCall(boolean base, List<Expr> arguments, int line, int column) implements Node {
    }

    /** A constructor; {@code chained} is null when it does not start by calling another one. */
    record ConstructorDecl(Set<Modifier> modifiers, String name, List<Parameter> parameters,
                           ConstructorCall chained, Stmt.Block body, int line, int column) implements MemberDecl {
    }

    /** One half of a property, with the access it was given if it differs from the property's. */
    record Accessor(Set<Modifier> modifiers, int line, int column) implements Node {
    }

    /** A property in the short form; either accessor may be absent. */
    record PropertyDecl(Set<Modifier> modifiers, TypeRef type, String name, Accessor getter, Accessor setter,
                        int line, int column) implements MemberDecl {
    }

    /** An event, whose type is a delegate. */
    record EventDecl(Set<Modifier> modifiers, TypeRef type, String name, int line, int column)
            implements MemberDecl {
    }
}
