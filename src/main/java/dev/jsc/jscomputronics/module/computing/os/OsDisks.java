/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.item.DiskItem;
import dev.jsc.jscomputronics.module.computing.os.fs.DiskFilesystem;
import dev.jsc.jscomputronics.module.computing.os.fs.FilesystemContents;
import dev.jsc.jscomputronics.module.computing.os.fs.SystemLayout;
import dev.jsc.jscomputronics.module.computing.storage.DriveVolumes;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ObjIntConsumer;

/**
 * The disk-set logic every OS-hosting machine shares: which disk boots, where an install lands,
 * whether a footprint fits, and what a format erases. Extracted so a desk computer (disks in its
 * hardware inventory) and a rack server (drives in the rack's front-panel bays) run the exact same
 * rules over different physical slots — the callers supply slot accessors, this class supplies the
 * behavior.
 */
public final class OsDisks {

    private OsDisks() {
    }

    /** Whether the stack is a disk stamped with a registered operating system. */
    public static boolean hasSystem(final ItemStack disk) {
        final ResourceLocation osId = disk.get(ComputingModule.SYSTEM_OS.get());
        return osId != null && OsRegistry.getOs(osId) != null;
    }

    /**
     * The disk that boots: the preferred slot when it holds a system, else the first disk with a
     * system, else EMPTY — so a machine with two installed OSes dual-boots by choice.
     */
    public static ItemStack systemDisk(final int diskCount, final IntFunction<ItemStack> diskInSlot,
                                       final int preferredSlot) {
        if (preferredSlot >= 0 && preferredSlot < diskCount) {
            final ItemStack preferred = diskInSlot.apply(preferredSlot);
            if (preferred.getItem() instanceof DiskItem && hasSystem(preferred)) {
                return preferred;
            }
        }
        for (int i = 0; i < diskCount; i++) {
            final ItemStack stack = diskInSlot.apply(i);
            if (stack.getItem() instanceof DiskItem && hasSystem(stack)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * The slot the firmware installs onto by default: the first disk without a system (a second OS
     * lands beside the first for dual boot), else the first disk; {@code -1} with no disk at all.
     */
    public static int defaultInstallSlot(final int diskCount, final IntFunction<ItemStack> diskInSlot) {
        int firstDisk = -1;
        for (int i = 0; i < diskCount; i++) {
            final ItemStack stack = diskInSlot.apply(i);
            if (!(stack.getItem() instanceof DiskItem)) {
                continue;
            }
            if (!hasSystem(stack)) {
                return i;
            }
            if (firstDisk == -1) {
                firstDisk = i;
            }
        }
        return firstDisk;
    }

    /**
     * Installs {@code osId} onto {@code preferredSlot}, or ({@code -1}) onto the default target: a
     * slot already carrying this OS (an idempotent re-install), else the first disk without a
     * system, else the first disk. The OS footprint must fit the chosen disk's free weight
     * (capacity − stored items − files). On success the stamped disk is written back through
     * {@code setDiskInSlot} so the owner's change hooks fire.
     */
    public static boolean installOs(final int diskCount, final IntFunction<ItemStack> diskInSlot,
                                    final ObjIntConsumer<ItemStack> setDiskInSlot,
                                    final ResourceLocation osId, final int preferredSlot) {
        final OsDef def = OsRegistry.getOs(osId);
        if (def == null) {
            return false;
        }
        int targetSlot = -1;
        if (preferredSlot >= 0 && preferredSlot < diskCount
                && diskInSlot.apply(preferredSlot).getItem() instanceof DiskItem) {
            targetSlot = preferredSlot;
        } else {
            for (int i = 0; i < diskCount; i++) {
                final ItemStack stack = diskInSlot.apply(i);
                if (!(stack.getItem() instanceof DiskItem)) {
                    continue;
                }
                if (osId.equals(stack.get(ComputingModule.SYSTEM_OS.get()))) {
                    // Already stamped with this OS; treat as re-install: success with no mutation.
                    return true;
                }
            }
            targetSlot = defaultInstallSlot(diskCount, diskInSlot);
        }
        if (targetSlot == -1) {
            return false; // no disk installed
        }
        final ItemStack disk = diskInSlot.apply(targetSlot);
        if (osId.equals(disk.get(ComputingModule.SYSTEM_OS.get()))) {
            return true; // re-install onto the same disk: nothing to do
        }
        final DiskItem target = (DiskItem) disk.getItem();
        final long diskCapacityItems = target.spec().capacityItems();
        final long storageUsedWeight = DriveVolumes.usedWeight(disk);
        final long fsUsedWeight = DiskFilesystem.filesWeight(disk);
        // Free weight in mB-eq; the system's size in megabytes costs items at the disk's own era.
        final long freeWeight =
                diskCapacityItems * StorageKey.MB_EQ_PER_ITEM - storageUsedWeight - fsUsedWeight;
        if (def.footprintItemsOn(target.spec().era()) * StorageKey.MB_EQ_PER_ITEM > freeWeight) {
            return false;
        }
        final ItemStack updated = disk.copy();
        updated.set(ComputingModule.SYSTEM_OS.get(), osId);
        // A graphical desktop OS lays down the Windows-like system folder skeleton on first install
        // (Program Files, Windows, Users\Public\Desktop, ...). Terminal/network OSes get nothing.
        final List<String> systemDirs = SystemLayout.directoriesFor(def, OsRegistry.getKernel(def.kernelId()));
        if (!systemDirs.isEmpty()) {
            FilesystemContents fs = updated.getOrDefault(
                    ComputingModule.FILESYSTEM.get(), FilesystemContents.EMPTY);
            for (final String d : systemDirs) {
                fs = fs.withDir(d);
            }
            updated.set(ComputingModule.FILESYSTEM.get(), fs);
        }
        setDiskInSlot.accept(updated, targetSlot);
        return true;
    }

    /**
     * Formats the disk in {@code slot}: erases the installed system, every file, the item storage
     * and the privacy split, leaving a blank disk written back through {@code setDiskInSlot}.
     * Returns whether the erased disk carried a system (so the caller can wipe its software state).
     */
    public static FormatResult formatDisk(final int diskCount, final IntFunction<ItemStack> diskInSlot,
                                          final ObjIntConsumer<ItemStack> setDiskInSlot, final int slot) {
        if (slot < 0 || slot >= diskCount) {
            return FormatResult.NOTHING;
        }
        final ItemStack disk = diskInSlot.apply(slot);
        if (!(disk.getItem() instanceof DiskItem)) {
            return FormatResult.NOTHING;
        }
        final boolean hadSystem = hasSystem(disk);
        final ItemStack updated = disk.copy();
        updated.remove(ComputingModule.SYSTEM_OS.get());
        updated.remove(ComputingModule.FILESYSTEM.get());
        DriveVolumes.erase(updated);
        updated.remove(ComputingModule.DISK_PUBLIC_PERMILLE.get());
        setDiskInSlot.accept(updated, slot);
        return hadSystem ? FormatResult.ERASED_SYSTEM : FormatResult.ERASED_DATA;
    }

    /**
     * The desktop environment a machine boots into: the OS's bundled one (the Frames editions), else
     * the first desktop-environment package installed on it (a Linux distribution after its desktop
     * package went in), else null (a TTY-only or network OS).
     */
    @org.jetbrains.annotations.Nullable
    public static ResourceLocation installedDesktopId(
            @org.jetbrains.annotations.Nullable final OsDef os,
            @org.jetbrains.annotations.Nullable
            final dev.jsc.jscomputronics.module.computing.program.ComputerConsoleState console) {
        if (os == null) {
            return null;
        }
        if (os.bundledDesktop().isPresent()) {
            return os.bundledDesktop().get();
        }
        if (console == null) {
            return null;
        }
        for (final String id : console.installed()) {
            final ResourceLocation rl = ResourceLocation.tryParse(id);
            final ProgramSpec spec = rl == null ? null : OsRegistry.getProgram(rl);
            if (spec != null && spec.kind() == ProgramKind.DESKTOP_ENVIRONMENT
                    && OsRegistry.getDesktop(rl) != null) {
                return rl;
            }
        }
        return null;
    }

    /**
     * Free weight in mB-equivalents available on a system disk for user files: the disk capacity
     * minus the stored items, the existing files, and the installed OS footprint.
     */
    public static long systemDiskFreeWeight(final ItemStack disk) {
        if (!(disk.getItem() instanceof DiskItem diskItem)) {
            return 0L;
        }
        final long capacity = diskItem.spec().capacityItems() * StorageKey.MB_EQ_PER_ITEM;
        final long storageUsed = DriveVolumes.usedWeight(disk);
        final long fsUsed = DiskFilesystem.filesWeight(disk);
        final ResourceLocation osId = disk.get(ComputingModule.SYSTEM_OS.get());
        final OsDef os = osId != null ? OsRegistry.getOs(osId) : null;
        final long osReserved = os != null
                ? os.footprintItemsOn(diskItem.spec().era()) * StorageKey.MB_EQ_PER_ITEM : 0L;
        return Math.max(0L, capacity - storageUsed - fsUsed - osReserved);
    }

    /** What a format actually erased. */
    public enum FormatResult {
        /** No disk in the slot; nothing changed. */
        NOTHING,
        /** A data disk was blanked. */
        ERASED_DATA,
        /** The blanked disk carried a system, so the machine's software state died with it. */
        ERASED_SYSTEM;

        public boolean formatted() {
            return this != NOTHING;
        }
    }
}
