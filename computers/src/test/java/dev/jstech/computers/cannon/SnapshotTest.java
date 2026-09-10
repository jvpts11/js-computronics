/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.cannon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.computers.cannon.asm.AsmProgram;
import dev.jstech.computers.cannon.asm.AsmReader;
import dev.jstech.computers.cannon.run.IHost;
import dev.jstech.computers.cannon.run.Loaded;
import dev.jstech.computers.cannon.run.Process;
import dev.jstech.computers.cannon.run.Snapshot;
import java.util.List;
import org.junit.jupiter.api.Test;

class SnapshotTest {

    private static final long ROOM = 64L * 1024;
    private static final int PLENTY = 1_000_000;

    /** How many instructions a program is allowed before it is put away and read back again. */
    private static final int SLICE = 3;

    /** How many times a program may be put away before the test gives up on it ending. */
    private static final int PATIENCE = 4000;

    private static Loaded load(final String before, final String body) {
        return loadSource(before + "class Monitor : IScript {\n"
                + "    public void OnInit() { }\n"
                + "    public void OnTick() {\n" + body + "\n    }\n"
                + "    public void OnDestroy() { }\n}\n");
    }

    /** What every file starts with, on one line so the sources keep their line numbers. */
    private static final String PRELUDE = "using System.*; using System.IO.*; using System.Collections.*; "
            + "using System.Utils.*; using System.Machine.*; using System.Network.*; using System.Operations.*; "
            + "using System.Execution.*; namespace Tests; ";

    private static Loaded loadSource(final String source) {
        final CannonCompiler.Result built =
                CannonCompiler.compile(List.of(new SourceFile("Monitor.can", PRELUDE + source)));
        assertTrue(built.ok(), () -> String.join("\n", built.lines()));
        final DiagnosticBag bag = new DiagnosticBag("Monitor.asm");
        final AsmProgram program = new AsmReader(built.assembly(), bag).read();
        assertFalse(bag.hasErrors(), () -> String.join("\n",
                bag.sorted().stream().map(Diagnostic::format).toList()));
        return Loaded.of(program);
    }

    /** Runs a tick straight through, for the answer a run through saves has to match. */
    private static Process straight(final Loaded program) {
        final Process process = new Process(program, ROOM, IHost.still());
        process.begin(process.create(program.entryPoint()), "OnTick");
        process.step(PLENTY);
        return process;
    }

    /**
     * Runs a tick a few instructions at a time, putting the process away and reading it back between
     * every slice, which is what a world being saved and loaded does to it.
     */
    private static Process throughSaves(final Loaded program) {
        Process process = new Process(program, ROOM, IHost.still());
        process.begin(process.create(program.entryPoint()), "OnTick");
        for (int i = 0; i < PATIENCE && process.state() == Process.State.RUNNING; i++) {
            process.step(SLICE);
            final Snapshot shot = process.save();
            process = Process.restore(program, shot, IHost.still());
        }
        return process;
    }

    /** Runs the same program both ways and says they agree, giving back the one that was saved. */
    private static Process bothWays(final Loaded program) {
        final Process straight = straight(program);
        final Process saved = throughSaves(program);
        assertEquals(straight.state(), saved.state(), () -> String.valueOf(saved.message()));
        assertEquals(straight.console(), saved.console());
        assertEquals(straight.heap().used(), saved.heap().used());
        return saved;
    }

    @Test
    void save_keepsTheNameTheProgramGaveItself() {
        final Loaded program = load("", """
                        Program.SetName("Sorter");
                        for (int i = 0; i < 20; i++) { Console.PrintLine("" + i); }
                        Console.PrintLine(Program.Name);
                """);
        final Process saved = bothWays(program);
        assertEquals("Sorter", saved.name(), "the name is written down with the rest and read back");
        assertEquals("Sorter", saved.console().getLast());
    }

    @Test
    void save_carriesALoopThroughToTheEnd() {
        final Process process = bothWays(load("", """
                        for (int i = 0; i < 3; i++) {
                            Console.PrintLine("round " + i);
                        }
                """));
        assertEquals(Process.State.FINISHED, process.state());
        assertEquals(List.of("round 0", "round 1", "round 2"), process.console());
    }

