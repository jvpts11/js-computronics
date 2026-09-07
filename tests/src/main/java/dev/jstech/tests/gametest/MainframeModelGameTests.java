/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.hardware.DiskSize;
import dev.jstech.computronics.hardware.StorageTier;
import dev.jstech.tests.JsTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * The Mainframe is drawn as one cabinet from its controller, with a bone per installed part, so the
 * block entity has to report what is seated, what condition the machine is in and whether the service
 * panel is off. None of that is in the client's copy of a computer by default: it travels in the block
 * update, which is what these tests pin down.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class MainframeModelGameTests {

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    private MainframeModelGameTests() {
    }

    private static MainframeBlockEntity place(final GameTestHelper helper, final BlockPos pos, final Block block) {
        helper.setBlock(pos, block);
        if (!(helper.getBlockEntity(pos) instanceof MainframeBlockEntity mainframe)) {
            throw new IllegalStateException("no mainframe at " + pos);
        }
        return mainframe;
    }

    /** Board, one processor, one memory stick, one graphics card, the power supply and a disk. */
    private static void fitMinimalBuild(final MainframeBlockEntity mainframe) {
        final ItemStackHandler hardware = mainframe.getHardware();
        hardware.setStackInSlot(MainframeBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(ComputingModule.MOTHERBOARD_MTX_P.get()));
        hardware.setStackInSlot(MainframeBlockEntity.CPU_SLOTS_START,
                new ItemStack(ComputingModule.CPU_ASCENT_965.get()));
        hardware.setStackInSlot(MainframeBlockEntity.RAM_SLOTS_START,
                new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        hardware.setStackInSlot(MainframeBlockEntity.GPU_SLOTS_START,
                new ItemStack(ComputingModule.GPU_HD_7970.get()));
        hardware.setStackInSlot(MainframeBlockEntity.PSU_SLOT,
                new ItemStack(ComputingModule.PSU_650G.get()));
        hardware.setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
    }

    @GameTest(template = ARENA)
    public static void mainframeModel_everyInstalledPartIsReportedToTheRenderer(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = place(helper, new BlockPos(2, 2, 2), ComputingModule.MAINFRAME.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertFalse(mainframe.hardwareInstalled(MainframeBlockEntity.CPU_SLOTS_START),
                            "an untouched cabinet shows no processor");
                    fitMinimalBuild(mainframe);
                    helper.assertTrue(mainframe.hardwareInstalled(MainframeBlockEntity.CPU_SLOTS_START),
                            "the first processor socket reports the part seated in it");
                    helper.assertFalse(mainframe.hardwareInstalled(MainframeBlockEntity.CPU_SLOTS_START + 1),
                            "an empty socket beside it stays empty");
                    helper.assertTrue(mainframe.hardwareInstalled(MainframeBlockEntity.RAM_SLOTS_START)
                                    && mainframe.hardwareInstalled(MainframeBlockEntity.GPU_SLOTS_START)
                                    && mainframe.hardwareInstalled(MainframeBlockEntity.PSU_SLOT)
                                    && mainframe.hardwareInstalled(MainframeBlockEntity.DISK_SLOTS_START),
                            "memory, graphics, the power supply and the disk each report their own slot");
                    helper.assertFalse(mainframe.diskCarriesSystem(0),
                            "a blank disk lights no system lamp");
                    helper.assertFalse(mainframe.hardwareInstalled(MainframeBlockEntity.TOTAL_SLOTS),
                            "a slot index past the hardware is never reported as filled");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void mainframeModel_blockUpdateCarriesTheCabinetsLook(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = place(helper, new BlockPos(2, 2, 2), ComputingModule.MAINFRAME.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    fitMinimalBuild(mainframe);
                    final CompoundTag tag = mainframe.getUpdateTag(helper.getLevel().registryAccess());
                    final int hardware = tag.getInt("VisualHardware");
                    helper.assertTrue((hardware & (1 << MainframeBlockEntity.CPU_SLOTS_START)) != 0,
                            "the update carries the seated processor for the client's model");
                    helper.assertTrue((hardware & (1 << MainframeBlockEntity.PSU_SLOT)) != 0,
                            "and the power supply, which decides whether the build comes up");
                    helper.assertTrue((hardware & (1 << (MainframeBlockEntity.CPU_SLOTS_START + 1))) == 0,
                            "an empty socket is not sent as filled");
                    helper.assertTrue(tag.contains("VisualSystems") && tag.contains("VisualFlags"),
                            "the disk lamps and the machine's condition travel with it");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void mainframeModel_servicePanelComesOffAndTravels(final GameTestHelper helper) {
        final MainframeBlockEntity mainframe = place(helper, new BlockPos(2, 2, 2), ComputingModule.MAINFRAME.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertFalse(mainframe.servicePanelOff(), "the service panel starts on, hiding the bay");
                    mainframe.toggleServicePanel();
                    helper.assertTrue(mainframe.servicePanelOff(), "sneak-use takes it off");
                    final CompoundTag tag = mainframe.getUpdateTag(helper.getLevel().registryAccess());
                    helper.assertTrue((tag.getInt("VisualFlags") & 8) != 0,
                            "the panel state travels to the client with the block update");
                    mainframe.toggleServicePanel();
                    helper.assertFalse(mainframe.servicePanelOff(), "and puts it back");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void mainframeModel_eachEraDrawsItsOwnCabinetOverTheWholeFootprint(final GameTestHelper helper) {
        final MainframeBlockEntity standard = place(helper, new BlockPos(1, 2, 1), ComputingModule.MAINFRAME.get());
        final MainframeBlockEntity vintage = place(helper, new BlockPos(5, 2, 1), ComputingModule.VINTAGE_MAINFRAME.get());
        final MainframeBlockEntity legacy = place(helper, new BlockPos(1, 2, 5), ComputingModule.LEGACY_MAINFRAME.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(standard.mainframeEra() == dev.jstech.core.tier.HardwareEra.STANDARD
                                    && vintage.mainframeEra() == dev.jstech.core.tier.HardwareEra.VINTAGE
                                    && legacy.mainframeEra() == dev.jstech.core.tier.HardwareEra.LEGACY,
                            "each cabinet reports its own era, which picks its model and atlas");
                    /*
                     * The cabinet is three wide, two tall and two deep; culling by the controller's own
                     * block would blink the whole machine out as the player walks past it.
                     */
                    final net.minecraft.world.phys.AABB box = standard.renderBox();
                    helper.assertTrue(box.getXsize() >= 3.0D && box.getYsize() >= 2.0D && box.getZsize() >= 2.0D,
                            "the render box covers the whole footprint, not just the controller");
                })
                .thenSucceed();
    }
}
