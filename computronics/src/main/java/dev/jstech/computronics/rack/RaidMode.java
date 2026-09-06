/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.rack;

import java.util.List;

/**
 * How a RAID Controller aggregates the drives of its bay into one logical volume. Pure arithmetic
 * over drive capacities and counts, with no Minecraft types, so the capacity, redundancy and
 * throughput rules are unit tested on their own.
 *
 * <p>Without a controller the bay's drives stay independent volumes; that is the {@link #NONE}
 * case, and it is what every bay does by default.
 */
public enum RaidMode {

    /** No array: each drive is its own volume, as on any other computer. */
    NONE(1, 0),
    /** Striping: full capacity and faster storage operations, but losing any drive loses it all. */
    RAID0(2, 0),
    /** Mirror: the smallest drive's capacity, surviving down to a single drive. */
    RAID1(2, Integer.MAX_VALUE),
    /** Parity: capacity of all but one drive, surviving exactly one loss; needs three drives. */
    RAID5(3, 1);

    /** The storage-throughput bonus of a healthy stripe, in percent. */
    public static final int STRIPE_THROUGHPUT_BONUS_PERCENT = 25;

    private final int minDrives;
    private final int lossesTolerated;

    RaidMode(final int minDrives, final int lossesTolerated) {
        this.minDrives = minDrives;
        this.lossesTolerated = lossesTolerated;
    }

    /** How many drives the mode needs before it forms an array at all. */
    public int minDrives() {
        return minDrives;
    }

    /**
     * How many drives the array can lose and still serve data. {@link #RAID1} mirrors every drive,
     * so it survives down to one — that is reported as {@code driveCount - 1} by
     * {@link #lossesTolerated(int)}; this raw value is the mode's own ceiling.
     */
    public int lossesTolerated(final int driveCount) {
        if (driveCount < minDrives) {
            return 0;
        }
        return Math.min(lossesTolerated, driveCount - 1);
    }

    /** Whether this mode forms an array over the given number of drives. */
    public boolean formsArray(final int driveCount) {
        return this != NONE && driveCount >= minDrives;
    }

    /**
     * The usable capacity of the array in items: the sum for a stripe, the smallest drive for a
     * mirror, and the sum minus one drive's worth for parity. Below the mode's minimum drive count
     * the array does not form and the capacity is zero — the caller falls back to plain volumes.
     */
    public long usableCapacity(final List<Long> driveCapacities) {
        return usableCapacity(driveCapacities, driveCapacities.size());
    }

    /**
     * The usable capacity of an array formed with {@code memberCount} drives, of which
     * {@code driveCapacities} are the members still present. A degraded array keeps the size it
     * promised — losing a member costs redundancy, not capacity — so the parity reserve is charged
     * against the member count the array was built with, not against what is left in the bay.
     */
    public long usableCapacity(final List<Long> driveCapacities, final int memberCount) {
        if (driveCapacities.isEmpty() || !formsArray(Math.max(memberCount, driveCapacities.size()))) {
            return 0L;
        }
        long sum = 0L;
        long smallest = Long.MAX_VALUE;
        for (final long capacity : driveCapacities) {
            sum += capacity;
            smallest = Math.min(smallest, capacity);
        }
        return switch (this) {
            case RAID0 -> sum;
            case RAID1 -> smallest;
            // Parity costs one drive's worth. Charging the smallest member (rather than subtracting
            // it from the raw sum) is what makes a mixed array safe: the survivors always have room
            // for the whole volume when one member is pulled, so a degraded array never has to drop
            // data it promised to hold. On the usual array of identical drives the two are equal.
            case RAID5 -> (Math.max(memberCount, driveCapacities.size()) - 1) * smallest;
            case NONE -> 0L;
        };
    }

    /** The storage-throughput multiplier in percent (100 = unchanged) for a healthy array. */
    public int throughputPercent() {
        return this == RAID0 ? 100 + STRIPE_THROUGHPUT_BONUS_PERCENT : 100;
    }

    /**
     * Whether an array that started with {@code originalDrives} members still serves data with
     * {@code presentDrives} of them left.
     */
    public boolean survives(final int originalDrives, final int presentDrives) {
        if (!formsArray(originalDrives)) {
            return false;
        }
        return originalDrives - presentDrives <= lossesTolerated(originalDrives);
    }

    /** Whether a member is missing and the mode can rebuild it once a replacement drive goes in. */
    public boolean rebuildable(final int originalDrives, final int presentDrives) {
        return presentDrives < originalDrives && survives(originalDrives, presentDrives)
                && this != RAID0;
    }
}
