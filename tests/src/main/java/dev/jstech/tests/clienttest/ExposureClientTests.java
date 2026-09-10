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
import dev.jstech.computers.client.os.ExposureApp;
import dev.jstech.computers.client.os.IDesktopApp;
import dev.jstech.computers.os.fs.DiskFilesystem;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.lwjgl.glfw.GLFW;

/**
 * Exposure as a player uses it: a file made from the menu, written, saved, and the folder's table of
 * problems saying which programs are broken after a second, broken file is saved beside the first.
 */
public final class ExposureClientTests {

    private ExposureClientTests() {
    }

    private static final int SETTLE = 4;
    private static final int SCREEN_WAIT = 80;
    private static final int BOOT_WAIT = 400;

    private static final BlockPos COMPUTER = new BlockPos(5, 2, 2);
    private static final BlockPos MONITOR = new BlockPos(6, 2, 2);
    private static final BlockPos PLAYER_AT_MONITOR = new BlockPos(8, 2, 2);
    private static final ResourceLocation FRAMES_XP =
            ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, "frames_xp");
    private static final String LAUNCHER = "Exposure";

    private static ResourceLocation program(final String path) {
        return ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, path);
    }

    private static ExposureApp exposure(final ClientTestContext ctx) {
        final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
        final var window = desktop == null ? null : desktop.windowFor(LAUNCHER);
        final IDesktopApp app = window == null ? null : window.app();
        return app instanceof ExposureApp e ? e : null;
    }

    private static void launch(final ClientTestContext ctx, final String label) {
        final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
        ctx.click(desktop.startButtonX(), desktop.startButtonY());
        final int item = desktop.launcherLabels().indexOf(label);
        ctx.click(desktop.startMenuItemX(item), desktop.startMenuItemY(item));
    }

    private static String diskText(final ClientTestContext ctx, final ServerLevel level, final String path) {
        return DiskFilesystem.read(TestWorldBuilder.at(level, ctx.origin())
                .blockEntity(COMPUTER, CraftingComputerBlockEntity.class).systemDisk(), path).orElse("");
    }

    @ClientTest(timeoutTicks = 2400)
    public static void exposure_makesSavesAndSurveysTheFolder(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    final CraftingComputerBlockEntity computer = world.placeRunningCraftingComputer(COMPUTER);
                    computer.installOs(FRAMES_XP);
                    for (final String id : new String[] {"exposure", "cannonc", "cannonrt"}) {
                        computer.console().install(program(id).toString());
                    }
                    world.placeMonitor(MONITOR, Direction.EAST);
                })
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).launcherLabels().contains(LAUNCHER),
                        SCREEN_WAIT, "Exposure to be listed in Start")
                .then(0, () -> launch(ctx, LAUNCHER))
                .thenWaitUntil(() -> exposure(ctx) != null, SCREEN_WAIT, "Exposure to open")
                .thenScreenshot(2, "exposure-open")
                // The File menu drops down where it is clicked, and its first entry asks for a name.
                .then(SETTLE, () -> ctx.clickDesktop(exposure(ctx).menuTitlePoint("File")))
                .thenWaitUntil(() -> exposure(ctx).menuOpen(), SCREEN_WAIT, "the File menu to drop down")
                .thenScreenshot(2, "exposure-file-menu")
                .then(SETTLE, () -> ctx.clickDesktop(exposure(ctx).menuItemPoint(0)))
                .thenWaitUntil(() -> exposure(ctx).asking(), SCREEN_WAIT, "New File to ask for a name")
                .then(SETTLE, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenWaitUntil(() -> !exposure(ctx).asking(), SCREEN_WAIT, "Escape to put the question away")
                // File > New File, answered the way the small window is answered.
                .then(SETTLE, () -> exposure(ctx).createFile("hello.can"))
                .thenWaitUntil(() -> exposure(ctx).openFile().endsWith("hello.can"), SCREEN_WAIT, "the new file to be open")
                .then(SETTLE, () -> ctx.type("using System.IO.*; namespace Hello; "
                        + "class Hello { static void Main() { Console.PrintLine(\"hi\"); } }"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_S, GLFW.GLFW_MOD_CONTROL))
                .thenWaitUntilServer(level -> diskText(ctx, level, "progs/hello.can").contains("class Hello"),
                        SCREEN_WAIT, "the file to be on the disk", level -> diskText(ctx, level, "progs/hello.can"))
                .thenWaitUntil(() -> exposure(ctx).fileNames().contains("hello.can") && exposure(ctx).problemCount() == 0,
                        SCREEN_WAIT, "the folder to list the file with nothing wrong")
                .thenScreenshot(2, "exposure-saved")
                // A second, broken file: the table blames it, and only it.
                .then(SETTLE, () -> exposure(ctx).createFile("broken.can"))
                .thenWaitUntil(() -> exposure(ctx).openFile().endsWith("broken.can"), SCREEN_WAIT, "the second file to be open")
                .then(SETTLE, () -> ctx.type("class Broken { int x = ; }"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_S, GLFW.GLFW_MOD_CONTROL))
                .thenWaitUntil(() -> exposure(ctx).problemCount() > 0, SCREEN_WAIT, "the table to list the broken program")
                .thenScreenshot(2, "exposure-problems")
                .thenAssert(0, () -> exposure(ctx).fileNames().contains("broken.can"), "the folder lists both files")
                // File > Open File goes through the system's file window; the row picked and Open opens it.
                .then(SETTLE, () -> exposure(ctx).showOpenFileDialog())
                .thenWaitUntil(() -> exposure(ctx).dialog().isOpen() && exposure(ctx).dialog().rowNames().contains("hello.can"),
                        SCREEN_WAIT, "the Open File window to list the folder's sources")
                .thenAssert(0, () -> ctx.screen(DesktopScreen.class).dialogWindowFor(LAUNCHER) != null
                                && ctx.screen(DesktopScreen.class).openWindowLabels().stream().filter(LAUNCHER::equals).count() == 1,
                        "the Open File window is a dialog of the editor, not a window the machine counts")
                .thenScreenshot(2, "open-file-dialog")
                .then(SETTLE, () -> ctx.clickDesktop(exposure(ctx).dialog().rowPoint("hello.can")))
                .then(SETTLE, () -> ctx.clickDesktop(exposure(ctx).dialog().primaryPoint()))
                .thenWaitUntil(() -> !exposure(ctx).dialog().isOpen() && exposure(ctx).openFile().endsWith("hello.can"),
                        SCREEN_WAIT, "Open to open the file it named");
    }
}
