/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.core.peripheral;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PeripheralCableTypeTest {

    @Test
    void computing_hasMaxLength16() {
        // Computing (peripheral) cable has a short range.
        assertEquals(16, PeripheralCableType.COMPUTING.maxLength());
        assertEquals("peripheral_cable",
                PeripheralCableType.COMPUTING.translationKey());
    }

    @Test
    void telemetry_hasMaxLength256() {
        // Telemetry cable has a long range for satellite control links.
        assertEquals(256, PeripheralCableType.TELEMETRY.maxLength());
        assertEquals("telemetry_cable",
                PeripheralCableType.TELEMETRY.translationKey());
    }

    @Test
    void industrialControl_hasMaxLength8() {
        // Industrial control cable has a very short range.
        assertEquals(8, PeripheralCableType.INDUSTRIAL_CONTROL.maxLength());
        assertEquals("industrial_peripheral_cable",
                PeripheralCableType.INDUSTRIAL_CONTROL.translationKey());
    }

    @Test
    void exactlyThreeTypes_existInTheMod() {
        // There are exactly three peripheral cable systems.
        assertEquals(3, PeripheralCableType.values().length);
    }
}
