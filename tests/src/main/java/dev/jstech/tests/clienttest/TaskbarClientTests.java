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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.Collections;
import java.util.List;

/**
 * The panel lists programs, not windows.
 *
 * <p>A program opened twice has one entry on the panel, and the entry says how the program stands: pinned
 * with nothing open, open, in front, or put away. Its entry lists its windows when it has several (the live
 * pictures on a modern panel, the titles on a period one), its menu pins it, and a pin is the machine's,
 * still there when the monitor is opened again. Frames 11 centres its entries where every other panel
 * lines them up from the left; Frames XP keeps the pinned ones on a quick launch beside Start.
 */
public final class TaskbarClientTests {

    private TaskbarClientTests() {
    }

    private static final int SETTLE = 4;
    private static final int SCREEN_WAIT = 80;
    private static final int BOOT_WAIT = 400;

    private static final BlockPos COMPUTER = new BlockPos(5, 2, 2);
    private static final BlockPos MONITOR = new BlockPos(6, 2, 2);
    private static final BlockPos PLAYER_AT_MONITOR = new BlockPos(8, 2, 2);
    private static final ResourceLocation FRAMES_11 = jsc("frames_11");
    private static final ResourceLocation FRAMES_XP = jsc("frames_xp");
    private static final String CALCULATOR = "Calculator";
    private static final String FILES = "Files";

