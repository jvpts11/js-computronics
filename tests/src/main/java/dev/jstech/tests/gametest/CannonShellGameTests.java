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
