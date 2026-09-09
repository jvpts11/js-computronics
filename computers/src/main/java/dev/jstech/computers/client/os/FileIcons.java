/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client.os;

import dev.jstech.computers.gui.layout.FilesLayout;
import dev.jstech.core.client.gui.component.Draw;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The small icons every program draws beside a file: a folder with a tab, a page with a folded
 * corner coloured by what the page holds, a cartridge for what cannot be opened.
 *
 * <p>They live in one place so the explorer, the studios' trees and a picker all show the same
 * thing for the same file, and a new kind of file gets its icon once.
 */
public final class FileIcons {

    /** What an icon stands for. */
    public enum Kind { UP, FOLDER, HOME, IQL, DOC, DAT, EXE, PKG, INF, BIN, CFG, LOG, CRAFT, SOURCE, PROGRAM, BUNDLE }

    private FileIcons() {
    }

    /** The icon for a file with that extension; a language's own files get the source and program pages. */
    public static Kind kindOf(final String ext) {
        final String lower = ext == null ? "" : ext.toLowerCase(Locale.ROOT);
        final var language = dev.jstech.core.JsCore.languages().byExtension(lower);
        if (language != null) {
            return language.sourceExtensions().contains(lower) ? Kind.SOURCE : Kind.PROGRAM;
        }
        return switch (lower) {
            case "iql" -> Kind.IQL;
            case "dat" -> Kind.DAT;
            case "exe", "sh" -> Kind.EXE;
            case "pkg" -> Kind.PKG;
            case "inf", "ini" -> Kind.INF;
            case "bin", "sys", "fon" -> Kind.BIN;
            case "cfg" -> Kind.CFG;
            case "log" -> Kind.LOG;
            case "craft" -> Kind.CRAFT;
            case "cpk", "sln", "canproj" -> Kind.BUNDLE;
            default -> Kind.DOC;
        };
    }

    /** The icon for a path, by its extension, or a folder's when {@code directory}. */
    public static Kind kindOfPath(final String path, final boolean directory) {
        if (directory) {
            return Kind.FOLDER;
        }
        final int dot = path.lastIndexOf('.');
        return kindOf(dot >= 0 && dot < path.length() - 1 ? path.substring(dot + 1) : "");
    }

    /** Draws the icon with its top-left at ({@code x}, {@code y}); it is {@link FilesLayout#ICON_W} wide and nine tall. */
    public static void draw(final GuiGraphics g, final int x, final int y, final Kind type) {
        final int w = FilesLayout.ICON_W;
        switch (type) {
            case UP -> {
                g.fill(x, y + 1, x + w, y + 9, 0xFFA8A8A8);
                g.fill(x, y + 1, x + w, y + 2, 0xFFD8D8D8);
                Draw.outline(g, x, y + 1, w, 8, 0xFF707070);
                g.fill(x + 5, y + 3, x + 7, y + 8, 0xFF303030);
                g.fill(x + 3, y + 4, x + 9, y + 5, 0xFF303030);
            }
            case FOLDER -> {
                g.fill(x, y + 2, x + 5, y, 0xFFFFE9A8);
                g.fill(x, y + 2, x + w, y + 9, 0xFFF4C842);
                g.fill(x, y + 2, x + w, y + 3, 0xFFFFF3C4);
                Draw.outline(g, x, y, w, 9, 0xFF9A7B16);
            }
            case HOME -> {
                // A disk drive: a slab with an activity lamp.
                g.fill(x, y + 1, x + w, y + 9, 0xFF8B93A4);
                g.fill(x + 1, y + 2, x + w - 1, y + 8, 0xFFC7CDDA);
                g.fill(x + 2, y + 3, x + w - 2, y + 4, 0xFFEDF0F6);
                g.fill(x + w - 4, y + 6, x + w - 2, y + 8, 0xFF49E07A);
            }
            case BIN -> {
                // A removable medium or an opaque installer file: a dark cartridge.
                g.fill(x + 1, y, x + w - 1, y + 9, 0xFF2E3238);
                g.fill(x + 3, y + 2, x + w - 3, y + 4, 0xFFB8BEC8);
                Draw.outline(g, x + 1, y, w - 2, 9, 0xFF1C1F24);
            }
            case IQL -> doc(g, x, y, 0xFFA9D4FF, 0xFF3A72B0);
            case DAT -> doc(g, x, y, 0xFFBDEEC0, 0xFF4F9B53);
            case EXE -> {
                doc(g, x, y, 0xFFDDE2EC, 0xFF3A4256);
                g.fill(x + 4, y + 3, x + 6, y + 7, 0xFF3A4256);   // a play glyph
                g.fill(x + 6, y + 4, x + 8, y + 6, 0xFF3A4256);
            }
            case PKG -> doc(g, x, y, 0xFFE8DDB5, 0xFF9C7A2B);
            case INF -> doc(g, x, y, 0xFFEDEDED, 0xFF8A93A6);
            case CFG -> doc(g, x, y, 0xFFE3E0F5, 0xFF6C5FB0);
            case LOG -> doc(g, x, y, 0xFFF0E6D6, 0xFFA0865A);
            case CRAFT -> doc(g, x, y, 0xFFFFD9B0, 0xFFC26A1A);
            // Something a person writes: a page with two lines of writing on it.
            case SOURCE -> {
                doc(g, x, y, 0xFFCFE6D8, 0xFF2E7D5B);
                g.fill(x + 3, y + 4, x + 8, y + 5, 0xFF2E7D5B);
                g.fill(x + 3, y + 6, x + 7, y + 7, 0xFF2E7D5B);
            }
            // Something a machine runs: the same page with the play mark an installer wears.
            case PROGRAM -> {
                doc(g, x, y, 0xFFD8E6CF, 0xFF4C7D2E);
                g.fill(x + 4, y + 3, x + 6, y + 8, 0xFF4C7D2E);
                g.fill(x + 6, y + 4, x + 8, y + 7, 0xFF4C7D2E);
            }
            // Something with other things inside it: a page with a band across it, like a parcel.
            case BUNDLE -> {
                doc(g, x, y, 0xFFE6DCCF, 0xFF7D5B2E);
                g.fill(x + 1, y + 5, x + 10, y + 6, 0xFF7D5B2E);
                g.fill(x + 5, y + 2, x + 6, y + 9, 0xFF7D5B2E);
            }
            case DOC -> doc(g, x, y, 0xFFDFE3EA, 0xFF8A93A6);
        }
    }

    private static void doc(final GuiGraphics g, final int x, final int y, final int fill, final int edge) {
        final int w = FilesLayout.ICON_W;
        g.fill(x + 1, y, x + w, y + 9, fill);
        g.fill(x + w - 3, y, x + w, y + 3, 0xFFFFFFFF);
        Draw.outline(g, x + 1, y, w - 1, 9, edge);
    }
}
