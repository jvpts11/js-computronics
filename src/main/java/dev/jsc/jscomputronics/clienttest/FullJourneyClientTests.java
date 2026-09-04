/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.clienttest;

import dev.jsc.jscomputronics.common.hardware.DiskSize;
import dev.jsc.jscomputronics.common.hardware.StorageTier;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.CraftingComputerBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.blockentity.ServerRackBlockEntity;
import dev.jsc.jscomputronics.module.computing.client.CraftingComputerScreen;
import dev.jsc.jscomputronics.module.computing.client.FirmwareScreen;
import dev.jsc.jscomputronics.module.computing.client.MainframeScreen;
import dev.jsc.jscomputronics.module.computing.client.PatternEncoderScreen;
import dev.jsc.jscomputronics.module.computing.client.ServerRackScreen;
import dev.jsc.jscomputronics.module.computing.client.os.CraftingManagerApp;
import dev.jsc.jscomputronics.module.computing.client.os.DesktopScreen;
import dev.jsc.jscomputronics.module.computing.client.os.DesktopWindow;
import dev.jsc.jscomputronics.module.computing.client.os.NetworkInteractorApp;
import dev.jsc.jscomputronics.module.computing.client.os.ThisPcApp;
import dev.jsc.jscomputronics.module.computing.gui.layout.CraftingComputerLayout;
import dev.jsc.jscomputronics.module.computing.menu.PatternEncoderMenu;
import dev.jsc.jscomputronics.module.computing.operation.NetworkStorage;
import dev.jsc.jscomputronics.module.computing.os.FilesystemKind;
import dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem;
import dev.jsc.jscomputronics.module.computing.os.fs.FileType;
import dev.jsc.jscomputronics.module.computing.os.media.MediaItem;
import dev.jsc.jscomputronics.module.computing.os.media.MediaKind;
import dev.jsc.jscomputronics.module.computing.os.media.MediaReaderBlockEntity;
import dev.jsc.jscomputronics.module.computing.program.Programs;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.testkit.TestWorldBuilder;
import net.minecraft.client.gui.screens.inventory.AbstractFurnaceScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The whole player journey, with nothing built by API: every block placed from the hand, every component
 * slotted through the assembly GUIs, both operating systems installed from their media through the firmware,
 * the Crafting Manager installed from its disc, the patterns authored at the encoder, loaded through the
 * Crafting Manager, and the crafts requested through the Network Interactor — with real smelting, a real
 * save/reload in the middle of a craft, and a multi-stage pipeline at the end. The only thing the test hands
 * the player is items (the mod is creative-only).
 */
public final class FullJourneyClientTests {

    private FullJourneyClientTests() {
    }

    private static final int SETTLE = 4;
    private static final int SCREEN_WAIT = 60;

    // World layout (relative; y = 2 stands on the ground). The Mainframe faces EAST (placed looking west), so
    // its 3x2x2 footprint covers x 1..2, z 1..3; the rack faces SOUTH (placed looking north) and covers
    // x 3..4, z 3..4, its rear touching the HBW cables at z = 2.
    private static final BlockPos MAINFRAME = new BlockPos(2, 2, 2);
    private static final BlockPos MAINFRAME_PART_SOUTH = new BlockPos(1, 2, 3);
    private static final BlockPos HBW_1 = new BlockPos(3, 2, 2);
    private static final BlockPos HBW_2 = new BlockPos(4, 2, 2);
    private static final BlockPos ROUTER = new BlockPos(5, 2, 2);
    private static final BlockPos ETHERNET = new BlockPos(6, 2, 2);
    private static final BlockPos CRAFTING_COMPUTER = new BlockPos(7, 2, 2);
    private static final BlockPos RACK = new BlockPos(4, 2, 4);
    private static final BlockPos MF_MONITOR = new BlockPos(0, 2, 2);
    private static final BlockPos MF_FLOPPY_DRIVE = new BlockPos(0, 2, 1);
    private static final BlockPos CC_CD_DRIVE = new BlockPos(7, 3, 2);
    private static final BlockPos CC_MONITOR = new BlockPos(8, 2, 2);
    private static final BlockPos CC_FLOPPY_DRIVE = new BlockPos(7, 2, 1);
    private static final BlockPos CRAFTING_CABLE = new BlockPos(7, 2, 3);
    private static final BlockPos SWITCH = new BlockPos(7, 2, 4);
    private static final BlockPos CABLE_BELOW_FURNACE = new BlockPos(7, 2, 5);
    private static final BlockPos CABLE_RUN_1 = new BlockPos(7, 2, 6);
    private static final BlockPos CABLE_RUN_2 = new BlockPos(7, 3, 6);
    private static final BlockPos CABLE_RUN_3 = new BlockPos(7, 4, 6);
    private static final BlockPos CABLE_ABOVE_FURNACE = new BlockPos(7, 4, 5);
    private static final BlockPos FURNACE = new BlockPos(7, 3, 5);
    private static final BlockPos ENCODER = new BlockPos(10, 2, 5);

    private static final ResourceLocation NETWORK_OS = ResourceLocation.fromNamespaceAndPath("jsc", "mc_net");
    private static final ResourceLocation FRAMES_95 = ResourceLocation.fromNamespaceAndPath("jsc", "frames_95");

