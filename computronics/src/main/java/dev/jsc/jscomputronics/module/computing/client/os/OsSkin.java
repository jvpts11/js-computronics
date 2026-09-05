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
 * rather than carrying its own hardcoded chrome. A skin is resolved from the installed OS id ({@code frames_95
 * / frames_xp / frames_11}). Each skin is a <em>distinct design</em>, not a recolour of one layout: every skin
 * holds its own palette and its own {@link Form} shape language, faithful to the approved style guide.
 *
 * <ul>
 *   <li>{@link Form#BEVEL} (Frames 95) — raised/sunken 3D bevels, solid navy title, grey chrome, square.</li>
 *   <li>{@link Form#LUNA} (Frames XP) — blue gradients, white title text, green active tab; only the TOP
 *       window corners are rounded (XP kept square bottom corners).</li>
 *   <li>{@link Form#FLAT} (Frames 11) — light chrome with DARK title text, flat fills, a thin accent line,
 *       all four window corners gently rounded.</li>
 * </ul>
 */
public final class OsSkin {

    /**
     * The shape language a skin draws its primitives in. A period Unix desktop is not the Frames 95
     * bevel in other colours — it has its own geometry — so KDE and GNOME on Legacy hardware each get
     * a form of their own rather than a recolour.
     */
    public enum Form {
        BEVEL, LUNA, FLAT,
        /** KDE of the early 2000s: vertical title gradient, single-pixel borders, soft greys. */
        KDE2,
        /** GNOME of the late 1990s: thick frame, centred title, warm greys, chunky bevelled studs. */
        GNOME1
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
    /** Whether this is a dark-theme variant (only the flat Frames 11 skin has one); flips the flat chrome. */
    private final boolean dark;
    /** The desktop environment id path this skin belongs to (the icon set and wallpaper key). */
    private final String desktopPath;

    private OsSkin(final DesktopTheme theme, final Form form, final int topRadius, final int bottomRadius,
                   final int titleText, final boolean titleShadow, final int windowBg, final int windowBorder,
                   final int accent, final int text, final int dim, final int fieldBg,
                   final int listSelectBg, final int listSelectText, final int listHoverBg, final boolean dark) {
        this(theme, form, topRadius, bottomRadius, titleText, titleShadow, windowBg, windowBorder, accent, text,
                dim, fieldBg, listSelectBg, listSelectText, listHoverBg, dark, switch (form) {
                    case LUNA -> "frames_xp";
                    case FLAT -> "frames_11";
                    default -> "frames_95";
                });
    }

    private OsSkin(final DesktopTheme theme, final Form form, final int topRadius, final int bottomRadius,
                   final int titleText, final boolean titleShadow, final int windowBg, final int windowBorder,
                   final int accent, final int text, final int dim, final int fieldBg,
                   final int listSelectBg, final int listSelectText, final int listHoverBg, final boolean dark,
                   final String desktopPath) {
        this.desktopPath = desktopPath;
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
        this.dark = dark;
    }

    // Frames 95 — classic grey bevel, solid navy title, square corners.
    private static final OsSkin FRAMES_95 = new OsSkin(
            DesktopTheme.forOs(ResourceLocation.fromNamespaceAndPath("jsc", "frames_95")),
            Form.BEVEL, 0, 0, 0xFFFFFFFF, false, 0xFFC0C0C0, 0xFF000000,
            0xFF000080, 0xFF000000, 0xFF505050, 0xFFFFFFFF,
            0xFF000080, 0xFFFFFFFF, 0xFFD4D0C8, false);

    // Frames XP — Luna blue gradients, white title, cream client; ONLY the top corners are rounded.
    private static final OsSkin FRAMES_XP = new OsSkin(
            DesktopTheme.forOs(ResourceLocation.fromNamespaceAndPath("jsc", "frames_xp")),
            Form.LUNA, 2, 0, 0xFFFFFFFF, true, 0xFFECECF6, 0xFF0831D9,
            0xFF2C66BD, 0xFF10203A, 0xFF5A6B85, 0xFFFFFFFF,
            0xFF2C66BD, 0xFFFFFFFF, 0xFFD8E4FB, false);

    // Frames 11 — flat light chrome with DARK title text and a thin accent; all corners rounded.
    private static final OsSkin FRAMES_11 = new OsSkin(
            DesktopTheme.forOs(ResourceLocation.fromNamespaceAndPath("jsc", "frames_11")),
            Form.FLAT, 2, 2, 0xFF202434, false, 0xFFFAFAFE, 0xFFC0C4D2,
            0xFF3A6AE0, 0xFF202434, 0xFF6B7488, 0xFFFFFFFF,
            0xFFE7EEFC, 0xFF1D4ED8, 0xFFF0F1F7, false);

    // Frames 11 (dark) — the same flat shape language on a dark slate palette, chosen in Settings. A brighter
    // accent keeps contrast on the dark ground; the flat chrome branches key off the dark flag.
    private static final OsSkin FRAMES_11_DARK = new OsSkin(
            DesktopTheme.forOs(ResourceLocation.fromNamespaceAndPath("jsc", "frames_11")),
            Form.FLAT, 2, 2, 0xFFE7E9EF, false, 0xFF1E212A, 0xFF3A4150,
            0xFF5B84F0, 0xFFE7E9EF, 0xFF9AA2B2, 0xFF14171F,
            0xFF2A3A63, 0xFFDCE7FF, 0xFF262B36, true);

    // The Linux desktop environments: flat chrome like Frames 11, each in its own palette and accent.
    // KDE Plasma — Breeze: light grey window, sky-blue accent.
    private static final OsSkin KDE_PLASMA = new OsSkin(
            DesktopTheme.forDesktop(ResourceLocation.fromNamespaceAndPath("jsc", "kde_plasma")),
            Form.FLAT, 2, 2, 0xFF232629, false, 0xFFEFF0F1, 0xFFB9BFC8,
            0xFF3DAEE9, 0xFF232629, 0xFF6E7680, 0xFFFCFCFC,
            0xFFD6ECF7, 0xFF1F6F9A, 0xFFE6EBEF, false, "kde_plasma");

    // GNOME — Adwaita: warm light window, GNOME blue accent, rounded.
    private static final OsSkin GNOME = new OsSkin(
            DesktopTheme.forDesktop(ResourceLocation.fromNamespaceAndPath("jsc", "gnome")),
            Form.FLAT, 3, 3, 0xFF2E3436, false, 0xFFF6F5F4, 0xFFC0BFBC,
            0xFF3584E4, 0xFF2E3436, 0xFF77767B, 0xFFFFFFFF,
            0xFFDCE8FA, 0xFF1C5FB4, 0xFFEBEBEA, false, "gnome");

    // Cinnamon — Mint-Y: light grey window, Mint green accent.
    private static final OsSkin CINNAMON = new OsSkin(
            DesktopTheme.forDesktop(ResourceLocation.fromNamespaceAndPath("jsc", "cinnamon")),
            Form.FLAT, 2, 2, 0xFF2B2B2B, false, 0xFFF7F7F7, 0xFFB0B0B0,
            0xFF69B03B, 0xFF2B2B2B, 0xFF6E6E6E, 0xFFFFFFFF,
            0xFFDFF0D4, 0xFF3C6E1E, 0xFFEBEBEB, false, "cinnamon");

    // The same desktops as they looked on Legacy-era hardware. These are not the modern skins in older
    // colours: each carries its own geometry, because that is what actually told the two apart at the
    // time. KDE ran cold blue-grey with vertical gradients and hairline borders; GNOME ran warm putty
    // with a thick frame and a centred title, since a separate window manager drew its decoration.

    private static final OsSkin KDE_PLASMA_LEGACY = new OsSkin(
            DesktopTheme.forDesktop(ResourceLocation.fromNamespaceAndPath("jsc", "kde_plasma")),
            Form.KDE2, 0, 0, 0xFFFFFFFF, true, 0xFFD6D2CD, 0xFF6F6A64,
            0xFF1D4C80, 0xFF1A1A1A, 0xFF5F5A54, 0xFFFFFFFF,
            0xFF33679F, 0xFFFFFFFF, 0xFFC7C2BB, false, "kde_plasma");

    private static final OsSkin GNOME_LEGACY = new OsSkin(
            DesktopTheme.forDesktop(ResourceLocation.fromNamespaceAndPath("jsc", "gnome")),
            Form.GNOME1, 0, 0, 0xFFFFFFFF, true, 0xFFD6D2C8, 0xFFB0AA9C,
            0xFF6D5A78, 0xFF1A1A1A, 0xFF5C574E, 0xFFFFFFFF,
            0xFF6D5A78, 0xFFFFFFFF, 0xFFC4BFB2, false, "gnome");

    /** The skin for an installed OS id; Frames 95 is the fallback. Kept for the Frames editions (id = desktop). */
    public static OsSkin forOs(final ResourceLocation osId) {
        return forDesktop(osId);
    }

    /**
     * The skin for a desktop as it looks on hardware of {@code era}. The Frames editions already ARE
     * their era (95 is Legacy, 11 is Standard). Of the Linux desktops only KDE and GNOME get a period
     * variant, because only those two install on Legacy hardware at all — Cinnamon is a later desktop
     * and requires a Standard machine, so it has no older self to wear.
     */
    public static OsSkin forDesktop(final ResourceLocation desktopId,
                                    final dev.jstech.core.tier.HardwareEra era) {
        if (era != null && era.ordinal() <= dev.jstech.core.tier.HardwareEra.LEGACY.ordinal()) {
            switch (desktopId.getPath()) {
                case "kde_plasma":
                    return KDE_PLASMA_LEGACY;
                case "gnome":
                    return GNOME_LEGACY;
                default:
                    break;
            }
        }
        return forDesktop(desktopId);
    }

    /** The skin for a desktop environment id (the Frames editions, KDE Plasma, GNOME, Cinnamon); Frames 95 fallback. */
    public static OsSkin forDesktop(final ResourceLocation desktopId) {
        return switch (desktopId.getPath()) {
            case "frames_xp" -> FRAMES_XP;
            case "frames_11" -> FRAMES_11;
            case "kde_plasma" -> KDE_PLASMA;
            case "gnome" -> GNOME;
            case "cinnamon" -> CINNAMON;
            default -> FRAMES_95;
        };
    }

    /** A safe default skin (Frames 95) for a field that needs a non-null value before the first render. */
    public static OsSkin fallback() {
        return FRAMES_95;
    }

    /**
     * Returns this skin with its accent replaced by {@code argb} (a per-computer override chosen in
     * Settings), or this skin unchanged when {@code argb} is {@code 0} (no override) or already the
     * current accent. Every control the skin draws (primary buttons, tab and field accents, the flat
     * list-selection bar) then uses the chosen colour.
     *
     * @param argb the override accent as an opaque ARGB int, or {@code 0} to keep the skin default
     * @return a skin using the override accent, or {@code this}
     */
    public OsSkin withAccent(final int argb) {
        if (argb == 0 || argb == accent) {
            return this;
        }
        return new OsSkin(theme, form, topRadius, bottomRadius, titleText, titleShadow, windowBg, windowBorder,
                argb, text, dim, fieldBg, listSelectBg, listSelectText, listHoverBg, dark, desktopPath);
    }

    /**
     * The dark-theme counterpart of this skin, or {@code this} when the skin has no dark variant. Only the flat
     * Frames 11 skin defines one; the earlier editions have no historical dark mode, so they are returned as-is.
     */
    public OsSkin darkVariant() {
        return form == Form.FLAT ? FRAMES_11_DARK : this;
    }

    /** Whether this is the dark-theme variant. */
    public boolean isDark() {
        return dark;
    }

    /** The desktop environment id path this skin represents (frames_95/xp/11, kde_plasma, gnome, cinnamon). */
    public String osPath() {
        return desktopPath;
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
            case FLAT -> dark ? 0xFF333A48 : 0xFFE3E5EE;
            case KDE2 -> 0xFF8B857E;
            case GNOME1 -> 0xFFA49E8F;
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
        final int t = frameThickness();
        roundedRect(g, x - t, y - t, w + t * 2, h + t * 2, windowBorder, topRadius, bottomRadius);
        if (form == Form.GNOME1) {
            // The thick period frame is relief, not a flat band: a separate window manager drew it, and
            // a plain slab of colour at this width just looks like a mistake.
            bevelDouble(g, x - t, y - t, w + t * 2, h + t * 2, true);
        }
        roundedRect(g, x, y, w, h, windowBg, topRadius, bottomRadius);
    }

    /** The title bar fill (solid navy / Luna gradient / flat light), rounding only the top corners. */
    public void titleBar(final GuiGraphics g, final int x, final int y, final int w, final int titleH) {
        titleBar(g, x, y, w, titleH, true);
    }

    /**
     * A window's title bar, lit when the window has focus and greyed when it does not. Every desktop
     * this mod imitates says which window is in front the same way — the active bar keeps its colour
     * and the rest fall back to a flat grey — and without that a stack of open programs gives the
     * player nothing to read.
     */
    public void titleBar(final GuiGraphics g, final int x, final int y, final int w, final int titleH,
                         final boolean active) {
        if (!active) {
            switch (form) {
                case BEVEL -> hGradient(g, x, y, w, titleH, 0xFF7F7F7F, 0xFFB0B0B0);
                case LUNA -> {
                    roundedRect(g, x, y, w, titleH, 0xFF8FA8C4, topRadius, 0);
                    g.fillGradient(x, y + topRadius, x + w, y + titleH, 0xFF9DB2C9, 0xFF7E93AC);
                }
                case FLAT -> {
                    roundedRect(g, x, y, w, titleH, flat(0xFFEDEEF2, 0xFF1B2029), topRadius, 0);
                    g.fill(x + topRadius, y + titleH - 1, x + w - topRadius, y + titleH, edge());
                }
                // Both period forms desaturate rather than dim: an inactive window of that age kept its
                // gradient and lost its colour.
                case KDE2 -> {
                    g.fillGradient(x, y, x + w, y + titleH / 2, 0xFFB4B0AA, 0xFF98938C);
                    g.fillGradient(x, y + titleH / 2, x + w, y + titleH, 0xFF938E87, 0xFF7E7972);
                    g.fill(x, y + titleH - 1, x + w, y + titleH, 0xFF6F6A64);
                }
                case GNOME1 -> {
                    g.fillGradient(x, y, x + w, y + titleH / 2, 0xFFAFAAA0, 0xFF938E84);
                    g.fillGradient(x, y + titleH / 2, x + w, y + titleH, 0xFF8C877D, 0xFF767168);
                }
            }
            return;
        }
        switch (form) {
            case BEVEL -> {
                // Classic active-title gradient: deep navy on the left brightening to blue on the right.
                hGradient(g, x, y, w, titleH, 0xFF000080, 0xFF1084D0);
            }
            case LUNA -> {
                // A smooth two-stop vertical gradient with a bright top gloss line, closer to the Luna glass.
                roundedRect(g, x, y, w, titleH, 0xFF3F7FD6, topRadius, 0);
                g.fillGradient(x, y + topRadius, x + w, y + titleH / 2, 0xFF4B91E2, 0xFF2F6FC6);
                g.fillGradient(x, y + titleH / 2, x + w, y + titleH, 0xFF2C66BD, 0xFF1C4D9C);
                g.fill(x + topRadius, y + 1, x + w - topRadius, y + 2, 0x66FFFFFF); // top gloss
                g.fill(x, y + titleH - 1, x + w, y + titleH, 0xFF16407F); // bottom shade
            }
            case FLAT -> {
                roundedRect(g, x, y, w, titleH, windowBg, topRadius, 0); // light bar, dark title text
                g.fill(x + topRadius, y + titleH - 1, x + w - topRadius, y + titleH, edge()); // hairline
            }
            case KDE2 -> {
                // Vertical three-stop blue: light crown, mid body, dark base, closed by a border line.
                g.fillGradient(x, y, x + w, y + titleH / 2, 0xFF6F9FD0, 0xFF33679F);
                g.fillGradient(x, y + titleH / 2, x + w, y + titleH, 0xFF2E5F95, 0xFF1D4C80);
                g.fill(x, y + titleH - 1, x + w, y + titleH, 0xFF6F6A64);
            }
            case GNOME1 -> {
                // Muted purple, the colour that separated a GNOME box from a KDE one across the room.
                g.fillGradient(x, y, x + w, y + titleH / 2, 0xFF8F7D99, 0xFF6D5A78);
                g.fillGradient(x, y + titleH / 2, x + w, y + titleH, 0xFF63506E, 0xFF55455F);
            }
        }
    }

    /**
     * Whether this skin centres a window title. Only the GNOME form does, which is exactly why it reads
     * as a different desktop rather than a repainted one — nothing else in the mod centres a title.
     */
    public boolean titleCentered() {
        return form == Form.GNOME1;
    }

    /** The thickness of the window frame in pixels; the GNOME form draws the chunky period border. */
    public int frameThickness() {
        return form == Form.GNOME1 ? 3 : 1;
    }

    /** One title-bar control, shaped per skin, with a real pressed state so a click reads. */
    public void windowControl(final GuiGraphics g, final Font font, final int x, final int y, final int bw,
                              final int bh, final Control control, final boolean hovered, final boolean pressed) {
        final boolean isClose = control == Control.CLOSE;
        int nudge = 0;
        switch (form) {
            case BEVEL -> {
                g.fill(x, y, x + bw, y + bh, 0xFFC0C0C0);
                bevelDouble(g, x, y, bw, bh, !pressed); // pressed → sunken
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
                    g.fill(x, y, x + bw, y + bh, isClose ? 0xFFC5341A : flat(0xFFD0D3DC, 0xFF3A4150));
                } else if (isClose && hovered) {
                    g.fill(x, y, x + bw, y + bh, 0xFFE5413A);
                } else if (hovered) {
                    g.fill(x, y, x + bw, y + bh, flat(0xFFE6E8F0, 0xFF2C3340));
                }
            }
            case KDE2 -> {
                // A small pale stud with a hairline border, not a bevelled block.
                if (pressed) {
                    g.fillGradient(x, y, x + bw, y + bh, 0xFFC9C4BE, 0xFFF2F1EF);
                } else {
                    g.fillGradient(x, y, x + bw, y + bh, hovered ? 0xFFFFFFFF : 0xFFF2F1EF, 0xFFC9C4BE);
                }
                outline(g, x, y, bw, bh, 0xFF6F6A64);
                nudge = pressed ? 1 : 0;
            }
            case GNOME1 -> {
                g.fill(x, y, x + bw, y + bh, hovered ? 0xFFE2DED4 : 0xFFD6D2C8);
                bevelDouble(g, x, y, bw, bh, !pressed);
                nudge = pressed ? 1 : 0;
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
            case FLAT -> closeLit ? 0xFFFFFFFF : flat(0xFF3A4256, 0xFFC4CAD6);
            case KDE2 -> 0xFF17324F;
            case GNOME1 -> 0xFF2A2A2A;
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
            case BEVEL -> bevelDouble(g, x, y, w, h, false); // sunken well
            case LUNA -> outline(g, x, y, w, h, edge());
            case FLAT -> outline(g, x, y, w, h, edge());
            case KDE2 -> outline(g, x, y, w, h, edge());
            case GNOME1 -> bevelDouble(g, x, y, w, h, false); // sunken well, warm
        }
    }

    /** A push button, optionally the primary/default one, with a pressed state. */
    public void button(final GuiGraphics g, final Font font, final int x, final int y, final int w, final int h,
                       final String label, final boolean hovered, final boolean pressed, final boolean primary) {
        switch (form) {
            case BEVEL -> {
                g.fill(x, y, x + w, y + h, 0xFFC0C0C0);
                bevelDouble(g, x, y, w, h, !pressed);
                // The default (primary) button carries the classic dotted focus rectangle just inside its face.
                if (primary && !pressed) {
                    dottedRect(g, x + 3, y + 3, w - 6, h - 6, 0xFF000000);
                }
            }
            case LUNA -> {
                // A glossier vertical sheen: bright top third, then the blue-tinted body.
                final int a = hovered ? 0xFFFFFFFF : 0xFFFDFDFF;
                g.fillGradient(x, y, x + w, y + h / 2, pressed ? 0xFFD0DBEF : a, pressed ? 0xFFE6EDF9 : 0xFFEAF0FB);
                g.fillGradient(x, y + h / 2, x + w, y + h, pressed ? 0xFFE6EDF9 : 0xFFDDE7F6, pressed ? a : 0xFFCBD9F0);
                g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x88FFFFFF); // top gloss line
                outline(g, x, y, w, h, primary ? 0xFF2C66BD : 0xFF7A9BD0);
            }
            case FLAT -> {
                final int base = primary ? (pressed ? darken(accent) : accent)
                        : (pressed ? flat(0xFFD0D3DC, 0xFF3A4150)
                                   : (hovered ? flat(0xFFEEF0F6, 0xFF2C3340) : flat(0xFFFBFBFE, 0xFF262B36)));
                roundedRect(g, x, y, w, h, base, 2, 2);
                roundedOutline(g, x, y, w, h, primary ? darken(accent) : flat(0xFFCDD1DD, 0xFF3A4150), 2);
            }
            case KDE2 -> {
                if (pressed) {
                    g.fillGradient(x, y, x + w, y + h, 0xFFC9C4BE, 0xFFF4F2EF);
                } else {
                    g.fillGradient(x, y, x + w, y + h, hovered ? 0xFFFFFFFF : 0xFFF4F2EF, 0xFFCEC9C2);
                }
                outline(g, x, y, w, h, primary ? 0xFF1D4C80 : 0xFF8B857E);
            }
            case GNOME1 -> {
                g.fill(x, y, x + w, y + h, hovered ? 0xFFE2DED4 : 0xFFD6D2C8);
                bevelDouble(g, x, y, w, h, !pressed);
                if (primary && !pressed) {
                    dottedRect(g, x + 3, y + 3, w - 6, h - 6, 0xFF2A2A2A);
                }
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
            case BEVEL -> bevelDouble(g, x, y, w, h, false); // sunken well
            case LUNA -> outline(g, x, y, w, h, focused ? 0xFF2C66BD : 0xFF7F9DB9);
            case FLAT -> {
                outline(g, x, y, w, h, focused ? accent : flat(0xFFCDD1DD, 0xFF3A4150));
                g.fill(x + 1, y + h - 2, x + w - 1, y + h, focused ? accent : flat(0xFFCDD1DD, 0xFF3A4150));
            }
            case KDE2 -> outline(g, x, y, w, h, focused ? 0xFF1D4C80 : 0xFF8B857E);
            case GNOME1 -> bevelDouble(g, x, y, w, h, false); // sunken well
        }
    }

    /** A tab in a tab strip. */
    public void tab(final GuiGraphics g, final Font font, final int x, final int y, final int w, final int h,
                    final String label, final boolean active) {
        switch (form) {
            case BEVEL -> {
                g.fill(x, y, x + w, y + h + (active ? 2 : 0), 0xFFC0C0C0);
                bevelDouble(g, x, y, w, h + (active ? 2 : 0), true);
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
            case KDE2 -> {
                if (active) {
                    g.fillGradient(x, y, x + w, y + h, 0xFFF4F2EF, 0xFFD6D2CD);
                } else {
                    g.fillGradient(x, y, x + w, y + h, 0xFFDCD8D2, 0xFFC2BDB6);
                }
                outline(g, x, y, w, h, 0xFF8B857E);
            }
            case GNOME1 -> {
                g.fill(x, y, x + w, y + h + (active ? 2 : 0), active ? 0xFFD6D2C8 : 0xFFC4BFB2);
                bevelDouble(g, x, y, w, h + (active ? 2 : 0), true);
            }
        }
        final int tc = switch (form) {
            case LUNA -> active ? 0xFF2B5A16 : 0xFF22324D;
            case FLAT -> active ? accent : dim;
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
                bevelDouble(g, x, y, w, h, true);
            }
            case LUNA -> {
                g.fillGradient(x, y, x + w, y + h, 0xFFFDFDFF, 0xFFC2D2EE);
                outline(g, x, y, w, h, 0xFF93A9CC);
            }
            case FLAT -> roundedRect(g, x + 1, y, w - 2, h, flat(0xFFC8CDDA, 0xFF3E4653), 2, 2);
            case KDE2 -> {
                g.fillGradient(x, y, x + w, y + h, 0xFFF4F2EF, 0xFFCEC9C2);
                outline(g, x, y, w, h, 0xFF8B857E);
            }
            case GNOME1 -> {
                g.fill(x, y, x + w, y + h, 0xFFD6D2C8);
                bevelDouble(g, x, y, w, h, true);
            }
        }
    }

    /** A status/footer bar background. */
    public void statusBar(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        switch (form) {
            case BEVEL -> {
                g.fill(x, y, x + w, y + h, 0xFFC0C0C0);
                g.fill(x, y, x + w, y + 1, 0xFF808080); // top shadow line only (a status bar is not a raised box)
                g.fill(x, y + 1, x + w, y + 2, 0xFFFFFFFF);
            }
            case LUNA -> {
                g.fill(x, y, x + w, y + h, 0xFFECECF6);
                g.fill(x, y, x + w, y + 1, 0xFFB9C4DA);
            }
            case FLAT -> {
                g.fill(x, y, x + w, y + h, panelFill() == 0xFFFFFFFF ? 0xFFF1F2F6 : panelFill());
                g.fill(x, y, x + w, y + 1, edge());
            }
            case KDE2 -> {
                g.fillGradient(x, y, x + w, y + h, 0xFFE2DED8, 0xFFBFBAB3);
                g.fill(x, y, x + w, y + 1, 0xFF6F6A64);
            }
            case GNOME1 -> {
                g.fill(x, y, x + w, y + h, 0xFFCDC8BC);
                g.fill(x, y, x + w, y + 1, 0xFF85806F);
                g.fill(x, y + 1, x + w, y + 2, 0xFFF0EDE6);
            }
        }
    }

    private int panelFill() {
        return switch (form) {
            case BEVEL -> 0xFFC0C0C0;
            case LUNA -> 0xFFF4F6FC;
            case FLAT -> dark ? 0xFF242833 : 0xFFFFFFFF;
            case KDE2 -> 0xFFD6D2CD;
            case GNOME1 -> 0xFFCDC8BC;
        };
    }

    /** A flat-chrome fill for the given light colour, darkened when this is the dark Frames 11 variant. */
    private int flat(final int light, final int darkColor) {
        return dark ? darkColor : light;
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

    /**
     * The authentic Windows 95 two-tone 3D border: an outer ring (white/black) over an inner ring
     * (light-grey/dark-grey), giving the classic raised button or sunken well look that a single 1px bevel
     * only approximates. {@code raised} is a button/tab face; not raised is a field/well.
     */
    public static void bevelDouble(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                   final boolean raised) {
        final int outerLight = raised ? 0xFFFFFFFF : 0xFF808080;
        final int outerDark = raised ? 0xFF000000 : 0xFFFFFFFF;
        final int innerLight = raised ? 0xFFDFDFDF : 0xFF000000;
        final int innerDark = raised ? 0xFF808080 : 0xFFDFDFDF;
        // Outer ring.
        g.fill(x, y, x + w, y + 1, outerLight);
        g.fill(x, y, x + 1, y + h, outerLight);
        g.fill(x, y + h - 1, x + w, y + h, outerDark);
        g.fill(x + w - 1, y, x + w, y + h, outerDark);
        // Inner ring, inset by one pixel.
        g.fill(x + 1, y + 1, x + w - 1, y + 2, innerLight);
        g.fill(x + 1, y + 1, x + 2, y + h - 1, innerLight);
        g.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, innerDark);
        g.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, innerDark);
    }

    /** A left-to-right gradient fill (GuiGraphics.fillGradient is vertical only), stepped in 2px columns. */
    public static void hGradient(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                 final int left, final int right) {
        final int lr = left >> 16 & 0xFF;
        final int lg = left >> 8 & 0xFF;
        final int lb = left & 0xFF;
        final int rr = right >> 16 & 0xFF;
        final int rg = right >> 8 & 0xFF;
        final int rb = right & 0xFF;
        for (int i = 0; i < w; i += 2) {
            final float t = w <= 1 ? 0f : i / (float) (w - 1);
            final int cr = (int) (lr + (rr - lr) * t);
            final int cg = (int) (lg + (rg - lg) * t);
            final int cb = (int) (lb + (rb - lb) * t);
            g.fill(x + i, y, x + Math.min(w, i + 2), y + h, 0xFF000000 | cr << 16 | cg << 8 | cb);
        }
    }

    /**
     * A soft drop shadow behind a window, so it lifts off the wallpaper. Drawn just before the window frame at
     * the same depth: the offset fringe stays visible while the opaque body covers the rest. Skipped for the
     * flat 95 look would feel wrong, so all three skins get a shadow (subtler on 95).
     */
    public void windowShadow(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        final int spread = form == Form.BEVEL ? 3 : 5;
        final int base = form == Form.BEVEL ? 0x0E : 0x12;
        // Farther layers are lighter; nearer layers stack on top, so the fringe just outside the window is
        // darkest and it fades out toward the edge.
        for (int i = spread; i >= 1; i--) {
            final int a = Math.min(0xFF, base * (spread - i + 1));
            g.fill(x + i, y + i + 1, x + w + i, y + h + i + 1, a << 24);
        }
    }

    /** A 1px outline of a single colour. */
    public static void outline(final GuiGraphics g, final int x, final int y, final int w, final int h,
                               final int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** A 1px outline that skips the corner pixels, to match a rounded fill of radius {@code r}. */
    public static void roundedOutline(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                      final int color, final int r) {
        g.fill(x + r, y, x + w - r, y + 1, color);
        g.fill(x + r, y + h - 1, x + w - r, y + h, color);
        g.fill(x, y + r, x + 1, y + h - r, color);
        g.fill(x + w - 1, y + r, x + w, y + h - r, color);
    }

    /** A 1px dotted rectangle (every other pixel), used for the classic 95 focus ring. */
    public static void dottedRect(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                  final int color) {
        for (int i = 0; i < w; i += 2) {
            g.fill(x + i, y, x + i + 1, y + 1, color);
            g.fill(x + i, y + h - 1, x + i + 1, y + h, color);
        }
        for (int i = 0; i < h; i += 2) {
            g.fill(x, y + i, x + 1, y + i + 1, color);
            g.fill(x + w - 1, y + i, x + w, y + i + 1, color);
        }
    }

    private static int lighten(final int argb) {
        final int r = Math.min(255, (argb >> 16 & 0xFF) + 24);
        final int gg = Math.min(255, (argb >> 8 & 0xFF) + 24);
        final int b = Math.min(255, (argb & 0xFF) + 24);
        return 0xFF000000 | r << 16 | gg << 8 | b;
    }

    private static int darken(final int argb) {
        final int r = Math.max(0, (argb >> 16 & 0xFF) - 28);
        final int gg = Math.max(0, (argb >> 8 & 0xFF) - 28);
        final int b = Math.max(0, (argb & 0xFF) - 28);
        return 0xFF000000 | r << 16 | gg << 8 | b;
    }
}
