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
import dev.jstech.computronics.client.os.VirtualStudioCodeApp;
import dev.jstech.computronics.client.os.DesktopScreen;
import dev.jstech.computronics.client.os.IDesktopApp;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

/**
 * Writing a program in the world, the way a player does it.
 *
 * <p>The language has been proved against a made-up machine and the editors a piece at a time. This is
 * the other half: a real computer with a real disk, a player opening an editor on its monitor, typing a
 * program into it, and the machine compiling and running what was typed. The program prints a line,
 * because a printed line is the one thing that can only appear if every part of the path worked.
 */
public final class CannonEditorClientTests {

    private CannonEditorClientTests() {
    }

    private static final int SETTLE = 4;
    private static final int SCREEN_WAIT = 80;
    /** Long enough for a cold start's POST to play out on the monitor before the desktop shows. */
    private static final int BOOT_WAIT = 400;

    private static final BlockPos COMPUTER = new BlockPos(5, 2, 2);
    private static final BlockPos MONITOR = new BlockPos(6, 2, 2);
    private static final BlockPos PLAYER_AT_MONITOR = new BlockPos(8, 2, 2);

    private static final ResourceLocation FRAMES_XP =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "frames_xp");
    private static final String EDITOR_LAUNCHER = "Virtual Studio Code";

    /** The program the player writes: short enough to type, and it says something when it runs. */
    private static final String SOURCE =
            "class Hello { static void Main() { Console.PrintLine(\"it runs\"); } }";

    private static ResourceLocation program(final String path) {
        return ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, path);
    }

    /** The window of a launched program, or null. */
    private static <T extends IDesktopApp> T app(final ClientTestContext ctx, final String label,
                                                 final Class<T> type) {
        final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
        final var window = desktop == null ? null : desktop.windowFor(label);
        return window != null && type.isInstance(window.app()) ? type.cast(window.app()) : null;
    }

    private static VirtualStudioCodeApp editor(final ClientTestContext ctx) {
        return app(ctx, EDITOR_LAUNCHER, VirtualStudioCodeApp.class);
    }

    /** Opens a program from the Start menu, the way a player reaches one. */
    private static void launch(final ClientTestContext ctx, final String label) {
        final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
        ctx.click(desktop.startButtonX(), desktop.startButtonY());
        final int item = desktop.launcherLabels().indexOf(label);
        ctx.click(desktop.startMenuItemX(item), desktop.startMenuItemY(item));
    }

    /** A program written at the keyboard, saved, compiled and run, all on one machine. */
    @ClientTest(timeoutTicks = 3000)
    public static void virtualStudioCode_writesAProgramTheMachineThenRuns(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    final CraftingComputerBlockEntity computer = world.placeRunningCraftingComputer(COMPUTER);
                    computer.installOs(FRAMES_XP);
                    for (final String id : new String[] {"virtual_studio_code", "cannonc", "cannonrt"}) {
                        computer.console().install(program(id).toString());
                    }
                    world.placeMonitor(MONITOR, Direction.EAST);
                })
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).launcherLabels().contains(EDITOR_LAUNCHER),
                        SCREEN_WAIT, "the editor to be listed in Start")
                .then(0, () -> launch(ctx, EDITOR_LAUNCHER))
                .thenWaitUntil(() -> editor(ctx) != null, SCREEN_WAIT, "the editor window to open")
                .thenScreenshot(2, "editor-open")
                /*
                 * A file the machine does not have yet: opening one that is not there is how writing a new
                 * program starts, so the editor has to take it as an empty buffer rather than refusing.
                 */
                .then(SETTLE, () -> editor(ctx).openFile("progs/hello.can"))
                .thenWaitUntil(() -> editor(ctx).openFile().equals("progs/hello.can"),
                        SCREEN_WAIT, "the editor to open the new file")
                .then(0, () -> ctx.type(SOURCE))
                .thenScreenshot(2, "editor-typed")
                .thenAssert(1, () -> editor(ctx).text().contains("Console.PrintLine"),
                        "what was typed is in the editor's buffer")
                .thenAssert(0, () -> editor(ctx).complaintCount() == 0,
                        "the compiler is happy with what was typed")
                // Ctrl+S, the way anybody saves.
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_S, GLFW.GLFW_MOD_CONTROL))
                .thenWaitUntilServer(level -> !dev.jstech.computronics.os.fs.DiskFilesystem.read(
                                TestWorldBuilder.at(level, ctx.origin())
                                        .blockEntity(COMPUTER, CraftingComputerBlockEntity.class).systemDisk(),
                                "progs/hello.can").orElse("").isEmpty(),
                        SCREEN_WAIT, "the program to be on the machine's disk", level -> "")
                .thenScreenshot(2, "editor-saved")
                /*
                 * The terminal panel is the machine's own console, so compiling and running happen at it and
                 * what the program prints lands where the player is already looking.
                 */
                .then(SETTLE, () -> editor(ctx).runInTerminal("cannonc progs/hello.can"))
                .thenWaitUntil(() -> editor(ctx).terminalText().contains(".asm"),
                        SCREEN_WAIT, "the compiler to say what it produced")
                .thenScreenshot(2, "compiled")
                .then(SETTLE, () -> editor(ctx).runInTerminal("cannon run progs/hello.asm"))
                .thenWaitUntil(() -> editor(ctx).terminalText().contains("it runs"),
                        SCREEN_WAIT, "the program the player wrote to print its line")
                .thenScreenshot(2, "ran");
    }
}
