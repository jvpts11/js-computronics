/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.run;

import dev.jstech.computronics.cannon.asm.AsmType;
import dev.jstech.computronics.cannon.asm.Instruction;
import dev.jstech.computronics.cannon.asm.Opcode;
import dev.jstech.computronics.cannon.asm.Operand;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
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
    private State state = State.RUNNING;
    private String message;
    private int spent;

    public Process(final Loaded program, final long heapBytes, final Host host) {
        this.program = program;
        this.heap = new Heap(heapBytes);
        this.library = new Library(this.heap, host);
        // Putting the starting values in a type's own fields is the program's work like any other, so
        // it waits its turn and is paid for out of the budget rather than run on the spot.
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

    /** What it said when it stopped, or null while it is still going. */
    public String message() {
        return this.message;
    }

    /** What it has written to its own console. */
    public List<String> console() {
        return this.library.console();
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
        return made instanceof Values.Obj object ? object : null;
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
    }

    /** How many calls are still waiting their turn, handlers among them. */
    public int waiting() {
        return this.waiting.size();
    }

    /** A handler bound to a method of an object, for the runtime to hand to an event source. */
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

    // ---------------------------------------------------------------- one instruction

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
            case LDC_I4 -> frame.push(((Operand.I4) instruction.operand()).value());
            case LDC_I8 -> frame.push(((Operand.I8) instruction.operand()).value());
            case LDC_R4 -> frame.push(((Operand.R4) instruction.operand()).value());
            case LDC_R8 -> frame.push(((Operand.R8) instruction.operand()).value());
            case LDNULL -> frame.push(null);
            case LDSTR -> frame.push(this.text(((Operand.Text) instruction.operand()).value(), line));
            case LDTHIS -> frame.push(frame.self);
            case LDLOC -> frame.push(frame.slots[((Operand.Slot) instruction.operand()).index()]);
            case STLOC -> frame.slots[((Operand.Slot) instruction.operand()).index()] = frame.pop();
            case POP -> frame.pop();
            case DUP -> frame.push(frame.peek());
            case LDFLD -> this.loadField(frame, (Operand.Field) instruction.operand(), line);
            case STFLD -> this.storeField(frame, (Operand.Field) instruction.operand(), line);
            case LDSFLD -> this.loadStatic(frame, (Operand.Field) instruction.operand(), line);
            case STSFLD -> this.storeStatic(frame, (Operand.Field) instruction.operand());
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
            case NEWOBJ -> this.newObject(frame, (Operand.Constructor) instruction.operand(), line);
            case NEWARR -> this.newArray(frame, (Operand.Type) instruction.operand(), line);
            case LDELEM -> this.loadElement(frame, line);
            case STELEM -> this.storeElement(frame, line);
            case LDLEN -> frame.push(this.array(frame.pop(), line).length());
            case DISPOSE -> this.heap.dispose(frame.pop(), line);
            case CASTCLASS -> this.cast(frame, ((Operand.Type) instruction.operand()).name(), line);
            case ISINST -> this.isInstance(frame, ((Operand.Type) instruction.operand()).name());
            case LDFN -> this.handler(frame, (Operand.Method) instruction.operand(), line);
            case CALL, CALLVIRT -> this.call(frame, (Operand.Method) instruction.operand(),
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
            case CEQ -> same(left, right);
            case CLT -> Numbers.compare(left, right) < 0;
            default -> Numbers.compare(left, right) > 0;
        });
    }

    private void arithmetic(final Frame frame, final Opcode opcode, final int line) {
        final Object right = frame.pop();
        final Object left = frame.pop();
        frame.push(Numbers.apply(opcode, left, right, line));
    }

    // ---------------------------------------------------------------- fields

    private void loadField(final Frame frame, final Operand.Field field, final int line) {
        final Object target = this.alive(frame.pop(), line);
        if (target instanceof Values.Obj object) {
            frame.push(object.get(field.name()));
            return;
        }
        frame.push(this.library.read(target, field.name(), line));
    }

    private void storeField(final Frame frame, final Operand.Field field, final int line) {
        final Object value = frame.pop();
        final Object target = this.alive(frame.pop(), line);
        if (!(target instanceof Values.Obj object)) {
            throw new Halt(Halt.Reason.NO_OBJECT, line, "there is no object to write " + field.name() + " on");
        }
        object.set(field.name(), value);
    }

    private void loadStatic(final Frame frame, final Operand.Field field, final int line) {
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

    private void storeStatic(final Frame frame, final Operand.Field field) {
        this.statics(field.owner()).set(field.name(), frame.pop());
    }

    private Values.Obj statics(final String owner) {
        return this.statics.computeIfAbsent(owner, Values.Obj::new);
    }

    // ---------------------------------------------------------------- objects

    private void newObject(final Frame frame, final Operand.Constructor made, final int line) {
        frame.push(this.instance(made.owner(), this.take(frame, made.parameters()), line));
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

    private void newArray(final Frame frame, final Operand.Type element, final int line) {
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

    // ---------------------------------------------------------------- calls

    private void handler(final Frame frame, final Operand.Method method, final int line) {
        final Object target = frame.pop();
        final Values.Bound bound = new Values.Bound(target, method.owner(), method.name(),
                method.parameters(), method.returns());
        final Values.DelegateValue made = new Values.DelegateValue(method.owner(), List.of(bound));
        this.heap.allocate(made, made.bytes(), line);
        frame.push(made);
    }

    private void call(final Frame frame, final Operand.Method named, final boolean through, final int line) {
        final List<Object> arguments = this.take(frame, named.parameters());
        if (through) {
            this.invoke(frame, arguments, line);
            return;
        }
        final Loaded.Method direct = this.program.method(named.owner(), named.name(), named.parameters());
        if (direct == null) {
            final Object self = this.library.takesTarget(named.owner(), named.name())
                    ? this.alive(frame.pop(), line) : null;
            this.push(frame, named, this.library.call(named, self, arguments, line));
            return;
        }
        final Object self = direct.isStatic() ? null : this.alive(frame.pop(), line);
        this.enter(this.onItsOwnType(direct, self, named), self, arguments, line);
    }

    // A call through an interface names the interface, but the object knows which class it is, and
    // that is the one whose lines should run.
    private Loaded.Method onItsOwnType(final Loaded.Method direct, final Object self,
                                       final Operand.Method named) {
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

    private void push(final Frame frame, final Operand.Method named, final Library.Answer answer) {
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

    // ---------------------------------------------------------------- odds and ends

    private String text(final String value, final int line) {
        // A fresh piece of text each time, so two that read the same are still two things the program
        // can free one of without the other going with it.
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

    // Two values are the same when they say the same thing, which for a bool and the number that
    // stands for it means comparing what they both mean rather than what they are.
    private static boolean same(final Object left, final Object right) {
        if (left == null || right == null) {
            return left == right;
        }
        if (left instanceof Boolean || right instanceof Boolean) {
            return truth(left) == truth(right);
        }
        if (left instanceof String || right instanceof String) {
            return left.equals(right);
        }
        if (left instanceof Number && right instanceof Number) {
            return Numbers.compare(left, right) == 0;
        }
        return left.equals(right);
    }
}
