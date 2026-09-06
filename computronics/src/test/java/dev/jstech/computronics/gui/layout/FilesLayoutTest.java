/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.gui.layout;

import dev.jstech.core.gui.layout.GuiLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class FilesLayoutTest {

    @Test
    public void layout_isCleanAtTheDefaultSize() {
        final GuiLayout l = FilesLayout.layout(FilesLayout.DEFAULT_W, FilesLayout.DEFAULT_H);
        assertTrue(l.isClean(), l.overlaps() + " " + l.outOfBounds());
    }

    @Test
    public void layout_isCleanAtTheMinimumSize() {
        final GuiLayout l = FilesLayout.layout(FilesLayout.MIN_W, FilesLayout.MIN_H);
        assertTrue(l.isClean(), l.overlaps() + " " + l.outOfBounds());
    }

    @Test
    public void layout_isCleanWhenMaximised() {
        final GuiLayout l = FilesLayout.layout(420, 300);
        assertTrue(l.isClean(), l.overlaps() + " " + l.outOfBounds());
    }

    @Test
    public void columns_neverRunIntoEachOther() {
        for (final int width : new int[]{FilesLayout.MIN_W, FilesLayout.DEFAULT_W, 420}) {
            final GuiLayout l = FilesLayout.columns(width);
            assertTrue(l.isClean(), width + ": " + l.overlaps() + " " + l.outOfBounds());
            assertTrue(FilesLayout.nameMaxW(width) >= 40, "a name gets room at " + width);
        }
    }

    @Test
    public void toolbar_addressSitsBetweenTheButtonsAndTheSearch() {
        final int width = FilesLayout.DEFAULT_W;
        assertTrue(FilesLayout.addressX() > FilesLayout.navX(2) + FilesLayout.NAV_W);
        assertTrue(FilesLayout.addressX() + FilesLayout.addressW(width) < FilesLayout.searchX(width));
        assertTrue(FilesLayout.searchX(width) + FilesLayout.SEARCH_W < FilesLayout.viewX(width));
        assertEquals(width - 2, FilesLayout.viewX(width) + FilesLayout.NAV_W);
    }

    @Test
    public void tree_isWideEnoughForADriveLabel() {
        // "Local Disk (C:)" is 15 characters at the desktop's ~6px glyph, plus the icon.
        assertTrue(FilesLayout.TREE_W >= FilesLayout.ICON_W + 3 + 15 * 4 + 4);
    }

    @Test
    public void list_showsAtLeastOneRowAtTheMinimumHeight() {
        assertTrue(FilesLayout.visibleRows(FilesLayout.MIN_H) >= 3);
        assertTrue(FilesLayout.listY() + FilesLayout.listH(FilesLayout.MIN_H) <= FilesLayout.statusY(FilesLayout.MIN_H));
    }
}
