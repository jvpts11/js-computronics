/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.os;

import dev.jstech.core.tier.HardwareEra;

/**
 * The firmware variant shown when a computer has no operating system installed.
 *
 * <p>Each hardware era ships a distinct firmware presentation: command-line for Vintage hardware,
 * the classic blue-panel BIOS for Legacy hardware, and a modern UEFI interface for Standard and
 * any later era.
 *
 * <p>This enum is pure (it depends only on {@link HardwareEra}, which is itself pure) so it
 * compiles and runs in the JUnit test sourceset without any Minecraft dependency.
 */
public enum FirmwareKind {
    /** Text-mode command-line BIOS. Used on Vintage-era hardware. */
    CLI_BIOS,
    /** Classic blue-panel visual BIOS. Used on Legacy-era hardware. */
    BLUE_BIOS,
    /** Modern UEFI interface. Used on Standard-era hardware and above. */
    UEFI;

    /**
     * Returns the firmware kind appropriate for the given hardware era.
     *
     * @param era the hardware era of the computer
     * @return {@link #CLI_BIOS} for {@link HardwareEra#VINTAGE}, {@link #BLUE_BIOS} for
     *         {@link HardwareEra#LEGACY}, and {@link #UEFI} for {@link HardwareEra#STANDARD} or any
     *         later era
     */
    public static FirmwareKind forEra(HardwareEra era) {
        return switch (era) {
            case VINTAGE -> CLI_BIOS;
            case LEGACY -> BLUE_BIOS;
            default -> UEFI;
        };
    }
}
