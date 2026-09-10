/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.run;

import dev.jstech.computers.cannon.Shape;
import dev.jstech.computers.cannon.asm.AsmType;
import dev.jstech.computers.cannon.asm.Instruction;
import dev.jstech.computers.cannon.asm.Opcode;
import dev.jstech.computers.cannon.asm.IOperand;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One running program.
 *
 * <p>A process runs in slices. It is given a budget of instructions, spends what it can, and stops
 * where it stands with everything it needs to carry on next time: its frames, its stack and where it
 * was in each of them. That is what lets a loop that would take a minute span a minute of ticks
 * without the game waiting on it, and it is why nothing here ever blocks.
 *
 * <p>Nothing it does reaches outside itself except through the console it writes to and the host it
 * asks the time of, so a whole program can be run and read back with no world around it.
 */
public final class Process {

    /** Where a process is up to. */
    public enum State {
        /** It has instructions left to run. */
        RUNNING,
        /** It is waiting for something outside it and will not spend budget until that settles. */
        PARKED,
        /** It ran to the end. */
        FINISHED,
        /** It stopped on a mistake. */
        HALTED
    }

    /**
     * One call in progress.
     *
     * <p>The stack holds nulls, because null is a value a program can have and hand around, so it is
     * kept in something that allows one rather than in something that treats it as an absence.
     */
    private static final class Frame {
        private final Loaded.Method method;
        private final Object[] slots;
        private final List<Object> stack = new ArrayList<>();
        private final Object self;
        private int at;
        /** Set on all but the last handler of a run, whose answers nobody is waiting for. */
        private boolean discard;

        Frame(final Loaded.Method method, final Object self) {
            this.method = method;
            this.slots = new Object[Math.max(method.slots(), method.parameters().size())];
            this.self = self;
        }

        void push(final Object value) {
            this.stack.add(value);
        }

        Object pop() {
            return this.stack.isEmpty() ? null : this.stack.remove(this.stack.size() - 1);
        }

        Object peek() {
            return this.stack.isEmpty() ? null : this.stack.getLast();
        }
    }

    private final Loaded program;
    private final Heap heap;
    private final Library library;
    private final Deque<Frame> frames = new ArrayDeque<>();
    private final Deque<Frame> waiting = new ArrayDeque<>();
    private final Map<String, Values.Obj> statics = new LinkedHashMap<>();
    private Values.Obj script;
    private State state = State.RUNNING;
    private String message;
    private int spent;

    public Process(final Loaded program, final long heapBytes, final IHost host) {
        this(program, heapBytes, host, true);
    }

    private Process(final Loaded program, final long heapBytes, final IHost host, final boolean fresh) {
        this.program = program;
        this.heap = new Heap(heapBytes);
        this.library = new Library(this.heap, host, program.entryPoint());
        this.library.serves(this);
        if (!fresh) {
            return;
        }
        /*
         * Putting the starting values in a type's own fields is the program's work like any other, so
         * it waits its turn and is paid for out of the budget rather than run on the spot.
         */
        for (final Loaded.Type type : program.types()) {
            if (type.setUp() != null) {
                this.waiting.add(new Frame(type.setUp(), null));
            }
        }
    }

    /** Where the process is up to. */
    public State state() {
        return this.state;
    }

    /** The program it is running, for reading it back after it has been written down. */
    public Loaded program() {
        return this.program;
    }

    /** What it said when it stopped, or null while it is still going. */
    public String message() {
        return this.message;
    }

    /** What it has written to its own console. */
    public List<String> console() {
        return this.library.console();
    }

    /** How many lines it has written since it started, the ones no longer kept included. */
    public int written() {
        return this.library.written();
    }

    /** What it is holding, to the byte. */
    public Heap heap() {
        return this.heap;
    }

    /** How many instructions it has run since it started. */
    public int spent() {
        return this.spent;
    }

    /**
     * Makes an instance of a class the program declares.
     *
     * <p>The object comes back at once and its constructor waits its turn, so making one costs the
     * budget like everything else and a constructor that never ends cannot hold up the tick.
     */
    public Values.Obj create(final String type) {
        final Object made = this.instance(type, List.of(), 0);
        if (!(made instanceof Values.Obj object)) {
            return null;
        }
        if (this.script == null) {
            this.script = object;
        }
        return object;
    }

    /**
     * The script this process is running, which is the first object it was asked to make.
     *
     * <p>Whatever runs the process needs it back to call the script again on the next tick, and needs it
     * back after a reload as well, so it is remembered here rather than by the caller.
     */
    public Values.Obj script() {
        return this.script;
    }

    /** Whether this is a program that runs at a terminal or one that stays up. */
    public Shape shape() {
        return this.program.shape();
    }

    /**
     * Puts a call on a type itself in the queue, for a program that starts at a static method and so
     * never has an instance of anything to be called on.
     */
    public void beginStatic(final String owner, final String method) {
        final Loaded.Method found = this.program.method(owner, method, List.of());
        if (found == null) {
            this.halt(new Halt(Halt.Reason.NO_SUCH_MEMBER, 0, owner + " has no " + method + " to run"));
            return;
        }
        this.waiting.add(new Frame(found, null));
        this.state = State.RUNNING;
    }

    /** Puts a call on that object in the queue, to be run by the slices that follow. */
    public void begin(final Values.Obj self, final String method) {
        final Loaded.Method found = this.program.method(self.type(), method, List.of());
        if (found == null) {
            this.halt(new Halt(Halt.Reason.NO_SUCH_MEMBER, 0,
                    self.type() + " has no " + method + " to run"));
            return;
        }
        this.waiting.add(new Frame(found, self));
        this.state = State.RUNNING;
    }

