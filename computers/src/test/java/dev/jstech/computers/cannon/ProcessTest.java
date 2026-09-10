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
import dev.jstech.computers.cannon.run.Library;
import dev.jstech.computers.cannon.run.Loaded;
import dev.jstech.computers.cannon.run.Process;
import dev.jstech.computers.cannon.run.Values;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessTest {

    private static final long ROOM = 64L * 1024;
    private static final int PLENTY = 1_000_000;

    /** What every script here has to hand, so a body can call something without declaring it first. */
    private static final String HELPERS = """
                int held = 0;

                int Twice(int value) { return value + value; }

                bool Find(out int value) { value = 42; return true; }

                void Note(int n) { Console.PrintLine("note " + n); }
            """;

    /** Compiles a script whose tick does {@code body}, and loads it ready to run. */
    private static Loaded load(final String before, final String body) {
        return loadSource(before + "class Monitor : IScript {\n" + HELPERS
                + "    public void OnInit() { }\n"
                + "    public void OnTick() {\n" + body + "\n    }\n"
                + "    public void OnDestroy() { }\n}\n");
    }

    /** What every file starts with, on one line so the sources keep their line numbers. */
    private static final String PRELUDE = "using System.*; using System.IO.*; using System.Collections.*; "
            + "using System.Utils.*; using System.Machine.*; using System.Network.*; using System.Operations.*; "
            + "using System.Execution.*; namespace Tests; ";

    /** Compiles a whole file, for the scripts that need a shape of their own. */
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

    /** Runs a tick to the end, on a heap big enough not to matter. */
    private static Process run(final String body) {
        return run("", body, ROOM);
    }

    private static Process run(final String before, final String body, final long heap) {
        final Loaded program = load(before, body);
        final Process process = new Process(program, heap, IHost.still());
        final Values.Obj self = process.create(program.entryPoint());
        assertNotNull(self);
        process.begin(self, "OnTick");
        process.step(PLENTY);
        return process;
    }

    private static void assertFinished(final Process process) {
        assertEquals(Process.State.FINISHED, process.state(), () -> String.valueOf(process.message()));
    }

    @Test
    void run_writesWhatTheProgramPrints() {
        final Process process = run("        Console.PrintLine(\"hello\");");
        assertFinished(process);
        assertEquals(List.of("hello"), process.console());
    }

    @Test
    void readLine_waitsForALineAndCarriesOnWithIt() {
        final Process process = run("        Console.PrintLine(\"got \" + Console.ReadLine());");
        assertEquals(Process.State.PARKED, process.state(), "nothing typed yet: the read waits");
        assertTrue(process.waitingForInput(), "the wait is a read, not anything else");
        assertEquals(List.of(), process.console(), "nothing is printed before the line comes");
        process.offerInput("Ada");
        assertEquals(Process.State.RUNNING, process.state(), "a typed line lets the read go on");
        process.step(PLENTY);
        assertFinished(process);
        assertEquals(List.of("got Ada"), process.console());
        assertFalse(process.waitingForInput());
    }

    @Test
    void readLine_takesALineTypedAheadWithoutWaiting() {
        final Loaded program = load("", "        Console.PrintLine(\"got \" + Console.ReadLine());");
        final Process process = new Process(program, ROOM, IHost.still());
        process.offerInput("early");
        process.begin(process.create(program.entryPoint()), "OnTick");
        process.step(PLENTY);
        assertFinished(process);
        assertEquals(List.of("got early"), process.console());
    }

    @Test
    void hasLine_saysWhetherALineIsWaitingWithoutTakingIt() {
        final Loaded program = load("", """
                        Console.PrintLine(Console.HasLine() ? "yes" : "no");
                        Console.PrintLine(Console.ReadLine());
                        Console.PrintLine(Console.HasLine() ? "yes" : "no");
                """);
        final Process process = new Process(program, ROOM, IHost.still());
        process.offerInput("one");
        process.begin(process.create(program.entryPoint()), "OnTick");
        process.step(PLENTY);
        assertFinished(process);
        assertEquals(List.of("yes", "one", "no"), process.console());
    }

    @Test
    void program_isCalledWhatItCallsItself() {
        final Process process = run("""
                        Console.PrintLine("[" + Program.Name + "]");
                        Program.SetName("Farm Watch");
                        Console.PrintLine(Program.Name);
                """);
        assertFinished(process);
        assertEquals(List.of("[]", "Farm Watch"), process.console());
        assertEquals("Farm Watch", process.name());
    }

    @Test
    void program_hasNoNameUntilItGivesItselfOne() {
        final Process process = run("        Console.PrintLine(\"quiet\");");
        assertFinished(process);
        assertEquals("", process.name());
    }

    @Test
    void readInt_waitsForALineAndReadsItAsANumber() {
        final Process process = run("        Console.PrintLine(\"twice \" + (Console.ReadInt() * 2));");
        assertEquals(Process.State.PARKED, process.state(), "nothing typed yet: the read waits");
        assertTrue(process.waitingForInput());
        process.offerInput(" 21 ");
        process.step(PLENTY);
        assertFinished(process);
        assertEquals(List.of("twice 42"), process.console());
    }

    @Test
    void readInt_stopsTheProgramOnALineThatIsNotANumber() {
        final Process process = run("        Console.PrintLine(\"n \" + Console.ReadInt());");
        process.offerInput("twelve");
        process.step(PLENTY);
        assertEquals(Process.State.HALTED, process.state());
        assertTrue(process.message().contains("'twelve' is not a number"), process.message());
    }

    @Test
    void readBool_readsTheUsualSpellingsOfYesAndNo() {
        final Loaded program = load("", """
                        Console.PrintLine(Console.ReadBool() ? "yes" : "no");
                        Console.PrintLine(Console.ReadBool() ? "yes" : "no");
                        Console.PrintLine("" + (Console.ReadDouble() + Console.ReadLong()));
                """);
        final Process process = new Process(program, ROOM, IHost.still());
        process.offerInput("Yes");
        process.offerInput("off");
        process.offerInput("1.5");
        process.offerInput("40");
        process.begin(process.create(program.entryPoint()), "OnTick");
        process.step(PLENTY);
        assertFinished(process);
        assertEquals(List.of("yes", "no", "41.5"), process.console());
    }

    @Test
    void convert_readsBoolsAndTriesNumbersWithoutStopping() {
        final Process process = run("""
                        long big;
                        bool okBig = Convert.TryLong("9000000000", out big);
                        double half;
                        bool okHalf = Convert.TryDouble("x", out half);
                        Console.PrintLine(okBig + " " + big + " " + okHalf + " " + half);
                        Console.PrintLine(Convert.ToBool("true") + " " + Convert.ToBool("No"));
                        Console.PrintLine("" + (Convert.ToFloat("2.5") * 2));
                """);
        assertFinished(process);
        assertEquals(List.of("true 9000000000 false 0.0", "true false", "5.0"), process.console());
    }

    @Test
    void struct_isCopiedWhenAssignedAndComparedByWhatItHolds() {
        final Process process = run("struct Point { public int X; public int Y; }", """
                        Point a = new Point();
                        a.X = 1;
                        Point b = a;
                        b.X = 2;
                        Point c = new Point();
                        c.X = 1;
                        Console.PrintLine("a " + a.X + " b " + b.X);
                        Console.PrintLine(a == c ? "same" : "different");
                        Console.PrintLine(a == b ? "same" : "different");
                """, ROOM);
        assertFinished(process);
        assertEquals(List.of("a 1 b 2", "same", "different"), process.console());
    }

    @Test
    void record_readsAsItsFieldsWhenJoinedToAString() {
        /*
         * A record in a sentence reads as what it holds, through the ToString it was given, and two
         * with the same fields are equal: what a player expects of one, and what the listing has to
         * say for the machine to do it.
         */
        final Process process = run("record Item(string Name, int Qty);", """
                        Item item = new Item("iron", 3);
                        Console.PrintLine("got " + item);
                        Console.PrintLine($"as {item}");
                        string text = "and ";
                        text += item;
                        Console.PrintLine(text);
                        Console.PrintLine(item.Equals(new Item("iron", 3)) ? "equal" : "different");
                """, ROOM);
        assertFinished(process);
        assertEquals(List.of("got Item { Name = iron, Qty = 3 }", "as Item { Name = iron, Qty = 3 }",
                "and Item { Name = iron, Qty = 3 }", "equal"), process.console());
    }

    @Test
    void run_worksOutANumberAndPutsItInASentence() {
        final Process process = run("""
                        int a = 2;
                        int b = 3;
                        Console.PrintLine("sum " + (a + b));
                """);
        assertFinished(process);
        assertEquals(List.of("sum 5"), process.console());
    }

    @Test
    void run_goesRoundALoopTheRightNumberOfTimes() {
        final Process process = run("""
                        for (int i = 0; i < 3; i++) {
                            Console.PrintLine("round " + i);
                        }
                """);
        assertFinished(process);
        assertEquals(List.of("round 0", "round 1", "round 2"), process.console());
    }

    @Test
    void run_choosesTheRightWayThroughABranchAndASwitch() {
        final Process process = run("""
                        int n = 2;
                        if (n > 1) { Console.PrintLine("more"); } else { Console.PrintLine("less"); }
                        switch (n) {
                            case 1: Console.PrintLine("one"); break;
                            case 2: Console.PrintLine("two"); break;
                            default: Console.PrintLine("other"); break;
                        }
                """);
        assertFinished(process);
        assertEquals(List.of("more", "two"), process.console());
    }

    @Test
    void run_callsItsOwnMethodsAndReadsItsOwnFields() {
        final Process process = run("", """
                        held = 4;
                        Console.PrintLine("twice " + Twice(held));
                """, ROOM);
        assertFinished(process);
        assertEquals(List.of("twice 8"), process.console());
    }

    @Test
    void run_keepsAListAndWalksIt() {
        final Process process = run("""
                        List<string> names = new List<string>();
                        names.Add("first");
                        names.Add("second");
                        Console.PrintLine("count " + names.Count);
                        foreach (string name in names) { Console.PrintLine(name); }
                """);
        assertFinished(process);
        assertEquals(List.of("count 2", "first", "second"), process.console());
    }

    @Test
    void run_asksAMapForSomethingItHasAndSomethingItDoesNot() {
        final Process process = run("""
                        Map<string, int> counts = new Map<string, int>();
                        counts.Put("iron", 7);
                        if (counts.TryGet("iron", out int found)) { Console.PrintLine("iron " + found); }
                        if (!counts.TryGet("gold", out int missing)) { Console.PrintLine("no gold"); }
                """);
        assertFinished(process);
        assertEquals(List.of("iron 7", "no gold"), process.console());
    }

    @Test
    void run_fillsInWhatItWasHandedAndTheCallerSeesIt() {
        final Process process = run("", """
                        int answer;
                        if (Find(out answer)) { Console.PrintLine("got " + answer); }
                """, ROOM);
        assertFinished(process);
        assertEquals(List.of("got 42"), process.console());
    }

    @Test
    void run_callsAHandlerThroughTheDelegateItWasHandedTo() {
        final Process process = run("delegate int Count(int value);\n", """
                        Count doubler = Twice;
                        Console.PrintLine("through " + doubler(5));
                """, ROOM);
        assertFinished(process);
        assertEquals(List.of("through 10"), process.console());
    }

    @Test
    void run_letsALambdaSeeAChangeMadeAfterItWasWritten() {
        final Process process = run("delegate int Later();\n", """
                        int n = 1;
                        Later made = () => n;
                        n = 5;
                        Console.PrintLine("kept " + made());
                """, ROOM);
        assertFinished(process);
        assertEquals(List.of("kept 5"), process.console());
    }

    @Test
    void run_keepsOnlyTheLastOfWhatAChattyProgramPrints() {
        final Process process = run("""
                        for (int i = 0; i < 260; i++) {
                            Console.PrintLine("line " + i);
                        }
                """);
        assertFinished(process);
        final List<String> said = process.console();
        assertEquals(Library.CONSOLE_LINES, said.size());
        assertEquals("line 60", said.getFirst());
        assertEquals("line 259", said.getLast());
    }

    @Test
    void run_stopsWhenItDividesByZero() {
        final Process process = run("""
                        int zero = 0;
                        Console.PrintLine("before");
                        int bad = 1 / zero;
                """);
        assertEquals(Process.State.HALTED, process.state());
        assertTrue(process.message().contains("divided by zero"), process.message());
        assertEquals("before", process.console().getFirst());
    }

    @Test
    void run_stopsWhenAnotherNameForSomethingFreedIsUsed() {
        /*
         * Disposing leaves the name that did it holding nothing, so the way to reach a freed object
         * is through a second name for it, and that is what has to be caught.
         */
        final Process process = run("""
                        List<string> names = new List<string>();
                        List<string> also = names;
                        dispose names;
                        also.Add("late");
                """);
        assertEquals(Process.State.HALTED, process.state());
        assertTrue(process.message().contains("disposed"), process.message());
    }

    @Test
    void run_saysThereIsNothingThereWhenTheNameWasEmptied() {
        final Process process = run("""
                        List<string> names = new List<string>();
                        dispose names;
                        names.Add("late");
                """);
        assertEquals(Process.State.HALTED, process.state());
        assertTrue(process.message().contains("nothing here"), process.message());
    }

    @Test
    void run_treatsTheNumberThatStandsForFalseAsFalse() {
        final Process process = run("""
                        while (false) { Console.PrintLine("never"); }
                        bool no = false;
                        if (!no) { Console.PrintLine("not false"); }
                """);
        assertFinished(process);
        assertEquals(List.of("not false"), process.console());
    }

    @Test
    void run_stopsWhenItAsksForMoreThanItHas() {
        final Process process = run("", "        int[] big = new int[10000];", 1024);
        assertEquals(Process.State.HALTED, process.state());
        assertTrue(process.message().contains("out of memory"), process.message());
        assertTrue(process.message().contains("line "), process.message());
    }

    @Test
    void run_countsWhatItIsHoldingToTheByte() {
        // The object itself is a header and its one int; the array is a header and ten of them.
        final Process process = run("        int[] room = new int[10];");
        assertFinished(process);
        assertEquals(16 + 4 + 16 + 4 * 10, process.heap().used());
    }

    @Test
    void run_givesBackWhatWasFreed() {
        final Process process = run("""
                        List<string> names = new List<string>();
                        names.Add("first");
                        dispose names;
                """);
        assertFinished(process);
        assertEquals(16 + 4 + 16 + 2 * 5, process.heap().used());
    }

    @Test
    void run_spendsOnlyTheBudgetItIsGiven() {
        final Loaded program = load("", "        for (int i = 0; i < 1000; i++) { }");
        final Process process = new Process(program, ROOM, IHost.still());
        process.begin(process.create(program.entryPoint()), "OnTick");
        assertEquals(5, process.step(5));
        assertEquals(Process.State.RUNNING, process.state());
    }

    @Test
    void run_runsWhatWasQueuedAfterTheWorkThatWasAlreadyThere() {
        final Loaded program = load("", "        Console.PrintLine(\"tick\");");
        final Process process = new Process(program, ROOM, IHost.still());
        final Values.Obj self = process.create(program.entryPoint());
        process.begin(self, "OnTick");
        process.post(process.handlerFor(self, "Note"), List.of(1));
        process.post(process.handlerFor(self, "Note"), List.of(2));
        // The tick's own call is waiting too, ahead of both handlers.
        assertEquals(3, process.waiting());
        process.step(PLENTY);
        assertFinished(process);
        assertEquals(List.of("tick", "note 1", "note 2"), process.console());
    }

    @Test
    void run_spendsNothingWhileItIsWaiting() {
        final Loaded program = load("", "        Console.PrintLine(\"after\");");
        final Process process = new Process(program, ROOM, IHost.still());
        process.begin(process.create(program.entryPoint()), "OnTick");
        process.park();
        assertEquals(0, process.step(PLENTY));
        assertEquals(Process.State.PARKED, process.state());
        assertEquals(List.of(), process.console());
        process.resume();
        process.step(PLENTY);
        assertFinished(process);
        assertEquals(List.of("after"), process.console());
    }

    @Test
    void run_paysForItsConstructorOutOfTheBudgetAndRunsItFirst() {
        final Loaded program = loadSource("""
                class Monitor : IScript {
                    int held = 0;

                    public Monitor() {
                        for (int i = 0; i < 100; i++) { held = i; }
                    }

                    public void OnInit() { }
                    public void OnTick() { Console.PrintLine("tick"); }
                    public void OnDestroy() { }
                }
                """);
        final Process process = new Process(program, ROOM, IHost.still());
        process.begin(process.create(program.entryPoint()), "OnTick");
        assertEquals(5, process.step(5));
        assertEquals(Process.State.RUNNING, process.state());
        assertEquals(List.of(), process.console());
        process.step(PLENTY);
        assertFinished(process);
        assertEquals(List.of("tick"), process.console());
    }

    @Test
    void run_callsEveryHandlerJoinedToAnEventInTheOrderTheyWereJoined() {
        final Loaded program = loadSource("""
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
                """);
        final Process process = new Process(program, ROOM, IHost.still());
        process.begin(process.create(program.entryPoint()), "OnTick");
        process.step(PLENTY);
        assertFinished(process);
        assertEquals(List.of("first 1", "second 1"), process.console());
    }

    @Test
    void run_carriesOnFromWhereItStopped() {
        final Loaded program = load("", """
                        for (int i = 0; i < 3; i++) {
                            Console.PrintLine("round " + i);
                        }
                """);
        final Process process = new Process(program, ROOM, IHost.still());
        process.begin(process.create(program.entryPoint()), "OnTick");
        int guard = 0;
        while (process.state() == Process.State.RUNNING && guard < 1000) {
            process.step(3);
            guard++;
        }
        assertFinished(process);
        assertEquals(List.of("round 0", "round 1", "round 2"), process.console());
    }

    @Test
    void run_callsIntoANamespacedClassWrittenInAnotherFile() {
        final CannonCompiler.Result built = CannonCompiler.compile(List.of(
                new SourceFile("Tools.can", """
                        namespace Tools;
                        public class Counter {
                            private int n;
                            public void Add(int k) { n = n + k; }
                            public int Count() { return n; }
                            public static int Twice(int x) { return x * 2; }
                        }
                        """),
                new SourceFile("Monitor.can", """
                        using Tools.*;
                        using System.*;
                        using System.IO.*;
                        namespace Main;
                        class Monitor : IScript {
                            public void OnInit() { }
                            public void OnTick() {
                                Counter c = new Counter();
                                c.Add(5);
                                Console.PrintLine("n " + c.Count());
                                Console.PrintLine("q " + Tools.Counter.Twice(3));
                            }
                            public void OnDestroy() { }
                        }
                        """)));
        assertTrue(built.ok(), () -> String.join("\n", built.lines()));
        final DiagnosticBag bag = new DiagnosticBag("Monitor.asm");
        final AsmProgram program = new AsmReader(built.assembly(), bag).read();
        assertFalse(bag.hasErrors(), () -> String.join("\n",
                bag.sorted().stream().map(Diagnostic::format).toList()));
        final Loaded loaded = Loaded.of(program);
        final Process process = new Process(loaded, ROOM, IHost.still());
        final Values.Obj self = process.create(loaded.entryPoint());
        assertNotNull(self);
        process.begin(self, "OnTick");
        process.step(PLENTY);
        assertFinished(process);
        assertEquals(List.of("n 5", "q 6"), process.console());
    }

    @Test
    void run_fillsTheHolesOfAnInterpolatedString() {
        final Process process = run("""
                        int a = 4;
                        int b = 6;
                        string who = "sum";
                        Console.PrintLine($"Test {who}: {a + b} ({Twice(a)})");
                        Console.PrintLine($"{a}{b}");
                        Console.PrintLine($"plain");
                """);
        assertFinished(process);
        assertEquals(List.of("Test sum: 10 (8)", "46", "plain"), process.console());
    }
}
