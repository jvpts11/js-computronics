/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jstech.core.tier.HardwareEra;
import dev.jsc.jscomputronics.module.computing.client.theme.MonitorFrameStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the physical monitor frame a software screen is shown inside, in the host computer's hardware-era style
 * (a cream CRT, a grey LCD, a thin flat bezel). The frame wraps the glass rectangle the software draws into: a
 * shell on every side, a dark inner frame for the CRT, and a chin below the glass with a model label, vent slots,
 * and a power LED.
 *
 * <p>The body is drawn with {@link #renderBody} from a screen's {@code renderBg} (absolute coordinates), before the
 * software window paints over the glass. Geometry and palette come from the pure {@link MonitorFrameStyle}. The
 * structure is all square fills; the CRT glass overlays (scanlines, phosphor glare) and the LED glow are textured
 * details added on top in a later pass.
 */
public final class MonitorFrame {

    private MonitorFrame() {
    }

    /** Glyph height of the vanilla font, used to vertically center the small chin label. */
    private static final int GLYPH_H = 7;

    private static final float LABEL_SCALE = 0.75f;

    /**
     * Draws the era's monitor body around the glass rectangle {@code (sx, sy)} sized {@code sw x sh}, in absolute
     * screen coordinates. Call from {@code renderBg} before drawing the software window, which paints over the glass.
     */
    public static void renderBody(final GuiGraphics g, final int sx, final int sy, final int sw, final int sh,
                                  final HardwareEra era, final Font font) {
        final MonitorFrameStyle s = MonitorFrameStyle.forEra(era == null ? HardwareEra.STANDARD : era);
        final MonitorFrameStyle.Geometry geo = s.geometry(sx, sy, sw, sh);

        // The plastic shell. The software window covers the glass area afterwards, so filling it here is harmless.
        g.fill(geo.x(), geo.y(), geo.right(), geo.bottom(), s.bezelColor());
        if (s.bezelHighlight() != 0) {
            g.fill(geo.x(), geo.y(), geo.right(), geo.y() + 1, s.bezelHighlight());
        }
        if (s.bezelShadow() != 0) {
            g.fill(geo.x(), geo.bottom() - 1, geo.right(), geo.bottom(), s.bezelShadow());
        }

        // The dark inner frame around the glass (CRT only). The window then covers the glass, leaving a dark ring.
        if (s.innerThickness() > 0) {
            final int it = s.innerThickness();
            g.fill(sx - it, sy - it, sx + sw + it, sy + sh + it, s.innerColor());
        }

        renderChin(g, font, s, geo);
    }

    /** Draws the chin strip below the glass: the model label on the left, then vents and the power LED on the right. */
    private static void renderChin(final GuiGraphics g, final Font font, final MonitorFrameStyle s,
                                   final MonitorFrameStyle.Geometry geo) {
        final int rowY = geo.chinY() + Math.max(0, (s.chinHeight() - GLYPH_H) / 2);

        if (!s.model().isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(geo.x() + 8, rowY, 0);
            g.pose().scale(LABEL_SCALE, LABEL_SCALE, 1.0f);
            g.drawString(font, s.model(), 0, 0, s.labelColor(), false);
            g.pose().popPose();
        }

        // Power LED at the right edge.
        final int ledX = geo.right() - 11;
        final int ledY = rowY;
        g.fill(ledX, ledY, ledX + 5, ledY + 5, s.ledColor());

        // Two vent slots to the left of the LED.
        if (s.ventColor() != 0) {
            int vx = ledX - 8;
            for (int i = 0; i < 2; i++) {
                g.fill(vx - 12, ledY + 1, vx, ledY + 4, s.ventColor());
                vx -= 16;
            }
        }
    }
}
