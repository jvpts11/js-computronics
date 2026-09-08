/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Tech Series.
 */
package dev.jstech.tests.clienttest;

import dev.jstech.computronics.JsComputronics;
import dev.jstech.computronics.blockentity.CraftingComputerBlockEntity;
import dev.jstech.computronics.client.os.DesktopScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * The taskbar's right button, on the panel that keeps its buttons in the middle.
 *
 * <p>Frames 11 centres its app icons where every other panel lines them up from the left. The right
 * button used to fall through to the left-hand layout's idea of where a button was, so empty bar opened
 * a window's menu, drawn where no icon stood. A window's icon opens the window's menu over that icon,
 * and the bar itself opens the bar's.
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
    private static final ResourceLocation FRAMES_11 =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "frames_11");
    private static final String CALCULATOR = "Calculator";

    @ClientTest(timeoutTicks = 2400)
    public static void frames11_rightButtonOnTheBarAndOnAnIconOpenTheRightMenus(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    final CraftingComputerBlockEntity computer = world.placeRunningCraftingComputer(COMPUTER);
                    computer.installOs(FRAMES_11);
                    world.placeMonitor(MONITOR, Direction.EAST);
                })
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).launcherLabels().contains(CALCULATOR),
                        SCREEN_WAIT, "the Calculator to be listed in Start")
                // Opened the way a program asks for another: what matters here is the bar, not Start.
                .then(0, () -> DesktopScreen.requestOpen(CALCULATOR))
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).windowFor(CALCULATOR) != null,
                        SCREEN_WAIT, "the Calculator window to open")
                // The bar, clear of every icon: the bar's own menu.
                .then(SETTLE, () -> {
                    final int[] p = ctx.screen(DesktopScreen.class).emptyPanelPoint();
                    ctx.rightClick(p[0] + 0.5, p[1] + 0.5);
                })
                .thenAssert(1, () -> ctx.screen(DesktopScreen.class).isPanelMenuOpen()
                                && !ctx.screen(DesktopScreen.class).isTaskMenuOpen(),
                        "right-clicking empty bar opens the bar's menu, not a window's")
                .thenScreenshot(2, "bar-menu")
                // A click on the bar clear of the menu puts it away (Escape would leave the desktop).
                .then(1, () -> {
                    final int[] p = ctx.screen(DesktopScreen.class).emptyPanelPoint();
                    ctx.click(p[0] + 0.5, p[1] + 0.5);
                })
                // The window's icon: the window's menu.
                .then(SETTLE, () -> {
                    final int[] p = ctx.screen(DesktopScreen.class).taskButtonPoint(0);
                    ctx.rightClick(p[0] + 0.5, p[1] + 0.5);
                })
                .thenAssert(1, () -> ctx.screen(DesktopScreen.class).isTaskMenuOpen()
                                && !ctx.screen(DesktopScreen.class).isPanelMenuOpen(),
                        "right-clicking the window's icon opens the window's menu, not the bar's")
                .thenScreenshot(2, "window-menu");
    }
}
