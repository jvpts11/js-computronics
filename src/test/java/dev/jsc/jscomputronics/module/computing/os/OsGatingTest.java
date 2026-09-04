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

import java.util.Set;

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
    // canRunProgram / canInstallProgram — platform + hardware gating
    // -------------------------------------------------------------------------

    @Test
    void canRunProgram_rejectsProgramOnUnsupportedPlatform() {
        // A Frames-only program is refused on the MC-DOS platform, however powerful the hardware.
        assertFalse(OsGating.canRunProgram(Platform.MC_DOS, Set.of(Platform.FRAMES), 9999, 9999, 0, 0));
    }

    @Test
    void canRunProgram_acceptsProgramOnSupportedPlatform() {
        assertTrue(OsGating.canRunProgram(Platform.FRAMES, Set.of(Platform.FRAMES), 9999, 9999, 0, 0));
    }

    @Test
    void canRunProgram_rejectsWhenCpuBelowMinimum() {
        assertFalse(OsGating.canRunProgram(Platform.FRAMES, Set.of(Platform.FRAMES), 1000, 512, 2000, 0));
    }

    @Test
    void canRunProgram_rejectsWhenVramBelowMinimum() {
        assertFalse(OsGating.canRunProgram(Platform.FRAMES, Set.of(Platform.FRAMES), 3000, 128, 0, 256));
    }

    @Test
    void canRunProgram_acceptsWhenPlatformAndHardwareMeetMinimums() {
        assertTrue(OsGating.canRunProgram(Platform.FRAMES, Set.of(Platform.FRAMES), 3000, 512, 2000, 256));
    }

    @Test
    void canRunProgram_acceptsMultiPlatformServiceOnAnyMember() {
        final Set<Platform> all = Set.of(Platform.MC_DOS, Platform.MC_NET, Platform.FRAMES);
        assertTrue(OsGating.canRunProgram(Platform.MC_DOS, all, 1, 0, 0, 0));
        assertTrue(OsGating.canRunProgram(Platform.FRAMES, all, 1, 0, 0, 0));
    }

    @Test
    void canInstallProgram_rejectsWhenFreeDiskBelowFootprint() {
        // Run requirements met, but not enough free disk for the footprint.
        assertFalse(OsGating.canInstallProgram(Platform.FRAMES, Set.of(Platform.FRAMES),
                3000, 512, 100L, 0, 0, 256));
    }

    @Test
    void canInstallProgram_acceptsWhenFreeDiskMeetsFootprint() {
        assertTrue(OsGating.canInstallProgram(Platform.FRAMES, Set.of(Platform.FRAMES),
                3000, 512, 1024L, 0, 0, 256));
    }

    @Test
    void canInstallProgram_rejectsOnUnsupportedPlatformEvenWithAmpleDisk() {
        assertFalse(OsGating.canInstallProgram(Platform.MC_DOS, Set.of(Platform.FRAMES),
                9999, 9999, 100000L, 0, 0, 256));
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
