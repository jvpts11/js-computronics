/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.run;

import dev.jstech.computers.cannon.asm.IOperand;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    private final IHost host;
    private final List<String> console = new ArrayList<>();
    private final Random random = new Random(0);
    private int written;

    /** The class this process was started from, which is how the world knows which program asked. */
    private final String caller;

    public Library(final Heap heap, final IHost host) {
        this(heap, host, "");
    }

    public Library(final Heap heap, final IHost host, final String caller) {
        this.heap = heap;
        this.host = host;
        this.caller = caller == null ? "" : caller;
    }

    /**
     * How many lines of its own output a process keeps.
     *
     * <p>There has to be a limit: what a process has written is part of what is saved with the machine
     * it runs on, and a program printing once a tick would otherwise grow that file for as long as the
     * world exists. What a program said thousands of lines ago is not what anyone reads anyway.
     */
    public static final int CONSOLE_LINES = 200;

    /** What the process has written, line by line, oldest of the ones it still keeps first. */
    public List<String> console() {
        return List.copyOf(this.console);
    }

    /**
     * How many lines the process has written since it started, the ones already dropped included.
     *
     * <p>A terminal showing what a program prints needs to know what it has not shown yet, and the count
     * of what is kept cannot say that once the oldest lines start falling off the end.
     */
    public int written() {
        return this.written;
    }

    /** Writes a line to the process's console, dropping the oldest once it is full. */
    public void write(final String line) {
        this.console.add(line);
        this.written++;
        while (this.console.size() > CONSOLE_LINES) {
            this.console.removeFirst();
        }
    }

    /** Puts back what a process had written before it was put away. */
    public void restore(final List<String> lines, final int written) {
        this.console.clear();
        this.console.addAll(lines);
        while (this.console.size() > CONSOLE_LINES) {
            this.console.removeFirst();
        }
        this.written = Math.max(written, this.console.size());
    }

    /** Whether the runtime, rather than the program, answers for this type. */
    public boolean answersFor(final String owner) {
        return switch (owner) {
            case "string", "List", "Map", "Math", "Console", "Convert", "Time", "Random", "Delegate" -> true;
            default -> this.host.provides(owner);
        };
    }

    /** What the last call cost beyond the one instruction every call costs, and clears it. */
    public int drawCost() {
        final int owed = this.owed;
        this.owed = 0;
        return owed;
    }

    private int owed;

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
        if (this.host.provides(owner)) {
            /*
             * To the machine, being asked for a value and being asked to do something are the same
             * question with different names, so a property goes out as a call that takes nothing.
             */
            final IHost.Reply reply = this.host.call(owner, name, List.of(), this.caller, line);
            this.owed += Math.max(0, reply.cost() - 1);
            return this.adopt(reply.value(), line);
        }
        throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, owner + " has no " + name);
    }

    /** Runs one of the calls the runtime answers for. */
    public Answer call(final IOperand.Method named, final Object self, final List<Object> arguments,
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
            default -> this.watchOrOutward(named, arguments, line);
        };
    }

    /** The process this library serves, for the few calls that are about the program rather than the world. */
    private Process owner;

    void serves(final Process process) {
        this.owner = process;
    }

    /**
     * Asking to be told about something is not the same as asking about it.
     *
     * <p>Reading the world goes out to the machine; watching it stays here, because what is being set up
     * belongs to the program: the process holds the watch, is woken by it, pays for it, and takes it
     * with it across a reload. The machine is only asked what the numbers are, once a tick, for
     * everything being watched at all.
     */
    private Answer watchOrOutward(final IOperand.Method named, final List<Object> arguments,
                                  final int line) {
        if (this.owner == null || !"Network".equals(named.owner()) || !named.name().startsWith("Watch")) {
            return this.outward(named, arguments, line);
        }
        final Process.Watching kind = switch (named.name()) {
            case "WatchBelow" -> Process.Watching.BELOW;
            case "WatchAbove" -> Process.Watching.ABOVE;
            default -> Process.Watching.CHANGE;
        };
        final String item = arguments.isEmpty() ? "" : String.valueOf(arguments.getFirst());
        final long threshold = kind == Process.Watching.CHANGE || arguments.size() < 2 ? 0L
                : Numbers.toLong(arguments.get(1));
        final Object last = arguments.isEmpty() ? null : arguments.getLast();
        if (!(last instanceof Values.DelegateValue handler)) {
            throw new Halt(Halt.Reason.NO_OBJECT, line, "there is no handler to call for " + item);
        }
        return Answer.of(this.owner.watch(item, kind, threshold, handler, line));
    }

    /**
     * Hands a call to the machine, and takes what comes back onto the program's heap.
     *
     * <p>The machine knows nothing of heaps or budgets: it answers with plain values and says what the
     * answer was worth. Everything else about it is settled here, in the one place that already knows how
     * much a thing costs to hold.
     */
    private Answer outward(final IOperand.Method named, final List<Object> arguments, final int line) {
        if (!this.host.provides(named.owner())) {
            throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line,
                    "the runtime does not answer for " + named.owner());
        }
        final IHost.Reply reply =
                this.host.call(named.owner(), named.name(), arguments, this.caller, line);
        this.owed += Math.max(0, reply.cost() - 1);
        final List<Object> filled = new ArrayList<>();
        for (final Object one : reply.filled()) {
            filled.add(this.adopt(one, line));
        }
        return new Answer(this.adopt(reply.value(), line), filled);
    }

    /**
     * Puts something the machine made onto the program's heap, contents and all.
     *
     * <p>What a program is handed is the program's to hold and to free, and it has to weigh what it
     * weighs. Anything already on the heap is left where it is, so handing back something the program
     * gave in the first place does not charge it twice.
     */
    private Object adopt(final Object made, final int line) {
        if (made == null || this.heap.bytesOf(made) > 0) {
            return made;
        }
        switch (made) {
            case String text -> this.heap.allocate(text, Heap.sizeOfText(text), line);
            case Values.ListValue list -> {
                for (int i = 0; i < list.items().size(); i++) {
                    list.items().set(i, this.adopt(list.items().get(i), line));
                }
                this.heap.allocate(list, list.bytes(), line);
            }
            case Values.MapValue map -> {
                final Map<Object, Object> adopted = new LinkedHashMap<>();
                for (final Map.Entry<Object, Object> entry : map.entries().entrySet()) {
                    adopted.put(this.adopt(entry.getKey(), line), this.adopt(entry.getValue(), line));
                }
                map.entries().clear();
                map.entries().putAll(adopted);
                this.heap.allocate(map, map.bytes(), line);
            }
            case Values.Obj object -> {
                for (final Map.Entry<String, Object> field : object.all().entrySet()) {
                    object.set(field.getKey(), this.adopt(field.getValue(), line));
                }
                this.heap.allocate(object, Heap.HEADER
                        + (long) Heap.REFERENCE * object.all().size(), line);
            }
            default -> {
                // A number, a bool or a character: a value, which weighs nothing of its own.
            }
        }
        return made;
    }

    private Answer console(final String name, final List<Object> arguments) {
        switch (name) {
            case "Print", "PrintLine" -> this.write(String.valueOf(arguments.getFirst()));
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

    /*
     * Joining two handlers makes a third that calls both. Parting takes the last one that matches,
     * which is how a listener removes only what it added.
     */
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
