/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.run;

import java.util.List;
import java.util.Map;

/**
 * A running program, frozen.
 *
 * <p>A process that stopped because the world was put away should carry on where it left off when the
 * world comes back, which means everything it was holding has to be written down: what it had
 * allocated, what each frame was doing, and where each of them was in its method.
 *
 * <p>Everything here is plain data with no Minecraft in it, so freezing and thawing can be tested on
 * its own, and whatever writes it to a save file is a thin layer over records that already hold the
 * whole truth. References between allocated things become numbers, because two objects can point at
 * each other and a tree cannot say that.
 */
public record Snapshot(long heapBudget, List<Held> held, List<FrameShot> frames, List<FrameShot> waiting,
                       Map<String, Map<String, Value>> statics, List<String> console,
                       String state, String message, int spent) {

    public Snapshot {
        held = List.copyOf(held);
        frames = List.copyOf(frames);
        waiting = List.copyOf(waiting);
        statics = Map.copyOf(statics);
        console = List.copyOf(console);
    }

    /**
     * One value, as a slot or a stack holds it.
     *
     * <p>A number is written as itself; anything allocated is written as the number of the thing it
     * points at, so two names for one object come back as two names for one object.
     */
    public sealed interface Value {

        /** Nothing at all. */
        record Nothing() implements Value {
        }

        /** A whole number of four bytes, which is also how a bool and a character travel. */
        record I4(int value) implements Value {
        }

        /** A whole number of eight bytes. */
        record I8(long value) implements Value {
        }

        /** A real of four bytes. */
        record R4(float value) implements Value {
        }

        /** A real of eight bytes. */
        record R8(double value) implements Value {
        }

        /** True or false. */
        record Bool(boolean value) implements Value {
        }

        /** One character. */
        record Ch(char value) implements Value {
        }

        /** Something on the heap, by the number it was written down under. */
        record Ref(int id) implements Value {
        }
    }

    /** One method bound to what it belongs to, as a delegate holds it. */
    public record BoundShot(Value target, String owner, String method, List<String> parameters,
                            String returns) {

        public BoundShot {
            parameters = List.copyOf(parameters);
        }
    }

    /** One thing the program had allocated, with what it costs and where it was made. */
    public sealed interface Held {

        /** The number this thing is written down under. */
        int id();

        /** What it costs. */
        long bytes();

        /** The line of the assembly it was made on. */
        int line();

        /** Whether the program has already freed it. */
        boolean freed();

        /** A piece of text. */
        record Text(int id, long bytes, int line, boolean freed, String value) implements Held {
        }

        /** An instance of a class, with what each of its fields holds. */
        record Object(int id, long bytes, int line, boolean freed, String type,
                      Map<String, Value> fields) implements Held {

            public Object {
                fields = Map.copyOf(fields);
            }
        }

        /** A fixed run of values. */
        record Array(int id, long bytes, int line, boolean freed, String element, List<Value> values)
                implements Held {

            public Array {
                values = List.copyOf(values);
            }
        }

        /** A run of values that grows. */
        record Listing(int id, long bytes, int line, boolean freed, List<Value> items) implements Held {

            public Listing {
                items = List.copyOf(items);
            }
        }

        /** Values reached by a key, written as two runs that line up. */
        record Keyed(int id, long bytes, int line, boolean freed, List<Value> keys, List<Value> values)
                implements Held {

            public Keyed {
                keys = List.copyOf(keys);
                values = List.copyOf(values);
            }
        }

        /** A handler, or a run of them. */
        record Handler(int id, long bytes, int line, boolean freed, String type, List<BoundShot> chain)
                implements Held {

            public Handler {
                chain = List.copyOf(chain);
            }
        }
    }

    /** One call in progress: which method, how far into it, and everything it was holding. */
    public record FrameShot(String owner, String name, List<String> parameters, int at, Value self,
                            List<Value> slots, List<Value> stack, boolean discard) {

        public FrameShot {
            parameters = List.copyOf(parameters);
            slots = List.copyOf(slots);
            stack = List.copyOf(stack);
        }
    }
}
