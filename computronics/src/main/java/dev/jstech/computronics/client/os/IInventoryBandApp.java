/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

/**
 * A desktop program that shows the player's real inventory inside its window: a framed band of the 36
 * container slots, laid out by the desktop over the app's content each frame. The app paints the slot
 * backgrounds where it says the cells are; the desktop draws the items and drives the cursor, drag and
 * shift-click through the vanilla container, so what the player carries is the same stack everywhere.
 */
public interface IInventoryBandApp extends IDesktopApp {

    /** Claims this window as the live instance its server replies are routed to; called when it comes to the front. */
    void markActive();

    /** The content-local x of inventory column {@code col}'s cell (where the item is drawn). */
    int invCellContentX(int col);

    /** The content-local y of inventory row {@code row}'s cell, for a content area {@code contentHeight} tall. */
    int invCellContentY(int row, int contentHeight);

    /** The content-local y just past the hotbar row, for a content area {@code contentHeight} tall. */
    int invBandBottom(int contentHeight);
}
