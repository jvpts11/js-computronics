/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.run;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a running program holds.
 *
 * <p>Numbers, bools and characters are the boxed ones Java already has, because they are values and
 * two of them that are equal are the same. Everything else is one of the kinds here, and every one
 * of those is a thing on the heap with a size and a life the program controls.
 */
public final class Values {

    private Values() {
    }

    /** An instance of a class the program declared, or of the object a lambda keeps things in. */
    public static final class Obj {

        private final String type;
        private final Map<String, Object> fields = new LinkedHashMap<>();

        public Obj(final String type) {
            this.type = type;
        }

        /** The name of the class this is one of. */
        public String type() {
            return this.type;
        }

        /** What that field holds. */
        public Object get(final String name) {
            return this.fields.get(name);
        }

        /** Puts something in that field. */
        public void set(final String name, final Object value) {
            this.fields.put(name, value);
        }

        @Override
        public String toString() {
            return this.type;
        }
    }

    /** A fixed run of values, all of the same type. */
    public static final class Arr {

        private final String element;
        private final Object[] values;

        public Arr(final String element, final int length) {
            this.element = element;
            this.values = new Object[length];
        }

        /** The type of what it holds. */
        public String element() {
            return this.element;
        }

        /** How many it holds. */
        public int length() {
            return this.values.length;
        }

        /** What is at that place. */
        public Object get(final int index, final int line) {
            this.check(index, line);
            return this.values[index];
        }

        /** Puts something at that place. */
        public void set(final int index, final Object value, final int line) {
            this.check(index, line);
            this.values[index] = value;
        }

        private void check(final int index, final int line) {
            if (index < 0 || index >= this.values.length) {
                throw new Halt(Halt.Reason.OUT_OF_RANGE, line,
                        "there is no place " + index + " in an array of " + this.values.length);
            }
        }
    }

    /** A run of values that grows. */
    public static final class ListValue {

        private final List<Object> items = new ArrayList<>();

        /** What it holds, in order. */
        public List<Object> items() {
            return this.items;
        }

        /** How many it holds. */
        public int size() {
            return this.items.size();
        }

        /** What is at that place. */
        public Object get(final int index, final int line) {
            this.check(index, line);
            return this.items.get(index);
        }

        /** Puts something at that place. */
        public void set(final int index, final Object value, final int line) {
            this.check(index, line);
            this.items.set(index, value);
        }

        private void check(final int index, final int line) {
            if (index < 0 || index >= this.items.size()) {
                throw new Halt(Halt.Reason.OUT_OF_RANGE, line,
                        "there is no place " + index + " in a list of " + this.items.size());
            }
        }

        /** What the list costs: its own header and a reference for each thing in it. */
        public long bytes() {
            return Heap.HEADER + (long) Heap.REFERENCE * this.items.size();
        }
    }

    /** Values reached by a key. */
    public static final class MapValue {

        private final Map<Object, Object> entries = new LinkedHashMap<>();

        /** What it holds, by key. */
        public Map<Object, Object> entries() {
            return this.entries;
        }

        /** What the map costs: its own header and a pair of references for each entry. */
        public long bytes() {
            return Heap.HEADER + 2L * Heap.REFERENCE * this.entries.size();
        }
    }

    /** One method bound to the object it belongs to. */
    public record Bound(Object target, String owner, String method, List<String> parameters,
                        String returns) {

        public Bound {
            parameters = List.copyOf(parameters);
        }
    }

    /**
     * A handler, or a run of them.
     *
     * <p>Joining two delegates makes a third that calls both in turn, which is what lets an event
     * have more than one listener without the event itself knowing how many.
     */
    public static final class DelegateValue {

        private final String type;
        private final List<Bound> chain;

        public DelegateValue(final String type, final List<Bound> chain) {
            this.type = type;
            this.chain = List.copyOf(chain);
        }

        /** The delegate type this is one of. */
        public String type() {
            return this.type;
        }

        /** The methods it calls, in order. */
        public List<Bound> chain() {
            return this.chain;
        }

        /** What it costs: its header, and a target and a method for each one in the run. */
        public long bytes() {
            return Heap.HEADER + 2L * Heap.REFERENCE * this.chain.size();
        }
    }
}
