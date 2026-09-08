/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.clienttest;

import dev.jstech.computers.JsComputers;
import dev.jstech.computers.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computers.client.os.DesktopScreen;
import dev.jstech.computers.client.os.DesktopWindow;
import dev.jstech.computers.client.os.FilesApp;
import dev.jstech.computers.client.os.FilesApps;
import dev.jstech.computers.operation.payload.RenameFilePayload;
import dev.jstech.computers.os.FilesystemKind;
import dev.jstech.computers.os.fs.DiskFilesystem;
import dev.jstech.computers.os.fs.FileType;
import dev.jstech.tests.testkit.TestWorldBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Two windows on one disk, seen from the player's side.
 *
 * <p>A machine has one disk, so two explorers on the same folder are two views of one thing, and what
 * one does the other shows. The explorer used to keep a single instance of itself as the one that gets
 * listings, so the second window went stale the moment the first did anything.
 */
public final class FilesSyncClientTests {

    private FilesSyncClientTests() {
    }

    private static final int SETTLE = 4;
    private static final int SCREEN_WAIT = 80;
    private static final int BOOT_WAIT = 400;

    private static final BlockPos COMPUTER = new BlockPos(5, 2, 2);
    private static final BlockPos MONITOR = new BlockPos(6, 2, 2);
    private static final BlockPos PLAYER_AT_MONITOR = new BlockPos(8, 2, 2);
    private static final String FOLDER = "progs";

    private static final ResourceLocation FRAMES_XP =
            ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, "frames_xp");

    /** The explorers open on the desktop, in the order they were opened. */
    private static List<FilesApp> explorers(final ClientTestContext ctx) {
        final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
        if (desktop == null) {
            return List.of();
        }
        return desktop.windowsFor("Files").stream()
                .map(DesktopWindow::app)
                .filter(FilesApp.class::isInstance)
                .map(FilesApp.class::cast)
                .toList();
    }

    private static boolean bothList(final ClientTestContext ctx, final String name) {
        final List<FilesApp> open = explorers(ctx);
        return open.size() == 2 && open.get(0).names().contains(name) && open.get(1).names().contains(name);
    }

    /** A file made in one explorer shows in the other, and so does its new name and kind. */
    @ClientTest(timeoutTicks = 2400)
    public static void twoExplorers_onOneFolderShowTheSameDisk(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    final CraftingComputerBlockEntity computer = world.placeRunningCraftingComputer(COMPUTER);
                    computer.installOs(FRAMES_XP);
                    DiskFilesystem.write(computer.systemDisk(), FOLDER + "/first.txt", FileType.TXT, "one",
                            1000L, FilesystemKind.HIERARCHICAL);
                    world.placeMonitor(MONITOR, Direction.EAST);
                })
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                // Two explorers on the same folder, the way a player ends up with them.
                .then(SETTLE, () -> {
                    DesktopScreen.requestOpenFiles(FOLDER);
                    DesktopScreen.requestOpenFiles(FOLDER);
                })
                .thenWaitUntil(() -> bothList(ctx, "first.txt"), SCREEN_WAIT, "both explorers to list the folder")
                .thenScreenshot(2, "two-explorers")
                // A file made in the first one.
                .then(SETTLE, () -> explorers(ctx).get(0).newFile(FileType.CAN))
                .thenWaitUntil(() -> bothList(ctx, "New File.can"), SCREEN_WAIT,
                        "the file made in one explorer to show in the other")
                .thenScreenshot(2, "file-in-both")
                /*
                 * Renamed across kinds, the way the rename field now allows: the other explorer shows the
                 * new name, and the machine's disk says the file is a program now.
                 */
                .then(SETTLE, () -> {
                    final var host = ctx.abs(COMPUTER);
                    PacketDistributor.sendToServer(new RenameFilePayload(host, FOLDER + "/first.txt",
                            FOLDER + "/first.can"));
                    FilesApps.diskChanged();
                })
                .thenWaitUntil(() -> bothList(ctx, "first.can"), SCREEN_WAIT,
                        "the new name to show in both explorers")
                .thenAssert(0, () -> !explorers(ctx).get(1).names().contains("first.txt"),
                        "the old name is gone from the other explorer")
                .thenServer(SETTLE, level -> {
                    final var disk = TestWorldBuilder.at(level, ctx.origin())
                            .blockEntity(COMPUTER, CraftingComputerBlockEntity.class).systemDisk();
                    FileType kind = null;
                    for (final DiskFilesystem.FileEntry entry
                            : DiskFilesystem.list(disk, FOLDER, FilesystemKind.HIERARCHICAL)) {
                        if (entry.path().equals(FOLDER + "/first.can")) {
                            kind = entry.type();
                        }
                    }
                    ctx.assertTrue(kind == FileType.CAN, "the renamed file is a program on the disk, was " + kind);
                })
                .thenScreenshot(2, "renamed-in-both");
    }
}
