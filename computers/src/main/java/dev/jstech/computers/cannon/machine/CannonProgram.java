/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon.machine;

import dev.jstech.computers.cannon.Shape;
import dev.jstech.computers.cannon.run.Process;
import dev.jstech.computers.cannon.run.Values;
import dev.jstech.computers.cannon.save.SnapshotTag;
import dev.jstech.core.language.ILanguageProcess;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

/**
 * One running Cannon program, as a machine sees it.
 *
 * <p>Everything on this side of it is in the machine's words (a budget, a state, what it printed)
 * and everything on the other side is the language's. The three lifecycle calls are the translation:
 * a machine says "another tick has come", and only here does that mean {@code OnTick}.
 */
final class CannonProgram implements ILanguageProcess {

    /** What a program is allowed to spend on its farewell, out of nobody's tick. */
    private static final int FAREWELL = 4096;

    private final Process process;

    CannonProgram(final Process process) {
        this.process = process;
    }

    /** The program underneath, for the parts of this mod that know it is Cannon. */
    Process process() {
        return this.process;
    }

    @Override
    public int step(final int budget) {
        return this.process.step(budget);
    }

    @Override
    public State state() {
        return switch (this.process.state()) {
            case RUNNING -> State.RUNNING;
            case PARKED -> State.PARKED;
            case FINISHED -> State.FINISHED;
            case HALTED -> State.HALTED;
        };
    }

    @Override
    public String message() {
        return this.process.message() == null ? "" : this.process.message();
    }

    @Override
    public List<String> console() {
        return this.process.console();
    }

    @Override
    public int written() {
        return this.process.written();
    }

    @Override
    public int spent() {
        return this.process.spent();
    }

    @Override
    public long heldBytes() {
        return this.process.heap().used();
    }

    @Override
    public long heapBytes() {
        return this.process.heap().budget();
    }

    @Override
    public boolean isService() {
        return this.process.shape() == Shape.SCRIPT;
    }

    @Override
    public void onTick() {
        final Values.Obj script = this.process.script();
        if (script != null) {
            this.process.begin(script, "OnTick");
        }
    }

    @Override
    public void onStop(final int budget) {
        final Values.Obj script = this.process.script();
        if (script == null || this.process.state() == Process.State.HALTED) {
            return;
        }
        this.process.begin(script, "OnDestroy");
        this.process.step(budget > 0 ? budget : FAREWELL);
    }

    @Override
    public List<String> watching() {
        return this.process.watching();
    }

    @Override
    public void offerInput(final String line) {
        this.process.offerInput(line);
    }

    @Override
    public boolean waitingForInput() {
        return this.process.waitingForInput();
    }

    @Override
    public String name() {
        return this.process.name();
    }

    @Override
    public void deliver(final Map<String, Long> totals) {
        this.process.deliver(totals);
    }

    @Override
    public void save(final CompoundTag tag) {
        tag.put("Snapshot", SnapshotTag.write(this.process.save()));
    }

    /** Where {@link #save} puts it, for the language to read back. */
    static CompoundTag snapshotOf(final CompoundTag tag) {
        return tag.getCompound("Snapshot");
    }
}
