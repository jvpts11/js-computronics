/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.run;

import dev.jstech.computronics.cannon.asm.Operand;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The part of the library the runtime answers for itself.
 *
 * <p>These are the calls a program can make that are pure calculation or that touch nothing but the
 * process: text, the two collections, the numbers, the console it writes to, and the joining of
 * handlers. What reaches into the world lives elsewhere, beside the network it reads.
 */
public final class Library {

    /** What a call gave back: its answer, and whatever it filled in on the way. */
    public record Answer(Object value, List<Object> filled) {

        public Answer {
            filled = filled == null ? List.of() : new ArrayList<>(filled);
        }

        /** An answer with nothing filled in. */
        static Answer of(final Object value) {
            return new Answer(value, List.of());
        }
    }

    private final Heap heap;
    private final Host host;
    private final List<String> console = new ArrayList<>();
    private final Random random = new Random(0);

    public Library(final Heap heap, final Host host) {
        this.heap = heap;
        this.host = host;
    }

    /** What the process has written, line by line. */
    public List<String> console() {
        return List.copyOf(this.console);
    }

    /** Writes a line to the process's console. */
    public void write(final String line) {
        this.console.add(line);
    }

    /** Whether the runtime, rather than the program, answers for this type. */
    public boolean answersFor(final String owner) {
        return switch (owner) {
            case "string", "List", "Map", "Math", "Console", "Convert", "Time", "Random", "Delegate" -> true;
            default -> false;
        };
    }

    /** Whether a call of this needs the thing it is called on to be on the stack under its arguments. */
    public boolean takesTarget(final String owner, final String name) {
        if ("string".equals(owner)) {
            return !"Format".equals(name) && !"Concat".equals(name);
        }
        return "List".equals(owner) || "Map".equals(owner);
    }

    /** Makes one of the collections the language brings with it. */
    public Object create(final String type, final int line) {
        final String bare = type.contains("<") ? type.substring(0, type.indexOf('<')) : type;
        if ("Map".equals(bare)) {
            final Values.MapValue made = new Values.MapValue();
            return this.heap.allocate(made, made.bytes(), line);
        }
        final Values.ListValue made = new Values.ListValue();
        return this.heap.allocate(made, made.bytes(), line);
    }

