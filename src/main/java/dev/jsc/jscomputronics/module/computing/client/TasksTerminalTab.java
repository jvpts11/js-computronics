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

/**
 * The Tasks tab (Mainframe-only): a sub-tabbed view of Processes, Hardware, and connected Devices.
 */
final class TasksTerminalTab extends AbstractTerminalTab {

    // Mirror of ComputerTerminalScreen constants; update together if layout changes.
    private static final int TASK_OP_ROWS = 4;
    private static final String[] TASK_SUBTABS = {"Processes", "Hardware", "Devices"};

    TasksTerminalTab(final ComputerTerminalScreen screen, final ComputerTerminalMenu menu) {
        super(screen, menu);
    }

    @Override
    public void renderTabBg(final GuiGraphics g, final int x, final int y,
                            final int cx, final int cy, final int cw,
                            final int mouseX, final int mouseY) {
        final int sw = cw / 3;
        g.fill(cx + screen.taskSubTab * sw + 4, cy + 36, cx + (screen.taskSubTab + 1) * sw - 4, cy + 37, ACCENT());
        g.fill(cx, cy + 38, cx + cw, cy + 39, LINE());
        if (screen.taskSubTab == 0 || screen.taskSubTab == 1) {
            tilesBg(g, cx, cy + 44, cw);
        }
        if (screen.taskSubTab == 1) {
            inlineTrack(g, cx, cy + 90, cw, frac(menu.installedCpus(), menu.cpuSlots()), ACCENT2());
            inlineTrack(g, cx, cy + 102, cw, frac(menu.installedRam(), menu.ramSlots()), ACCENT2());
            inlineTrack(g, cx, cy + 114, cw, frac(menu.installedGpus(), menu.gpuSlots()), ACCENT());
            inlineTrack(g, cx, cy + 126, cw, frac(menu.installedDisks(), menu.diskSlots()), ACCENT());
        }
    }

    @Override
    public void renderTabLabels(final GuiGraphics g, final int cx, final int cy, final int cw) {
        final int sw = cw / 3;
        for (int i = 0; i < 3; i++) {
            g.drawCenteredString(font(), TASK_SUBTABS[i], cx + i * sw + sw / 2, cy + 28,
                    i == screen.taskSubTab ? ACCENT() : DIM());
        }
        switch (screen.taskSubTab) {
            case 0 -> tasksProcesses(g, cx, cy, cw);
            case 1 -> tasksHardware(g, cx, cy, cw);
            default -> tasksDevices(g, cx, cy, cw);
        }
    }

    private void tilesBg(final GuiGraphics g, final int x, final int y, final int cw) {
        final int tileW = (cw - 8) / 3;
        for (int i = 0; i < 3; i++) {
            final int tx = x + i * (tileW + 4);
            g.fill(tx, y, tx + tileW, y + 28, PANEL());
            g.fill(tx, y, tx + tileW, y + 1, LINE());
        }
    }

    private void tasksProcesses(final GuiGraphics g, final int cx, final int cy, final int cw) {
        final int tileW = (cw - 8) / 3;
        final List<OperationRecord> active = menu.activeOps();
        tile(g, cx, cy + 44, "IN FLIGHT", String.valueOf(active.size()), "");
        tile(g, cx + tileW + 4, cy + 44, "PENDING", String.valueOf(menu.pendingOps()), "");
        tile(g, cx + 2 * (tileW + 4), cy + 44, "DONE", fmt(menu.completedOps()), "");
        g.drawString(font(), "IN PROGRESS", cx, cy + 78, DIM(), false);
        if (active.isEmpty()) {
            g.drawString(font(), "Idle - no Operations running.", cx, cy + 90, DIM(), false);
            return;
        }
        for (int i = 0; i < TASK_OP_ROWS && i < active.size(); i++) {
            taskOpRow(g, cx, cy + 90 + i * 14, cw, active.get(i));
        }
        if (active.size() > TASK_OP_ROWS) {
            g.drawString(font(), "+" + (active.size() - TASK_OP_ROWS) + " more",
                    cx, cy + 90 + TASK_OP_ROWS * 14, DIM(), false);
        }
    }

    private void taskOpRow(final GuiGraphics g, final int cx, final int ry, final int cw,
                           final OperationRecord op) {
        final byte type = op.type();
        g.drawString(font(), opTypeLabel(type), cx + 4, ry, opTypeColor(type), false);
        final double f = op.requested() <= 0 ? 0 : Math.min(1.0, (double) op.moved() / op.requested());
        final String pct = (int) Math.round(f * 100) + "%";
        final int nameW = Math.max(0, cw - 44 - font().width(pct) - 8);
        g.drawString(font(), font().plainSubstrByWidth(op.name().getString(), nameW),
                cx + 44, ry, TEXT(), false);
        g.drawString(font(), pct, cx + cw - font().width(pct) - 4, ry, ACCENT(), false);
        track(g, cx + 4, ry + 9, cw - 8, f, ACCENT2());
    }

    private void tasksHardware(final GuiGraphics g, final int cx, final int cy, final int cw) {
        final int tileW = (cw - 8) / 3;
        tile(g, cx, cy + 44, "CAPACITY", fmt(menu.capacity()), "it/t");
        tile(g, cx + tileW + 4, cy + 44, "QUEUES", String.valueOf(menu.queues()), "");
        tile(g, cx + 2 * (tileW + 4), cy + 44, "RAM BUF", fmt(menu.ramBuffer()), "it");
        g.drawString(font(), "HARDWARE", cx, cy + 78, DIM(), false);
        barLabel(g, cx, cy + 90, cw, "CPU", menu.installedCpus() + "/" + menu.cpuSlots());
        barLabel(g, cx, cy + 102, cw, "RAM", menu.installedRam() + "/" + menu.ramSlots());
        barLabel(g, cx, cy + 114, cw, "GPU", menu.installedGpus() + "/" + menu.gpuSlots());
        barLabel(g, cx, cy + 126, cw, "Disk", menu.installedDisks() + "/" + menu.diskSlots());
    }

    private void tasksDevices(final GuiGraphics g, final int cx, final int cy, final int cw) {
        deviceRow(g, cx, cy + 48, cw, "Mainframe", 1);
        deviceRow(g, cx, cy + 62, cw, "Servers", menu.serverCount());
        deviceRow(g, cx, cy + 76, cw, "Personal Computers", menu.pcCount());
        deviceRow(g, cx, cy + 90, cw, "Subframes", menu.subframeCount());
        g.drawString(font(), "Network storage", cx, cy + 110, DIM(), false);
        final String st = fmt(menu.storageUsed()) + " / " + fmt(menu.storageCapacity());
        g.drawString(font(), st, cx + cw - font().width(st), cy + 110, TEXT(), false);
    }

    private void deviceRow(final GuiGraphics g, final int cx, final int y, final int cw,
                           final String name, final int count) {
        g.drawString(font(), name, cx + 4, y, TEXT(), false);
        final String c = String.valueOf(count);
        g.drawString(font(), c, cx + cw - font().width(c) - 4, y, count > 0 ? GREEN() : DIM(), false);
    }
}
