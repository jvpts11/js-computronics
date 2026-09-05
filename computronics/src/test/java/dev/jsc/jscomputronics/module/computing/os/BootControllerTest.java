/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.os;

import dev.jsc.jscomputronics.module.computing.os.boot.BootController;
import dev.jsc.jscomputronics.module.computing.os.boot.BootController.BootTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pure unit tests for {@link BootController#targetFor(boolean, OsCapability)}.
 * No Minecraft or NeoForge imports are allowed here.
 */
class BootControllerTest {

    @Test
    void targetFor_noOsReturnsFirmware() {
        assertEquals(BootTarget.FIRMWARE, BootController.targetFor(false, (OsCapability) null));
    }

    @Test
    void targetFor_hasOsFalseWithNonNullCapabilityStillReturnsFirmware() {
        // hasOs=false takes priority over whatever capability is passed.
        assertEquals(BootTarget.FIRMWARE, BootController.targetFor(false, OsCapability.NETWORK_GUI));
    }

    @Test
    void targetFor_terminalOnlyOsReturnsTerminalOnly() {
        assertEquals(BootTarget.TERMINAL_ONLY, BootController.targetFor(true, OsCapability.TERMINAL_ONLY));
    }

    @Test
    void targetFor_networkGuiOsReturnsNetworkGui() {
        assertEquals(BootTarget.NETWORK_GUI, BootController.targetFor(true, OsCapability.NETWORK_GUI));
    }

    @Test
    void targetFor_fullDesktopOsReturnsFullDesktop() {
        assertEquals(BootTarget.FULL_DESKTOP, BootController.targetFor(true, OsCapability.FULL_DESKTOP));
    }
}