    /**
     * Runs up to {@code budget} instructions and says how many it used.
     *
     * <p>It stops early when the program finishes, halts, or parks. Whatever it did not use is left,
     * because a process that has nothing to do should not be charged for the tick.
     */
    public int step(final int budget) {
        int used = 0;
        while (used < budget && this.state == State.RUNNING) {
            if (this.frames.isEmpty() && !this.take()) {
                break;
            }
            used++;
            this.spent++;
            try {
                this.one();
            } catch (final Halt halt) {
                this.halt(halt);
            }
            /*
             * Reaching into the machine costs more than moving a number about, and the difference is
             * charged to this tick rather than hidden, so a program that talks to the world all the time
             * gets through less of itself than one that does its own arithmetic.
             */
            final int reached = this.library.drawCost();
            used += reached;
            this.spent += reached;
        }
        if (this.frames.isEmpty() && this.waiting.isEmpty() && this.state == State.RUNNING) {
            this.state = State.FINISHED;
        }
        return used;
    }

    /**
     * Waits for something outside the process, spending nothing until it settles.
     *
     * <p>Nothing here blocks a thread: a process that is waiting simply stops being given budget, and
     * the rest of the computer carries on without it.
     */
    public void park() {
        if (this.state == State.RUNNING) {
            this.state = State.PARKED;
        }
    }

    /** Lets a waiting process have budget again. */
    public void resume() {
        if (this.state == State.PARKED) {
            this.state = State.RUNNING;
        }
    }

    /*
     * Lines typed at the terminal this process is in front of, in the order they came, waiting for the
     * program to read them. Bounded: a terminal keeps what was typed ahead, not everything ever typed.
     */
    private final Deque<String> input = new ArrayDeque<>();
    private static final int INPUT_LINES = 16;

    /** Hands the process a typed line; a process stopped on a read carries on with it. */
    public void offerInput(final String line) {
        if (this.input.size() < INPUT_LINES) {
            this.input.addLast(line == null ? "" : line);
        }
        if (this.waitingForInput()) {
            this.resume();
        }
    }

    /**
     * Whether the process is stopped on a read. Read off the code rather than kept as a flag, so a
     * process put away mid-read and brought back after the world was away is still seen to be waiting.
     */
    public boolean waitingForInput() {
        final Frame frame = this.frames.peek();
        if (this.state != State.PARKED || frame == null || frame.at >= frame.method.code().size()) {
            return false;
        }
        final Instruction next = frame.method.code().get(frame.at);
        return (next.opcode() == Opcode.CALL || next.opcode() == Opcode.CALLVIRT)
                && next.operand() instanceof IOperand.Method named && Library.readsLine(named);
    }

    /** The name the program gave itself, kept with it so a machine lists it by that after a reload. */
    private String name = "";

    /** Names the program, as its own call to {@code Program.SetName} does; blank means no name. */
    void setName(final String value) {
        this.name = value == null ? "" : value.strip();
    }

    /** The name the program gave itself, or empty when it gave none. */
    public String name() {
        return this.name;
    }

    /** The next line typed, or an empty string when none has been. */
    String takeInput() {
        final String line = this.input.pollFirst();
        return line == null ? "" : line;
    }

    /** Whether a typed line is waiting to be read. */
    boolean hasInput() {
        return !this.input.isEmpty();
    }

    /**
     * Puts a handler in the queue, to run when the process next has nothing else to do.
     *
     * <p>Handlers run in the order they arrived, in the same slice as the work that was already
     * there, out of the same budget. So a process that fires a great many of them does not get more
     * of the tick than one that fires none.
     */
    public void post(final Values.DelegateValue handler, final List<Object> arguments) {
        if (handler == null || handler.chain().isEmpty()) {
            return;
        }
        final Values.Bound bound = handler.chain().getFirst();
        final Loaded.Method method =
                this.program.method(bound.owner(), bound.method(), bound.parameters());
        if (method == null || method.code().isEmpty()) {
            return;
        }
        final Frame frame = new Frame(method, bound.target());
        fill(frame, arguments);
        this.waiting.add(frame);
        /*
         * A process that had run out of work has work again. One that stopped on a mistake stays
         * stopped: nothing should be able to start it running after that but a person.
         */
        if (this.state == State.FINISHED) {
            this.state = State.RUNNING;
        }
    }

    /** How many calls are still waiting their turn, handlers among them. */
    public int waiting() {
        return this.waiting.size();
    }

    /** A handler bound to a method of an object, for the runtime to hand to an event source. */
    // watching the world

    /** What a watch is waiting for. */
    public enum Watching {
        /** Any change at all in what the network holds of that thing. */
        CHANGE,
        /** The moment it falls to or below a number, and not again until it has gone back above. */
        BELOW,
        /** The moment it rises to or above a number, and not again until it has gone back below. */
        ABOVE
    }

    /** One thing a program asked to be told about. */
    private static final class Watch {
        private final int id;
        private final String item;
        private final Watching kind;
        private final long threshold;
        private final Values.DelegateValue handler;
        private final Values.Obj token;
        private long last;
        private boolean armed;
        private boolean seen;

        Watch(final int id, final String item, final Watching kind, final long threshold,
              final Values.DelegateValue handler, final Values.Obj token) {
            this.id = id;
            this.item = item;
            this.kind = kind;
            this.threshold = threshold;
            this.handler = handler;
            this.token = token;
            this.armed = true;
        }
    }

    private final List<Watch> watches = new ArrayList<>();
    private int nextWatch = 1;

