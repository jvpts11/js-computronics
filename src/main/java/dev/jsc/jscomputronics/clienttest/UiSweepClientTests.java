/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.clienttest;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.client.CraftingSwitchScreen;
import dev.jsc.jscomputronics.module.computing.client.ServerRackScreen;
import dev.jsc.jscomputronics.module.computing.client.SupercomputerConsoleScreen;
import dev.jsc.jscomputronics.module.computing.client.SupercomputerNodeScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.lwjgl.glfw.GLFW;

/**
 * A fast render sweep over the block-backed screens that the focused client tests do not already open — the
 * server rack and router infrastructure, the Crafting Switch, and the supercomputer cluster — right-clicking
 * each in turn, screenshotting it, and confirming it actually opens and renders (a screen that opened one tick
 * and closed, or crashed the render, fails here). The assembly computers, the Pattern Encoder, the desktop and
 * its programs are covered by the other client tests.
 */
public final class UiSweepClientTests {

    private UiSweepClientTests() {
    }

    private static final int SETTLE = 4;
    private static final int SCREEN_WAIT = 40;

    private static final BlockPos RACK = new BlockPos(2, 2, 2);
    private static final BlockPos SWITCH = new BlockPos(5, 2, 2);
    private static final BlockPos CONSOLE = new BlockPos(8, 2, 2);
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
            world.setBlock(RACK, ComputingModule.SERVER_RACK.get());
            world.setBlock(SWITCH, ComputingModule.CRAFTING_SWITCH.get());
            world.setBlock(CONSOLE, ComputingModule.SUPERCOMPUTER_CONSOLE.get());
            world.setBlock(NODE, ComputingModule.SUPERCOMPUTER_NODE.get());
        });
        open(ctx, RACK, ServerRackScreen.class, "server-rack");
        open(ctx, SWITCH, CraftingSwitchScreen.class, "crafting-switch");
        open(ctx, CONSOLE, SupercomputerConsoleScreen.class, "supercomputer-console");
        open(ctx, NODE, SupercomputerNodeScreen.class, "supercomputer-node");
    }
}
