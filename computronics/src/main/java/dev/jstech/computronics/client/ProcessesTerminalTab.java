/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client;

import dev.jstech.computronics.menu.ComputerTerminalMenu;
import dev.jstech.computronics.operation.payload.ProcessActionPayload;
import dev.jstech.computronics.operation.payload.ProcessListPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The Processes tab: a task manager for the CURRENT computer (the host the terminal is attached to). It lists
 * that computer's processes — the IQL Engine service and each of its jobs — and lets the player manage the
 * selected one from a small toolbar: Start/Stop/Restart a service, End (pause) / Restart a job. A computer
 * with no service (a Personal Computer today) shows "no processes running".
 */
final class ProcessesTerminalTab extends AbstractTerminalTab {

    // The terminal draws its own header bar over cy..cy+17, so the tab's content starts below it.
    private static final int SUBHEAD_Y = 22;
    private static final int TOOLBAR_Y = 20;
    private static final int BTN_H = 14;
    private static final int LIST_Y = 40;
    private static final int ROW_H = 20;

    private int selected;
    private int lastCx;
    private int lastCy;
    private int lastCw;

    ProcessesTerminalTab(final ComputerTerminalScreen screen, final ComputerTerminalMenu menu) {
        super(screen, menu);
    }

    @Override
    public void renderTabBg(final GuiGraphics g, final int x, final int y,
                            final int cx, final int cy, final int cw,
                            final int mouseX, final int mouseY) {
        lastCx = cx;
        lastCy = cy;
        lastCw = cw;
        final var processes = menu.processes();
        clampSelection(processes.size());
        int row = cy + LIST_Y;
        for (int i = 0; i < processes.size(); i++) {
            g.fill(cx, row, cx + cw, row + ROW_H - 2, i == selected ? HOVER() : PANEL());
            if (i == selected) {
                g.fill(cx, row, cx + 2, row + ROW_H - 2, ACCENT()); // selection marker
            }
            row += ROW_H;
        }
    }

    @Override
    public void renderTabLabels(final GuiGraphics g, final int cx, final int cy, final int cw) {
        final var processes = menu.processes();
        clampSelection(processes.size());

        g.drawString(font(), "SERVICES & JOBS", cx, cy + SUBHEAD_Y, DIM(), false);

        // Toolbar: two context buttons for the selected process (disabled when the list is empty).
        final boolean any = !processes.isEmpty();
        final ProcessListPayload.ProcessLine sel = any ? processes.get(selected) : null;
        final boolean service = sel != null && sel.kind() == ProcessListPayload.KIND_SERVICE;
        final boolean running = sel != null && (sel.state().equals("running") || sel.state().equals("active"));
        final String primary = sel == null ? "—" : service ? (running ? "Stop" : "Start") : "End";
        final int primaryColor = service ? TEXT() : RED();
        final int[] b1 = primaryRect(cx, cy, cw);
        final int[] b2 = restartRect(cx, cy, cw);
        button(g, b1[0], b1[1], b1[2], b1[3], primary, any ? primaryColor : DIM(), any);
        button(g, b2[0], b2[1], b2[2], b2[3], "Restart", any ? ACCENT() : DIM(), any);

        if (!any) {
            g.drawString(font(), "no processes running", cx, cy + LIST_Y + 6, DIM(), false);
            return;
        }

        int row = cy + LIST_Y;
        for (int i = 0; i < processes.size(); i++) {
            final ProcessListPayload.ProcessLine p = processes.get(i);
            if (p.kind() == ProcessListPayload.KIND_SERVICE) {
                cogIcon(g, cx + 3, row + 3);
            } else {
                jobIcon(g, cx + 3, row + 3);
            }
            g.drawString(font(), p.name(), cx + 18, row + 2, TEXT(), false);
            final String state = p.state();
            g.drawString(font(), state, cx + cw - 4 - font().width(state), row + 2, stateColor(state), false);
            g.drawString(font(), p.detail(), cx + 18, row + 12, DIM(), false);
            row += ROW_H;
        }
    }

