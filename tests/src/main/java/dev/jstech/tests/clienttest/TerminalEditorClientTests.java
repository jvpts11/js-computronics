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
import dev.jstech.computers.client.os.IDesktopApp;
import dev.jstech.computers.client.os.ShellApp;
import dev.jstech.computers.os.fs.DiskFilesystem;
import dev.jstech.tests.testkit.TestWorldBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import org.lwjgl.glfw.GLFW;

/**
 * The two editors that take over a terminal, driven from the keyboard the way a player drives them:
 * Vim with its modes and colon commands, Emacs with its chords, both writing a file to the disk and
 * giving the terminal back. The prompt's own caret is driven too, since it is the same keyboard.
 */
public final class TerminalEditorClientTests {

    private TerminalEditorClientTests() {
    }

    private static final int SETTLE = 4;
    private static final int SCREEN_WAIT = 80;
    private static final int BOOT_WAIT = 400;

    private static final BlockPos COMPUTER = new BlockPos(5, 2, 2);
    private static final BlockPos MONITOR = new BlockPos(6, 2, 2);
    private static final BlockPos PLAYER_AT_MONITOR = new BlockPos(8, 2, 2);
    private static final ResourceLocation FRAMES_XP =
            ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, "frames_xp");
    private static final String TERMINAL = "Command Prompt";

    private static ResourceLocation program(final String path) {
        return ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, path);
    }

    private static ShellApp shell(final ClientTestContext ctx) {
        final DesktopScreen desktop = ctx.screen(DesktopScreen.class);
        final var window = desktop == null ? null : desktop.windowFor(TERMINAL);
        final IDesktopApp app = window == null ? null : window.app();
        return app instanceof ShellApp s ? s : null;
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

    /** A machine with the terminal, Vim and Emacs, at its desktop with the terminal window open. */
    private static ClientTestContext atTheTerminal(final ClientTestContext ctx) {
        return atTheTerminal(ctx, computer -> { });
    }

    /** The same, with something put on the machine's disk before the desktop opens. */
    private static ClientTestContext atTheTerminal(final ClientTestContext ctx,
                                                   final java.util.function.Consumer<CraftingComputerBlockEntity> seed) {
        return ctx.thenBuild(0, world -> {
                    final CraftingComputerBlockEntity computer = world.placeRunningCraftingComputer(COMPUTER);
                    computer.installOs(FRAMES_XP);
                    for (final String id : new String[] {"vim", "emacs", "cannonc"}) {
                        computer.console().install(program(id).toString());
                    }
                    seed.accept(computer);
                    world.placeMonitor(MONITOR, Direction.EAST);
                })
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).launcherLabels().contains(TERMINAL),
                        SCREEN_WAIT, "the terminal to be listed in Start")
                .then(0, () -> launch(ctx, TERMINAL))
                .thenWaitUntil(() -> shell(ctx) != null, SCREEN_WAIT, "the terminal window to open");
    }

    /**
     * Vim: insert mode types, Escape leaves it, the motions move, dd cuts a line, u puts it back, and
     * :wq writes the file and gives the terminal back. Escape inside the editor must not close the
     * desktop, which is what it does everywhere else.
     */
    @ClientTest(timeoutTicks = 2400)
    public static void vim_writesAFileAndGivesTheTerminalBack(final ClientTestContext ctx) {
        atTheTerminal(ctx)
                .then(SETTLE, () -> ctx.type("vim progs/hello.can"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .thenWaitUntil(() -> shell(ctx) != null && shell(ctx).editing(), SCREEN_WAIT, "Vim to take the terminal")
                .thenScreenshot(2, "vim-open")
                .then(SETTLE, () -> ctx.type("i"))
                .then(1, () -> ctx.type("class Hello {"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .then(1, () -> ctx.type("static void Main() { Console.PrintLine(\"hi\"); }"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .then(1, () -> ctx.type("this line goes"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .then(1, () -> ctx.type("}"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAssert(0, () -> ctx.screen(DesktopScreen.class) != null && shell(ctx).editing(),
                        "Escape leaves insert mode and nothing else")
                // Up to the line that should go, cut it, and check the undo brings it back before cutting it again.
                .then(1, () -> ctx.type("k"))
                .then(1, () -> ctx.type("dd"))
                .then(1, () -> ctx.type("u"))
                .then(1, () -> ctx.type("dd"))
                .thenScreenshot(2, "vim-typed")
                .then(1, () -> ctx.type(":wq"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .thenWaitUntil(() -> shell(ctx) != null && !shell(ctx).editing(), SCREEN_WAIT, "Vim to give the terminal back")
                .thenWaitUntilServer(level -> diskText(ctx, level, "progs/hello.can").contains("class Hello")
                                && !diskText(ctx, level, "progs/hello.can").contains("this line goes"),
                        SCREEN_WAIT, "the file to be on the disk without the cut line",
                        level -> diskText(ctx, level, "progs/hello.can"))
                .thenScreenshot(2, "vim-saved");
    }

    /**
     * Vim on a file named from inside its folder: the file comes up with its text, w moves a word,
     * D cuts to the end of the line, :w writes it and :q gives the terminal back.
     */
    @ClientTest(timeoutTicks = 2400)
    public static void vim_opensAFileNamedFromInsideItsFolderAndCutsToTheEnd(final ClientTestContext ctx) {
        atTheTerminal(ctx, computer -> DiskFilesystem.write(computer.systemDisk(), "progs/notes.can",
                        dev.jstech.computers.os.fs.FileType.CAN, "abc def\nend", Long.MAX_VALUE,
                        dev.jstech.computers.os.FilesystemKind.HIERARCHICAL))
                .then(SETTLE, () -> ctx.type("cd progs"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .thenWaitUntil(() -> shell(ctx) != null && shell(ctx).prompt().contains("progs"), SCREEN_WAIT,
                        "the prompt to be inside the folder")
                .then(SETTLE, () -> ctx.type("vim notes.can"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .thenWaitUntil(() -> shell(ctx) != null && shell(ctx).editing(), SCREEN_WAIT, "Vim to take the terminal")
                .thenWaitUntil(() -> shell(ctx).editorText().startsWith("abc def"), SCREEN_WAIT,
                        "the file named from its folder to come up with its text")
                .thenScreenshot(2, "vim-relative-open")
                .then(SETTLE, () -> ctx.type("w"))
                .then(1, () -> ctx.type("D"))
                .thenAssert(1, () -> shell(ctx).editorText().equals("abc \nend"),
                        "D cuts from the cursor to the end of the line")
                .then(1, () -> ctx.type(":w"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .thenWaitUntilServer(level -> diskText(ctx, level, "progs/notes.can").equals("abc \nend"),
                        SCREEN_WAIT, ":w to write the file where it was", level -> diskText(ctx, level, "progs/notes.can"))
                .then(1, () -> ctx.type(":q"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .thenWaitUntil(() -> shell(ctx) != null && !shell(ctx).editing(), SCREEN_WAIT, "Vim to give the terminal back");
    }

    /** Emacs compiles the buffer into its second buffer on M-x compile, and C-x u takes a change back. */
    @ClientTest(timeoutTicks = 2400)
    public static void emacs_compilesTheBufferOnMxCompile(final ClientTestContext ctx) {
        atTheTerminal(ctx)
                .then(SETTLE, () -> ctx.type("emacs progs/prog.can"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .thenWaitUntil(() -> shell(ctx) != null && shell(ctx).editing(), SCREEN_WAIT, "Emacs to take the terminal")
                .then(SETTLE, () -> ctx.type("using System.IO.*; namespace P; class A { static void Main() { Console.PrintLine(\"x\"); } }"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_X, GLFW.GLFW_MOD_ALT))
                .then(1, () -> ctx.type("compile"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .thenWaitUntil(() -> shell(ctx).editorLowerText().contains("Compilation finished")
                                && shell(ctx).editorLowerText().contains("lines of assembly"),
                        SCREEN_WAIT, "the second buffer to say the program compiled")
                .thenScreenshot(2, "emacs-compiled")
                // Break it, compile again: the complaint lands in the second buffer with its line.
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_E, GLFW.GLFW_MOD_CONTROL))
                .then(1, () -> ctx.type(" }"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_X, GLFW.GLFW_MOD_ALT))
                .then(1, () -> ctx.type("compile"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .thenWaitUntil(() -> shell(ctx).editorLowerText().contains("error(s)"), SCREEN_WAIT,
                        "the second buffer to carry the compiler's complaint")
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_X, GLFW.GLFW_MOD_CONTROL))
                .then(1, () -> ctx.type("u"))
                // The program itself ends in "} }"; the stray brace made it "} } }", and the undo takes that back.
                .thenAssert(1, () -> !shell(ctx).editorText().endsWith("} } }"), "C-x u takes the change back")
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_X, GLFW.GLFW_MOD_CONTROL))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL))
                .then(1, () -> ctx.type("y"))
                .thenWaitUntil(() -> shell(ctx) != null && !shell(ctx).editing(), SCREEN_WAIT, "Emacs to give the terminal back");
    }

    /**
     * Emacs: text types straight in, C-x C-s writes, C-x C-c with changes asks and n keeps editing,
     * C-k cuts to the end of the line and C-y puts it back, and C-x C-c after a save leaves.
     */
    @ClientTest(timeoutTicks = 2400)
    public static void emacs_writesAFileAndAsksBeforeLeavingWithChanges(final ClientTestContext ctx) {
        atTheTerminal(ctx)
                .then(SETTLE, () -> ctx.type("emacs progs/note.txt"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .thenWaitUntil(() -> shell(ctx) != null && shell(ctx).editing(), SCREEN_WAIT, "Emacs to take the terminal")
                .then(SETTLE, () -> ctx.type("first line here"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .then(1, () -> ctx.type("second"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_X, GLFW.GLFW_MOD_CONTROL))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_S, GLFW.GLFW_MOD_CONTROL))
                .thenWaitUntilServer(level -> diskText(ctx, level, "progs/note.txt").contains("second"),
                        SCREEN_WAIT, "the note to be written", level -> diskText(ctx, level, "progs/note.txt"))
                // A change after the save, then the way out: it asks, n stays.
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_K, GLFW.GLFW_MOD_CONTROL))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_X, GLFW.GLFW_MOD_CONTROL))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL))
                .thenScreenshot(2, "emacs-asks")
                .then(1, () -> ctx.type("n"))
                .thenAssert(0, () -> shell(ctx).editing(), "n keeps the editor up")
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_Y, GLFW.GLFW_MOD_CONTROL))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_X, GLFW.GLFW_MOD_CONTROL))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_S, GLFW.GLFW_MOD_CONTROL))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_X, GLFW.GLFW_MOD_CONTROL))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL))
                .thenWaitUntil(() -> shell(ctx) != null && !shell(ctx).editing(), SCREEN_WAIT, "Emacs to give the terminal back")
                .thenWaitUntilServer(level -> diskText(ctx, level, "progs/note.txt").contains("second")
                                && diskText(ctx, level, "progs/note.txt").contains("first line here"),
                        SCREEN_WAIT, "the note to hold both lines after the yank",
                        level -> diskText(ctx, level, "progs/note.txt"))
                .thenScreenshot(2, "emacs-done");
    }

    /** The prompt's caret moves within the line: a word put back into the middle of a command runs it whole. */
    @ClientTest(timeoutTicks = 2400)
    public static void prompt_movesTheCaretWithinTheLine(final ClientTestContext ctx) {
        atTheTerminal(ctx)
                .then(SETTLE, () -> ctx.type("echo  world"))
                // Home, then Right past "echo ", then the missing word in the gap that was left for it.
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_HOME))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_RIGHT, GLFW.GLFW_MOD_CONTROL))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_RIGHT))
                .then(1, () -> ctx.type("hello"))
                .then(1, () -> ctx.key(GLFW.GLFW_KEY_ENTER))
                .thenWaitUntil(() -> shell(ctx) != null && shell(ctx).scrollbackText().contains("hello world"),
                        SCREEN_WAIT, "the prompt to echo the line as it was edited")
                .thenScreenshot(2, "prompt-caret");
    }
}