    private static ResourceLocation jsc(final String path) {
        return ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, path);
    }

    private static DesktopScreen desktop(final ClientTestContext ctx) {
        return ctx.screen(DesktopScreen.class);
    }

    private static int count(final List<String> labels, final String label) {
        return Collections.frequency(labels, label);
    }

    private static ClientTestContext atTheDesktop(final ClientTestContext ctx, final ResourceLocation os) {
        return ctx.thenBuild(0, world -> {
                    final CraftingComputerBlockEntity computer = world.placeRunningCraftingComputer(COMPUTER);
                    computer.installOs(os);
                    world.placeMonitor(MONITOR, Direction.EAST);
                })
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenWaitUntil(() -> desktop(ctx).launcherLabels().contains(CALCULATOR),
                        SCREEN_WAIT, "the Calculator to be listed in Start");
    }

    @ClientTest(timeoutTicks = 2400)
    public static void frames11_rightButtonOnTheBarAndOnAnIconOpenTheRightMenus(final ClientTestContext ctx) {
        atTheDesktop(ctx, FRAMES_11)
                // Opened the way a program asks for another: what matters here is the bar, not Start.
                .then(0, () -> DesktopScreen.requestOpen(CALCULATOR))
                .thenWaitUntil(() -> desktop(ctx).windowFor(CALCULATOR) != null,
                        SCREEN_WAIT, "the Calculator window to open")
                // The bar, clear of every icon: the bar's own menu.
                .then(SETTLE, () -> {
                    final int[] p = desktop(ctx).emptyPanelPoint();
                    ctx.rightClick(p[0] + 0.5, p[1] + 0.5);
                })
                .thenAssert(1, () -> desktop(ctx).isPanelMenuOpen() && !desktop(ctx).isTaskMenuOpen(),
                        "right-clicking empty bar opens the bar's menu, not a window's")
                .thenScreenshot(2, "bar-menu")
                // A click on the bar clear of the menu puts it away (Escape would leave the desktop).
                .then(1, () -> {
                    final int[] p = desktop(ctx).emptyPanelPoint();
                    ctx.click(p[0] + 0.5, p[1] + 0.5);
                })
                // The program's icon: the program's menu.
                .then(SETTLE, () -> {
                    final int[] p = desktop(ctx).taskEntryPoint(CALCULATOR);
                    ctx.rightClick(p[0] + 0.5, p[1] + 0.5);
                })
                .thenAssert(1, () -> desktop(ctx).isTaskMenuOpen() && !desktop(ctx).isPanelMenuOpen(),
                        "right-clicking the program's icon opens the program's menu, not the bar's")
                .thenAssert(0, () -> desktop(ctx).taskMenuLabels().contains("Pin to taskbar"),
                        "a program with a window offers to be pinned")
                .thenScreenshot(2, "window-menu");
    }

    @ClientTest(timeoutTicks = 4800)
    public static void frames11_listsAProgramOnceShowsItsWindowsAndKeepsItsPin(final ClientTestContext ctx) {
        final DesktopWindow[] back = new DesktopWindow[1];
        atTheDesktop(ctx, FRAMES_11)
                // A fresh machine pins its file explorer: on the bar with nothing open.
                .thenWaitUntil(() -> desktop(ctx).pinnedLabels().contains(FILES), SCREEN_WAIT,
                        "the machine to say the explorer is pinned")
                .thenAssert(0, () -> desktop(ctx).taskEntryLabels().contains(FILES) && desktop(ctx).windowFor(FILES) == null,
                        "the pinned explorer is on the bar with no window")
                .then(0, () -> DesktopScreen.requestOpen(CALCULATOR))
                .thenWaitUntil(() -> desktop(ctx).windowsFor(CALCULATOR).size() == 1, SCREEN_WAIT, "the first Calculator")
                .then(SETTLE, () -> DesktopScreen.requestOpen(CALCULATOR))
                .thenWaitUntil(() -> desktop(ctx).windowsFor(CALCULATOR).size() == 2, SCREEN_WAIT, "the second Calculator")
                .thenAssert(0, () -> count(desktop(ctx).taskEntryLabels(), CALCULATOR) == 1,
                        "a program opened twice has one entry on the bar")
                .thenScreenshot(2, "one-entry-two-windows")
                // A click on the entry lists the windows, with their live pictures.
                .then(SETTLE, () -> {
                    final int[] p = desktop(ctx).taskEntryPoint(CALCULATOR);
                    ctx.click(p[0] + 0.5, p[1] + 0.5);
                })
                .thenAssert(1, () -> desktop(ctx).isTaskPopupOpen() && desktop(ctx).taskPopupTitles().size() == 2,
                        "the entry's popup lists both windows")
                .thenScreenshot(2, "group-cards")
                // The first card is the window at the back; picking it brings it to the front.
                .then(SETTLE, () -> {
                    back[0] = desktop(ctx).windowsFor(CALCULATOR).get(0);
                    ctx.clickDesktop(desktop(ctx).taskPopupItemPoint(0));
                })
                .thenAssert(1, () -> !desktop(ctx).isTaskPopupOpen()
                                && desktop(ctx).windowsFor(CALCULATOR).get(1) == back[0],
                        "picking a card brings that window to the front and puts the popup away")
                // The card's close box ends that window alone.
                .then(SETTLE, () -> {
                    final int[] p = desktop(ctx).taskEntryPoint(CALCULATOR);
                    ctx.click(p[0] + 0.5, p[1] + 0.5);
                })
                .then(SETTLE, () -> ctx.clickDesktop(desktop(ctx).taskPopupClosePoint(0)))
                .thenAssert(1, () -> desktop(ctx).windowsFor(CALCULATOR).size() == 1,
                        "the card's close box ends one window, not the program")
                .then(SETTLE, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                // Pin it from its menu; the machine keeps the pin.
                .then(SETTLE, () -> {
                    final int[] p = desktop(ctx).taskEntryPoint(CALCULATOR);
                    ctx.rightClick(p[0] + 0.5, p[1] + 0.5);
                })
                .then(SETTLE, () -> ctx.clickDesktop(desktop(ctx).taskMenuPoint("Pin to taskbar")))
                .thenAssert(1, () -> desktop(ctx).pinnedLabels().contains(CALCULATOR), "the program is pinned")
                .then(SETTLE, () -> {
                    final int[] p = desktop(ctx).taskEntryPoint(CALCULATOR);
                    ctx.rightClick(p[0] + 0.5, p[1] + 0.5);
                })
                .then(SETTLE, () -> ctx.clickDesktop(desktop(ctx).taskMenuPoint("Close")))
                .thenAssert(1, () -> desktop(ctx).windowFor(CALCULATOR) == null
                                && desktop(ctx).taskEntryLabels().contains(CALCULATOR),
                        "a pinned program keeps its place on the bar with nothing open")
                .thenScreenshot(2, "pinned-closed")
                // Leave and come back: the pin is the machine's, not this screen's.
                .then(SETTLE, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenWaitUntil(() -> desktop(ctx).pinnedLabels().contains(CALCULATOR), SCREEN_WAIT,
                        "the pin to come back from the machine")
                // A pinned entry with nothing open starts the program.
                .then(SETTLE, () -> {
                    final int[] p = desktop(ctx).taskEntryPoint(CALCULATOR);
                    ctx.click(p[0] + 0.5, p[1] + 0.5);
                })
                .thenWaitUntil(() -> desktop(ctx).windowFor(CALCULATOR) != null, SCREEN_WAIT,
                        "clicking the pinned entry to start the program")
                // Unpin it and close it: the entry goes with the last window.
                .then(SETTLE, () -> {
                    final int[] p = desktop(ctx).taskEntryPoint(CALCULATOR);
                    ctx.rightClick(p[0] + 0.5, p[1] + 0.5);
                })
                .then(SETTLE, () -> ctx.clickDesktop(desktop(ctx).taskMenuPoint("Unpin from taskbar")))
                .thenAssert(1, () -> !desktop(ctx).pinnedLabels().contains(CALCULATOR), "the program is unpinned")
                .then(SETTLE, () -> {
                    final int[] p = desktop(ctx).taskEntryPoint(CALCULATOR);
                    ctx.rightClick(p[0] + 0.5, p[1] + 0.5);
                })
                .then(SETTLE, () -> ctx.clickDesktop(desktop(ctx).taskMenuPoint("Close")))
                .thenAssert(1, () -> !desktop(ctx).taskEntryLabels().contains(CALCULATOR),
                        "an unpinned program leaves the bar with its last window");
    }

    @ClientTest(timeoutTicks = 2400)
    public static void framesXp_groupsWindowsUnderOneButtonAndKeepsPinsOnTheQuickLaunch(final ClientTestContext ctx) {
        atTheDesktop(ctx, FRAMES_XP)
                .then(0, () -> DesktopScreen.requestOpen(CALCULATOR))
                .thenWaitUntil(() -> desktop(ctx).windowsFor(CALCULATOR).size() == 1, SCREEN_WAIT, "the first Calculator")
                .then(SETTLE, () -> DesktopScreen.requestOpen(CALCULATOR))
                .thenWaitUntil(() -> desktop(ctx).windowsFor(CALCULATOR).size() == 2, SCREEN_WAIT, "the second Calculator")
                .thenAssert(0, () -> count(desktop(ctx).taskEntryLabels(), CALCULATOR) == 1,
                        "a program opened twice has one button")
                // The button lists the windows by title, the way that desktop grouped them.
                .then(SETTLE, () -> {
                    final int[] p = desktop(ctx).taskEntryPoint(CALCULATOR);
                    ctx.click(p[0] + 0.5, p[1] + 0.5);
                })
                .thenAssert(1, () -> desktop(ctx).isTaskPopupOpen() && desktop(ctx).taskPopupTitles().size() == 2,
                        "the grouped button lists both windows")
                .thenScreenshot(2, "xp-group-list")
                .then(SETTLE, () -> ctx.clickDesktop(desktop(ctx).taskPopupItemPoint(1)))
                .thenAssert(1, () -> !desktop(ctx).isTaskPopupOpen(), "picking a row puts the list away")
                // The quick launch beside Start holds the pinned explorer; a click there opens it.
                .thenWaitUntil(() -> desktop(ctx).pinnedLabels().contains(FILES), SCREEN_WAIT,
                        "the machine to say the explorer is pinned")
                .then(SETTLE, () -> {
                    final int[] p = desktop(ctx).taskEntryPoint(FILES);
                    ctx.click(p[0] + 0.5, p[1] + 0.5);
                })
                .thenWaitUntil(() -> desktop(ctx).windowFor(FILES) != null, SCREEN_WAIT,
                        "the quick launch icon to open the explorer")
                .thenScreenshot(2, "xp-quick-launch");
    }

    /** What KDE calls the calculator: the panel lists programs by the desktop's own names. */
    private static final String KCALC = "KCalc";

    @ClientTest(timeoutTicks = 2400)
    public static void kde_showsAProgramsWindowsAsPicturesFromItsButton(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    final CraftingComputerBlockEntity computer = world.placeRunningCraftingComputer(COMPUTER);
                    computer.installOs(jsc("ubuntu"));
                    computer.console().install("jsc:kde_plasma");
                    world.placeMonitor(MONITOR, Direction.EAST);
                })
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenWaitUntil(() -> desktop(ctx).launcherLabels().contains(KCALC),
                        SCREEN_WAIT, "KCalc to be listed in the launcher")
                .then(0, () -> DesktopScreen.requestOpen(KCALC))
                .thenWaitUntil(() -> desktop(ctx).windowsFor(KCALC).size() == 1, SCREEN_WAIT, "the first KCalc")
                .then(SETTLE, () -> DesktopScreen.requestOpen(KCALC))
                .thenWaitUntil(() -> desktop(ctx).windowsFor(KCALC).size() == 2, SCREEN_WAIT, "the second KCalc")
                .thenAssert(0, () -> count(desktop(ctx).taskEntryLabels(), KCALC) == 1,
                        "a program opened twice has one button on the panel")
                .then(SETTLE, () -> {
                    final int[] p = desktop(ctx).taskEntryPoint(KCALC);
                    ctx.click(p[0] + 0.5, p[1] + 0.5);
                })
                .thenAssert(1, () -> desktop(ctx).isTaskPopupOpen() && desktop(ctx).taskPopupTitles().size() == 2,
                        "the button's popup shows both windows")
                .thenScreenshot(2, "kde-group-cards")
                .then(SETTLE, () -> ctx.clickDesktop(desktop(ctx).taskPopupItemPoint(0)))
                .thenAssert(1, () -> !desktop(ctx).isTaskPopupOpen(), "picking a card puts the popup away");
    }
}
