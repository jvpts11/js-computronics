/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jstech.computronics.ComputingModule;
import dev.jstech.computronics.JsComputronics;
import dev.jstech.computronics.blockentity.MainframeBlockEntity;
import dev.jstech.computronics.hardware.DiskSize;
import dev.jstech.computronics.hardware.StorageTier;
import dev.jstech.computronics.os.FilesystemKind;
import dev.jstech.computronics.os.OsRegistry;
import dev.jstech.computronics.os.VolumeLabel;
import dev.jstech.computronics.os.boot.BootController;
import dev.jstech.computronics.os.fs.DiskFilesystem;
import dev.jstech.computronics.os.fs.FileType;
import dev.jstech.computronics.os.fs.FilesystemContents;
import dev.jstech.computronics.os.fs.StoredFile;
import dev.jstech.computronics.storage.DriveVolumes;
import dev.jstech.computronics.storage.StorageKey;
import dev.jstech.tests.JsTests;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-world integration tests for the disk filesystem data model:
 * the FILESYSTEM and SYSTEM_OS data components on a DiskItem stack.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class OsFilesystemGameTests {

    private OsFilesystemGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    @GameTest(template = ARENA)
    public static void fs_componentsRoundTripOnDisk(final GameTestHelper helper) {
        // Build a DiskItem stack (NVMe 1 TB, since any registered disk works).
        final ItemStack stack = new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1));

        // Stamp it with a FILESYSTEM containing one IQL file.
        final StoredFile file = new StoredFile("a.iql", FileType.IQL, "SELECT *");
        final FilesystemContents fs = new FilesystemContents(Map.of("a.iql", file));
        stack.set(ComputingModule.FILESYSTEM.get(), fs);

        // Stamp it with a SYSTEM_OS identifying mc_net.
        final ResourceLocation osId =
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "mc_net");
        stack.set(ComputingModule.SYSTEM_OS.get(), osId);

        /*
         * copy() exercises the DataComponent codec path (the components are serialised and
         * deserialised into a fresh stack, exactly as happens on save/load).
         */
        final ItemStack copy = stack.copy();

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final FilesystemContents recovered =
                            copy.get(ComputingModule.FILESYSTEM.get());
                    helper.assertTrue(recovered != null,
                            "FILESYSTEM component must survive stack.copy()");

                    final StoredFile recovered_file = recovered.files().get("a.iql");
                    helper.assertTrue(recovered_file != null,
                            "The 'a.iql' entry must be present after copy");
                    helper.assertTrue("SELECT *".equals(recovered_file.content()),
                            "File content must survive copy; got: " + recovered_file.content());
                    helper.assertTrue(FileType.IQL == recovered_file.type(),
                            "File type must survive copy; got: " + recovered_file.type());

                    final ResourceLocation recoveredOs =
                            copy.get(ComputingModule.SYSTEM_OS.get());
                    helper.assertTrue(recoveredOs != null,
                            "SYSTEM_OS component must survive stack.copy()");
                    helper.assertTrue(osId.equals(recoveredOs),
                            "SYSTEM_OS must equal jsc:mc_net after copy; got: " + recoveredOs);
                })
                .thenSucceed();
    }

    @GameTest(template = "empty")
    public static void fs_volumeLabelRoundTripsAndClears(final GameTestHelper helper) {
        final ItemStack stack = new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1));

        // With no label set, a volume reads as its fallback name.
        helper.assertTrue("Local Disk".equals(VolumeLabel.of(stack, "Local Disk")),
                "An unlabeled volume must read as its fallback name");

        // A set label survives the DataComponent codec path (copy).
        VolumeLabel.set(stack, "Games");
        final ItemStack copy = stack.copy();

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue("Games".equals(VolumeLabel.of(copy, "Local Disk")),
                            "A set volume label must survive copy; got: " + VolumeLabel.of(copy, "Local Disk"));

                    // Clearing the label (blank) restores the fallback name.
                    VolumeLabel.set(copy, "   ");
                    helper.assertTrue("Local Disk".equals(VolumeLabel.of(copy, "Local Disk")),
                            "Clearing the label must restore the fallback name");
                })
                .thenSucceed();
    }

    /**
     * Verifies that the installed OS lives on the system disk's SYSTEM_OS component:
     * installing the OS stamps the disk, and removing that disk makes hasOs() return false
     * (the computer falls back to firmware).
     */
    @GameTest(template = ARENA)
    public static void os_osLivesOnSystemDisk(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);

        // Place a Mainframe and install a valid hardware build including a disk.
        helper.setBlock(pos, ComputingModule.MAINFRAME.get());
        if (!(helper.getBlockEntity(pos) instanceof MainframeBlockEntity mainframe)) {
            helper.fail("no MainframeBlockEntity at " + pos);
            return;
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
        // The disk must be installed BEFORE installOs() so the footprint check finds a disk.
        inv.setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START,
                new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500)));
        mainframe.togglePower();

        final ResourceLocation soRede =
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "mc_net");

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // Install the Network OS onto the disk.
                    final boolean installed = mainframe.installOs(soRede);
                    helper.assertTrue(installed, "installOs(mc_net) must return true");
                    helper.assertTrue(mainframe.hasOs(), "hasOs() must be true after installation");
                    helper.assertTrue(soRede.equals(mainframe.installedOsId()),
                            "installedOsId() must equal jsc:mc_net; got " + mainframe.installedOsId());

                    // The system disk stack must carry the SYSTEM_OS component.
                    final ItemStack sysDisk = mainframe.systemDisk();
                    helper.assertFalse(sysDisk.isEmpty(), "systemDisk() must return a non-empty stack");
                    final ResourceLocation onDisk = sysDisk.get(ComputingModule.SYSTEM_OS.get());
                    helper.assertTrue(soRede.equals(onDisk),
                            "the system disk stack must carry SYSTEM_OS == jsc:mc_net; got " + onDisk);

                    // Remove the disk from the inventory, and the OS must disappear with it.
                    inv.setStackInSlot(MainframeBlockEntity.DISK_SLOTS_START, ItemStack.EMPTY);

                    helper.assertFalse(mainframe.hasOs(),
                            "hasOs() must be false once the system disk is removed");
                    final BootController.BootTarget target =
                            BootController.targetForComputer(mainframe);
                    helper.assertTrue(target == BootController.BootTarget.FIRMWARE,
                            "boot target must be FIRMWARE without the system disk; got " + target);
                })
                .thenSucceed();
    }

    // Task 5: DiskFilesystem API tests

    /**
     * A write followed by a read must return the same content (FLAT filesystem).
     */
    @GameTest(template = ARENA)
    public static void fs_writeThenReadRoundTrips(final GameTestHelper helper) {
        final ItemStack disk = new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1));

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final DiskFilesystem.WriteResult result = DiskFilesystem.write(
                            disk, "daily.iql", FileType.IQL, "SELECT *", 1000L,
                            FilesystemKind.FLAT);
                    helper.assertTrue(result == DiskFilesystem.WriteResult.OK,
                            "write must return OK; got: " + result);

                    final Optional<String> content = DiskFilesystem.read(disk, "daily.iql");
                    helper.assertTrue(content.isPresent(),
                            "read must return a non-empty Optional after write");
                    helper.assertTrue("SELECT *".equals(content.get()),
                            "read content must equal the written value; got: " + content.get());
                })
                .thenSucceed();
    }

    /**
     * A write whose content cost exceeds the free-weight budget must return DISK_FULL
     * without modifying the disk.
     */
    @GameTest(template = ARENA)
    public static void fs_writeRejectsWhenDiskFull(final GameTestHelper helper) {
        final ItemStack disk = new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1));

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // Build a content string whose byte size (in mB-eq) exceeds the 0 budget.
                    final String largeContent = "A".repeat(4097); // 4 097 bytes → 2 mB-eq
                    final DiskFilesystem.WriteResult result = DiskFilesystem.write(
                            disk, "big.txt", FileType.TXT, largeContent, 0L,
                            FilesystemKind.FLAT);
                    helper.assertTrue(result == DiskFilesystem.WriteResult.DISK_FULL,
                            "write must return DISK_FULL when cost > freeWeight; got: " + result);

                    // The disk must NOT have been mutated.
                    helper.assertFalse(DiskFilesystem.exists(disk, "big.txt"),
                            "the file must not exist on the disk after a DISK_FULL rejection");
                })
                .thenSucceed();
    }

    /**
     * Writing with FileType.DAT (a virtual projection type) must be rejected as READ_ONLY.
     */
    @GameTest(template = ARENA)
    public static void fs_cannotWriteDatType(final GameTestHelper helper) {
        final ItemStack disk = new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1));

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final DiskFilesystem.WriteResult result = DiskFilesystem.write(
                            disk, "storage.dat", FileType.DAT, "", 1000L,
                            FilesystemKind.FLAT);
                    helper.assertTrue(result == DiskFilesystem.WriteResult.READ_ONLY,
                            "write with FileType.DAT must return READ_ONLY; got: " + result);
                })
                .thenSucceed();
    }

    // Task 6: StorageProjection tests

    /**
     * A disk whose storage volume holds two StorageKeys must surface two read-only .dat entries in
     * list(); read() on a .dat path must return empty; delete() on a .dat path must return false.
     */
    @GameTest(template = ARENA)
    public static void fs_datProjectionMirrorsStorageReadOnly(final GameTestHelper helper) {
        final ItemStack disk = new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1));

        // Fill the disk's volume with two distinct StorageKeys.
        final StorageKey cobble = StorageKey.of(Items.COBBLESTONE);
        final StorageKey iron = StorageKey.of(Items.IRON_INGOT);
        final long cobbleQty = 500L;
        final long ironQty = 3L;
        final Map<StorageKey, Long> map = new LinkedHashMap<>();
        map.put(cobble, cobbleQty);
        map.put(iron, ironQty);
        DriveVolumes.write(disk, map);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // list() in FLAT mode must include two .dat entries.
                    final List<DiskFilesystem.FileEntry> entries =
                            DiskFilesystem.list(disk, "", FilesystemKind.FLAT);
                    final long datCount = entries.stream()
                            .filter(e -> e.type() == FileType.DAT)
                            .count();
                    helper.assertTrue(datCount == 2L,
                            "expected 2 .dat entries from the storage volume; got: " + datCount);

                    // All .dat entries must be read-only.
                    final boolean allReadOnly = entries.stream()
                            .filter(e -> e.type() == FileType.DAT)
                            .allMatch(DiskFilesystem.FileEntry::readOnly);
                    helper.assertTrue(allReadOnly,
                            ".dat entries must all be marked readOnly");

                    // Each .dat entry's weight must equal key.weight(qty).
                    boolean cobbleWeightOk = false;
                    boolean ironWeightOk = false;
                    for (final DiskFilesystem.FileEntry e : entries) {
                        if (e.type() != FileType.DAT) {
                            continue;
                        }
                        if (e.weight() == cobble.weight(cobbleQty)) {
                            cobbleWeightOk = true;
                        }
                        if (e.weight() == iron.weight(ironQty)) {
                            ironWeightOk = true;
                        }
                    }
                    helper.assertTrue(cobbleWeightOk,
                            "no .dat entry with weight == cobble.weight(500)");
                    helper.assertTrue(ironWeightOk,
                            "no .dat entry with weight == iron.weight(3)");

                    // read() on a .dat path must return empty.
                    final String firstDatPath = entries.stream()
                            .filter(e -> e.type() == FileType.DAT)
                            .findFirst()
                            .map(DiskFilesystem.FileEntry::path)
                            .orElse("");
                    helper.assertFalse(firstDatPath.isEmpty(),
                            "expected at least one .dat entry to have a path");
                    final Optional<String> readResult = DiskFilesystem.read(disk, firstDatPath);
                    helper.assertFalse(readResult.isPresent(),
                            "read() on a .dat path must return empty; got: " + readResult);

                    // delete() on a .dat path must return false.
                    final boolean deleted = DiskFilesystem.delete(disk, firstDatPath);
                    helper.assertFalse(deleted,
                            "delete() on a .dat path must return false");
                })
                .thenSucceed();
    }

    // Real directories (hierarchical filesystem, the Frames desktop)

    /**
     * mkdir creates a persistent empty directory on a hierarchical disk and listDirs surfaces it;
     * a flat filesystem rejects directories.
     */
    @GameTest(template = ARENA)
    public static void fs_mkdirCreatesAndListsDirectory(final GameTestHelper helper) {
        final ItemStack disk = new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1));

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(DiskFilesystem.mkdir(disk, "Projects", FilesystemKind.HIERARCHICAL),
                            "mkdir must return true on a hierarchical disk");
                    final List<String> dirs =
                            DiskFilesystem.listDirs(disk, "", FilesystemKind.HIERARCHICAL);
                    helper.assertTrue(dirs.contains("Projects"),
                            "listDirs must include the new directory; got " + dirs);
                    helper.assertFalse(DiskFilesystem.mkdir(disk, "More", FilesystemKind.FLAT),
                            "mkdir must return false on a flat filesystem");
                })
                .thenSucceed();
    }

    /**
     * A file written under a subdirectory implies that directory in listDirs and is listed inside
     * it (not at the root).
     */
    @GameTest(template = ARENA)
    public static void fs_listDirsInfersFromNestedFile(final GameTestHelper helper) {
        final ItemStack disk = new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1));

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final DiskFilesystem.WriteResult r = DiskFilesystem.write(
                            disk, "Docs/notes.txt", FileType.TXT, "hi", 1000L,
                            FilesystemKind.HIERARCHICAL);
                    helper.assertTrue(r == DiskFilesystem.WriteResult.OK,
                            "nested write must succeed; got " + r);
                    final List<String> dirs =
                            DiskFilesystem.listDirs(disk, "", FilesystemKind.HIERARCHICAL);
                    helper.assertTrue(dirs.contains("Docs"),
                            "listDirs must infer 'Docs' from the nested file; got " + dirs);
                    final List<DiskFilesystem.FileEntry> atRoot =
                            DiskFilesystem.list(disk, "", FilesystemKind.HIERARCHICAL);
                    helper.assertTrue(atRoot.stream().noneMatch(e -> e.path().equals("Docs/notes.txt")),
                            "the nested file must not appear at the root");
                    final List<DiskFilesystem.FileEntry> inDocs =
                            DiskFilesystem.list(disk, "Docs", FilesystemKind.HIERARCHICAL);
                    helper.assertTrue(inDocs.stream().anyMatch(e -> e.path().equals("Docs/notes.txt")),
                            "the nested file must be listed inside Docs");
                })
                .thenSucceed();
    }

    /**
     * rmdir removes a directory and everything nested under it.
     */
    @GameTest(template = ARENA)
    public static void fs_rmdirRemovesRecursively(final GameTestHelper helper) {
        final ItemStack disk = new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1));

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    DiskFilesystem.mkdir(disk, "Work", FilesystemKind.HIERARCHICAL);
                    DiskFilesystem.write(disk, "Work/a.txt", FileType.TXT, "x", 1000L,
                            FilesystemKind.HIERARCHICAL);
                    DiskFilesystem.write(disk, "Work/sub/b.txt", FileType.TXT, "y", 1000L,
                            FilesystemKind.HIERARCHICAL);
                    helper.assertTrue(DiskFilesystem.rmdir(disk, "Work", FilesystemKind.HIERARCHICAL),
                            "rmdir must return true when it removed content");
                    helper.assertFalse(DiskFilesystem.exists(disk, "Work/a.txt"),
                            "nested file a.txt must be gone");
                    helper.assertFalse(DiskFilesystem.exists(disk, "Work/sub/b.txt"),
                            "nested file b.txt must be gone");
                    helper.assertTrue(
                            DiskFilesystem.listDirs(disk, "", FilesystemKind.HIERARCHICAL).isEmpty(),
                            "no directories must remain after rmdir of the only tree");
                })
                .thenSucceed();
    }

    /**
     * Renaming a directory re-keys every file nested under it, preserving content.
     */
    @GameTest(template = ARENA)
    public static void fs_renameDirectoryRekeysContents(final GameTestHelper helper) {
        final ItemStack disk = new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1));

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    DiskFilesystem.write(disk, "Old/a.txt", FileType.TXT, "data", 1000L,
                            FilesystemKind.HIERARCHICAL);
                    helper.assertTrue(DiskFilesystem.rename(disk, "Old", "New", FilesystemKind.HIERARCHICAL),
                            "directory rename must succeed");
                    helper.assertFalse(DiskFilesystem.exists(disk, "Old/a.txt"), "old path must be gone");
                    helper.assertTrue(DiskFilesystem.exists(disk, "New/a.txt"),
                            "content must move to the new path");
                    final Optional<String> c = DiskFilesystem.read(disk, "New/a.txt");
                    helper.assertTrue(c.isPresent() && "data".equals(c.get()),
                            "content must survive the rename");
                })
                .thenSucceed();
    }

    /**
     * Renaming a directory onto an already-occupied destination is rejected, so colliding files in the
     * destination are never silently overwritten (no data loss).
     */
    @GameTest(template = ARENA)
    public static void fs_relocateRejectsDirectoryCollision(final GameTestHelper helper) {
        final ItemStack disk = new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1));

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    DiskFilesystem.write(disk, "Old/a.txt", FileType.TXT, "moving", 1000L,
                            FilesystemKind.HIERARCHICAL);
                    DiskFilesystem.write(disk, "New/a.txt", FileType.TXT, "keep", 1000L,
                            FilesystemKind.HIERARCHICAL);
                    helper.assertFalse(
                            DiskFilesystem.rename(disk, "Old", "New", FilesystemKind.HIERARCHICAL),
                            "renaming a directory onto an occupied one must be rejected");
                    final Optional<String> kept = DiskFilesystem.read(disk, "New/a.txt");
                    helper.assertTrue(kept.isPresent() && "keep".equals(kept.get()),
                            "the destination file must NOT be overwritten when the rename is rejected");
                    final Optional<String> src = DiskFilesystem.read(disk, "Old/a.txt");
                    helper.assertTrue(src.isPresent() && "moving".equals(src.get()),
                            "the source file must remain after the rejected rename (no data lost)");
                })
                .thenSucceed();
    }

    /**
     * A program runs only on an OS that meets its declared capability and era: the NMS needs a full desktop
     * (Frames), the IQL Engine service runs anywhere, an unregistered program is unrestricted, and a gated
     * program is refused when no OS is installed.
     */
    @GameTest(template = ARENA)
    public static void os_programGatingHonorsCapability(final GameTestHelper helper) {
        final ResourceLocation nms = ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "nms");
        final ResourceLocation iql = ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "iqlengine");
        final ResourceLocation frames95 = ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "frames_95");
        final ResourceLocation framesXp = ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "frames_xp");
        final ResourceLocation mcDos = ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "mc_dos");
        final ResourceLocation unknown =
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "no_such_program");

        // Ample hardware, so only the platform and OS-version gates decide the outcome here.
        final int cpu = 9999;
        final int vram = 9999;
        helper.assertFalse(OsRegistry.canRunProgram(frames95, nms, cpu, vram),
                "the NMS needs Frames XP or newer, so Frames 95 must refuse it");
        helper.assertTrue(OsRegistry.canRunProgram(framesXp, nms, cpu, vram),
                "the NMS must run on Frames XP");
        helper.assertFalse(OsRegistry.canRunProgram(mcDos, nms, cpu, vram),
                "the NMS must be refused on MC-DOS (wrong platform)");
        helper.assertTrue(OsRegistry.canRunProgram(mcDos, iql, cpu, vram),
                "the IQL Engine (a headless service) still runs on a non-Frames Mainframe");
        helper.assertFalse(OsRegistry.canRunProgram(null, nms, cpu, vram),
                "a gated program must be refused when no OS is installed");
        helper.assertTrue(OsRegistry.canRunProgram(mcDos, unknown, cpu, vram),
                "an unregistered program declares no requirement and must pass");
        // The 11-only Automation Manager: refused on Frames XP, allowed on Frames 11.
        final ResourceLocation frames11 = ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "frames_11");
        final ResourceLocation autoMgr =
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "automation_manager");
        helper.assertFalse(OsRegistry.canRunProgram(framesXp, autoMgr, cpu, vram),
                "the Automation Manager needs Frames 11, so Frames XP must refuse it");
        helper.assertTrue(OsRegistry.canRunProgram(frames11, autoMgr, cpu, vram),
                "the Automation Manager must run on Frames 11");
        helper.succeed();
    }

    /**
     * The single program registry is well-formed: every built-in program is registered, and each carries a
     * usable command name, display name and at least one platform. This guards the descriptor list against a
     * registration mistake (an empty field, a program that never reached the registry).
     */
    @GameTest(template = ARENA)
    public static void programs_registryIsWellFormed(final GameTestHelper helper) {
        final var builtins = dev.jstech.computronics.os.OsBootstrap.builtinPrograms();
        helper.assertTrue(!builtins.isEmpty(), "the built-in program list must not be empty");
        for (final var spec : builtins) {
            helper.assertTrue(OsRegistry.getProgram(spec.id()) == spec,
                    "program " + spec.id() + " must be registered in the registry");
            helper.assertTrue(!spec.commandName().isBlank(), "program " + spec.id() + " needs a command name");
            helper.assertTrue(!spec.displayName().isBlank(), "program " + spec.id() + " needs a display name");
            helper.assertTrue(!spec.platforms().isEmpty(), "program " + spec.id() + " needs a platform");
        }
        // A known program resolves, and its title key follows the vanilla convention.
        final ResourceLocation nms = ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "nms");
        helper.assertTrue(OsRegistry.getProgram(nms) != null, "the NMS must be registered");
        helper.assertTrue(OsRegistry.getProgram(nms).titleKey().equals("program.jsc.nms"),
                "the title key must be program.jsc.nms");

        /*
         * The OS registry is well-formed too: every built-in OS is registered with a display name and a
         * kernel that itself exists, so its lang key and install disc derive cleanly.
         */
        final var oses = dev.jstech.computronics.os.OsBootstrap.builtinOses();
        helper.assertTrue(!oses.isEmpty(), "the built-in OS list must not be empty");
        for (final var os : oses) {
            helper.assertTrue(OsRegistry.getOs(os.id()) == os, "OS " + os.id() + " must be registered");
            helper.assertTrue(!os.displayName().isBlank(), "OS " + os.id() + " needs a display name");
            helper.assertTrue(OsRegistry.getKernel(os.kernelId()) != null,
                    "OS " + os.id() + " references kernel " + os.kernelId() + " which must be registered");
        }
        helper.succeed();
    }

    /**
     * Moving a file into a directory relocates it, keeping its name.
     */
    @GameTest(template = ARENA)
    public static void fs_moveFileIntoDirectory(final GameTestHelper helper) {
        final ItemStack disk = new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1));

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    DiskFilesystem.write(disk, "report.txt", FileType.TXT, "z", 1000L,
                            FilesystemKind.HIERARCHICAL);
                    DiskFilesystem.mkdir(disk, "Archive", FilesystemKind.HIERARCHICAL);
                    helper.assertTrue(
                            DiskFilesystem.move(disk, "report.txt", "Archive", FilesystemKind.HIERARCHICAL),
                            "move must succeed");
                    helper.assertFalse(DiskFilesystem.exists(disk, "report.txt"),
                            "the file must leave the root");
                    helper.assertTrue(DiskFilesystem.exists(disk, "Archive/report.txt"),
                            "the file must arrive in Archive");
                })
                .thenSucceed();
    }

    /**
     * Installing a graphical desktop OS seeds the Windows-like system folder skeleton on the disk.
     */
    @GameTest(template = ARENA)
    public static void os_desktopInstallSeedsSystemFolders(final GameTestHelper helper) {
        final BlockPos pos = new BlockPos(2, 2, 2);
        helper.setBlock(pos, ComputingModule.MAINFRAME.get());
        if (!(helper.getBlockEntity(pos) instanceof MainframeBlockEntity mainframe)) {
            helper.fail("no MainframeBlockEntity at " + pos);
            return;
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
                new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1)));
        mainframe.togglePower();

        final ResourceLocation framesXp =
                ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, "frames_xp");

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(mainframe.installOs(framesXp), "installOs(frames_xp) must succeed");
                    final ItemStack disk = mainframe.systemDisk();
                    helper.assertFalse(disk.isEmpty(), "system disk must be present");
                    final FilesystemContents fs = disk.get(ComputingModule.FILESYSTEM.get());
                    helper.assertTrue(fs != null,
                            "FILESYSTEM component must be present after a desktop install");
                    helper.assertTrue(fs.hasDir("Program Files"), "Program Files must be seeded");
                    helper.assertTrue(fs.hasDir("Users/Public/Desktop"),
                            "the desktop folder must be seeded");
                })
                .thenSucceed();
    }

    /**
     * The console state (installed programs and the desktop personalization) must survive an
     * NBT save/load round-trip (a world reload).
     */
    @GameTest(template = ARENA)
    public static void console_prefsAndInstallPersistNbt(final GameTestHelper helper) {
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final dev.jstech.computronics.program.ComputerConsoleState state =
                            new dev.jstech.computronics.program.ComputerConsoleState();
                    state.install("jsc:nms");
                    state.setWallpaper("winxp");
                    state.setComputerName("HAL");
                    final net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                    state.save(tag);

                    final dev.jstech.computronics.program.ComputerConsoleState loaded =
                            new dev.jstech.computronics.program.ComputerConsoleState();
                    loaded.load(tag);
                    helper.assertTrue(loaded.isInstalled("jsc:nms"),
                            "an installed program must persist across a reload");
                    helper.assertTrue("winxp".equals(loaded.wallpaper()),
                            "the chosen wallpaper must persist; got " + loaded.wallpaper());
                    helper.assertTrue("HAL".equals(loaded.computerName()),
                            "the computer name must persist; got " + loaded.computerName());
                })
                .thenSucceed();
    }

    /**
     * A free-positioned desktop icon's grid cell must survive an NBT save/load round-trip (a world reload),
     * so an icon the player moved stays exactly where they left it.
     */
    @GameTest(template = ARENA)
    public static void console_iconPositionPersistsNbt(final GameTestHelper helper) {
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final dev.jstech.computronics.program.ComputerConsoleState state =
                            new dev.jstech.computronics.program.ComputerConsoleState();
                    final int cell =
                            dev.jstech.computronics.program.ComputerConsoleState.packCell(2, 3);
                    state.setIconCell("file:Notes.txt", cell);
                    state.setIconCell("app:Network",
                            dev.jstech.computronics.program.ComputerConsoleState.packCell(1, 0));
                    final net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                    state.save(tag);

                    final dev.jstech.computronics.program.ComputerConsoleState loaded =
                            new dev.jstech.computronics.program.ComputerConsoleState();
                    loaded.load(tag);
                    final Integer back = loaded.iconCells().get("file:Notes.txt");
                    helper.assertTrue(back != null && back == cell,
                            "a pinned icon's cell must persist; got " + back);
                    helper.assertTrue(
                            dev.jstech.computronics.program.ComputerConsoleState
                                    .cellColumn(back) == 2,
                            "the persisted column must be 2");
                    helper.assertTrue(
                            dev.jstech.computronics.program.ComputerConsoleState
                                    .cellRow(back) == 3,
                            "the persisted row must be 3");
                    helper.assertTrue(loaded.iconCells().containsKey("app:Network"),
                            "a pinned launcher's cell must persist too");
                })
                .thenSucceed();
    }

    /**
     * Dragging a file from the desktop folder into another folder, then back to the desktop, conserves the
     * file and its content end to end (the move never loses or duplicates it), the data path behind the
     * cross-window desktop&lt;-&gt;explorer drag.
     */
    @GameTest(template = ARENA)
    public static void fs_moveBetweenDesktopAndFolderConservesFile(final GameTestHelper helper) {
        final ItemStack disk = new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1));
        final String desktop = dev.jstech.computronics.os.fs.SystemLayout.DESKTOP_DIR;

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // Seed a file on the desktop and a destination folder.
                    DiskFilesystem.write(disk, desktop + "/notes.txt", FileType.TXT, "payload", 100000L,
                            FilesystemKind.HIERARCHICAL);
                    DiskFilesystem.mkdir(disk, "Documents", FilesystemKind.HIERARCHICAL);

                    // Desktop -> Documents (the cross-window drag from the desktop into an explorer window).
                    helper.assertTrue(
                            DiskFilesystem.move(disk, desktop + "/notes.txt", "Documents",
                                    FilesystemKind.HIERARCHICAL),
                            "desktop -> folder move must succeed");
                    helper.assertFalse(DiskFilesystem.exists(disk, desktop + "/notes.txt"),
                            "the file must leave the desktop");
                    helper.assertTrue(DiskFilesystem.exists(disk, "Documents/notes.txt"),
                            "the file must arrive in Documents");

                    // Documents -> Desktop (the cross-window drag from an explorer window back onto the desktop).
                    helper.assertTrue(
                            DiskFilesystem.move(disk, "Documents/notes.txt", desktop,
                                    FilesystemKind.HIERARCHICAL),
                            "folder -> desktop move must succeed");
                    helper.assertFalse(DiskFilesystem.exists(disk, "Documents/notes.txt"),
                            "the file must leave Documents");
                    helper.assertTrue(DiskFilesystem.exists(disk, desktop + "/notes.txt"),
                            "the file must return to the desktop");

                    // Content survives the whole round-trip (conservation).
                    final Optional<String> content = DiskFilesystem.read(disk, desktop + "/notes.txt");
                    helper.assertTrue(content.isPresent() && "payload".equals(content.get()),
                            "the file content must survive the round-trip; got " + content);
                })
                .thenSucceed();
    }

    /**
     * A {@code .dat} projection cannot be moved between folders by hand, and the move is refused without
     * mutating the disk, so the no-drag rule holds at the data layer (the desktop and explorer also block
     * it client-side and raise the locked dialog).
     */
    @GameTest(template = ARENA)
    public static void fs_datCannotBeMovedBetweenFolders(final GameTestHelper helper) {
        final ItemStack disk = new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1));

        // A stored key projects exactly one read-only .dat at the root.
        final StorageKey cobble = StorageKey.of(Items.COBBLESTONE);
        final Map<StorageKey, Long> map = new LinkedHashMap<>();
        map.put(cobble, 64L);
        DriveVolumes.write(disk, map);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    DiskFilesystem.mkdir(disk, "Documents", FilesystemKind.HIERARCHICAL);
                    // On a hierarchical disk the .dat projection lives under "Storage".
                    final String datPath =
                            DiskFilesystem.list(disk, "Storage", FilesystemKind.HIERARCHICAL).stream()
                                    .filter(e -> e.type() == FileType.DAT)
                                    .findFirst()
                                    .map(DiskFilesystem.FileEntry::path)
                                    .orElse("");
                    helper.assertFalse(datPath.isEmpty(), "a projected .dat must be present to test the rule");
                    helper.assertFalse(
                            DiskFilesystem.move(disk, datPath, "Documents", FilesystemKind.HIERARCHICAL),
                            "moving a .dat between folders must be refused");
                    // The projection still surfaces (nothing was mutated); the .dat did not relocate.
                    final boolean stillProjected =
                            DiskFilesystem.list(disk, "Storage", FilesystemKind.HIERARCHICAL)
                                    .stream().anyMatch(e -> e.type() == FileType.DAT);
                    helper.assertTrue(stillProjected, "the .dat projection must remain after a refused move");
                })
                .thenSucceed();
    }
}
