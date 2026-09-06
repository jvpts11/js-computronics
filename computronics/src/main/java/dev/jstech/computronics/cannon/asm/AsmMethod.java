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
 * One method of the assembly: what it takes, what it gives back, how many places it needs, and the
 * lines it runs.
 *
 * <p>Parameters and locals share one numbered set of places, parameters first, so an instruction
 * only ever needs one number to reach either. {@code slots} is how many of them the method needs at
 * once, which is what the runtime allocates when the method is entered.
 *
 * <p>A method with no body is a signature an interface asks for, or the shape a delegate stands for.
 */
public record AsmMethod(String name, String returns, List<String> parameters, boolean isStatic,
                        int slots, List<Instruction> body) {

    public AsmMethod {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(returns, "returns");
        parameters = List.copyOf(parameters);
        body = body == null ? null : List.copyOf(body);
        if (slots < 0) {
            throw new IllegalArgumentException("a method cannot need a negative number of places");
        }
    }

    /** Whether the method has lines of its own. */
    public boolean hasBody() {
        return this.body != null;
    }

    /** How the method is written on its own line, without the places or the body. */
    public String signature() {
        return this.returns + " " + this.name + "(" + String.join(", ", this.parameters) + ")";
    }

    /**
     * Gathers a method as its lines are read, one at a time, keeping the line each came from so a
     * branch to a label that is never marked can be reported where it was written.
     */
    public static final class Builder {

        private final String name;
        private final String returns;
        private final List<String> parameters;
        private final boolean isStatic;
        private final int slots;
        private final boolean hasBody;
        private final List<Instruction> body = new ArrayList<>();
        private final List<Integer> lines = new ArrayList<>();

        public Builder(final String name, final String returns, final List<String> parameters,
                       final boolean isStatic, final int slots, final boolean hasBody) {
            this.name = name;
            this.returns = returns;
            this.parameters = List.copyOf(parameters);
            this.isStatic = isStatic;
            this.slots = slots;
            this.hasBody = hasBody;
        }

        /** Adds one line, with the line of the listing it came from. */
        public void add(final Instruction instruction, final int line) {
            this.body.add(instruction);
            this.lines.add(line);
        }

        /** The labels a branch could name, and the line each branch is on. */
        public List<Instruction> instructions() {
            return List.copyOf(this.body);
        }

        /** The line of the listing instruction {@code index} came from. */
        public int lineOf(final int index) {
            return this.lines.get(index);
        }

        /** The finished method. */
        public AsmMethod build() {
            return new AsmMethod(this.name, this.returns, this.parameters, this.isStatic,
                    this.slots, this.hasBody ? this.body : null);
        }
    }
}
