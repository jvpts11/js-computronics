/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.cannon;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.computronics.cannon.asm.AsmProgram;
import dev.jstech.computronics.cannon.asm.AsmReader;
import dev.jstech.computronics.cannon.run.Halt;
import dev.jstech.computronics.cannon.run.Host;
import dev.jstech.computronics.cannon.run.Loaded;
import dev.jstech.computronics.cannon.run.Process;
import dev.jstech.computronics.cannon.run.Values;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A program reading the machine it is on. What matters here is the shape of the answers: the little
 * records the machine hands back are ordinary objects to the program, which reads their fields, keeps
 * them, walks lists of them and pays for them like anything else it holds.
 */
class HostComputerTest {

    private static final long ROOM = 64L * 1024;
    private static final int PLENTY = 1_000_000;

    /** A machine that says the same things every time, so the program's side is what is under test. */
    private static final class Machine implements Host {

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
            return "Computer".equals(owner);
        }

        @Override
        public Reply call(final String owner, final String member, final List<Object> arguments,
                          final String caller, final int line) {
            return switch (member) {
                case "Name" -> Reply.of("Workshop", 5);
                case "RamMb" -> Reply.of(4096, 5);
                case "FreeRamMb" -> Reply.of(3900, 5);
                case "Online" -> Reply.of(true, 5);
                case "Cpu" -> {
                    final Values.Obj cpu = new Values.Obj("CpuInfo");
                    cpu.set("Mhz", 2400);
                    cpu.set("Cores", 2);
                    cpu.set("Era", "legacy");
                    yield Reply.of(cpu, 5);
                }
                case "Os" -> {
                    final Values.Obj os = new Values.Obj("OsInfo");
                    os.set("Id", "jsc:frames_xp");
                    os.set("Name", "Frames XP");
                    yield Reply.of(os, 5);
                }
                case "Disks" -> {
                    final Values.ListValue disks = new Values.ListValue();
                    disks.items().add(disk("C", 120, 500));
                    disks.items().add(disk("D", 4, 700));
                    yield Reply.of(disks, 30);
                }
                default -> throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, "Computer has no " + member);
            };
        }

        private static Values.Obj disk(final String mount, final long used, final long capacity) {
            final Values.Obj made = new Values.Obj("DiskInfo");
            made.set("Mount", mount);
            made.set("UsedMb", used);
            made.set("CapacityMb", capacity);
            return made;
        }
    }

    private static Process run(final String body) {
        final String source = "class Monitor : IScript {\n"
                + "    public void OnInit() { }\n"
                + "    public void OnTick() {\n" + body + "\n    }\n"
                + "    public void OnDestroy() { }\n}\n";
        final CannonCompiler.Result built =
                CannonCompiler.compile(List.of(new SourceFile("Monitor.can", source)));
        assertTrue(built.ok(), () -> String.join("\n", built.lines()));
        final DiagnosticBag bag = new DiagnosticBag("Monitor.asm");
        final AsmProgram written = new AsmReader(built.assembly(), bag).read();
        assertFalse(bag.hasErrors(), () -> String.join("\n",
                bag.sorted().stream().map(Diagnostic::format).toList()));
        final Loaded program = Loaded.of(written);
        final Process process = new Process(program, ROOM, new Machine());
        process.begin(process.create(program.entryPoint()), "OnTick");
        process.step(PLENTY);
        return process;
    }

    private static void assertFinished(final Process process) {
        assertEquals(Process.State.FINISHED, process.state(), () -> String.valueOf(process.message()));
    }

    @Test
    void computer_readsThePlainNumbersOffTheMachine() {
        final Process process = run("""
                        Console.PrintLine(Computer.Name + " has " + Computer.RamMb + " MB");
                        Console.PrintLine("free " + Computer.FreeRamMb);
                """);
        assertFinished(process);
        assertEquals(List.of("Workshop has 4096 MB", "free 3900"), process.console());
    }

    @Test
    void computer_readsAFieldOfTheRecordItIsHanded() {
        final Process process = run("""
                        CpuInfo cpu = Computer.Cpu;
                        Console.PrintLine(cpu.Cores + " cores at " + cpu.Mhz + " (" + cpu.Era + ")");
                """);
        assertFinished(process);
        assertEquals(List.of("2 cores at 2400 (legacy)"), process.console());
    }

    @Test
    void computer_readsThroughWithoutKeepingTheRecordItself() {
        final Process process = run("        Console.PrintLine(\"on \" + Computer.Os.Name);");
        assertFinished(process);
        assertEquals(List.of("on Frames XP"), process.console());
    }

    @Test
    void computer_walksAListOfRecords() {
        final Process process = run("""
                        foreach (DiskInfo disk in Computer.Disks()) {
                            Console.PrintLine(disk.Mount + ": " + disk.UsedMb + " of " + disk.CapacityMb);
                        }
                """);
        assertFinished(process);
        assertEquals(List.of("C: 120 of 500", "D: 4 of 700"), process.console());
    }

    @Test
    void computer_worksOutSomethingFromWhatItRead() {
        final Process process = run("""
                        long used = 0;
                        long room = 0;
                        foreach (DiskInfo disk in Computer.Disks()) {
                            used = used + disk.UsedMb;
                            room = room + disk.CapacityMb;
                        }
                        Console.PrintLine("using " + used + " of " + room);
                """);
        assertFinished(process);
        assertEquals(List.of("using 124 of 1200"), process.console());
    }

    @Test
    void computer_whatItHandsBackWeighsOnTheProgramsHeap() {
        final Process process = run("        List<DiskInfo> disks = Computer.Disks();");
        assertFinished(process);
        // The list, the two records in it, and the text of each mount: all the program's to hold.
        assertTrue(process.heap().used() > 100,
                "the records are counted; used " + process.heap().used());
    }

    @Test
    void computer_stopsTheProgramWhenItAsksForSomethingTheMachineHasNot() {
        final Process process = run("        Console.PrintLine(Computer.Name);");
        assertFinished(process);
        final Process bad = run("        List<string> p = Computer.Programs();");
        assertEquals(Process.State.HALTED, bad.state());
        assertTrue(bad.message().contains("Programs"), bad.message());
    }
}
