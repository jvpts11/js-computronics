/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.tests.gametest;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.os.FilesystemKind;
import dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem;
import dev.jsc.jscomputronics.module.computing.os.fs.FileType;
import dev.jstech.tests.JsTests;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The media filesystem under a churn of writes, reads and deletes, checking the one thing storage must never do:
 * lose or corrupt data. Every file read back must be byte-for-byte what was written; a delete removes exactly one
 * file and leaves the rest intact; a full disk and an invalid path are refused without mutating what is already
 * stored. Seeded and logged (pass {@code -Djsc.megaseed=N} to replay).
 */
@GameTestHolder(JsTests.MODID)
@PrefixGameTestTemplate(false)
public final class MediaFilesystemStressGameTests {

    private MediaFilesystemStressGameTests() {
    }

    private static final String ARENA = "empty";
    private static final int FILES = 60;
    private static final FilesystemKind KIND = FilesystemKind.HIERARCHICAL;
    private static final long ROOM = Long.MAX_VALUE / 4; // ample space for the bulk writes

    private static long seed() {
        final String override = System.getProperty("jsc.megaseed", "");
        return override.isEmpty() ? System.nanoTime() : Long.parseLong(override);
    }

    private static String body(final Random rng, final int i) {
        final StringBuilder sb = new StringBuilder("file-").append(i).append(':');
        final int len = 8 + rng.nextInt(200);
        for (int c = 0; c < len; c++) {
            sb.append((char) ('a' + rng.nextInt(26)));
        }
        return sb.toString();
    }

    @GameTest(template = ARENA)
    public static void mediaWritesReadsAndDeletesConserveData(final GameTestHelper helper) {
        final long seed = seed();
        final Random rng = new Random(seed);
        final ItemStack disk = new ItemStack(ComputingModule.FLOPPY_DISK.get());
        final Map<String, String> expected = new HashMap<>();

        // Write a batch of files and remember exactly what went in.
        for (int i = 0; i < FILES; i++) {
            final String path = "f" + i + ".txt";
            final String content = body(rng, i);
            final DiskFilesystem.WriteResult result = DiskFilesystem.write(disk, path, FileType.TXT, content, ROOM, KIND);
            helper.assertTrue(result == DiskFilesystem.WriteResult.OK, "write " + path + " must succeed; got " + result);
            expected.put(path, content);
        }

        // Every file must read back byte-for-byte, and the listing must hold them all.
        for (final Map.Entry<String, String> e : expected.entrySet()) {
            final String read = DiskFilesystem.read(disk, e.getKey()).orElse(null);
            helper.assertTrue(e.getValue().equals(read), e.getKey() + " must read back what was written (seed="
                    + seed + "); wrote " + e.getValue().length() + " chars, read " + (read == null ? "null" : read.length()));
        }
        final List<DiskFilesystem.FileEntry> listing = DiskFilesystem.list(disk, "", KIND);
        final long realFiles = listing.stream().filter(f -> f.type() == FileType.TXT).count();
        helper.assertTrue(realFiles >= FILES, "the listing must hold every written file; got " + realFiles + "/" + FILES);

        // Delete half; the deleted ones vanish, the rest survive untouched.
        int deleted = 0;
        for (int i = 0; i < FILES; i += 2) {
            final String path = "f" + i + ".txt";
            helper.assertTrue(DiskFilesystem.delete(disk, path), "delete " + path + " must report success");
            helper.assertTrue(DiskFilesystem.read(disk, path).isEmpty(), path + " must be gone after delete");
            expected.remove(path);
            deleted++;
        }
        helper.assertTrue(deleted == FILES / 2, "half the files must be deleted; deleted " + deleted);
        for (final Map.Entry<String, String> e : expected.entrySet()) {
            helper.assertTrue(e.getValue().equals(DiskFilesystem.read(disk, e.getKey()).orElse(null)),
                    e.getKey() + " must survive the deletes intact");
        }

        // A full disk and an invalid path are refused without disturbing the stored data.
        final String survivor = expected.keySet().iterator().next();
        final String survivorBody = expected.get(survivor);
        helper.assertTrue(DiskFilesystem.write(disk, "overflow.txt", FileType.TXT, "x", 0L, KIND)
                == DiskFilesystem.WriteResult.DISK_FULL, "a write with no free space must report DISK_FULL");
        helper.assertTrue(DiskFilesystem.read(disk, "overflow.txt").isEmpty(), "a DISK_FULL write must store nothing");
        helper.assertTrue(DiskFilesystem.write(disk, "/leading-slash.txt", FileType.TXT, "x", ROOM, KIND)
                == DiskFilesystem.WriteResult.INVALID_PATH, "a path starting with '/' must be refused as invalid");
        helper.assertTrue(survivorBody.equals(DiskFilesystem.read(disk, survivor).orElse(null)),
                "the refused writes must not corrupt an existing file");
        helper.succeed();
    }
}
