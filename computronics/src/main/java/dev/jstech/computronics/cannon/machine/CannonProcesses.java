/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon.machine;

import dev.jstech.computronics.cannon.Diagnostic;
import dev.jstech.computronics.cannon.DiagnosticBag;
import dev.jstech.computronics.cannon.Shape;
import dev.jstech.computronics.cannon.asm.AsmProgram;
import dev.jstech.computronics.cannon.asm.AsmReader;
import dev.jstech.computronics.cannon.run.Host;
import dev.jstech.computronics.cannon.run.Loaded;
import dev.jstech.computronics.cannon.run.Process;
import dev.jstech.computronics.cannon.run.Values;
import dev.jstech.computronics.cannon.save.SnapshotTag;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * The Cannon programs one computer is running.
 *
 * <p>A script is called once when it starts, then once per tick for as long as it is up, and once more
 * when it is stopped. It is never waited on: each tick the machine hands out the instructions its
 * processor is worth and every program spends its share and stops where it stands, so a program that
 * loops forever costs the same tick as one that does nothing.
 *
 * <p>The listing a program was started from is kept beside it. A program that came back after a reload
 * has to be the program that was running, not whatever is in that file now, and the file may not even be
 * there any more.
 */
public final class CannonProcesses {

    /** What a program gets when it did not ask for a size of its own. */
    public static final int DEFAULT_HEAP_MB = 1;

    /** The most a program may ask for, because a script is not what a machine's memory is for. */
    public static final int MAX_HEAP_MB = 64;

    /** What a program is allowed to spend on its farewell before the machine stops waiting. */
    private static final int FAREWELL = 4096;

    private static final String MAIN = "Main";
    private static final String WHEN_STARTED = "OnInit";
    private static final String EVERY_TICK = "OnTick";
    private static final String WHEN_STOPPED = "OnDestroy";

    /** One program the machine is running: what it is called, what it was started from, and where it is. */
    public record Live(int id, String name, String assembly, int heapMb, Process process) {
    }

    /** What came of asking for a program to start: its number, or why it did not. */
    public record Started(int id, String message) {

        public boolean ok() {
            return this.id > 0;
        }

        static Started failed(final String why) {
            return new Started(0, why);
        }
    }

    private final List<Live> live = new ArrayList<>();
    private int next = 1;
    private int held;
    private int shown;

    /**
     * The program the terminal is holding, or 0.
     *
     * <p>A machine has one prompt, so it has at most one program in front of it. That program keeps its
     * place in the list after it returns, because what it printed last is not read until the terminal
     * has had its turn; every other finished program is cleared away as soon as it is done.
     */
    public int held() {
        return this.held;
    }

    /** Says the terminal is now waiting on that program. */
    public void hold(final int id) {
        this.held = id;
        this.shown = 0;
    }

    /**
     * What the held program has printed since this was last asked, and never the same line twice.
     *
     * <p>A program that printed more than its console keeps while nobody was looking has scrolled: what
     * fell off the end is gone, the way it is gone from any terminal nobody was watching.
     */
    public List<String> unseen() {
        final Live one = this.byId(this.held);
        if (one == null) {
            return List.of();
        }
        final List<String> kept = one.process().console();
        final int written = one.process().written();
        final int fresh = Math.min(written - this.shown, kept.size());
        this.shown = written;
        return fresh <= 0 ? List.of() : List.copyOf(kept.subList(kept.size() - fresh, kept.size()));
    }

    /** Lets the terminal go, clearing the program away if it had already finished. */
    public void release() {
        final Live one = this.byId(this.held);
        this.held = 0;
        if (one != null && one.process().state() != Process.State.RUNNING
                && one.process().state() != Process.State.PARKED) {
            this.live.remove(one);
        }
    }

    /** Everything running, in the order it was started. */
    public List<Live> all() {
        return List.copyOf(this.live);
    }

    /** The program of that number, or null. */
    @Nullable
    public Live byId(final int id) {
        for (final Live one : this.live) {
            if (one.id() == id) {
                return one;
            }
        }
        return null;
    }

