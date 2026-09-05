/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os;

/**
 * How an operating system gets onto a disk when its medium is booted from the firmware.
 */
public enum InstallMode {

    /** A guided installer: pick the disk, confirm, done (MC-DOS, the Frames editions, most distributions). */
    GUIDED,

    /**
     * A live root shell where the player performs the real installation steps by hand (partition, mount,
     * bootstrap the base system, generate fstab, chroot, bootloader, reboot). Arch-style.
     */
    LIVE_MANUAL,

    /** Like {@link #LIVE_MANUAL}, but the base system and kernel are compiled from source first. Gentoo-style. */
    SOURCE
}
