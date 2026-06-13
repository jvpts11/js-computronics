/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.menu.ComputerTerminalMenu;
import dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord;
import net.minecraft.client.gui.GuiGraphics;

import java.util.List;

/** The Operations tab: a scrollable log of recent network operations with a provenance detail pane. */
final class OpsTerminalTab extends AbstractTerminalTab {

    // Mirror of ComputerTerminalScreen.OPS_ROWS; update together if layout changes.
    private static final int OPS_ROWS = 4;

    OpsTerminalTab(final ComputerTerminalScreen screen, final ComputerTerminalMenu menu) {
        super(screen, menu);
    }

    @Override
    public void renderTabBg(final GuiGraphics g, final int x, final int y,
                            final int cx, final int cy, final int cw,
                            final int mouseX, final int mouseY) {
        final List<OperationRecord> ops = menu.operationsLog();
        final int start = clampOpScroll(ops.size());
        for (int i = 0; i < OPS_ROWS && start + i < ops.size(); i++) {
            final int ry = cy + 32 + i * 12;
            final boolean sel = (start + i) == screen.selectedOp;
            g.fill(cx, ry, cx + cw, ry + 11, sel ? TAB_ON() : PANEL());
            if (sel) {
                g.fill(cx, ry, cx + 2, ry + 11, ACCENT());
            }
        }
        if (ops.size() > OPS_ROWS) {
            final int trackTop = cy + 32;
            final int trackH = OPS_ROWS * 12 - 1;
            final int maxOff = ops.size() - OPS_ROWS;
            final int thumbH = Math.max(8, trackH * OPS_ROWS / ops.size());
            final int thumbY = trackTop + (trackH - thumbH) * start / maxOff;
            g.fill(cx + cw - 2, trackTop, cx + cw, trackTop + trackH, LINE());
            g.fill(cx + cw - 2, thumbY, cx + cw, thumbY + thumbH, ACCENT());
        }
        g.fill(cx, cy + 90, cx + cw, cy + 140, PANEL());
        g.fill(cx, cy + 90, cx + cw, cy + 91, LINE());
        if (screen.selectedOp >= 0 && screen.selectedOp < ops.size()) {
            drawDataIcon(g, ops.get(screen.selectedOp).key(), -1L, cx + 5, cy + 96);
        }
    }

    @Override
    public void renderTabLabels(final GuiGraphics g, final int cx, final int cy, final int cw) {
        g.drawString(font(), "OPERATIONS", cx, cy + 20, DIM(), false);
        final List<OperationRecord> ops = menu.operationsLog();
        final String n = ops.size() + (ops.size() == 1 ? " op" : " ops");
        g.drawString(font(), n, cx + cw - font().width(n), cy + 20, DIM(), false);
        if (ops.isEmpty()) {
            g.drawString(font(), "No operations yet.", cx, cy + 40, DIM(), false);
            return;
        }
        final int start = clampOpScroll(ops.size());
        for (int i = 0; i < OPS_ROWS && start + i < ops.size(); i++) {
            opListRow(g, cx, cy + 34 + i * 12, cw, ops.get(start + i));
        }
        if (screen.selectedOp >= 0 && screen.selectedOp < ops.size()) {
            final OperationRecord op = ops.get(screen.selectedOp);
            g.drawString(font(), op.name().getString(), cx + 24, cy + 96, TEXT(), false);
            final String sub = fmt(op.moved()) + " of " + fmt(op.requested()) + "  " + statusLabel(op.status());
            g.drawString(font(), sub, cx + 24, cy + 106, statusColor(op.status()), false);
            // At most two provenance rows fit in the box; if there are more sources,
            // the second row is replaced by a one-line summary so nothing overflows.
            final List<OperationRecord.MoveRow> mv = op.moves();
            if (!mv.isEmpty()) {
                moveRow(g, cx, cy + 118, mv.get(0));
                if (mv.size() == 2) {
                    moveRow(g, cx, cy + 128, mv.get(1));
                } else if (mv.size() > 2) {
                    g.drawString(font(), "+" + (mv.size() - 1) + " more sources", cx + 6, cy + 128, DIM(), false);
                }
            }
        }
    }

    private void opListRow(final GuiGraphics g, final int cx, final int ry, final int cw,
                           final OperationRecord op) {
        final byte type = op.type();
        g.drawString(font(), opTypeLabel(type), cx + 4, ry, opTypeColor(type), false);
        final String q = fmt(op.moved());
        final int nameW = Math.max(0, cw - 44 - font().width(q) - 8);
        final String name = font().plainSubstrByWidth(op.name().getString(), nameW);
        g.drawString(font(), name, cx + 44, ry, TEXT(), false);
        g.drawString(font(), q, cx + cw - font().width(q) - 4, ry, statusColor(op.status()), false);
    }
}