    @Test
    void save_keepsTwoNamesForOneThingAsOneThing() {
        final Process process = bothWays(load("", """
                        List<string> names = new List<string>();
                        List<string> also = names;
                        names.Add("first");
                        names.Add("second");
                        Console.PrintLine("also " + also.Count);
                """));
        assertEquals(List.of("also 2"), process.console());
    }

    @Test
    void save_keepsWhatAMapWasAskedToHold() {
        final Process process = bothWays(load("", """
                        Map<string, int> counts = new Map<string, int>();
                        counts.Put("iron", 7);
                        counts.Put("gold", 3);
                        if (counts.TryGet("iron", out int found)) { Console.PrintLine("iron " + found); }
                        if (!counts.TryGet("tin", out int missing)) { Console.PrintLine("no tin"); }
                """));
        assertEquals(List.of("iron 7", "no tin"), process.console());
    }

    @Test
    void save_keepsWhatALambdaWasKeeping() {
        final Process process = bothWays(load("delegate int Later();\n", """
                        int n = 1;
                        Later made = () => n;
                        n = 5;
                        Console.PrintLine("kept " + made());
                """));
        assertEquals(List.of("kept 5"), process.console());
    }

    @Test
    void save_keepsEveryHandlerJoinedToAnEvent() {
        final Process process = bothWays(loadSource("""
                delegate void Note(int n);
                class Monitor : IScript {
                    event Note Notes;

                    void First(int n) { Console.PrintLine("first " + n); }
                    void Second(int n) { Console.PrintLine("second " + n); }

                    public void OnInit() { }
                    public void OnTick() {
                        Notes += First;
                        Notes += Second;
                        Notes(1);
                    }
                    public void OnDestroy() { }
                }
                """));
        assertEquals(List.of("first 1", "second 1"), process.console());
    }

    @Test
    void save_keepsWhatAClassOfItsOwnIsHolding() {
        final Process process = bothWays(loadSource("""
                class Tally {
                    int count;

                    public Tally() { count = 0; }
                    public void Add(int n) { count = count + n; }
                    public int Total() { return count; }
                }
                class Monitor : IScript {
                    public void OnInit() { }
                    public void OnTick() {
                        Tally tally = new Tally();
                        for (int i = 1; i < 4; i++) { tally.Add(i); }
                        Console.PrintLine("total " + tally.Total());
                    }
                    public void OnDestroy() { }
                }
                """));
        assertEquals(List.of("total 6"), process.console());
    }

    @Test
    void save_keepsWhatWasPutInATypesOwnField() {
        final Process process = bothWays(loadSource("""
                class Counter {
                    static int seen;

                    public static void Saw() { seen = seen + 1; }
                    public static int Seen() { return seen; }
                }
                class Monitor : IScript {
                    public void OnInit() { }
                    public void OnTick() {
                        Counter.Saw();
                        Counter.Saw();
                        Console.PrintLine("seen " + Counter.Seen());
                    }
                    public void OnDestroy() { }
                }
                """));
        assertEquals(List.of("seen 2"), process.console());
    }

    @Test
    void save_bringsBackAProcessThatStoppedOnAMistakeAsStopped() {
        final Process process = bothWays(load("", """
                        int zero = 0;
                        Console.PrintLine("before");
                        int bad = 1 / zero;
                """));
        assertEquals(Process.State.HALTED, process.state());
        assertNotNull(process.message());
        assertTrue(process.message().contains("divided by zero"), process.message());
        assertEquals("before", process.console().getFirst());
    }

    @Test
    void save_keepsWhatWasFreedFreed() {
        final Process process = bothWays(load("", """
                        List<string> names = new List<string>();
                        names.Add("first");
                        dispose names;
                        Console.PrintLine("gone");
                """));
        assertEquals(List.of("gone"), process.console());
    }

    @Test
    void save_countsWhatItRanOnBothSidesOfTheSave() {
        final Loaded program = load("", "        for (int i = 0; i < 20; i++) { }");
        final Process straight = straight(program);
        final Process saved = throughSaves(program);
        assertEquals(straight.spent(), saved.spent());
    }
}
