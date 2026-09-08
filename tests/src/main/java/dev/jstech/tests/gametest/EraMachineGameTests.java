/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.HardwareItems;
import dev.jstech.computers.block.MainframeBlock;
import dev.jstech.computers.block.MainframePartBlock;
import dev.jstech.computers.block.MainframeStructure;
import dev.jstech.computers.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computers.blockentity.MainframeBlockEntity;
import dev.jstech.core.tier.HardwareEra;
import dev.jstech.tests.JsTests;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Integration coverage for the era-variant Crafting Computers and Mainframes: the assembly menu stays
 * open (stillValid validates the block family, not the Standard block alone, the bug that made an
 * era GUI flash for one tick and close), the era board gate (a board of the wrong era never powers the
 * machine on), the Mainframe multiblock stamping its era onto every structural part, and the
 * era-correct block dropping.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class EraMachineGameTests {

    private EraMachineGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    // Crafting Computer: the menu must stay open for every era

    @GameTest(template = ARENA)
    public static void craftingComputer_menuStaysOpen(final GameTestHelper helper) {
        assertCraftingMenuOpens(helper, ComputingModule.CRAFTING_COMPUTER.get());
    }

    @GameTest(template = ARENA)
    public static void vintageCrafting_menuStaysOpen(final GameTestHelper helper) {
        assertCraftingMenuOpens(helper, ComputingModule.VINTAGE_CRAFTING_COMPUTER.get());
    }

    @GameTest(template = ARENA)
    public static void legacyCrafting_menuStaysOpen(final GameTestHelper helper) {
        assertCraftingMenuOpens(helper, ComputingModule.LEGACY_CRAFTING_COMPUTER.get());
    }

    private static void assertCraftingMenuOpens(final GameTestHelper helper, final Block block) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, block);
        if (!(helper.getBlockEntity(pos) instanceof CraftingComputerBlockEntity be)) {
            helper.fail("no CraftingComputerBlockEntity at " + pos + " for " + block);
            return;
        }
        final Player player = helper.makeMockPlayer(GameType.CREATIVE);
        final BlockPos absolute = helper.absolutePos(pos);
        player.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        final var menu = new dev.jstech.computers.menu.CraftingComputerMenu(
                1, player.getInventory(), be);
        helper.assertFalse(menu.slots.isEmpty(), "the Crafting Computer menu must build its slots for " + block);
        helper.assertTrue(menu.stillValid(player),
                "the Crafting Computer menu must stay valid for " + block
                        + "; otherwise the server closes the GUI one tick after it opens");
        helper.succeed();
    }

    // Mainframe: the menu must stay open for every era

    @GameTest(template = ARENA)
    public static void mainframe_menuStaysOpen(final GameTestHelper helper) {
        assertMainframeMenuOpens(helper, ComputingModule.MAINFRAME.get());
    }

    @GameTest(template = ARENA)
    public static void vintageMainframe_menuStaysOpen(final GameTestHelper helper) {
        assertMainframeMenuOpens(helper, ComputingModule.VINTAGE_MAINFRAME.get());
    }

    @GameTest(template = ARENA)
    public static void legacyMainframe_menuStaysOpen(final GameTestHelper helper) {
        assertMainframeMenuOpens(helper, ComputingModule.LEGACY_MAINFRAME.get());
    }

    private static void assertMainframeMenuOpens(final GameTestHelper helper, final Block block) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, block);
        if (!(helper.getBlockEntity(pos) instanceof MainframeBlockEntity be)) {
            helper.fail("no MainframeBlockEntity at " + pos + " for " + block);
            return;
        }
        final Player player = helper.makeMockPlayer(GameType.CREATIVE);
        final BlockPos absolute = helper.absolutePos(pos);
        player.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        final var menu = new dev.jstech.computers.menu.MainframeMenu(
                1, player.getInventory(), be);
        helper.assertFalse(menu.slots.isEmpty(), "the Mainframe menu must build its slots for " + block);
        helper.assertTrue(menu.stillValid(player),
                "the Mainframe menu must stay valid for " + block
                        + "; otherwise the server closes the GUI one tick after it opens");
        helper.succeed();
    }

    // Era board gate: the right-era board powers on, the wrong-era board does not

    @GameTest(template = ARENA)
    public static void vintageCrafting_validBuild_powersOn(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.VINTAGE_CRAFTING_COMPUTER.get());
        if (!(helper.getBlockEntity(pos) instanceof CraftingComputerBlockEntity be)) {
            helper.fail("no CraftingComputerBlockEntity at " + pos);
            return;
        }
        final var hw = be.getHardware();
        hw.setStackInSlot(CraftingComputerBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(HardwareItems.MOTHERBOARD_BABYAT_VINTAGE.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.CPU_SLOT,
                new ItemStack(HardwareItems.CPU_INTEGRA_486SX.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.RAM_SLOTS_START,
                new ItemStack(HardwareItems.RAM_SIMM_4.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.PSU_SLOT,
                new ItemStack(HardwareItems.PSU_300B.get()));
        be.togglePower();
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> helper.assertTrue(be.isRunning(),
                        "vintage Crafting Computer on a Vintage Baby-AT board must power on"))
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void vintageCrafting_wrongEraBoard_doesNotPower(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.VINTAGE_CRAFTING_COMPUTER.get());
        if (!(helper.getBlockEntity(pos) instanceof CraftingComputerBlockEntity be)) {
            helper.fail("no CraftingComputerBlockEntity at " + pos);
            return;
        }
        // A Legacy ATX board in a Vintage chassis: the era gate must reject it, so the machine never runs.
        final var hw = be.getHardware();
        hw.setStackInSlot(CraftingComputerBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(HardwareItems.MOTHERBOARD_ATX_LEGACY_LGA775.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.CPU_SLOT,
                new ItemStack(HardwareItems.CPU_INTEGRA_DUO_E4300.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.RAM_SLOTS_START,
                new ItemStack(HardwareItems.RAM_DDR2_2048.get()));
        hw.setStackInSlot(CraftingComputerBlockEntity.PSU_SLOT,
                new ItemStack(HardwareItems.PSU_500B.get()));
        be.togglePower();
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> helper.assertFalse(be.isRunning(),
                        "vintage Crafting Computer must reject a Legacy board and stay off"))
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void vintageMainframe_validBuild_powersOn(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.VINTAGE_MAINFRAME.get());
        if (!(helper.getBlockEntity(pos) instanceof MainframeBlockEntity be)) {
            helper.fail("no MainframeBlockEntity at " + pos);
            return;
        }
        final var hw = be.getHardware();
        hw.setStackInSlot(MainframeBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(HardwareItems.MOTHERBOARD_MTX_VINTAGE.get()));
        hw.setStackInSlot(MainframeBlockEntity.CPU_SLOTS_START,
                new ItemStack(HardwareItems.CPU_VELOCION_K6_III.get()));
        hw.setStackInSlot(MainframeBlockEntity.RAM_SLOTS_START,
                new ItemStack(HardwareItems.RAM_SIMM_4.get()));
        hw.setStackInSlot(MainframeBlockEntity.PSU_SLOT,
                new ItemStack(HardwareItems.PSU_300B.get()));
        be.togglePower();
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> helper.assertTrue(be.isRunning(),
                        "vintage Mainframe on a Vintage MTX board with a Socket-7 CPU must power on"))
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void vintageMainframe_wrongEraBoard_doesNotPower(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.VINTAGE_MAINFRAME.get());
        if (!(helper.getBlockEntity(pos) instanceof MainframeBlockEntity be)) {
            helper.fail("no MainframeBlockEntity at " + pos);
            return;
        }
        // A Legacy MTX board in a Vintage Mainframe: same MTX form factor, wrong era, so the gate rejects it.
        final var hw = be.getHardware();
        hw.setStackInSlot(MainframeBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(HardwareItems.MOTHERBOARD_MTX_LEGACY.get()));
        hw.setStackInSlot(MainframeBlockEntity.CPU_SLOTS_START,
                new ItemStack(HardwareItems.CPU_VELOCION_DUAL_240.get()));
        hw.setStackInSlot(MainframeBlockEntity.RAM_SLOTS_START,
                new ItemStack(HardwareItems.RAM_DDR2_2048.get()));
        hw.setStackInSlot(MainframeBlockEntity.PSU_SLOT,
                new ItemStack(HardwareItems.PSU_500B.get()));
        be.togglePower();
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> helper.assertFalse(be.isRunning(),
                        "vintage Mainframe must reject a Legacy MTX board (right form factor, wrong era)"))
                .thenSucceed();
    }

    // Mainframe multiblock: every structural part inherits the controller's era

    @GameTest(template = ARENA)
    public static void vintageMainframe_formsWithEraParts(final GameTestHelper helper) {
        final BlockPos controller = new BlockPos(4, 2, 4);
        final Direction facing = Direction.NORTH;
        helper.setBlock(controller, ComputingModule.VINTAGE_MAINFRAME.get().defaultBlockState()
                .setValue(MainframeBlock.FACING, facing));
        ((MainframeBlock) ComputingModule.VINTAGE_MAINFRAME.get()).setPlacedBy(
                helper.getLevel(), helper.absolutePos(controller),
                helper.getBlockState(controller), null, ItemStack.EMPTY);
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    int parts = 0;
                    int vintageParts = 0;
                    for (final BlockPos p : MainframeStructure.allPositions(controller, facing)) {
                        final var state = helper.getBlockState(p);
                        if (state.getBlock() instanceof MainframePartBlock) {
                            parts++;
                            if (HardwareEra.fromLevel(state.getValue(MainframePartBlock.ERA)) == HardwareEra.VINTAGE) {
                                vintageParts++;
                            }
                        }
                    }
                    helper.assertTrue(parts > 0, "the Vintage Mainframe must raise its structural parts");
                    helper.assertTrue(parts == vintageParts,
                            "every part of a Vintage Mainframe must carry ERA=VINTAGE; "
                                    + vintageParts + " of " + parts + " did");
                })
                .thenSucceed();
    }

    // Era-correct drops: breaking the block returns its own era's item

    @GameTest(template = ARENA)
    public static void vintageCrafting_break_dropsVintageItem(final GameTestHelper helper) {
        assertBreakDropsItem(helper, ComputingModule.VINTAGE_CRAFTING_COMPUTER.get(),
                ComputingModule.VINTAGE_CRAFTING_COMPUTER_ITEM.get());
    }

    @GameTest(template = ARENA)
    public static void legacyCrafting_break_dropsLegacyItem(final GameTestHelper helper) {
        assertBreakDropsItem(helper, ComputingModule.LEGACY_CRAFTING_COMPUTER.get(),
                ComputingModule.LEGACY_CRAFTING_COMPUTER_ITEM.get());
    }

    private static void assertBreakDropsItem(final GameTestHelper helper, final Block block, final Item expected) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, block);
        helper.startSequence()
                // destroyBlock through the level with dropBlock=true so the loot table runs.
                .thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(pos), true))
                .thenExecuteAfter(SETTLE, () -> helper.assertItemEntityPresent(expected, pos, 3.0))
                .thenSucceed();
    }
}
