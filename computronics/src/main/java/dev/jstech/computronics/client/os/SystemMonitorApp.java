/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.core.client.gui.theme.JsTechTheme;
import dev.jstech.computronics.operation.payload.RequestSettingsPayload;
import dev.jstech.computronics.operation.payload.SettingsSnapshotPayload;
import dev.jstech.computronics.operation.payload.SettingsSnapshotPayload.DiskUse;
import dev.jstech.computronics.operation.payload.SettingsSnapshotPayload.RamUse;
import dev.jstech.core.client.gui.component.Draw;
import dev.jstech.core.client.gui.component.Label;
import dev.jstech.core.client.gui.component.ListView;
import dev.jstech.core.client.gui.component.Panel;
import dev.jstech.core.client.gui.component.UiContext;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

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
    private static final int BAR_H = 8;
    private static final int DISK_ROW_H = BAR_H + 12;
    private static final int MEM_ROW_H = 10;
    /** Memory holders shown before the list scrolls, so the disks below keep their room. */
    private static final int MEM_ROWS_SHOWN = 4;

    private final BlockPos host;
    private OsSkin skin = OsSkin.fallback();
    @Nullable
    private SettingsSnapshotPayload data;
    private int frame;
    private int lastMouseX;
    private int lastMouseY;

    private static SystemMonitorApp active;

    private final Panel root = new Panel();
    private final Label loadingLabel;
    private final Label nameLabel;
    private final Label osLabel;
    private final Label[] specGroups = new Label[3];
    private final Label[] specLabels = new Label[3];
    private final Label[] specValues = new Label[3];
    private final Label memoryHeader;
    private final Label memoryFree;
    private final ListView<RamUse> memList;
    private final Label storageHeader;
    private final Label programsLabel;
    private final ListView<DiskUse> diskList;

    public SystemMonitorApp(final BlockPos host) {
        this.host = host;
        loadingLabel = root.add(new Label("Reading machine...", Label.Tone.DIM));
        nameLabel = root.add(new Label(() -> data == null || data.computerName().isEmpty() ? "Computer" : data.computerName()));
        osLabel = root.add(new Label(() -> data == null ? "" : data.osLabel() + "  (" + data.platform() + ")", Label.Tone.DIM)
                .setAlign(Label.Align.RIGHT));
        spec(0, "Processor", () -> data == null || data.cpuLabel().isEmpty() ? "-" : data.cpuLabel(), () -> cpuClock(data == null ? 0 : data.cpuMhz()));
        spec(1, "Memory", () -> "RAM", () -> data == null ? "-"
                : JsTechTheme.fmt(data.ramUsedMb()) + " / " + JsTechTheme.fmt(data.ramMb()) + " MB");
        spec(2, "Graphics", () -> data != null && data.vramMb() > 0 ? "VRAM" : "no GPU",
                () -> data != null && data.vramMb() > 0 ? JsTechTheme.fmt(data.vramMb()) + " MB" : "-");
        memoryHeader = root.add(new Label("MEMORY", Label.Tone.DIM));
        memoryFree = root.add(new Label(() -> data == null ? ""
                : JsTechTheme.fmt(Math.max(0, data.ramMb() - data.ramUsedMb())) + " MB free", Label.Tone.DIM)
                .setAlign(Label.Align.RIGHT));
        memList = root.add(new ListView<RamUse>(() -> data == null ? List.of() : data.ramUses(), MEM_ROW_H, this::renderMemoryRow));
        storageHeader = root.add(new Label("STORAGE", Label.Tone.DIM));
        programsLabel = root.add(new Label(() -> data == null ? "" : data.installed().size() + " programs installed", Label.Tone.DIM)
                .setAlign(Label.Align.RIGHT));
        diskList = root.add(new ListView<DiskUse>(() -> data == null ? List.of() : data.disks(), DISK_ROW_H, this::renderDiskRow));
        active = this;
        PacketDistributor.sendToServer(new RequestSettingsPayload(host));
    }

    private void spec(final int index, final String group, final Supplier<String> label, final Supplier<String> value) {
        specGroups[index] = root.add(new Label(group, Label.Tone.DIM));
        specLabels[index] = root.add(new Label(label));
        specValues[index] = root.add(new Label(value).setAlign(Label.Align.RIGHT));
    }

    /** Routes a settings snapshot to the open System Monitor window (shared with the Settings app). */
    public static void accept(final SettingsSnapshotPayload payload) {
        if (active != null && active.host.equals(payload.hostPos())) {
            active.data = payload;
        }
    }

    @Override
    public String title() {
        return "System Monitor";
    }

    @Override
    public int defaultWidth() {
        return 260;
    }

    @Override
    public int defaultHeight() {
        return 236;
    }

    @Override
    public int minWidth() {
        return 220;
    }

    @Override
    public int minHeight() {
        return 176;
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
        active = this;
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
        frame++;
        if (frame % REFRESH_FRAMES == 0) {
            PacketDistributor.sendToServer(new RequestSettingsPayload(host));
        }
        final UiContext ctx = new UiContext(skin, font, mouseX, mouseY, partialTick);
        g.fill(x, y, x + width, y + height, skin.windowBg());
        final int px = x + 6;
        final int pw = width - 12;
        final boolean ready = data != null;
        loadingLabel.setVisible(!ready);
        loadingLabel.setBounds(px, y + 8, pw, 8);
        for (final var c : List.of(nameLabel, osLabel, memoryHeader, memoryFree, memList, storageHeader,
                programsLabel, diskList)) {
            c.setVisible(ready);
        }
        int row = y + 6;
        nameLabel.setBounds(px, row, pw / 2, 8);
        osLabel.setBounds(px + pw / 2, row, pw / 2, 8);
        row += 12;
        if (ready) {
            g.fill(px, row, px + pw, row + 1, skin.edge());
        }
        row += 5;
        for (int i = 0; i < 3; i++) {
            specGroups[i].setVisible(ready);
            specLabels[i].setVisible(ready);
            specValues[i].setVisible(ready);
            specGroups[i].setBounds(px, row, 60, 8);
            specLabels[i].setBounds(px + 62, row, pw / 2 - 62, 8);
            specValues[i].setBounds(px + pw / 2, row, pw / 2, 8);
            row += 12;
        }
        row += 4;
        memoryHeader.setBounds(px, row, pw / 2, 8);
        memoryFree.setBounds(px + pw / 2, row, pw / 2, 8);
        row += 12;
        // The memory list shows a few holders and scrolls past that, so the disks below keep their room.
        final int holders = data == null ? 1 : Math.max(1, data.ramUses().size());
        final int memH = MEM_ROW_H * Math.min(MEM_ROWS_SHOWN, holders);
        memList.setBounds(px, row, pw, memH);
        row += memH + 4;
        storageHeader.setBounds(px, row, pw / 2, 8);
        programsLabel.setBounds(px + pw / 2, row, pw / 2, 8);
        row += 12;
        diskList.setBounds(px, row, pw, Math.max(DISK_ROW_H, y + height - row));
        root.render(g, ctx);
    }

    private void renderMemoryRow(final GuiGraphics g, final UiContext ctx, final RamUse use, final int index, final int x,
                                 final int y, final int w, final int h, final boolean hovered, final boolean selected) {
        final Font font = ctx.font();
        final String amount = JsTechTheme.fmt(use.mb()) + " MB";
        final int amountW = font.width(amount);
        final String name = font.plainSubstrByWidth(use.label(), w - amountW - 6);
        g.drawString(font, name, x, y + 1, ctx.skin().text(), false);
        g.drawString(font, amount, x + w - amountW, y + 1, ctx.skin().dim(), false);
    }

    private void renderDiskRow(final GuiGraphics g, final UiContext ctx, final DiskUse disk, final int index, final int x,
                               final int y, final int w, final int h, final boolean hovered, final boolean selected) {
        final Font font = ctx.font();
        final String tag = disk.label() + (disk.system() ? " (system)" : "");
        g.drawString(font, tag, x, y, ctx.skin().text(), false);
        final long cap = Math.max(1L, disk.capMb());
        final double frac = Math.min(1.0, (double) disk.usedMb() / cap);
        final String usage = JsTechTheme.fmt(disk.usedMb()) + " / " + JsTechTheme.fmt(disk.capMb()) + " MB";
        g.drawString(font, usage, x + w - font.width(usage), y, ctx.skin().dim(), false);
        final int barY = y + 10;
        g.fill(x, barY, x + w, barY + BAR_H, ctx.skin().fieldBg());
        g.fill(x, barY, x + (int) (w * frac), barY + BAR_H, usageColor(frac));
        Draw.outline(g, x, barY, w, BAR_H, ctx.skin().edge());
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

    @Override
    public boolean mouseScrolled(final double delta) {
        return root.mouseScrolled(lastMouseX, lastMouseY, delta);
    }
}
