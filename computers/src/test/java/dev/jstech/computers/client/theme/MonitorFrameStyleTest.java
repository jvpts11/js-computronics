/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.core.tier.HardwareEra;
import org.junit.jupiter.api.Test;

class MonitorFrameStyleTest {

    @Test
    void forEra_resolvesVintageLegacyAndDefaultsFutureErasToFlat() {
        assertSame(MonitorFrameStyle.CRT, MonitorFrameStyle.forEra(HardwareEra.VINTAGE));
        assertSame(MonitorFrameStyle.LCD, MonitorFrameStyle.forEra(HardwareEra.LEGACY));
        assertSame(MonitorFrameStyle.FLAT, MonitorFrameStyle.forEra(HardwareEra.STANDARD));
        // The future eras have no frame of their own yet and reuse the flat bezel.
        assertSame(MonitorFrameStyle.FLAT, MonitorFrameStyle.forEra(HardwareEra.ADVANCED));
        assertSame(MonitorFrameStyle.FLAT, MonitorFrameStyle.forEra(HardwareEra.EXA));
        assertSame(MonitorFrameStyle.FLAT, MonitorFrameStyle.forEra(HardwareEra.SINGULARITY));
    }

    @Test
    void border_sumsBezelAndInnerThickness() {
        assertEquals(MonitorFrameStyle.CRT.bezelThickness() + MonitorFrameStyle.CRT.innerThickness(),
                MonitorFrameStyle.CRT.border());
    }

    @Test
    void geometry_wrapsGlassByTheBorderOnEverySide() {
        final MonitorFrameStyle s = MonitorFrameStyle.CRT;
        final int sx = 20;
        final int sy = 10;
        final int sw = 100;
        final int sh = 50;
        final MonitorFrameStyle.Geometry geo = s.geometry(sx, sy, sw, sh);
        final int b = s.border();
        assertEquals(sx - b, geo.x());
        assertEquals(sy - b, geo.y());
        assertEquals(sx + sw + b, geo.right());
        // The bottom adds the chin strip on top of the bottom border.
        assertEquals(sy + sh + b + s.chinHeight(), geo.bottom());
    }

    @Test
    void geometry_placesTheChinBelowTheGlass() {
        final MonitorFrameStyle s = MonitorFrameStyle.CRT;
        final int sy = 10;
        final int sh = 50;
        final MonitorFrameStyle.Geometry geo = s.geometry(20, sy, 100, sh);
        assertTrue(geo.chinY() >= sy + sh, "chin must start at or below the glass bottom");
        assertTrue(geo.chinY() + s.chinHeight() <= geo.bottom(), "chin must fit within the shell");
    }
}
