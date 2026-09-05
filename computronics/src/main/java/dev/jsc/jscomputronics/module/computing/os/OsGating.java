/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os;

import dev.jstech.core.tier.HardwareEra;

import java.util.Set;

/**
 * Pure gating logic for OS installation and program execution.
 *
 * <p>All methods are stateless and operate only on plain enums and integers, so this class compiles
 * and runs in the JUnit test sourceset without any Minecraft dependency.
 *
 * <p>Two independent axes gate the system. <b>OS-onto-hardware</b> install uses the minimum-era rule:
 * an OS whose minimum era is X installs on hardware at era X or later ({@link #canInstall}).
 * <b>Program</b> gating uses the OS <em>platform</em> plus raw hardware minimums, not the era: a
 * program declares the platforms it supports and its minimum CPU clock, VRAM, and (for install) disk
 * footprint; it runs on an OS whose platform it supports and whose computer meets those hardware
 * minimums ({@link #canRunProgram}, {@link #canInstallProgram}).
 */
public final class OsGating {

    private OsGating() {}

    /**
     * Returns {@code true} when the given hardware is capable of running an OS whose minimum era is
     * {@code osMinEra}.
     *
     * <p>A hardware era satisfies the requirement when its ordinal is greater than or equal to the
     * OS minimum era ordinal (newer or equal is accepted; older is rejected).
     *
     * @param osMinEra the minimum hardware era declared by the OS
     * @param hardware the era of the computer's hardware
     * @return {@code true} iff the hardware era is at least {@code osMinEra}
     */
    public static boolean canInstall(HardwareEra osMinEra, HardwareEra hardware) {
        return hardware.ordinal() >= osMinEra.ordinal();
    }

    /**
     * Returns {@code true} when a program may <em>run</em> on the given OS and computer: the OS
     * platform is one the program supports, and the computer meets the program's minimum CPU clock
     * and VRAM. Disk space is not re-checked at run time (it was checked at install).
     *
     * @param osPlatform    the platform of the running OS
     * @param progPlatforms the platforms the program supports
     * @param cpuMhz        the computer's best CPU clock in MHz (max across installed CPUs)
     * @param vramMb        the computer's total VRAM in MB (sum across installed GPUs)
     * @param minCpuMhz     the minimum CPU clock the program requires
     * @param minVramMb     the minimum VRAM the program requires
     * @return {@code true} iff the platform and hardware minimums are satisfied
     */
    public static boolean canRunProgram(Platform osPlatform, Set<Platform> progPlatforms,
                                        int cpuMhz, int vramMb, int minCpuMhz, int minVramMb) {
        return progPlatforms.contains(osPlatform)
                && cpuMhz >= minCpuMhz
                && vramMb >= minVramMb;
    }

    /**
     * Returns {@code true} when a program may be <em>installed</em> on the given OS and computer: it
     * satisfies {@link #canRunProgram} <em>and</em> the system disk has at least the program's disk
     * footprint free.
     *
     * @param osPlatform    the platform of the running OS
     * @param progPlatforms the platforms the program supports
     * @param cpuMhz        the computer's best CPU clock in MHz
     * @param vramMb        the computer's total VRAM in MB
     * @param freeDiskMb    the free space on the system disk in MB
     * @param minCpuMhz     the minimum CPU clock the program requires
     * @param minVramMb     the minimum VRAM the program requires
     * @param minDiskMb     the disk footprint the program needs free to install
     * @return {@code true} iff the run requirements and the free-disk requirement are satisfied
     */
    public static boolean canInstallProgram(Platform osPlatform, Set<Platform> progPlatforms,
                                            int cpuMhz, int vramMb, long freeDiskMb,
                                            int minCpuMhz, int minVramMb, int minDiskMb) {
        return canRunProgram(osPlatform, progPlatforms, cpuMhz, vramMb, minCpuMhz, minVramMb)
                && freeDiskMb >= minDiskMb;
    }
}