    /** Reads one of the things the runtime keeps rather than the program: a length, a count, a tick. */
    public Object read(final Object target, final String name, final int line) {
        if (target instanceof String text && "Length".equals(name)) {
            return text.length();
        }
        if (target instanceof Values.ListValue list && "Count".equals(name)) {
            return list.size();
        }
        if (target instanceof Values.MapValue map && "Count".equals(name)) {
            return map.entries().size();
        }
        throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, "there is no " + name + " to read here");
    }

    /** Reads one of the values the runtime keeps on a type of its own rather than on an object. */
    public Object readStatic(final String owner, final String name, final int line) {
        if ("Time".equals(owner)) {
            return switch (name) {
                case "Tick" -> this.host.tick();
                case "DayTime" -> this.host.dayTime();
                case "Day" -> this.host.day();
                default -> throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, "Time has no " + name);
            };
        }
        throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, owner + " has no " + name);
    }

    /** Runs one of the calls the runtime answers for. */
    public Answer call(final Operand.Method named, final Object self, final List<Object> arguments,
                       final int line) {
        return switch (named.owner()) {
            case "Console" -> this.console(named.name(), arguments);
            case "Math" -> Answer.of(this.maths(named.name(), arguments, line));
            case "Convert" -> this.convert(named.name(), arguments, line);
            case "Random" -> Answer.of(this.chance(named.name(), arguments));
            case "Time" -> Answer.of(Numbers.toLong(arguments.getFirst()) * 20L);
            case "Delegate" -> Answer.of(this.delegates(named.name(), arguments, line));
            case "string" -> Answer.of(this.text(named.name(), self, arguments, line));
            case "List" -> Answer.of(this.list(named.name(), self, arguments, line));
            case "Map" -> this.map(named.name(), self, arguments, line);
            default -> throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line,
                    "the runtime does not answer for " + named.owner());
        };
    }

    private Answer console(final String name, final List<Object> arguments) {
        switch (name) {
            case "Print", "PrintLine" -> this.console.add(String.valueOf(arguments.getFirst()));
            default -> this.console.clear();
        }
        return Answer.of(null);
    }

    private Object maths(final String name, final List<Object> arguments, final int line) {
        final Object first = arguments.getFirst();
        final boolean real = first instanceof Double || first instanceof Float;
        return switch (name) {
            case "Abs" -> real ? (Object) Math.abs(Numbers.toDouble(first))
                    : (Object) Math.abs(Numbers.toInt(first));
            case "Min" -> real ? (Object) Math.min(Numbers.toDouble(first), Numbers.toDouble(arguments.get(1)))
                    : (Object) Math.min(Numbers.toInt(first), Numbers.toInt(arguments.get(1)));
            case "Max" -> real ? (Object) Math.max(Numbers.toDouble(first), Numbers.toDouble(arguments.get(1)))
                    : (Object) Math.max(Numbers.toInt(first), Numbers.toInt(arguments.get(1)));
            case "Clamp" -> real
                    ? (Object) Math.min(Math.max(Numbers.toDouble(first), Numbers.toDouble(arguments.get(1))),
                            Numbers.toDouble(arguments.get(2)))
                    : (Object) Math.min(Math.max(Numbers.toInt(first), Numbers.toInt(arguments.get(1))),
                            Numbers.toInt(arguments.get(2)));
            case "Floor" -> Math.floor(Numbers.toDouble(first));
            case "Ceil" -> Math.ceil(Numbers.toDouble(first));
            case "Round" -> (double) Math.round(Numbers.toDouble(first));
            case "Sqrt" -> Math.sqrt(Numbers.toDouble(first));
            case "Pow" -> Math.pow(Numbers.toDouble(first), Numbers.toDouble(arguments.get(1)));
            default -> throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, "Math has no " + name);
        };
    }

    private Answer convert(final String name, final List<Object> arguments, final int line) {
        final Object first = arguments.getFirst();
        switch (name) {
            case "ToString" -> {
                return Answer.of(this.made(String.valueOf(first), line));
            }
            case "TryInt" -> {
                try {
                    return new Answer(true, List.of(Integer.parseInt(String.valueOf(first).trim())));
                } catch (final NumberFormatException notANumber) {
                    return new Answer(false, List.of(0));
                }
            }
            default -> {
                return Answer.of(this.number(name, String.valueOf(first), line));
            }
        }
    }

    private Object number(final String name, final String text, final int line) {
        try {
            return switch (name) {
                case "ToInt" -> Integer.parseInt(text.trim());
                case "ToLong" -> Long.parseLong(text.trim());
                case "ToDouble" -> Double.parseDouble(text.trim());
                default -> throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, "Convert has no " + name);
            };
        } catch (final NumberFormatException notANumber) {
            throw new Halt(Halt.Reason.BAD_CAST, line, "'" + text + "' is not a number");
        }
    }

    private Object chance(final String name, final List<Object> arguments) {
        return switch (name) {
            case "Next" -> this.random.nextInt(Math.max(1, Numbers.toInt(arguments.getFirst())));
            case "NextDouble" -> this.random.nextDouble();
            default -> {
                this.random.setSeed(Numbers.toLong(arguments.getFirst()));
                yield null;
            }
        };
    }

    // Joining two handlers makes a third that calls both. Parting takes the last one that matches,
    // which is how a listener removes only what it added.
    private Object delegates(final String name, final List<Object> arguments, final int line) {
        final Object left = arguments.getFirst();
        final Object right = arguments.get(1);
        if (!(right instanceof Values.DelegateValue added)) {
            return left;
        }
        final List<Values.Bound> chain = new ArrayList<>();
        if (left instanceof Values.DelegateValue held) {
            chain.addAll(held.chain());
        }
        if ("Combine".equals(name)) {
            chain.addAll(added.chain());
        } else {
            for (int i = chain.size() - 1; i >= 0; i--) {
                if (chain.get(i).equals(added.chain().getFirst())) {
                    chain.remove(i);
                    break;
                }
            }
        }
        if (chain.isEmpty()) {
            return null;
        }
        final Values.DelegateValue made = new Values.DelegateValue(added.type(), chain);
        return this.heap.allocate(made, made.bytes(), line);
    }

    private Object text(final String name, final Object self, final List<Object> arguments, final int line) {
        if ("Concat".equals(name)) {
            return this.made(String.valueOf(arguments.getFirst()) + String.valueOf(arguments.get(1)), line);
        }
        if ("Format".equals(name)) {
            return this.made(this.format(arguments), line);
        }
        final String value = String.valueOf(self);
        return switch (name) {
            case "Substring" -> this.made(arguments.size() == 1
                    ? value.substring(Numbers.toInt(arguments.getFirst()))
                    : value.substring(Numbers.toInt(arguments.getFirst()),
                            Numbers.toInt(arguments.getFirst()) + Numbers.toInt(arguments.get(1))), line);
            case "IndexOf" -> value.indexOf(String.valueOf(arguments.getFirst()));
            case "Contains" -> value.contains(String.valueOf(arguments.getFirst()));
            case "StartsWith" -> value.startsWith(String.valueOf(arguments.getFirst()));
            case "EndsWith" -> value.endsWith(String.valueOf(arguments.getFirst()));
            case "ToUpper" -> this.made(value.toUpperCase(java.util.Locale.ROOT), line);
            case "ToLower" -> this.made(value.toLowerCase(java.util.Locale.ROOT), line);
            case "Trim" -> this.made(value.strip(), line);
            case "Replace" -> this.made(value.replace(String.valueOf(arguments.getFirst()),
                    String.valueOf(arguments.get(1))), line);
            case "Split" -> this.split(value, arguments.getFirst(), line);
            default -> throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, "a string has no " + name);
        };
    }

    private String format(final List<Object> arguments) {
        String result = String.valueOf(arguments.getFirst());
        for (int i = 1; i < arguments.size(); i++) {
            result = result.replace("{" + (i - 1) + "}", String.valueOf(arguments.get(i)));
        }
        return result;
    }

    private Object split(final String value, final Object on, final int line) {
        final Values.ListValue made = new Values.ListValue();
        this.heap.allocate(made, made.bytes(), line);
        for (final String part : value.split(java.util.regex.Pattern.quote(String.valueOf(on)), -1)) {
            made.items().add(this.made(part, line));
        }
        this.heap.resize(made, made.bytes(), line);
        return made;
    }

    private Object list(final String name, final Object self, final List<Object> arguments, final int line) {
        if (!(self instanceof Values.ListValue held)) {
            throw new Halt(Halt.Reason.NO_OBJECT, line, "there is no list here");
        }
        final Object answer = switch (name) {
            case "Add" -> {
                held.items().add(arguments.getFirst());
                yield null;
            }
            case "Insert" -> {
                held.items().add(Numbers.toInt(arguments.getFirst()), arguments.get(1));
                yield null;
            }
            case "RemoveAt" -> {
                held.get(Numbers.toInt(arguments.getFirst()), line);
                held.items().remove(Numbers.toInt(arguments.getFirst()));
                yield null;
            }
            case "Remove" -> held.items().remove(arguments.getFirst());
            case "Clear" -> {
                held.items().clear();
                yield null;
            }
            case "Contains" -> held.items().contains(arguments.getFirst());
            case "IndexOf" -> held.items().indexOf(arguments.getFirst());
            case "Get" -> held.get(Numbers.toInt(arguments.getFirst()), line);
            case "Set" -> {
                held.set(Numbers.toInt(arguments.getFirst()), arguments.get(1), line);
                yield null;
            }
            case "Sort" -> {
                held.items().sort((left, right) -> compare(left, right));
                yield null;
            }
            default -> throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, "a list has no " + name);
        };
        this.heap.resize(held, held.bytes(), line);
        return answer;
    }

    private Answer map(final String name, final Object self, final List<Object> arguments, final int line) {
        if (!(self instanceof Values.MapValue held)) {
            throw new Halt(Halt.Reason.NO_OBJECT, line, "there is no map here");
        }
        if ("TryGet".equals(name)) {
            final Object found = held.entries().get(arguments.getFirst());
            return new Answer(found != null, List.of(found == null ? 0 : found));
        }
        final Object answer = switch (name) {
            case "Put" -> {
                held.entries().put(arguments.getFirst(), arguments.get(1));
                yield null;
            }
            case "Get" -> held.entries().get(arguments.getFirst());
            case "ContainsKey" -> held.entries().containsKey(arguments.getFirst());
            case "Remove" -> held.entries().remove(arguments.getFirst()) != null;
            case "Keys" -> this.listOf(held.entries().keySet(), line);
            case "Values" -> this.listOf(held.entries().values(), line);
            default -> throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, "a map has no " + name);
        };
        this.heap.resize(held, held.bytes(), line);
        return Answer.of(answer);
    }

    private Object listOf(final Iterable<Object> values, final int line) {
        final Values.ListValue made = new Values.ListValue();
        this.heap.allocate(made, made.bytes(), line);
        for (final Object value : values) {
            made.items().add(value);
        }
        this.heap.resize(made, made.bytes(), line);
        return made;
    }

    private static int compare(final Object left, final Object right) {
        if (left instanceof String first && right instanceof String second) {
            return first.compareTo(second);
        }
        return Numbers.compare(left, right);
    }

    private String made(final String value, final int line) {
        return this.heap.allocate(new String(value.toCharArray()), Heap.sizeOfText(value), line);
    }
}
