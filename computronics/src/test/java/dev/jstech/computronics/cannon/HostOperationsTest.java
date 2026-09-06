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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A program asking the network to move things. Two things matter here and neither is the moving: that a
 * refusal is something a script can carry on from rather than a stop, and that every ask says which
 * program made it, because a base runs many at once.
 */
class HostOperationsTest {

    private static final long ROOM = 64L * 1024;
    private static final int PLENTY = 1_000_000;

    /** A network that writes down what it was asked and by whom, and refuses one particular thing. */
    private static final class Asked implements Host {

        private final List<String> log = new ArrayList<>();

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
            return "Operations".equals(owner);
        }

        @Override
        public Reply call(final String owner, final String member, final List<Object> arguments,
                          final String caller, final int line) {
            final String item = arguments.isEmpty() ? "" : String.valueOf(arguments.getFirst());
            if ("List".equals(member)) {
                return Reply.of(new Values.ListValue(), 50);
            }
            if ("Reprioritise".equals(member)) {
                final Values.Obj made = new Values.Obj("AskResult");
                final String wanted = arguments.size() < 2 ? "" : String.valueOf(arguments.get(1));
                final boolean known = "high".equals(wanted) || "low".equals(wanted);
                made.set("Ok", known);
                made.set("Message", known ? "a1 is now " + wanted : "no such priority: " + wanted);
                if (known) {
                    this.log.add("Reprioritise " + item + " to " + wanted);
                }
                return Reply.of(made, 200);
            }
            if (!"Pull".equals(member) && !"Push".equals(member) && !"Craft".equals(member)) {
                throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, "Operations has no " + member);
            }
            final long amount = arguments.size() > 1 && arguments.get(1) instanceof Number n
                    ? n.longValue() : 0L;
            final Values.Obj made = new Values.Obj("AskResult");
            if ("minecraft:bedrock".equals(item)) {
                made.set("Ok", false);
                made.set("Message", "no pattern crafts Bedrock");
                return Reply.of(made, 200);
            }
            this.log.add(member + " " + amount + " " + item + " for " + caller);
            made.set("Ok", true);
            made.set("Message", member + " queued");
            return Reply.of(made, 200);
        }
    }

    private static Process run(final Asked net, final String className, final String body) {
        final String source = "class " + className + " : IScript {\n"
                + "    public void OnInit() { }\n"
                + "    public void OnTick() {\n" + body + "\n    }\n"
                + "    public void OnDestroy() { }\n}\n";
        final CannonCompiler.Result built =
                CannonCompiler.compile(List.of(new SourceFile(className + ".can", source)));
        assertTrue(built.ok(), () -> String.join("\n", built.lines()));
        final DiagnosticBag bag = new DiagnosticBag(className + ".asm");
        final AsmProgram written = new AsmReader(built.assembly(), bag).read();
        assertFalse(bag.hasErrors(), () -> String.join("\n",
                bag.sorted().stream().map(Diagnostic::format).toList()));
        final Loaded program = Loaded.of(written);
        final Process process = new Process(program, ROOM, net);
        process.begin(process.create(program.entryPoint()), "OnTick");
        process.step(PLENTY);
        return process;
    }

    @Test
    void operations_asksTheNetworkToMoveSomething() {
        final Asked net = new Asked();
        final Process process = run(net, "Restock", """
                        AskResult asked = Operations.Pull("minecraft:iron_ingot", 64);
                        Console.PrintLine(asked.Ok ? "asked" : "refused");
                """);
        assertEquals(Process.State.FINISHED, process.state(), String.valueOf(process.message()));
        assertEquals(List.of("asked"), process.console());
        assertEquals(List.of("Pull 64 minecraft:iron_ingot for Restock"), net.log);
    }

    @Test
    void operations_namesTheScriptThatAskedNotJustThatAProgramDid() {
        final Asked net = new Asked();
        run(net, "Smelter", "        Operations.Craft(\"minecraft:iron_ingot\", 8);");
        run(net, "Tidier", "        Operations.Push(\"minecraft:cobblestone\", 512);");
        // Two programs on one machine, and the network can tell which of them asked for what.
        assertEquals(List.of("Craft 8 minecraft:iron_ingot for Smelter",
                "Push 512 minecraft:cobblestone for Tidier"), net.log);
    }

    @Test
    void operations_letsAScriptCarryOnWhenTheNetworkRefuses() {
        final Asked net = new Asked();
        final Process process = run(net, "Hopeful", """
                        AskResult asked = Operations.Craft("minecraft:bedrock", 1);
                        if (!asked.Ok) {
                            Console.PrintLine("could not: " + asked.Message);
                        }
                        Console.PrintLine("carrying on");
                """);
        // A refusal is not the program's mistake, so it is answered rather than thrown.
        assertEquals(Process.State.FINISHED, process.state(), String.valueOf(process.message()));
        assertEquals(List.of("could not: no pattern crafts Bedrock", "carrying on"), process.console());
        assertTrue(net.log.isEmpty(), "and nothing was queued");
    }

    @Test
    void operations_movesARunningOneUpTheQueue() {
        final Asked net = new Asked();
        final Process process = run(net, "Impatient", """
                        AskResult moved = Operations.Reprioritise("a1", "high");
                        Console.PrintLine(moved.Message);
                        AskResult bad = Operations.Reprioritise("a1", "immediately");
                        Console.PrintLine(bad.Ok ? "moved" : bad.Message);
                """);
        assertEquals(Process.State.FINISHED, process.state(), String.valueOf(process.message()));
        assertEquals(List.of("a1 is now high", "no such priority: immediately"), process.console());
        assertEquals(List.of("Reprioritise a1 to high"), net.log);
    }

    @Test
    void operations_costsFarMoreToAskForWorkThanToLookAtIt() {
        final Asked net = new Asked();
        final Process looking = run(net, "Looker", "        List<OperationInfo> ops = Operations.List();");
        final Process asking = run(net, "Asker", "        Operations.Pull(\"minecraft:stone\", 1);");
        assertTrue(asking.spent() > looking.spent() + 100,
                "asking the network to work costs more than reading it; "
                        + asking.spent() + " against " + looking.spent());
    }

    @Test
    void operations_isNotThereAtAllOnAMachineOffTheNetwork() {
        final Process process = new Process(compile(), ROOM, Host.still());
        process.begin(process.create("Lonely"), "OnTick");
        process.step(PLENTY);
        assertEquals(Process.State.HALTED, process.state());
        assertTrue(process.message().contains("Operations"), process.message());
    }

    private static Loaded compile() {
        final CannonCompiler.Result built = CannonCompiler.compile(List.of(new SourceFile("Lonely.can", """
                class Lonely : IScript {
                    public void OnInit() { }
                    public void OnTick() { Operations.Pull("minecraft:stone", 1); }
                    public void OnDestroy() { }
                }
                """)));
        assertTrue(built.ok(), () -> String.join("\n", built.lines()));
        final DiagnosticBag bag = new DiagnosticBag("Lonely.asm");
        final AsmProgram written = new AsmReader(built.assembly(), bag).read();
        assertFalse(bag.hasErrors(), () -> String.join("\n",
                bag.sorted().stream().map(Diagnostic::format).toList()));
        return Loaded.of(written);
    }
}
