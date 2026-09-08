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
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.computers.cannon.asm.AsmProgram;
import dev.jstech.computers.cannon.asm.AsmReader;
import dev.jstech.computers.cannon.run.IHost;
import dev.jstech.computers.cannon.run.Loaded;
import dev.jstech.computers.cannon.run.Process;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Being told instead of asking.
 *
 * <p>The thing worth getting right is not the telling but the not-telling: a program told once that the
 * iron has run low must not be told again every tick that it is still low, and must be told again if it
 * runs low a second time.
 */
class WatchTest {

    private static final long ROOM = 64L * 1024;
    private static final int PLENTY = 1_000_000;

    private static Process start(final String body) {
        final String source = "class Watcher : IScript {\n"
                + "    public void OnInit() {\n" + body + "\n    }\n"
                + "    public void OnTick() { }\n"
                + "    public void Told(StockEvent e) {\n"
                + "        Console.PrintLine(e.Item + \" \" + e.Previous + \" -> \" + e.Total);\n"
                + "    }\n"
                + "    public void OnDestroy() { }\n}\n";
        final CannonCompiler.Result built =
                CannonCompiler.compile(List.of(new SourceFile("Watcher.can", source)));
        assertTrue(built.ok(), () -> String.join("\n", built.lines()));
        final DiagnosticBag bag = new DiagnosticBag("Watcher.asm");
        final AsmProgram written = new AsmReader(built.assembly(), bag).read();
        assertFalse(bag.hasErrors(), () -> String.join("\n",
                bag.sorted().stream().map(Diagnostic::format).toList()));
        final Loaded program = Loaded.of(written);
        final Process process = new Process(program, ROOM, IHost.still());
        process.begin(process.create(program.entryPoint()), "OnInit");
        process.step(PLENTY);
        assertEquals(Process.State.FINISHED, process.state(),
                () -> "setting the watch up: " + process.message());
        return process;
    }

    /** Hands the process a reading and lets it run whatever that set off. */
    private static void hold(final Process process, final long total) {
        process.deliver(Map.of("iron", total));
        process.step(PLENTY);
    }

    @Test
    void watch_tellsTheProgramWhenTheNumberChanges() {
        final Process process = start("        Network.Watch(\"iron\", Told);");
        hold(process, 100);
        hold(process, 100);
        hold(process, 80);
        // The first reading is only a reading: a program has not seen a change until there is one.
        assertEquals(List.of("iron 100 -> 80"), process.console());
    }

    @Test
    void watchBelow_tellsItOnTheWayDownAndNotAgainWhileItStaysDown() {
        final Process process = start("        Network.WatchBelow(\"iron\", 50, Told);");
        hold(process, 100);
        hold(process, 40);
        hold(process, 30);
        hold(process, 20);
        assertEquals(List.of("iron 100 -> 40"), process.console());
    }

    @Test
    void watchBelow_tellsItAgainWhenItHasBeenBackUp() {
        final Process process = start("        Network.WatchBelow(\"iron\", 50, Told);");
        hold(process, 100);
        hold(process, 40);
        hold(process, 90);
        hold(process, 10);
        assertEquals(List.of("iron 100 -> 40", "iron 90 -> 10"), process.console());
    }

    @Test
    void watchAbove_tellsItOnTheWayUp() {
        final Process process = start("        Network.WatchAbove(\"iron\", 500, Told);");
        hold(process, 100);
        hold(process, 900);
        hold(process, 1000);
        assertEquals(List.of("iron 100 -> 900"), process.console());
    }

    @Test
    void watch_doesNotGoOffForAProgramThatStartedWithItAlreadyLow() {
        final Process process = start("        Network.WatchBelow(\"iron\", 50, Told);");
        hold(process, 10);
        hold(process, 5);
        // It has not just run low; it was already low. Telling the program otherwise would be a lie.
        assertEquals(List.of(), process.console());
    }

    @Test
    void watch_stopsWhenTheProgramLetsGoOfIt() {
        final Process process = start("""
                        Subscription kept = Network.Watch("iron", Told);
                        dispose kept;
                """);
        hold(process, 100);
        hold(process, 50);
        assertEquals(List.of(), process.console());
        assertTrue(process.watching().isEmpty(), "and the machine stops looking it up");
    }

    @Test
    void watch_namesEverythingBeingWatchedOnceEachHoweverManyAreWaiting() {
        final Process process = start("""
                        Network.Watch("iron", Told);
                        Network.WatchBelow("iron", 10, Told);
                        Network.Watch("copper", Told);
                """);
        assertEquals(List.of("iron", "copper"), process.watching());
    }

    @Test
    void watch_carriesOnAcrossASaveWithoutCryingWolf() {
        final Process before = start("        Network.WatchBelow(\"iron\", 50, Told);");
        hold(before, 100);
        hold(before, 40);
        assertEquals(List.of("iron 100 -> 40"), before.console());
        final Process after = Process.restore(before.program(), before.save(), IHost.still());
        hold(after, 30);
        hold(after, 20);
        // It knew it was already below when the world came back, so it does not say so a second time.
        assertEquals(List.of("iron 100 -> 40"), after.console());
        hold(after, 80);
        hold(after, 10);
        assertEquals(List.of("iron 100 -> 40", "iron 80 -> 10"), after.console());
    }
}
