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
import dev.jstech.computers.operation.payload.ComputingPayloads;
import dev.jstech.computers.os.FilesystemKind;
import dev.jstech.computers.os.fs.DiskFilesystem;
import dev.jstech.computers.os.fs.FileType;
import dev.jstech.computers.os.media.MediaItem;
import dev.jstech.computers.os.media.MediaKind;
import dev.jstech.computers.storage.DriveVolumes;
import dev.jstech.computers.storage.StorageKey;
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

/**
 * In-world tests for the {@code .dat} integrity rule: a {@code .dat} is a read-only projection of stored
 * items, so it can never be created, written, deleted, or renamed by hand, and the one sanctioned manual
 * action, dragging it onto a removable medium, moves the underlying item conservatively (no loss, no dupe).
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class OsDatLockGameTests {

    private OsDatLockGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int SETTLE = 4;

    /**
     * A manual {@code .dat} action (delete or rename) is refused at the filesystem API, the gate every
     * client and server path funnels through. Neither mutates the disk.
     */
    @GameTest(template = ARENA)
    public static void dat_deleteAndRenameAreRefused(final GameTestHelper helper) {
        final ItemStack disk = new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1));
        final Map<StorageKey, Long> map = new LinkedHashMap<>();
        map.put(StorageKey.of(Items.IRON_INGOT), 7L);
        DriveVolumes.write(disk, map);

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    final List<DiskFilesystem.FileEntry> entries =
                            DiskFilesystem.list(disk, "", FilesystemKind.FLAT);
                    final String datPath = entries.stream()
                            .filter(e -> e.type() == FileType.DAT)
                            .map(DiskFilesystem.FileEntry::path)
                            .findFirst()
                            .orElse("");
                    helper.assertFalse(datPath.isEmpty(), "a .dat projection must be present");

                    // Delete must refuse.
                    helper.assertFalse(DiskFilesystem.delete(disk, datPath),
                            "deleting a .dat must be refused");
                    // Rename must refuse (FLAT has no rename, but HIERARCHICAL relocate must reject the .dat).
                    helper.assertFalse(
                            DiskFilesystem.rename(disk, datPath, "renamed.dat", FilesystemKind.HIERARCHICAL),
                            "renaming a .dat must be refused");

                    // The storage projection is unchanged: the .dat is still there.
                    final boolean stillThere = DiskFilesystem.list(disk, "", FilesystemKind.FLAT).stream()
                            .anyMatch(e -> e.type() == FileType.DAT && e.path().equals(datPath));
                    helper.assertTrue(stillThere, "the .dat must survive the refused delete/rename");
                })
                .thenSucceed();
    }

    /**
     * Creating a {@code .dat} by hand is refused everywhere: writing a {@code FileType.DAT} returns READ_ONLY,
     * and writing a {@code .dat}-named file with a real type cannot happen because the {@code .dat} extension
     * only ever resolves to the virtual-projection type, which {@code write} rejects.
     */
    @GameTest(template = ARENA)
    public static void dat_creationIsRefused(final GameTestHelper helper) {
        final ItemStack disk = new ItemStack(ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1));

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    // Writing with the projection type is rejected.
                    helper.assertTrue(
                            DiskFilesystem.write(disk, "loot.dat", FileType.DAT, "", 100_000L,
                                    FilesystemKind.FLAT) == DiskFilesystem.WriteResult.READ_ONLY,
                            "writing a FileType.DAT file must return READ_ONLY");

                    // The .dat extension resolves only to the read-only projection type, which is not editable.
                    final FileType resolved = FileType.fromExtension("dat").orElse(null);
                    helper.assertTrue(resolved == FileType.DAT,
                            ".dat must resolve to FileType.DAT");
                    helper.assertFalse(resolved.userEditable(),
                            "FileType.DAT must not be user-editable (no manual create/write)");

                    // No .dat file was created on the disk.
                    helper.assertFalse(DiskFilesystem.exists(disk, "loot.dat"),
                            "no .dat file may exist after a refused create");
                })
                .thenSucceed();
    }

    /**
     * Dragging a {@code .dat} onto a removable DATA medium moves the stored item conservatively: the sum of the
     * item across the source disk storage and the medium snapshot is invariant, and the source loses exactly
     * what the medium gains.
     */
    @GameTest(template = ARENA)
    public static void dat_mediaTransferConservesItems(final GameTestHelper helper) {
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

        final ResourceLocation mcNetOs =
                ResourceLocation.fromNamespaceAndPath(JsComputers.MODID, "mc_net");

        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    helper.assertTrue(mainframe.installOs(mcNetOs), "installOs(mc_net) must succeed");

                    // Seed the system disk's storage with a known item quantity so a .dat projects from it.
                    final StorageKey key = StorageKey.of(Items.IRON_INGOT);
                    final long startQty = 40L;
                    final ItemStack sysDisk = mainframe.systemDisk();
                    helper.assertFalse(sysDisk.isEmpty(), "the system disk must be present");
                    final Map<StorageKey, Long> seed = new LinkedHashMap<>();
                    seed.put(key, startQty);
                    DriveVolumes.write(sysDisk, seed);

                    // Resolve the .dat path the projection emits, then invert it back to the StorageKey.
                    final String datPath = DiskFilesystem.list(sysDisk, "", FilesystemKind.FLAT).stream()
                            .filter(e -> e.type() == FileType.DAT)
                            .map(DiskFilesystem.FileEntry::path)
                            .findFirst()
                            .orElse("");
                    helper.assertFalse(datPath.isEmpty(), "the disk must project a .dat for the stored item");
                    final StorageKey resolved = ComputingPayloads.resolveDatKey(sysDisk, datPath);
                    helper.assertTrue(key.equals(resolved),
                            "the .dat path must invert back to its StorageKey; got " + resolved);

                    // A DATA medium with capacity for the whole stack.
                    final ItemStack media = new ItemStack(ComputingModule.CD_ROM.get());
                    MediaItem.setKind(media, MediaKind.DATA);

                    final long beforeDisk = mainframe.localStore().count(key);
                    final long beforeMedia = MediaItem.data(media).count(key);
                    helper.assertTrue(beforeDisk == startQty,
                            "the local store must report the seeded quantity; got " + beforeDisk);

                    // Move the item onto the medium and assert conservation.
                    final long moved = ComputingPayloads.transferDatToMedium(mainframe, key, media);
                    helper.assertTrue(moved > 0L, "the transfer must move at least one item");

                    final long afterDisk = mainframe.localStore().count(key);
                    final long afterMedia = MediaItem.data(media).count(key);

                    helper.assertTrue(beforeDisk + beforeMedia == afterDisk + afterMedia,
                            "the item total must be conserved across the transfer; before="
                                    + (beforeDisk + beforeMedia) + " after=" + (afterDisk + afterMedia));
                    helper.assertTrue(afterDisk == beforeDisk - moved,
                            "the source must lose exactly what was moved");
                    helper.assertTrue(afterMedia == beforeMedia + moved,
                            "the medium must gain exactly what was moved");
                })
                .thenSucceed();
    }
}
