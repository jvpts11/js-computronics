/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.HardwareItems;
import dev.jstech.computronics.blockentity.PersonalComputerBlockEntity;
import dev.jstech.tests.JsTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Verifies era-based hardware enforcement for the Vintage and Legacy Personal Computers:
 * a mismatched CPU socket prevents boot, a matching socket allows it, and breaking either
 * block drops the era-correct BlockItem back into the world.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class EraComputerGameTests {

    private EraComputerGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    @GameTest(template = ARENA)
    public static void vintagePc_validBuild_powersOn(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.VINTAGE_PERSONAL_COMPUTER.get());
        if (!(helper.getBlockEntity(pos) instanceof PersonalComputerBlockEntity be)) {
            helper.fail("no vintage personal computer at " + pos);
            return;
        }
        final var hw = be.getHardware();
        hw.setStackInSlot(PersonalComputerBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(HardwareItems.MOTHERBOARD_BABYAT_VINTAGE.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.CPU_SLOT,
                new ItemStack(HardwareItems.CPU_INTEGRA_486SX.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.RAM_SLOTS_START,
                new ItemStack(HardwareItems.RAM_SIMM_4.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.PSU_SLOT,
                new ItemStack(HardwareItems.PSU_300B.get()));
        be.togglePower();

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> helper.assertTrue(
                        be.isRunning(),
                        "vintage PC with matching socket-3 CPU and SIMM RAM must power on"))
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void vintagePc_wrongEraCpu_doesNotPower(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.VINTAGE_PERSONAL_COMPUTER.get());
        if (!(helper.getBlockEntity(pos) instanceof PersonalComputerBlockEntity be)) {
            helper.fail("no vintage personal computer at " + pos);
            return;
        }
        // LGA-775 CPU installed into a Socket-3 board: socket mismatch prevents boot.
        final var hw = be.getHardware();
        hw.setStackInSlot(PersonalComputerBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(HardwareItems.MOTHERBOARD_BABYAT_VINTAGE.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.CPU_SLOT,
                new ItemStack(HardwareItems.CPU_INTEGRA_DUO_E4300.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.RAM_SLOTS_START,
                new ItemStack(HardwareItems.RAM_SIMM_4.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.PSU_SLOT,
                new ItemStack(HardwareItems.PSU_300B.get()));
        be.togglePower();

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> helper.assertFalse(
                        be.isRunning(),
                        "vintage PC with LGA-775 CPU in Socket-3 board must not power on"))
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void legacyPc_validBuild_powersOn(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.LEGACY_PERSONAL_COMPUTER.get());
        if (!(helper.getBlockEntity(pos) instanceof PersonalComputerBlockEntity be)) {
            helper.fail("no legacy personal computer at " + pos);
            return;
        }
        final var hw = be.getHardware();
        hw.setStackInSlot(PersonalComputerBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(HardwareItems.MOTHERBOARD_ATX_LEGACY_LGA775.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.CPU_SLOT,
                new ItemStack(HardwareItems.CPU_INTEGRA_DUO_E4300.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.RAM_SLOTS_START,
                new ItemStack(HardwareItems.RAM_DDR2_2048.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.PSU_SLOT,
                new ItemStack(HardwareItems.PSU_500B.get()));
        be.togglePower();

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> helper.assertTrue(
                        be.isRunning(),
                        "legacy PC with matching LGA-775 CPU and DDR2 RAM must power on"))
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void legacyPc_wrongEraCpu_doesNotPower(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.LEGACY_PERSONAL_COMPUTER.get());
        if (!(helper.getBlockEntity(pos) instanceof PersonalComputerBlockEntity be)) {
            helper.fail("no legacy personal computer at " + pos);
            return;
        }
        // Socket-3 CPU installed into an LGA-775 board: socket mismatch prevents boot.
        final var hw = be.getHardware();
        hw.setStackInSlot(PersonalComputerBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(HardwareItems.MOTHERBOARD_ATX_LEGACY_LGA775.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.CPU_SLOT,
                new ItemStack(HardwareItems.CPU_INTEGRA_486SX.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.RAM_SLOTS_START,
                new ItemStack(HardwareItems.RAM_DDR2_2048.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.PSU_SLOT,
                new ItemStack(HardwareItems.PSU_500B.get()));
        be.togglePower();

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> helper.assertFalse(
                        be.isRunning(),
                        "legacy PC with Socket-3 CPU in LGA-775 board must not power on"))
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void vintagePc_break_dropsVintageItem(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.VINTAGE_PERSONAL_COMPUTER.get());
        helper.startSequence()
                /*
                 * The vanilla helper.destroyBlock passes dropBlock=false, so drop through the level
                 * directly (dropBlock=true) to exercise the block's loot table.
                 */
                .thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(pos), true))
                .thenExecuteAfter(SETTLE, () -> helper.assertItemEntityPresent(
                        ComputingModule.VINTAGE_PERSONAL_COMPUTER_ITEM.get(), pos, 3.0))
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void legacyPc_break_dropsLegacyItem(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.LEGACY_PERSONAL_COMPUTER.get());
        helper.startSequence()
                /*
                 * The vanilla helper.destroyBlock passes dropBlock=false, so drop through the level
                 * directly (dropBlock=true) to exercise the block's loot table.
                 */
                .thenExecute(() -> helper.getLevel().destroyBlock(helper.absolutePos(pos), true))
                .thenExecuteAfter(SETTLE, () -> helper.assertItemEntityPresent(
                        ComputingModule.LEGACY_PERSONAL_COMPUTER_ITEM.get(), pos, 3.0))
                .thenSucceed();
    }
}