    /** Whether anything is running at all, which is what lets a machine skip the work entirely. */
    public boolean isEmpty() {
        return this.live.isEmpty();
    }

    /** The megabytes every running program is holding between them. */
    public int heapMb() {
        int sum = 0;
        for (final Live one : this.live) {
            sum += one.heapMb();
        }
        return sum;
    }

    /**
     * Starts a program from the text of a listing.
     *
     * <p>The listing is read here rather than where the file was, so a program that cannot be read never
     * becomes a process at all and the reason reaches the prompt as one line.
     */
    public Started start(final String name, final String assembly, final int heapMb, final Host host) {
        final DiagnosticBag bag = new DiagnosticBag(name);
        final AsmProgram read = new AsmReader(assembly, bag).read();
        if (bag.hasErrors()) {
            final List<Diagnostic> found = bag.sorted();
            return Started.failed(found.isEmpty() ? name + ": cannot be read" : found.getFirst().format());
        }
        final Loaded program = Loaded.of(read);
        if (program.entryPoint() == null) {
            return Started.failed(name + ": nothing in here is a script");
        }
        final int room = Math.clamp(heapMb <= 0 ? DEFAULT_HEAP_MB : heapMb, 1, MAX_HEAP_MB);
        final Process process = new Process(program, (long) room * 1024 * 1024, host);
        if (program.shape() == Shape.CONSOLE) {
            // Nothing is made: a program that runs at a terminal starts at a static method, the way one
            // does anywhere else, and has no instance of itself to be called on.
            process.beginStatic(program.entryPoint(), MAIN);
        } else {
            final Values.Obj script = process.create(program.entryPoint());
            if (script == null) {
                return Started.failed(name + ": " + program.entryPoint() + " cannot be made");
            }
            process.begin(script, WHEN_STARTED);
        }
        final int id = this.next++;
        this.live.add(new Live(id, name, assembly, room, process));
        return new Started(id, name + " started as " + id);
    }

    /**
     * Stops a program, letting it say goodbye first.
     *
     * <p>The farewell is paid for out of a budget of its own rather than the machine's, because a machine
     * that is being taken apart cannot be asked to wait several ticks for it, and a program that spends
     * more than that has forfeited the chance to finish.
     */
    public boolean stop(final int id) {
        final Live one = this.byId(id);
        if (one == null) {
            return false;
        }
        final Values.Obj script = one.process().script();
        if (script != null && one.process().state() != Process.State.HALTED) {
            one.process().begin(script, WHEN_STOPPED);
            one.process().step(FAREWELL);
        }
        this.live.remove(one);
        if (this.held == id) {
            this.held = 0;
        }
        return true;
    }

    /** Stops everything, as a machine being turned off or broken does. */
    public void stopAll() {
        for (final Live one : List.copyOf(this.live)) {
            this.stop(one.id());
        }
    }

    /**
     * Gives the machine's instructions out and runs them.
     *
     * <p>Every program that is still going gets the same share of the tick, and whatever does not divide
     * evenly goes to the ones that have been waiting longest. A program that has finished what it was
     * asked to do is asked again, which is what makes a script a script rather than something that runs
     * once.
     */
    public void tick(final int budget) {
        this.tick(budget, null);
    }

