/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.run;

import dev.jstech.computronics.cannon.asm.AsmMethod;
import dev.jstech.computronics.cannon.asm.AsmProgram;
import dev.jstech.computronics.cannon.asm.AsmType;
import dev.jstech.computronics.cannon.asm.Instruction;
import dev.jstech.computronics.cannon.asm.Opcode;
import dev.jstech.computronics.cannon.asm.Operand;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A program made ready to run.
 *
 * <p>The listing is text, and text is what a player reads, not what a machine should chase down a
 * line at a time. So it is turned into this once: labels become the numbers of the lines they mark,
 * methods are found by the name and the types they take, and a type knows the one it stands on. From
 * then on nothing is looked up by reading text again.
 */
public final class Loaded {

    /** One method, with its lines and its labels already resolved. */
    public record Method(String owner, String name, List<String> parameters, String returns,
                         boolean isStatic, int slots, List<Instruction> code, Map<String, Integer> labels) {

        public Method {
            parameters = List.copyOf(parameters);
            code = code == null ? List.of() : List.copyOf(code);
            labels = Map.copyOf(labels);
        }

        /** Whether the parameter at that place is one the method fills in. */
        public boolean fillsIn(final int index) {
            return this.parameters.get(index).startsWith("out ");
        }

        /** Whether it hands anything back. */
        public boolean gives() {
            return !"void".equals(this.returns);
        }

        /** How the method is written where it is called. */
        public String describe() {
            return this.owner + "." + this.name + "(" + String.join(", ", this.parameters) + ")";
        }
    }

    /**
     * One type, with what it holds and what it can do.
     *
     * <p>{@code setUp} is the method named after the type and marked static: it puts the starting
     * values in the type's own fields, and it is kept apart because a constructor of a class with no
     * parameters is written the same way and the two are not the same thing.
     */
    public record Type(String name, AsmType.Kind kind, List<String> bases, List<AsmType.Field> fields,
                       Map<String, Integer> values, Map<String, Method> methods, Method invoke,
                       Method setUp) {

        public Type {
            bases = List.copyOf(bases);
        }
    }

    private final Map<String, Type> types = new LinkedHashMap<>();
    private final String entryPoint;

    private Loaded(final AsmProgram program) {
        this.entryPoint = program.entryPoint();
        for (final AsmType type : program.types()) {
            this.types.put(type.name(), load(type));
        }
    }

    /** Makes a program ready to run. */
    public static Loaded of(final AsmProgram program) {
        return new Loaded(program);
    }

    /** The type of that name, or null when the runtime provides it instead of the program. */
    public Type type(final String name) {
        return this.types.get(name);
    }

    /** The class the runtime starts from, or null for a library. */
    public String entryPoint() {
        return this.entryPoint;
    }

    /** Every type the program declares. */
    public List<Type> types() {
        return List.copyOf(this.types.values());
    }

    /**
     * The class a type stands on, or null.
     *
     * <p>The listing writes the class and the interfaces on one line, in the order they were written,
     * and does not mark which is which. It does not have to: every name there belongs to the program,
     * so which of them is a class is a question the program itself answers.
     */
    public String baseOf(final String name) {
        final Type type = this.types.get(name);
        if (type == null) {
            return null;
        }
        for (final String base : type.bases()) {
            final Type other = this.types.get(base);
            if (other != null && other.kind() == AsmType.Kind.CLASS) {
                return base;
            }
        }
        return null;
    }

    /**
     * The method of that shape on that type, looking up through what the type stands on.
     *
     * <p>An instance call looks on the runtime type of the object first, which is what makes a call
     * through an interface reach the class that implements it.
     */
    public Method method(final String owner, final String name, final List<String> parameters) {
        for (String at = owner; at != null;) {
            final Type type = this.types.get(at);
            if (type == null) {
                return null;
            }
            final Method found = type.methods().get(key(name, parameters));
            if (found != null) {
                return found;
            }
            at = this.baseOf(at);
        }
        return null;
    }

    /** The field of that name on that type or on one it stands on, or null. */
    public AsmType.Field field(final String owner, final String name) {
        for (String at = owner; at != null;) {
            final Type type = this.types.get(at);
            if (type == null) {
                return null;
            }
            for (final AsmType.Field field : type.fields()) {
                if (field.name().equals(name)) {
                    return field;
                }
            }
            at = this.baseOf(at);
        }
        return null;
    }

    /** Whether a type is, or stands on, another. */
    public boolean isA(final String type, final String other) {
        if (type == null || other == null) {
            return false;
        }
        if (type.equals(other)) {
            return true;
        }
        final Type known = this.types.get(type);
        if (known == null) {
            return false;
        }
        for (final String base : known.bases()) {
            if (this.isA(base, other)) {
                return true;
            }
        }
        return false;
    }

    private static Type load(final AsmType type) {
        final Map<String, Method> methods = new LinkedHashMap<>();
        Method setUp = null;
        for (final AsmMethod method : type.methods()) {
            final Method loaded = load(type.name(), method);
            if (method.isStatic() && method.name().equals(type.name()) && method.parameters().isEmpty()) {
                setUp = loaded;
                continue;
            }
            methods.put(key(method.name(), method.parameters()), loaded);
        }
        final Map<String, Integer> values = new LinkedHashMap<>();
        for (final AsmType.Value value : type.values()) {
            values.put(value.name(), value.number());
        }
        return new Type(type.name(), type.kind(), type.bases(), type.fields(), values, methods,
                type.invoke() == null ? null : load(type.name(), type.invoke()), setUp);
    }

    private static Method load(final String owner, final AsmMethod method) {
        final Map<String, Integer> labels = new LinkedHashMap<>();
        if (method.hasBody()) {
            for (int i = 0; i < method.body().size(); i++) {
                final String label = method.body().get(i).label();
                if (label != null) {
                    labels.put(label, i);
                }
            }
        }
        return new Method(owner, method.name(), method.parameters(), method.returns(), method.isStatic(),
                method.slots(), method.body(), labels);
    }

    private static String key(final String name, final List<String> parameters) {
        return name + "(" + String.join(", ", parameters) + ")";
    }

    /** Where a branch goes, or the end of the method when nothing carries that label. */
    public static int target(final Method method, final Instruction instruction) {
        if (instruction.operand() instanceof Operand.Label label) {
            final Integer at = method.labels().get(label.name());
            if (at != null) {
                return at;
            }
        }
        return method.code().size();
    }

    /** Whether an instruction moves somewhere other than the next line. */
    public static boolean branches(final Opcode opcode) {
        return opcode.branches();
    }
}
