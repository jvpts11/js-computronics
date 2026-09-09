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
import dev.jstech.computers.program.ServerCliComputer;
import dev.jstech.computers.program.cli.CliCommands;
import dev.jstech.computers.program.cli.CliLine;
import dev.jstech.computers.program.cli.CliShell;
import dev.jstech.tests.JsTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The system's own files and the installed programs' folders, as the prompt sees them.
 *
 * <p>A system folder with nothing in it and a Program Files with no programs are not a computer
 * anybody has used. Both are generated from what the machine runs and has installed, so they are
 * there to read, appear when a program is set up, and cannot be deleted.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class ProgramFilesGameTests {

    private ProgramFilesGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 2;

    private static ResourceLocation jsc(final String path) {
        return ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, path);
    }

    private static CraftingComputerBlockEntity computer(final GameTestHelper helper, final BlockPos at,
                                                        final String os) {
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
        if (!computer.installOs(jsc(os))) {
            helper.fail("could not install " + os);
            return null;
        }
        return computer;
    }

    private static String run(final CliShell shell, final ServerCliComputer cli, final String line) {
        final StringBuilder out = new StringBuilder();
        for (final CliLine each : shell.run(line, cli).lines()) {
            out.append(each.text()).append('\n');
        }
        return out.toString();
    }

    /** Frames: the system under its own folder, a program under Program Files once it is installed. */
    @GameTest(template = ARENA)
    public static void frames_listsTheSystemFolderAndEachInstalledProgram(final GameTestHelper helper) {
        final CraftingComputerBlockEntity computer = computer(helper, new BlockPos(2, 2, 2), "frames_xp");
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = new ServerCliComputer(computer, helper.getLevel());
                    final CliShell shell = CliCommands.shellFor(cli, 52);
                    final String root = run(shell, cli, "dir");
                    helper.assertTrue(root.contains("Frames") && !root.contains("Windows"),
                            "the system folder is the system's own; got " + root);
                    final String system = run(shell, cli, "dir Frames\\System");
                    helper.assertTrue(system.contains("kernel.sys") && system.contains("frames.ini"),
                            "the system folder has a system in it; got " + system);
                    helper.assertTrue(system.contains("editor.exe") || system.contains("files.exe"),
                            "what every edition ships with lives with the system; got " + system);
                    final String ini = run(shell, cli, "type Frames\\System\\frames.ini");
                    helper.assertTrue(ini.contains("edition=Frames XP"), "the ini names the edition; got " + ini);
                    final String before = run(shell, cli, "dir \"Program Files\"");
                    helper.assertFalse(before.contains("Minesweeper"), "nothing installed, nothing listed; got " + before);

                    /*
                     * Minesweeper is a program of the generation before Frames XP's, so it goes where a
                     * system of the day put programs made for the one before it; the studio, written for
                     * XP, goes in Program Files proper.
                     */
                    computer.console().install(jsc("minesweeper").toString());
                    computer.console().install(jsc("virtual_studio").toString());
                    final String after = run(shell, cli, "dir \"Program Files\"");
                    helper.assertTrue(after.contains("Virtual Studio") && !after.contains("Minesweeper"),
                            "an installed program of the system's own generation has a folder there; got " + after);
                    final String older = run(shell, cli, "dir \"Program Files (x86)\"");
                    helper.assertTrue(older.contains("Minesweeper"),
                            "one of an older generation goes under (x86); got " + older);
                    final String inside = run(shell, cli, "dir \"Program Files (x86)\\Minesweeper\"");
                    helper.assertTrue(inside.contains("mines.exe") && inside.contains("readme.txt")
                            && inside.contains("uninstall.exe"), "with its program in it; got " + inside);
                    final String readme = run(shell, cli, "type \"Program Files (x86)\\Minesweeper\\readme.txt\"");
                    helper.assertTrue(readme.contains("Minesweeper 5.1"),
                            "the readme names the program and its version; got " + readme);
                    final String moved = run(shell, cli, "cd \"Program Files (x86)\\Minesweeper\"");
                    helper.assertFalse(moved.contains("not found") || moved.contains("No such"),
                            "the prompt can enter it; got " + moved);
                    final String deleted = run(shell, cli, "del readme.txt");
                    helper.assertFalse(deleted.contains("deleted") || deleted.contains("Deleted"),
                            "a generated file cannot be deleted; got " + deleted);
                })
                .thenSucceed();
    }

    /** Linux: an installed package under /usr/bin and /usr/share, and the distribution in /etc/os-release. */
    @GameTest(template = ARENA)
    public static void linux_listsInstalledPackagesUnderUsr(final GameTestHelper helper) {
        final CraftingComputerBlockEntity computer = computer(helper, new BlockPos(2, 2, 2), "ubuntu");
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = new ServerCliComputer(computer, helper.getLevel());
                    final CliShell shell = CliCommands.shellFor(cli, 52);
                    final String release = run(shell, cli, "cat /etc/os-release");
                    helper.assertTrue(release.contains("NAME=\"Ubuntu\""), "the distribution names itself; got " + release);
                    computer.console().install(jsc("screenfetch").toString());
                    final String bin = run(shell, cli, "ls /usr/bin");
                    helper.assertTrue(bin.contains("screenfetch"), "an installed package has its binary; got " + bin);
                    final String share = run(shell, cli, "cat /usr/share/screenfetch/readme");
                    helper.assertTrue(share.contains("screenfetch 3.9.1"), "and its readme with its version; got " + share);
                })
                .thenSucceed();
    }
}
