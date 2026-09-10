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
import dev.jstech.computers.client.os.VirtualStudioApp;
import dev.jstech.computers.client.os.VirtualStudioCodeApp;
import dev.jstech.computers.os.edit.project.ProjectFile;
import dev.jstech.computers.os.edit.project.ProjectTemplate;
import dev.jstech.computers.os.edit.project.SolutionFile;
import dev.jstech.computers.os.fs.DiskFilesystem;
import dev.jstech.computers.os.FilesystemKind;
import dev.jstech.computers.os.fs.FileType;
import dev.jstech.tests.testkit.TestWorldBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
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
            ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, "frames_xp");
    private static final String EDITOR_LAUNCHER = "Virtual Studio Code";

    /** The program the player writes: short enough to type, and it says something when it runs. */
    private static final String SOURCE =
            "using System.IO.*; namespace Hello; class Hello { static void Main() { Console.PrintLine(\"it runs\"); } }";

    private static ResourceLocation program(final String path) {
        return ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, path);
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
                .thenWaitUntilServer(level -> !dev.jstech.computers.os.fs.DiskFilesystem.read(
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

    private static final String STUDIO_LAUNCHER = "Virtual Studio";
    private static final String TERMINAL = "Command Prompt";

    private static VirtualStudioApp studio(final ClientTestContext ctx) {
        return app(ctx, STUDIO_LAUNCHER, VirtualStudioApp.class);
    }

    /** What the machine's terminal window has printed so far, or empty when there is none. */
    private static String terminalText(final ClientTestContext ctx) {
        final ShellApp shell = app(ctx, TERMINAL, ShellApp.class);
        return shell == null ? "" : shell.scrollbackText();
    }

    /** Whether the machine's system disk holds a non-empty file at {@code path}. */
    private static boolean onDisk(final ClientTestContext ctx, final net.minecraft.server.level.ServerLevel level,
                                  final String path) {
        return !diskText(ctx, level, path).isEmpty();
    }

    private static String diskText(final ClientTestContext ctx, final ServerLevel level, final String path) {
        return DiskFilesystem.read(computerOf(ctx, level).systemDisk(), path).orElse("");
    }

    private static CraftingComputerBlockEntity computerOf(final ClientTestContext ctx, final ServerLevel level) {
        return TestWorldBuilder.at(level, ctx.origin()).blockEntity(COMPUTER, CraftingComputerBlockEntity.class);
    }

    /* A solution already on the disk, so a test can open it without going through the wizard. */

    private static final String FARM_DIR = "progs/Farm";
    private static final String FARM_PROJECT = FARM_DIR + "/Farm";
    private static final String FARM_SILO = FARM_PROJECT + "/Silo.can";

    private static void seedFarm(final CraftingComputerBlockEntity computer) {
        // A solution lists its projects by their files, folder and file, not by name.
        seed(computer, FARM_DIR + "/" + SolutionFile.fileName("Farm"), FileType.SLN,
                new SolutionFile("Farm", List.of(SolutionFile.projectPath("Farm")), "Farm").write());
        seed(computer, FARM_PROJECT + "/" + ProjectFile.fileName("Farm"), FileType.CANPROJ,
                new ProjectFile("Farm", ProjectFile.Kind.CONSOLE, ProjectTemplate.LANGUAGE,
                        List.of("Program.can", "Silo.can"), List.of(), ProjectFile.defaultEntry("Farm")).write());
        seed(computer, FARM_PROJECT + "/Program.can", FileType.CAN, "using System.IO.*; namespace Farm; "
                + "class Program { static void Main() { Console.PrintLine(\"farm\"); } }");
        seed(computer, FARM_SILO, FileType.CAN, "namespace Farm; class Silo { public long Stored; }");
    }

    private static void seed(final CraftingComputerBlockEntity computer, final String path, final FileType type,
                             final String text) {
        DiskFilesystem.write(computer.systemDisk(), path, type, text, Long.MAX_VALUE, FilesystemKind.HIERARCHICAL);
    }

    /** A machine with the studio, at its desktop, the studio open on the seeded solution with a file on a tab. */
    private static ClientTestContext studioOnFarm(final ClientTestContext ctx) {
        return ctx.thenBuild(0, world -> {
                    final CraftingComputerBlockEntity computer = world.placeRunningCraftingComputer(COMPUTER);
                    computer.installOs(FRAMES_XP);
                    for (final String id : new String[] {"virtual_studio", "cannonc", "cannonrt"}) {
                        computer.console().install(program(id).toString());
                    }
                    seedFarm(computer);
                    world.placeMonitor(MONITOR, Direction.EAST);
                })
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).launcherLabels().contains(STUDIO_LAUNCHER),
                        SCREEN_WAIT, "the studio to be listed in Start")
                .then(0, () -> launch(ctx, STUDIO_LAUNCHER))
                .thenWaitUntil(() -> studio(ctx) != null && studio(ctx).onStartWindow(),
                        SCREEN_WAIT, "the studio to open on its Start Window")
                .then(SETTLE, () -> studio(ctx).openSolutionFolder(FARM_DIR))
                .thenWaitUntil(() -> "Farm".equals(studio(ctx).solutionName())
                                && studio(ctx).explorerLabels().contains("Silo.can"),
                        SCREEN_WAIT, "the solution to open with its sources in the tree")
                .then(SETTLE, () -> studio(ctx).openFile(FARM_SILO))
                .thenWaitUntil(() -> FARM_SILO.equals(studio(ctx).openFile()), SCREEN_WAIT,
                        "the file to be on a tab");
    }

    /**
     * The machine remembers what the studio had open, not only that it was open: after the game itself
     * was closed (every kept instance forgotten), the studio comes back on the same solution with the
     * same file on its tab.
     */
    @ClientTest(timeoutTicks = 3000)
    public static void virtualStudio_comesBackOnItsSolutionAfterTheGameWasClosed(final ClientTestContext ctx) {
        studioOnFarm(ctx)
                .thenScreenshot(2, "before-leaving")
                .then(SETTLE, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAwaitNoScreen(SCREEN_WAIT)
                // What the game being closed does to the client: nothing of the programs' insides survives.
                .then(SETTLE, DesktopScreen::forgetClientState)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenWaitUntil(() -> studio(ctx) != null && !studio(ctx).onStartWindow()
                                && "Farm".equals(studio(ctx).solutionName()) && FARM_SILO.equals(studio(ctx).openFile()),
                        SCREEN_WAIT * 3, "the studio to come back on its solution with the file on its tab")
                .thenScreenshot(2, "came-back");
    }

    /**
     * The tree is worked with the right button: a source is deleted from its row, off the disk and out
     * of the project, and a project gets a new item from its row, written into the project file.
     */
    @ClientTest(timeoutTicks = 3000)
    public static void virtualStudio_deletesAndAddsSourcesFromTheTree(final ClientTestContext ctx) {
        studioOnFarm(ctx)
                .then(SETTLE, () -> ctx.rightClickDesktop(studio(ctx).explorerRowPoint("Silo.can")))
                .thenWaitUntil(() -> studio(ctx).treeMenuOpen(), SCREEN_WAIT, "the tree's menu to open")
                .thenAssert(0, () -> studio(ctx).treeMenuLabels().contains("Delete")
                                && studio(ctx).treeMenuLabels().contains("Exclude From Project"),
                        "a source's menu offers Delete and Exclude From Project")
                .thenScreenshot(2, "source-menu")
                .then(SETTLE, () -> ctx.clickDesktop(studio(ctx).treeMenuPoint("Delete")))
                .thenWaitUntil(() -> studio(ctx).deleteQuestionOpen(), SCREEN_WAIT, "the delete question")
                .then(SETTLE, () -> studio(ctx).confirmDelete())
                .thenWaitUntilServer(level -> !onDisk(ctx, level, FARM_SILO)
                                && !diskText(ctx, level, FARM_PROJECT + "/Farm.canproj").contains("Silo.can"),
                        SCREEN_WAIT, "the file to be gone from the disk and from the project file",
                        level -> "project=" + diskText(ctx, level, FARM_PROJECT + "/Farm.canproj"))
                .thenWaitUntil(() -> !studio(ctx).explorerLabels().contains("Silo.can")
                                && !FARM_SILO.equals(studio(ctx).openFile()),
                        SCREEN_WAIT, "the tree and the tabs to let the file go")
                .then(SETTLE, () -> ctx.rightClickDesktop(studio(ctx).explorerRowPoint("v Farm *")))
                .thenWaitUntil(() -> studio(ctx).treeMenuOpen(), SCREEN_WAIT, "the project's menu to open")
                .then(SETTLE, () -> ctx.clickDesktop(studio(ctx).treeMenuPoint("Add New Item...")))
                .thenWaitUntil(() -> studio(ctx).askOpen(), SCREEN_WAIT, "the name question")
                .then(SETTLE, () -> studio(ctx).answerAsk("Barn.can"))
                .thenWaitUntil(() -> (FARM_PROJECT + "/Barn.can").equals(studio(ctx).openFile()), SCREEN_WAIT,
                        "the new item to be on a tab")
                .then(0, () -> ctx.type("namespace Farm; class Barn { }"))
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_S, GLFW.GLFW_MOD_CONTROL))
                .thenWaitUntilServer(level -> onDisk(ctx, level, FARM_PROJECT + "/Barn.can")
                                && diskText(ctx, level, FARM_PROJECT + "/Farm.canproj").contains("Barn.can"),
                        SCREEN_WAIT, "the new item to be on the disk and in the project file",
                        level -> "project=" + diskText(ctx, level, FARM_PROJECT + "/Farm.canproj"))
                .thenWaitUntil(() -> studio(ctx).explorerLabels().contains("Barn.can"), SCREEN_WAIT,
                        "the tree to list the new item")
                .thenScreenshot(2, "added-item");
    }

    /** The same program, run with F5: the editor builds it and runs it at its own terminal, in one key. */
    @ClientTest(timeoutTicks = 3000)
    public static void virtualStudioCode_runsTheOpenFileWithF5(final ClientTestContext ctx) {
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
                .thenScreenshot(2, "welcome")
                .then(SETTLE, () -> editor(ctx).openFolder("progs"))
                .then(SETTLE, () -> editor(ctx).openFile("progs/hello.can"))
                .thenWaitUntil(() -> editor(ctx).openFile().equals("progs/hello.can"),
                        SCREEN_WAIT, "the editor to open the new file")
                .then(0, () -> ctx.type(SOURCE))
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_S, GLFW.GLFW_MOD_CONTROL))
                .thenWaitUntilServer(level -> onDisk(ctx, level, "progs/hello.can"),
                        SCREEN_WAIT, "the program to be on the machine's disk", level -> "")
                // F5: the build goes first and the run waits for it, at the terminal panel.
                .then(SETTLE, () -> ctx.key(GLFW.GLFW_KEY_F5))
                .thenWaitUntil(() -> editor(ctx).terminalText().contains("it runs"),
                        SCREEN_WAIT * 3, "the program to print its line after F5")
                .thenWaitUntilServer(level -> onDisk(ctx, level, "progs/build/hello.asm"),
                        SCREEN_WAIT, "the listing to be in the folder's build directory", level -> "")
                .thenScreenshot(2, "ran-f5");
    }

    /**
     * The tree opens on a double click and only picks on one, and the empty end of the tab strip is
     * nothing to click: it used to be the last tab's close mark, and a stray click there shut the tabs
     * one at a time.
     */
    @ClientTest(timeoutTicks = 3000)
    public static void virtualStudioCode_opensOnADoubleClickAndKeepsItsTabs(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    final CraftingComputerBlockEntity computer = world.placeRunningCraftingComputer(COMPUTER);
                    computer.installOs(FRAMES_XP);
                    for (final String id : new String[] {"virtual_studio_code", "cannonc", "cannonrt"}) {
                        computer.console().install(program(id).toString());
                    }
                    seed(computer, "progs/hello.can", FileType.CAN, SOURCE);
                    world.placeMonitor(MONITOR, Direction.EAST);
                })
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).launcherLabels().contains(EDITOR_LAUNCHER),
                        SCREEN_WAIT, "the editor to be listed in Start")
                .then(0, () -> launch(ctx, EDITOR_LAUNCHER))
                .thenWaitUntil(() -> editor(ctx) != null, SCREEN_WAIT, "the editor window to open")
                .then(SETTLE, () -> editor(ctx).openFolder("progs"))
                .thenWaitUntil(() -> editor(ctx).sideLabels().contains("hello.can"), SCREEN_WAIT,
                        "the folder's tree to list the file")
                .thenAssert(0, () -> !editor(ctx).sideLabels().contains("OPEN EDITORS"),
                        "the side panel is the folder alone")
                .then(SETTLE, () -> ctx.clickDesktop(editor(ctx).sideRowPoint("hello.can")))
                // Well past the double-click window: one click has to leave the file where it is.
                .thenAssert(20, () -> editor(ctx).openFile().isEmpty(), "one click only picks the file")
                .then(SETTLE, () -> {
                    final int[] row = editor(ctx).sideRowPoint("hello.can");
                    ctx.clickDesktop(row);
                    ctx.clickDesktop(row);
                })
                .thenWaitUntil(() -> "progs/hello.can".equals(editor(ctx).openFile()), SCREEN_WAIT,
                        "two clicks to open the file onto a tab")
                .thenScreenshot(2, "opened-by-double-click")
                .then(SETTLE, () -> ctx.clickDesktop(editor(ctx).tabStripEnd()))
                .thenAssert(SETTLE, () -> "progs/hello.can".equals(editor(ctx).openFile()),
                        "a click past the last tab closes nothing");
    }

    /** A type the player wrote in one file, reached from another in the same folder. */
    private static final String SILO = "namespace Farm; class Silo { public long Stored; "
            + "public void Fill(long amount) { Stored = Stored + amount; } }";
    /** The file being written, stopped right after the dot the list should open at. */
    private static final String FARM = "namespace Farm; class Farm { private Silo silo = new Silo(); "
            + "public void Go() { silo.";

    /**
     * The suggestion list knows the program's own types, not only the language's: a file declares a
     * class, the file beside it reaches into a variable of that class, and the list names its members.
     */
    @ClientTest(timeoutTicks = 3000)
    public static void virtualStudioCode_suggestsWhatTheProgramItselfDeclares(final ClientTestContext ctx) {
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
                .then(SETTLE, () -> editor(ctx).openFolder("progs"))
                .then(SETTLE, () -> editor(ctx).openFile("progs/silo.can"))
                .thenWaitUntil(() -> editor(ctx).openFile().equals("progs/silo.can"),
                        SCREEN_WAIT, "the editor to open the first file")
                .then(0, () -> ctx.type(SILO))
                .then(0, () -> ctx.key(GLFW.GLFW_KEY_S, GLFW.GLFW_MOD_CONTROL))
                .thenWaitUntilServer(level -> onDisk(ctx, level, "progs/silo.can"),
                        SCREEN_WAIT, "the first file to be on the machine's disk", level -> "")
                .then(SETTLE, () -> editor(ctx).openFile("progs/farm.can"))
                .thenWaitUntil(() -> editor(ctx).openFile().equals("progs/farm.can"),
                        SCREEN_WAIT, "the editor to open the second file")
                .then(0, () -> ctx.type(FARM))
                .then(SETTLE, () -> ctx.key(GLFW.GLFW_KEY_SPACE, GLFW.GLFW_MOD_CONTROL))
                .thenWaitUntil(() -> editor(ctx).completionLabels().contains("Fill"),
                        SCREEN_WAIT, "the list to name what the silo can do, from the file beside this one")
                .then(0, () -> {
                    final List<String> offered = editor(ctx).completionLabels();
                    if (!offered.contains("Stored")) {
                        throw new AssertionError("the silo's field is missing from " + offered);
                    }
                })
                .thenScreenshot(2, "own-type-suggestions");
    }

    /**
     * A project made the way the studio wants it: from the Start Window, through the wizard, into a
     * solution with one project, built as a solution and started, with the program's line at the terminal.
     */
    @ClientTest(timeoutTicks = 3000)
    public static void virtualStudio_createsASolutionBuildsItAndStartsIt(final ClientTestContext ctx) {
        ctx.thenBuild(0, world -> {
                    final CraftingComputerBlockEntity computer = world.placeRunningCraftingComputer(COMPUTER);
                    computer.installOs(FRAMES_XP);
                    for (final String id : new String[] {"virtual_studio", "cannonc", "cannonrt"}) {
                        computer.console().install(program(id).toString());
                    }
                    world.placeMonitor(MONITOR, Direction.EAST);
                })
                .thenTeleport(SETTLE, PLAYER_AT_MONITOR, Direction.WEST)
                .thenRightClick(SETTLE, MONITOR)
                .thenAwaitScreen(DesktopScreen.class, BOOT_WAIT)
                .thenWaitUntil(() -> ctx.screen(DesktopScreen.class).launcherLabels().contains(STUDIO_LAUNCHER),
                        SCREEN_WAIT, "the studio to be listed in Start")
                .then(0, () -> launch(ctx, STUDIO_LAUNCHER))
                .thenWaitUntil(() -> studio(ctx) != null && studio(ctx).onStartWindow(),
                        SCREEN_WAIT, "the studio to open on its Start Window")
                .thenScreenshot(2, "start-window")
                // The card on the Start Window, clicked where it is drawn, opens the wizard.
                .then(SETTLE, () -> ctx.clickDesktop(studio(ctx).startLinkCenter("Create a new project")))
                .thenWaitUntil(() -> studio(ctx).wizardOpen(), SCREEN_WAIT, "the New Project wizard to open")
                .thenScreenshot(2, "wizard-templates")
                .then(SETTLE, () -> studio(ctx).chooseTemplate(ProjectTemplate.CONSOLE_APP))
                .thenScreenshot(2, "wizard-configure")
                .then(SETTLE, () -> studio(ctx).createProject(ProjectTemplate.CONSOLE_APP, "Hello"))
                .thenWaitUntilServer(level -> onDisk(ctx, level, "progs/Hello/Hello.sln")
                                && onDisk(ctx, level, "progs/Hello/Hello/Hello.canproj")
                                && onDisk(ctx, level, "progs/Hello/Hello/Hello.can"),
                        SCREEN_WAIT, "the solution, project and source to be on the disk", level -> "")
                .thenWaitUntil(() -> studio(ctx).solutionName().equals("Hello")
                                && studio(ctx).projectNames().contains("Hello"),
                        SCREEN_WAIT, "the studio to open the solution it made")
                .thenWaitUntil(() -> studio(ctx).openFile().equals("progs/Hello/Hello/Hello.can"),
                        SCREEN_WAIT, "the first source to be open")
                .thenAssert(0, () -> studio(ctx).text().contains("Hello from Hello"),
                        "the template's program is in the editor")
                .thenScreenshot(2, "solution-open")
                // The right button on the code: the clipboard and the refactorings, then Escape puts it away.
                .then(SETTLE, () -> ctx.rightClickDesktop(studio(ctx).editorCenter()))
                .thenWaitUntil(() -> studio(ctx).editorMenuOpen(), SCREEN_WAIT, "the code's menu to open")
                .thenAssert(0, () -> studio(ctx).editorMenuLabels().contains("Quick Actions and Refactorings"),
                        "the refactorings are on the code's menu")
                .thenScreenshot(2, "editor-menu")
                .then(SETTLE, () -> ctx.key(GLFW.GLFW_KEY_ESCAPE))
                .thenAssert(0, () -> !studio(ctx).editorMenuOpen() && ctx.screen(DesktopScreen.class) != null,
                        "Escape closes the menu and nothing more")
                .then(SETTLE, () -> studio(ctx).buildSolution())
                .thenWaitUntil(() -> studio(ctx).outputLines().stream().anyMatch(l -> l.startsWith("Build succeeded")),
                        SCREEN_WAIT, "the build to succeed")
                .thenWaitUntilServer(level -> onDisk(ctx, level, "progs/Hello/Hello/build/Hello.asm"),
                        SCREEN_WAIT, "the listing to be where the project file says", level -> "")
                .thenScreenshot(2, "built")
                .then(SETTLE, () -> studio(ctx).startProgram())
                // Start runs at the studio's own terminal, in its dock, not in a window of its own.
                .thenWaitUntil(() -> studio(ctx).terminalText().contains("Hello from Hello"),
                        SCREEN_WAIT * 3, "the started program to print its line at the studio's terminal")
                .thenAssert(0, () -> app(ctx, TERMINAL, ShellApp.class) == null,
                        "no terminal window opened for it")
                .thenScreenshot(2, "started")
                // Once it has returned, the machine is not running it any more: the Task Manager must not list it.
                .thenWaitUntilServer(level -> computerOf(ctx, level).cannon().all().isEmpty(), SCREEN_WAIT,
                        "the finished program to leave the machine's process list",
                        level -> "still listed: " + computerOf(ctx, level).cannon().all().stream()
                                .map(one -> one.name() + "/" + one.process().state()).toList())
                // The system's file window, opened for a solution: the folder is entered, the file picked, Open opens it.
                .then(SETTLE, () -> studio(ctx).showOpenSolutionDialog())
                .thenWaitUntil(() -> studio(ctx).dialog().isOpen() && studio(ctx).dialog().rowNames().contains("Hello"),
                        SCREEN_WAIT, "the Open Project/Solution window to list the projects folder")
                /*
                 * The file window is a window of its own over the studio, listed with the studio on the
                 * panel rather than as a program of its own.
                 */
                .thenAssert(0, () -> ctx.screen(DesktopScreen.class).dialogTitles().contains("Open Project/Solution")
                                && ctx.screen(DesktopScreen.class).dialogWindowFor(STUDIO_LAUNCHER) != null
                                && java.util.Collections.frequency(ctx.screen(DesktopScreen.class).taskEntryLabels(), STUDIO_LAUNCHER) == 1
                                && !ctx.screen(DesktopScreen.class).taskEntryLabels().contains("Open Project/Solution"),
                        "the Open window is a dialog of the studio, under the studio's one panel entry")
                .thenScreenshot(2, "open-dialog")
                .then(SETTLE, () -> {
                    ctx.clickDesktop(studio(ctx).dialog().rowPoint("Hello"));
                    ctx.clickDesktop(studio(ctx).dialog().rowPoint("Hello"));
                })
                .thenWaitUntil(() -> studio(ctx).dialog().rowNames().contains("Hello.sln"), SCREEN_WAIT,
                        "a double click to go into the folder")
                .then(SETTLE, () -> ctx.clickDesktop(studio(ctx).dialog().rowPoint("Hello.sln")))
                .thenAssert(0, () -> studio(ctx).dialog().typedName().equals("Hello.sln"), "picking a row fills in the name")
                .thenScreenshot(2, "open-dialog-picked")
                .then(SETTLE, () -> ctx.clickDesktop(studio(ctx).dialog().primaryPoint()))
                .thenWaitUntil(() -> !studio(ctx).dialog().isOpen() && studio(ctx).solutionName().equals("Hello"),
                        SCREEN_WAIT, "Open to open the solution it named");
    }
}
