/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.cannon.CannonCompiler;
import dev.jstech.computronics.cannon.SourceFile;
import dev.jstech.computronics.cannon.machine.CannonProcesses;
import dev.jstech.computronics.cannon.run.Host;
import dev.jstech.computronics.cannon.run.Process;
import dev.jstech.tests.JsTests;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A script on a real machine is called once when it starts and once every tick after that, and it only
 * ever gets the instructions the machine's processor is worth. These check that: what a script says over
 * several ticks, how the tick is shared when more than one is running, and that both come back unchanged
 * from a save.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class CannonProcessGameTests {

    private CannonProcessGameTests() {
    }

    private static final String ARENA = "empty";

    /** A script that says which tick it is on, so its console counts the ticks it was given. */
    private static final String COUNTER = """
            class Counter : IScript {
                int seen;

                public void OnInit() { seen = 0; Console.PrintLine("up"); }
                public void OnTick() { seen = seen + 1; Console.PrintLine("tick " + seen); }
                public void OnDestroy() { Console.PrintLine("down"); }
            }
            """;

    /** Compiles a script and gives back the listing a machine would be asked to run. */
    private static String listing(final String source) {
        final CannonCompiler.Result built =
                CannonCompiler.compile(List.of(new SourceFile("Script.can", source)));
        if (!built.ok()) {
            throw new IllegalStateException(String.join("\n", built.lines()));
        }
        return built.assembly();
    }

    private static Process only(final CannonProcesses processes) {
        return processes.all().getFirst().process();
    }

    @GameTest(template = ARENA)
    public static void cannon_callsAScriptOnceWhenItStartsAndOnceATickAfter(final GameTestHelper helper) {
        final CannonProcesses processes = new CannonProcesses();
        final CannonProcesses.Started started =
                processes.start("counter.asm", listing(COUNTER), 1, Host.still());
        helper.assertTrue(started.ok(), "it starts: " + started.message());
        for (int i = 0; i < 4; i++) {
            processes.tick(512);
        }
        final List<String> said = only(processes).console();
        helper.assertTrue(said.equals(List.of("up", "tick 1", "tick 2", "tick 3")),
                "four ticks say what they did; got " + said);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void cannon_sharesOneTickBetweenEveryProgramRunning(final GameTestHelper helper) {
        final CannonProcesses processes = new CannonProcesses();
        final String listing = listing(COUNTER);
        processes.start("one.asm", listing, 1, Host.still());
        processes.start("two.asm", listing, 1, Host.still());
        processes.tick(9);
        final int first = processes.all().getFirst().process().spent();
        final int second = processes.all().get(1).process().spent();
        helper.assertTrue(first + second == 9, "the whole tick is spent; got " + first + " and " + second);
        helper.assertTrue(Math.abs(first - second) <= 1,
                "and evenly between them; got " + first + " and " + second);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void cannon_carriesEveryProgramThroughASaveAndOn(final GameTestHelper helper) {
        final CannonProcesses before = new CannonProcesses();
        before.start("counter.asm", listing(COUNTER), 2, Host.still());
        before.tick(512);
        before.tick(512);
        final CompoundTag tag = new CompoundTag();
        before.save(tag);

        final CannonProcesses after = new CannonProcesses();
        after.load(tag, Host.still());
        helper.assertTrue(after.all().size() == 1, "one program comes back; got " + after.all().size());
        helper.assertTrue(after.all().getFirst().heapMb() == 2,
                "with the memory it was given; got " + after.all().getFirst().heapMb());
        after.tick(512);
        final List<String> said = only(after).console();
        helper.assertTrue(said.equals(List.of("up", "tick 1", "tick 2")),
                "and carries on counting; got " + said);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void cannon_letsAProgramSayGoodbyeWhenItIsStopped(final GameTestHelper helper) {
        final CannonProcesses processes = new CannonProcesses();
        final int id = processes.start("counter.asm", listing(COUNTER), 1, Host.still()).id();
        processes.tick(512);
        final Process running = only(processes);
        helper.assertTrue(processes.stop(id), "it stops");
        helper.assertTrue(processes.isEmpty(), "and is gone from the list");
        helper.assertTrue(running.console().contains("down"),
                "having said goodbye; got " + running.console());
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void cannon_refusesAListingItCannotRead(final GameTestHelper helper) {
        final CannonProcesses processes = new CannonProcesses();
        final CannonProcesses.Started started =
                processes.start("broken.asm", "this is not an assembly", 1, Host.still());
        helper.assertFalse(started.ok(), "it does not start");
        helper.assertTrue(processes.isEmpty(), "and nothing is left running");
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void cannon_budgetFollowsTheClockBetweenItsBounds(final GameTestHelper helper) {
        helper.assertTrue(CannonProcesses.budgetFor(0) == 0, "no processor is worth nothing");
        helper.assertTrue(CannonProcesses.budgetFor(100) == CannonProcesses.LEAST_PER_TICK,
                "the slowest machine still moves; got " + CannonProcesses.budgetFor(100));
        helper.assertTrue(CannonProcesses.budgetFor(700) == 87,
                "an early one follows its clock; got " + CannonProcesses.budgetFor(700));
        helper.assertTrue(CannonProcesses.budgetFor(4000) == 500,
                "a middling one too; got " + CannonProcesses.budgetFor(4000));
        helper.assertTrue(CannonProcesses.budgetFor(19_200) == CannonProcesses.MOST_PER_TICK,
                "and the fastest is capped; got " + CannonProcesses.budgetFor(19_200));
        helper.succeed();
    }
}
