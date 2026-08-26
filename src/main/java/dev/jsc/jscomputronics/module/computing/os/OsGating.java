/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os;

import dev.jsc.jscomputronics.common.tier.HardwareEra;

/**
 * Pure gating logic for OS installation and program execution.
 *
 * <p>All methods are stateless and operate only on ordinal comparisons between pure enums, so this
 * class compiles and runs in the JUnit test sourceset without any Minecraft dependency.
 *
 * <p>Era gating follows the minimum-era / backward-compatibility rule: an OS whose minimum era is X
 * installs on hardware at era X or any later era. Installing a newer-era OS on older hardware is
 * rejected. Program compat follows the same principle: a program whose minimum OS era is Y runs on
 * an OS whose era is Y or higher, provided the OS also satisfies the program's minimum capability
 * tier.
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
     * Returns {@code true} when an OS with the given capability and era can execute a program that
     * declares the given minimum capability and minimum OS era.
     *
     * <p>Both conditions must hold simultaneously: the OS capability tier must be at least
     * {@code progMinCap}, and the OS era must be at least {@code progMinOsEra}.
     *
     * @param osCap       the capability tier of the running OS
     * @param osEra       the hardware era of the OS (its {@code minEra})
     * @param progMinCap  the minimum capability tier required by the program
     * @param progMinOsEra the minimum OS era required by the program
     * @return {@code true} iff both the capability and era requirements are satisfied
     */
    public static boolean canRun(OsCapability osCap, HardwareEra osEra,
                                  OsCapability progMinCap, HardwareEra progMinOsEra) {
        return osCap.ordinal() >= progMinCap.ordinal()
                && osEra.ordinal() >= progMinOsEra.ordinal();
    }
}