    /**
     * Asks to be told when what the network holds of something changes.
     *
     * <p>The program is handed a token. Disposing it stops the watch, which is the same gesture that
     * frees anything else, so there is nothing new to learn: what a program stops holding, it stops
     * paying for, and here it also stops being woken by.
     */
    public Values.Obj watch(final String item, final Watching kind, final long threshold,
                            final Values.DelegateValue handler, final int line) {
        if (handler == null || handler.chain().isEmpty()) {
            throw new Halt(Halt.Reason.NO_OBJECT, line, "there is no handler to call for " + item);
        }
        final Values.Obj token = new Values.Obj("Subscription");
        token.set("Id", this.nextWatch);
        token.set("Item", item);
        this.heap.allocate(token, Heap.HEADER + 2L * Heap.REFERENCE, line);
        this.watches.add(new Watch(this.nextWatch++, item, kind, threshold, handler, token));
        return token;
    }

    /**
     * Everything this process is watching, so whatever has the world can look each up once and no more,
     * however many watches are waiting on the same thing.
     */
    public List<String> watching() {
        final List<String> items = new ArrayList<>();
        for (final Watch watch : this.watches) {
            if (!items.contains(watch.item)) {
                items.add(watch.item);
            }
        }
        return items;
    }

    /**
     * Hands in what the world now holds, and queues a call for every watch that was waiting for it.
     *
     * <p>A watch whose token the program has thrown away is dropped here rather than fired: stopping is
     * the program's to decide, and it decided.
     */
    public void deliver(final Map<String, Long> totals) {
        this.watches.removeIf(watch -> this.heap.isFreed(watch.token));
        for (final Watch watch : this.watches) {
            final Long now = totals.get(watch.item);
            if (now == null) {
                continue;
            }
            final long before = watch.last;
            final boolean first = !watch.seen;
            watch.last = now;
            watch.seen = true;
            if (this.fires(watch, before, now, first)) {
                this.post(watch.handler, List.of(this.stockEvent(watch.item, before, now, first)));
            }
        }
    }

    /**
     * Whether that watch goes off.
     *
     * <p>A threshold watch fires on the crossing, not on the state: a program told once that the iron
     * is low should not be told again every tick that it is still low. It rearms when the number goes
     * back the other way. The first look is only ever a reading, never a crossing, because a program
     * that starts up with the iron already low has not just seen it fall.
     */
    private boolean fires(final Watch watch, final long before, final long now, final boolean first) {
        return switch (watch.kind) {
            case CHANGE -> !first && before != now;
            case BELOW -> {
                if (now > watch.threshold) {
                    watch.armed = true;
                    yield false;
                }
                final boolean go = watch.armed && !first;
                watch.armed = false;
                yield go;
            }
            case ABOVE -> {
                if (now < watch.threshold) {
                    watch.armed = true;
                    yield false;
                }
                final boolean go = watch.armed && !first;
                watch.armed = false;
                yield go;
            }
        };
    }

    private Values.Obj stockEvent(final String item, final long before, final long now,
                                  final boolean first) {
        final Values.Obj made = new Values.Obj("StockEvent");
        made.set("Item", item);
        made.set("Total", now);
        made.set("Previous", first ? now : before);
        this.heap.allocate(made, Heap.HEADER + 3L * Heap.REFERENCE, 0);
        return made;
    }

    public Values.DelegateValue handlerFor(final Values.Obj self, final String name) {
        final Loaded.Type type = this.program.type(self.type());
        for (final Loaded.Method method : type.methods().values()) {
            if (method.name().equals(name)) {
                return new Values.DelegateValue(self.type(), List.of(new Values.Bound(self,
                        method.owner(), method.name(), method.parameters(), method.returns())));
            }
        }
        return null;
    }

    // Starts the next call that was waiting, if there is one and nothing else is running.
    private boolean take() {
        final Frame next = this.waiting.poll();
        if (next == null) {
            return false;
        }
        this.frames.push(next);
        return true;
    }

    private void halt(final Halt halt) {
        this.state = State.HALTED;
        this.message = halt.getMessage();
        this.library.write(halt.getMessage());
        this.frames.clear();
    }

    // putting it away and back

    /**
     * Writes the whole process down: what it has allocated, what each call was doing, and where each
     * of them had got to.
     *
     * <p>Two names for one object come back as two names for one object, because everything allocated
     * is written under a number and every reference is written as that number.
     */
    public Snapshot save() {
        final Map<Object, Integer> numbers = new IdentityHashMap<>();
        final List<Object> things = this.heap.everything();
        for (int i = 0; i < things.size(); i++) {
            numbers.put(things.get(i), i);
        }
        final List<Snapshot.IHeld> held = new ArrayList<>();
        for (int i = 0; i < things.size(); i++) {
            held.add(this.freeze(things.get(i), i, numbers));
        }
        /*
         * The frames are a stack, so they come out top first; they are written bottom first, which is
         * the order they have to be put back in.
         */
        final List<Frame> stack = new ArrayList<>(this.frames);
        java.util.Collections.reverse(stack);
        final List<Snapshot.FrameShot> frames = new ArrayList<>();
        for (final Frame frame : stack) {
            frames.add(freeze(frame, numbers));
        }
        final List<Snapshot.FrameShot> queued = new ArrayList<>();
        for (final Frame frame : this.waiting) {
            queued.add(freeze(frame, numbers));
        }
        final Map<String, Map<String, Snapshot.IValue>> kept = new LinkedHashMap<>();
        for (final Map.Entry<String, Values.Obj> entry : this.statics.entrySet()) {
            kept.put(entry.getKey(), fields(entry.getValue(), numbers));
        }
        final List<Snapshot.WatchShot> watching = new ArrayList<>();
        for (final Watch watch : this.watches) {
            watching.add(new Snapshot.WatchShot(watch.id, watch.item, watch.kind.name(), watch.threshold,
                    value(watch.handler, numbers), value(watch.token, numbers), watch.last, watch.armed,
                    watch.seen));
        }
        return new Snapshot(this.heap.budget(), held, frames, queued, kept, value(this.script, numbers),
                watching, this.library.console(), this.library.written(), this.state.name(),
                this.message == null ? "" : this.message, this.spent, this.name);
    }

