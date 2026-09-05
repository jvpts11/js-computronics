/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jstech.tests.JsTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Battery 1, front I: the new world blocks drop their own item when broken (Definition-of-Done loot), so a player
 * never loses a Crafting Switch or crafting cable by mining it.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class MachineCraftWorldGameTests {

    private MachineCraftWorldGameTests() {
    }

    private static final String ARENA = "empty";

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void craftingSwitch_dropsItsItemWhenBroken(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.CRAFTING_SWITCH.get());
        helper.startSequence()
                .thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(pos), true))
                .thenExecuteAfter(2, () -> helper.assertItemEntityPresent(
                        ComputingModule.CRAFTING_SWITCH_ITEM.get(), pos, 2.0))
                .thenSucceed();
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void craftingCable_dropsItsItemWhenBroken(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.CRAFTING_CABLE.get());
        helper.startSequence()
                .thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(pos), true))
                .thenExecuteAfter(2, () -> helper.assertItemEntityPresent(
                        ComputingModule.CRAFTING_CABLE_ITEM.get(), pos, 2.0))
                .thenSucceed();
    }
}