    /**
     * The same, with a way to look up what the network holds.
     *
     * <p>Everything being watched is looked up once, however many programs are watching it, and the
     * answers are handed to each of them. A machine watching nothing pays nothing for the ability.
     */
    public void tick(final int budget, final java.util.function.ToLongFunction<String> stock) {
        if (stock != null && !this.live.isEmpty()) {
            final java.util.Map<String, Long> totals = new java.util.LinkedHashMap<>();
            for (final Live one : this.live) {
                for (final String item : one.process().watching()) {
                    totals.computeIfAbsent(item, stock::applyAsLong);
                }
            }
            if (!totals.isEmpty()) {
                for (final Live one : this.live) {
                    one.process().deliver(totals);
                }
            }
        }
        if (this.live.isEmpty() || budget <= 0) {
            return;
        }
        final List<Live> ready = new ArrayList<>();
        final List<Live> done = new ArrayList<>();
        for (final Live one : this.live) {
            final Process.State state = one.process().state();
            final boolean console = one.process().shape() == Shape.CONSOLE;
            if (state == Process.State.HALTED || state == Process.State.FINISHED) {
                // A program that runs at a terminal is done when it returns, and is asked nothing more;
                // one that stays up is asked again, which is what makes it stay up. Either way, a
                // finished terminal program only leaves once whoever was waiting on it has read it.
                if (console && one.id() != this.held) {
                    done.add(one);
                } else if (!console && state == Process.State.FINISHED
                        && one.process().script() != null) {
                    one.process().begin(one.process().script(), EVERY_TICK);
                    ready.add(one);
                }
                continue;
            }
            ready.add(one);
        }
        this.live.removeAll(done);
        if (ready.isEmpty()) {
            return;
        }
        final int share = budget / ready.size();
        final int over = budget % ready.size();
        for (int i = 0; i < ready.size(); i++) {
            ready.get(i).process().step(share + (i < over ? 1 : 0));
        }
    }

    // ---------------------------------------------------------------- across a reload

    private static final String PROCESSES = "processes";
    private static final String NEXT = "next";
    private static final String ID = "id";
    private static final String NAME = "name";
    private static final String ASSEMBLY = "assembly";
    private static final String HEAP = "heap";
    private static final String STATE = "state";
    private static final String HELD = "held";
    private static final String SHOWN = "shown";

    /** Writes every running program down. */
    public void save(final CompoundTag tag) {
        final ListTag written = new ListTag();
        for (final Live one : this.live) {
            final CompoundTag each = new CompoundTag();
            each.putInt(ID, one.id());
            each.putString(NAME, one.name());
            each.putString(ASSEMBLY, one.assembly());
            each.putInt(HEAP, one.heapMb());
            each.put(STATE, SnapshotTag.write(one.process().save()));
            written.add(each);
        }
        tag.put(PROCESSES, written);
        tag.putInt(NEXT, this.next);
        tag.putInt(HELD, this.held);
        tag.putInt(SHOWN, this.shown);
    }

    /** Reads them back, each one carrying on from the instruction it had reached. */
    public void load(final CompoundTag tag, final Host host) {
        this.live.clear();
        this.next = Math.max(1, tag.getInt(NEXT));
        this.held = tag.getInt(HELD);
        this.shown = tag.getInt(SHOWN);
        final ListTag written = tag.getList(PROCESSES, Tag.TAG_COMPOUND);
        for (int i = 0; i < written.size(); i++) {
            final CompoundTag each = written.getCompound(i);
            final String name = each.getString(NAME);
            final String assembly = each.getString(ASSEMBLY);
            final DiagnosticBag bag = new DiagnosticBag(name);
            final AsmProgram read = new AsmReader(assembly, bag).read();
            if (bag.hasErrors()) {
                // The listing was saved with the process, so this cannot happen to a save this version
                // wrote; dropping the process is still better than refusing to load the machine.
                continue;
            }
            final Process process = Process.restore(Loaded.of(read),
                    SnapshotTag.read(each.getCompound(STATE)), host);
            this.live.add(new Live(each.getInt(ID), name, assembly, each.getInt(HEAP), process));
        }
    }

    // ---------------------------------------------------------------- what the machine is worth

    /** The fewest instructions a tick, so even the oldest processor that can run this gets somewhere. */
    public static final int LEAST_PER_TICK = 32;

    /** The most, so one machine cannot spend the server's tick on a loop that never ends. */
    public static final int MOST_PER_TICK = 2048;

    /**
     * What a machine's processors are worth in a tick, given their cores times their megahertz added up.
     *
     * <p>It follows the clock, so a faster machine really does get through more of a script in the same
     * second, and it is bounded at both ends: an old machine still moves, and a new one cannot take the
     * server's tick with it.
     */
    public static int budgetFor(final long coreMegahertz) {
        if (coreMegahertz <= 0) {
            return 0;
        }
        return Math.clamp(coreMegahertz / 8, LEAST_PER_TICK, MOST_PER_TICK);
    }
}
