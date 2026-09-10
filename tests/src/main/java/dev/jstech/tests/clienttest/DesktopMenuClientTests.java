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
import dev.jstech.computers.client.os.FilesApp;
import dev.jstech.computers.client.os.IDesktopApp;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * The desktop's own menus and settings: the right button on the wallpaper, its New menu making a
 * folder, the explorer's address bar typed over, and a desktop drawn at a smaller scale still taking
 * clicks where things are drawn.
 */
public final class DesktopMenuClientTests {

    private DesktopMenuClientTests() {
    }

    private static final int SETTLE = 4;
    private static final int SCREEN_WAIT = 80;
    private static final int BOOT_WAIT = 400;

    private static final BlockPos COMPUTER = new BlockPos(5, 2, 2);
    private static final BlockPos MONITOR = new BlockPos(6, 2, 2);
    private static final BlockPos PLAYER_AT_MONITOR = new BlockPos(8, 2, 2);
    private static final ResourceLocation FRAMES_11 =
            ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, "frames_11");
    private static final ResourceLocation FRAMES_XP =
            ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, "frames_xp");
    /** A point on the wallpaper, well clear of the icon column on the left and of the taskbar. */
    private static final int[] WALLPAPER = {300, 60};

    private static FilesApp files(final ClientTestContext ctx) {
        final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
        final var window = desktop == null ? null : desktop.windowFor("Files");
        final IDesktopApp app = window == null ? null : window.app();
        return app instanceof FilesApp f ? f : null;
    }

    private static ClientTestContext atTheDesktop(final ClientTestContext ctx, final ResourceLocation os,
                                                  final int scale) {
        return ctx.thenBuild(0, world -> {
                    final CraftingComputerBlockEntity computer = world.placeRunningCraftingComputer(COMPUTER);
                    computer.installOs(os);
                    computer.console().settings().setGuiScale(scale);
                    world.placeMonitor(MONITOR, Direction.EAST);
                })
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenWaitUntil(() -> !ctx.screen(DesktopScreen.class).launcherLabels().isEmpty(),
                        SCREEN_WAIT, "the desktop to list its programs");
    }

    /** The right button on the wallpaper opens the menu; New opens beside it; Folder makes one. */
    @ClientTest(timeoutTicks = 2400)
    public static void desktop_rightButtonOffersNewAndMakesAFolder(final ClientTestContext ctx) {
        atTheDesktop(ctx, FRAMES_11, 0)
                .then(SETTLE, () -> ctx.rightClickDesktop(WALLPAPER))
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).deskMenuOpen(), SCREEN_WAIT, "the desktop menu to open")
                .thenAssert(0, () -> ctx.screen(DesktopScreen.class).deskMenuLabels().containsAll(
                        java.util.List.of("New", "Refresh", "Display settings", "Personalize", "Properties")),
                        "the wallpaper's menu offers what a desktop offers")
                .thenScreenshot(2, "desk-menu")
                .then(SETTLE, () -> {
                    final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
                    ctx.clickDesktop(desktop.deskMenuItemCenter(desktop.deskMenuLabels().indexOf("New")));
                })
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).deskSubmenuItemCenter(0) != null,
                        SCREEN_WAIT, "the New menu to open beside it")
                .thenScreenshot(2, "desk-menu-new")
                .then(SETTLE, () -> ctx.clickDesktop(ctx.screen(DesktopScreen.class).deskSubmenuItemCenter(0)))
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).desktopItemNames().contains("New Folder"),
                        SCREEN_WAIT, "a folder to appear on the desktop")
                .thenAssert(0, () -> !ctx.screen(DesktopScreen.class).deskMenuOpen(), "the menu closed on the choice")
                .thenScreenshot(2, "desk-new-folder");
    }

    /**
     * The address bar is a text field once it is clicked into: the whole path is selected, so Ctrl+C
     * copies it; Shift with the arrows selects part of it, and Ctrl+C copies that. And the folder's own
     * menu opens the terminal with its prompt in the folder.
     */
    @ClientTest(timeoutTicks = 2400)
    public static void explorer_selectsTheAddressAndOpensTheTerminalHere(final ClientTestContext ctx) {
        atTheDesktop(ctx, FRAMES_XP, 0)
                // A folder that is really on the disk: the prompt refuses to enter one that is not.
                .thenServer(0, level -> dev.jstech.computers.os.fs.DiskFilesystem.write(
                        dev.jstech.tests.testkit.TestWorldBuilder.at(level, ctx.origin())
                                .blockEntity(COMPUTER, CraftingComputerBlockEntity.class).systemDisk(),
                        "progs/note.txt", dev.jstech.computers.os.fs.FileType.TXT, "a note", Long.MAX_VALUE,
                        dev.jstech.computers.os.FilesystemKind.HIERARCHICAL))
                .then(SETTLE, () -> DesktopScreen.requestOpenFiles("progs"))
                .thenWaitUntil(() -> files(ctx) != null && "progs".equals(files(ctx).currentDir()), SCREEN_WAIT,
                        "the explorer to open on the folder")
                .then(SETTLE, () -> ctx.clickDesktop(files(ctx).addressEditPoint()))
                .thenWaitUntil(() -> files(ctx).editingAddress(), SCREEN_WAIT, "the address bar to turn into text")
                .thenScreenshot(2, "address-selected")
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL))
                .thenAssert(1, () -> "C:\\progs\\".equals(net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard()),
                        "the whole address is selected on the way in, so Ctrl+C copies it")
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_END))
                .then(1, () -> {
                    for (int i = 0; i < 6; i++) {
                        ctx.key(GLFW.GLFW_KEY_LEFT, GLFW.GLFW_MOD_SHIFT);
                    }
                })
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL))
                .thenAssert(1, () -> "progs\\".equals(net.minecraft.client.Minecraft.getInstance().keyboardHandler.getClipboard()),
                        "Shift with the arrows selects part of the address, and Ctrl+C copies that")
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenWaitUntil(() -> !files(ctx).editingAddress(), SCREEN_WAIT, "Escape to leave the address as it was")
                .then(SETTLE, () -> files(ctx).openBackgroundMenu())
                .thenWaitUntil(() -> files(ctx).contextOpen(), SCREEN_WAIT, "the folder's menu to open")
                .thenAssert(0, () -> files(ctx).contextLabels().contains("Open in Command Prompt"),
                        "the menu offers the terminal")
                .thenScreenshot(2, "folder-menu")
                .then(SETTLE, () -> ctx.clickDesktop(files(ctx).contextPoint("Open in Command Prompt")))
                .thenWaitUntil(() -> terminal(ctx) != null && terminal(ctx).prompt().startsWith("C:\\progs"),
                        SCREEN_WAIT * 2, "the terminal to come up with its prompt in the folder")
                .thenScreenshot(2, "terminal-here");
    }

    private static dev.jstech.computers.client.os.ShellApp terminal(final ClientTestContext ctx) {
        final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
        final var window = desktop == null ? null : desktop.windowFor("Command Prompt");
        final IDesktopApp app = window == null ? null : window.app();
        return app instanceof dev.jstech.computers.client.os.ShellApp s ? s : null;
    }

    /** A click past the last crumb turns the address into text; a path typed there is where the explorer goes. */
    @ClientTest(timeoutTicks = 2400)
    public static void explorer_addressBarIsTypedOver(final ClientTestContext ctx) {
        atTheDesktop(ctx, FRAMES_XP, 0)
                .then(SETTLE, () -> DesktopScreen.requestOpenFiles(""))
                .thenWaitUntil(() -> files(ctx) != null, SCREEN_WAIT, "the explorer to open")
                .then(SETTLE, () -> ctx.clickDesktop(files(ctx).addressEditPoint()))
                .thenWaitUntil(() -> files(ctx).editingAddress(), SCREEN_WAIT, "the address bar to turn into text")
                .thenScreenshot(2, "address-editing")
                .then(SETTLE, () -> ctx.key(GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_X, GLFW.GLFW_MOD_CONTROL))
                .then(1, () -> ctx.type("C:\\progs\\"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .thenWaitUntil(() -> files(ctx) != null && files(ctx).currentDir().equals("progs") && !files(ctx).editingAddress(),
                        SCREEN_WAIT, "the explorer to go where the typed path says")
                .thenScreenshot(2, "address-typed");
    }

    /**
     * A desktop drawn at half puts the same things closer together, and a click lands where they are
     * drawn; a machine told nothing is at three quarters, which is what every other test runs at.
     */
    @ClientTest(timeoutTicks = 2400)
    public static void desktop_drawsSmallerAtTheScaleSetForIt(final ClientTestContext ctx) {
        atTheDesktop(ctx, FRAMES_11, 50)
                .thenAssert(0, () -> Math.abs(ctx.screen(DesktopScreen.class).desktopScale() - 0.5) < 0.001,
                        "the desktop reads its scale from the machine")
                .thenScreenshot(2, "desktop-50")
                .then(SETTLE, () -> DesktopScreen.requestOpenFiles(""))
                .thenWaitUntil(() -> files(ctx) != null, SCREEN_WAIT, "the explorer to open on the scaled desktop")
                .then(SETTLE, () -> ctx.clickDesktop(files(ctx).addressEditPoint()))
                .thenWaitUntil(() -> files(ctx).editingAddress(), SCREEN_WAIT, "a click to land where the address bar is drawn")
                .thenScreenshot(2, "desktop-50-explorer");
    }

    /** The headings' edges can be dragged: the Type column pulled to the left grows wider. */
    @ClientTest(timeoutTicks = 2400)
    public static void explorer_columnsAreResizedByDraggingTheirEdges(final ClientTestContext ctx) {
        final int[] before = new int[1];
        atTheDesktop(ctx, FRAMES_XP, 0)
                .then(SETTLE, () -> DesktopScreen.requestOpenFiles(""))
                .thenWaitUntil(() -> files(ctx) != null, SCREEN_WAIT, "the explorer to open")
                .then(SETTLE, () -> {
                    before[0] = files(ctx).typeColumnWidth();
                    final int[] edge = files(ctx).columnEdgePoint(1);
                    ctx.dragDesktop(edge, new int[] {edge[0] - 30, edge[1]});
                })
                .thenWaitUntil(() -> files(ctx).typeColumnWidth() >= before[0] + 20, SCREEN_WAIT,
                        "the Type column to grow by what its edge was dragged")
                .thenScreenshot(2, "columns-dragged");
    }

    /** The scale a machine starts at, before anybody opens Settings. */
    @ClientTest(timeoutTicks = 2400)
    public static void desktop_startsAtThreeQuarters(final ClientTestContext ctx) {
        atTheDesktop(ctx, FRAMES_XP, 0)
                .thenAssert(0, () -> Math.abs(ctx.screen(DesktopScreen.class).desktopScale() - 0.75) < 0.001,
                        "a machine told nothing draws its desktop at three quarters")
                .thenScreenshot(2, "desktop-default");
    }
}
