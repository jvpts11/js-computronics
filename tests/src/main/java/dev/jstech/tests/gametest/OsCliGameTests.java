/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.HardwareItems;
import dev.jstech.computronics.JsComputronics;
import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.hardware.DiskSize;
import dev.jstech.computronics.hardware.StorageTier;
import dev.jstech.computronics.operation.payload.ConsoleInitPayload;
import dev.jstech.computronics.os.FilesystemKind;
import dev.jstech.computronics.os.ShellFamily;
import dev.jstech.computronics.os.fs.DiskFilesystem;
import dev.jstech.computronics.os.fs.FileType;
import dev.jstech.computronics.program.ServerCliComputer;
import dev.jstech.computronics.program.cli.CliCommand;
import dev.jstech.computronics.program.cli.CliCommands;
import dev.jstech.computronics.program.cli.CliComputer;
import dev.jstech.tests.JsTests;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/**
 * In-world integration tests for the filesystem CLI commands (dir, type, del, run).
 *
 * <p>Each test places a Mainframe with MC-DOS installed (FLAT filesystem), writes files
 * directly via {@link DiskFilesystem}, and then drives the command handlers through the same
 * methods the dispatcher invokes. No client screen is required; the {@link ServerCliComputer}
 * is constructed directly from the block entity.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class OsCliGameTests {

    private OsCliGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    /**
     * The MC-DOS OS resource location. MC-DOS runs on the {@code dos} kernel (FLAT filesystem)
     * and is available from the Vintage era onward, so it can be installed on any Mainframe.
     */
    private static final ResourceLocation MC_DOS =
            ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "mc_dos");

    // -------------------------------------------------------------------------
    // the console-open payload
    // -------------------------------------------------------------------------

    /**
     * Every shell's command list, live installer included, must encode into the payload that opens a
     * console. A usage line past the wire cap does not truncate: it fails to encode and disconnects the
     * player as the console opens, on every machine that offers the command.
     */
    @GameTest(template = ARENA)
    public static void consoleInit_everyCommandUsageFitsTheWire(final GameTestHelper helper) {
        for (final ShellFamily family : ShellFamily.values()) {
            for (final boolean live : new boolean[] {false, true}) {
                final List<ConsoleInitPayload.WireCommand> commands = new ArrayList<>();
                for (final CliCommand command : CliCommands.commandsFor(family, live)) {
                    commands.add(new ConsoleInitPayload.WireCommand(command.name(), command.usage()));
                }
                final ConsoleInitPayload payload = new ConsoleInitPayload(BlockPos.ZERO, List.of(), commands, List.of());
                final RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                        helper.getLevel().registryAccess());
                try {
                    ConsoleInitPayload.STREAM_CODEC.encode(buf, payload);
                } catch (final RuntimeException e) {
                    helper.fail("the " + family + (live ? " live" : "") + " shell's commands do not encode: "
                            + e.getMessage());
                    return;
                }
                helper.assertTrue(ConsoleInitPayload.STREAM_CODEC.decode(buf).commands().equals(commands),
                        "the " + family + (live ? " live" : "") + " shell's commands survive the round trip");
            }
        }
        helper.succeed();
    }

    // -------------------------------------------------------------------------
    // dir (listDisk)
    // -------------------------------------------------------------------------

    /**
     * After writing a file directly via {@link DiskFilesystem}, {@code dir} (listDisk) must include
     * that file in its listing.
     */
    @GameTest(template = ARENA)
    public static void cliDir_listsWrittenFile(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // Write a file directly onto the system disk.
                    final ItemStack disk = mainframe.systemDisk();
                    helper.assertFalse(disk.isEmpty(), "system disk must be present after mc_dos install");
                    DiskFilesystem.write(disk, "test.iql", FileType.IQL, "SELECT * FROM items",
                            1000L, FilesystemKind.FLAT);

                    // Drive the listDisk method directly.
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final CliComputer.FsResult result = cli.listDisk("");

                    helper.assertTrue(result.ok(),
                            "listDisk must succeed with mc_dos installed; got: " + result.message());
                    final List<CliComputer.FsEntry> entries = result.entries();
                    final boolean found = entries.stream().anyMatch(e -> e.name().equals("test.iql"));
                    helper.assertTrue(found,
                            "listDisk must include test.iql; got entries: " + entries);
                })
                .thenSucceed();
    }

    /**
     * Without a system disk (no OS), {@code dir} must report an error rather than throwing.
     */
    @GameTest(template = ARENA)
    public static void cliDir_failsWithoutOs(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        // Place a Mainframe with valid hardware but no OS installed.
        final MainframeBlockEntity mainframe = placeMainframeNoDisk(helper, pos);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final CliComputer.FsResult result = cli.listDisk("");
                    helper.assertFalse(result.ok(),
                            "listDisk must fail when no OS is installed");
                })
                .thenSucceed();
    }

    // -------------------------------------------------------------------------
    // type (readFile)
    // -------------------------------------------------------------------------

    /**
     * {@code type} (readFile) must return the exact content that was written to the file.
     */
    @GameTest(template = ARENA)
    public static void cliType_returnsFileContent(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);
        final String expected = "SELECT 32 Cobblestone";

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ItemStack disk = mainframe.systemDisk();
                    DiskFilesystem.write(disk, "query.iql", FileType.IQL, expected,
                            1000L, FilesystemKind.FLAT);

                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final CliComputer.FsResult result = cli.readFile("query.iql");

                    helper.assertTrue(result.ok(),
                            "readFile must succeed for an existing .iql file; got: " + result.message());
                    helper.assertTrue(expected.equals(result.message()),
                            "readFile must return the written content; got: " + result.message());
                })
                .thenSucceed();
    }

    /**
     * {@code type} on a missing file must fail with a clear error, not throw.
     */
    @GameTest(template = ARENA)
    public static void cliType_failsOnMissingFile(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final CliComputer.FsResult result = cli.readFile("nonexistent.iql");
                    helper.assertFalse(result.ok(),
                            "readFile on a missing file must return a failure result");
                })
                .thenSucceed();
    }

    // -------------------------------------------------------------------------
    // del (deleteFile)
    // -------------------------------------------------------------------------

    /**
     * After {@code del} (deleteFile) succeeds, a subsequent {@code type} (readFile) on the same
     * path must fail — confirming the file was actually removed from the disk.
     */
    @GameTest(template = ARENA)
    public static void cliDel_removesFile_andSubsequentTypeErrors(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ItemStack disk = mainframe.systemDisk();
                    DiskFilesystem.write(disk, "temp.txt", FileType.TXT, "hello",
                            1000L, FilesystemKind.FLAT);

                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());

                    // Deletion must succeed.
                    final CliComputer.FsResult delResult = cli.deleteFile("temp.txt");
                    helper.assertTrue(delResult.ok(),
                            "deleteFile must succeed for an existing file; got: " + delResult.message());

                    // The file must no longer be readable.
                    final CliComputer.FsResult readResult = cli.readFile("temp.txt");
                    helper.assertFalse(readResult.ok(),
                            "readFile after del must fail; got ok with: " + readResult.message());
                })
                .thenSucceed();
    }

    /**
     * {@code del} on a non-existent file must fail rather than silently succeeding.
     */
    @GameTest(template = ARENA)
    public static void cliDel_failsOnMissingFile(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final CliComputer.FsResult result = cli.deleteFile("ghost.txt");
                    helper.assertFalse(result.ok(),
                            "deleteFile on a missing file must return failure");
                })
                .thenSucceed();
    }

    // -------------------------------------------------------------------------
    // run (runScript)
    // -------------------------------------------------------------------------

    /**
     * {@code run} on a stored {@code .iql} file must parse and execute the statement, returning
     * an OK result via the same IQL dispatch path as the {@code operation} command.
     *
     * <p>The Mainframe has no network here, so an effecting statement that reaches the network
     * (e.g. SELECT) will fail at dispatch; we assert on the {@link CliComputer.FsResult#ok()}
     * flag of the script execution itself: a parse error or missing-file error counts as a test
     * failure; a dispatch-level failure ("no Mainframe") is acceptable and still proves the
     * script was read and parsed correctly.
     */
    @GameTest(template = ARENA)
    public static void cliRun_executesIqlScript(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ItemStack disk = mainframe.systemDisk();
                    // A syntactically valid effecting IQL statement.
                    DiskFilesystem.write(disk, "daily.iql", FileType.IQL,
                            "SELECT 64 Cobblestone", 1000L, FilesystemKind.FLAT);

                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final CliComputer.FsResult result = cli.runScript("daily.iql");

                    // The script must have been found and parsed successfully.
                    // A network-dispatch failure is acceptable (no network here);
                    // a file-not-found or syntax error is a test failure.
                    final boolean parsedOk = result.ok()
                            || (result.opResult() != null)
                            || (!result.ok() && result.message().contains("Mainframe"));
                    helper.assertTrue(parsedOk,
                            "runScript must reach dispatch (not fail on missing file / syntax); got: "
                                    + result.message());
                })
                .thenSucceed();
    }

    /**
     * {@code run} on a non-{@code .iql} file must fail with a clear extension error.
     */
    @GameTest(template = ARENA)
    public static void cliRun_rejectsNonIqlFile(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ItemStack disk = mainframe.systemDisk();
                    DiskFilesystem.write(disk, "notes.txt", FileType.TXT, "hello",
                            1000L, FilesystemKind.FLAT);

                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final CliComputer.FsResult result = cli.runScript("notes.txt");
                    helper.assertFalse(result.ok(),
                            "runScript on a .txt file must fail");
                    helper.assertTrue(result.message().contains("iql"),
                            "error message must mention .iql; got: " + result.message());
                })
                .thenSucceed();
    }

    /**
     * {@code run} on a missing file must fail rather than throwing.
     */
    @GameTest(template = ARENA)
    public static void cliRun_failsOnMissingFile(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final CliComputer.FsResult result = cli.runScript("missing.iql");
                    helper.assertFalse(result.ok(),
                            "runScript on a missing file must return failure");
                })
                .thenSucceed();
    }

    // -------------------------------------------------------------------------
    // write (writeFile)
    // -------------------------------------------------------------------------

    /**
     * {@code write} (writeFile) must create a file whose content {@code type} (readFile) then returns,
     * proving the CLI write path persists onto the system disk.
     */
    @GameTest(template = ARENA)
    public static void cliWrite_createsFileReadableByType(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);
        final String content = "print hello world";

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final CliComputer.FsResult write = cli.writeFile("notes.txt", content);
                    helper.assertTrue(write.ok(),
                            "writeFile must succeed for a .txt file; got: " + write.message());

                    final CliComputer.FsResult read = cli.readFile("notes.txt");
                    helper.assertTrue(read.ok() && content.equals(read.message()),
                            "readFile must return the written content; got: " + read.message());
                })
                .thenSucceed();
    }

    /**
     * {@code write} must refuse a read-only file type (a {@code .dat} projection name): items leave
     * only via the Network Interactor, so {@code .dat} is never a real, writable file.
     */
    @GameTest(template = ARENA)
    public static void cliWrite_rejectsReadOnlyType(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final CliComputer.FsResult write = cli.writeFile("cobblestone.dat", "x");
                    helper.assertFalse(write.ok(),
                            "writeFile must reject a .dat (read-only) file type");
                })
                .thenSucceed();
    }

    /**
     * {@code write} without an installed OS (no system disk) must fail, not throw.
     */
    @GameTest(template = ARENA)
    public static void cliWrite_failsWithoutOs(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeNoDisk(helper, pos);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final CliComputer.FsResult write = cli.writeFile("a.txt", "hi");
                    helper.assertFalse(write.ok(),
                            "writeFile must fail when no OS is installed");
                })
                .thenSucceed();
    }

    // -------------------------------------------------------------------------
    // DOS directory navigation and filesystem verbs
    // -------------------------------------------------------------------------

    /** {@code cd} into a subdirectory updates the current location, and {@code cd ..} returns. */
    @GameTest(template = ARENA)
    public static void cliCd_changesAndReportsCurrentLocation(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    helper.assertTrue(cli.makeDir("Work").ok(), "mkdir Work must succeed");
                    helper.assertTrue(cli.changeDir("Work").ok(), "cd Work must succeed");
                    helper.assertTrue(cli.currentLocation().dosPath().equals("C:\\Work"),
                            "cwd must be C:\\Work; got " + cli.currentLocation().dosPath());
                    helper.assertTrue(cli.changeDir("..").ok(), "cd .. must succeed");
                    helper.assertTrue(cli.currentLocation().isRoot(),
                            "cd .. from C:\\Work must return to the root");
                    helper.assertFalse(cli.changeDir("Nope").ok(),
                            "cd into a missing directory must fail");
                })
                .thenSucceed();
    }

    /** {@code dir} lists subdirectories as {@code <DIR>} entries alongside files. */
    @GameTest(template = ARENA)
    public static void cliDir_listsSubdirectories(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    helper.assertTrue(cli.makeDir("Docs").ok(), "mkdir Docs must succeed");
                    final CliComputer.FsResult listing = cli.listDisk("");
                    helper.assertTrue(listing.ok(), "dir must succeed");
                    final boolean hasDir = listing.entries().stream()
                            .anyMatch(e -> e.isDir() && e.name().equals("Docs"));
                    helper.assertTrue(hasDir,
                            "dir must list the Docs subdirectory; got " + listing.entries());
                })
                .thenSucceed();
    }

    /** {@code rmdir} removes an empty directory but refuses a non-empty one. */
    @GameTest(template = ARENA)
    public static void cliRmdir_removesEmptyButRefusesNonEmpty(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    helper.assertTrue(cli.makeDir("Full").ok(), "mkdir Full must succeed");
                    helper.assertTrue(cli.writeFile("Full\\note.txt", "hi").ok(),
                            "write into Full must succeed");
                    helper.assertFalse(cli.removeDir("Full").ok(),
                            "rmdir must refuse a non-empty directory");
                    helper.assertTrue(cli.deleteFile("Full\\note.txt").ok(),
                            "del of the inner file must succeed");
                    helper.assertTrue(cli.removeDir("Full").ok(),
                            "rmdir must remove the now-empty directory");
                })
                .thenSucceed();
    }

    /** {@code copy} duplicates a file, leaving the source in place. */
    @GameTest(template = ARENA)
    public static void cliCopy_duplicatesFile(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    helper.assertTrue(cli.writeFile("a.txt", "payload").ok(), "write a.txt must succeed");
                    helper.assertTrue(cli.copyPath("a.txt", "b.txt").ok(), "copy a.txt b.txt must succeed");
                    helper.assertTrue(cli.readFile("a.txt").ok(), "source must still exist after copy");
                    final CliComputer.FsResult copy = cli.readFile("b.txt");
                    helper.assertTrue(copy.ok() && copy.message().equals("payload"),
                            "copy must contain the source content");
                })
                .thenSucceed();
    }

    /** {@code move} relocates a file into another directory. */
    @GameTest(template = ARENA)
    public static void cliMove_relocatesFileIntoDirectory(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    helper.assertTrue(cli.makeDir("Dest").ok(), "mkdir Dest must succeed");
                    helper.assertTrue(cli.writeFile("m.txt", "data").ok(), "write m.txt must succeed");
                    helper.assertTrue(cli.movePath("m.txt", "Dest").ok(), "move m.txt Dest must succeed");
                    helper.assertFalse(cli.readFile("m.txt").ok(), "source must be gone after move");
                    helper.assertTrue(cli.readFile("Dest\\m.txt").ok(), "file must exist under Dest");
                })
                .thenSucceed();
    }

    /** {@code ren} renames a file, keeping it in the same directory. */
    @GameTest(template = ARENA)
    public static void cliRen_renamesFileInPlace(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    helper.assertTrue(cli.writeFile("old.txt", "x").ok(), "write old.txt must succeed");
                    helper.assertTrue(cli.renamePath("old.txt", "new.txt").ok(), "ren must succeed");
                    helper.assertFalse(cli.readFile("old.txt").ok(), "old name must be gone");
                    helper.assertTrue(cli.readFile("new.txt").ok(), "new name must exist");
                })
                .thenSucceed();
    }

    /** File arguments resolve against the current directory (relative) and from the root (absolute). */
    @GameTest(template = ARENA)
    public static void cliCwd_resolvesRelativeAndAbsolutePaths(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    helper.assertTrue(cli.makeDir("Sub").ok(), "mkdir Sub must succeed");
                    helper.assertTrue(cli.changeDir("Sub").ok(), "cd Sub must succeed");
                    // Written relative to the cwd, so it lands at Sub/rel.txt.
                    helper.assertTrue(cli.writeFile("rel.txt", "inside").ok(), "relative write must succeed");
                    helper.assertTrue(cli.readFile("rel.txt").ok(), "relative read must find the file");
                    helper.assertTrue(cli.changeDir("\\").ok(), "cd \\ must return to the root");
                    helper.assertTrue(cli.readFile("Sub\\rel.txt").ok(),
                            "absolute path must reach the file written relatively");
                })
                .thenSucceed();
    }

    /**
     * A second data disk installed in the computer appears as {@code D:}: the shell can switch to it,
     * read and write files on it independently of {@code C:}, and copy files across drives.
     */
    @GameTest(template = ARENA)
    public static void cliDrives_dataDiskIsDAndCrossDriveCopyWorks(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);
        // Add a plain (OS-less) data disk in the next disk slot; it becomes D:.
        mainframe.getInventory().setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START + 1,
                new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    // Switch to D: and work there independently of C:.
                    helper.assertTrue(cli.changeDrive('D').ok(), "switching to D: must succeed");
                    helper.assertTrue(cli.currentLocation().drive() == 'D',
                            "current drive must be D after switching; got " + cli.currentLocation().dosPath());
                    helper.assertTrue(cli.writeFile("note.txt", "on-d").ok(), "write on D: must succeed");
                    final CliComputer.FsResult readD = cli.readFile("note.txt");
                    helper.assertTrue(readD.ok() && readD.message().equals("on-d"),
                            "read on D: must return the D: content");
                    // Back to C:; the D: file must not be visible there.
                    helper.assertTrue(cli.changeDrive('C').ok(), "switching back to C: must succeed");
                    helper.assertFalse(cli.readFile("note.txt").ok(),
                            "the D: file must not exist on C:");
                    // Cross-drive copy C: -> D:.
                    helper.assertTrue(cli.writeFile("src.txt", "payload").ok(), "write on C: must succeed");
                    helper.assertTrue(cli.copyPath("src.txt", "D:\\copy.txt").ok(),
                            "cross-drive copy C: -> D: must succeed");
                    final CliComputer.FsResult copied = cli.readFile("D:\\copy.txt");
                    helper.assertTrue(copied.ok() && copied.message().equals("payload"),
                            "the cross-drive copy on D: must hold the source content");
                    // An unmapped drive letter fails cleanly.
                    helper.assertFalse(cli.changeDrive('Z').ok(), "switching to an unmapped drive must fail");
                })
                .thenSucceed();
    }

    // -------------------------------------------------------------------------
    // Settings store and the config command
    // -------------------------------------------------------------------------

    /** setConfig routes to the name, the settings store (clamping), and the disk's network share. */
    @GameTest(template = ARENA)
    public static void config_routesNameSettingsAndNetshare(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    helper.assertTrue(cli.setConfig("name", "CORE-1").ok(), "config name must succeed");
                    helper.assertTrue(mainframe.console().computerName().equals("CORE-1"),
                            "the computer name must be stored");
                    helper.assertTrue(cli.setConfig("clock", "12h").ok(), "config clock must succeed");
                    helper.assertTrue(mainframe.console().settings().clock12h(), "clock must be 12h");
                    // Out-of-range brightness is clamped, not rejected.
                    helper.assertTrue(cli.setConfig("brightness", "150").ok(), "config brightness must succeed");
                    helper.assertTrue(mainframe.console().settings().brightness() == 100,
                            "brightness must clamp to 100");
                    // Netshare routes to the system disk's public-share component.
                    helper.assertTrue(cli.setConfig("netshare", "600").ok(), "config netshare must succeed");
                    helper.assertTrue(dev.jstech.computronics.item.DiskItem
                                    .publicPermille(mainframe.systemDisk()) == 600,
                            "netshare must set the disk public permille");
                    helper.assertFalse(cli.setConfig("frobnicate", "1").ok(), "an unknown key must fail");
                    final String summary = String.join("\n", cli.configSummary());
                    helper.assertTrue(summary.contains("name") && summary.contains("netshare")
                                    && summary.contains("clock"),
                            "the summary must list the settings; got: " + summary);
                })
                .thenSucceed();
    }

    /** Settings survive a console NBT save/load round-trip. */
    @GameTest(template = ARENA)
    public static void config_persistsAcrossConsoleReload(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    cli.setConfig("clock", "12h");
                    cli.setConfig("accent", "3A6AE0");
                    final net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                    mainframe.console().save(tag);
                    final dev.jstech.computronics.program.ComputerConsoleState reloaded =
                            new dev.jstech.computronics.program.ComputerConsoleState();
                    reloaded.load(tag);
                    helper.assertTrue(reloaded.settings().clock12h(), "clock must survive a reload");
                    helper.assertTrue(reloaded.settings().accent() == 0xFF3A6AE0,
                            "accent must survive a reload");
                })
                .thenSucceed();
    }

    /** The MC-DOS {@code config} command lists the settings and changes one through the shell. */
    @GameTest(template = ARENA)
    public static void configCommand_listsAndSetsThroughTheShell(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final dev.jstech.computronics.program.cli.CliShell shell =
                            dev.jstech.computronics.program.cli.CliCommands.newShell(52);
                    final var listed = shell.run("config", cli);
                    helper.assertTrue(listed.lines().stream().anyMatch(l -> l.text().contains("clock")),
                            "'config' must list the clock setting");
                    shell.run("config clock 12h", cli);
                    helper.assertTrue(mainframe.console().settings().clock12h(),
                            "'config clock 12h' must set the clock through the shell");
                })
                .thenSucceed();
    }

    /** A theme preset bundles an accent and a wallpaper; "system" clears both back to default. */
    @GameTest(template = ARENA)
    public static void config_themePresetBundlesAccentAndWallpaper(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithMcDos(helper, pos);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    helper.assertTrue(cli.setConfig("theme", "ocean").ok(), "config theme ocean must succeed");
                    helper.assertTrue(mainframe.console().settings().accent() == 0xFF12A26F,
                            "ocean must set the teal accent");
                    helper.assertTrue(mainframe.console().wallpaper().equals("winxp"),
                            "ocean must set the winxp wallpaper");
                    helper.assertTrue(cli.setConfig("theme", "system").ok(), "config theme system must succeed");
                    helper.assertTrue(mainframe.console().settings().accent() == 0,
                            "system must clear the accent");
                    helper.assertTrue(mainframe.console().wallpaper().isEmpty(),
                            "system must clear the wallpaper");
                })
                .thenSucceed();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Places a Mainframe at {@code pos} with a valid hardware build, installs MC-DOS onto the disk
     * (hierarchical filesystem), and returns the block entity. The computer is left powered on.
     */
    private static MainframeBlockEntity placeMainframeWithMcDos(
            final GameTestHelper helper, final BlockPos pos) {
        helper.setBlock(pos, ComputingModule.MAINFRAME.get());
        if (!(helper.getBlockEntity(pos) instanceof MainframeBlockEntity mainframe)) {
            throw new IllegalStateException("no MainframeBlockEntity at " + pos);
        }
        final ItemStackHandler inv = mainframe.getInventory();
        inv.setStackInSlot(MainframeBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(ComputingModule.MOTHERBOARD_MTX_P.get()));
        inv.setStackInSlot(MainframeBlockEntity.CPU_SLOTS_START,
                new ItemStack(ComputingModule.CPU_SERVO_2620.get()));
        inv.setStackInSlot(MainframeBlockEntity.RAM_SLOTS_START,
                new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        inv.setStackInSlot(MainframeBlockEntity.PSU_SLOT,
                new ItemStack(ComputingModule.PSU_650G.get()));
        // A 500 GB HDD provides 2 000 item slots; MC-DOS needs 256.
        inv.setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        mainframe.togglePower();
        // Install MC-DOS so the filesystem kind resolves to a hierarchical filesystem.
        final boolean installed = mainframe.installOs(MC_DOS);
        if (!installed) {
            throw new IllegalStateException("failed to install mc_dos on the test Mainframe");
        }
        return mainframe;
    }

    /**
     * Places a Mainframe at {@code pos} with a valid hardware build but NO disk installed (and
     * therefore no OS), and returns the block entity powered on.
     */
    private static MainframeBlockEntity placeMainframeNoDisk(
            final GameTestHelper helper, final BlockPos pos) {
        helper.setBlock(pos, ComputingModule.MAINFRAME.get());
        if (!(helper.getBlockEntity(pos) instanceof MainframeBlockEntity mainframe)) {
            throw new IllegalStateException("no MainframeBlockEntity at " + pos);
        }
        final ItemStackHandler inv = mainframe.getInventory();
        inv.setStackInSlot(MainframeBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(ComputingModule.MOTHERBOARD_MTX_P.get()));
        inv.setStackInSlot(MainframeBlockEntity.CPU_SLOTS_START,
                new ItemStack(ComputingModule.CPU_SERVO_2620.get()));
        inv.setStackInSlot(MainframeBlockEntity.RAM_SLOTS_START,
                new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        inv.setStackInSlot(MainframeBlockEntity.PSU_SLOT,
                new ItemStack(ComputingModule.PSU_650G.get()));
        // No disk slot filled — systemDisk() will return empty, resolveDiskCtx() returns null.
        mainframe.togglePower();
        return mainframe;
    }

    /** Constructs a {@link ServerCliComputer} backed by the given Mainframe, for testing. */
    private static ServerCliComputer cliFor(final MainframeBlockEntity mainframe,
                                            final ServerLevel level) {
        return new ServerCliComputer(mainframe, level);
    }

    // --- Linux -----------------------------------------------------------------------------------

    /**
     * A Linux distribution boots to a bash TTY: the OS installs on the Linux kernel (POSIX shell family),
     * lays down the Unix tree, targets the terminal, and the shell speaks POSIX verbs over the rooted tree
     * (home directory start, {@code pwd}/{@code ls}/{@code mkdir}/{@code cd}), with the DOS verbs gone.
     */
    @GameTest(template = ARENA)
    public static void linux_ubuntuBootsToBashTty(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithOs(helper, pos,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "ubuntu"));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(dev.jstech.computronics.os.boot.BootController
                                    .targetForComputer(mainframe)
                                    == dev.jstech.computronics.os.boot.BootController.BootTarget.TERMINAL_ONLY,
                            "a Linux distribution without a desktop environment must boot to the terminal");
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    helper.assertTrue(cli.shellFamily() == dev.jstech.computronics.os.ShellFamily.POSIX,
                            "the Linux kernel must give the shell the POSIX family");
                    helper.assertTrue("player@ubuntu:~$".equals(cli.prompt()),
                            "the bash prompt must start in the home directory; got " + cli.prompt());
                    final var fs = mainframe.systemDisk().get(ComputingModule.FILESYSTEM.get());
                    helper.assertTrue(fs != null && fs.hasDir("home/player") && fs.hasDir("etc"),
                            "the install must lay down the Unix tree (/home/player, /etc)");

                    final dev.jstech.computronics.program.cli.CliShell shell =
                            dev.jstech.computronics.program.cli.CliCommands.newShell(
                                    cli.shellFamily(), 52);
                    helper.assertTrue(text(shell.run("pwd", cli)).contains("/home/player"),
                            "'pwd' must print the home directory");
                    shell.run("mkdir docs", cli);
                    helper.assertTrue(text(shell.run("ls", cli)).contains("docs/"),
                            "'ls' must list the new directory with a trailing slash");
                    shell.run("cd /", cli);
                    helper.assertTrue("/".equals(text(shell.run("pwd", cli)).trim()),
                            "'cd /' must land on the root; pwd printed " + text(shell.run("pwd", cli)));
                    helper.assertTrue("player@ubuntu:/$".equals(cli.prompt()),
                            "the prompt must follow the directory; got " + cli.prompt());
                    helper.assertTrue(text(shell.run("ls", cli)).contains("home/"),
                            "'ls' at the root must show the Unix tree");
                    helper.assertTrue(text(shell.run("dir", cli)).contains("command not found"),
                            "the DOS 'dir' verb must not exist on a Linux shell");
                    helper.assertTrue(text(shell.run("df", cli)).contains("/dev/sda1"),
                            "'df' must report the system disk");
                    shell.run("cd", cli);
                    helper.assertTrue("player@ubuntu:~$".equals(cli.prompt()),
                            "'cd' with no argument must return home; got " + cli.prompt());
                })
                .thenSucceed();
    }

    /** Arch boots a zsh prompt, and every distribution shares the Linux kernel's POSIX verbs. */
    @GameTest(template = ARENA)
    public static void linux_archUsesZshPromptOnTheSameKernel(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithOs(helper, pos,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "arch"));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    helper.assertTrue("player@arch ~ %".equals(cli.prompt()),
                            "Arch must show the zsh prompt; got " + cli.prompt());
                    final var shell = dev.jstech.computronics.program.cli.CliCommands.newShell(
                            cli.shellFamily(), 52);
                    helper.assertTrue(text(shell.run("uname -a", cli)).contains("Linux arch"),
                            "'uname -a' must name the Linux kernel and the host");
                })
                .thenSucceed();
    }

    // --- Firmware boot manager -------------------------------------------------------------------

    /**
     * Dual boot: with two disks each carrying a system, the firmware's preferred boot disk decides which OS
     * boots, the choice survives an NBT round-trip, and clearing it falls back to the first disk with a system.
     */
    @GameTest(template = ARENA)
    public static void firmware_bootOrderPicksTheSystemDisk(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithOs(helper, pos, MC_DOS);
        final ResourceLocation ubuntu = ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "ubuntu");
        mainframe.getInventory().setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START + 1,
                new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(mainframe.defaultInstallSlot() == 1,
                            "the free second disk must be the default install target; got "
                                    + mainframe.defaultInstallSlot());
                    helper.assertTrue(mainframe.installOs(ubuntu, -1),
                            "installing a second OS beside the first must succeed (dual boot)");
                    helper.assertTrue(ubuntu.equals(mainframe.diskInSlot(1).get(ComputingModule.SYSTEM_OS.get())),
                            "the second OS must land on the free disk, not over the first");
                    helper.assertTrue(MC_DOS.equals(mainframe.installedOsId()),
                            "with no preference the first disk with a system boots; got " + mainframe.installedOsId());
                    mainframe.setBootDiskSlot(1);
                    helper.assertTrue(ubuntu.equals(mainframe.installedOsId()),
                            "the preferred boot disk must boot; got " + mainframe.installedOsId());
                    // The choice persists across a reload.
                    final net.minecraft.nbt.CompoundTag tag =
                            mainframe.saveWithoutMetadata(helper.getLevel().registryAccess());
                    mainframe.setBootDiskSlot(-1);
                    helper.assertTrue(MC_DOS.equals(mainframe.installedOsId()),
                            "clearing the preference must fall back to the first system disk");
                    mainframe.loadWithComponents(tag, helper.getLevel().registryAccess());
                    helper.assertTrue(mainframe.bootDiskSlot() == 1,
                            "the boot disk preference must survive NBT; got " + mainframe.bootDiskSlot());
                    helper.assertTrue(ubuntu.equals(mainframe.installedOsId()),
                            "after the reload the preferred disk must boot again");
                })
                .thenSucceed();
    }

    // --- Package managers + Mirror -----------------------------------------------------------------

    /**
     * Packages come from the network's Mirror service: without it {@code apt} cannot resolve the mirror, after
     * {@code mirror install} on the Mainframe it installs a package (here the IQL Engine service), the
     * listing marks installed packages, and a foreign distribution's manager does not exist on this one.
     */
    @GameTest(template = ARENA)
    public static void linux_packageManagerInstallsFromTheMirror(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithOs(helper, pos,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "ubuntu"));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final var shell = dev.jstech.computronics.program.cli.CliCommands.newShell(
                            cli.shellFamily(), 52);
                    helper.assertTrue(text(shell.run("apt install iqlengine", cli)).contains("could not resolve mirror"),
                            "without a Mirror, apt must fail to resolve the mirror");
                    helper.assertTrue(text(shell.run("mirror install", cli)).contains("Mirror installed"),
                            "'mirror install' must install the Mirror on the Mainframe");
                    helper.assertTrue(mainframe.isMirrorInstalled(), "the Mirror flag must be set");
                    helper.assertTrue(text(shell.run("apt install iqlengine", cli)).contains("done"),
                            "with the Mirror serving, apt must install the IQL Engine package");
                    helper.assertTrue(mainframe.isIqlEngineInstalled(),
                            "installing the iqlengine package must switch the Engine on");
                    helper.assertTrue(text(shell.run("apt install iqlengine", cli)).contains("newest version"),
                            "a second install must report the package as already installed");
                    helper.assertTrue(text(shell.run("apt search", cli)).contains("[installed]"),
                            "'apt search' must mark installed packages");
                    helper.assertTrue(text(shell.run("pacman -S mirror", cli)).contains("command not found"),
                            "pacman must not exist on an apt distribution");
                })
                .thenSucceed();
    }

    /** A source build (emerge) settles into the installed set once its completion tick has passed. */
    @GameTest(template = ARENA)
    public static void linux_sourceBuildSettlesWhenDone(final GameTestHelper helper) {
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final dev.jstech.computronics.program.ComputerConsoleState console =
                            new dev.jstech.computronics.program.ComputerConsoleState();
                    console.startBuild("jsc:example", 100L);
                    helper.assertTrue(console.settleBuilds(50L).isEmpty(), "a build must not settle early");
                    helper.assertFalse(console.isInstalled("jsc:example"), "a building package is not installed yet");
                    helper.assertTrue(console.settleBuilds(100L).contains("jsc:example"),
                            "the build must settle at its completion tick");
                    helper.assertTrue(console.isInstalled("jsc:example"), "a settled build is installed");
                    // Persistence: a pending build survives an NBT round-trip.
                    console.startBuild("jsc:other", 900L);
                    final net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                    console.save(tag);
                    final dev.jstech.computronics.program.ComputerConsoleState loaded =
                            new dev.jstech.computronics.program.ComputerConsoleState();
                    loaded.load(tag);
                    helper.assertTrue(loaded.pendingBuilds().containsKey("jsc:other"),
                            "a pending build must persist across a reload");
                })
                .thenSucceed();
    }

    /**
     * A desktop environment is a package: a TTY Linux boots to the terminal until {@code apt install gnome}
     * puts GNOME on it, after which the computer reports GNOME as its desktop and boots the full desktop.
     */
    @GameTest(template = ARENA)
    public static void linux_desktopEnvironmentPackageBootsTheDesktop(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithOs(helper, pos,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "ubuntu"));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(mainframe.installedDesktopId() == null,
                            "a fresh Linux install has no desktop environment");
                    helper.assertTrue(dev.jstech.computronics.os.boot.BootController
                                    .targetForComputer(mainframe)
                                    == dev.jstech.computronics.os.boot.BootController.BootTarget.TERMINAL_ONLY,
                            "without a desktop environment the distribution boots to the TTY");
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final var shell = dev.jstech.computronics.program.cli.CliCommands.newShell(
                            cli.shellFamily(), 52);
                    shell.run("mirror install", cli);
                    helper.assertTrue(text(shell.run("apt install gnome", cli)).contains("done"),
                            "apt must install the GNOME package from the mirror");
                    final ResourceLocation gnome = ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "gnome");
                    helper.assertTrue(gnome.equals(mainframe.installedDesktopId()),
                            "the installed desktop package must become the computer's desktop; got "
                                    + mainframe.installedDesktopId());
                    // Installing the package does not put the running machine into a desktop: a system
                    // that is already up keeps the session it booted until it is restarted.
                    helper.assertTrue(dev.jstech.computronics.os.boot.BootController
                                    .targetForComputer(mainframe)
                                    == dev.jstech.computronics.os.boot.BootController.BootTarget.TERMINAL_ONLY,
                            "a running TTY session must not grow a desktop without a restart");

                    // The restart is what applies it: POST fixes the session from what is now on disk.
                    mainframe.setBootedDesktopId(mainframe.installedDesktopId());
                    helper.assertTrue(dev.jstech.computronics.os.boot.BootController
                                    .targetForComputer(mainframe)
                                    == dev.jstech.computronics.os.boot.BootController.BootTarget.FULL_DESKTOP,
                            "after a restart the distribution boots the installed desktop");

                    // And removing it takes effect the same way: on the next boot, not immediately.
                    shell.run("apt remove gnome", cli);
                    helper.assertTrue(dev.jstech.computronics.os.boot.BootController
                                    .targetForComputer(mainframe)
                                    == dev.jstech.computronics.os.boot.BootController.BootTarget.FULL_DESKTOP,
                            "the running desktop session survives its package being removed");
                    mainframe.setBootedDesktopId(mainframe.installedDesktopId());
                    helper.assertTrue(dev.jstech.computronics.os.boot.BootController
                                    .targetForComputer(mainframe)
                                    == dev.jstech.computronics.os.boot.BootController.BootTarget.TERMINAL_ONLY,
                            "after the restart the machine is back at the TTY");

                    helper.assertTrue(dev.jstech.computronics.os.OsRegistry.getDesktop(gnome) != null
                                    && dev.jstech.computronics.os.OsRegistry.getDesktop(gnome).panelStyle()
                                    == dev.jstech.computronics.os.PanelStyle.GNOME,
                            "the GNOME desktop environment must be registered with the GNOME chrome");
                })
                .thenSucceed();
    }

    /**
     * The manual Arch install end to end: a booted live medium owns the terminal, the real command sequence
     * (against the network mirror) lands Arch on the chosen disk, the live session ends, and the computer boots
     * the new system with its zsh prompt.
     */
    @GameTest(template = ARENA)
    public static void linux_archLiveInstallByHandBootsTheSystem(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithOs(helper, pos,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "ubuntu"));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    mainframe.installMirror();
                    mainframe.console().startLiveInstall(
                            dev.jstech.computronics.program.install.LiveInstallState.Distro.ARCH);
                    helper.assertTrue(dev.jstech.computronics.os.boot.BootController
                                    .targetForComputer(mainframe)
                                    == dev.jstech.computronics.os.boot.BootController.BootTarget.TERMINAL_ONLY,
                            "a booted live medium runs in the terminal");
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final var shell = dev.jstech.computronics.program.cli.CliCommands.shellFor(cli, 52);
                    helper.assertTrue("root@archiso ~ #".equals(cli.prompt()),
                            "the live shell must be a root prompt on the ISO; got " + cli.prompt());
                    helper.assertTrue(text(shell.run("ls", cli)).contains("command not found"),
                            "the live shell only knows the installer verbs");
                    helper.assertTrue(text(shell.run("pacstrap /mnt base linux", cli)).contains("not a mountpoint"),
                            "pacstrap before mount must fail with the real error");
                    shell.run("mkfs.ext4 /dev/sda", cli);
                    shell.run("mount /dev/sda /mnt", cli);
                    helper.assertTrue(text(shell.run("pacstrap /mnt base linux", cli)).contains("installation complete"),
                            "pacstrap must pull the base system from the mirror");
                    shell.run("genfstab -U /mnt >> /mnt/etc/fstab", cli);
                    shell.run("arch-chroot /mnt", cli);
                    helper.assertTrue("[root@archiso /]#".equals(cli.prompt()), "the chroot changes the prompt");
                    shell.run("grub-install /dev/sda", cli);
                    shell.run("passwd", cli);
                    shell.run("exit", cli);
                    helper.assertTrue(text(shell.run("reboot", cli)).contains("Installation complete"),
                            "reboot after a full sequence must complete the install");
                    final ResourceLocation arch = ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "arch");
                    helper.assertTrue(mainframe.console().liveInstall() == null, "the live session must end");
                    helper.assertTrue(arch.equals(mainframe.installedOsId()),
                            "the computer must boot the hand-installed Arch; got " + mainframe.installedOsId());
                    final ServerCliComputer after = cliFor(mainframe, helper.getLevel());
                    helper.assertTrue("player@arch ~ %".equals(after.prompt()),
                            "after the reboot Arch shows its zsh prompt; got " + after.prompt());
                })
                .thenSucceed();
    }

    /**
     * The Gentoo live install by hand, through the live shell: stage3 from the mirror, chroot, sync, the
     * kernel sources compile for real (CPU-scaled ticks, so genkernel refuses until they are done), then
     * genkernel, GRUB, passwd, reboot, and the installed Gentoo shows its bash prompt.
     */
    @GameTest(template = ARENA, timeoutTicks = 1000)
    public static void linux_gentooLiveInstallCompilesTheKernelBeforeBooting(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithOs(helper, pos,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "ubuntu"));
        // The test CPU runs at 2000 MHz, which the live installer turns into a 32 s (640 tick) kernel build.
        final int kernelTicks = 640;
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    mainframe.installMirror();
                    mainframe.console().startLiveInstall(
                            dev.jstech.computronics.program.install.LiveInstallState.Distro.GENTOO);
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final var shell = dev.jstech.computronics.program.cli.CliCommands.shellFor(cli, 52);
                    helper.assertTrue("livecd ~ #".equals(cli.prompt()),
                            "the Gentoo live CD is a root prompt; got " + cli.prompt());
                    helper.assertTrue(text(shell.run("tar xpf stage3-amd64.tar.xz -C /mnt", cli)).contains("Not a mountpoint"),
                            "unpacking the stage3 before mounting must fail with the real error");
                    shell.run("mkfs.ext4 /dev/sda", cli);
                    shell.run("mount /dev/sda /mnt", cli);
                    helper.assertTrue(text(shell.run("tar xpf stage3-amd64.tar.xz -C /mnt", cli)).contains("done"),
                            "the stage3 tarball must come from the mirror");
                    shell.run("chroot /mnt", cli);
                    helper.assertTrue("(chroot) livecd / #".equals(cli.prompt()), "the chroot changes the prompt");
                    helper.assertTrue(text(shell.run("emerge sys-kernel/gentoo-sources", cli)).contains("portage tree is empty"),
                            "emerging before a sync must fail with the real error");
                    shell.run("emerge --sync", cli);
                    helper.assertTrue(text(shell.run("emerge sys-kernel/gentoo-sources", cli)).contains("about " + (kernelTicks / 20) + "s"),
                            "the kernel sources announce their CPU-scaled compile time");
                    helper.assertTrue(text(shell.run("genkernel all", cli)).contains("still compiling"),
                            "genkernel must wait for the sources to finish compiling");
                })
                .thenExecuteAfter(kernelTicks + 10, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final var shell = dev.jstech.computronics.program.cli.CliCommands.shellFor(cli, 52);
                    helper.assertTrue(text(shell.run("genkernel all", cli)).contains("Kernel compiled successfully"),
                            "once the sources are compiled genkernel builds the kernel");
                    shell.run("grub-install /dev/sda", cli);
                    shell.run("passwd", cli);
                    shell.run("exit", cli);
                    helper.assertTrue(text(shell.run("reboot", cli)).contains("Installation complete"),
                            "reboot after the full sequence must complete the install");
                    final ResourceLocation gentoo = ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "gentoo");
                    helper.assertTrue(mainframe.console().liveInstall() == null, "the live session must end");
                    helper.assertTrue(gentoo.equals(mainframe.installedOsId()),
                            "the computer must boot the hand-installed Gentoo; got " + mainframe.installedOsId());
                    final ServerCliComputer after = cliFor(mainframe, helper.getLevel());
                    helper.assertTrue("player@gentoo:~$".equals(after.prompt()),
                            "after the reboot Gentoo shows its bash prompt; got " + after.prompt());
                })
                .thenSucceed();
    }

    /**
     * On a source-based distribution a package compiles in the background: a repeated emerge reports the
     * running build instead of restarting it, {@code emerge --status} lists it, and once it finishes the
     * shell announces the finished build ahead of the next command's output.
     */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void linux_emergeAnnouncesAFinishedBuild(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithOs(helper, pos,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "gentoo"));
        // Minesweeper's 16 MB footprint on the 2000 MHz test CPU is an 8 s (160 tick) build.
        final int buildTicks = 160;
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    mainframe.installMirror();
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final var shell = dev.jstech.computronics.program.cli.CliCommands.shellFor(cli, 52);
                    helper.assertTrue(text(shell.run("emerge mines", cli)).contains("compiling (about " + (buildTicks / 20) + "s)"),
                            "emerge starts a CPU-scaled source build");
                    helper.assertTrue(text(shell.run("emerge mines", cli)).contains("already compiling"),
                            "a second emerge of the same package reports the running build");
                    final String status = text(shell.run("emerge --status", cli));
                    helper.assertTrue(status.contains("minesweeper") && status.contains("compiling"),
                            "emerge --status lists the running build; got " + status);
                    helper.assertFalse(mainframe.console().isInstalled("jsc:minesweeper"),
                            "a building package is not installed yet");
                })
                .thenExecuteAfter(buildTicks + 10, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final var shell = dev.jstech.computronics.program.cli.CliCommands.shellFor(cli, 52);
                    final var response = shell.run("pwd", cli);
                    helper.assertTrue(!response.lines().isEmpty()
                                    && response.lines().get(0).text().contains("mines: build finished, package installed"),
                            "the finished build is announced ahead of the next command; got " + text(response));
                    helper.assertTrue(text(shell.run("pwd", cli)).contains("/home/player"),
                            "the announcement is made once, then the shell is back to normal output");
                    helper.assertTrue(mainframe.console().isInstalled("jsc:minesweeper"),
                            "a finished build is installed");
                    helper.assertTrue(text(shell.run("emerge --status", cli)).contains("no builds in progress"),
                            "nothing is left compiling");
                })
                .thenSucceed();
    }

    /** Formatting a disk erases its system, files and storage, and clears a boot-order pointer at it. */
    @GameTest(template = ARENA)
    public static void firmware_formatDiskErasesTheSystem(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithOs(helper, pos,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "ubuntu"));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(mainframe.installedOsId() != null, "the fixture installs an OS");
                    mainframe.setBootDiskSlot(0);
                    helper.assertTrue(mainframe.formatDisk(0), "formatting the installed disk succeeds");
                    helper.assertTrue(mainframe.installedOsId() == null, "the formatted disk lost its system");
                    helper.assertTrue(mainframe.bootDiskSlot() == -1,
                            "a boot-order pointer at the formatted disk is cleared");
                    helper.assertFalse(mainframe.formatDisk(3), "an empty slot has nothing to format");
                })
                .thenSucceed();
    }

    /**
     * The DOS-family shell manages the machine: {@code mirror install} turns the Mainframe service on,
     * {@code uninstall mirror} turns it off again, and {@code format} erases a data drive only after the
     * explicit confirmation flag — never the running system drive.
     */
    @GameTest(template = ARENA)
    public static void shell_formatAndUninstallManageTheComputer(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithOs(helper, pos,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "frames_xp"));
        // A second disk becomes drive D:.
        mainframe.getInventory().setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START + 1,
                new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final var shell = dev.jstech.computronics.program.cli.CliCommands.shellFor(cli, 52);
                    shell.run("mirror install", cli);
                    helper.assertTrue(mainframe.isMirrorInstalled(), "the mirror verb installs the service");
                    helper.assertTrue(text(shell.run("uninstall mirror", cli)).contains("Removing mirror"),
                            "uninstall removes an installed service by name");
                    helper.assertFalse(mainframe.isMirrorInstalled(), "the service flag turns off with it");
                    helper.assertTrue(text(shell.run("format D:", cli)).contains("WILL BE LOST"),
                            "format without /y only warns");
                    helper.assertTrue(text(shell.run("format D: /y", cli)).contains("done"),
                            "format /y erases the data drive");
                    helper.assertTrue(text(shell.run("format C: /y", cli)).contains("cannot format"),
                            "the running system drive is never formatted from the shell");
                })
                .thenSucceed();
    }

    /** Removing an installed desktop-environment package drops the distribution back to the TTY. */
    @GameTest(template = ARENA)
    public static void linux_packageRemoveDropsTheDesktopEnvironment(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithOs(helper, pos,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "ubuntu"));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    mainframe.installMirror();
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final var shell = dev.jstech.computronics.program.cli.CliCommands.shellFor(cli, 52);
                    shell.run("apt install cinnamon", cli);
                    helper.assertTrue(mainframe.installedDesktopId() != null,
                            "installing a desktop environment registers it");
                    helper.assertTrue(text(shell.run("apt remove cinnamon", cli)).contains("Removing cinnamon"),
                            "the package manager removes an installed package");
                    helper.assertTrue(mainframe.installedDesktopId() == null,
                            "without the package the computer boots back to the TTY");
                    helper.assertTrue(text(shell.run("apt remove cinnamon", cli)).contains("not installed"),
                            "removing it again reports there is nothing to remove");
                })
                .thenSucceed();
    }

    /**
     * Each distribution removes packages with its own manager's flags, and every manager advertises
     * removal in its usage line — a verb that works but is never listed cannot be discovered.
     */
    @GameTest(template = ARENA)
    public static void packageManagers_removeWithTheirOwnFlagsAndAdvertiseIt(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithOs(helper, pos,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "arch"));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    mainframe.installMirror();
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final var shell = dev.jstech.computronics.program.cli.CliCommands.shellFor(cli, 52);
                    shell.run("pacman -S cinnamon", cli);
                    helper.assertTrue(mainframe.installedDesktopId() != null,
                            "pacman -S installs a package");
                    helper.assertTrue(text(shell.run("pacman", cli)).contains("-R <package>"),
                            "the usage line names the removal flag");
                    helper.assertTrue(text(shell.run("pacman -R cinnamon", cli)).contains("Removing cinnamon"),
                            "pacman -R removes an installed package");
                    helper.assertTrue(mainframe.installedDesktopId() == null,
                            "the desktop environment is gone with its package");
                })
                .thenSucceed();
    }

    /**
     * Software cannot predate its hardware generation. KDE and GNOME are old enough for a Legacy
     * machine; Cinnamon is a much later desktop and needs a Standard one; a Vintage machine gets no
     * graphical desktop at all and stays at the TTY.
     */
    @GameTest(template = ARENA)
    public static void desktopEnvironments_installOnlyOnHardwareOfTheirOwnEraOrNewer(final GameTestHelper helper) {
        final BlockPos legacyPos = new BlockPos(2, 2, 2);
        final BlockPos vintagePos = new BlockPos(6, 2, 2);
        final ResourceLocation ubuntu = ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "ubuntu");
        // A machine of an era is its era chassis carrying a board of that same era: the chassis accepts
        // no other, so the two can never disagree.
        final MainframeBlockEntity legacy = placeMainframeWithEra(helper, legacyPos, ubuntu,
                ComputingModule.LEGACY_MAINFRAME.get(),
                HardwareItems.MOTHERBOARD_MTX_LEGACY.get(), HardwareItems.CPU_VELOCION_DUAL_285.get(),
                HardwareItems.RAM_DDR2_2048.get(), HardwareItems.PSU_500B.get());
        final MainframeBlockEntity vintage = placeMainframeWithEra(helper, vintagePos, ubuntu,
                ComputingModule.VINTAGE_MAINFRAME.get(),
                HardwareItems.MOTHERBOARD_MTX_VINTAGE.get(), HardwareItems.CPU_VELOCION_K6_III.get(),
                HardwareItems.RAM_EDO_16.get(), HardwareItems.PSU_300B.get());
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    legacy.installMirror();
                    vintage.installMirror();
                    final ServerCliComputer legacyCli = cliFor(legacy, helper.getLevel());
                    final var legacyShell =
                            dev.jstech.computronics.program.cli.CliCommands.shellFor(legacyCli, 52);

                    helper.assertTrue(text(legacyShell.run("apt install kde-plasma", legacyCli))
                                    .contains("Setting up"),
                            "KDE is old enough for a Legacy machine");
                    helper.assertTrue(text(legacyShell.run("apt install cinnamon", legacyCli))
                                    .contains("Standard hardware"),
                            "Cinnamon must refuse a Legacy machine and say which era it needs");

                    final ServerCliComputer vintageCli = cliFor(vintage, helper.getLevel());
                    final var vintageShell =
                            dev.jstech.computronics.program.cli.CliCommands.shellFor(vintageCli, 52);
                    helper.assertTrue(text(vintageShell.run("apt install gnome", vintageCli))
                                    .contains("Legacy hardware"),
                            "a Vintage machine gets no desktop environment at all");
                    helper.assertTrue(vintage.installedDesktopId() == null,
                            "the refused install must leave the Vintage machine at the TTY");
                })
                .thenSucceed();
    }

    /**
     * Installed software belongs to the disk, not to the computer. Swapping in a fresh disk must give a
     * clean machine, and putting the original back must bring its programs with it — the bug being that
     * a newly installed system inherited the previous one's programs and desktop.
     */
    @GameTest(template = ARENA)
    public static void installedSoftware_ridesOnTheDiskAndNotOnTheComputer(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithOs(helper, pos,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "ubuntu"));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    mainframe.installMirror();
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final var shell = dev.jstech.computronics.program.cli.CliCommands
                            .shellFor(cli, 52);
                    shell.run("apt install cinnamon", cli);
                    helper.assertTrue(mainframe.installedDesktopId() != null,
                            "the desktop environment installs on the original disk");

                    // Pull the system disk out and drop in a blank one, as a player swapping drives does.
                    final ItemStackHandler inv = mainframe.getInventory();
                    final ItemStack original = inv.getStackInSlot(MainframeBlockEntity.DISK_SLOTS_START);
                    inv.setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START,
                            new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
                    helper.assertTrue(mainframe.console().installed().isEmpty(),
                            "a fresh disk must give a machine with no programs installed");
                    helper.assertTrue(mainframe.installedDesktopId() == null,
                            "a fresh disk must not inherit the previous system's desktop");

                    // Put the original back: its software comes with it.
                    inv.setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START, original);
                    helper.assertTrue(mainframe.installedDesktopId() != null,
                            "the original disk brings its desktop environment back");
                })
                .thenSucceed();
    }

    /**
     * The windows open on a desktop are the machine's state: they persist with it, and a restart or a
     * shutdown closes them, as on any real machine. This is what lets whoever opens the monitor next —
     * or the same player after the game was closed — find the desktop as it was left.
     */
    @GameTest(template = ARENA)
    public static void openWindows_persistWithTheMachineAndCloseOnRestartOrShutdown(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithOs(helper, pos,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "ubuntu"));
        final java.util.List<dev.jstech.computronics.os.OpenWindow> layout = java.util.List.of(
                new dev.jstech.computronics.os.OpenWindow("Files", 40, 30, 200, 140, false, false),
                new dev.jstech.computronics.os.OpenWindow("Editor", 60, 50, 180, 120, true, false));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    mainframe.setNeedsPost(false);
                    mainframe.setOpenWindows(layout);
                    helper.assertTrue(mainframe.openWindows().equals(layout),
                            "the machine keeps the windows it was handed");

                    // Round-trip through NBT: the layout has to survive a save and reload of the world.
                    final var registries = helper.getLevel().registryAccess();
                    final net.minecraft.nbt.CompoundTag saved = mainframe.saveWithoutMetadata(registries);
                    mainframe.setOpenWindows(java.util.List.of());
                    mainframe.loadWithComponents(saved, registries);
                    helper.assertTrue(mainframe.openWindows().equals(layout),
                            "the windows must come back from the saved block entity");

                    // A restart closes everything.
                    mainframe.setNeedsPost(true);
                    helper.assertTrue(mainframe.openWindows().isEmpty(),
                            "a restart leaves no windows open");

                    // So does switching the machine off.
                    mainframe.setNeedsPost(false);
                    mainframe.setOpenWindows(layout);
                    mainframe.setPowered(false);
                    helper.assertTrue(mainframe.openWindows().isEmpty(),
                            "a shutdown leaves no windows open");
                })
                .thenSucceed();
    }

    /** Pulling the disk that carries the system leaves nothing to boot: the machine falls to the firmware. */
    @GameTest(template = ARENA)
    public static void firmware_pullingTheSystemDiskDropsTheSession(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithOs(helper, pos,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "ubuntu"));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(mainframe.validateOsSession(), "an installed system is a valid session");
                    mainframe.getInventory().setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START, ItemStack.EMPTY);
                    helper.assertTrue(mainframe.installedOsId() == null, "the system left with its disk");
                    helper.assertFalse(mainframe.validateOsSession(), "no disk means no bootable session");
                    helper.assertTrue(dev.jstech.computronics.os.boot.BootController
                                    .targetForComputer(mainframe)
                                    == dev.jstech.computronics.os.boot.BootController.BootTarget.FIRMWARE,
                            "with the system disk gone the machine boots to the firmware");
                })
                .thenSucceed();
    }

    /** Ejecting the live installer's medium kills the live session (the machine falls back to its disk OS). */
    @GameTest(template = ARENA, timeoutTicks = 60)
    public static void linux_ejectingTheLiveMediumEndsTheSession(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final BlockPos readerPos = pos.east();
        final MainframeBlockEntity mainframe = placeMainframeWithOs(helper, pos,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "ubuntu"));
        // A GPU gives the Mainframe peripheral ports so the adjacent reader can link to it.
        mainframe.getInventory().setStackInSlot(MainframeBlockEntity.GPU_SLOTS_START,
                new ItemStack(ComputingModule.GPU_HD_7970.get()));
        helper.setBlock(readerPos, ComputingModule.CD_DRIVE.get());
        if (!(helper.getBlockEntity(readerPos)
                instanceof dev.jstech.computronics.os.media.MediaReaderBlockEntity reader)) {
            helper.fail("no media reader at " + readerPos);
            return;
        }
        final ItemStack media = new ItemStack(ComputingModule.CD_ROM.get());
        dev.jstech.computronics.os.media.MediaItem.setKind(
                media, dev.jstech.computronics.os.media.MediaKind.OS_INSTALL);
        dev.jstech.computronics.os.media.MediaItem.setPayload(
                media, ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "arch"));
        reader.mediaSlot().setStackInSlot(0, media);
        helper.startSequence()
                .thenExecuteAfter(SETTLE + 4, () -> {
                    helper.assertTrue(mainframe.linkedEndpoints().contains(helper.absolutePos(readerPos).asLong()),
                            "the reader must link to the Mainframe");
                    mainframe.console().startLiveInstall(
                            dev.jstech.computronics.program.install.LiveInstallState.Distro.ARCH);
                    helper.assertTrue(mainframe.validateOsSession(),
                            "a live session with its medium in the drive is valid");
                    helper.assertTrue("root@archiso ~ #".equals(cliFor(mainframe, helper.getLevel()).prompt()),
                            "the live shell runs while the medium is in");
                    reader.mediaSlot().setStackInSlot(0, ItemStack.EMPTY);
                    helper.assertTrue(mainframe.validateOsSession(),
                            "ejecting the medium falls back to the installed disk system");
                    helper.assertTrue(mainframe.console().liveInstall() == null,
                            "the live session died with its medium");
                    helper.assertTrue("player@ubuntu:~$".equals(cliFor(mainframe, helper.getLevel()).prompt()),
                            "the console is the disk system's shell again");
                })
                .thenSucceed();
    }

    /** Formatting the system disk wipes the software layer: history, installed programs, and services. */
    @GameTest(template = ARENA)
    public static void console_formatWipesTheSoftwareState(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithOs(helper, pos,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "ubuntu"));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    mainframe.installMirror();
                    mainframe.console().pushHistory("ls -l");
                    mainframe.console().install("jsc:minesweeper");
                    helper.assertTrue(mainframe.formatDisk(0), "the system disk formats");
                    helper.assertTrue(mainframe.console().history().isEmpty(),
                            "the old system's command history died with its disk");
                    helper.assertFalse(mainframe.console().isInstalled("jsc:minesweeper"),
                            "installed programs died with the disk");
                    helper.assertFalse(mainframe.isMirrorInstalled(),
                            "a wiped Mainframe serves no services");
                })
                .thenSucceed();
    }

    /** screenfetch prints the machine's identity: distro, kernel, shell, DE, hardware and disk usage. */
    @GameTest(template = ARENA)
    public static void linux_screenfetchShowsTheSystem(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithOs(helper, pos,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "ubuntu"));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final var shell = dev.jstech.computronics.program.cli.CliCommands.shellFor(cli, 52);
                    // A package the Mirror serves, not a built-in: a fresh system does not have it.
                    helper.assertTrue(text(shell.run("screenfetch", cli)).contains("command not found"),
                            "screenfetch is a package, absent on a fresh install");
                    mainframe.installMirror();
                    shell.run("apt install screenfetch", cli);
                    final String out = text(shell.run("screenfetch", cli));
                    helper.assertTrue(out.contains("player@ubuntu"), "the header is user@host; got " + out);
                    helper.assertTrue(out.contains("OS: Ubuntu"), "the OS line names the distribution");
                    helper.assertTrue(out.contains("Kernel: Linux 6.8-jsc"), "the kernel line is the Linux one");
                    helper.assertTrue(out.contains("Shell: bash"), "the shell line is the distro's shell");
                    helper.assertTrue(out.contains("DE: none (tty1)"), "without a DE the machine is a TTY");
                    helper.assertTrue(out.contains("CPU: 2000 MHz"), "the CPU line shows the clock");
                    shell.run("apt install cinnamon", cli);
                    helper.assertTrue(text(shell.run("neofetch", cli)).contains("DE: Cinnamon"),
                            "with a desktop environment installed the DE line names it (alias included)");
                })
                .thenSucceed();
    }

    /** A source build settles by itself through the computer's tick — no command needed to finish it. */
    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void linux_buildSettlesByTickingWithoutACommand(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        final MainframeBlockEntity mainframe = placeMainframeWithOs(helper, pos,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "gentoo"));
        // Minesweeper's 16 MB footprint on the 2000 MHz test CPU is an 8 s (160 tick) build.
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    mainframe.installMirror();
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final var shell = dev.jstech.computronics.program.cli.CliCommands.shellFor(cli, 52);
                    shell.run("emerge mines", cli);
                    helper.assertTrue(mainframe.console().buildTotal("jsc:minesweeper") > 0,
                            "a running build knows its full duration (for the progress lines)");
                })
                .thenExecuteAfter(200, () -> {
                    // No command ran since: the block's own ticker settled the finished build.
                    helper.assertTrue(mainframe.console().isInstalled("jsc:minesweeper"),
                            "the tick settles a finished build without a command");
                    final ServerCliComputer cli = cliFor(mainframe, helper.getLevel());
                    final var shell = dev.jstech.computronics.program.cli.CliCommands.shellFor(cli, 52);
                    helper.assertTrue(text(shell.run("pwd", cli)).contains("build finished"),
                            "with no console open the finished notice waits for the next command");
                })
                .thenSucceed();
    }

    /** The command history is per computer: what was typed on one machine never surfaces on another. */
    @GameTest(template = ARENA)
    public static void console_historyIsPerComputer(final GameTestHelper helper) {
        final BlockPos posA = new BlockPos(1, 2, 2);
        final BlockPos posB = new BlockPos(4, 2, 2);
        final MainframeBlockEntity a = placeMainframeWithOs(helper, posA,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "ubuntu"));
        final MainframeBlockEntity b = placeMainframeWithOs(helper, posB,
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "debian"));
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    a.console().pushHistory("echo typed-on-a");
                    helper.assertTrue(b.console().history().isEmpty(),
                            "computer B must not see computer A's history");
                    helper.assertTrue(a.console().history().contains("echo typed-on-a"),
                            "computer A keeps its own history");
                })
                .thenSucceed();
    }

    /** Places a powered Mainframe with a full build and a disk, then installs the given OS on it. */
    private static MainframeBlockEntity placeMainframeWithOs(final GameTestHelper helper, final BlockPos pos,
                                                            final ResourceLocation osId) {
        return placeMainframeWithOs(helper, pos, osId, ComputingModule.MAINFRAME.get());
    }

    /**
     * A Mainframe of a given era: its era chassis plus a board, CPU, RAM and supply of that same era.
     * The chassis accepts only a board of its own era, so passing parts from another one leaves the
     * machine with no valid build.
     */
    private static MainframeBlockEntity placeMainframeWithEra(final GameTestHelper helper, final BlockPos pos,
                                                              final ResourceLocation osId,
                                                              final net.minecraft.world.level.block.Block chassis,
                                                              final net.minecraft.world.item.Item board,
                                                              final net.minecraft.world.item.Item cpu,
                                                              final net.minecraft.world.item.Item ram,
                                                              final net.minecraft.world.item.Item psu) {
        helper.setBlock(pos, chassis);
        if (!(helper.getBlockEntity(pos) instanceof MainframeBlockEntity mainframe)) {
            throw new IllegalStateException("no MainframeBlockEntity at " + pos);
        }
        final ItemStackHandler inv = mainframe.getInventory();
        inv.setStackInSlot(MainframeBlockEntity.MOTHERBOARD_SLOT, new ItemStack(board));
        inv.setStackInSlot(MainframeBlockEntity.CPU_SLOTS_START, new ItemStack(cpu));
        inv.setStackInSlot(MainframeBlockEntity.RAM_SLOTS_START, new ItemStack(ram));
        inv.setStackInSlot(MainframeBlockEntity.PSU_SLOT, new ItemStack(psu));
        inv.setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        mainframe.togglePower();
        if (!mainframe.installOs(osId)) {
            throw new IllegalStateException("failed to install " + osId + " on the era Mainframe at " + pos);
        }
        return mainframe;
    }

    /** The same fixture on a chosen chassis, so a test can put the machine in a specific hardware era. */
    private static MainframeBlockEntity placeMainframeWithOs(final GameTestHelper helper, final BlockPos pos,
                                                            final ResourceLocation osId,
                                                            final net.minecraft.world.level.block.Block chassis) {
        helper.setBlock(pos, chassis);
        if (!(helper.getBlockEntity(pos) instanceof MainframeBlockEntity mainframe)) {
            throw new IllegalStateException("no MainframeBlockEntity at " + pos);
        }
        final ItemStackHandler inv = mainframe.getInventory();
        inv.setStackInSlot(MainframeBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(ComputingModule.MOTHERBOARD_MTX_P.get()));
        inv.setStackInSlot(MainframeBlockEntity.CPU_SLOTS_START,
                new ItemStack(ComputingModule.CPU_SERVO_2620.get()));
        inv.setStackInSlot(MainframeBlockEntity.RAM_SLOTS_START,
                new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        inv.setStackInSlot(MainframeBlockEntity.PSU_SLOT,
                new ItemStack(ComputingModule.PSU_650G.get()));
        inv.setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        mainframe.togglePower();
        if (!mainframe.installOs(osId)) {
            throw new IllegalStateException("failed to install " + osId + " on the test Mainframe");
        }
        return mainframe;
    }

    /** All of a shell response's lines joined with newlines. */
    private static String text(final dev.jstech.computronics.program.cli.CliShell.Response response) {
        final StringBuilder sb = new StringBuilder();
        for (final var line : response.lines()) {
            sb.append(line.text()).append('\n');
        }
        return sb.toString();
    }
}
