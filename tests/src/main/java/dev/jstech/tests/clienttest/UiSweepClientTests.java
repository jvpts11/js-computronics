/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.clienttest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.blockentity.ServerRackBlockEntity;
import dev.jstech.computronics.client.ClusterManagementComputerScreen;
import dev.jstech.computronics.client.CraftingSwitchScreen;
import dev.jstech.computronics.client.ServerRackScreen;
import dev.jstech.computronics.client.os.DesktopScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * A fast render sweep over the block-backed screens that the focused client tests do not already open (the
 * server rack and router infrastructure, the Crafting Switch, and the supercomputer cluster) right-clicking
 * each in turn, screenshotting it, and confirming it actually opens and renders (a screen that opened one tick
 * and closed, or crashed the render, fails here). The assembly computers, the Pattern Encoder, the desktop and
 * its programs are covered by the other client tests.
 */
public final class UiSweepClientTests {

    private UiSweepClientTests() {
    }

    private static final int SETTLE = 4;
    private static final int SCREEN_WAIT = 40;
    /** Long enough for a cold start's POST to play out on the monitor before the desktop shows. */
    private static final int BOOT_WAIT = 400;

    private static final BlockPos RACK = new BlockPos(2, 2, 2);
    private static final BlockPos RACK_MONITOR = new BlockPos(3, 2, 2);
    private static final BlockPos PLAYER_AT_RACK_MONITOR = new BlockPos(4, 2, 2);
    private static final ResourceLocation FRAMES_XP = ResourceLocation.fromNamespaceAndPath("jsc", "frames_xp");
    private static final BlockPos SWITCH = new BlockPos(5, 2, 2);
    private static final BlockPos CLUSTER_MANAGER = new BlockPos(8, 2, 2);
    private static final BlockPos NODE = new BlockPos(11, 2, 2);

    /** Right-clicks {@code block}, waits for {@code screen}, screenshots it, asserts it stays open, then closes. */
    private static void open(final ClientTestContext ctx, final BlockPos block, final Class<? extends Screen> screen,
                             final String label) {
        ctx.thenTeleport(SETTLE, block.south(), Direction.NORTH)
                .thenRightClick(SETTLE, block)
                .thenAwaitScreen(screen, SCREEN_WAIT)
                .thenScreenshot(2, label)
                .thenAssert(0, () -> ctx.screen(screen) != null,
                        label + " must open and stay rendered (not open a tick and close)")
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);
    }

    @ClientTest(timeoutTicks = 1800)
    public static void uiSweep_serverAndClusterScreensRender(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
            // A rack with a mounted server running a desktop: its monitor opens the server's desktop below.
            final ServerRackBlockEntity rack = world.placeSeededRack(RACK);
            rack.installOs(FRAMES_XP);
            rack.setPowered(true);
            world.placeMonitor(RACK_MONITOR, Direction.EAST);
            world.setBlock(SWITCH, ComputingModule.CRAFTING_SWITCH.get());
            world.setBlock(CLUSTER_MANAGER, ComputingModule.CLUSTER_MANAGEMENT_COMPUTER.get());
            world.setBlock(NODE, ComputingModule.SUPERCOMPUTER_RACK.get());
        });
        open(ctx, RACK, ServerRackScreen.class, "server-rack");
        open(ctx, SWITCH, CraftingSwitchScreen.class, "crafting-switch");
        open(ctx, CLUSTER_MANAGER, ClusterManagementComputerScreen.class, "cluster-management-computer");
        open(ctx, NODE, ServerRackScreen.class, "supercomputer-rack");
        /*
         * A rack server's desktop through a monitor. The rack names its era to the client only once its unit has
         * travelled over, and the monitor frame (which the recipe viewer asks for every frame) must cope before.
         */
        ctx.thenTeleport(SETTLE, PLAYER_AT_RACK_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, RACK_MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenAssert(2, () -> ctx.screen(DesktopScreen.class).frameBounds() != null,
                        "the rack server's desktop must frame itself whether or not the rack's era is known yet")
                .thenScreenshot(2, "rack-server-desktop")
                // The panel's own right-click opens its menu, and the Task Manager is one entry on it.
                .then(2, () -> {
                    final int[] p = ctx.screen(DesktopScreen.class).emptyPanelPoint();
                    ctx.rightClick(p[0] + 0.5, p[1] + 0.5);
                })
                .thenAssert(2, () -> ctx.screen(DesktopScreen.class).isPanelMenuOpen(),
                        "right-clicking the panel must open the panel's own menu")
                .thenScreenshot(2, "panel-menu")
                .then(0, () -> {
                    final int[] p = ctx.screen(DesktopScreen.class).panelMenuPoint("Task Manager");
                    ctx.assertTrue(p != null, "the menu must carry a Task Manager entry");
                    ctx.click(p[0] + 0.5, p[1] + 0.5);
                })
                .thenAssert(4, () -> ctx.screen(DesktopScreen.class).openWindowLabels().contains("Task Manager"),
                        "the menu's Task Manager entry must open it")
                .thenScreenshot(4, "task-manager")
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT);
    }
}
