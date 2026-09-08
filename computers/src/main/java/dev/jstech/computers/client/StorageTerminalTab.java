/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.client;

import dev.jstech.computers.menu.ComputerTerminalMenu;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The Storage tab: per-disk public/private slider band above the local-storage item grid,
 * shifted down by {@link #STORAGE_SHIFT} so the band never overlaps the grid.
 */
final class StorageTerminalTab extends AbstractTerminalTab {

    // Layout constants, mirroring ComputerTerminalScreen; update together if layout changes.
    private static final int STORAGE_SHIFT = 8;
    private static final int STORAGE_NET_ROWS = 3;
    private static final int SLIDER_TRACK0_DY = 1;
    private static final int SLIDER_ROW_PITCH = 9;
    private static final int SLIDER_TRACK_H = 7;
    private static final int SLIDER_TRACK_LX = 24;
    private static final int SLIDER_HANDLE_W = 3;
    private static final int NET_X = 68;
    private static final int DEPOSIT_W = 160;
    private static final int DEPOSIT_Y = 126;
    private static final int SORT_X = 182;
    private static final int SORT_W = 46;
    private static final int TOOLBAR_Y = 36;

    StorageTerminalTab(final ComputerTerminalScreen screen, final ComputerTerminalMenu menu) {
        super(screen, menu);
    }

    @Override
    public void renderTabBg(final GuiGraphics g, final int x, final int y,
                            final int cx, final int cy, final int cw,
                            final int mouseX, final int mouseY) {
        sliderBandBg(g, cx, cy, cw);
        gridBg(g, x, y, STORAGE_SHIFT, STORAGE_NET_ROWS);
    }

    @Override
    public void renderTabLabels(final GuiGraphics g, final int cx, final int cy, final int cw) {
        sliderBandLabels(g, cx, cy, cw);
        final int shown = visibleItems().size();
        final String t = shown + (shown == 1 ? " type" : " types");
        g.drawString(font(), t, cx + cw - font().width(t), TOOLBAR_Y + STORAGE_SHIFT + 3, DIM(), false);
        g.drawCenteredString(font(), screen.sortByQuantity ? "Qty" : "Name",
                SORT_X + SORT_W / 2, TOOLBAR_Y + STORAGE_SHIFT + 3, ACCENT());
        final boolean holding = !menu.getCarried().isEmpty();
        g.drawCenteredString(font(), "DEPOSIT TO STORAGE",
                NET_X + DEPOSIT_W / 2, DEPOSIT_Y + STORAGE_SHIFT + 3, holding ? ACCENT() : DIM());
    }

    // Slider band rendering

    private void sliderBandBg(final GuiGraphics g, final int cx, final int cy, final int cw) {
        final int bandTop = cy + 18;
        if (!menu.storageHasSlider()) {
            g.fill(cx, bandTop, cx + cw, bandTop + 11, PANEL());
            g.fill(cx, bandTop, cx + 2, bandTop + 11, GREEN());
            return;
        }
        final int disks = menu.diskCount();
        final int trackX = cx + SLIDER_TRACK_LX;
        final int trackW = cw - SLIDER_TRACK_LX - 2;
        for (int d = 0; d < disks; d++) {
            final int ty = bandTop + SLIDER_TRACK0_DY + d * SLIDER_ROW_PITCH;
            sliderTrackBg(g, trackX, ty, trackW, d);
        }
    }

    private void sliderTrackBg(final GuiGraphics g, final int tx, final int ty, final int tw, final int disk) {
        final int permille = sliderValue(disk);
        final boolean empty = menu.diskCapacityWeight(disk) <= 0L;
        g.fill(tx, ty, tx + tw, ty + SLIDER_TRACK_H, TRACK());
        g.fill(tx, ty, tx + tw, ty + 1, LINE());
        if (empty) {
            return;
        }
        final int span = tw - SLIDER_HANDLE_W;
        final int handleX = tx + Math.round(span * (permille / 1000.0f));
        if (handleX > tx + 1) {
            g.fill(tx + 1, ty + 1, handleX, ty + SLIDER_TRACK_H - 1, GREEN());
        }
        if (handleX + SLIDER_HANDLE_W < tx + tw - 1) {
            g.fill(handleX + SLIDER_HANDLE_W, ty + 1, tx + tw - 1, ty + SLIDER_TRACK_H - 1, PANEL());
        }
        for (int i = 0; i <= 4; i++) {
            final int tickX = tx + Math.round(span * (i / 4.0f)) + SLIDER_HANDLE_W / 2;
            g.fill(tickX, ty + SLIDER_TRACK_H, tickX + 1, ty + SLIDER_TRACK_H + 1, LINE());
        }
        final int handleColor = screen.draggingSliderDisk == disk ? 0xFFFFFFFF : ACCENT();
        g.fill(handleX, ty - 2, handleX + SLIDER_HANDLE_W, ty + SLIDER_TRACK_H + 2, handleColor);
    }

    private void sliderBandLabels(final GuiGraphics g, final int cx, final int cy, final int cw) {
        final int bandTop = cy + 18;
        if (!menu.storageHasSlider()) {
            g.drawString(font(), "PUBLIC · NETWORK STORAGE", cx + 4, bandTop + 2, GREEN(), false);
            return;
        }
        final int disks = menu.diskCount();
        final int trackX = cx + SLIDER_TRACK_LX;
        for (int d = 0; d < disks; d++) {
            final int ty = bandTop + SLIDER_TRACK0_DY + d * SLIDER_ROW_PITCH;
            g.drawString(font(), String.valueOf((char) ('A' + d)), cx + 2, ty, DIM(), false);
            if (menu.diskCapacityWeight(d) <= 0L) {
                g.drawString(font(), "no disk", trackX + 4, ty, DIM(), false);
                continue;
            }
            final int permille = sliderValue(d);
            final String readout = (permille / 10) + "% pub";
            g.drawString(font(), readout, cx + cw - font().width(readout), ty, GREEN(), false);
        }
    }
}
