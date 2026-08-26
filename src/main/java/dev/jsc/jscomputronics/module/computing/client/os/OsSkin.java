/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The one drawing framework every desktop program paints through, so a program looks like the OS it runs on
 * rather than carrying its own hardcoded chrome. A skin is resolved from the installed OS id ({@code panes_95
 * / panes_xp / panes_11}). Each skin is a <em>distinct design</em>, not a recolour of one layout: every skin
 * holds its own palette and its own {@link Form} shape language, faithful to the approved style guide.
 *
 * <ul>
 *   <li>{@link Form#BEVEL} (Panes 95) — raised/sunken 3D bevels, solid navy title, grey chrome, square.</li>
 *   <li>{@link Form#LUNA} (Panes XP) — blue gradients, white title text, green active tab; only the TOP
 *       window corners are rounded (XP kept square bottom corners).</li>
 *   <li>{@link Form#FLAT} (Panes 11) — light chrome with DARK title text, flat fills, a thin accent line,
 *       all four window corners gently rounded.</li>
 * </ul>
 */
public final class OsSkin {

    /** The shape language a skin draws its primitives in. */
    public enum Form {
        BEVEL, LUNA, FLAT
    }

    /** A window-control glyph, so the control's shape can vary independently of its meaning. */
    public enum Control {
        MINIMIZE, MAXIMIZE, RESTORE, CLOSE
    }

    private final DesktopTheme theme;
    private final Form form;
    private final int topRadius;
    private final int bottomRadius;
    private final int titleText;
    private final boolean titleShadow;
    private final int windowBg;
    private final int windowBorder;
    private final int accent;
    private final int text;
    private final int dim;
    private final int fieldBg;
    private final int listSelectBg;
    private final int listSelectText;
    private final int listHoverBg;

    private OsSkin(final DesktopTheme theme, final Form form, final int topRadius, final int bottomRadius,
                   final int titleText, final boolean titleShadow, final int windowBg, final int windowBorder,
                   final int accent, final int text, final int dim, final int fieldBg,
                   final int listSelectBg, final int listSelectText, final int listHoverBg) {
        this.theme = theme;
        this.form = form;
        this.topRadius = topRadius;
        this.bottomRadius = bottomRadius;
        this.titleText = titleText;
        this.titleShadow = titleShadow;
        this.windowBg = windowBg;
        this.windowBorder = windowBorder;
        this.accent = accent;
        this.text = text;
        this.dim = dim;
        this.fieldBg = fieldBg;
        this.listSelectBg = listSelectBg;
        this.listSelectText = listSelectText;
        this.listHoverBg = listHoverBg;
    }

    // Panes 95 — classic grey bevel, solid navy title, square corners.
    private static final OsSkin PANES_95 = new OsSkin(
            DesktopTheme.forOs(ResourceLocation.fromNamespaceAndPath("jsc", "panes_95")),
            Form.BEVEL, 0, 0, 0xFFFFFFFF, false, 0xFFC0C0C0, 0xFF000000,
            0xFF000080, 0xFF000000, 0xFF505050, 0xFFFFFFFF,
            0xFF000080, 0xFFFFFFFF, 0xFFD4D0C8);

    // Panes XP — Luna blue gradients, white title, cream client; ONLY the top corners are rounded.
    private static final OsSkin PANES_XP = new OsSkin(
            DesktopTheme.forOs(ResourceLocation.fromNamespaceAndPath("jsc", "panes_xp")),
            Form.LUNA, 2, 0, 0xFFFFFFFF, true, 0xFFECECF6, 0xFF0831D9,
            0xFF2C66BD, 0xFF10203A, 0xFF5A6B85, 0xFFFFFFFF,
            0xFF2C66BD, 0xFFFFFFFF, 0xFFD8E4FB);

    // Panes 11 — flat light chrome with DARK title text and a thin accent; all corners rounded.
    private static final OsSkin PANES_11 = new OsSkin(
            DesktopTheme.forOs(ResourceLocation.fromNamespaceAndPath("jsc", "panes_11")),
            Form.FLAT, 2, 2, 0xFF202434, false, 0xFFFAFAFE, 0xFFC0C4D2,
            0xFF3A6AE0, 0xFF202434, 0xFF6B7488, 0xFFFFFFFF,
            0xFFE7EEFC, 0xFF1D4ED8, 0xFFF0F1F7);

    /** The skin for an installed OS id; Panes 95 is the fallback. */
    public static OsSkin forOs(final ResourceLocation osId) {
        return switch (osId.getPath()) {
            case "panes_xp" -> PANES_XP;
            case "panes_11" -> PANES_11;
            default -> PANES_95;
        };
    }

    /** A safe default skin (Panes 95) for a field that needs a non-null value before the first render. */
    public static OsSkin fallback() {
        return PANES_95;
    }

    /** The installed OS id path this skin represents (panes_95/panes_xp/panes_11). */
    public String osPath() {
        return switch (form) {
            case LUNA -> "panes_xp";
            case FLAT -> "panes_11";
            default -> "panes_95";
        };
    }

    public DesktopTheme theme() {
        return theme;
    }

    public Form form() {
        return form;
    }

    public int accent() {
        return accent;
    }

    public int text() {
        return text;
    }

    public int dim() {
        return dim;
    }

    public int windowBg() {
        return windowBg;
    }

    public int windowBorder() {
        return windowBorder;
    }

    public int titleText() {
        return titleText;
    }

    public boolean textShadow() {
        return titleShadow;
    }

    /** The fill of a content panel (grey 95 / cream XP / white 11). */
    public int panelBg() {
        return panelFill();
    }

    /** The fill of a text field. */
    public int fieldBg() {
        return fieldBg;
    }

    /** A 1px border/separator colour for panels and bands, per skin. */
    public int edge() {
        return switch (form) {
            case BEVEL -> 0xFF808080;
            case LUNA -> 0xFFB9C4DA;
            case FLAT -> 0xFFE3E5EE;
        };
    }

    /** The hover-row background. */
    public int listHover() {
        return listHoverBg;
    }

    // ---- window chrome ----

    /**
     * The window body background and 1px outer border, with the skin's per-corner rounding (XP rounds only the
     * top; 11 rounds all four; 95 none). Rounded corner pixels are left unpainted, so they show what is behind.
     */
    public void windowFrame(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        roundedRect(g, x - 1, y - 1, w + 2, h + 2, windowBorder, topRadius, bottomRadius);
        roundedRect(g, x, y, w, h, windowBg, topRadius, bottomRadius);
    }

    /** The title bar fill (solid navy / Luna gradient / flat light), rounding only the top corners. */
    public void titleBar(final GuiGraphics g, final int x, final int y, final int w, final int titleH) {
        switch (form) {
            case BEVEL -> roundedRect(g, x, y, w, titleH, 0xFF000080, topRadius, 0);
            case LUNA -> {
                // Three stacked bands approximate the Luna glass gradient; top corners rounded to match.
                roundedRect(g, x, y, w, titleH, 0xFF3F7FD6, topRadius, 0);
                g.fill(x, y + titleH / 3, x + w, y + titleH * 2 / 3, 0xFF2C66BD);
                g.fill(x, y + titleH * 2 / 3, x + w, y + titleH, 0xFF1C4D9C);
            }
            case FLAT -> {
                roundedRect(g, x, y, w, titleH, windowBg, topRadius, 0); // light bar, dark title text
                g.fill(x + topRadius, y + titleH - 1, x + w - topRadius, y + titleH, 0xFFE7E9F0); // hairline
            }
        }
    }

    /** One title-bar control, shaped per skin, with a real pressed state so a click reads. */
    public void windowControl(final GuiGraphics g, final Font font, final int x, final int y, final int bw,
                              final int bh, final Control control, final boolean hovered, final boolean pressed) {
        final boolean isClose = control == Control.CLOSE;
        int nudge = 0;
        switch (form) {
            case BEVEL -> {
                g.fill(x, y, x + bw, y + bh, 0xFFC0C0C0);
                bevel(g, x, y, bw, bh, !pressed); // pressed → sunken
                nudge = pressed ? 1 : 0;
            }
            case LUNA -> {
                final int top = isClose ? 0xFFE58A6F : 0xFF6F9FE0;
                final int bottom = isClose ? 0xFFC5341A : 0xFF2F63B8;
                if (pressed) {
                    g.fillGradient(x, y, x + bw, y + bh, bottom, top); // inverted = pushed-in
                } else {
                    g.fillGradient(x, y, x + bw, y + bh, hovered ? lighten(top) : top, bottom);
                }
                outline(g, x, y, bw, bh, isClose ? 0xFF8E2010 : 0xFF15448E);
                nudge = pressed ? 1 : 0;
            }
            case FLAT -> {
                if (pressed) {
                    g.fill(x, y, x + bw, y + bh, isClose ? 0xFFC5341A : 0xFFD0D3DC);
                } else if (isClose && hovered) {
                    g.fill(x, y, x + bw, y + bh, 0xFFE5413A);
                } else if (hovered) {
                    g.fill(x, y, x + bw, y + bh, 0xFFE6E8F0);
                }
            }
        }
        glyph(g, font, control, x + nudge, y + nudge, bw, bh, hovered, pressed);
    }

    private void glyph(final GuiGraphics g, final Font font, final Control control, final int x, final int y,
                       final int bw, final int bh, final boolean hovered, final boolean pressed) {
        final boolean closeLit = control == Control.CLOSE && (pressed || (form == Form.FLAT && hovered));
        final int color = switch (form) {
            case BEVEL -> 0xFF000000;
            case LUNA -> 0xFFFFFFFF;
            case FLAT -> closeLit ? 0xFFFFFFFF : 0xFF3A4256;
        };
        final String s = switch (control) {
            case MINIMIZE -> "_";
            case MAXIMIZE -> "□";
            case RESTORE -> "❐";
            case CLOSE -> "✕";
        };
        g.drawString(font, s, x + (bw - font.width(s)) / 2, y + (bh - 7) / 2, color, false);
    }

    // ---- widgets (used by the programs' content) ----

    /** A group panel/box: sunken bevel (95), soft border (XP), or hairline (11). */
    public void panel(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        g.fill(x, y, x + w, y + h, panelFill());
        switch (form) {
            case BEVEL -> bevel(g, x, y, w, h, false); // sunken
            case LUNA -> outline(g, x, y, w, h, 0xFFB9C4DA);
            case FLAT -> outline(g, x, y, w, h, 0xFFE3E5EE);
        }
    }

    /** A push button, optionally the primary/default one, with a pressed state. */
    public void button(final GuiGraphics g, final Font font, final int x, final int y, final int w, final int h,
                       final String label, final boolean hovered, final boolean pressed, final boolean primary) {
        switch (form) {
            case BEVEL -> {
                g.fill(x, y, x + w, y + h, 0xFFC0C0C0);
                bevel(g, x, y, w, h, !pressed);
            }
            case LUNA -> {
                final int a = hovered ? 0xFFFFFFFF : 0xFFFDFDFF;
                g.fillGradient(x, y, x + w, y + h, pressed ? 0xFFD6E0F2 : a, pressed ? a : 0xFFD6E0F2);
                outline(g, x, y, w, h, primary ? 0xFF2C66BD : 0xFF7A9BD0);
            }
            case FLAT -> {
                final int base = primary ? (pressed ? 0xFF2F59C4 : 0xFF3A6AE0)
                        : (pressed ? 0xFFD0D3DC : (hovered ? 0xFFEEF0F6 : 0xFFFBFBFE));
                g.fill(x, y, x + w, y + h, base);
                outline(g, x, y, w, h, primary ? 0xFF2F59C4 : 0xFFCDD1DD);
            }
        }
        final int tc = (form == Form.FLAT && primary) ? 0xFFFFFFFF : text;
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 7) / 2 + (pressed ? 1 : 0), tc, false);
    }

    /** A text input field. */
    public void field(final GuiGraphics g, final int x, final int y, final int w, final int h,
                      final boolean focused) {
        g.fill(x, y, x + w, y + h, fieldBg);
        switch (form) {
            case BEVEL -> bevel(g, x, y, w, h, false); // sunken
            case LUNA -> outline(g, x, y, w, h, focused ? 0xFF2C66BD : 0xFF7F9DB9);
            case FLAT -> {
                outline(g, x, y, w, h, 0xFFCDD1DD);
                g.fill(x, y + h - 2, x + w, y + h, focused ? accent : 0xFFCDD1DD); // accent underline
            }
        }
    }

    /** A tab in a tab strip. */
    public void tab(final GuiGraphics g, final Font font, final int x, final int y, final int w, final int h,
                    final String label, final boolean active) {
        switch (form) {
            case BEVEL -> {
                g.fill(x, y, x + w, y + h + (active ? 2 : 0), 0xFFC0C0C0);
                bevel(g, x, y, w, h + (active ? 2 : 0), true);
            }
            case LUNA -> {
                if (active) {
                    g.fillGradient(x, y, x + w, y + h, 0xFFFFFFFF, 0xFFDFEECB);
                    g.fill(x, y, x + w, y + 2, 0xFF8FD14F);
                    outline(g, x, y, w, h, 0xFF7FA83F);
                } else {
                    g.fillGradient(x, y, x + w, y + h, 0xFFF4F7FD, 0xFFCDD9EE);
                    outline(g, x, y, w, h, 0xFF93A9CC);
                }
            }
            case FLAT -> {
                if (active) {
                    g.fill(x, y + h - 2, x + w, y + h, accent); // underline only
                }
            }
        }
        final int tc = switch (form) {
            case LUNA -> active ? 0xFF2B5A16 : 0xFF22324D;
            case FLAT -> active ? 0xFF1D4ED8 : 0xFF5B6478;
            default -> text;
        };
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + (h - 7) / 2, tc, false);
    }

    /** A list/grid row background for the hover and selection states. */
    public void listRow(final GuiGraphics g, final int x, final int y, final int w, final int h,
                        final boolean hovered, final boolean selected) {
        if (selected) {
            g.fill(x, y, x + w, y + h, listSelectBg);
            if (form == Form.FLAT) {
                g.fill(x, y, x + 3, y + h, accent); // accent bar on the left
            }
        } else if (hovered) {
            g.fill(x, y, x + w, y + h, listHoverBg);
        }
    }

    /** The text colour for a list row, given its selection state. */
    public int listRowText(final boolean selected) {
        return selected ? listSelectText : text;
    }

    /** A scrollbar thumb. */
    public void scrollThumb(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        switch (form) {
            case BEVEL -> {
                g.fill(x, y, x + w, y + h, 0xFFC0C0C0);
                bevel(g, x, y, w, h, true);
            }
            case LUNA -> {
                g.fillGradient(x, y, x + w, y + h, 0xFFFDFDFF, 0xFFC2D2EE);
                outline(g, x, y, w, h, 0xFF93A9CC);
            }
            case FLAT -> g.fill(x + 1, y, x + w - 1, y + h, 0xFFC8CDDA);
        }
    }

    /** A status/footer bar background. */
    public void statusBar(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        switch (form) {
            case BEVEL -> {
                g.fill(x, y, x + w, y + h, 0xFFC0C0C0);
                bevel(g, x, y, w, h, false);
            }
            case LUNA -> {
                g.fill(x, y, x + w, y + h, 0xFFECECF6);
                g.fill(x, y, x + w, y + 1, 0xFFB9C4DA);
            }
            case FLAT -> {
                g.fill(x, y, x + w, y + h, 0xFFF1F2F6);
                g.fill(x, y, x + w, y + 1, 0xFFE3E5EE);
            }
        }
    }

    private int panelFill() {
        return switch (form) {
            case BEVEL -> 0xFFC0C0C0;
            case LUNA -> 0xFFF4F6FC;
            case FLAT -> 0xFFFFFFFF;
        };
    }

    // ---- primitives ----

    /** A filled rectangle whose TOP corners are rounded by {@code rTop}px and BOTTOM by {@code rBottom}px. */
    public static void roundedRect(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                   final int color, final int rTop, final int rBottom) {
        final int top = Math.max(0, rTop);
        final int bottom = Math.max(0, rBottom);
        g.fill(x, y + top, x + w, y + h - bottom, color);
        for (int i = 0; i < top; i++) {
            final int inset = top - i;
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
        }
        for (int i = 0; i < bottom; i++) {
            final int inset = bottom - i;
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
    }

    /** A raised (light top-left, dark bottom-right) or sunken (inverted) 1px bevel border. */
    public static void bevel(final GuiGraphics g, final int x, final int y, final int w, final int h,
                             final boolean raised) {
        final int light = raised ? 0xFFFFFFFF : 0xFF404040;
        final int dark = raised ? 0xFF404040 : 0xFFFFFFFF;
        g.fill(x, y, x + w, y + 1, light);
        g.fill(x, y, x + 1, y + h, light);
        g.fill(x, y + h - 1, x + w, y + h, dark);
        g.fill(x + w - 1, y, x + w, y + h, dark);
    }

    /** A 1px outline of a single colour. */
    public static void outline(final GuiGraphics g, final int x, final int y, final int w, final int h,
                               final int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static int lighten(final int argb) {
        final int r = Math.min(255, (argb >> 16 & 0xFF) + 24);
        final int gg = Math.min(255, (argb >> 8 & 0xFF) + 24);
        final int b = Math.min(255, (argb & 0xFF) + 24);
        return 0xFF000000 | r << 16 | gg << 8 | b;
    }
}