    /** Reads a process back out of what {@link #save()} wrote, ready to carry on where it stopped. */
    public static Process restore(final Loaded program, final Snapshot shot, final IHost host) {
        final Process process = new Process(program, shot.heapBudget(), host, false);
        process.setName(shot.name());
        final Map<Integer, Object> byNumber = new LinkedHashMap<>();
        for (final Snapshot.IHeld written : shot.held()) {
            byNumber.put(written.id(), shell(written));
        }
        /*
         * Handlers are settled before anything is filled in, because one cannot be changed after it is
         * made and whatever points at one has to point at the one that stays.
         */
        for (final Snapshot.IHeld written : shot.held()) {
            if (written instanceof Snapshot.IHeld.Handler handler) {
                final List<Values.Bound> chain = new ArrayList<>();
                for (final Snapshot.BoundShot bound : handler.chain()) {
                    chain.add(new Values.Bound(value(bound.target(), byNumber), bound.owner(),
                            bound.method(), bound.parameters(), bound.returns()));
                }
                byNumber.put(handler.id(), new Values.DelegateValue(handler.type(), chain));
            }
        }
        for (final Snapshot.IHeld written : shot.held()) {
            fill(written, byNumber);
            process.heap.restore(byNumber.get(written.id()), written.bytes(), written.line(),
                    written.freed());
        }
        for (final Snapshot.FrameShot written : shot.frames()) {
            final Frame frame = thaw(program, written, byNumber);
            if (frame != null) {
                process.frames.push(frame);
            }
        }
        for (final Snapshot.FrameShot written : shot.waiting()) {
            final Frame frame = thaw(program, written, byNumber);
            if (frame != null) {
                process.waiting.add(frame);
            }
        }
        for (final Map.Entry<String, Map<String, Snapshot.IValue>> entry : shot.statics().entrySet()) {
            final Values.Obj holder = process.statics(entry.getKey());
            for (final Map.Entry<String, Snapshot.IValue> field : entry.getValue().entrySet()) {
                holder.set(field.getKey(), value(field.getValue(), byNumber));
            }
        }
        if (value(shot.script(), byNumber) instanceof Values.Obj script) {
            process.script = script;
        }
        for (final Snapshot.WatchShot written : shot.watches()) {
            if (value(written.handler(), byNumber) instanceof Values.DelegateValue handler
                    && value(written.token(), byNumber) instanceof Values.Obj token) {
                final Watch watch = new Watch(written.id(), written.item(),
                        Watching.valueOf(written.kind()), written.threshold(), handler, token);
                watch.last = written.last();
                watch.armed = written.armed();
                watch.seen = written.seen();
                process.watches.add(watch);
                process.nextWatch = Math.max(process.nextWatch, written.id() + 1);
            }
        }
        process.library.restore(shot.console(), shot.written());
        process.state = State.valueOf(shot.state());
        process.message = shot.message().isEmpty() ? null : shot.message();
        process.spent = shot.spent();
        return process;
    }

    private Snapshot.IHeld freeze(final Object thing, final int number, final Map<Object, Integer> numbers) {
        final long bytes = this.heap.bytesOf(thing);
        final int line = this.heap.lineOf(thing);
        final boolean freed = this.heap.isFreed(thing);
        if (thing instanceof String text) {
            return new Snapshot.IHeld.Text(number, bytes, line, freed, text);
        }
        if (thing instanceof Values.Obj object) {
            return new Snapshot.IHeld.Object(number, bytes, line, freed, object.type(),
                    fields(object, numbers));
        }
        if (thing instanceof Values.Arr array) {
            return new Snapshot.IHeld.Array(number, bytes, line, freed, array.element(),
                    values(array.all(), numbers));
        }
        if (thing instanceof Values.ListValue list) {
            return new Snapshot.IHeld.Listing(number, bytes, line, freed, values(list.items(), numbers));
        }
        if (thing instanceof Values.MapValue map) {
            return new Snapshot.IHeld.Keyed(number, bytes, line, freed,
                    values(new ArrayList<>(map.entries().keySet()), numbers),
                    values(new ArrayList<>(map.entries().values()), numbers));
        }
        final Values.DelegateValue delegate = (Values.DelegateValue) thing;
        final List<Snapshot.BoundShot> chain = new ArrayList<>();
        for (final Values.Bound bound : delegate.chain()) {
            chain.add(new Snapshot.BoundShot(value(bound.target(), numbers), bound.owner(),
                    bound.method(), bound.parameters(), bound.returns()));
        }
        return new Snapshot.IHeld.Handler(number, bytes, line, freed, delegate.type(), chain);
    }

    private static Object shell(final Snapshot.IHeld written) {
        return switch (written) {
            case Snapshot.IHeld.Text text -> new String(text.value().toCharArray());
            case Snapshot.IHeld.Object object -> new Values.Obj(object.type());
            case Snapshot.IHeld.Array array -> new Values.Arr(array.element(), array.values().size());
            case Snapshot.IHeld.Listing ignored -> new Values.ListValue();
            case Snapshot.IHeld.Keyed ignored -> new Values.MapValue();
            case Snapshot.IHeld.Handler handler -> new Values.DelegateValue(handler.type(), List.of());
        };
    }

