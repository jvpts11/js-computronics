/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.client.gui.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToastDataTest {

    @Test
    void of_titleOnly_hasNoDescription() {
        ToastData t = ToastData.of("toast.jsc.op_done", ToastData.Severity.SUCCESS);
        assertFalse(t.hasDescription());
        assertEquals("", t.descriptionKey());
    }

    @Test
    void withDescription_hasDescription() {
        ToastData t = new ToastData(
                "toast.jsc.attack", "toast.jsc.attack.desc", ToastData.Severity.CRITICAL);
        assertTrue(t.hasDescription());
    }

    @Test
    void blankTitle_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> ToastData.of("   ", ToastData.Severity.INFO));
    }

    @Test
    void nullSeverity_throws() {
        assertThrows(NullPointerException.class,
                () -> new ToastData("t", "", null));
    }

    @Test
    void duration_scalesWithSeverity() {
        assertTrue(ToastData.of("t", ToastData.Severity.CRITICAL).defaultDurationMillis()
                > ToastData.of("t", ToastData.Severity.INFO).defaultDurationMillis());
    }

    @Test
    void accentColor_distinctPerSeverity() {
        int info = ToastData.of("t", ToastData.Severity.INFO).accentColor();
        int success = ToastData.of("t", ToastData.Severity.SUCCESS).accentColor();
        int warning = ToastData.of("t", ToastData.Severity.WARNING).accentColor();
        int critical = ToastData.of("t", ToastData.Severity.CRITICAL).accentColor();
        // All four should differ.
        assertEquals(4, java.util.Set.of(info, success, warning, critical).size());
    }

    @Test
    void allSeverities_haveColorAndDuration() {
        for (ToastData.Severity s : ToastData.Severity.values()) {
            ToastData t = ToastData.of("t", s);
            assertTrue(t.defaultDurationMillis() > 0);
            // accentColor is opaque (alpha 0xFF).
            assertEquals(0xFF, (t.accentColor() >>> 24) & 0xFF);
        }
    }
}