    // Assembly GUI slot centres (window-relative): the Mainframe and Crafting Computer share the left column.
    private static final int MOBO_X = 16;
    private static final int MOBO_Y = 48;
    private static final int PSU_X = 16;
    private static final int PSU_Y = 81;
    private static final int CPU_X = 52;
    private static final int CPU_Y = 48;
    private static final int RAM_X = 52;
    private static final int RAM_Y = 81;
    private static final int MF_GPU_X = 52;
    private static final int MF_GPU_Y = 132;
    private static final int MF_DISK_X = 16;
    private static final int MF_DISK_Y = 132;
    private static final int MF_HOTBAR_Y = 248;
    private static final int CC_PCIE0_X = 52;
    private static final int CC_PCIE1_X = 70;
    private static final int CC_PCIE_Y = 114;
    private static final int CC_DISK_X = 16;
    private static final int CC_DISK_Y = 114;
    private static final int CC_HOTBAR_Y = CraftingComputerLayout.INV_Y + 58 + 8;
    private static final int CC_POWER_X = CraftingComputerLayout.POWER_X + CraftingComputerLayout.COL_R_W / 2;
    private static final int CC_POWER_Y = CraftingComputerLayout.POWER_Y + CraftingComputerLayout.BTN_H / 2;
    private static final int RACK_SLOT_X = dev.jsc.jscomputronics.module.computing.gui.layout
            .ServerRackLayout.SERVER_X + 8;
    private static final int RACK_SLOT_Y = dev.jsc.jscomputronics.module.computing.gui.layout
            .ServerRackLayout.ROW_Y0 + 8;
    private static final int RACK_HOTBAR_Y = dev.jsc.jscomputronics.module.computing.gui.layout
            .ServerRackLayout.HOTBAR_Y + 8;
    private static final int FURNACE_FUEL_X = 64;
    private static final int FURNACE_FUEL_Y = 61;
    private static final int FURNACE_HOTBAR_Y = 150;

    // Pattern Encoder (window-relative).
    private static final int TAB_ROW_Y = 24;
    private static final int TAB_CRAFTING_X = 33;
    private static final int TAB_PROCESSING_X = 89;
    private static final int TAB_MULTI_X = 156;
    private static final int MEDIA_SLOT_X = 16;
    private static final int MEDIA_SLOT_Y = 116;
    private static final int GHOST_CELL_X = 34;
    private static final int GHOST_CELL_Y = 52;
    private static final int INPUT_CELL_X = 16;
    private static final int OUTPUT_CELL_X = 146;
    private static final int PROC_CELL_Y = 52;
    private static final int MACHINE_BTN_X = 101;
    private static final int MACHINE_BTN_Y = 50;
    private static final int WRITE_X = 144;
    private static final int WRITE_Y = 116;
    private static final int ENCODER_HOTBAR_Y = 204;

    private static int hotbarX(final int slot) {
        return 16 + slot * 18;
    }

    private static ItemStack installer(final ItemStack medium, final MediaKind kind, final ResourceLocation payload) {
        MediaItem.setKind(medium, kind);
        MediaItem.setPayload(medium, payload);
        return medium;
    }

