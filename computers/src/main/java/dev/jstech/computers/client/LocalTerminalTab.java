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

/** The Local tab: capacity/queue/RAM tiles and five hardware-fill bars. */
final class LocalTerminalTab extends AbstractTerminalTab {

    LocalTerminalTab(final ComputerTerminalScreen screen, final ComputerTerminalMenu menu) {
        super(screen, menu);
    }

    @Override
    public void renderTabBg(final GuiGraphics g, final int x, final int y,
                            final int cx, final int cy, final int cw,
                            final int mouseX, final int mouseY) {
        final int tileW = (cw - 8) / 3;
        for (int i = 0; i < 3; i++) {
            final int tx = cx + i * (tileW + 4);
            g.fill(tx, cy + 26, tx + tileW, cy + 52, PANEL());
            g.fill(tx, cy + 26, tx + tileW, cy + 27, LINE());
        }
        inlineTrack(g, cx, cy + 70, cw, frac(menu.installedCpus(), menu.cpuSlots()), ACCENT2());
        inlineTrack(g, cx, cy + 82, cw, frac(menu.installedRam(), menu.ramSlots()), ACCENT2());
        inlineTrack(g, cx, cy + 94, cw, frac(menu.installedGpus(), menu.gpuSlots()), ACCENT());
        inlineTrack(g, cx, cy + 106, cw, frac(menu.installedDisks(), menu.diskSlots()), ACCENT());
        final long cap = menu.storageCapacity();
        final double sf = cap <= 0 ? 0 : Math.min(1.0, (double) menu.storageUsed() / cap);
        inlineTrack(g, cx, cy + 122, cw, sf, cap <= 0 ? DIM() : (sf > 0.9 ? RED() : GREEN()));
    }

    @Override
    public void renderTabLabels(final GuiGraphics g, final int cx, final int cy, final int cw) {
        final int tileW = (cw - 8) / 3;
        tile(g, cx + 0 * (tileW + 4), cy + 26, "CAPACITY", fmt(menu.capacity()), "it/t");
        tile(g, cx + 1 * (tileW + 4), cy + 26, "QUEUES", String.valueOf(menu.queues()), "");
        tile(g, cx + 2 * (tileW + 4), cy + 26, "RAM BUF", fmt(menu.ramBuffer()), "it");

        g.drawString(font(), "HARDWARE", cx, cy + 56, DIM(), false);
        final int net = menu.networkLinkState();
        final String netStr = net == 2 ? "conflict"
                : net == 1 ? menu.serverCount() + " servers" : "offline";
        g.drawString(font(), netStr, cx + cw - font().width(netStr), cy + 56, net == 2 ? RED() : DIM(), false);

        barLabel(g, cx, cy + 70, cw, "CPU", menu.installedCpus() + "/" + menu.cpuSlots());
        barLabel(g, cx, cy + 82, cw, "RAM", menu.installedRam() + "/" + menu.ramSlots());
        barLabel(g, cx, cy + 94, cw, "GPU", menu.installedGpus() + "/" + menu.gpuSlots());
        barLabel(g, cx, cy + 106, cw, "Disk", menu.installedDisks() + "/" + menu.diskSlots());

        final String store = menu.storageCapacity() <= 0 ? "no disk"
                : fmt(menu.storageUsed()) + "/" + fmt(menu.storageCapacity());
        barLabel(g, cx, cy + 122, cw, "Storage", store);
    }
}
