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
import dev.jstech.computers.cannon.run.Halt;
import dev.jstech.computers.cannon.run.IHost;
import dev.jstech.computers.cannon.run.Loaded;
import dev.jstech.computers.cannon.run.Process;
import dev.jstech.computers.cannon.run.Values;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A program reaching out of itself. The machine here is a made-up one whose whole world is a map of
 * paths to text, which is enough to check that what a program asks for is what it gets, that the asking
 * costs what it should, and that what comes back belongs to the program's own memory.
 */
class HostFileTest {

    private static final long ROOM = 64L * 1024;
    private static final int PLENTY = 1_000_000;

    /** A machine with nothing in the world but a handful of files. */
    private static final class Drive implements IHost {

        private final Map<String, String> files = new LinkedHashMap<>();

        @Override
        public long tick() {
            return 0;
        }

        @Override
        public long dayTime() {
            return 0;
        }

        @Override
        public long day() {
            return 0;
        }

        @Override
        public boolean provides(final String owner) {
            return "File".equals(owner);
        }

        @Override
        public Reply call(final String owner, final String member, final List<Object> arguments,
                          final String caller, final int line) {
            final String path = arguments.isEmpty() ? "" : String.valueOf(arguments.getFirst());
            return switch (member) {
                case "Exists" -> Reply.of(this.files.containsKey(path), 10);
                case "Read" -> {
                    final String held = this.files.get(path);
                    if (held == null) {
                        throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, path + ": file not found");
                    }
                    yield Reply.of(held, 50);
                }
                case "TryRead" -> {
                    final String held = this.files.get(path);
                    yield new Reply(held != null, List.of(held == null ? "" : held), 50);
                }
                case "Write" -> {
                    this.files.put(path, String.valueOf(arguments.get(1)));
                    yield Reply.of(true, 100);
                }
                case "Append" -> {
                    this.files.merge(path, String.valueOf(arguments.get(1)), String::concat);
                    yield Reply.of(true, 100);
                }
                case "Delete" -> Reply.of(this.files.remove(path) != null, 100);
                case "List" -> {
                    final Values.ListValue names = new Values.ListValue();
                    names.items().addAll(new ArrayList<>(this.files.keySet()));
                    yield Reply.of(names, 50);
                }
                default -> throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, "File has no " + member);
            };
        }
    }

    private static Loaded load(final String body) {
        final String source = "using System.*; using System.IO.*; using System.Collections.*; using System.Utils.*; "
                + "using System.Machine.*; using System.Network.*; using System.Operations.*; namespace Tests; "
                + "class Monitor : IScript {\n"
                + "    public void OnInit() { }\n"
                + "    public void OnTick() {\n" + body + "\n    }\n"
                + "    public void OnDestroy() { }\n}\n";
        final CannonCompiler.Result built =
                CannonCompiler.compile(List.of(new SourceFile("Monitor.can", source)));
        assertTrue(built.ok(), () -> String.join("\n", built.lines()));
        final DiagnosticBag bag = new DiagnosticBag("Monitor.asm");
        final AsmProgram program = new AsmReader(built.assembly(), bag).read();
        assertFalse(bag.hasErrors(), () -> String.join("\n",
                bag.sorted().stream().map(Diagnostic::format).toList()));
        return Loaded.of(program);
    }

    private static Process run(final Drive drive, final String body) {
        final Loaded program = load(body);
        final Process process = new Process(program, ROOM, drive);
        final Values.Obj self = process.create(program.entryPoint());
        assertNotNull(self);
        process.begin(self, "OnTick");
        process.step(PLENTY);
        return process;
    }

    @Test
    void file_writesSomethingAndReadsItBack() {
        final Drive drive = new Drive();
        final Process process = run(drive, """
                        File.Write("C:\\\\log.txt", "first line");
                        Console.PrintLine(File.Read("C:\\\\log.txt"));
                """);
        assertEquals(Process.State.FINISHED, process.state(), String.valueOf(process.message()));
        assertEquals(List.of("first line"), process.console());
        assertEquals("first line", drive.files.get("C:\\log.txt"));
    }

    @Test
    void file_tryReadSaysWhetherItWasThereAndHandsBackWhatItFound() {
        final Drive drive = new Drive();
        drive.files.put("stock.csv", "iron,64");
        final Process process = run(drive, """
                        if (File.TryRead("stock.csv", out string held)) { Console.PrintLine("got " + held); }
                        if (!File.TryRead("gone.csv", out string missing)) { Console.PrintLine("no file"); }
                """);
        assertEquals(Process.State.FINISHED, process.state(), String.valueOf(process.message()));
        assertEquals(List.of("got iron,64", "no file"), process.console());
    }

    @Test
    void file_appendsToWhatIsAlreadyThere() {
        final Drive drive = new Drive();
        final Process process = run(drive, """
                        File.Write("log", "one");
                        File.Append("log", ";two");
                        Console.PrintLine(File.Read("log"));
                """);
        assertEquals(Process.State.FINISHED, process.state(), String.valueOf(process.message()));
        assertEquals(List.of("one;two"), process.console());
    }

    @Test
    void file_listComesBackAsAListTheProgramCanWalk() {
        final Drive drive = new Drive();
        drive.files.put("a.txt", "");
        drive.files.put("b.txt", "");
        final Process process = run(drive, """
                        List<string> names = File.List("C:\\\\");
                        foreach (string name in names) { Console.PrintLine(name); }
                """);
        assertEquals(Process.State.FINISHED, process.state(), String.valueOf(process.message()));
        assertEquals(List.of("a.txt", "b.txt"), process.console());
    }

    @Test
    void file_whatItHandsBackIsTheProgramsToHoldAndToFree() {
        final Drive drive = new Drive();
        drive.files.put("stock.csv", "iron,64");
        final Process process = run(drive, """
                        string held = File.Read("stock.csv");
                        Console.PrintLine("holding " + held.Length);
                """);
        assertEquals(Process.State.FINISHED, process.state(), String.valueOf(process.message()));
        // The text came from outside, but it weighs on this program's heap like anything else it holds.
        assertTrue(process.heap().used() >= 16 + 2L * "iron,64".length(),
                "the file's text is counted; used " + process.heap().used());
    }

    @Test
    void file_reachingTheDriveCostsMoreThanArithmetic() {
        final Drive drive = new Drive();
        drive.files.put("a", "x");
        final Process cheap = run(drive, "        int n = 1 + 1;");
        final Process dear = run(drive, "        string s = File.Read(\"a\");");
        assertTrue(dear.spent() > cheap.spent() + 40,
                "a read costs its price; " + dear.spent() + " against " + cheap.spent());
    }

    @Test
    void file_stopsTheProgramWhenItReadsSomethingThatIsNotThere() {
        final Process process = run(new Drive(), "        string s = File.Read(\"gone\");");
        assertEquals(Process.State.HALTED, process.state());
        assertTrue(process.message().contains("file not found"), process.message());
    }

    @Test
    void file_isNotThereAtAllOnAMachineThatHasNoDrives() {
        final Process process = run2("        File.Write(\"log\", \"x\");");
        assertEquals(Process.State.HALTED, process.state());
        assertTrue(process.message().contains("File"), process.message());
    }

    /** The same run, on the host that answers for nothing at all. */
    private static Process run2(final String body) {
        final Loaded program = load(body);
        final Process process = new Process(program, ROOM, IHost.still());
        process.begin(process.create(program.entryPoint()), "OnTick");
        process.step(PLENTY);
        return process;
    }
}
