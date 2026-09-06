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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * A program asking the network what it holds, and a program asking a machine that has no network. The
 * second is the one that matters: not being on a network is an ordinary state of an ordinary computer,
 * and the program has to be able to find that out without stopping.
 */
class HostNetworkTest {

    private static final long ROOM = 64L * 1024;
    private static final int PLENTY = 1_000_000;

    /** A network of two servers holding a few things between them. */
    private static final class Net implements Host {

        private final boolean linked;
        private final Map<String, Map<String, Long>> holdings = new LinkedHashMap<>();

        Net(final boolean linked) {
            this.linked = linked;
        }

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
            return "Network".equals(owner);
        }

        @Override
        public Reply call(final String owner, final String member, final List<Object> arguments,
                          final int line) {
            if ("Online".equals(member)) {
                return Reply.of(this.linked, 10);
            }
            if ("Current".equals(member)) {
                return Reply.of(this.linked ? "net-1" : null, 10);
            }
            if (!this.linked) {
                throw new Halt(Halt.Reason.NO_NETWORK, line, "this computer is not on a network");
            }
            final String item = arguments.isEmpty() ? "" : String.valueOf(arguments.getFirst());
            return switch (member) {
                case "Total" -> {
                    long sum = 0;
                    for (final Long held : this.holdings.getOrDefault(item, Map.of()).values()) {
                        sum += held;
                    }
                    yield Reply.of(sum, 50);
                }
                case "Types" -> {
                    final Values.ListValue names = new Values.ListValue();
                    names.items().addAll(this.holdings.keySet());
                    yield Reply.of(names, 50);
                }
                case "Find" -> {
                    final Values.ListValue all = new Values.ListValue();
                    this.holdings.getOrDefault(item, Map.of()).forEach((server, held) -> {
                        final Values.Obj made = new Values.Obj("HoldingInfo");
                        made.set("Server", server);
                        made.set("Quantity", held);
                        all.items().add(made);
                    });
                    yield Reply.of(all, 50);
                }
                default -> throw new Halt(Halt.Reason.NO_SUCH_MEMBER, line, "Network has no " + member);
            };
        }
    }

    private static Process run(final Net net, final String body) {
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
        final Process process = new Process(program, ROOM, net);
        process.begin(process.create(program.entryPoint()), "OnTick");
        process.step(PLENTY);
        return process;
    }

    private static Net stocked() {
        final Net net = new Net(true);
        net.holdings.put("minecraft:iron_ingot", new LinkedHashMap<>(Map.of("Server A", 640L)));
        net.holdings.put("minecraft:copper_ingot", new LinkedHashMap<>(Map.of("Server A", 128L)));
        return net;
    }

    @Test
    void network_tellsAProgramHowMuchTheNetworkHolds() {
        final Process process = run(stocked(), """
                        Console.PrintLine("iron " + Network.Total("minecraft:iron_ingot"));
                        Console.PrintLine("gold " + Network.Total("minecraft:gold_ingot"));
                """);
        assertEquals(Process.State.FINISHED, process.state(), String.valueOf(process.message()));
        assertEquals(List.of("iron 640", "gold 0"), process.console());
    }

    @Test
    void network_walksWhatItHoldsAndWhoHoldsIt() {
        final Process process = run(stocked(), """
                        foreach (string type in Network.Types()) {
                            Console.PrintLine(type);
                        }
                        foreach (HoldingInfo where in Network.Find("minecraft:iron_ingot")) {
                            Console.PrintLine(where.Server + " has " + where.Quantity);
                        }
                """);
        assertEquals(Process.State.FINISHED, process.state(), String.valueOf(process.message()));
        assertEquals(List.of("minecraft:iron_ingot", "minecraft:copper_ingot", "Server A has 640"),
                process.console());
    }

    @Test
    void network_letsAProgramAskWhetherThereIsOneAtAll() {
        final Process process = run(new Net(false), """
                        if (Network.Online) {
                            Console.PrintLine("on " + Network.Current);
                        } else {
                            Console.PrintLine("standalone");
                        }
                """);
        assertEquals(Process.State.FINISHED, process.state(), String.valueOf(process.message()));
        assertEquals(List.of("standalone"), process.console());
    }

    @Test
    void network_stopsAProgramThatReadsItWithoutOne() {
        final Process process = run(new Net(false), "        long n = Network.Total(\"minecraft:stone\");");
        assertEquals(Process.State.HALTED, process.state());
        assertTrue(process.message().contains("not on a network"), process.message());
    }

    @Test
    void network_readingItCostsFarMoreThanTheProgramsOwnArithmetic() {
        final Net net = stocked();
        final Process quiet = run(net, "        long n = 1 + 1;");
        final Process asking = run(net, "        long n = Network.Total(\"minecraft:iron_ingot\");");
        assertTrue(asking.spent() > quiet.spent() + 40,
                "asking the network is charged; " + asking.spent() + " against " + quiet.spent());
    }

    @Test
    void network_decidesSomethingFromWhatItRead() {
        final Process process = run(stocked(), """
                        long iron = Network.Total("minecraft:iron_ingot");
                        if (iron < 1000) {
                            Console.PrintLine("running low: " + iron);
                        }
                """);
        assertEquals(Process.State.FINISHED, process.state(), String.valueOf(process.message()));
        assertEquals(List.of("running low: 640"), process.console());
    }
}
