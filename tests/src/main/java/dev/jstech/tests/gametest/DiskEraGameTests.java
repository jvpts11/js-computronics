/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jsc.jscomputronics.JsComputronics;
import dev.jsc.jscomputronics.common.hardware.DiskSize;
import dev.jsc.jscomputronics.common.hardware.DiskSpec;
import dev.jsc.jscomputronics.common.hardware.StorageTier;
import dev.jstech.core.tier.HardwareEra;
import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.HardwareItems;
import dev.jsc.jscomputronics.module.computing.os.OsDef;
import dev.jsc.jscomputronics.module.computing.os.OsDisks;
import dev.jsc.jscomputronics.module.computing.os.OsRegistry;
import dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem;
import dev.jsc.jscomputronics.module.computing.os.fs.FileType;
import dev.jsc.jscomputronics.module.computing.os.FilesystemKind;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jstech.tests.JsTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * What an item costs on a disk follows from the word size of the era the disk was made for: 1 MB at 16
 * bits, 16 MB at 32, 256 MB at 64. A drive's nameplate is therefore honest in every era, a system's size in
 * megabytes costs more items on older disks, and a file's bytes weigh more there too.
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class DiskEraGameTests {

    private DiskEraGameTests() {
    }

    private static final String ARENA = "empty";

    private static OsDef os(final String path) {
        final OsDef def = OsRegistry.getOs(ResourceLocation.fromNamespaceAndPath(JsComputronics.MODID, path));
        if (def == null) {
            throw new IllegalStateException("no such OS: " + path);
        }
        return def;
    }

    @GameTest(template = ARENA)
    public static void diskSpec_nameplateFollowsTheEraOfTheDrive(final GameTestHelper helper) {
        final DiskSpec vintage = HardwareItems.DISK_TRENCH_20M.get().spec();
        helper.assertTrue(vintage.era() == HardwareEra.VINTAGE && vintage.capacityItems() == 20 && vintage.capacityMb() == 20,
                "the Trench 20M is a 20 MB vintage drive of 20 items; got " + vintage);
        helper.assertTrue("20 MB".equals(DiskSpec.sizeLabel(vintage.capacityMb())), "labelled 20 MB");
        final DiskSpec legacy = HardwareItems.DISK_LINK_IDE_4G.get().spec();
        helper.assertTrue(legacy.era() == HardwareEra.LEGACY && legacy.capacityItems() == 256 && legacy.capacityMb() == 4_096,
                "the IDE 4G is a 4 GB legacy drive of 256 items; got " + legacy);
        final DiskSpec standard = ComputingModule.disk(StorageTier.NVME, DiskSize.TB_1).spec();
        helper.assertTrue(standard.era() == HardwareEra.STANDARD && standard.capacityItems() == 4_096
                        && "1 TB".equals(DiskSpec.sizeLabel(standard.capacityMb())),
                "the 1 TB NVMe is 4 096 items at 256 MB each; got " + standard + " " + DiskSpec.sizeLabel(standard.capacityMb()));
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void osFootprint_costsItemsByTheDiskEra(final GameTestHelper helper) {
        final OsDef frames11 = os("frames_11");
        helper.assertTrue(frames11.footprintMb() == 20_480, "Frames 11 is 20 GB; got " + frames11.footprintMb());
        helper.assertTrue(frames11.footprintItemsOn(HardwareEra.STANDARD) == 80, "80 items of a standard disk; got "
                + frames11.footprintItemsOn(HardwareEra.STANDARD));
        helper.assertTrue(frames11.footprintItemsOn(HardwareEra.VINTAGE) == 20_480,
                "more than any vintage drive holds; got " + frames11.footprintItemsOn(HardwareEra.VINTAGE));
        final OsDef dos = os("mc_dos");
        helper.assertTrue(dos.footprintItemsOn(HardwareEra.VINTAGE) == 4 && dos.footprintItemsOn(HardwareEra.STANDARD) == 1,
                "MC-DOS is 4 items of a vintage drive and one of a standard one; got "
                        + dos.footprintItemsOn(HardwareEra.VINTAGE) + " / " + dos.footprintItemsOn(HardwareEra.STANDARD));
        // Installed on a 100 MB vintage drive, Frames 95 (48 MB) leaves 52 items' worth of room.
        final ItemStack trench = new ItemStack(HardwareItems.DISK_TRENCH_100M.get());
        trench.set(ComputingModule.SYSTEM_OS.get(), os("frames_95").id());
        final long free = OsDisks.systemDiskFreeWeight(trench);
        helper.assertTrue(free == 52L * StorageKey.MB_EQ_PER_ITEM, "52 000 mB-eq free; got " + free);
        helper.succeed();
    }

    @GameTest(template = ARENA)
    public static void files_weighMoreOnAnOlderDisk(final GameTestHelper helper) {
        final String text = "x".repeat(3_000);
        final ItemStack vintage = new ItemStack(HardwareItems.DISK_TRENCH_100M.get());
        final ItemStack standard = new ItemStack(ComputingModule.disk(StorageTier.HDD, DiskSize.GB_500));
        helper.assertTrue(DiskFilesystem.write(vintage, "notes.txt", FileType.TXT, text, 100_000L,
                FilesystemKind.HIERARCHICAL, 0L) == DiskFilesystem.WriteResult.OK, "the vintage drive takes the file");
        helper.assertTrue(DiskFilesystem.write(standard, "notes.txt", FileType.TXT, text, 100_000L,
                FilesystemKind.HIERARCHICAL, 0L) == DiskFilesystem.WriteResult.OK, "the standard drive takes the file");
        helper.assertTrue(DiskFilesystem.filesWeight(vintage) == 3L, "3 000 bytes are 3 mB-eq at 16 bits; got "
                + DiskFilesystem.filesWeight(vintage));
        helper.assertTrue(DiskFilesystem.filesWeight(standard) == 1L, "and one block at 64 bits; got "
                + DiskFilesystem.filesWeight(standard));
        helper.succeed();
    }
}
