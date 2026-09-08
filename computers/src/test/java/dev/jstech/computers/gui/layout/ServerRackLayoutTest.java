/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computers.
 */
package dev.jstech.computers.gui.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.jstech.core.gui.layout.GuiLayout;
import org.junit.jupiter.api.Test;

class ServerRackLayoutTest {

    @Test
    void layout_hasNoSolidOverlaps() {
        final GuiLayout l = ServerRackLayout.layout();
        assertTrue(l.overlaps().isEmpty(), "overlapping elements: " + l.overlaps());
    }

    @Test
    void layout_keepsEveryElementInBounds() {
        final GuiLayout l = ServerRackLayout.layout();
        assertTrue(l.outOfBounds().isEmpty(), "out-of-bounds elements: " + l.outOfBounds());
    }

    @Test
    void layout_isClean() {
        assertTrue(ServerRackLayout.layout().isClean());
    }

    @Test
    void rows_startBelowTheHeaderAndEndAboveTheInventory() {
        assertTrue(ServerRackLayout.ROW_Y0 >= ServerRackLayout.HEADER_Y + ServerRackLayout.HEADER_H,
                "the first rack row must clear the header bar");
        final int lastRowBottom = ServerRackLayout.rowY(ServerRackLayout.ROWS - 1) + ServerRackLayout.SLOT;
        assertTrue(lastRowBottom <= ServerRackLayout.INV_Y,
                "the last rack row (" + lastRowBottom + ") must end above the player inventory");
    }

    @Test
    void frontSlots_endBeforeTheStatusColumn() {
        final int lastSlotEnd = ServerRackLayout.frontSlotX(ServerRackLayout.FRONT_SLOTS - 1)
                + ServerRackLayout.SLOT;
        assertTrue(lastSlotEnd <= ServerRackLayout.STATUS_X,
                "front slots (" + lastSlotEnd + ") must not run into the status text");
    }

    @Test
    void rowY_advancesByThePitch() {
        assertEquals(ServerRackLayout.ROW_Y0, ServerRackLayout.rowY(0));
        assertEquals(ServerRackLayout.ROW_Y0 + ServerRackLayout.ROW_PITCH, ServerRackLayout.rowY(1));
    }
}
