/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.tests.gametest;

import dev.jstech.core.util.BlockDrops;
import dev.jstech.industrial.IndustrialModule;
import dev.jstech.industrial.blockentity.MaceratorBlockEntity;
import dev.jstech.tests.JsTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Verifies that a content-holding block spills its stored items into the world instead of destroying them when broken, the conservation contract every machine, computer and rack now shares through {@link BlockDrops}.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class DropGameTests {

    private DropGameTests() {
    }

    private static final String ARENA = "empty";

    @GameTest(template = ARENA)
    public static void contentSpill_dropsStoredItemsAndClearsTheSlot(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, IndustrialModule.MACERATOR.get());
        if (!(helper.getBlockEntity(pos) instanceof MaceratorBlockEntity machine)) {
            helper.fail("no macerator");
            return;
        }
        machine.getInventory().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 5));

        BlockDrops.spill(helper.getLevel(), helper.absolutePos(pos), machine.getInventory());

        helper.assertTrue(machine.getInventory().getStackInSlot(0).isEmpty(),
                "the slot must be cleared once its contents have spilled");
        helper.assertItemEntityPresent(Items.COBBLESTONE, pos, 2.0);
        helper.succeed();
    }
}
