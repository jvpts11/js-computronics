/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import dev.jstech.computronics.menu.ComputerTerminalMenu;
import net.minecraft.client.gui.GuiGraphics;

/** The Network tab: virtual item grid drawn from the network snapshot. */
final class NetworkTerminalTab extends AbstractTerminalTab {

    // Grid constants — mirror ComputerTerminalScreen layout values; update together if layout changes.
    private static final int NET_ROWS = 4;
    private static final int NET_X = 68;
    private static final int DEPOSIT_W = 160; // NET_COLS * 18 - 2
    private static final int DEPOSIT_Y = 126; // NET_Y + NET_ROWS * 18 + 2
    private static final int SORT_X = 182;    // NET_X + TOOLBAR_W - SORT_W
    private static final int SORT_W = 46;
    private static final int TOOLBAR_Y = 36;

    NetworkTerminalTab(final ComputerTerminalScreen screen, final ComputerTerminalMenu menu) {
        super(screen, menu);
    }

    @Override
    public void renderTabBg(final GuiGraphics g, final int x, final int y,
                            final int cx, final int cy, final int cw,
                            final int mouseX, final int mouseY) {
        gridBg(g, x, y, 0, NET_ROWS);
    }

    @Override
    public void renderTabLabels(final GuiGraphics g, final int cx, final int cy, final int cw) {
        g.drawString(font(), "NETWORK", cx, cy + 20, DIM(), false);
        final int shown = visibleItems().size();
        final String t = shown + (shown == 1 ? " item" : " items");
        g.drawString(font(), t, cx + cw - font().width(t), cy + 20, DIM(), false);
        g.drawCenteredString(font(), screen.sortByQuantity ? "Qty" : "Name",
                SORT_X + SORT_W / 2, TOOLBAR_Y + 3, ACCENT());
        final boolean holding = !menu.getCarried().isEmpty();
        g.drawCenteredString(font(), "DEPOSIT TO NETWORK",
                NET_X + DEPOSIT_W / 2, DEPOSIT_Y + 3, holding ? ACCENT() : DIM());
    }
}
