/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.JsComputers;
import dev.jstech.computers.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computers.hardware.DiskSize;
import dev.jstech.computers.hardware.StorageTier;
import dev.jstech.computers.os.FilesystemKind;
import dev.jstech.computers.os.fs.DiskFilesystem;
import dev.jstech.computers.os.fs.FileType;
import dev.jstech.computers.program.ServerCliComputer;
import dev.jstech.computers.program.cli.CliCommands;
import dev.jstech.computers.program.cli.CliLine;
import dev.jstech.computers.program.cli.CliShell;
import dev.jstech.tests.JsTests;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The compiler and the runtime at the prompt, the way a player types them.
 *
 * <p>The explorer names a new file "New File.can", with a space in it, and a player working in a
 * folder types the name without the folder. Both have to reach the compiler whole.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class CannonShellGameTests {

    private CannonShellGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 2;

    private static final String HELLO = """
            using System.IO.*;
            namespace Programs;
            class Hello {
                static void Main() {
                    Console.PrintLine("it runs");
                }
            }
            """;

    private static CraftingComputerBlockEntity computer(final GameTestHelper helper, final BlockPos at) {
        helper.setBlock(at, ComputingModule.CRAFTING_COMPUTER.get());
        if (!(helper.getBlockEntity(at) instanceof CraftingComputerBlockEntity computer)) {
            helper.fail("no computer at " + at);
            return null;
        }
        final ItemStackHandler hw = computer.getHardware();
        hw.setStackInSlot(CraftingComputerBlockEntity.MOTHERBOARD_SLOT, new ItemStack(ComputingModule.MOTHERBOARD_ATX_P.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.CPU_SLOT, new ItemStack(ComputingModule.CPU_ASCENT_965.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.RAM_SLOTS_START, new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.PSU_SLOT, new ItemStack(ComputingModule.PSU_650G.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        computer.installOs(ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, "frames_xp"));
        for (final String id : new String[] {"cannonc", "cannonrt"}) {
            computer.console().install(ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, id).toString());
        }
        return computer;
    }

    private static String text(final CliShell.Response response) {
        final StringBuilder out = new StringBuilder();
        for (final CliLine line : response.lines()) {
            out.append(line.text()).append('\n');
        }
        return out.toString();
    }

    /**
     * The terminal editors are commands once their program is installed, and typing one hands the
     * terminal over to it. They were listed by a name the machine never used for them, so they read
     * as not found on every machine that had them.
     */
    @GameTest(template = ARENA)
    public static void editors_areCommandsOnceInstalled(final GameTestHelper helper) {
        final CraftingComputerBlockEntity computer = computer(helper, new BlockPos(2, 2, 2));
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = new ServerCliComputer(computer, helper.getLevel());
                    final CliShell shell = CliCommands.shellFor(cli, 52);
                    final String before = text(shell.run("vim progs/a.can", cli));
                    helper.assertTrue(before.contains("not found"), "vim is no command before it is installed; got " + before);
                    computer.console().install(ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, "vim").toString());
                    computer.console().install(ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, "emacs").toString());
                    final CliShell.Response vim = shell.run("vim progs/a.can", cli);
                    helper.assertTrue(vim.handOver() != null && "vim".equals(vim.handOver().editor()),
                            "vim hands the terminal over once installed; got " + text(vim));
                    final CliShell.Response emacs = shell.run("emacs progs/a.can", cli);
                    helper.assertTrue(emacs.handOver() != null && "emacs".equals(emacs.handOver().editor()),
                            "emacs hands the terminal over once installed; got " + text(emacs));
                    /*
                     * A file named from inside its folder is handed over by its whole path: the terminal
                     * asks the machine for the file by that, knowing nothing of where the prompt stands.
                     * It used to hand the bare name over, and the editor opened an empty file of that name.
                     */
                    DiskFilesystem.write(computer.systemDisk(), "progs/a.can", FileType.CAN, HELLO,
                            Long.MAX_VALUE, FilesystemKind.HIERARCHICAL);
                    final String moved = text(shell.run("cd progs", cli));
                    helper.assertFalse(moved.contains("not found"), "the folder is there to enter; got " + moved);
                    final CliShell.Response inside = shell.run("vim a.can", cli);
                    helper.assertTrue(inside.handOver() != null && "progs/a.can".equals(inside.handOver().path()),
                            "a file named from its folder is handed over whole; got "
                                    + (inside.handOver() == null ? "nothing" : inside.handOver().path()));
                    final CliShell.Response dos = shell.run("vim ..\\progs\\a.can", cli);
                    helper.assertTrue(dos.handOver() != null && "progs/a.can".equals(dos.handOver().path()),
                            "a DOS path is handed over as the disk knows it; got "
                                    + (dos.handOver() == null ? "nothing" : dos.handOver().path()));
                })
                .thenSucceed();
    }

    /** dir lists the files of a folder beside its folders, from the root and from inside it. */
    @GameTest(template = ARENA)
    public static void dir_listsFilesAndFoldersAlike(final GameTestHelper helper) {
        final CraftingComputerBlockEntity computer = computer(helper, new BlockPos(2, 2, 2));
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    DiskFilesystem.write(computer.systemDisk(), "Projects/Farm/Farm/Program.can", FileType.CAN, HELLO,
                            Long.MAX_VALUE, FilesystemKind.HIERARCHICAL);
                    DiskFilesystem.write(computer.systemDisk(), "Projects/Farm/Farm.sln", FileType.SLN, "name: Farm",
                            Long.MAX_VALUE, FilesystemKind.HIERARCHICAL);
                    final ServerCliComputer cli = new ServerCliComputer(computer, helper.getLevel());
                    final CliShell shell = CliCommands.shellFor(cli, 52);
                    final String top = text(shell.run("dir Projects\\Farm", cli));
                    helper.assertTrue(top.contains("Farm") && top.contains("<DIR>") && top.contains("Farm.sln"),
                            "the solution folder lists its project folder and its file; got " + top);
                    shell.run("cd Projects\\Farm\\Farm", cli);
                    final String inside = text(shell.run("dir", cli));
                    helper.assertTrue(inside.contains("Program.can"), "the project folder lists its source; got " + inside);
                })
                .thenSucceed();
    }

    private static final String ASKS = """
            using System.IO.*;
            namespace Programs;
            class Asks {
                static void Main() {
                    Console.PrintLine("name?");
                    Console.PrintLine("hello " + Console.ReadLine());
                }
            }
            """;

    /**
     * A program in front of the terminal reads what is typed there: it stops on the read, the machine
     * lists it as waiting for input, and the line typed lets it finish.
     */
    @GameTest(template = ARENA)
    public static void cannon_aProgramReadsALineTypedAtItsTerminal(final GameTestHelper helper) {
        final CraftingComputerBlockEntity computer = computer(helper, new BlockPos(2, 2, 2));
        if (computer == null) {
            return;
        }
        // The machine itself ticks the program here, so it has to be on, not just built.
        computer.togglePower();
        /*
         * The process is kept from one step to the next: once it has returned with nobody watching the
         * terminal, the terminal lets it go, and what it printed is only on the process itself.
         */
        final dev.jstech.core.language.ILanguageProcess[] asked = new dev.jstech.core.language.ILanguageProcess[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    DiskFilesystem.write(computer.systemDisk(), "progs/asks.can", FileType.CAN, ASKS,
                            Long.MAX_VALUE, FilesystemKind.HIERARCHICAL);
                    final ServerCliComputer cli = new ServerCliComputer(computer, helper.getLevel());
                    final CliShell shell = CliCommands.shellFor(cli, 52);
                    final String compiled = text(shell.run("cannonc progs/asks.can", cli));
                    helper.assertTrue(compiled.contains("wrote"), "the program compiles; got " + compiled);
                    final String ran = text(shell.run("cannon run progs/asks.asm", cli));
                    helper.assertFalse(ran.contains("not found") || ran.contains("No such") || ran.contains("cannon:"),
                            "the program starts at the terminal; got " + ran);
                })
                .thenExecuteAfter(SETTLE * 4, () -> {
                    final int id = computer.cannon().held();
                    helper.assertTrue(id > 0, "the terminal is holding the program");
                    final dev.jstech.computers.cannon.machine.MachinePrograms.Live one = computer.cannon().byId(id);
                    helper.assertTrue(one != null && one.process().waitingForInput(),
                            "the program stops on its read; state " + (one == null ? "gone" : one.process().state()));
                    helper.assertTrue("input".equals(dev.jstech.computers.cannon.machine.MachinePrograms.stateOf(one.process())),
                            "the machine lists it as waiting for input");
                    helper.assertTrue(one.process().console().contains("name?"), "what it printed before the read is there");
                    asked[0] = one.process();
                    helper.assertTrue(computer.cannon().offerInput("Ada"), "a typed line goes to the program in front");
                })
                .thenExecuteAfter(SETTLE * 4, () -> {
                    helper.assertTrue(asked[0].console().contains("hello Ada"),
                            "the line typed is what the program read; console " + asked[0].console());
                    helper.assertFalse(asked[0].waitingForInput(), "it is no longer waiting");
                    helper.assertTrue(asked[0].state() == dev.jstech.core.language.ILanguageProcess.State.FINISHED,
                            "and it returned; state " + asked[0].state());
                })
                .thenExecuteAfter(SETTLE * 2, () -> {
                    // A program that returned is not something the machine is running any more.
                    helper.assertTrue(computer.cannon().all().isEmpty(),
                            "a program that returned leaves the machine's list; still there: "
                                    + computer.cannon().all().stream().map(one -> one.name() + "/"
                                    + dev.jstech.computers.cannon.machine.MachinePrograms.stateOf(one.process())).toList());
                })
                .thenSucceed();
    }

    /**
     * A terminal program stopped on a read is stopped for good when it loses the terminal: another
     * program taking the terminal ends it there and then, and one found waiting with no terminal in
     * front of it is cleared by the machine's next tick. Either way it does not stay on the machine's
     * list for ever at no cost, as something running.
     */
    @GameTest(template = ARENA)
    public static void cannon_aProgramWaitingForALineWithNoTerminalIsStopped(final GameTestHelper helper) {
        final CraftingComputerBlockEntity computer = computer(helper, new BlockPos(2, 2, 2));
        if (computer == null) {
            return;
        }
        computer.togglePower();
        final int[] first = new int[1];
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    DiskFilesystem.write(computer.systemDisk(), "progs/asks.can", FileType.CAN, ASKS,
                            Long.MAX_VALUE, FilesystemKind.HIERARCHICAL);
                    final ServerCliComputer cli = new ServerCliComputer(computer, helper.getLevel());
                    final CliShell shell = CliCommands.shellFor(cli, 52);
                    shell.run("cannonc progs/asks.can", cli);
                    shell.run("cannon run progs/asks.asm", cli);
                    first[0] = computer.cannon().held();
                    helper.assertTrue(first[0] > 0, "the first run holds the terminal");
                })
                .thenExecuteAfter(SETTLE * 4, () -> {
                    helper.assertTrue(computer.cannon().byId(first[0]).process().waitingForInput(),
                            "the first run is stopped on its read");
                    // A second run takes the terminal: the first can never be answered now, so it goes.
                    final ServerCliComputer cli = new ServerCliComputer(computer, helper.getLevel());
                    final CliShell shell = CliCommands.shellFor(cli, 52);
                    shell.run("cannon run progs/asks.asm", cli);
                    helper.assertTrue(computer.cannon().held() != first[0] && computer.cannon().held() > 0,
                            "the second run holds the terminal");
                    helper.assertTrue(computer.cannon().byId(first[0]) == null,
                            "the first run is gone the moment the terminal moves on");
                    helper.assertTrue(computer.cannon().all().size() == 1, "one program is left running");
                })
                .thenExecuteAfter(SETTLE * 4, () -> {
                    // The terminal lets the second go while it is still waiting: the tick clears it.
                    computer.cannon().release();
                    helper.assertTrue(computer.cannon().all().size() == 1,
                            "letting go leaves a waiting program in place for the tick to judge");
                })
                .thenExecuteAfter(SETTLE * 4, () -> {
                    helper.assertTrue(computer.cannon().all().isEmpty(),
                            "a program waiting on a keyboard nobody can reach is cleared; still there: "
                                    + computer.cannon().all().stream().map(one -> one.name()).toList());
                })
                .thenSucceed();
    }

    /** A program that stays up and says what it is called. */
    private static final String NAMED = """
            using System.*;
            using System.IO.*;
            using System.Execution.*;
            namespace Programs;
            class Named : IScript {
                public void OnInit() { Program.SetName("Sorter"); Console.PrintLine("I am " + Program.Name); }
                public void OnTick() { }
                public void OnDestroy() { }
            }
            """;

    /**
     * The machine lists a program by the name it gave itself, and one that gave none by the runtime
     * that runs it, never by the path it was started from.
     */
    @GameTest(template = ARENA)
    public static void cannon_listsAProgramByItsOwnNameOrByTheRuntime(final GameTestHelper helper) {
        final CraftingComputerBlockEntity computer = computer(helper, new BlockPos(2, 2, 2));
        if (computer == null) {
            return;
        }
        computer.togglePower();
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    DiskFilesystem.write(computer.systemDisk(), "progs/named.can", FileType.CAN, NAMED,
                            Long.MAX_VALUE, FilesystemKind.HIERARCHICAL);
                    DiskFilesystem.write(computer.systemDisk(), "progs/asks.can", FileType.CAN, ASKS,
                            Long.MAX_VALUE, FilesystemKind.HIERARCHICAL);
                    final ServerCliComputer cli = new ServerCliComputer(computer, helper.getLevel());
                    final CliShell shell = CliCommands.shellFor(cli, 52);
                    helper.assertTrue(text(shell.run("cannonc progs/named.can", cli)).contains("wrote"), "named compiles");
                    helper.assertTrue(text(shell.run("cannonc progs/asks.can", cli)).contains("wrote"), "asks compiles");
                    shell.run("cannon run progs/named.asm", cli);
                    shell.run("cannon run progs/asks.asm", cli);
                })
                .thenExecuteAfter(SETTLE * 4, () -> {
                    final ServerCliComputer cli = new ServerCliComputer(computer, helper.getLevel());
                    final CliShell shell = CliCommands.shellFor(cli, 52);
                    final String listed = text(shell.run("cannon ps", cli));
                    helper.assertTrue(listed.contains("Sorter"), "the script is listed by the name it gave itself; got " + listed);
                    helper.assertTrue(listed.contains("cannonrt"), "the nameless one is listed by the runtime; got " + listed);
                    helper.assertFalse(listed.contains("asks.asm") || listed.contains("progs"),
                            "no path stands in for a name; got " + listed);
                    for (final dev.jstech.computers.cannon.machine.MachinePrograms.Live one : computer.cannon().all()) {
                        helper.assertTrue("Sorter".equals(one.name()) || "cannonrt".equals(one.name()),
                                "the machine's own list agrees; got " + one.name());
                    }
                })
                .thenSucceed();
    }

    /** A source whose name has a space, compiled by its quoted name, from the root and from its folder. */
    @GameTest(template = ARENA)
    public static void cannonc_compilesANameWithASpaceFromTheRootAndFromItsFolder(final GameTestHelper helper) {
        final CraftingComputerBlockEntity computer = computer(helper, new BlockPos(2, 2, 2));
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    DiskFilesystem.write(computer.systemDisk(), "progs/New File.can", FileType.CAN, HELLO,
                            Long.MAX_VALUE, FilesystemKind.HIERARCHICAL);
                    final ServerCliComputer cli = new ServerCliComputer(computer, helper.getLevel());
                    final CliShell shell = CliCommands.shellFor(cli, 52);
                    final String fromRoot = text(shell.run("cannonc \"progs/New File.can\"", cli));
                    helper.assertTrue(fromRoot.contains("wrote"), "the quoted name reaches the compiler whole; got "
                            + fromRoot);
                    helper.assertTrue(DiskFilesystem.exists(computer.systemDisk(), "progs/New File.asm"),
                            "the listing lands beside the source, under the same name");
                    shell.run("cd progs", cli);
                    final String fromFolder = text(shell.run("cannonc \"New File.can\"", cli));
                    helper.assertTrue(fromFolder.contains("wrote"),
                            "a name without its folder resolves against the prompt's folder; got " + fromFolder);
                    // Without quotes the shell sees two names, and says so of the first rather than of nothing.
                    final String unquoted = text(shell.run("cannonc New File.can", cli));
                    helper.assertTrue(unquoted.contains("cannonc:") && !unquoted.contains("wrote"),
                            "two words are two files, and the first is not there; got " + unquoted);
                    final String ran = text(shell.run("cannon run \"New File.asm\"", cli));
                    helper.assertFalse(ran.contains("not found") || ran.contains("no such"),
                            "the runtime takes the quoted listing by its name; got " + ran);
                })
                .thenSucceed();
    }
}