    /*
     * A later pass, because two things can point at each other and neither can be filled in until both
     * exist.
     */
    private static void fill(final Snapshot.IHeld written, final Map<Integer, Object> byNumber) {
        final Object thing = byNumber.get(written.id());
        switch (written) {
            case Snapshot.IHeld.Object object -> {
                for (final Map.Entry<String, Snapshot.IValue> field : object.fields().entrySet()) {
                    ((Values.Obj) thing).set(field.getKey(), value(field.getValue(), byNumber));
                }
            }
            case Snapshot.IHeld.Array array -> {
                for (int i = 0; i < array.values().size(); i++) {
                    ((Values.Arr) thing).set(i, value(array.values().get(i), byNumber), 0);
                }
            }
            case Snapshot.IHeld.Listing list -> {
                for (final Snapshot.IValue item : list.items()) {
                    ((Values.ListValue) thing).items().add(value(item, byNumber));
                }
            }
            case Snapshot.IHeld.Keyed keyed -> {
                for (int i = 0; i < keyed.keys().size(); i++) {
                    ((Values.MapValue) thing).entries().put(value(keyed.keys().get(i), byNumber),
                            value(keyed.values().get(i), byNumber));
                }
            }
            default -> { }
        }
    }

    private static Snapshot.FrameShot freeze(final Frame frame, final Map<Object, Integer> numbers) {
        return new Snapshot.FrameShot(frame.method.owner(), frame.method.name(),
                frame.method.parameters(), frame.at, value(frame.self, numbers),
                values(java.util.Arrays.asList(frame.slots), numbers), values(frame.stack, numbers),
                frame.discard);
    }

    private static Frame thaw(final Loaded program, final Snapshot.FrameShot written,
                              final Map<Integer, Object> byNumber) {
        final Loaded.Method method = found(program, written);
        if (method == null) {
            return null;
        }
        final Frame frame = new Frame(method, value(written.self(), byNumber));
        for (int i = 0; i < written.slots().size() && i < frame.slots.length; i++) {
            frame.slots[i] = value(written.slots().get(i), byNumber);
        }
        for (final Snapshot.IValue held : written.stack()) {
            frame.push(value(held, byNumber));
        }
        frame.at = written.at();
        frame.discard = written.discard();
        return frame;
    }

    /**
     * The method a frame was in.
     *
     * <p>The one that puts a type's own starting values in place is not among the methods that can be
     * called by name, so it is asked for separately: a process put away before it ran would otherwise
     * come back without it.
     */
    private static Loaded.Method found(final Loaded program, final Snapshot.FrameShot written) {
        final Loaded.Method named =
                program.method(written.owner(), written.name(), written.parameters());
        if (named != null) {
            return named;
        }
        final Loaded.Type type = program.type(written.owner());
        if (type == null || type.setUp() == null) {
            return null;
        }
        final Loaded.Method setUp = type.setUp();
        return setUp.name().equals(written.name()) && setUp.parameters().equals(written.parameters())
                ? setUp : null;
    }

    private static Map<String, Snapshot.IValue> fields(final Values.Obj object,
                                                      final Map<Object, Integer> numbers) {
        final Map<String, Snapshot.IValue> written = new LinkedHashMap<>();
        for (final Map.Entry<String, Object> field : object.all().entrySet()) {
            written.put(field.getKey(), value(field.getValue(), numbers));
        }
        return written;
    }

    private static List<Snapshot.IValue> values(final List<Object> things,
                                               final Map<Object, Integer> numbers) {
        final List<Snapshot.IValue> written = new ArrayList<>();
        for (final Object thing : things) {
            written.add(value(thing, numbers));
        }
        return written;
    }

    private static Snapshot.IValue value(final Object thing,
                                        final Map<Object, Integer> numbers) {
        return switch (thing) {
            case null -> new Snapshot.IValue.Nothing();
            case Integer number -> new Snapshot.IValue.I4(number);
            case Long number -> new Snapshot.IValue.I8(number);
            case Float number -> new Snapshot.IValue.R4(number);
            case Double number -> new Snapshot.IValue.R8(number);
            case Boolean flag -> new Snapshot.IValue.Bool(flag);
            case Character letter -> new Snapshot.IValue.Ch(letter);
            default -> {
                final Integer number = numbers.get(thing);
                yield number == null ? new Snapshot.IValue.Nothing() : new Snapshot.IValue.Ref(number);
            }
        };
    }

    private static Object value(final Snapshot.IValue written,
                                          final Map<Integer, Object> byNumber) {
        return switch (written) {
            case Snapshot.IValue.Nothing ignored -> null;
            case Snapshot.IValue.I4 number -> number.value();
            case Snapshot.IValue.I8 number -> number.value();
            case Snapshot.IValue.R4 number -> number.value();
            case Snapshot.IValue.R8 number -> number.value();
            case Snapshot.IValue.Bool flag -> flag.value();
            case Snapshot.IValue.Ch letter -> letter.value();
            case Snapshot.IValue.Ref reference -> byNumber.get(reference.id());
        };
    }

    // one instruction

    private void one() {
        final Frame frame = this.frames.peek();
        if (frame.at >= frame.method.code().size()) {
            this.leave(frame, null);
            return;
        }
        final Instruction instruction = frame.method.code().get(frame.at);
        frame.at++;
        this.run(frame, instruction, frame.at);
    }

