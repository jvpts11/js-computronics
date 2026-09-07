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
import dev.jstech.computronics.cannon.run.IHost;
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
    private static final class Net implements IHost {

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
            return "Network".equals(owner) || "Mainframe".equals(owner);
        }

        @Override
        public Reply call(final String owner, final String member, final List<Object> arguments,
                          final String caller, final int line) {
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
            if ("Stats".equals(member)) {
                final Values.Obj made = new Values.Obj("WorkStat");
                made.set("Type", item);
                made.set("Count", "select".equals(item) ? 12 : 0);
                made.set("AverageWait", "select".equals(item) ? 3 : 0);
                made.set("AverageRun", 0);
                made.set("ShortfallPercent", 0);
                made.set("Moved", "select".equals(item) ? 640L : 0L);
                return Reply.of(made, 50);
            }
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

    private static Loaded compile(final String body) {
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
        return Loaded.of(written);
    }

    private static Process run(final Net net, final String body) {
        final Loaded program = compile(body);
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
    void priceOf_chargesForHowMuchWasGatheredNotJustForAsking() {
        // A sweep of the whole network is not the same question as asking after one thing, and a program
        // doing it every tick should feel the difference.
        assertTrue(dev.jstech.computronics.cannon.machine.HostNetwork.priceOf(300)
                >= dev.jstech.computronics.cannon.machine.HostNetwork.priceOf(0) + 300,
                "a longer answer costs more");
        assertEquals(dev.jstech.computronics.cannon.machine.HostNetwork.priceOf(0),
                dev.jstech.computronics.cannon.machine.HostNetwork.priceOf(-1),
                "and nothing is charged twice for being empty");
    }

    @Test
    void network_theProcessPaysWhatTheMachineSaidTheAnswerWasWorth() {
        final Net dear = new Net(true);
        dear.holdings.put("a", new LinkedHashMap<>(Map.of("Server A", 1L)));
        final Process quiet = run(dear, "        long n = 1 + 1;");
        final Process asking = run(dear, "        long n = Network.Total(\"a\");");
        // The machine said fifty; the tick is charged fifty, not one.
        assertTrue(asking.spent() >= quiet.spent() + 45,
                "the price the machine named is charged; " + asking.spent() + " against " + quiet.spent());
    }

    @Test
    void network_runsAProgramOutOfMemoryRatherThanTruncatingWhatItAskedFor() {
        final Net huge = new Net(true);
        for (int i = 0; i < 4000; i++) {
            huge.holdings.put("a rather long name for kind number " + i,
                    new LinkedHashMap<>(Map.of("Server A", 1L)));
        }
        // A small machine asking a big network for everything is told it does not fit, which is the
        // honest answer and the one that says to put more memory in.
        final Loaded program = compile("        List<string> t = Network.Types();");
        final Process process = new Process(program, 8L * 1024, huge);
        process.begin(process.create(program.entryPoint()), "OnTick");
        process.step(PLENTY);
        assertEquals(Process.State.HALTED, process.state());
        assertTrue(process.message().contains("out of memory"), process.message());
    }

    @Test
    void mainframe_readsWhatTheNetworkHasBeenDoing() {
        final Process process = run(stocked(), """
                        WorkStat select = Mainframe.Stats("select");
                        Console.PrintLine(select.Count + " selects, " + select.Moved + " moved");
                        WorkStat craft = Mainframe.Stats("craft");
                        Console.PrintLine("crafts " + craft.Count);
                """);
        assertEquals(Process.State.FINISHED, process.state(), String.valueOf(process.message()));
        // A kind of work the network has not done reads as zero, not as nothing, so a script can add up
        // without asking first whether there is anything to add up.
        assertEquals(List.of("12 selects, 640 moved", "crafts 0"), process.console());
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
