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
import dev.jstech.computronics.JsComputronics;
import dev.jstech.computronics.block.MonitorBlock;
import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.blockentity.PersonalComputerBlockEntity;
import dev.jstech.computronics.blockentity.ServerRackBlockEntity;
import dev.jstech.computronics.hardware.DiskSize;
import dev.jstech.computronics.hardware.StorageTier;
import dev.jstech.computronics.operation.payload.ComputingPayloads;
import dev.jstech.computronics.os.OsHost;
import dev.jstech.computronics.os.media.MediaItem;
import dev.jstech.computronics.os.media.MediaKind;
import dev.jstech.computronics.os.media.MediaReaderBlockEntity;
import dev.jstech.tests.JsTests;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * A guided installer writes the system and then waits for a reboot. Until that reboot happens the
 * machine is still running the installer, whatever the disk now holds: leaving the monitor and coming
 * back must return to the reboot prompt, never to a system that was never booted. Only a restart or
 * a power change ends the installer.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class OsInstallGameTests {

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;
    private static final ResourceLocation DEBIAN = ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "debian");
    private static final ResourceLocation FRAMES_95 = ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "frames_95");

    private OsInstallGameTests() {
    }

    /** A running Mainframe with an empty disk and, beside it, a CD drive holding a guided installer. */
    private static MainframeBlockEntity placeMainframeWithInstaller(final GameTestHelper helper, final BlockPos pos) {
        helper.setBlock(pos, ComputingModule.MAINFRAME.get());
        if (!(helper.getBlockEntity(pos) instanceof MainframeBlockEntity mainframe)) {
            throw new IllegalStateException("no Mainframe at " + pos);
        }
        final ItemStackHandler inv = mainframe.getInventory();
        inv.setStackInSlot(MainframeBlockEntity.MOTHERBOARD_SLOT, new ItemStack(ComputingModule.MOTHERBOARD_MTX_P.get()));
        inv.setStackInSlot(MainframeBlockEntity.CPU_SLOTS_START, new ItemStack(ComputingModule.CPU_SERVO_2620.get()));
        inv.setStackInSlot(MainframeBlockEntity.RAM_SLOTS_START, new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        inv.setStackInSlot(MainframeBlockEntity.PSU_SLOT, new ItemStack(ComputingModule.PSU_650G.get()));
        // A GPU gives the Mainframe peripheral ports so the adjacent reader can link to it.
        inv.setStackInSlot(MainframeBlockEntity.GPU_SLOTS_START, new ItemStack(ComputingModule.GPU_HD_7970.get()));
        inv.setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        mainframe.togglePower();

        final BlockPos readerPos = pos.east();
        helper.setBlock(readerPos, ComputingModule.CD_DRIVE.get());
        if (!(helper.getBlockEntity(readerPos) instanceof MediaReaderBlockEntity reader)) {
            throw new IllegalStateException("no reader at " + readerPos);
        }
        final ItemStack disc = new ItemStack(ComputingModule.CD_ROM.get());
        MediaItem.setKind(disc, MediaKind.OS_INSTALL);
        MediaItem.setPayload(disc, DEBIAN);
        reader.mediaSlot().setStackInSlot(0, disc);
        return mainframe;
    }

    /** Runs the installer's final write, as the firmware action does when the on-screen progress ends. */
    private static void finishInstaller(final GameTestHelper helper, final MainframeBlockEntity mainframe) {
        helper.assertTrue(!mainframe.linkedEndpoints().isEmpty(), "the reader links to the Mainframe");
        helper.assertTrue(ComputingPayloads.installOsFromReader(helper.getLevel(), mainframe, -1L, -1),
                "the guided installer writes the system");
        helper.assertTrue(DEBIAN.equals(mainframe.installedOsId()), "the system is on the disk");
    }

    @GameTest(template = ARENA)
    public static void guidedInstall_keepsTheInstallerUntilTheReboot(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithInstaller(helper, pos);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    mainframe.setNeedsPost(false); // the player already sat through POST to reach the firmware
                    helper.assertTrue(MonitorBlock.entryFor(mainframe) == MonitorBlock.Entry.BOOT,
                            "before the install a running machine boots its target");
                    finishInstaller(helper, mainframe);
                    helper.assertTrue(mainframe.pendingInstallSlot() != OsHost.NO_PENDING_INSTALL,
                            "the machine remembers it is still in the installer");
                    helper.assertTrue(MonitorBlock.entryFor(mainframe) == MonitorBlock.Entry.INSTALLER,
                            "reopening the monitor returns to the installer's reboot prompt, not the system");
                    // The reboot the prompt asks for: a restart, as the firmware's boot-disk action performs.
                    mainframe.setNeedsPost(true);
                    helper.assertTrue(mainframe.pendingInstallSlot() == OsHost.NO_PENDING_INSTALL,
                            "the restart ends the installer");
                    helper.assertTrue(MonitorBlock.entryFor(mainframe) == MonitorBlock.Entry.POST,
                            "the restart runs POST");
                    mainframe.setNeedsPost(false);
                    helper.assertTrue(MonitorBlock.entryFor(mainframe) == MonitorBlock.Entry.BOOT,
                            "after POST the machine boots the system it installed");
                })
                .thenSucceed();
    }

    /**
     * A guided installer for a system newer than the machine's era is refused with a reason the player
     * can act on, and the disk stays as it was. The refusal used to be silent: the installer reported
     * every step done, and the next boot landed back in the firmware with nothing on the disk.
     */
    @GameTest(template = ARENA)
    public static void guidedInstall_refusesASystemNewerThanTheMachine(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.VINTAGE_PERSONAL_COMPUTER.get());
        if (!(helper.getBlockEntity(pos) instanceof PersonalComputerBlockEntity pc)) {
            throw new IllegalStateException("no vintage personal computer at " + pos);
        }
        // The weakest machine the mod builds, with the smallest disk that still holds the system.
        final ItemStackHandler hw = pc.getHardware();
        hw.setStackInSlot(PersonalComputerBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(HardwareItems.MOTHERBOARD_BABYAT_VINTAGE.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.CPU_SLOT, new ItemStack(HardwareItems.CPU_INTEGRA_486SX.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.RAM_SLOTS_START, new ItemStack(HardwareItems.RAM_SIMM_4.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.PSU_SLOT, new ItemStack(HardwareItems.PSU_300B.get()));
        hw.setStackInSlot(PersonalComputerBlockEntity.DISK_SLOTS_START,
                new ItemStack(HardwareItems.DISK_TRENCH_20M.get()));
        pc.togglePower();

        final BlockPos readerPos = pos.east();
        helper.setBlock(readerPos, ComputingModule.CD_DRIVE.get());
        if (!(helper.getBlockEntity(readerPos) instanceof MediaReaderBlockEntity reader)) {
            throw new IllegalStateException("no reader at " + readerPos);
        }
        final ItemStack disc = new ItemStack(ComputingModule.CD_ROM.get());
        MediaItem.setKind(disc, MediaKind.OS_INSTALL);
        MediaItem.setPayload(disc, FRAMES_95);
        reader.mediaSlot().setStackInSlot(0, disc);

        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    helper.assertTrue(pc.isRunning(), "the vintage machine powers on");
                    helper.assertTrue(!pc.linkedEndpoints().isEmpty(), "the reader links to the computer");
                    final String failure = ComputingPayloads.installFailure(helper.getLevel(), pc, -1L, -1);
                    helper.assertTrue(failure != null && failure.contains("Legacy") && failure.contains("Vintage"),
                            "the refusal names the era the system needs and the machine's own, got: " + failure);
                    helper.assertTrue(!pc.hasOs(), "nothing was written to the disk");
                    helper.assertTrue(pc.pendingInstallSlot() == OsHost.NO_PENDING_INSTALL,
                            "no reboot is pending after a refused install");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void guidedInstall_powerCycleEndsTheInstaller(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithInstaller(helper, pos);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    mainframe.setNeedsPost(false);
                    finishInstaller(helper, mainframe);
                    helper.assertTrue(MonitorBlock.entryFor(mainframe) == MonitorBlock.Entry.INSTALLER,
                            "the installer waits for its reboot");
                    mainframe.togglePower(); // off
                    helper.assertTrue(mainframe.pendingInstallSlot() == OsHost.NO_PENDING_INSTALL,
                            "switching the machine off drops the installer session");
                    helper.assertTrue(MonitorBlock.entryFor(mainframe) == MonitorBlock.Entry.NO_POWER,
                            "an off machine has no signal");
                    mainframe.togglePower(); // on again
                    helper.assertTrue(MonitorBlock.entryFor(mainframe) == MonitorBlock.Entry.POST,
                            "a cold start runs POST and then boots the installed system");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void guidedInstall_pendingRebootIsSavedWithTheMachine(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithInstaller(helper, pos);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    mainframe.setNeedsPost(false);
                    finishInstaller(helper, mainframe);
                    final CompoundTag saved = mainframe.saveWithoutMetadata(helper.getLevel().registryAccess());
                    helper.assertTrue(saved.contains("PendingInstall") && saved.getInt("PendingInstall") == -1,
                            "the pending reboot is written with the machine, so a reload keeps it in the installer");
                    // A formatted or pulled disk leaves nothing to reboot into: the pending install is dropped.
                    mainframe.getInventory().setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START, ItemStack.EMPTY);
                    helper.assertTrue(MonitorBlock.entryFor(mainframe) == MonitorBlock.Entry.BOOT
                                    && mainframe.pendingInstallSlot() == OsHost.NO_PENDING_INSTALL,
                            "with the installed disk gone the machine falls back to its boot target");
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA)
    public static void rackUnit_pendingRebootRidesOnTheServerItem(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.SERVER_RACK.get());
        if (!(helper.getBlockEntity(pos) instanceof ServerRackBlockEntity rack)) {
            helper.fail("no rack at " + pos);
            return;
        }
        TestWorldBuilder.mountDefaultServer(rack, 0);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final OsHost unit = rack.unitHost(0);
                    unit.setPendingInstallSlot(0);
                    helper.assertTrue(unit.pendingInstallSlot() == 0, "the unit remembers its pending reboot");
                    final CompoundTag onItem = rack.getServers().getStackInSlot(0).get(ComputingModule.SERVER_CONSOLE.get());
                    helper.assertTrue(onItem != null && onItem.getInt("PendingInstall") == 0,
                            "the pending reboot is flushed onto the Server item with the rest of its session");
                    rack.toggleBayPower(0); // the bay switch is this machine's power button
                    helper.assertTrue(unit.pendingInstallSlot() == OsHost.NO_PENDING_INSTALL,
                            "switching the bay off drops the installer session");
                    final CompoundTag after = rack.getServers().getStackInSlot(0).get(ComputingModule.SERVER_CONSOLE.get());
                    helper.assertTrue(after == null || !after.contains("PendingInstall"),
                            "and the item no longer carries it");
                })
                .thenSucceed();
    }
}
