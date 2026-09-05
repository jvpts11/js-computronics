/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingSwitchBlockEntity;
import dev.jsc.jscomputronics.module.industrial.IndustrialModule;
import dev.jstech.tests.JsTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * GameTests for the Crafting Switch: it must survey its faces (detect an adjacent machine's item handler) and
 * discover its Crafting Computer by a BFS through the crafting cable, just like the supercomputer cluster.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class CraftingSwitchGameTests {

    private CraftingSwitchGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    @GameTest(template = ARENA)
    public static void craftingSwitch_detectsMachineAndComputer(final GameTestHelper helper) {
        // computer — crafting cable — switch, with a Macerator on another switch face.
        final BlockPos computer = new BlockPos(2, 2, 2);
        final BlockPos cable = new BlockPos(3, 2, 2);
        final BlockPos sw = new BlockPos(4, 2, 2);
        final BlockPos machine = new BlockPos(4, 2, 3);

        helper.setBlock(computer, ComputingModule.CRAFTING_COMPUTER.get());
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machine, IndustrialModule.MACERATOR.get());

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    if (!(helper.getBlockEntity(sw) instanceof CraftingSwitchBlockEntity be)) {
                        helper.fail("no Crafting Switch block entity");
                        return;
                    }
                    helper.assertTrue(be.cableFace() != null, "the switch detects its crafting-cable face");
                    int machineFaces = 0;
                    for (final Direction d : Direction.values()) {
                        if (be.machineOnFace(d)) {
                            machineFaces++;
                        }
                    }
                    helper.assertTrue(machineFaces >= 1,
                            "the adjacent Macerator is detected on a face; machine faces=" + machineFaces);
                    helper.assertTrue(be.linkedComputer() != null,
                            "the switch finds its Crafting Computer over the crafting cable");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void craftingSwitch_updateTagCarriesSurveyToTheGui(final GameTestHelper helper) {
        // The survey results (machine faces, linked computer) are transient server data: the GUI only sees
        // them through the block-entity update tag. A switch with a machine and a linked computer must
        // publish both, or the screen renders UNLINKED with no machine — a bug this mod shipped once.
        final BlockPos computer = new BlockPos(2, 2, 2);
        final BlockPos cable = new BlockPos(3, 2, 2);
        final BlockPos sw = new BlockPos(4, 2, 2);
        final BlockPos machine = new BlockPos(4, 2, 3);

        helper.setBlock(computer, ComputingModule.CRAFTING_COMPUTER.get());
        helper.setBlock(cable, ComputingModule.CRAFTING_CABLE.get());
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machine, IndustrialModule.MACERATOR.get());

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    if (!(helper.getBlockEntity(sw) instanceof CraftingSwitchBlockEntity be)) {
                        helper.fail("no Crafting Switch block entity");
                        return;
                    }
                    final var tag = be.getUpdateTag(helper.getLevel().registryAccess());
                    helper.assertTrue(tag.getInt("MachineMask") != 0,
                            "the update tag must carry the surveyed machine faces for the GUI");
                    helper.assertTrue(tag.getBoolean("Linked"),
                            "the update tag must carry the linked-computer flag for the GUI");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void craftingSwitch_noComputerWithoutCable(final GameTestHelper helper) {
        // A switch with no crafting cable must not bind to any computer.
        final BlockPos sw = new BlockPos(3, 2, 2);
        final BlockPos machine = new BlockPos(3, 2, 3);
        helper.setBlock(sw, ComputingModule.CRAFTING_SWITCH.get());
        helper.setBlock(machine, IndustrialModule.MACERATOR.get());

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 2, () -> {
                    if (!(helper.getBlockEntity(sw) instanceof CraftingSwitchBlockEntity be)) {
                        helper.fail("no Crafting Switch block entity");
                        return;
                    }
                    helper.assertTrue(be.cableFace() == null, "no cable face without a crafting cable");
                    helper.assertTrue(be.linkedComputer() == null, "no computer without a crafting cable");
                })
                .thenSucceed();
    }
}