    private void run(final Frame frame, final Instruction instruction, final int line) {
        switch (instruction.opcode()) {
            case LDC_I4 -> frame.push(((IOperand.I4) instruction.operand()).value());
            case LDC_I8 -> frame.push(((IOperand.I8) instruction.operand()).value());
            case LDC_R4 -> frame.push(((IOperand.R4) instruction.operand()).value());
            case LDC_R8 -> frame.push(((IOperand.R8) instruction.operand()).value());
            case LDNULL -> frame.push(null);
            case LDSTR -> frame.push(this.text(((IOperand.Text) instruction.operand()).value(), line));
            case LDTHIS -> frame.push(frame.self);
            case LDLOC -> frame.push(frame.slots[((IOperand.Slot) instruction.operand()).index()]);
            case STLOC -> frame.slots[((IOperand.Slot) instruction.operand()).index()] = frame.pop();
            case POP -> frame.pop();
            case COPY -> frame.push(this.copyOf(frame.pop(), line));
            case DUP -> frame.push(frame.peek());
            case LDFLD -> this.loadField(frame, (IOperand.Field) instruction.operand(), line);
            case STFLD -> this.storeField(frame, (IOperand.Field) instruction.operand(), line);
            case LDSFLD -> this.loadStatic(frame, (IOperand.Field) instruction.operand(), line);
            case STSFLD -> this.storeStatic(frame, (IOperand.Field) instruction.operand());
            case ADD, SUB, MUL, DIV, REM, AND, OR, XOR, SHL, SHR ->
                    this.arithmetic(frame, instruction.opcode(), line);
            case NEG -> frame.push(Numbers.negate(frame.pop()));
            case NOT -> frame.push(Numbers.complement(frame.pop()));
            case CONV_I4 -> frame.push(Numbers.toInt(frame.pop()));
            case CONV_I8 -> frame.push(Numbers.toLong(frame.pop()));
            case CONV_R4 -> frame.push(Numbers.toFloat(frame.pop()));
            case CONV_R8 -> frame.push(Numbers.toDouble(frame.pop()));
            case CEQ, CLT, CGT -> this.compare(frame, instruction.opcode());
            case BR -> frame.at = Loaded.target(frame.method, instruction);
            case BRTRUE -> this.jumpIf(frame, instruction, truth(frame.pop()));
            case BRFALSE -> this.jumpIf(frame, instruction, !truth(frame.pop()));
            case BEQ, BNE, BLT, BLE, BGT, BGE -> this.jumpCompare(frame, instruction);
            case NEWOBJ -> this.newObject(frame, (IOperand.Constructor) instruction.operand(), line);
            case NEWARR -> this.newArray(frame, (IOperand.Type) instruction.operand(), line);
            case LDELEM -> this.loadElement(frame, line);
            case STELEM -> this.storeElement(frame, line);
            case LDLEN -> frame.push(this.array(frame.pop(), line).length());
            case DISPOSE -> this.heap.dispose(frame.pop(), line);
            case CASTCLASS -> this.cast(frame, ((IOperand.Type) instruction.operand()).name(), line);
            case ISINST -> this.isInstance(frame, ((IOperand.Type) instruction.operand()).name());
            case LDFN -> this.handler(frame, (IOperand.Method) instruction.operand(), line);
            case CALL, CALLVIRT -> this.call(frame, (IOperand.Method) instruction.operand(),
                    instruction.opcode() == Opcode.CALLVIRT, line);
            case SYS -> throw new Halt(Halt.Reason.NO_NETWORK, line,
                    "this computer is not on a network");
            case RET -> this.leave(frame, frame.method.gives() ? frame.pop() : null);
            default -> { }
        }
    }

    private void jumpIf(final Frame frame, final Instruction instruction, final boolean go) {
        if (go) {
            frame.at = Loaded.target(frame.method, instruction);
        }
    }

    private void jumpCompare(final Frame frame, final Instruction instruction) {
        final Object right = frame.pop();
        final Object left = frame.pop();
        final boolean go = switch (instruction.opcode()) {
            case BEQ -> same(left, right);
            case BNE -> !same(left, right);
            case BLT -> Numbers.compare(left, right) < 0;
            case BLE -> Numbers.compare(left, right) <= 0;
            case BGT -> Numbers.compare(left, right) > 0;
            default -> Numbers.compare(left, right) >= 0;
        };
        this.jumpIf(frame, instruction, go);
    }

    private void compare(final Frame frame, final Opcode opcode) {
        final Object right = frame.pop();
        final Object left = frame.pop();
        frame.push(switch (opcode) {
            case CEQ -> this.same(left, right);
            case CLT -> Numbers.compare(left, right) < 0;
            default -> Numbers.compare(left, right) > 0;
        });
    }

    private void arithmetic(final Frame frame, final Opcode opcode, final int line) {
        final Object right = frame.pop();
        final Object left = frame.pop();
        frame.push(Numbers.apply(opcode, left, right, line));
    }

    // fields

    private void loadField(final Frame frame, final IOperand.Field field, final int line) {
        final Object target = this.alive(frame.pop(), line);
        if (target instanceof Values.Obj object) {
            frame.push(object.get(field.name()));
            return;
        }
        frame.push(this.library.read(target, field.name(), line));
    }

    private void storeField(final Frame frame, final IOperand.Field field, final int line) {
        final Object value = frame.pop();
        final Object target = this.alive(frame.pop(), line);
        if (!(target instanceof Values.Obj object)) {
            throw new Halt(Halt.Reason.NO_OBJECT, line, "there is no object to write " + field.name() + " on");
        }
        object.set(field.name(), value);
    }

    private void loadStatic(final Frame frame, final IOperand.Field field, final int line) {
        final Loaded.Type type = this.program.type(field.owner());
        if (type == null) {
            frame.push(this.library.readStatic(field.owner(), field.name(), line));
            return;
        }
        if (type.kind() == AsmType.Kind.ENUM) {
            frame.push(type.values().get(field.name()));
            return;
        }
        frame.push(this.statics(field.owner()).get(field.name()));
    }

    private void storeStatic(final Frame frame, final IOperand.Field field) {
        this.statics(field.owner()).set(field.name(), frame.pop());
    }

    private Values.Obj statics(final String owner) {
        return this.statics.computeIfAbsent(owner, Values.Obj::new);
    }

    // objects

    private void newObject(final Frame frame, final IOperand.Constructor made, final int line) {
        frame.push(this.instance(made.owner(), this.take(frame, made.parameters()), line));
    }

