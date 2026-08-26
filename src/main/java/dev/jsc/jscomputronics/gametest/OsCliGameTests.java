/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.gametest;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.common.hardware.DiskSize;
import dev.jsc.jscomputronics.common.hardware.StorageTier;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.blockentity.MainframeBlockEntity;
import dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem;
import dev.jsc.jscomputronics.module.computing.os.fs.FileType;
import dev.jsc.jscomputronics.module.computing.os.FilesystemKind;
import dev.jsc.jscomputronics.module.computing.program.ServerCliComputer;
import dev.jsc.jscomputronics.module.computing.program.cli.CliComputer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

/**
 * In-world integration tests for the filesystem CLI commands (dir, type, del, run).
 *
 * <p>Each test places a Mainframe with MC-DOS installed (FLAT filesystem), writes files
 * directly via {@link DiskFilesystem}, and then drives the command handlers through the same
 * methods the dispatcher invokes. No client screen is required; the {@link ServerCliComputer}
 * is constructed directly from the block entity.
 */
@GameTestHolder(JsComputronics.MODID)
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
                    final boolean found = entries.stream().anyMatch(e -> e.path().equals("test.iql"));
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
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Places a Mainframe at {@code pos} with a valid hardware build, installs MC-DOS onto the disk
     * (FLAT filesystem), and returns the block entity. The computer is left powered on.
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
        // Install MC-DOS so the filesystem kind resolves to FLAT.
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
}
