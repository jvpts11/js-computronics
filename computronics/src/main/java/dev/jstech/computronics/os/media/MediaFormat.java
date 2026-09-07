/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os.media;

import dev.jstech.core.tier.HardwareEra;

/**
 * The physical format of a medium, independent of the {@link MediaKind content} it carries.
 *
 * <p>The format decides how much the medium can hold and which {@link MediaDriveType drive} can read
 * it. A medium's content (OS installer, program installer, or a data snapshot) is orthogonal: a
 * floppy can hold an OS installer, a CD can hold a data snapshot, and so on.
 *
 * <p>This enum is free of Minecraft / NeoForge imports so it stays usable from pure-JUnit tests.
 * Capacities are in item-equivalents (1 item = 4 MB) and are tunable balance numbers, not final.
 */
public enum MediaFormat {

    /** 3.5" floppy disk, the smallest, earliest medium. Read/write. */
    FLOPPY(1_024),

    /** CD, the Legacy-era optical medium. 700 MB class. */
    CD(8_192),

    /** DVD, the Standard-era optical medium. 4.7 GB class. */
    DVD(65_536),

    /** USB flash drive, the highest-capacity removable medium. */
    USB(262_144);

    private final int capacityItems;

    MediaFormat(final int capacityItems) {
        this.capacityItems = capacityItems;
    }

    /**
     * Returns this format's capacity in item-equivalents (1 item = 4 MB). Bounds how large a data
     * snapshot, OS image, or program a medium of this format can carry.
     */
    public int capacityItems() {
        return capacityItems;
    }

    /** The hardware era the format belongs to, which decides what a file's bytes weigh on it. */
    public HardwareEra era() {
        return switch (this) {
            case FLOPPY -> HardwareEra.VINTAGE;
            case CD -> HardwareEra.LEGACY;
            case DVD, USB -> HardwareEra.STANDARD;
        };
    }
}