    @Override
    public boolean onMouseClicked(final double mouseX, final double mouseY, final int button) {
        if (button != 0) {
            return false;
        }
        final var processes = menu.processes();
        if (processes.isEmpty()) {
            return false;
        }
        clampSelection(processes.size());
        // Toolbar buttons act on the selected process.
        final int[] b1 = primaryRect(lastCx, lastCy, lastCw);
        final int[] b2 = restartRect(lastCx, lastCy, lastCw);
        if (inRect(mouseX, mouseY, b1[0], b1[1], b1[2], b1[3])) {
            act(processes.get(selected), primaryAction(processes.get(selected)));
            return true;
        }
        if (inRect(mouseX, mouseY, b2[0], b2[1], b2[2], b2[3])) {
            act(processes.get(selected), ProcessActionPayload.ACTION_RESTART);
            return true;
        }
        // Click a row to select it.
        int row = lastCy + LIST_Y;
        for (int i = 0; i < processes.size(); i++) {
            if (inRect(mouseX, mouseY, lastCx, row, lastCw, ROW_H - 2)) {
                selected = i;
                return true;
            }
            row += ROW_H;
        }
        return false;
    }

    private static int primaryAction(final ProcessListPayload.ProcessLine p) {
        if (p.kind() == ProcessListPayload.KIND_JOB) {
            return ProcessActionPayload.ACTION_END;
        }
        final boolean running = p.state().equals("running") || p.state().equals("active");
        return running ? ProcessActionPayload.ACTION_STOP : ProcessActionPayload.ACTION_START;
    }

    private void act(final ProcessListPayload.ProcessLine p, final int action) {
        PacketDistributor.sendToServer(new ProcessActionPayload(menu.hostPos(), p.kind(), p.name(), action));
    }

    private void clampSelection(final int size) {
        if (selected >= size) {
            selected = Math.max(0, size - 1);
        }
    }

    private int[] restartRect(final int cx, final int cy, final int cw) {
        return new int[]{cx + cw - 46, cy + TOOLBAR_Y, 44, BTN_H};
    }

    private int[] primaryRect(final int cx, final int cy, final int cw) {
        return new int[]{cx + cw - 46 - 36, cy + TOOLBAR_Y, 34, BTN_H};
    }

    private void button(final GuiGraphics g, final int x, final int y, final int w, final int h,
                        final String label, final int textColor, final boolean enabled) {
        g.fill(x, y, x + w, y + h, enabled ? PANEL() : TRACK());
        g.fill(x, y, x + w, y + 1, LINE());
        g.fill(x, y + h - 1, x + w, y + h, LINE());
        g.fill(x, y, x + 1, y + h, LINE());
        g.fill(x + w - 1, y, x + w, y + h, LINE());
        g.drawCenteredString(font(), label, x + w / 2, y + (h - 8) / 2, textColor);
    }

    private void cogIcon(final GuiGraphics g, final int x, final int y) {
        final int c = ACCENT();
        g.fill(x + 5, y, x + 7, y + 2, c);
        g.fill(x + 5, y + 10, x + 7, y + 12, c);
        g.fill(x, y + 5, x + 2, y + 7, c);
        g.fill(x + 10, y + 5, x + 12, y + 7, c);
        g.fill(x + 3, y + 3, x + 9, y + 5, c);
        g.fill(x + 3, y + 7, x + 9, y + 9, c);
        g.fill(x + 3, y + 3, x + 5, y + 9, c);
        g.fill(x + 7, y + 3, x + 9, y + 9, c);
    }

    private void jobIcon(final GuiGraphics g, final int x, final int y) {
        final int c = ACCENT2();
        g.fill(x + 2, y + 2, x + 10, y + 10, c);
        g.fill(x + 4, y + 4, x + 8, y + 8, TRACK());
    }

    private int stateColor(final String state) {
        return switch (state) {
            case "running", "active" -> GREEN();
            case "paused", "idle" -> AMBER();
            case "stopped" -> RED();
            default -> DIM();
        };
    }
}
