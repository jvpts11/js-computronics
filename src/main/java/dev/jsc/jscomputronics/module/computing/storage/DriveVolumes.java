/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.storage;

import dev.jsc.jscomputronics.module.computing.ComputingModule;
import dev.jsc.jscomputronics.module.computing.item.DiskItem;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.UUID;

/**
 * How a drive item reaches its stored items. The item carries a volume id and a usage summary; the
 * contents live in {@link StorageVolumes}. Writers take the live volume through {@link #of} and refresh
 * the summary afterwards; readers that only need a size use the summary, which is valid on both sides.
 */
public final class DriveVolumes {

    private DriveVolumes() {
    }

    /**
     * The live volume behind a drive, created and stamped onto the drive on first use. A blank read-only
     * volume off the server thread, or for a stack that is not a drive.
     */
    public static StorageVolume of(final ItemStack drive) {
        if (!(drive.getItem() instanceof DiskItem)) {
            return StorageVolume.EMPTY;
        }
        final StorageVolumes store = StorageVolumes.current();
        if (store == null) {
            return StorageVolume.EMPTY;
        }
        UUID id = drive.get(ComputingModule.DISK_VOLUME.get());
        if (id == null) {
            id = UUID.randomUUID();
            drive.set(ComputingModule.DISK_VOLUME.get(), id);
        }
        return store.volume(id);
    }

    /** The live volume behind a drive when it already has one, else the blank read-only volume. */
    public static StorageVolume peek(final ItemStack drive) {
        final UUID id = drive.get(ComputingModule.DISK_VOLUME.get());
        if (id == null) {
            return StorageVolume.EMPTY;
        }
        final StorageVolumes store = StorageVolumes.current();
        final StorageVolume volume = store == null ? null : store.find(id);
        return volume == null ? StorageVolume.EMPTY : volume;
    }

    /** An immutable copy of a drive's contents; empty when it has none or this is not the server. */
    public static ServerStorageContents contents(final ItemStack drive) {
        return peek(drive).snapshot();
    }

    /** The drive's usage summary, valid wherever the item is. */
    public static DiskUsage usage(final ItemStack drive) {
        return drive.getOrDefault(ComputingModule.DISK_USAGE.get(), DiskUsage.EMPTY);
    }

    /** The stored data weight, from the summary. */
    public static long usedWeight(final ItemStack drive) {
        return usage(drive).usedWeight();
    }

    /** Rewrites the drive's summary from its volume; every write to the volume ends with this. */
    public static void refreshUsage(final ItemStack drive, final StorageVolume volume) {
        if (volume.isEmpty()) {
            drive.remove(ComputingModule.DISK_USAGE.get());
        } else {
            drive.set(ComputingModule.DISK_USAGE.get(), DiskUsage.of(volume));
        }
    }

    /** Replaces a drive's contents outright and refreshes its summary. */
    public static void write(final ItemStack drive, final Map<StorageKey, Long> contents) {
        final StorageVolume volume = of(drive);
        volume.replaceAll(contents);
        refreshUsage(drive, volume);
    }

    /** Wipes a drive's stored items: the volume is dropped from the store and the drive forgets it. */
    public static void erase(final ItemStack drive) {
        final UUID id = drive.get(ComputingModule.DISK_VOLUME.get());
        if (id != null) {
            final StorageVolumes store = StorageVolumes.current();
            if (store != null) {
                store.remove(id);
            }
        }
        drive.remove(ComputingModule.DISK_VOLUME.get());
        drive.remove(ComputingModule.DISK_USAGE.get());
    }
}
