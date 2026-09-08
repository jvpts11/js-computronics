/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.clienttest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.JsComputronics;
import dev.jstech.computronics.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computronics.client.os.DesktopScreen;
import dev.jstech.computronics.client.os.DesktopWindow;
import dev.jstech.computronics.client.os.EditorApp;
import dev.jstech.computronics.client.os.FilesApp;
import dev.jstech.computronics.os.media.MediaItem;
import dev.jstech.computronics.os.media.MediaKind;
import dev.jstech.computronics.os.media.MediaReaderBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * An install disc, read the way a player reads one: in the explorer, then in the Editor.
 *
 * <p>The disc's readme and licence are not stored anywhere; they are generated from what the disc
 * installs, the moment they are asked for. That the machine generates them is proved elsewhere. This is
 * about whether the text reaches the window the player opened it in, on each of the desktops that
 * draw that window differently.
 */
public final class InstallMediaClientTests {

    private InstallMediaClientTests() {
    }

    private static final int SETTLE = 4;
    private static final int SCREEN_WAIT = 80;
    private static final int BOOT_WAIT = 400;

    private static final BlockPos COMPUTER = new BlockPos(5, 2, 2);
    private static final BlockPos DRIVE = new BlockPos(5, 2, 3);
    private static final BlockPos MONITOR = new BlockPos(6, 2, 2);
    private static final BlockPos PLAYER_AT_MONITOR = new BlockPos(8, 2, 2);

    private static final ResourceLocation MINESWEEPER =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "minesweeper");

    private static ResourceLocation os(final String path) {
        return ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, path);
    }

    private static MediaReaderBlockEntity drive(final ClientTestContext ctx, final ServerLevel level) {
        if (level.getBlockEntity(ctx.abs(DRIVE)) instanceof MediaReaderBlockEntity be) {
            return be;
        }
        throw new ClientTestFailure("no floppy drive at " + ctx.abs(DRIVE));
    }

    /** The explorer's own name for the disc in that drive, and for a file on it. */
    private static String mediaDir(final ClientTestContext ctx) {
        return "media:" + ctx.abs(DRIVE).asLong();
    }

    /** The newest window of a program, since opening a second file opens a second Editor. */
    private static <T> T app(final ClientTestContext ctx, final String label, final Class<T> type) {
        final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
        if (desktop == null) {
            return null;
        }
        final List<DesktopWindow> windows = desktop.windowsFor(label);
        final DesktopWindow window = windows.isEmpty() ? null : windows.getLast();
        return window != null && type.isInstance(window.app()) ? type.cast(window.app()) : null;
    }

    @ClientTest(timeoutTicks = 2400)
    public static void readme_onFrames95OpensWithItsText(final ClientTestContext ctx) {
        readme(ctx, "frames_95");
    }

    @ClientTest(timeoutTicks = 2400)
    public static void readme_onFramesXpOpensWithItsText(final ClientTestContext ctx) {
        readme(ctx, "frames_xp");
    }

    @ClientTest(timeoutTicks = 2400)
    public static void readme_onFrames11OpensWithItsText(final ClientTestContext ctx) {
        readme(ctx, "frames_11");
    }

    /** The readme on a program's floppy opens in the Editor with its text, not as an empty page. */
    private static void readme(final ClientTestContext ctx, final String desktop) {
        ctx.thenBuild(0, world -> {
                    final CraftingComputerBlockEntity computer = world.placeRunningCraftingComputer(COMPUTER);
                    computer.installOs(os(desktop));
                    world.setBlock(DRIVE, ComputingModule.FLOPPY_DRIVE.get());
                    world.placeMonitor(MONITOR, Direction.EAST);
                })
                // A program's install floppy, seated in the drive beside the computer.
                .thenServer(SETTLE * 3, level -> {
                    final ItemStack floppy = new ItemStack(ComputingModule.FLOPPY_DISK.get());
                    MediaItem.setKind(floppy, MediaKind.PROGRAM_INSTALL);
                    MediaItem.setPayload(floppy, MINESWEEPER);
                    final MediaReaderBlockEntity reader = drive(ctx, level);
                    ctx.assertTrue(reader.insertMedia(floppy).isEmpty(), "the drive takes the floppy");
                    ctx.assertEquals(ctx.abs(COMPUTER), reader.ownerPos(),
                            "the drive is linked to the computer beside it");
                })
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .then(SETTLE, () -> DesktopScreen.requestOpenFiles(mediaDir(ctx)))
                .thenWaitUntil(() -> {
                            final FilesApp files = app(ctx, "Files", FilesApp.class);
                            return files != null && files.names().contains("README.TXT");
                        }, SCREEN_WAIT, "the explorer to list the floppy's readme")
                .thenScreenshot(2, "floppy-listed")
                // Opened the way a double-click opens it.
                .then(SETTLE, () -> DesktopScreen.requestOpenFile(mediaDir(ctx) + "/README.TXT"))
                .thenWaitUntil(() -> {
                            final EditorApp editor = app(ctx, "Editor", EditorApp.class);
                            return editor != null && editor.openFile().endsWith("README.TXT");
                        }, SCREEN_WAIT, "the Editor to open the readme")
                .thenWaitUntil(() -> app(ctx, "Editor", EditorApp.class).text().contains("Minesweeper"),
                        SCREEN_WAIT, "the readme's text to reach the Editor")
                .thenScreenshot(2, "readme-open")
                // The licence too, since it is the other file a player opens to see what they are getting.
                .then(SETTLE, () -> DesktopScreen.requestOpenFile(mediaDir(ctx) + "/LICENSE.TXT"))
                .thenWaitUntil(() -> {
                            final EditorApp editor = app(ctx, "Editor", EditorApp.class);
                            return editor != null && editor.openFile().endsWith("LICENSE.TXT")
                                    && editor.text().contains("licensed");
                        }, SCREEN_WAIT, "the licence's text to reach the Editor")
                .thenScreenshot(2, "license-open");
    }
}
