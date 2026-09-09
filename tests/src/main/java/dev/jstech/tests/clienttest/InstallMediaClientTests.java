/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.clienttest;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.JsComputers;
import dev.jstech.computers.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computers.client.os.DesktopScreen;
import dev.jstech.computers.client.os.DesktopWindow;
import dev.jstech.computers.client.os.EditorApp;
import dev.jstech.computers.client.os.FilesApp;
import dev.jstech.computers.operation.payload.InstallFromMediaPayload;
import dev.jstech.computers.os.media.MediaItem;
import dev.jstech.computers.os.media.MediaKind;
import dev.jstech.computers.os.media.MediaReaderBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * An install disc, read the way a player reads one: in the explorer, then in the Editor.
 *
 * <p>The disc's readme and licence are not stored anywhere; they are generated from what the disc
 * installs, the moment they are asked for. That the machine generates them is proved elsewhere. This is
 * about whether the text reaches the window the player opened it in, on each of the desktops that
 * draw that window differently and for each kind of disc, since a floppy and a DVD speak different
 * dialects.
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

    private static ResourceLocation jsc(final String path) {
        return ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, path);
    }

    /** A disc in a drive: what it is, what it installs, and what its files are called in its dialect. */
    private record Disc(Block drive, Item medium, String program, String readme, String licence, String marker) {
    }

    /** A Vintage-era program on a floppy: uppercase 8.3 names. */
    private static final Disc MINESWEEPER_FLOPPY = new Disc(ComputingModule.FLOPPY_DRIVE.get(),
            ComputingModule.FLOPPY_DISK.get(), "minesweeper", "README.TXT", "LICENSE.TXT", "Minesweeper");

    /** A Standard-era program on a DVD: lowercase names and a sources folder, the disc reported empty. */
    private static final Disc VIRTUAL_STUDIO_DVD = new Disc(ComputingModule.DVD_DRIVE.get(),
            ComputingModule.DVD_ROM.get(), "virtual_studio", "readme.txt", "license.txt", "Virtual Studio");

    private static MediaReaderBlockEntity drive(final ClientTestContext ctx, final ServerLevel level) {
        if (level.getBlockEntity(ctx.abs(DRIVE)) instanceof MediaReaderBlockEntity be) {
            return be;
        }
        throw new ClientTestFailure("no media drive at " + ctx.abs(DRIVE));
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
        readme(ctx, "frames_95", MINESWEEPER_FLOPPY);
    }

    @ClientTest(timeoutTicks = 2400)
    public static void readme_onFramesXpOpensWithItsText(final ClientTestContext ctx) {
        readme(ctx, "frames_xp", MINESWEEPER_FLOPPY);
    }

    @ClientTest(timeoutTicks = 2400)
    public static void readme_onFrames11OpensWithItsText(final ClientTestContext ctx) {
        readme(ctx, "frames_11", MINESWEEPER_FLOPPY);
    }

    /** The exact disc and desktop the readme was reported empty on. */
    @ClientTest(timeoutTicks = 2400)
    public static void readme_ofAStandardDvdOnFrames11OpensWithItsText(final ClientTestContext ctx) {
        readme(ctx, "frames_11", VIRTUAL_STUDIO_DVD);
    }

    /**
     * Running the disc's setup opens a Setup window that copies for a while and then the program is
     * there: on the desktop as a launcher, and the window gone. Installing used to be a flag that
     * flipped the instant it was asked, with nothing on the screen either way.
     */
    @ClientTest(timeoutTicks = 2400)
    public static void setup_fromAFloppyOpensTheWindowAndInstallsInTime(final ClientTestContext ctx) {
        final Disc disc = MINESWEEPER_FLOPPY;
        ctx.thenBuild(0, world -> {
                    final CraftingComputerBlockEntity computer = world.placeRunningCraftingComputer(COMPUTER);
                    computer.installOs(jsc("frames_xp"));
                    world.setBlock(DRIVE, disc.drive());
                    world.placeMonitor(MONITOR, Direction.EAST);
                })
                .thenServer(SETTLE * 3, level -> {
                    final ItemStack medium = new ItemStack(disc.medium());
                    MediaItem.setKind(medium, MediaKind.PROGRAM_INSTALL);
                    MediaItem.setPayload(medium, jsc(disc.program()));
                    ctx.assertTrue(drive(ctx, level).insertMedia(medium).isEmpty(), "the drive takes the floppy");
                })
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenAssert(SETTLE, () -> !ctx.screen(DesktopScreen.class).launcherLabels().contains("Minesweeper"),
                        "the program is not there before setup")
                // What the disc's setup.exe and This PC's Install button both send.
                .then(SETTLE, () -> PacketDistributor.sendToServer(new InstallFromMediaPayload(
                        ctx.abs(COMPUTER), ctx.abs(DRIVE).asLong())))
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).windowFor("Setup") != null,
                        SCREEN_WAIT, "the Setup window to open")
                .thenScreenshot(2, "setup-copying")
                .thenAssert(SETTLE, () -> !ctx.screen(DesktopScreen.class).launcherLabels().contains("Minesweeper"),
                        "the program is not installed while Setup is still copying")
                // A 16 MB floppy takes its sixteen seconds; the window goes by itself once it is done.
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).launcherLabels().contains("Minesweeper"),
                        20 * 25, "the program to be installed when Setup finishes")
                .thenScreenshot(2, "setup-done")
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).windowFor("Setup") == null,
                        20 * 5, "the Setup window to close on its own");
    }

    /** The readme on a program's disc opens in the Editor with its text, not as an empty page. */
    private static void readme(final ClientTestContext ctx, final String desktop, final Disc disc) {
        ctx.thenBuild(0, world -> {
                    final CraftingComputerBlockEntity computer = world.placeRunningCraftingComputer(COMPUTER);
                    computer.installOs(jsc(desktop));
                    world.setBlock(DRIVE, disc.drive());
                    world.placeMonitor(MONITOR, Direction.EAST);
                })
                // The program's install disc, seated in the drive beside the computer.
                .thenServer(SETTLE * 3, level -> {
                    final ItemStack medium = new ItemStack(disc.medium());
                    MediaItem.setKind(medium, MediaKind.PROGRAM_INSTALL);
                    MediaItem.setPayload(medium, jsc(disc.program()));
                    final MediaReaderBlockEntity reader = drive(ctx, level);
                    ctx.assertTrue(reader.insertMedia(medium).isEmpty(), "the drive takes the disc");
                    ctx.assertEquals(ctx.abs(COMPUTER), reader.ownerPos(),
                            "the drive is linked to the computer beside it");
                })
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .then(SETTLE, () -> DesktopScreen.requestOpenFiles(mediaDir(ctx)))
                .thenWaitUntil(() -> {
                            final FilesApp files = app(ctx, "Files", FilesApp.class);
                            return files != null && files.names().contains(disc.readme());
                        }, SCREEN_WAIT, "the explorer to list the disc's readme")
                .thenScreenshot(2, "disc-listed")
                // Opened the way a double-click opens it.
                .then(SETTLE, () -> DesktopScreen.requestOpenFile(mediaDir(ctx) + "/" + disc.readme()))
                .thenWaitUntil(() -> {
                            final EditorApp editor = app(ctx, "Editor", EditorApp.class);
                            return editor != null && editor.openFile().endsWith(disc.readme());
                        }, SCREEN_WAIT, "the Editor to open the readme")
                .thenWaitUntil(() -> app(ctx, "Editor", EditorApp.class).text().contains(disc.marker()),
                        SCREEN_WAIT, "the readme's text to reach the Editor")
                .thenScreenshot(2, "readme-open")
                // The licence too, since it is the other file a player opens to see what they are getting.
                .then(SETTLE, () -> DesktopScreen.requestOpenFile(mediaDir(ctx) + "/" + disc.licence()))
                .thenWaitUntil(() -> {
                            final EditorApp editor = app(ctx, "Editor", EditorApp.class);
                            return editor != null && editor.openFile().endsWith(disc.licence())
                                    && editor.text().contains("licensed");
                        }, SCREEN_WAIT, "the licence's text to reach the Editor")
                .thenScreenshot(2, "license-open");
    }
}