    @ClientTest(timeoutTicks = 9000)
    public static void journey_buildsAssemblesInstallsAndCraftsEverythingAsThePlayer(final ClientTestContext ctx) {
        // ---- 1. Build the network backbone from the hand.
        ctx.thenGive(0, new ItemStack(ComputingModule.MAINFRAME.get()))
                .thenTeleport(SETTLE, new BlockPos(5, 2, 2), Direction.WEST)
                .then(SETTLE, () -> ctx.selectHotbar(0))
                .thenPlace(1, MAINFRAME)
                .thenWaitUntilServer(level -> level.getBlockEntity(abs(ctx, MAINFRAME)) instanceof MainframeBlockEntity,
                        SCREEN_WAIT, "the Mainframe to be placed", level -> "block=" + level.getBlockState(abs(ctx, MAINFRAME)))
                .thenGive(0, new ItemStack(ComputingModule.HBW_CABLE.get(), 4), new ItemStack(ComputingModule.PERSONAL_ROUTER.get()),
                        new ItemStack(ComputingModule.ETHERNET_CABLE.get(), 4))
                .thenTeleport(SETTLE, new BlockPos(4, 2, 0), Direction.SOUTH)
                .then(SETTLE, () -> ctx.selectHotbar(0))
                .thenPlace(1, HBW_1)
                .thenPlace(2, HBW_2)
                .then(2, () -> ctx.selectHotbar(1))
                .thenPlace(1, ROUTER)
                .then(2, () -> ctx.selectHotbar(2))
                .thenPlace(1, ETHERNET)
                // The Crafting Computer faces the player, so placing it while looking west puts its rear on the cable.
                .thenGive(2, new ItemStack(ComputingModule.CRAFTING_COMPUTER.get()))
                .thenTeleport(SETTLE, new BlockPos(9, 2, 2), Direction.WEST)
                .then(SETTLE, () -> ctx.selectHotbar(0))
                .thenPlace(1, CRAFTING_COMPUTER)
                .thenGive(2, new ItemStack(ComputingModule.SERVER_RACK_ITEM.get()))
                .thenTeleport(SETTLE, new BlockPos(4, 2, 7), Direction.NORTH)
                .then(SETTLE, () -> ctx.selectHotbar(0))
                .thenPlace(1, RACK)
                .thenWaitUntilServer(level -> level.getBlockEntity(abs(ctx, RACK)) instanceof ServerRackBlockEntity
                                && level.getBlockEntity(abs(ctx, CRAFTING_COMPUTER)) instanceof CraftingComputerBlockEntity,
                        SCREEN_WAIT, "the rack and the Crafting Computer to be placed",
                        level -> "rack=" + level.getBlockState(abs(ctx, RACK)) + " cc=" + level.getBlockState(abs(ctx, CRAFTING_COMPUTER)))
                .thenScreenshot(2, "01-backbone");

        // ---- 2. Assemble the Mainframe through its GUI and power it on.
        ctx.thenGive(0, new ItemStack(ComputingModule.MOTHERBOARD_MTX_P.get()), new ItemStack(ComputingModule.CPU_SERVO_2620.get()),
                        new ItemStack(ComputingModule.RAM_DDR3_8192.get()), new ItemStack(ComputingModule.PSU_650G.get()),
                        new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)), new ItemStack(ComputingModule.GPU_HD_7970.get()))
                .thenTeleport(SETTLE, new BlockPos(1, 2, 5), Direction.NORTH)
                .then(SETTLE, () -> ctx.selectHotbar(8))
                .thenRightClick(1, MAINFRAME_PART_SOUTH)
                .thenAwaitScreen(MainframeScreen.class, SCREEN_WAIT)
                .then(2, () -> slotFromHotbar(ctx, 0, MF_HOTBAR_Y, MOBO_X, MOBO_Y))
                .then(2, () -> slotFromHotbar(ctx, 1, MF_HOTBAR_Y, CPU_X, CPU_Y))
                .then(2, () -> slotFromHotbar(ctx, 2, MF_HOTBAR_Y, RAM_X, RAM_Y))
                .then(2, () -> slotFromHotbar(ctx, 3, MF_HOTBAR_Y, PSU_X, PSU_Y))
                .then(2, () -> slotFromHotbar(ctx, 4, MF_HOTBAR_Y, MF_DISK_X, MF_DISK_Y))
                .then(2, () -> slotFromHotbar(ctx, 5, MF_HOTBAR_Y, MF_GPU_X, MF_GPU_Y))
                .thenScreenshot(SETTLE, "02-mainframe-assembled")
                .then(0, () -> ctx.clickGui(MainframeScreen.powerButtonX(), MainframeScreen.powerButtonY()))
                .thenWaitUntilServer(level -> mainframe(ctx, level).isRunning(), SCREEN_WAIT,
                        "the Mainframe to power on from its GUI", level -> "running=" + mainframe(ctx, level).isRunning())
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);

        // ---- 3. Install the Network OS from a floppy through the firmware on a linked monitor, then reboot.
        ctx.thenGive(0, new ItemStack(ComputingModule.FLOPPY_DRIVE.get()), new ItemStack(ComputingModule.MONITOR.get()),
                        installer(new ItemStack(ComputingModule.FLOPPY_DISK.get()), MediaKind.OS_INSTALL, NETWORK_OS))
                .thenTeleport(SETTLE, new BlockPos(-2, 2, 2), Direction.EAST)
                .then(SETTLE, () -> ctx.selectHotbar(0))
                .thenPlace(1, MF_FLOPPY_DRIVE)
                .then(2, () -> ctx.selectHotbar(1))
                .thenPlace(1, MF_MONITOR)
                .then(2, () -> ctx.selectHotbar(2))
                .thenRightClick(SETTLE, MF_FLOPPY_DRIVE)
                .thenWaitUntilServer(level -> reader(ctx, level, MF_FLOPPY_DRIVE).ownerPos() != null
                                && !reader(ctx, level, MF_FLOPPY_DRIVE).mediaSlot().getStackInSlot(0).isEmpty(),
                        SCREEN_WAIT, "the Mainframe's floppy drive to link and hold the Network OS floppy",
                        level -> "owner=" + reader(ctx, level, MF_FLOPPY_DRIVE).ownerPos()
                                + " media=" + reader(ctx, level, MF_FLOPPY_DRIVE).mediaSlot().getStackInSlot(0))
                .then(2, () -> ctx.selectHotbar(8))
                .thenRightClick(SETTLE, MF_MONITOR)
                .thenAwaitScreen(FirmwareScreen.class, SCREEN_WAIT)
                .thenScreenshot(SETTLE, "03-mainframe-firmware")
                .then(0, () -> {
                    final int[] install = ctx.screen(FirmwareScreen.class).installButtonCenter();
                    ctx.click(install[0], install[1]);
                })
                .thenWaitUntilServer(level -> mainframe(ctx, level).hasOs(), SCREEN_WAIT,
                        "the firmware to install the Network OS from the linked floppy",
                        level -> "hasOs=" + mainframe(ctx, level).hasOs())
                .thenAwaitNoScreen(SCREEN_WAIT)
                // Reboot so the freshly installed OS boots (power off, power on — as the player would).
                .thenTeleport(SETTLE, new BlockPos(1, 2, 5), Direction.NORTH)
                .thenRightClick(SETTLE, MAINFRAME_PART_SOUTH)
                .thenAwaitScreen(MainframeScreen.class, SCREEN_WAIT)
                .then(2, () -> ctx.clickGui(MainframeScreen.powerButtonX(), MainframeScreen.powerButtonY()))
                .thenWaitUntilServer(level -> !mainframe(ctx, level).isRunning(), SCREEN_WAIT, "the Mainframe to power off",
                        level -> "running=" + mainframe(ctx, level).isRunning())
                .then(SETTLE, () -> ctx.clickGui(MainframeScreen.powerButtonX(), MainframeScreen.powerButtonY()))
                .thenWaitUntilServer(level -> mainframe(ctx, level).isRunning() && mainframe(ctx, level).networkUuid() != null,
                        SCREEN_WAIT, "the Mainframe to boot the Network OS and own a network",
                        level -> "running=" + mainframe(ctx, level).isRunning() + " net=" + mainframe(ctx, level).networkUuid())
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);

        // ---- 4. A server in the rack, through the rack GUI.
        ctx.thenGive(0, ComputingModule.defaultServer())
                .thenTeleport(SETTLE, new BlockPos(4, 2, 7), Direction.NORTH)
                .then(SETTLE, () -> ctx.selectHotbar(8))
                .thenRightClick(1, RACK)
                .thenAwaitScreen(ServerRackScreen.class, SCREEN_WAIT)
                .then(2, () -> slotFromHotbar(ctx, 0, RACK_HOTBAR_Y, RACK_SLOT_X, RACK_SLOT_Y))
                .thenScreenshot(SETTLE, "04-rack")
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT)
                .thenWaitUntilServer(level -> rack(ctx, level) != null && !rack(ctx, level).getServers().getStackInSlot(0).isEmpty(),
                        SCREEN_WAIT, "the server to sit in the rack", level -> "rack=" + rack(ctx, level))
                // Storage lives on the rack's front-panel bay drives now, and the interim rack GUI has
                // no hotswap slots yet, so the drives go in server-side; the rack GUI rework will make
                // this a player action.
                .thenServer(SETTLE, level -> {
                    final var rackBe = rack(ctx, level);
                    rackBe.insertDrive(0, new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1)));
                    rackBe.insertDrive(0, new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1)));
                });

        // ---- 5. Assemble the Crafting Computer (card + GPU) and power it on.
        ctx.thenGive(0, new ItemStack(ComputingModule.MOTHERBOARD_ATX_P.get()), new ItemStack(ComputingModule.CPU_ASCENT_965.get()),
                        new ItemStack(ComputingModule.RAM_DDR3_8192.get()), new ItemStack(ComputingModule.PSU_650G.get()),
                        new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)), new ItemStack(ComputingModule.CRAFTING_CARD_T2.get()),
                        new ItemStack(ComputingModule.GPU_HD_7970.get()))
                .thenTeleport(SETTLE, new BlockPos(7, 2, 0), Direction.SOUTH)
                .then(SETTLE, () -> ctx.selectHotbar(8))
                .thenRightClick(1, CRAFTING_COMPUTER)
                .thenAwaitScreen(CraftingComputerScreen.class, SCREEN_WAIT)
                .then(2, () -> slotFromHotbar(ctx, 0, CC_HOTBAR_Y, MOBO_X, MOBO_Y))
                .then(2, () -> slotFromHotbar(ctx, 1, CC_HOTBAR_Y, CPU_X, CPU_Y))
                .then(2, () -> slotFromHotbar(ctx, 2, CC_HOTBAR_Y, RAM_X, RAM_Y))
                .then(2, () -> slotFromHotbar(ctx, 3, CC_HOTBAR_Y, PSU_X, PSU_Y))
                .then(2, () -> slotFromHotbar(ctx, 4, CC_HOTBAR_Y, CC_DISK_X, CC_DISK_Y))
                .then(2, () -> slotFromHotbar(ctx, 5, CC_HOTBAR_Y, CC_PCIE0_X, CC_PCIE_Y))
                .then(2, () -> slotFromHotbar(ctx, 6, CC_HOTBAR_Y, CC_PCIE1_X, CC_PCIE_Y))
                .thenScreenshot(SETTLE, "05-crafting-computer-assembled")
                .then(0, () -> ctx.clickGui(CC_POWER_X, CC_POWER_Y))
                .thenWaitUntilServer(level -> cc(ctx, level).isRunning(), SCREEN_WAIT,
                        "the Crafting Computer to power on from its GUI", level -> "running=" + cc(ctx, level).isRunning())
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);

        // ---- 6. Frames 95 from a CD through the firmware on the Crafting Computer's monitor, then reboot.
        ctx.thenGive(0, new ItemStack(ComputingModule.CD_DRIVE.get()), new ItemStack(ComputingModule.MONITOR.get()),
                        installer(new ItemStack(ComputingModule.CD_ROM.get()), MediaKind.OS_INSTALL, FRAMES_95))
                .thenTeleport(SETTLE, new BlockPos(9, 2, 2), Direction.WEST)
                .then(SETTLE, () -> ctx.selectHotbar(0))
                .thenPlace(1, CC_CD_DRIVE)
                .then(2, () -> ctx.selectHotbar(2))
                .thenRightClick(SETTLE, CC_CD_DRIVE)
                .thenTeleport(SETTLE, new BlockPos(10, 2, 2), Direction.WEST)
                .then(SETTLE, () -> ctx.selectHotbar(1))
                .thenPlace(1, CC_MONITOR)
                .thenWaitUntilServer(level -> reader(ctx, level, CC_CD_DRIVE).ownerPos() != null
                                && !reader(ctx, level, CC_CD_DRIVE).mediaSlot().getStackInSlot(0).isEmpty(),
                        SCREEN_WAIT, "the CD drive to link to the Crafting Computer and hold the Frames 95 disc",
                        level -> "owner=" + reader(ctx, level, CC_CD_DRIVE).ownerPos())
                .then(2, () -> ctx.selectHotbar(8))
                .thenRightClick(SETTLE, CC_MONITOR)
                .thenAwaitScreen(FirmwareScreen.class, SCREEN_WAIT)
                .thenScreenshot(SETTLE, "06-crafting-computer-firmware")
                .then(0, () -> {
                    final int[] install = ctx.screen(FirmwareScreen.class).installButtonCenter();
                    ctx.click(install[0], install[1]);
                })
                .thenWaitUntilServer(level -> cc(ctx, level).hasOs(), SCREEN_WAIT,
                        "the firmware to install Frames 95 from the linked CD drive", level -> "hasOs=" + cc(ctx, level).hasOs())
                .thenAwaitNoScreen(SCREEN_WAIT)
                .thenTeleport(SETTLE, new BlockPos(7, 2, 0), Direction.SOUTH)
                .thenRightClick(SETTLE, CRAFTING_COMPUTER)
                .thenAwaitScreen(CraftingComputerScreen.class, SCREEN_WAIT)
                .then(2, () -> ctx.clickGui(CC_POWER_X, CC_POWER_Y))
                .thenWaitUntilServer(level -> !cc(ctx, level).isRunning(), SCREEN_WAIT, "the Crafting Computer to power off",
                        level -> "running=" + cc(ctx, level).isRunning())
                .then(SETTLE, () -> ctx.clickGui(CC_POWER_X, CC_POWER_Y))
                .thenWaitUntilServer(level -> cc(ctx, level).isRunning(), SCREEN_WAIT, "the Crafting Computer to boot Frames 95",
                        level -> "running=" + cc(ctx, level).isRunning())
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);

        // ---- 7. Swap the disc for the Crafting Manager installer and install it from This PC.
        ctx.thenGive(0, ItemStack.EMPTY)
                .thenTeleport(SETTLE, new BlockPos(9, 2, 2), Direction.WEST)
                .then(SETTLE, () -> ctx.selectHotbar(0))
                .thenSneakClick(1, CC_CD_DRIVE, Direction.UP)
                .thenWaitUntilServer(level -> reader(ctx, level, CC_CD_DRIVE).mediaSlot().getStackInSlot(0).isEmpty(),
                        SCREEN_WAIT, "sneak-clicking the CD drive to eject the disc",
                        level -> "media=" + reader(ctx, level, CC_CD_DRIVE).mediaSlot().getStackInSlot(0))
                .thenGive(0, installer(new ItemStack(ComputingModule.CD_ROM.get()), MediaKind.PROGRAM_INSTALL, Programs.CRAFTING_MANAGER))
                .then(SETTLE, () -> ctx.selectHotbar(0))
                .thenRightClick(1, CC_CD_DRIVE)
                .thenWaitUntilServer(level -> !reader(ctx, level, CC_CD_DRIVE).mediaSlot().getStackInSlot(0).isEmpty(),
                        SCREEN_WAIT, "the installer disc to sit in the CD drive", level -> "")
                .thenTeleport(SETTLE, new BlockPos(10, 2, 2), Direction.WEST)
                .then(SETTLE, () -> ctx.selectHotbar(8))
                .thenRightClick(1, CC_MONITOR)
                .thenAwaitScreen(DesktopScreen.class, SCREEN_WAIT)
                .thenScreenshot(SETTLE, "07-frames-desktop")
                .then(0, () -> launch(ctx, "This PC"))
                .thenWaitUntil(() -> app(ctx, "This PC", ThisPcApp.class) != null
                                && firstInstallable(app(ctx, "This PC", ThisPcApp.class)) >= 0,
                        SCREEN_WAIT, "This PC to list the installer disc with an Install button")
                .thenScreenshot(2, "07-this-pc")
                .then(0, () -> {
                    final ThisPcApp app = app(ctx, "This PC", ThisPcApp.class);
                    ctx.clickDesktop(appPoint(ctx, "This PC", app.installButtonCenter(firstInstallable(app))));
                })
                .thenWaitUntilServer(level -> cc(ctx, level).console().isInstalled(Programs.CRAFTING_MANAGER.toString()),
                        SCREEN_WAIT, "This PC to install the Crafting Manager from the disc",
                        level -> "installed=" + cc(ctx, level).console().installed())
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);

        // ---- 8. The machine: switch, crafting cables, a furnace hung off the run with both buses, and coal.
        ctx.thenGive(0, new ItemStack(ComputingModule.CRAFTING_CABLE.get(), 8), new ItemStack(ComputingModule.CRAFTING_SWITCH.get()),
                        new ItemStack(Items.FURNACE), new ItemStack(ComputingModule.INPUT_BUS_ITEM.get()),
                        new ItemStack(ComputingModule.RECEIVING_BUS_ITEM.get()), new ItemStack(Items.COAL, 8))
                .thenTeleport(SETTLE, new BlockPos(9, 2, 4), Direction.WEST)
                .then(SETTLE, () -> ctx.selectHotbar(0))
                .thenPlace(1, CRAFTING_CABLE)
                .then(2, () -> ctx.selectHotbar(1))
                .thenPlace(1, SWITCH)
                .then(2, () -> ctx.selectHotbar(0))
                .thenPlace(1, CABLE_BELOW_FURNACE)
                .thenPlace(2, CABLE_RUN_1)
                .thenPlace(2, CABLE_RUN_2)
                .thenPlace(2, CABLE_RUN_3)
                .thenPlaceAgainst(2, CABLE_ABOVE_FURNACE, Direction.NORTH)
                .then(2, () -> ctx.selectHotbar(2))
                .thenPlace(1, FURNACE)
                .then(2, () -> ctx.selectHotbar(3))
                .thenSneakClick(1, CABLE_ABOVE_FURNACE, Direction.DOWN)
                .then(2, () -> ctx.selectHotbar(4))
                .thenSneakClick(1, CABLE_BELOW_FURNACE, Direction.UP)
                .thenScreenshot(SETTLE, "08-machine-run")
                .thenWaitUntilServer(level -> switchDeclaresFurnace(ctx, level), SCREEN_WAIT,
                        "the switch to discover the furnace over the cable run through its buses",
                        level -> "declared=" + sw(ctx, level).declaredMachines() + " furnace=" + level.getBlockState(abs(ctx, FURNACE)))
                .then(2, () -> ctx.selectHotbar(8))
                .thenRightClick(1, FURNACE)
                .thenAwaitScreen(AbstractFurnaceScreen.class, SCREEN_WAIT)
                .then(2, () -> slotFromHotbar(ctx, 5, FURNACE_HOTBAR_Y, FURNACE_FUEL_X, FURNACE_FUEL_Y))
                .then(2, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT)
                .thenWaitUntilServer(level -> level.getBlockEntity(abs(ctx, FURNACE)) instanceof FurnaceBlockEntity f && f.getItem(1).is(Items.COAL),
                        SCREEN_WAIT, "the coal to sit in the furnace's fuel slot", level -> "");

        // ---- 9. Author a processing pattern (raw iron -> ingot, furnace, 600 ticks), a bench pattern (ingot ->
        //         nuggets) and a multi-stage pattern (processing then bench) on one floppy at the encoder.
        ctx.thenGive(0, new ItemStack(ComputingModule.PATTERN_ENCODER.get()), new ItemStack(ComputingModule.FLOPPY_DISK.get()),
                        new ItemStack(Items.RAW_IRON, 8), new ItemStack(Items.IRON_INGOT, 8))
                .thenTeleport(SETTLE, new BlockPos(10, 2, 7), Direction.NORTH)
                .then(SETTLE, () -> ctx.selectHotbar(0))
                .thenPlace(1, ENCODER)
                .then(2, () -> ctx.selectHotbar(8))
                .thenRightClick(SETTLE, ENCODER)
                .thenAwaitScreen(PatternEncoderScreen.class, SCREEN_WAIT)
                .then(2, () -> slotFromHotbar(ctx, 1, ENCODER_HOTBAR_Y, MEDIA_SLOT_X, MEDIA_SLOT_Y))
                // Processing.
                .then(2, () -> ctx.clickGui(TAB_PROCESSING_X, TAB_ROW_Y))
                .then(2, () -> ctx.clickGui(hotbarX(2), ENCODER_HOTBAR_Y))
                .then(2, () -> ctx.clickGui(INPUT_CELL_X, PROC_CELL_Y))
                .then(2, () -> ctx.clickGui(hotbarX(2), ENCODER_HOTBAR_Y))
                .then(2, () -> ctx.clickGui(hotbarX(3), ENCODER_HOTBAR_Y))
                .then(2, () -> ctx.clickGui(OUTPUT_CELL_X, PROC_CELL_Y))
                .then(2, () -> ctx.clickGui(hotbarX(3), ENCODER_HOTBAR_Y))
                .then(2, () -> ctx.clickGui(MACHINE_BTN_X, MACHINE_BTN_Y))
                .then(1, () -> ctx.type("furnace"))
                .then(2, () -> {
                    final PatternEncoderScreen screen = ctx.screen(PatternEncoderScreen.class);
                    final int row = screen.machinePickerRows().indexOf("minecraft:furnace");
                    ctx.assertTrue(row >= 0, "the picker must list minecraft:furnace; rows=" + screen.machinePickerRows());
                    ctx.clickGui(screen.machinePickerRowX(), screen.machinePickerRowY(row - screen.machinePickerScroll()));
                })
                .then(2, () -> {
                    ctx.clickGui(PatternEncoderScreen.timeoutBoxX(), PatternEncoderScreen.timeoutBoxY());
                    for (int i = 0; i < 4; i++) {
                        ctx.key(GLFW.GLFW_KEY_BACKSPACE);
                    }
                    ctx.type("600");
                })
                .thenScreenshot(SETTLE, "09-processing-pattern")
                .then(0, () -> ctx.clickGui(WRITE_X, WRITE_Y))
                .thenWaitUntilServer(level -> craftFiles(ctx, level) == 1, SCREEN_WAIT, "the processing .craft on the floppy",
                        level -> "files=" + craftFiles(ctx, level))
                // Bench: one ingot in the grid resolves to nine nuggets.
                .then(2, () -> ctx.clickGui(TAB_CRAFTING_X, TAB_ROW_Y))
                .then(2, () -> ctx.clickGui(hotbarX(3), ENCODER_HOTBAR_Y))
                .then(2, () -> ctx.clickGui(GHOST_CELL_X, GHOST_CELL_Y))
                .then(2, () -> ctx.clickGui(hotbarX(3), ENCODER_HOTBAR_Y))
                .thenScreenshot(SETTLE, "09-bench-pattern")
                .then(0, () -> ctx.clickGui(WRITE_X, WRITE_Y))
                .thenWaitUntilServer(level -> craftFiles(ctx, level) == 2, SCREEN_WAIT, "the bench .craft on the floppy",
                        level -> "files=" + craftFiles(ctx, level))
                // Multi-stage: pick the processing file, then the bench file, from the stage picker.
                .then(2, () -> ctx.clickGui(TAB_MULTI_X, TAB_ROW_Y))
                .then(2, () -> ctx.clickGui(PatternEncoderScreen.addStageButtonX(), PatternEncoderScreen.addStageButtonY()))
                .thenAssert(2, () -> ctx.screen(PatternEncoderScreen.class).isStagePickerOpen(), "Add stage opens the picker")
                .then(0, () -> pickStage(ctx, "iron_ingot"))
                .then(SETTLE, () -> ctx.clickGui(PatternEncoderScreen.addStageButtonX(), PatternEncoderScreen.addStageButtonY()))
                .then(2, () -> pickStage(ctx, "iron_nugget"))
                .thenScreenshot(SETTLE, "09-multi-stage-pattern")
                .then(0, () -> ctx.clickGui(WRITE_X, WRITE_Y))
                .thenWaitUntilServer(level -> craftFiles(ctx, level) == 3, SCREEN_WAIT, "the multi-stage .craft on the floppy",
                        level -> "files=" + craftFiles(ctx, level))
                // Take the floppy back into the hotbar.
                .then(2, () -> ctx.clickGui(MEDIA_SLOT_X, MEDIA_SLOT_Y))
                .then(2, () -> ctx.clickGui(hotbarX(1), ENCODER_HOTBAR_Y))
                .then(2, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);

        // ---- 10. A floppy drive on the Crafting Computer, the floppy in it, and both machine patterns loaded.
        ctx.thenServer(0, level -> ctx.give(0, new ItemStack(ComputingModule.FLOPPY_DRIVE.get())))
                .thenTeleport(SETTLE, new BlockPos(7, 2, -1), Direction.SOUTH)
                .then(SETTLE, () -> ctx.selectHotbar(0))
                .thenPlace(1, CC_FLOPPY_DRIVE)
                .then(2, () -> ctx.selectHotbar(1))
                .thenRightClick(SETTLE, CC_FLOPPY_DRIVE)
                .thenWaitUntilServer(level -> reader(ctx, level, CC_FLOPPY_DRIVE).ownerPos() != null
                                && !reader(ctx, level, CC_FLOPPY_DRIVE).mediaSlot().getStackInSlot(0).isEmpty(),
                        SCREEN_WAIT, "the floppy with the patterns to sit in a drive linked to the Crafting Computer",
                        level -> "owner=" + reader(ctx, level, CC_FLOPPY_DRIVE).ownerPos())
                .thenTeleport(SETTLE, new BlockPos(10, 2, 2), Direction.WEST)
                .then(SETTLE, () -> ctx.selectHotbar(8))
                .thenRightClick(1, CC_MONITOR)
                .thenAwaitScreen(DesktopScreen.class, SCREEN_WAIT)
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).launcherLabels().contains("Crafting Mgr"), SCREEN_WAIT,
                        "the Crafting Manager to be listed on the Start menu")
                .then(0, () -> launch(ctx, "Crafting Mgr"))
                .thenWaitUntil(() -> app(ctx, "Crafting Mgr", CraftingManagerApp.class) != null
                                && app(ctx, "Crafting Mgr", CraftingManagerApp.class).mediaFiles().size() == 3,
                        SCREEN_WAIT, "the Crafting Manager to list the three files")
                .then(0, () -> {
                    final CraftingManagerApp app = app(ctx, "Crafting Mgr", CraftingManagerApp.class);
                    final List<String> files = app.mediaFiles();
                    for (int i = 0; i < files.size(); i++) {
                        if (!files.get(i).startsWith("iron_nugget")) { // the bench craft rides inside the multi-stage
                            ctx.clickDesktop(app.mediaRowCenter(i));
                        }
                    }
                })
                .then(2, () -> ctx.clickDesktop(app(ctx, "Crafting Mgr", CraftingManagerApp.class).actionButtonCenter(0)))
                .thenWaitUntil(() -> app(ctx, "Crafting Mgr", CraftingManagerApp.class).romNames().size() == 2, SCREEN_WAIT,
                        "the processing and multi-stage recipes to appear in the ROM")
                .thenScreenshot(2, "10-crafting-manager-loaded")
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);

        // ---- 11. Raw iron into the network through the Network Interactor, then request two ingots: real
        //          smelting, with a save and reload while the furnace works.
        ctx.thenGive(0, new ItemStack(Items.RAW_IRON, 4))
                .then(SETTLE, () -> ctx.selectHotbar(8))
                .thenRightClick(1, CC_MONITOR)
                .thenAwaitScreen(DesktopScreen.class, SCREEN_WAIT)
                .then(2, () -> launch(ctx, "Network"))
                .thenWaitUntil(() -> app(ctx, "Network", NetworkInteractorApp.class) != null, SCREEN_WAIT, "the Network Interactor window")
                .then(2, () -> ctx.clickDesktop(appPoint(ctx, "Network", app(ctx, "Network", NetworkInteractorApp.class).inventoryBandSlotCenter(27))))
                .then(2, () -> ctx.clickDesktop(appPoint(ctx, "Network", app(ctx, "Network", NetworkInteractorApp.class).gridFirstCellCenter())))
                .thenWaitUntilServer(level -> stored(ctx, level, Items.RAW_IRON) >= 4, SCREEN_WAIT,
                        "the raw iron to be deposited into network storage", level -> "rawIron=" + stored(ctx, level, Items.RAW_IRON))
                .then(2, () -> ctx.clickDesktop(appPoint(ctx, "Network", app(ctx, "Network", NetworkInteractorApp.class).craftingTabCenter())))
                .thenWaitUntil(() -> app(ctx, "Network", NetworkInteractorApp.class).craftableNames().contains("Iron Ingot"), SCREEN_WAIT,
                        "the furnace recipe in the Crafting tab")
                .then(0, () -> requestCraft(ctx, "Iron Ingot", 1))
                .thenWaitUntilServer(level -> level.getBlockEntity(abs(ctx, FURNACE)) instanceof FurnaceBlockEntity f && f.getItem(0).is(Items.RAW_IRON),
                        200, "the Input Bus to feed raw iron into the furnace", level -> "ops=" + mainframe(ctx, level).activeOperationRecords())
                .thenScreenshot(2, "11-smelting")
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT)
                .thenSaveAndReload(SETTLE)
                .thenWaitUntilServer(level -> stored(ctx, level, Items.IRON_INGOT) >= 2, 1200,
                        "two smelted ingots to reach network storage after the reload",
                        level -> "ingots=" + stored(ctx, level, Items.IRON_INGOT) + " ops=" + mainframe(ctx, level).activeOperationRecords()
                                + " recent=" + mainframe(ctx, level).recentOperations());

        // ---- 12. The multi-stage pipeline: nine nuggets = smelt one ingot, then the bench stage.
        ctx.thenTeleport(SETTLE, new BlockPos(10, 2, 2), Direction.WEST)
                .thenRightClick(SETTLE, CC_MONITOR)
                .thenAwaitScreen(DesktopScreen.class, SCREEN_WAIT)
                .thenWaitUntil(() -> app(ctx, "Network", NetworkInteractorApp.class) != null
                                || ctx.screen(DesktopScreen.class).launcherLabels().contains("Network"), SCREEN_WAIT, "the desktop")
                .then(2, () -> {
                    if (app(ctx, "Network", NetworkInteractorApp.class) == null) {
                        launch(ctx, "Network");
                    }
                })
                .thenWaitUntil(() -> app(ctx, "Network", NetworkInteractorApp.class) != null, SCREEN_WAIT, "the Network Interactor window")
                .then(2, () -> ctx.clickDesktop(appPoint(ctx, "Network", app(ctx, "Network", NetworkInteractorApp.class).craftingTabCenter())))
                .thenWaitUntil(() -> app(ctx, "Network", NetworkInteractorApp.class).craftableNames().contains("Iron Nugget"), SCREEN_WAIT,
                        "the multi-stage recipe's nugget in the Crafting tab")
                .then(0, () -> requestCraft(ctx, "Iron Nugget", 8))
                .thenWaitUntilServer(level -> stored(ctx, level, Items.IRON_NUGGET) >= 9, 1200,
                        "nine nuggets from the multi-stage pipeline to reach network storage",
                        level -> "nuggets=" + stored(ctx, level, Items.IRON_NUGGET) + " ops=" + mainframe(ctx, level).activeOperationRecords()
                                + " recent=" + mainframe(ctx, level).recentOperations())
                .thenScreenshot(2, "12-multi-stage-done")
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);
    }

    // --- helpers ---

    /** Picks up the stack from hotbar slot {@code slot} and drops it into the slot at ({@code x}, {@code y}). */
    private static void slotFromHotbar(final ClientTestContext ctx, final int slot, final int hotbarY, final int x, final int y) {
        ctx.clickGui(hotbarX(slot), hotbarY);
        ctx.clickGui(x, y);
    }

    private static void launch(final ClientTestContext ctx, final String label) {
        final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
        ctx.click(desktop.startButtonX(), desktop.startButtonY());
        final int item = desktop.launcherLabels().indexOf(label);
        ctx.assertTrue(item >= 0, "the Start menu must list " + label + "; got " + desktop.launcherLabels());
        ctx.click(desktop.startMenuItemX(), desktop.startMenuItemY(item));
    }

    private static <T> T app(final ClientTestContext ctx, final String label, final Class<T> type) {
        if (!(ctx.mc().screen instanceof DesktopScreen desktop)) {
            return null;
        }
        final DesktopWindow window = desktop.windowFor(label);
        return window != null && type.isInstance(window.app()) ? type.cast(window.app()) : null;
    }

    /** Converts an app content-local point into desktop coordinates. */
    private static int[] appPoint(final ClientTestContext ctx, final String label, final int[] local) {
        final DesktopWindow window = ctx.screen(DesktopScreen.class).windowFor(label);
        if (window == null) {
            throw new ClientTestFailure("the " + label + " window is gone");
        }
        return new int[]{window.x() + 4 + local[0], window.y() + 18 + local[1]};
    }

    private static int firstInstallable(final ThisPcApp app) {
        for (int i = 0; i < 32; i++) {
            if (app.isInstallable(i)) {
                return i;
            }
        }
        return -1;
    }

    private static void pickStage(final ClientTestContext ctx, final String fileStartsWith) {
        final PatternEncoderScreen screen = ctx.screen(PatternEncoderScreen.class);
        final List<String> files = screen.stagePickerFiles();
        for (int i = 0; i < files.size(); i++) {
            if (files.get(i).startsWith(fileStartsWith)) {
                ctx.clickGui(screen.stagePickerRowX(), screen.stagePickerRowY(i));
                return;
            }
        }
        throw new ClientTestFailure("no " + fileStartsWith + "* file in the stage picker; files=" + files);
    }

    /** Opens the craft popup for {@code name} on the Crafting tab, adds {@code plusOnes} and submits. */
    private static void requestCraft(final ClientTestContext ctx, final String name, final int plusOnes) {
        final NetworkInteractorApp app = app(ctx, "Network", NetworkInteractorApp.class);
        final int index = app.craftableNames().indexOf(name);
        ctx.assertTrue(index >= 0, "the Crafting tab must list " + name + "; got " + app.craftableNames());
        ctx.clickDesktop(appPoint(ctx, "Network", app.craftableCellCenter(index)));
        ctx.assertTrue(app.isCraftPopupOpen(), "clicking " + name + " opens the request popup");
        for (int i = 0; i < plusOnes; i++) {
            ctx.clickDesktop(appPoint(ctx, "Network", app.craftPopupStepCenter(2)));
        }
        ctx.clickDesktop(appPoint(ctx, "Network", app.craftPopupSubmitCenter()));
    }

    private static BlockPos abs(final ClientTestContext ctx, final BlockPos relative) {
        return ctx.abs(relative);
    }

    private static MainframeBlockEntity mainframe(final ClientTestContext ctx, final ServerLevel level) {
        return TestWorldBuilder.at(level, ctx.origin()).blockEntity(MAINFRAME, MainframeBlockEntity.class);
    }

    private static CraftingComputerBlockEntity cc(final ClientTestContext ctx, final ServerLevel level) {
        return TestWorldBuilder.at(level, ctx.origin()).blockEntity(CRAFTING_COMPUTER, CraftingComputerBlockEntity.class);
    }

    private static ServerRackBlockEntity rack(final ClientTestContext ctx, final ServerLevel level) {
        return level.getBlockEntity(abs(ctx, RACK)) instanceof ServerRackBlockEntity r ? r : null;
    }

    private static MediaReaderBlockEntity reader(final ClientTestContext ctx, final ServerLevel level, final BlockPos at) {
        return TestWorldBuilder.at(level, ctx.origin()).blockEntity(at, MediaReaderBlockEntity.class);
    }

    private static dev.jsc.jscomputronics.module.computing.blockentity.CraftingSwitchBlockEntity sw(
            final ClientTestContext ctx, final ServerLevel level) {
        return TestWorldBuilder.at(level, ctx.origin()).blockEntity(SWITCH,
                dev.jsc.jscomputronics.module.computing.blockentity.CraftingSwitchBlockEntity.class);
    }

    private static boolean switchDeclaresFurnace(final ClientTestContext ctx, final ServerLevel level) {
        return level.getBlockEntity(abs(ctx, SWITCH)) instanceof dev.jsc.jscomputronics.module.computing.blockentity
                .CraftingSwitchBlockEntity s
                && s.declaredMachines().stream().anyMatch(m -> m.machineType().equals("minecraft:furnace"));
    }

    private static int craftFiles(final ClientTestContext ctx, final ServerLevel level) {
        final ItemStack media = TestWorldBuilder.at(level, ctx.origin())
                .blockEntity(ENCODER, dev.jsc.jscomputronics.module.computing.blockentity.PatternEncoderBlockEntity.class)
                .media().getStackInSlot(0);
        int n = 0;
        for (final DiskFilesystem.FileEntry e : DiskFilesystem.list(media, "", FilesystemKind.HIERARCHICAL)) {
            if (e.type() == FileType.CRAFT) {
                n++;
            }
        }
        return n;
    }

    private static long stored(final ClientTestContext ctx, final ServerLevel level, final net.minecraft.world.item.Item item) {
        final MainframeBlockEntity mf = mainframe(ctx, level);
        return mf.networkUuid() == null ? -1 : NetworkStorage.of(level, mf.networkUuid()).count(StorageKey.of(item));
    }
}