    /**
     * A copy of a struct: a new object holding what the old one holds, counted like any other. A value
     * that is not a struct, null included, is handed back as it is, since there is nothing to copy.
     */
    private Object copyOf(final Object value, final int line) {
        if (!(value instanceof Values.Obj original)) {
            return value;
        }
        final Loaded.Type known = this.program.type(original.type());
        if (known == null || known.kind() != AsmType.Kind.STRUCT) {
            return value;
        }
        final Values.Obj made = new Values.Obj(original.type());
        this.heap.allocate(made, this.sizeOf(known), line);
        for (final Map.Entry<String, Object> field : original.all().entrySet()) {
            made.set(field.getKey(), this.copyOf(field.getValue(), line));
        }
        return made;
    }

    private Object instance(final String type, final List<Object> arguments, final int line) {
        final Loaded.Type known = this.program.type(type);
        if (known == null) {
            return this.library.create(type, line);
        }
        final Values.Obj made = new Values.Obj(type);
        this.heap.allocate(made, this.sizeOf(known), line);
        final Loaded.Method constructor = this.constructorOf(known, arguments.size());
        if (constructor != null) {
            this.enter(constructor, made, arguments, line);
        }
        return made;
    }

    private Loaded.Method constructorOf(final Loaded.Type type, final int count) {
        for (final Loaded.Method method : type.methods().values()) {
            if (method.name().equals(type.name()) && !method.isStatic()
                    && method.parameters().size() == count) {
                return method;
            }
        }
        return null;
    }

    private long sizeOf(final Loaded.Type type) {
        long bytes = Heap.HEADER;
        for (String at = type.name(); at != null; at = this.program.baseOf(at)) {
            final Loaded.Type known = this.program.type(at);
            for (final AsmType.Field field : known.fields()) {
                if (!field.isStatic()) {
                    bytes += Heap.sizeOf(field.type());
                }
            }
        }
        return bytes;
    }

    private void newArray(final Frame frame, final IOperand.Type element, final int line) {
        final int length = Numbers.toInt(frame.pop());
        if (length < 0) {
            throw new Halt(Halt.Reason.OUT_OF_RANGE, line, "an array cannot have " + length + " places");
        }
        final Values.Arr made = new Values.Arr(element.name(), length);
        this.heap.allocate(made, Heap.HEADER + (long) Heap.sizeOf(element.name()) * length, line);
        frame.push(made);
    }

    private void loadElement(final Frame frame, final int line) {
        final int index = Numbers.toInt(frame.pop());
        frame.push(this.array(frame.pop(), line).get(index, line));
    }

    private void storeElement(final Frame frame, final int line) {
        final Object value = frame.pop();
        final int index = Numbers.toInt(frame.pop());
        this.array(frame.pop(), line).set(index, value, line);
    }

    private Values.Arr array(final Object value, final int line) {
        if (this.alive(value, line) instanceof Values.Arr array) {
            return array;
        }
        throw new Halt(Halt.Reason.NO_OBJECT, line, "there is no array here");
    }

    private void cast(final Frame frame, final String type, final int line) {
        final Object value = frame.pop();
        if (value == null || this.isOfType(value, type)) {
            frame.push(value);
            return;
        }
        throw new Halt(Halt.Reason.BAD_CAST, line, "this is not a " + type);
    }

    private void isInstance(final Frame frame, final String type) {
        final Object value = frame.pop();
        frame.push(value != null && this.isOfType(value, type));
    }

    private boolean isOfType(final Object value, final String type) {
        if (value instanceof Values.Obj object) {
            return this.program.isA(object.type(), type);
        }
        if (value instanceof Values.DelegateValue delegate) {
            return delegate.type().equals(type);
        }
        return switch (type) {
            case "string" -> value instanceof String;
            case "object" -> true;
            default -> value instanceof Values.Arr array && type.equals(array.element() + "[]");
        };
    }

    // calls

    private void handler(final Frame frame, final IOperand.Method method, final int line) {
        final Object target = frame.pop();
        final Values.Bound bound = new Values.Bound(target, method.owner(), method.name(),
                method.parameters(), method.returns());
        final Values.DelegateValue made = new Values.DelegateValue(method.owner(), List.of(bound));
        this.heap.allocate(made, made.bytes(), line);
        frame.push(made);
    }

    private void call(final Frame frame, final IOperand.Method named, final boolean through, final int line) {
        final List<Object> arguments = this.take(frame, named.parameters());
        if (through) {
            this.invoke(frame, arguments, line);
            return;
        }
        final Loaded.Method direct = this.program.method(named.owner(), named.name(), named.parameters());
        if (direct == null) {
            if (Library.readsLine(named) && this.input.isEmpty()) {
                /*
                 * Nothing has been typed: the call is put back so it is asked again once a line comes,
                 * and the process waits without spending anything. The read takes nothing off the
                 * stack, which is what makes asking it again the same as asking it once.
                 */
                frame.at--;
                this.park();
                return;
            }
            final Object self = this.library.takesTarget(named.owner(), named.name())
                    ? this.alive(frame.pop(), line) : null;
            this.push(frame, named, this.library.call(named, self, arguments, line));
            return;
        }
        final Object self = direct.isStatic() ? null : this.alive(frame.pop(), line);
        this.enter(this.onItsOwnType(direct, self, named), self, arguments, line);
    }

    /*
     * A call through an interface names the interface, but the object knows which class it is, and
     * that is the one whose lines should run.
     */
    private Loaded.Method onItsOwnType(final Loaded.Method direct, final Object self,
                                       final IOperand.Method named) {
        if (!(self instanceof Values.Obj object) || object.type().equals(named.owner())) {
            return direct;
        }
        final Loaded.Method own = this.program.method(object.type(), named.name(), named.parameters());
        return own != null && !own.code().isEmpty() ? own : direct;
    }

