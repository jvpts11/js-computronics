/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os;

import dev.jsc.jscomputronics.common.tier.HardwareEra;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsGatingTest {

    // -------------------------------------------------------------------------
    // canInstall — era gating
    // -------------------------------------------------------------------------

    @Test
    void canInstall_rejectsNewerEraOsOnOlderHardware() {
        // A Legacy-minimum OS must not install on Vintage hardware.
        assertFalse(OsGating.canInstall(HardwareEra.LEGACY, HardwareEra.VINTAGE));
    }

    @Test
    void canInstall_acceptsNewerEraOsOnEqualOrNewerHardware() {
        // A Legacy-minimum OS installs fine on Standard hardware.
        assertTrue(OsGating.canInstall(HardwareEra.LEGACY, HardwareEra.STANDARD));
    }

    @Test
    void canInstall_acceptsVintageMinOsOnVintageHardware() {
        // Vintage-minimum OS installs on Vintage hardware (equal era is accepted).
        assertTrue(OsGating.canInstall(HardwareEra.VINTAGE, HardwareEra.VINTAGE));
    }

    @Test
    void canInstall_acceptsVintageMinOsOnAnyLaterEra() {
        // Vintage-minimum OS installs on every later era too.
        for (HardwareEra hw : HardwareEra.values()) {
            assertTrue(OsGating.canInstall(HardwareEra.VINTAGE, hw),
                    "Expected canInstall(VINTAGE, " + hw + ") to be true");
        }
    }

    @Test
    void canInstall_rejectsSingularityMinOsOnEverythingBelow() {
        // Singularity-minimum OS should be rejected on all hardware below Singularity.
        assertFalse(OsGating.canInstall(HardwareEra.SINGULARITY, HardwareEra.VINTAGE));
        assertFalse(OsGating.canInstall(HardwareEra.SINGULARITY, HardwareEra.LEGACY));
        assertFalse(OsGating.canInstall(HardwareEra.SINGULARITY, HardwareEra.STANDARD));
        assertFalse(OsGating.canInstall(HardwareEra.SINGULARITY, HardwareEra.ADVANCED));
        assertFalse(OsGating.canInstall(HardwareEra.SINGULARITY, HardwareEra.EXA));
    }

    // -------------------------------------------------------------------------
    // canRun — capability + era gating
    // -------------------------------------------------------------------------

    @Test
    void canRun_rejectsFullDesktopProgramOnNetworkGuiOs() {
        // An OS with NETWORK_GUI capability cannot run an APP that requires FULL_DESKTOP.
        assertFalse(OsGating.canRun(
                OsCapability.NETWORK_GUI, HardwareEra.VINTAGE,
                OsCapability.FULL_DESKTOP, HardwareEra.VINTAGE));
    }

    @Test
    void canRun_acceptsFullDesktopProgramOnFullDesktopOs() {
        // An OS with FULL_DESKTOP capability can run a FULL_DESKTOP-requiring program.
        assertTrue(OsGating.canRun(
                OsCapability.FULL_DESKTOP, HardwareEra.STANDARD,
                OsCapability.FULL_DESKTOP, HardwareEra.STANDARD));
    }

    @Test
    void canRun_rejectsWhenOsEraIsBelowProgramMinOsEra() {
        // Even if capability is sufficient, a program requiring a newer OS era is rejected.
        assertFalse(OsGating.canRun(
                OsCapability.FULL_DESKTOP, HardwareEra.VINTAGE,
                OsCapability.TERMINAL_ONLY, HardwareEra.LEGACY));
    }

    @Test
    void canRun_acceptsTerminalProgramOnHigherCapabilityOs() {
        // An OS with FULL_DESKTOP capability can run a TERMINAL_ONLY-requiring program.
        assertTrue(OsGating.canRun(
                OsCapability.FULL_DESKTOP, HardwareEra.STANDARD,
                OsCapability.TERMINAL_ONLY, HardwareEra.VINTAGE));
    }

    @Test
    void canRun_acceptsServiceProgramOnMinimalOs() {
        // A SERVICE program with TERMINAL_ONLY / VINTAGE min runs on any OS.
        assertTrue(OsGating.canRun(
                OsCapability.TERMINAL_ONLY, HardwareEra.VINTAGE,
                OsCapability.TERMINAL_ONLY, HardwareEra.VINTAGE));
    }

    // -------------------------------------------------------------------------
    // FirmwareKind.forEra
    // -------------------------------------------------------------------------

    @Test
    void firmwareKind_forEra_vintageIsCliBios() {
        assertEquals(FirmwareKind.CLI_BIOS, FirmwareKind.forEra(HardwareEra.VINTAGE));
    }

    @Test
    void firmwareKind_forEra_legacyIsBlueBios() {
        assertEquals(FirmwareKind.BLUE_BIOS, FirmwareKind.forEra(HardwareEra.LEGACY));
    }

    @Test
    void firmwareKind_forEra_standardIsUefi() {
        assertEquals(FirmwareKind.UEFI, FirmwareKind.forEra(HardwareEra.STANDARD));
    }

    @Test
    void firmwareKind_forEra_allErasAboveStandardAreUefi() {
        // Advanced, Exa, and Singularity all map to UEFI.
        assertEquals(FirmwareKind.UEFI, FirmwareKind.forEra(HardwareEra.ADVANCED));
        assertEquals(FirmwareKind.UEFI, FirmwareKind.forEra(HardwareEra.EXA));
        assertEquals(FirmwareKind.UEFI, FirmwareKind.forEra(HardwareEra.SINGULARITY));
    }
}
