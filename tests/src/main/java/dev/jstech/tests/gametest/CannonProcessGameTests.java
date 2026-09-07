/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.JsComputronics;
import dev.jstech.computronics.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computronics.cannon.CannonCompiler;
import dev.jstech.computronics.cannon.SourceFile;
import dev.jstech.computronics.cannon.machine.MachinePrograms;
import dev.jstech.computronics.cannon.run.Library;
import dev.jstech.computronics.hardware.DiskSize;
import dev.jstech.computronics.hardware.StorageTier;
import dev.jstech.core.language.LanguageProcess;
import dev.jstech.tests.JsTests;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * A program on a real machine is called once when it starts and once every tick after that if it stays
 * up, and it only ever gets the instructions the machine's processor is worth. These check that: what a
 * program says over several ticks, how the tick is shared when more than one is running, and that both
 * come back unchanged from a save.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class CannonProcessGameTests {

    private CannonProcessGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 2;

    /** A script that says which tick it is on, so its console counts the ticks it was given. */
    private static final String COUNTER = """
            class Counter : IScript {
                int seen;

                public void OnInit() { seen = 0; Console.PrintLine("up"); }
                public void OnTick() { seen = seen + 1; Console.PrintLine("tick " + seen); }
                public void OnDestroy() { Console.PrintLine("down"); }
            }
            """;

    /** A program that runs at a terminal: it starts at Main, prints, and is done. */
    private static final String HELLO = """
            class Hello {
                static void Main() {
                    for (int i = 0; i < 3; i++) { Console.PrintLine("hi " + i); }
                }
            }
            """;

    private static String listing(final String source) {
        final CannonCompiler.Result built =
                CannonCompiler.compile(List.of(new SourceFile("Script.can", source)));
        if (!built.ok()) {
            throw new IllegalStateException(String.join("\n", built.lines()));
        }
        return built.assembly();
    }

    /** A computer with enough hardware to run and a system on it. */
    private static CraftingComputerBlockEntity computer(final GameTestHelper helper, final BlockPos at) {
        helper.setBlock(at, ComputingModule.CRAFTING_COMPUTER.get());
        if (!(helper.getBlockEntity(at) instanceof CraftingComputerBlockEntity computer)) {
            helper.fail("no computer at " + at);
            return null;
        }
        final ItemStackHandler hw = computer.getHardware();
        hw.setStackInSlot(CraftingComputerBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(ComputingModule.MOTHERBOARD_ATX_P.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.CPU_SLOT,
                new ItemStack(ComputingModule.CPU_ASCENT_965.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.RAM_SLOTS_START,
                new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.PSU_SLOT,
                new ItemStack(ComputingModule.PSU_650G.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        computer.installOs(ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "frames_xp"));
        return computer;
    }

    private static LanguageProcess only(final MachinePrograms programs) {
        return programs.all().getFirst().process();
    }

    @GameTest(template = ARENA)
    public static void programs_callAScriptOnceWhenItStartsAndOnceATickAfter(final GameTestHelper helper) {
        final CraftingComputerBlockEntity computer = computer(helper, new BlockPos(2, 2, 2));
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MachinePrograms programs = computer.cannon();
                    final MachinePrograms.Started started =
                            programs.start("counter.asm", listing(COUNTER), 1, computer);
                    helper.assertTrue(started.ok(), "it starts: " + started.message());
                    for (int i = 0; i < 4; i++) {
                        programs.tick(512);
                    }
                    final List<String> said = only(programs).console();
                    helper.assertTrue(said.equals(List.of("up", "tick 1", "tick 2", "tick 3")),
                            "four ticks say what they did; got " + said);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void programs_shareOneTickBetweenEveryProgramRunning(final GameTestHelper helper) {
        final CraftingComputerBlockEntity computer = computer(helper, new BlockPos(2, 2, 2));
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MachinePrograms programs = computer.cannon();
                    final String listing = listing(COUNTER);
                    programs.start("one.asm", listing, 1, computer);
                    programs.start("two.asm", listing, 1, computer);
                    programs.tick(9);
                    final int first = programs.all().getFirst().process().spent();
                    final int second = programs.all().get(1).process().spent();
                    helper.assertTrue(first + second == 9,
                            "the whole tick is spent; got " + first + " and " + second);
                    helper.assertTrue(Math.abs(first - second) <= 1,
                            "and evenly between them; got " + first + " and " + second);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void programs_carryEveryProgramThroughASaveAndOn(final GameTestHelper helper) {
        final CraftingComputerBlockEntity computer = computer(helper, new BlockPos(2, 2, 2));
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MachinePrograms before = computer.cannon();
                    before.start("counter.asm", listing(COUNTER), 2, computer);
                    before.tick(512);
                    before.tick(512);
                    final CompoundTag tag = new CompoundTag();
                    before.save(tag);

                    final MachinePrograms after = new MachinePrograms();
                    after.load(tag, computer);
                    helper.assertTrue(after.all().size() == 1,
                            "one program comes back; got " + after.all().size());
                    helper.assertTrue(after.all().getFirst().heapMb() == 2,
                            "with the memory it was given; got " + after.all().getFirst().heapMb());
                    after.tick(512);
                    final List<String> said = only(after).console();
                    helper.assertTrue(said.equals(List.of("up", "tick 1", "tick 2")),
                            "and carries on counting; got " + said);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void programs_letAProgramSayGoodbyeWhenItIsStopped(final GameTestHelper helper) {
        final CraftingComputerBlockEntity computer = computer(helper, new BlockPos(2, 2, 2));
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MachinePrograms programs = computer.cannon();
                    final int id = programs.start("counter.asm", listing(COUNTER), 1, computer).id();
                    programs.tick(512);
                    final LanguageProcess running = only(programs);
                    helper.assertTrue(programs.stop(id), "it stops");
                    helper.assertTrue(programs.isEmpty(), "and is gone from the list");
                    helper.assertTrue(running.console().contains("down"),
                            "having said goodbye; got " + running.console());
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void programs_runATerminalProgramOnceAndAreDoneWithIt(final GameTestHelper helper) {
        final CraftingComputerBlockEntity computer = computer(helper, new BlockPos(2, 2, 2));
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MachinePrograms programs = computer.cannon();
                    final MachinePrograms.Started started =
                            programs.start("hello.asm", listing(HELLO), 1, computer);
                    helper.assertTrue(started.ok(), "it starts: " + started.message());
                    final LanguageProcess running = only(programs);
                    programs.tick(512);
                    helper.assertTrue(running.console().equals(List.of("hi 0", "hi 1", "hi 2")),
                            "it says its piece; got " + running.console());
                    programs.tick(512);
                    helper.assertTrue(programs.isEmpty(),
                            "and is gone, not asked again; " + programs.all().size() + " left");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void programs_keepAFinishedTerminalProgramWhileTheTerminalHasIt(final GameTestHelper helper) {
        final CraftingComputerBlockEntity computer = computer(helper, new BlockPos(2, 2, 2));
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MachinePrograms programs = computer.cannon();
                    final int id = programs.start("hello.asm", listing(HELLO), 1, computer).id();
                    programs.hold(id);
                    programs.tick(512);
                    programs.tick(512);
                    helper.assertTrue(programs.all().size() == 1, "it waits to be read");
                    helper.assertTrue(only(programs).state() == LanguageProcess.State.FINISHED,
                            "having finished");
                    programs.release();
                    helper.assertTrue(programs.isEmpty(), "and goes once the terminal lets it");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void programs_handTheTerminalEachLineOnceAndOnlyOnce(final GameTestHelper helper) {
        final CraftingComputerBlockEntity computer = computer(helper, new BlockPos(2, 2, 2));
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MachinePrograms programs = computer.cannon();
                    final int id = programs.start("hello.asm", listing(HELLO), 1, computer).id();
                    programs.hold(id);
                    helper.assertTrue(programs.unseen().isEmpty(), "nothing has been printed yet");
                    programs.tick(512);
                    final List<String> first = programs.unseen();
                    helper.assertTrue(first.equals(List.of("hi 0", "hi 1", "hi 2")),
                            "it hands over what was printed; got " + first);
                    helper.assertTrue(programs.unseen().isEmpty(),
                            "and does not hand the same lines twice");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void programs_giveTheTerminalTheLastOfALoudProgramWhenItScrolled(final GameTestHelper helper) {
        final CraftingComputerBlockEntity computer = computer(helper, new BlockPos(2, 2, 2));
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MachinePrograms programs = computer.cannon();
                    final int id = programs.start("loud.asm", listing("""
                            class Loud {
                                static void Main() {
                                    for (int i = 0; i < 260; i++) { Console.PrintLine("line " + i); }
                                }
                            }
                            """), 1, computer).id();
                    programs.hold(id);
                    programs.tick(100000);
                    final List<String> seen = programs.unseen();
                    // What fell off the end while nobody looked is gone, as it is on any terminal; what
                    // is left is the newest, in order, ending with the last thing the program said.
                    helper.assertTrue(seen.size() == Library.CONSOLE_LINES,
                            "it hands over everything still kept; got " + seen.size());
                    helper.assertTrue("line 259".equals(seen.getLast()),
                            "ending with the last; got " + seen.getLast());
                    helper.assertTrue("line 60".equals(seen.getFirst()),
                            "starting where it scrolled; got " + seen.getFirst());
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void programs_refuseAListingTheyCannotRead(final GameTestHelper helper) {
        final CraftingComputerBlockEntity computer = computer(helper, new BlockPos(2, 2, 2));
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MachinePrograms programs = computer.cannon();
                    helper.assertFalse(
                            programs.start("broken.asm", "this is not an assembly", 1, computer).ok(),
                            "it does not start");
                    helper.assertTrue(programs.isEmpty(), "and nothing is left running");
                    // And a file no registered language claims is refused by name, not by guessing.
                    helper.assertFalse(programs.start("thing.zz", "whatever", 1, computer).ok(),
                            "nothing runs a .zz");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void programs_budgetFollowsTheClockBetweenItsBounds(final GameTestHelper helper) {
        helper.assertTrue(MachinePrograms.budgetFor(0) == 0, "no processor is worth nothing");
        helper.assertTrue(MachinePrograms.budgetFor(100) == MachinePrograms.LEAST_PER_TICK,
                "the slowest machine still moves; got " + MachinePrograms.budgetFor(100));
        helper.assertTrue(MachinePrograms.budgetFor(700) == 87,
                "an early one follows its clock; got " + MachinePrograms.budgetFor(700));
        helper.assertTrue(MachinePrograms.budgetFor(4000) == 500,
                "a middling one too; got " + MachinePrograms.budgetFor(4000));
        helper.assertTrue(MachinePrograms.budgetFor(19_200) == MachinePrograms.MOST_PER_TICK,
                "and the fastest is capped; got " + MachinePrograms.budgetFor(19_200));
        helper.succeed();
    }
}
