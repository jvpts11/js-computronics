/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.asm;

import java.util.List;

/**
 * The one thing an instruction may carry beside its name.
 *
 * <p>Which of these an instruction takes is fixed by the instruction itself, so a listing can be
 * read back without guessing: {@code ldc.i4} is always followed by a whole number and {@code br} is
 * always followed by a label.
 */
public sealed interface Operand {

    /** How the operand is written in the listing. */
    String write();

    /** A whole number that fits in four bytes, which is also how bools and characters travel. */
    record I4(int value) implements Operand {

        @Override
        public String write() {
            return Integer.toString(this.value);
        }
    }

    /** A whole number that needs eight bytes. */
    record I8(long value) implements Operand {

        @Override
        public String write() {
            return Long.toString(this.value);
        }
    }

    /** A four-byte real. */
    record R4(float value) implements Operand {

        @Override
        public String write() {
            return Float.toString(this.value);
        }
    }

    /** An eight-byte real. */
    record R8(double value) implements Operand {

        @Override
        public String write() {
            return Double.toString(this.value);
        }
    }

    /** Text, written between quotes with the same escapes the language uses. */
    record Text(String value) implements Operand {

        @Override
        public String write() {
            return quote(this.value);
        }
    }

    /** One of the numbered places a method keeps its parameters and its locals in. */
    record Slot(int index) implements Operand {

        @Override
        public String write() {
            return Integer.toString(this.index);
        }
    }

    /** A place in the same method that a branch can jump to. */
    record Label(String name) implements Operand {

        @Override
        public String write() {
            return this.name;
        }
    }

    /**
     * A field. {@code owner} is null when it belongs to the type the method is in, which is the
     * common case and reads better without repeating the name on every line.
     */
    record Field(String owner, String name) implements Operand {

        @Override
        public String write() {
            return this.owner == null ? this.name : this.owner + "." + this.name;
        }
    }

    /** A method, written with the types it takes and the type it gives back. */
    record Method(String owner, String name, List<String> parameters, String returns) implements Operand {

        public Method {
            parameters = List.copyOf(parameters);
        }

        @Override
        public String write() {
            return this.owner + "." + this.name + "(" + String.join(", ", this.parameters) + ") -> "
                    + this.returns;
        }
    }

    /** A constructor, which is named by its type and gives that type back. */
    record Constructor(String owner, List<String> parameters) implements Operand {

        public Constructor {
            parameters = List.copyOf(parameters);
        }

        @Override
        public String write() {
            return this.owner + "(" + String.join(", ", this.parameters) + ")";
        }
    }

    /** A type, as it is written in the language. */
    record Type(String name) implements Operand {

        @Override
        public String write() {
            return this.name;
        }
    }

    /** Wraps text in quotes and escapes what has to be escaped. */
    static String quote(final String value) {
        final StringBuilder text = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            switch (c) {
                case '\n' -> text.append("\\n");
                case '\t' -> text.append("\\t");
                case '\r' -> text.append("\\r");
                case '\0' -> text.append("\\0");
                case '"' -> text.append("\\\"");
                case '\\' -> text.append("\\\\");
                default -> text.append(c);
            }
        }
        return text.append('"').toString();
    }
}
