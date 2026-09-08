/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computers.ComputingModule;
import dev.jstech.computers.JsComputers;
import dev.jstech.computers.blockentity.MainframeBlockEntity;
import dev.jstech.computers.hardware.DiskSize;
import dev.jstech.computers.hardware.StorageTier;
import dev.jstech.computers.os.FilesystemKind;
import dev.jstech.computers.os.fs.DiskFilesystem;
import dev.jstech.computers.os.fs.FileType;
import dev.jstech.tests.JsTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;
import java.util.Optional;

/**
 * Integration tests for the NMS File menu: saving and opening {@code .iql} scripts on the
 * Mainframe's system disk via {@link DiskFilesystem}.
 *
 * <p>Tests drive the server-side handler logic directly (no client screen), writing and reading
 * through the same API surface the payload handlers use.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class NmsFileGameTests {

    private NmsFileGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;
    private static final ResourceLocation SO_REDE =
            ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, "mc_net");

    // Setup helper: place a Mainframe with board+CPU+RAM+PSU+disk, install the OS, return the BE.

    private static MainframeBlockEntity placeMainframe(final GameTestHelper helper, final BlockPos pos) {
        helper.setBlock(pos, ComputingModule.MAINFRAME.get());
        if (!(helper.getBlockEntity(pos) instanceof MainframeBlockEntity mf)) {
            helper.fail("no MainframeBlockEntity at " + pos);
            return null;
        }
        final ItemStackHandler hw = mf.getInventory();
        hw.setStackInSlot(MainframeBlockEntity.MOTHERBOARD_SLOT,
                new ItemStack(ComputingModule.MOTHERBOARD_MTX_P.get()));
        hw.setStackInSlot(MainframeBlockEntity.CPU_SLOTS_START,
                new ItemStack(ComputingModule.CPU_ASCENT_965.get()));
        hw.setStackInSlot(MainframeBlockEntity.RAM_SLOTS_START,
                new ItemStack(ComputingModule.RAM_DDR3_8192.get()));
        hw.setStackInSlot(MainframeBlockEntity.PSU_SLOT,
                new ItemStack(ComputingModule.PSU_650G.get()));
        hw.setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        // installOs writes SYSTEM_OS onto the disk component and returns true on success.
        final boolean installed = mf.installOs(SO_REDE);
        if (!installed) {
            helper.fail("installOs returned false at " + pos);
            return null;
        }
        return mf;
    }

    // Tests

    /**
     * Writes an {@code .iql} script to the Mainframe's system disk, then asserts the file appears
     * in {@link DiskFilesystem#list} and its content round-trips correctly via
     * {@link DiskFilesystem#read}. This is the core of the File → Save handler logic.
     */
    @GameTest(template = ARENA)
    public static void nmsFile_saveWritesToDisk(final GameTestHelper helper) {
        final BlockPos mfPos = new BlockPos(2, 2, 2);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MainframeBlockEntity mf = placeMainframe(helper, mfPos);
                    if (mf == null) {
                        return;
                    }

                    final ItemStack sysDisk = mf.systemDisk();
                    helper.assertFalse(sysDisk.isEmpty(), "system disk must be present after installOs");

                    final FilesystemKind kind = FilesystemKind.FLAT; // SO_REDE uses a DOS kernel
                    final String content = "SELECT * FROM items WHERE qty > 10";
                    final String fileName = "my_query.iql";

                    // Simulate the save handler: write the file to the disk.
                    final DiskFilesystem.WriteResult result =
                            DiskFilesystem.write(sysDisk, fileName, FileType.IQL, content,
                                    Long.MAX_VALUE, kind);
                    helper.assertTrue(result == DiskFilesystem.WriteResult.OK,
                            "write must return OK; got: " + result);
                    mf.setChanged();

                    // The file must appear in list().
                    final List<DiskFilesystem.FileEntry> entries =
                            DiskFilesystem.list(sysDisk, "", kind);
                    final long iqlCount = entries.stream()
                            .filter(e -> e.type() == FileType.IQL)
                            .count();
                    helper.assertTrue(iqlCount == 1L,
                            "exactly one .iql entry must appear in list(); got: " + iqlCount);
                    helper.assertTrue(DiskFilesystem.exists(sysDisk, fileName),
                            "exists() must confirm the written file");

                    // The content round-trips correctly.
                    final Optional<String> read = DiskFilesystem.read(sysDisk, fileName);
                    helper.assertTrue(read.isPresent(), "read() must return the file content");
                    helper.assertTrue(content.equals(read.get()),
                            "round-trip content mismatch: got [" + read.get() + "]");
                })
                .thenSucceed();
    }

    /**
     * Verifies that only {@link FileType#IQL} files appear in the list filtered by that type, even
     * when the disk holds files of multiple types.
     */
    @GameTest(template = ARENA)
    public static void nmsFile_listReturnsOnlyIqlFiles(final GameTestHelper helper) {
        final BlockPos mfPos = new BlockPos(2, 2, 2);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MainframeBlockEntity mf = placeMainframe(helper, mfPos);
                    if (mf == null) {
                        return;
                    }

                    final ItemStack sysDisk = mf.systemDisk();
                    final FilesystemKind kind = FilesystemKind.FLAT;

                    DiskFilesystem.write(sysDisk, "script.iql", FileType.IQL,
                            "SELECT 1", Long.MAX_VALUE, kind);
                    DiskFilesystem.write(sysDisk, "notes.txt", FileType.TXT,
                            "some text", Long.MAX_VALUE, kind);
                    mf.setChanged();

                    final List<DiskFilesystem.FileEntry> all = DiskFilesystem.list(sysDisk, "", kind);
                    // There should be two real files (excluding any .dat projections).
                    final long realCount = all.stream().filter(e -> !e.readOnly()).count();
                    helper.assertTrue(realCount == 2L,
                            "expected 2 real files; got: " + realCount);

                    // Filter to .iql only, mirroring the iqlFileList() helper in the payload handler.
                    final long iqlCount = all.stream().filter(e -> e.type() == FileType.IQL).count();
                    helper.assertTrue(iqlCount == 1L,
                            "filter must yield exactly 1 .iql entry; got: " + iqlCount);
                    helper.assertTrue(all.stream().filter(e -> e.type() == FileType.IQL)
                                    .allMatch(e -> e.path().equals("script.iql")),
                            "the single IQL entry must be script.iql");
                })
                .thenSucceed();
    }

    /**
     * Verifies that reading back a saved file delivers the correct content, mirroring the
     * File → Open handler flow.
     */
    @GameTest(template = ARENA)
    public static void nmsFile_openReturnsContent(final GameTestHelper helper) {
        final BlockPos mfPos = new BlockPos(2, 2, 2);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MainframeBlockEntity mf = placeMainframe(helper, mfPos);
                    if (mf == null) {
                        return;
                    }

                    final ItemStack sysDisk = mf.systemDisk();
                    final FilesystemKind kind = FilesystemKind.FLAT;
                    final String content =
                            "SELECT item, qty FROM items WHERE server = 'srv-01' ORDER BY qty DESC";

                    DiskFilesystem.write(sysDisk, "report.iql", FileType.IQL,
                            content, Long.MAX_VALUE, kind);
                    mf.setChanged();

                    // Open handler: read the file content.
                    final Optional<String> read = DiskFilesystem.read(sysDisk, "report.iql");
                    helper.assertTrue(read.isPresent(), "read() must be present for an existing file");
                    helper.assertTrue(content.equals(read.get()),
                            "opened content must equal saved content; got [" + read.get() + "]");

                    // Opening a non-existent file must return empty (the handler sends ok=false).
                    final Optional<String> missing = DiskFilesystem.read(sysDisk, "nonexistent.iql");
                    helper.assertFalse(missing.isPresent(),
                            "read() must return empty for a non-existent file");
                })
                .thenSucceed();
    }

    /**
     * Verifies that the disk is the source of truth: the Mainframe's old {@code savedScript} NBT
     * field is not involved in saving or loading; the file content persists independently.
     */
    @GameTest(template = ARENA)
    public static void nmsFile_diskIsSourceOfTruth(final GameTestHelper helper) {
        final BlockPos mfPos = new BlockPos(2, 2, 2);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final MainframeBlockEntity mf = placeMainframe(helper, mfPos);
                    if (mf == null) {
                        return;
                    }

                    final ItemStack sysDisk = mf.systemDisk();
                    final FilesystemKind kind = FilesystemKind.FLAT;
                    final String fileContent = "INSERT INTO items VALUES (minecraft:diamond, 64)";

                    // Write the file.
                    DiskFilesystem.write(sysDisk, "insert.iql", FileType.IQL,
                            fileContent, Long.MAX_VALUE, kind);
                    mf.setChanged();

                    /*
                     * The savedScript field may hold a different value (legacy). The file content
                     * must match what was written to disk, independent of savedScript.
                     */
                    mf.setSavedScript("SOME OLD SCRIPT");
                    final Optional<String> read = DiskFilesystem.read(sysDisk, "insert.iql");
                    helper.assertTrue(read.isPresent(), "file must exist after write");
                    helper.assertTrue(fileContent.equals(read.get()),
                            "disk content must equal what was written, not savedScript; got: " + read.get());

                    // The file content must be listed.
                    helper.assertTrue(DiskFilesystem.exists(sysDisk, "insert.iql"),
                            "exists() must return true for the written file");
                })
                .thenSucceed();
    }
}
