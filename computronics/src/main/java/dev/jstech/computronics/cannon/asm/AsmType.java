/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.asm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * One type of the assembly.
 *
 * <p>A property is not a kind of its own here. In the short form the language allows, a property is
 * a field with a rule about who may write it, and that rule was settled before the assembly was
 * written, so the listing shows the field.
 */
public final class AsmType {

    /** Which of the four kinds a type is, written as the directive that opens it. */
    public enum Kind {
        CLASS("class"),
        INTERFACE("interface"),
        ENUM("enum"),
        DELEGATE("delegate");

        private final String text;

        Kind(final String text) {
            this.text = text;
        }

        /** The word after the dot that opens this kind. */
        public String text() {
            return this.text;
        }

        /** The kind that directive opens, or null. */
        public static Kind written(final String text) {
            for (final Kind kind : values()) {
                if (kind.text.equals(text)) {
                    return kind;
                }
            }
            return null;
        }
    }

    /** One field, with the type it holds. */
    public record Field(String name, String type, boolean isStatic) {
    }

    /** One event, with the delegate it hands values to. */
    public record Event(String name, String type) {
    }

    /** One name in an enum, with the number behind it. */
    public record Value(String name, int number) {
    }

    private final Kind kind;
    private final String name;
    private final List<String> bases = new ArrayList<>();
    private final List<Field> fields = new ArrayList<>();
    private final List<Event> events = new ArrayList<>();
    private final List<Value> values = new ArrayList<>();
    private final List<AsmMethod> methods = new ArrayList<>();
    private AsmMethod invoke;

    public AsmType(final Kind kind, final String name) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.name = Objects.requireNonNull(name, "name");
    }

    /** Which of the four kinds this is. */
    public Kind kind() {
        return this.kind;
    }

    /** What the type is called. */
    public String name() {
        return this.name;
    }

    /** The base class and the interfaces, in the order they were written. */
    public List<String> bases() {
        return List.copyOf(this.bases);
    }

    /** The fields it holds, including the ones a property is kept in. */
    public List<Field> fields() {
        return List.copyOf(this.fields);
    }

    /** The events it declares. */
    public List<Event> events() {
        return List.copyOf(this.events);
    }

    /** The names of an enum, with their numbers. */
    public List<Value> values() {
        return List.copyOf(this.values);
    }

    /** The methods it declares. */
    public List<AsmMethod> methods() {
        return List.copyOf(this.methods);
    }

    /** For a delegate, the shape of the method it stands for. */
    public AsmMethod invoke() {
        return this.invoke;
    }

    /** Adds a base class or an interface. */
    public void addBase(final String base) {
        this.bases.add(base);
    }

    /** Adds a field. */
    public void addField(final Field field) {
        this.fields.add(field);
    }

    /** Adds an event. */
    public void addEvent(final Event event) {
        this.events.add(event);
    }

    /** Adds one of an enum's names. */
    public void addValue(final Value value) {
        this.values.add(value);
    }

    /** Adds a method. */
    public void addMethod(final AsmMethod method) {
        this.methods.add(method);
    }

    /** Sets a delegate's shape. */
    public void setInvoke(final AsmMethod invoke) {
        this.invoke = invoke;
    }
}
