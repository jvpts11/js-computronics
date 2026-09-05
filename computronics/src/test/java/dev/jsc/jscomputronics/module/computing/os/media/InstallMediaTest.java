/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os.media;

import dev.jstech.core.tier.HardwareEra;
import dev.jsc.jscomputronics.module.computing.os.ProgramKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public final class InstallMediaTest {

    @Test
    public void forSystem_followsTheEra() {
        assertEquals(MediaFormat.FLOPPY, InstallMedia.forSystem(HardwareEra.VINTAGE));
        assertEquals(MediaFormat.CD, InstallMedia.forSystem(HardwareEra.LEGACY));
        assertEquals(MediaFormat.USB, InstallMedia.forSystem(HardwareEra.STANDARD));
    }

    @Test
    public void forSystem_laterErasStayOnTheStick() {
        for (final HardwareEra era : HardwareEra.values()) {
            if (era.compareTo(HardwareEra.STANDARD) >= 0) {
                assertEquals(MediaFormat.USB, InstallMedia.forSystem(era), era.name());
            }
        }
    }

    @Test
    public void forProgram_vintageAndLegacyIgnoreTheKind() {
        for (final ProgramKind kind : ProgramKind.values()) {
            assertEquals(MediaFormat.FLOPPY, InstallMedia.forProgram(HardwareEra.VINTAGE, kind), kind.name());
            assertEquals(MediaFormat.CD, InstallMedia.forProgram(HardwareEra.LEGACY, kind), kind.name());
        }
    }

    @Test
    public void forProgram_standardSplitsServicesFromApplications() {
        assertEquals(MediaFormat.USB, InstallMedia.forProgram(HardwareEra.STANDARD, ProgramKind.SERVICE));
        assertEquals(MediaFormat.DVD, InstallMedia.forProgram(HardwareEra.STANDARD, ProgramKind.APP));
        assertEquals(MediaFormat.DVD, InstallMedia.forProgram(HardwareEra.STANDARD, ProgramKind.DESKTOP_ENVIRONMENT));
    }

    @Test
    public void readerName_namesEveryFormat() {
        for (final MediaFormat format : MediaFormat.values()) {
            assertEquals(false, InstallMedia.readerName(format).isBlank(), format.name());
        }
        assertEquals("Dock Station", InstallMedia.readerName(MediaFormat.USB));
    }
}