    /**
     * Calls what a delegate holds.
     *
     * <p>A delegate can hold a run of handlers, and all of them are called, in the order they were
     * joined. They are stacked up rather than run one after another on the spot, so a run of a
     * hundred handlers costs the budget the same as a hundred calls written out and cannot take the
     * tick away from anything else. Only the last of them leaves an answer, which is what the caller
     * is waiting for.
     */
    private void invoke(final Frame frame, final List<Object> arguments, final int line) {
        final Object value = this.alive(frame.pop(), line);
        if (!(value instanceof Values.DelegateValue delegate) || delegate.chain().isEmpty()) {
            throw new Halt(Halt.Reason.NO_OBJECT, line, "there is no handler to call");
        }
        final List<Values.Bound> chain = delegate.chain();
        for (int i = chain.size() - 1; i >= 0; i--) {
            final Values.Bound bound = chain.get(i);
            final Loaded.Method method =
                    this.program.method(bound.owner(), bound.method(), bound.parameters());
            if (method == null || method.code().isEmpty()) {
                if (i == chain.size() - 1) {
                    throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line,
                            "there is no " + bound.method() + " to call");
                }
                continue;
            }
            final Frame made = new Frame(method, bound.target());
            fill(made, arguments);
            made.discard = i < chain.size() - 1;
            this.frames.push(made);
        }
    }

    private void enter(final Loaded.Method method, final Object self, final List<Object> arguments,
                       final int line) {
        if (method.code().isEmpty()) {
            throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, method.describe() + " has no body to run");
        }
        final Frame frame = new Frame(method, self);
        fill(frame, arguments);
        this.frames.push(frame);
    }

    private static void fill(final Frame frame, final List<Object> arguments) {
        for (int i = 0; i < arguments.size() && i < frame.slots.length; i++) {
            frame.slots[i] = arguments.get(i);
        }
    }

    /**
     * Leaves a method, putting back what it gives and then what it filled in, the last of those on
     * top, which is the order the caller stores them in.
     */
    private void leave(final Frame frame, final Object answer) {
        this.frames.pop();
        if (this.frames.isEmpty()) {
            return;
        }
        if (frame.discard) {
            return;
        }
        final Frame caller = this.frames.peek();
        if (frame.method.gives()) {
            caller.push(answer);
        }
        for (int i = 0; i < frame.method.parameters().size(); i++) {
            if (frame.method.fillsIn(i)) {
                caller.push(frame.slots[i]);
            }
        }
    }

    private void push(final Frame frame, final IOperand.Method named, final Library.Answer answer) {
        if (!"void".equals(named.returns())) {
            frame.push(answer.value());
        }
        for (final Object filled : answer.filled()) {
            frame.push(filled);
        }
    }

    /**
     * Takes the arguments off the stack. They were pushed in order, so they come off backwards, and
     * they may be null, which is why the list is one that allows it.
     *
     * <p>A place the method fills in was never pushed: the caller hands over somewhere to write, not
     * a value, so that place is left empty here and holds what the method put there when it returns.
     */
    private List<Object> take(final Frame frame, final List<String> parameters) {
        final List<Object> taken = new ArrayList<>(java.util.Collections.nCopies(parameters.size(), null));
        for (int i = parameters.size() - 1; i >= 0; i--) {
            if (parameters.get(i).startsWith("out ")) {
                continue;
            }
            taken.set(i, frame.pop());
        }
        return taken;
    }

    // odds and ends

    private String text(final String value, final int line) {
        /*
         * A fresh piece of text each time, so two that read the same are still two things the program
         * can free one of without the other going with it.
         */
        return this.heap.allocate(new String(value.toCharArray()), Heap.sizeOfText(value), line);
    }

    private Object alive(final Object value, final int line) {
        if (value != null && this.heap.isFreed(value)) {
            throw new Halt(Halt.Reason.USE_AFTER_DISPOSE, line, "this was disposed and cannot be used");
        }
        if (value == null) {
            throw new Halt(Halt.Reason.NO_OBJECT, line, "there is nothing here to reach into");
        }
        return value;
    }

    /**
     * Whether a value counts as true where a branch asks.
     *
     * <p>The assembly has no constant for a bool: true and false are written as a one and a zero, so
     * a number stands for a bool wherever one was meant, and zero is the false one.
     */
    private static boolean truth(final Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.longValue() != 0;
        }
        return value != null;
    }

    /*
     * Two values are the same when they say the same thing, which for a bool and the number that
     * stands for it means comparing what they both mean rather than what they are. Two structs or two
     * records are the same when everything they hold is, field by field, however far down that goes.
     */
    private boolean same(final Object left, final Object right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left instanceof Boolean || right instanceof Boolean) {
            return truth(left) == truth(right);
        }
        if (left instanceof String || right instanceof String) {
            return left.equals(right);
        }
        if (left instanceof Values.Obj one && right instanceof Values.Obj other && one.type().equals(other.type())) {
            final Loaded.Type kind = this.program.type(one.type());
            if (kind != null && kind.kind().byValue()) {
                final Map<String, Object> mine = one.all();
                final Map<String, Object> theirs = other.all();
                if (!mine.keySet().equals(theirs.keySet())) {
                    return false;
                }
                for (final Map.Entry<String, Object> field : mine.entrySet()) {
                    if (!this.same(field.getValue(), theirs.get(field.getKey()))) {
                        return false;
                    }
                }
                return true;
            }
        }
        if (left instanceof Number && right instanceof Number) {
            return Numbers.compare(left, right) == 0;
        }
        return left.equals(right);
    }
}
