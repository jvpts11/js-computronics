/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.cannon.CannonCompiler;
import dev.jstech.computronics.cannon.DiagnosticBag;
import dev.jstech.computronics.cannon.SourceFile;
import dev.jstech.computronics.cannon.asm.AsmProgram;
import dev.jstech.computronics.cannon.asm.AsmReader;
import dev.jstech.computronics.cannon.run.Host;
import dev.jstech.computronics.cannon.run.Loaded;
import dev.jstech.computronics.cannon.run.Process;
import dev.jstech.computronics.cannon.run.Snapshot;
import dev.jstech.computronics.cannon.save.SnapshotTag;
import dev.jstech.tests.JsTests;
import java.util.List;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A program running on a computer is stopped when the world is put away and has to carry on where it
 * left off when the world comes back. These check the writing down: a process frozen, written to a tag,
 * read back and run to the end says exactly what the same program says when nothing interrupts it.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class CannonSnapshotGameTests {

    private CannonSnapshotGameTests() {
    }

    private static final String ARENA = "empty";

    private static final long ROOM = 64L * 1024;
    private static final int PLENTY = 1_000_000;

    /** How many instructions the program is allowed before it is written to a tag and read back. */
    private static final int SLICE = 4;

    private static final int PATIENCE = 4000;

    private static Loaded load(final String body) {
        final String source = "class Monitor : IScript {\n"
                + "    public void OnInit() { }\n"
                + "    public void OnTick() {\n" + body + "\n    }\n"
                + "    public void OnDestroy() { }\n}\n";
        final CannonCompiler.Result built =
                CannonCompiler.compile(List.of(new SourceFile("Monitor.can", source)));
        if (!built.ok()) {
            throw new IllegalStateException(String.join("\n", built.lines()));
        }
        final DiagnosticBag bag = new DiagnosticBag("Monitor.asm");
        final AsmProgram program = new AsmReader(built.assembly(), bag).read();
        if (bag.hasErrors()) {
            throw new IllegalStateException(String.join("\n", bag.sorted().stream().map(d -> d.format()).toList()));
        }
        return Loaded.of(program);
    }

    private static Process straight(final Loaded program) {
        final Process process = new Process(program, ROOM, Host.still());
        process.begin(process.create(program.entryPoint()), "OnTick");
        process.step(PLENTY);
        return process;
    }

    /** Runs the program in slices, writing it to a tag and reading it back between every one. */
    private static Process throughTags(final Loaded program) {
        Process process = new Process(program, ROOM, Host.still());
        process.begin(process.create(program.entryPoint()), "OnTick");
        for (int i = 0; i < PATIENCE && process.state() == Process.State.RUNNING; i++) {
            process.step(SLICE);
            final CompoundTag tag = SnapshotTag.write(process.save());
            process = Process.restore(program, SnapshotTag.read(tag), Host.still());
        }
        return process;
    }

    private static void bothWays(final GameTestHelper helper, final String body, final List<String> said) {
        final Loaded program = load(body);
        final Process straight = straight(program);
        final Process saved = throughTags(program);
        helper.assertTrue(straight.console().equals(said),
                "uninterrupted it says " + said + "; got " + straight.console());
        helper.assertTrue(saved.console().equals(said),
                "through a save it says " + said + "; got " + saved.console() + " " + saved.message());
        helper.assertTrue(saved.state() == straight.state(),
                "it ends the same way either side of a save; got " + saved.state());
        helper.assertTrue(saved.heap().used() == straight.heap().used(),
                "it holds the same bytes either side of a save; got " + saved.heap().used()
                        + " against " + straight.heap().used());
    }

    @GameTest(template = ARENA)
    public static void snapshotTag_carriesALoopThroughASaveToTheEnd(final GameTestHelper helper) {
        bothWays(helper, """
                        for (int i = 0; i < 3; i++) {
                            Console.PrintLine("round " + i);
                        }
                """, List.of("round 0", "round 1", "round 2"));
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void snapshotTag_keepsTwoNamesForOneThingAsOneThing(final GameTestHelper helper) {
        bothWays(helper, """
                        List<string> names = new List<string>();
                        List<string> also = names;
                        names.Add("first");
                        names.Add("second");
                        Console.PrintLine("also " + also.Count);
                """, List.of("also 2"));
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void snapshotTag_keepsWhatAMapAndALambdaWereHolding(final GameTestHelper helper) {
        bothWays(helper, """
                        Map<string, int> counts = new Map<string, int>();
                        counts.Put("iron", 7);
                        if (counts.TryGet("iron", out int found)) { Console.PrintLine("iron " + found); }
                """, List.of("iron 7"));
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void snapshotTag_bringsBackEveryKindItWroteDown(final GameTestHelper helper) {
        final Snapshot written = new Snapshot(4096,
                List.of(new Snapshot.Held.Text(0, 26, 3, false, "hello"),
                        new Snapshot.Held.Object(1, 24, 4, false, "Tally",
                                Map.of("count", new Snapshot.Value.I4(7))),
                        new Snapshot.Held.Array(2, 32, 5, true, "int",
                                List.of(new Snapshot.Value.I8(9), new Snapshot.Value.R4(1.5f))),
                        new Snapshot.Held.Listing(3, 24, 6, false,
                                List.of(new Snapshot.Value.Ref(0), new Snapshot.Value.Nothing())),
                        new Snapshot.Held.Keyed(4, 32, 7, false,
                                List.of(new Snapshot.Value.Ch('a')),
                                List.of(new Snapshot.Value.R8(2.25))),
                        new Snapshot.Held.Handler(5, 32, 8, false, "Note",
                                List.of(new Snapshot.BoundShot(new Snapshot.Value.Ref(1), "Monitor",
                                        "First", List.of("int"), "void")))),
                List.of(new Snapshot.FrameShot("Monitor", "OnTick", List.of(), 12,
                        new Snapshot.Value.Ref(1), List.of(new Snapshot.Value.Bool(true)),
                        List.of(new Snapshot.Value.I4(3)), false)),
                List.of(new Snapshot.FrameShot("Counter", "Counter", List.of(), 0,
                        new Snapshot.Value.Nothing(), List.of(), List.of(), true)),
                Map.of("Counter", Map.of("seen", new Snapshot.Value.I4(2))),
                new Snapshot.Value.Ref(1), List.of("first", "second"), "RUNNING", "", 91);
        final Snapshot read = SnapshotTag.read(SnapshotTag.write(written));
        helper.assertTrue(read.equals(written), "what came back out of the tag is what went in; got " + read);
        helper.succeed();
    }
}
