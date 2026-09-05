/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.client.JscOsTheme;
import dev.jstech.computronics.operation.payload.RequestSettingsPayload;
import dev.jstech.computronics.operation.payload.SettingsSnapshotPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;

/**
 * The System Monitor: a pre-installed "this machine at a glance" dashboard. It reports the local computer's
 * processor, memory, and graphics specs, and draws a live usage bar for every disk. It reuses the settings
 * snapshot the Settings app already builds, so it needs no server code of its own, and re-requests it on a
 * slow cadence so the storage bars track items being stored.
 */
public final class SystemMonitorApp implements DesktopApp {

    private static final int REFRESH_FRAMES = 40;

    private static final int C_GREEN = 0xFF2EA043;
    private static final int C_AMBER = 0xFFE0A020;
    private static final int C_RED = 0xFFD1495B;

    private final BlockPos host;
    private OsSkin skin = OsSkin.fallback();
    private SettingsSnapshotPayload data;
    private int frame;

    private static SystemMonitorApp active;

    public SystemMonitorApp(final BlockPos host) {
        this.host = host;
        active = this;
        PacketDistributor.sendToServer(new RequestSettingsPayload(host));
    }

    /** Routes a settings snapshot to the open System Monitor window (shared with the Settings app). */
    public static void accept(final SettingsSnapshotPayload payload) {
        if (active != null && active.host.equals(payload.hostPos())) {
            active.data = payload;
        }
    }

    @Override public String title() {
        return "System Monitor";
    }

    @Override public int defaultWidth() {
        return 260;
    }

    @Override public int defaultHeight() {
        return 190;
    }

    @Override public int minWidth() {
        return 220;
    }

    @Override public int minHeight() {
        return 150;
    }

    @Override public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
        active = this;
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        g.fill(x, y, x + width, y + height, skin.windowBg());
        frame++;
        if (frame % REFRESH_FRAMES == 0) {
            PacketDistributor.sendToServer(new RequestSettingsPayload(host));
        }

        final int px = x + 6;
        final int pw = width - 12;
        if (data == null) {
            g.drawString(font, "Reading machine...", px, y + 8, skin.dim(), false);
            return;
        }

        int row = y + 6;
        final String name = data.computerName().isEmpty() ? "Computer" : data.computerName();
        g.drawString(font, name, px, row, skin.text(), false);
        final String os = data.osLabel() + "  (" + data.platform() + ")";
        g.drawString(font, os, x + width - 6 - font.width(os), row, skin.dim(), false);
        row += 12;
        g.fill(px, row, px + pw, row + 1, skin.edge());
        row += 5;

        row = specRow(g, font, px, row, pw, "Processor", data.cpuLabel().isEmpty() ? "-" : data.cpuLabel(),
                cpuClock(data.cpuMhz()));
        row = specRow(g, font, px, row, pw, "Memory", "RAM buffer", JscOsTheme.fmt(data.ramMb()) + " MB");
        row = specRow(g, font, px, row, pw, "Graphics", data.vramMb() > 0 ? "VRAM" : "no GPU",
                data.vramMb() > 0 ? JscOsTheme.fmt(data.vramMb()) + " MB" : "-");

        row += 4;
        g.drawString(font, "STORAGE", px, row, skin.dim(), false);
        g.drawString(font, data.installed().size() + " programs installed",
                x + width - 6 - font.width(data.installed().size() + " programs installed"), row, skin.dim(), false);
        row += 12;

        final int barH = 8;
        final int rowH = barH + 12;
        final int maxRows = Math.max(1, (y + height - row) / rowH);
        int shown = 0;
        for (final SettingsSnapshotPayload.DiskUse disk : data.disks()) {
            if (shown >= maxRows) {
                break;
            }
            storageRow(g, font, px, row, pw, barH, disk);
            row += rowH;
            shown++;
        }
        if (data.disks().size() > maxRows) {
            g.drawString(font, "+ " + (data.disks().size() - maxRows) + " more disk(s)", px, row, skin.dim(), false);
        }
    }

    private int specRow(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                        final String group, final String label, final String value) {
        g.drawString(font, group, x, y, skin.dim(), false);
        g.drawString(font, label, x + 62, y, skin.text(), false);
        g.drawString(font, value, x + w - font.width(value), y, skin.text(), false);
        return y + 12;
    }

    private void storageRow(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                            final int barH, final SettingsSnapshotPayload.DiskUse disk) {
        final String tag = disk.label() + (disk.system() ? " (system)" : "");
        g.drawString(font, tag, x, y, skin.text(), false);
        final long cap = Math.max(1L, disk.capMb());
        final double frac = Math.min(1.0, (double) disk.usedMb() / cap);
        final String usage = JscOsTheme.fmt(disk.usedMb()) + " / " + JscOsTheme.fmt(disk.capMb()) + " MB";
        g.drawString(font, usage, x + w - font.width(usage), y, skin.dim(), false);
        final int barY = y + 10;
        g.fill(x, barY, x + w, barY + barH, skin.fieldBg());
        g.fill(x, barY, x + (int) (w * frac), barY + barH, usageColor(frac));
        OsSkin.outline(g, x, barY, w, barH, skin.edge());
    }

    private static int usageColor(final double frac) {
        if (frac >= 0.9) {
            return C_RED;
        }
        if (frac >= 0.7) {
            return C_AMBER;
        }
        return C_GREEN;
    }

    private static String cpuClock(final int mhz) {
        if (mhz <= 0) {
            return "-";
        }
        return mhz >= 1000 ? String.format(Locale.ROOT, "%.2f GHz", mhz / 1000.0) : mhz + " MHz";
    }
}
