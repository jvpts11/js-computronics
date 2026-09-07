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
import dev.jstech.computronics.cannon.CannonCosts;
import dev.jstech.computronics.cannon.machine.MachineHost;
import dev.jstech.computronics.cannon.run.IHost;
import dev.jstech.computronics.cannon.run.Values;
import dev.jstech.computronics.hardware.DiskSize;
import dev.jstech.computronics.hardware.StorageTier;
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
 * What the machine charges a program, against what the editors tell the player it will charge.
 *
 * <p>The cost of every call is written down in one place so an editor can show it before the line is
 * even run. That table is only worth anything if it is the truth, and the truth is what a real computer
 * actually takes off a program's budget. So this asks a real machine, one call at a time, and compares.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class CannonCostGameTests {

    private CannonCostGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 2;

    /** A computer good enough to answer, with a disk in it and a system on it. */
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

    /**
     * Asks the machine for one member and checks it charged what the table says.
     *
     * <p>A call that brings back rows is charged by how many, so the reply itself says how many there
     * were and the expected price is worked out from that rather than guessed.
     */
    private static void charges(final GameTestHelper helper, final MachineHost host,
                                final String owner, final String member, final List<Object> arguments) {
        final IHost.Reply reply;
        try {
            reply = host.call(owner, member, arguments, "costs.asm", 1);
        } catch (final RuntimeException refused) {
            helper.fail(owner + "." + member + " could not be asked at all: " + refused.getMessage());
            return;
        }
        final int rows = reply.value() instanceof Values.ListValue list ? list.size() : 0;
        final int expected = CannonCosts.of(owner, member).at(rows);
        helper.assertTrue(reply.cost() == expected,
                owner + "." + member + " charged " + reply.cost() + ", the table says " + expected
                        + " (" + rows + " rows)");
    }

    /**
     * Every call a program can make that only needs the machine itself, priced as the table promises.
     *
     * <p>The ones left out are the ones that would change the world to ask them: writing to the drive
     * and asking the network for work. Their prices are fixed rather than counted, and a reader can see
     * the constant in the host beside the one in the table.
     */
    @GameTest(template = ARENA)
    public static void costs_areWhatTheEditorsPromise(final GameTestHelper helper) {
        final BlockPos at = new BlockPos(2, 2, 2);
        final CraftingComputerBlockEntity computer = computer(helper, at);
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MachineHost host = new MachineHost(computer);

                    // What the computer is, and what it holds.
                    for (final String member : List.of("Name", "Cpu", "Os", "RamMb", "FreeRamMb", "Online")) {
                        charges(helper, host, "Computer", member, List.of());
                    }
                    for (final String member : List.of("Disks", "Programs", "Processes")) {
                        charges(helper, host, "Computer", member, List.of());
                    }

                    // The drive, asked about but not written to.
                    charges(helper, host, "File", "Exists", List.of("nothing.txt"));
                    charges(helper, host, "File", "List", List.of(""));

                    /*
                     * The network, with none attached: whether there is one is still a question the
                     * machine answers, and it charges for answering it.
                     */
                    charges(helper, host, "Network", "Online", List.of());
                    charges(helper, host, "Network", "Current", List.of());
                    charges(helper, host, "Mainframe", "Online", List.of());
                })
                .thenSucceed();
    }

    /** Being told about something costs nothing, which is the whole reason to prefer it to asking. */
    @GameTest(template = ARENA)
    public static void watching_costsNothingToArm(final GameTestHelper helper) {
        final BlockPos at = new BlockPos(2, 2, 2);
        final CraftingComputerBlockEntity computer = computer(helper, at);
        if (computer == null) {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    for (final String member : List.of("Watch", "WatchBelow", "WatchAbove")) {
                        helper.assertTrue(CannonCosts.of("Network", member).at(0) == 0,
                                member + " should cost nothing to arm");
                        /*
                         * The machine is never asked: a watch is answered inside the runtime, which is
                         * what makes it free. If that ever changes, the host starts answering for it and
                         * this stops being true.
                         */
                        helper.assertTrue(!new MachineHost(computer).provides("Watch"),
                                "a watch is the runtime's to answer, not the machine's");
                    }
                })
                .thenSucceed();
    }
}
