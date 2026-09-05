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

    /** The live operations followed by the recent log — so a craft in flight shows the moment it starts, not
     *  only once it has finished. Live ones come first; their status (PROCESSING/PENDING) tells them apart. */
    private List<OperationRecord> ops() {
        final List<OperationRecord> out = new java.util.ArrayList<>(menu.activeOps());
        out.addAll(menu.operationsLog());
        return out;
    }

    @Override
    public void renderTabBg(final GuiGraphics g, final int x, final int y,
                            final int cx, final int cy, final int cw,
                            final int mouseX, final int mouseY) {
        final List<OperationRecord> ops = ops();
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
        final List<OperationRecord> ops = ops();
        final int live = menu.activeOps().size();
        final String n = live > 0 ? live + " live / " + ops.size()
                : ops.size() + (ops.size() == 1 ? " op" : " ops");
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
            // Show "all" for an uncapped request, so a Long.MAX demand never renders as an absurd,
            // overflowing "9223372036854.8M" total.
            final String reqLabel = op.requested() >= 1_000_000_000L ? "all" : fmt(op.requested());
            final String sub = fmt(op.moved()) + " of " + reqLabel + "  " + statusLabel(op.status());
            g.drawString(font(), sub, cx + 24, cy + 106, statusColor(op.status()), false);
            // A craft carries its stages as sub-operations; show those (what it is made of, how far each is)
            // rather than provenance rows, which is what makes a multi-stage craft legible here.
            if (!op.subs().isEmpty()) {
                subRows(g, cx, cy + 118, op.subs());
            } else {
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
    }

    /** Up to two sub-operation rows ("stage  moved/planned") plus a "+N more" line, in the detail box. */
    private void subRows(final GuiGraphics g, final int cx, final int ry, final List<OperationRecord.SubRow> subs) {
        final int show = Math.min(2, subs.size());
        for (int i = 0; i < show; i++) {
            final OperationRecord.SubRow s = subs.get(i);
            final String name = font().plainSubstrByWidth(s.server(), 90);
            final String prog = s.moved() + "/" + s.planned();
            g.drawString(font(), name, cx + 6, ry + i * 10, TEXT(), false);
            g.drawString(font(), prog, cx + 100, ry + i * 10, subStateColor(s.state()), false);
        }
        if (subs.size() > show) {
            g.drawString(font(), "+" + (subs.size() - show) + " more stages", cx + 6, ry + show * 10, DIM(), false);
        }
    }

    private int subStateColor(final byte state) {
        return switch (state) {
            case OperationRecord.SubRow.SUB_COMPLETED -> statusColor(OperationRecord.STATUS_COMPLETED);
            case OperationRecord.SubRow.SUB_STREAMING -> statusColor(OperationRecord.STATUS_PROCESSING);
            case OperationRecord.SubRow.SUB_READING -> statusColor(OperationRecord.STATUS_WAITING);
            default -> DIM();
        };
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
