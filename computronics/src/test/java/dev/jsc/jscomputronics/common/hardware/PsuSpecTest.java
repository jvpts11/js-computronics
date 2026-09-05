/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.common.hardware;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PsuSpecTest {

    @Test
    void autoScaling_defaultsFalse() {
        final PsuSpec psu = new PsuSpec(650, 90);
        assertFalse(psu.autoScaling(), "the 2-arg convenience constructor builds a conventional PSU");
    }

    @Test
    void autoScaling_trueViaFullConstructor() {
        final PsuSpec psu = new PsuSpec(1, 100, true);
        assertTrue(psu.autoScaling());
    }

    @Test
    void constructor_rejectsNonPositiveWattage() {
        assertThrows(IllegalArgumentException.class, () -> new PsuSpec(0, 90));
    }

    @Test
    void constructor_rejectsEfficiencyOutOfRange() {
        assertThrows(IllegalArgumentException.class, () -> new PsuSpec(650, 0));
        assertThrows(IllegalArgumentException.class, () -> new PsuSpec(650, 101));
    }
}
