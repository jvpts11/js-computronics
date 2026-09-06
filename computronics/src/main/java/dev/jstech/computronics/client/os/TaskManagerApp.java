/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jstech.computronics.client.os;

import dev.jstech.computronics.operation.payload.RequestSettingsPayload;
import dev.jstech.computronics.operation.payload.SettingsSnapshotPayload;
import dev.jstech.computronics.operation.payload.SettingsSnapshotPayload.DiskUse;
import dev.jstech.computronics.operation.payload.SettingsSnapshotPayload.RamUse;
import dev.jstech.core.client.gui.component.Draw;
import dev.jstech.core.client.gui.component.Texts;
import dev.jstech.core.client.gui.theme.JsTechTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * The Task Manager: what this machine is running and what it is spending. Every desktop gets the tool it
 * really had, not one window repainted — Frames 95 the Close Program box it actually shipped, Frames XP the
 * four-tab manager with its menu bar and status bar, Frames 11 the rail and the wide table, and each Linux
 * desktop the system monitor its own package brings. They all read the same machine: the memory ledger the
 * notification area draws from, the disks, the processor, and the network link.
 */
public final class TaskManagerApp implements DesktopApp {

    /** The shape this window takes, decided by the desktop it opened on. */
    private enum Form { CLOSE_BOX, LUNA, MODERN, PLASMA, GNOME }

    private static final int REFRESH_FRAMES = 40;
    private static final long SAMPLE_MS = 1000L;
    /** How many seconds of history the graphs keep. */
    private static final int HISTORY = 60;
    private static final int ROW_H = 10;
    private static final int MENU_H = 11;
    private static final int STATUS_H = 11;

    private static final String KIND_WINDOW = "WINDOW";
    private static final String KIND_SYSTEM = "SYSTEM";
    private static final String KIND_DESKTOP = "DESKTOP";
    private static final String KIND_SERVICE = "SERVICE";

    private final BlockPos host;
    private final Form form;
    private OsSkin skin = OsSkin.fallback();
    @Nullable
    private SettingsSnapshotPayload data;
    private int frame;
    private int page;
    private int selected = -1;
    private int scroll;
    private int lastMouseX;
    private int lastMouseY;
    /** The content rectangle of the last frame, so a click is read against exactly what was drawn. */
    private int cx;
    private int cy;
    private int cw;
    private int ch;

    private final Load load = new Load();
    private final int[] cpuHistory = new int[HISTORY];
    private final int[] memHistory = new int[HISTORY];
    private int samples;
    private long lastSampleAt;

    private static TaskManagerApp active;

    public TaskManagerApp(final BlockPos host, final ResourceLocation desktopId) {
        this.host = host;
        this.form = formOf(desktopId);
        active = this;
        PacketDistributor.sendToServer(new RequestSettingsPayload(host));
    }

    private static Form formOf(@Nullable final ResourceLocation desktopId) {
        final String path = desktopId == null ? "" : desktopId.getPath();
        return switch (path) {
            case "frames_95" -> Form.CLOSE_BOX;
            case "frames_11" -> Form.MODERN;
            case "kde_plasma" -> Form.PLASMA;
            case "gnome", "cinnamon" -> Form.GNOME;
            default -> Form.LUNA;
        };
    }

    /** Routes a settings snapshot to the open Task Manager (shared with Settings and the System Monitor). */
    public static void accept(final SettingsSnapshotPayload payload) {
        if (active != null && active.host.equals(payload.hostPos())) {
            active.data = payload;
        }
    }

    // ---- the machine, as this window reads it -------------------------------------------------------

    private List<RamUse> processes() {
        return data == null ? List.of() : data.ramUses();
    }

    /** Only the windows the player opened: what the older managers called tasks. */
    private List<RamUse> tasks() {
        final List<RamUse> out = new ArrayList<>();
        for (final RamUse use : processes()) {
            if (KIND_WINDOW.equals(use.kind())) {
                out.add(use);
            }
        }
        return out;
    }

    private List<RamUse> services() {
        final List<RamUse> out = new ArrayList<>();
        for (final RamUse use : processes()) {
            if (KIND_SERVICE.equals(use.kind())) {
                out.add(use);
            }
        }
        return out;
    }

    /** The rows the page in front is showing, so selection and ending act on what is under the cursor. */
    private List<RamUse> rows() {
        if (form == Form.CLOSE_BOX) {
            return tasks();
        }
        if (form == Form.LUNA) {
            return page == 0 ? tasks() : processes();
        }
        if (form == Form.MODERN && page == 2) {
            return services();
        }
        if (form == Form.PLASMA && page == 1) {
            return tasks();
        }
        return processes();
    }

    @Nullable
    private RamUse selectedRow() {
        final List<RamUse> rows = rows();
        return selected >= 0 && selected < rows.size() ? rows.get(selected) : null;
    }

    /**
     * Ends the picked process. Only a window can be ended: the system, its desktop and its services are what
     * the machine IS, not something the player started, and no real manager lets those be killed either.
     */
    private void endSelected() {
        final RamUse use = selectedRow();
        if (use != null && KIND_WINDOW.equals(use.kind())) {
            DesktopScreen.requestClose(use.label());
            selected = -1;
            PacketDistributor.sendToServer(new RequestSettingsPayload(host));
        }
    }

    private boolean canEnd() {
        final RamUse use = selectedRow();
        return use != null && KIND_WINDOW.equals(use.kind());
    }

    private int totalMb() {
        return data == null ? 0 : Math.max(1, data.ramMb());
    }

    private int usedMb() {
        return data == null ? 0 : data.ramUsedMb();
    }

    /** This process's share of the machine's load, by what it holds. See {@link Load} for what that means. */
    private int cpuOf(final RamUse use) {
        final int total = Math.max(1, usedMb());
        return (int) Math.round(load.percent() * (double) use.mb() / total);
    }

    /**
     * The machine's processor load. What a computer here truly spends is the work its scripts and services
     * do, and until the scripting language exists there is almost none of it — but a real machine is never
     * perfectly idle either, so the reading drifts around a small baseline instead of sitting at a dead
     * zero. The drift is deliberately smaller than any real change, so a script starting still reads clearly.
     */
    private static final class Load {

        private final Random noise = new Random();
        private double value = 3.0;

        void step(final double target) {
            value += (target - value) * 0.2 + (noise.nextDouble() - 0.5) * 1.8;
            value = Math.max(0.5, Math.min(100.0, value));
        }

        int percent() {
            return (int) Math.round(value);
        }
    }

    /** Samples the load and the memory once a second, so the graphs read a minute of the machine's life. */
    private void sample() {
        final long now = System.currentTimeMillis();
        if (now - lastSampleAt < SAMPLE_MS) {
            return;
        }
        lastSampleAt = now;
        // The baseline the machine idles around: the system itself, plus a little for each thing it runs.
        load.step(2.0 + services().size() * 0.8 + tasks().size() * 0.6);
        final int at = samples % HISTORY;
        cpuHistory[at] = load.percent();
        memHistory[at] = usedMb();
        samples++;
    }

    // ---- window ------------------------------------------------------------------------------------

    @Override
    public String title() {
        return switch (form) {
            case CLOSE_BOX -> "Close Program";
            case PLASMA, GNOME -> "System Monitor";
            default -> "Task Manager";
        };
    }

    @Override
    public int defaultWidth() {
        return form == Form.CLOSE_BOX ? 190 : 300;
    }

    @Override
    public int defaultHeight() {
        return form == Form.CLOSE_BOX ? 150 : 200;
    }

    @Override
    public int minWidth() {
        return form == Form.CLOSE_BOX ? 170 : 240;
    }

    @Override
    public int minHeight() {
        return form == Form.CLOSE_BOX ? 130 : 150;
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
        active = this;
    }

    /** The page names of the rail or the tab strip this form carries. */
    private String[] pages() {
        return switch (form) {
            case LUNA -> new String[] {"Applications", "Processes", "Performance", "Networking"};
            case MODERN -> new String[] {"Processes", "Performance", "Services", "Storage", "Network"};
            case PLASMA -> new String[] {"Overview", "Applications", "Processes", "History"};
            case GNOME -> new String[] {"Processes", "Resources", "File Systems"};
            case CLOSE_BOX -> new String[0];
        };
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
        sample();
        cx = x;
        cy = y;
        cw = width;
        ch = height;
        g.fill(x, y, x + width, y + height, skin.windowBg());
        if (data == null) {
            g.drawString(font, "Reading machine...", x + 6, y + 8, skin.dim(), false);
            return;
        }
        switch (form) {
            case CLOSE_BOX -> renderCloseBox(g, font, x, y, width, height);
            case LUNA -> renderLuna(g, font, x, y, width, height);
            case MODERN -> renderModern(g, font, x, y, width, height);
            case PLASMA -> renderPlasma(g, font, x, y, width, height);
            case GNOME -> renderGnome(g, font, x, y, width, height);
        }
    }

    // ---- Frames 95: the Close Program box ----------------------------------------------------------

    private void renderCloseBox(final GuiGraphics g, final Font font, final int x, final int y,
                               final int width, final int height) {
        final int pad = 6;
        final int btnH = 13;
        final int listH = height - 2 * pad - btnH - 24;
        final int lx = x + pad;
        final int lw = width - 2 * pad;
        skin.field(g, lx, y + pad, lw, listH, false);
        drawRows(g, font, tasks(), lx + 2, y + pad + 2, lw - 4, listH - 4, false);
        final int textY = y + pad + listH + 3;
        Texts.small(g, font, "Ending a program may lose unsaved work.", lx, textY, skin.dim());
        Texts.small(g, font, "Ending the system restarts the machine.", lx, textY + 8, skin.dim());
        final int by = y + height - pad - btnH;
        final int bw = (lw - 8) / 3;
        button(g, font, lx, by, bw, btnH, "End Task", canEnd());
        button(g, font, lx + bw + 4, by, bw, btnH, "Shut Down", true);
        button(g, font, lx + 2 * (bw + 4), by, bw, btnH, "Cancel", true);
    }

    // ---- Frames XP: the four-tab manager -----------------------------------------------------------

    private void renderLuna(final GuiGraphics g, final Font font, final int x, final int y,
                            final int width, final int height) {
        // Menu bar, tab strip, the page, then the status bar along the foot.
        final String[] menus = {"File", "Options", "View", "Windows", "Shut Down", "Help"};
        int mx = x + 4;
        for (final String menu : menus) {
            Texts.small(g, font, menu, mx, y + 2, skin.text());
            mx += Texts.smallWidth(font, menu) + 7;
        }
        final int tabY = y + MENU_H;
        drawTabs(g, font, x + 3, tabY, width - 6);
        final int px = x + 5;
        final int py = tabY + 14;
        final int pw = width - 10;
        final int ph = y + height - STATUS_H - py - 4;
        Draw.outline(g, px - 1, py - 1, pw + 2, ph + 2, skin.edge());
        switch (page) {
            case 0 -> {
                final int listH = ph - 17;
                skin.field(g, px, py, pw, listH, false);
                columns(g, font, px + 2, py + 1, pw - 4, new String[] {"Task", "Status"}, new int[] {0, pw - 52});
                drawRows(g, font, tasks(), px + 2, py + 10, pw - 4, listH - 11, false);
                final int by = py + listH + 3;
                final int bw = (pw - 8) / 3;
                button(g, font, px + pw - 3 * bw - 8, by, bw, 13, "End Task", canEnd());
                button(g, font, px + pw - 2 * bw - 4, by, bw, 13, "Switch To", canEnd());
                button(g, font, px + pw - bw, by, bw, 13, "New Task", true);
            }
            case 1 -> {
                final int listH = ph - 17;
                skin.field(g, px, py, pw, listH, false);
                columns(g, font, px + 2, py + 1, pw - 4,
                        new String[] {"Image Name", "Kind", "CPU", "Mem Usage"},
                        new int[] {0, pw - 118, pw - 74, pw - 48});
                drawRows(g, font, processes(), px + 2, py + 10, pw - 4, listH - 11, true);
                button(g, font, px + pw - 60, py + listH + 3, 60, 13, "End Process", canEnd());
            }
            case 2 -> renderPerformance(g, font, px, py, pw, ph);
            default -> renderNetworking(g, font, px, py, pw, ph);
        }
        final int sy = y + height - STATUS_H;
        g.fill(x, sy, x + width, sy + 1, skin.edge());
        final int third = width / 3;
        Texts.small(g, font, "Processes: " + processes().size(), x + 4, sy + 2, skin.text());
        Texts.small(g, font, "CPU Usage: " + load.percent() + "%", x + third + 4, sy + 2, skin.text());
        Texts.small(g, font, "Commit: " + usedMb() + "M / " + totalMb() + "M",
                x + 2 * third + 4, sy + 2, skin.text());
    }

    /** The XP performance page: the two meters on the left, their histories on the right, facts underneath. */
    private void renderPerformance(final GuiGraphics g, final Font font, final int x, final int y,
                                   final int w, final int h) {
        final int meterW = 68;
        final int graphH = (h - 34) / 2;
        gauge(g, font, x + 2, y + 8, meterW, graphH - 10, load.percent() + " %");
        Texts.small(g, font, "CPU Usage", x + 2, y, skin.dim());
        gauge(g, font, x + 2, y + graphH + 16, meterW, graphH - 10, usedMb() + " MB");
        Texts.small(g, font, "Memory Usage", x + 2, y + graphH + 8, skin.dim());
        final int gx = x + meterW + 8;
        final int gw = w - meterW - 10;
        Texts.small(g, font, "CPU Usage History", gx, y, skin.dim());
        history(g, gx, y + 8, gw, graphH - 10, cpuHistory, 100);
        Texts.small(g, font, "Memory Usage History", gx, y + graphH + 8, skin.dim());
        history(g, gx, y + graphH + 16, gw, graphH - 10, memHistory, totalMb());
        final int fy = y + 2 * graphH + 12;
        final int half = w / 2;
        facts(g, font, x + 2, fy, half - 4, new String[][] {
            {"Processes", String.valueOf(processes().size())},
            {"Programs", String.valueOf(tasks().size())},
            {"Services", String.valueOf(services().size())}});
        facts(g, font, x + half + 2, fy, half - 4, new String[][] {
            {"Total MB", JsTechTheme.fmt(totalMb())},
            {"Free MB", JsTechTheme.fmt(Math.max(0, totalMb() - usedMb()))},
            {"Processor", clock()}});
    }

    /** The XP networking page: the link, and what the network is doing through it. */
    private void renderNetworking(final GuiGraphics g, final Font font, final int x, final int y,
                                  final int w, final int h) {
        final boolean up = DesktopScreen.hostNetworked(host);
        Texts.small(g, font, "Network", x + 2, y + 2, skin.dim());
        g.drawString(font, up ? "Connected" : "Not connected", x + 2, y + 11,
                up ? 0xFF2EA043 : skin.dim(), false);
        history(g, x + 2, y + 24, w - 4, h - 30, cpuHistory, 100);
        Texts.small(g, font, "Link activity over the last minute", x + 2, y + h - 8, skin.dim());
    }

    // ---- Frames 11: the rail and the wide table ----------------------------------------------------

    private void renderModern(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height) {
        final int railW = 74;
        drawRail(g, font, x, y, railW, height);
        final int px = x + railW + 4;
        final int pw = width - railW - 8;
        switch (page) {
            case 1 -> renderPerformance(g, font, px, y + 4, pw, height - 8);
            case 3 -> renderDisks(g, font, px, y + 4, pw, height - 8);
            case 4 -> renderNetworking(g, font, px, y + 4, pw, height - 8);
            default -> {
                button(g, font, x + width - 48, y + 3, 44, 12, "End task", canEnd());
                columns(g, font, px, y + 18, pw,
                        new String[] {"Name", "Kind", "CPU", "Memory"},
                        new int[] {0, pw - 104, pw - 62, pw - 40});
                drawRows(g, font, rows(), px, y + 27, pw, height - 31, true);
            }
        }
    }

    /** The page rail of the modern desktops: one row per page, the one in front marked with its own bar. */
    private void drawRail(final GuiGraphics g, final Font font, final int x, final int y,
                          final int w, final int h) {
        g.fill(x, y, x + w, y + h, skin.panelBg());
        g.fill(x + w - 1, y, x + w, y + h, skin.edge());
        final String[] names = pages();
        for (int i = 0; i < names.length; i++) {
            final int ry = y + 4 + i * 13;
            final boolean on = i == page;
            if (on) {
                g.fill(x + 2, ry, x + w - 3, ry + 12, skin.listHover());
                g.fill(x + 2, ry + 2, x + 4, ry + 10, skin.accent());
            }
            Texts.small(g, font, names[i], x + 7, ry + 3, on ? skin.text() : skin.dim());
        }
    }

    // ---- KDE Plasma and GNOME ----------------------------------------------------------------------

    private void renderPlasma(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height) {
        final int railW = 62;
        drawRail(g, font, x, y, railW, height);
        final int px = x + railW + 4;
        final int pw = width - railW - 8;
        if (page == 3) {
            renderPerformance(g, font, px, y + 4, pw, height - 8);
            return;
        }
        final int cardH = 22;
        if (page == 0) {
            card(g, font, px, y + 4, pw / 2 - 2, cardH, "Memory", usedMb() + " / " + totalMb() + " MB");
            card(g, font, px + pw / 2 + 2, y + 4, pw / 2 - 2, cardH, "Processor",
                    load.percent() + "%  " + clock());
        }
        final int ty = page == 0 ? y + cardH + 8 : y + 4;
        button(g, font, x + width - 46, ty - 1, 42, 11, "End", canEnd());
        columns(g, font, px, ty + 12, pw, new String[] {"Name", "Kind", "CPU", "Memory"},
                new int[] {0, pw - 104, pw - 62, pw - 40});
        drawRows(g, font, rows(), px, ty + 21, pw, y + height - (ty + 25), true);
    }

    private void renderGnome(final GuiGraphics g, final Font font, final int x, final int y,
                             final int width, final int height) {
        // A header bar with the segmented control centred, and the action at its right end.
        final int barH = 14;
        g.fill(x, y, x + width, y + barH, skin.panelBg());
        g.fill(x, y + barH - 1, x + width, y + barH, skin.edge());
        final String[] names = pages();
        int segW = 0;
        for (final String name : names) {
            segW += Texts.smallWidth(font, name) + 10;
        }
        int sx = x + (width - segW) / 2;
        for (int i = 0; i < names.length; i++) {
            final int w = Texts.smallWidth(font, names[i]) + 10;
            if (i == page) {
                g.fill(sx, y + 2, sx + w, y + barH - 3, skin.listHover());
            }
            Texts.small(g, font, names[i], sx + 5, y + 4, i == page ? skin.text() : skin.dim());
            sx += w;
        }
        button(g, font, x + width - 42, y + 2, 40, 11, "End", canEnd());
        final int py = y + barH + 2;
        switch (page) {
            case 1 -> renderPerformance(g, font, x + 4, py + 2, width - 8, height - barH - 8);
            case 2 -> renderDisks(g, font, x + 4, py + 2, width - 8, height - barH - 8);
            default -> {
                columns(g, font, x + 4, py, width - 8, new String[] {"Process Name", "Kind", "CPU", "Memory"},
                        new int[] {0, width - 112, width - 70, width - 48});
                drawRows(g, font, rows(), x + 4, py + 9, width - 8, y + height - (py + 12), true);
            }
        }
    }

    // ---- shared pieces ------------------------------------------------------------------------------

    /** The disks page: one bar per volume, the same figures the System Monitor shows. */
    private void renderDisks(final GuiGraphics g, final Font font, final int x, final int y,
                             final int w, final int h) {
        int row = y;
        for (final DiskUse disk : data == null ? List.<DiskUse>of() : data.disks()) {
            if (row + 18 > y + h) {
                break;
            }
            final String tag = disk.label() + (disk.system() ? " (system)" : "");
            Texts.small(g, font, Texts.clip(font, tag, w - 70), x, row, skin.text());
            final String use = JsTechTheme.fmt(disk.usedMb()) + " / " + JsTechTheme.fmt(disk.capMb()) + " MB";
            Texts.small(g, font, use, x + w - Texts.smallWidth(font, use), row, skin.dim());
            final double frac = Math.min(1.0, (double) disk.usedMb() / Math.max(1L, disk.capMb()));
            g.fill(x, row + 9, x + w, row + 15, skin.fieldBg());
            g.fill(x, row + 9, x + (int) (w * frac), row + 15, usageColor(frac));
            Draw.outline(g, x, row + 9, w, 6, skin.edge());
            row += 19;
        }
    }

    /** One label-over-value card, as the Plasma monitor lays its overview out. */
    private void card(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                      final int h, final String name, final String value) {
        g.fill(x, y, x + w, y + h, skin.fieldBg());
        Draw.outline(g, x, y, w, h, skin.edge());
        Texts.small(g, font, name, x + 4, y + 3, skin.dim());
        g.drawString(font, Texts.clip(font, value, w - 8), x + 4, y + 11, skin.text(), false);
    }

    /** A boxed pair-list, as the XP performance page groups its totals. */
    private void facts(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                       final String[][] pairs) {
        Draw.outline(g, x, y, w, pairs.length * 9 + 4, skin.edge());
        int row = y + 2;
        for (final String[] pair : pairs) {
            Texts.small(g, font, pair[0], x + 3, row, skin.dim());
            Texts.small(g, font, pair[1], x + w - 3 - Texts.smallWidth(font, pair[1]), row, skin.text());
            row += 9;
        }
    }

    /** One of the boxed meters: a dark face with the figure over a filled foot, as those managers drew them. */
    private void gauge(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                       final int h, final String figure) {
        g.fill(x, y, x + w, y + h, 0xFF001400);
        Draw.outline(g, x, y, w, h, skin.edge());
        grid(g, x, y, w, h);
        final int fill = (int) ((h - 2) * Math.min(1.0, load.percent() / 100.0));
        g.fill(x + 1, y + h - 1 - fill, x + w - 1, y + h - 1, 0xFF1F8B1F);
        Texts.small(g, font, figure, x + (w - Texts.smallWidth(font, figure)) / 2, y + h / 2 - 4, 0xFF39D639);
    }

    /** A history graph: the same green grid and line those managers all drew, over the last minute. */
    private void history(final GuiGraphics g, final int x, final int y, final int w, final int h,
                         final int[] series, final int max) {
        g.fill(x, y, x + w, y + h, 0xFF001400);
        Draw.outline(g, x, y, w, h, skin.edge());
        grid(g, x, y, w, h);
        if (samples < 2) {
            return;
        }
        final int count = Math.min(samples, HISTORY);
        final int span = Math.max(1, max);
        int prevX = x;
        int prevY = y + h - 1;
        for (int i = 0; i < count; i++) {
            // Oldest on the left: walk back from the newest sample.
            final int idx = ((samples - count + i) % HISTORY + HISTORY) % HISTORY;
            final int px = x + (int) ((long) i * (w - 1) / Math.max(1, count - 1));
            final int py = y + h - 1 - (int) ((long) series[idx] * (h - 2) / span);
            if (i > 0) {
                line(g, prevX, prevY, px, py);
            }
            prevX = px;
            prevY = py;
        }
    }

    private static void grid(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        for (int gy = y + h / 4; gy < y + h; gy += Math.max(4, h / 4)) {
            g.fill(x + 1, gy, x + w - 1, gy + 1, 0xFF0F3D0F);
        }
        for (int gx = x + w / 6; gx < x + w; gx += Math.max(6, w / 6)) {
            g.fill(gx, y + 1, gx + 1, y + h - 1, 0xFF0F3D0F);
        }
    }

    /** A one-pixel line between two points, walked along its longer axis. */
    private static void line(final GuiGraphics g, final int x1, final int y1, final int x2, final int y2) {
        final int dx = x2 - x1;
        final int dy = y2 - y1;
        final int steps = Math.max(1, Math.max(Math.abs(dx), Math.abs(dy)));
        for (int i = 0; i <= steps; i++) {
            final int px = x1 + dx * i / steps;
            final int py = y1 + dy * i / steps;
            g.fill(px, py, px + 1, py + 1, 0xFF39D639);
        }
    }

    /** The tab strip of the Luna form, drawn in the skin's own tab shape. */
    private void drawTabs(final GuiGraphics g, final Font font, final int x, final int y, final int w) {
        final String[] names = pages();
        int tx = x;
        for (int i = 0; i < names.length; i++) {
            final int tw = Texts.smallWidth(font, names[i]) + 8;
            skin.tab(g, font, tx, y, tw, 12, "", i == page);
            Texts.small(g, font, names[i], tx + 4, y + 3, i == page ? skin.text() : skin.dim());
            tx += tw + 1;
        }
        g.fill(x, y + 11, x + w, y + 12, skin.edge());
    }

    /** A column header row, each name at its own offset from the left of the list. */
    private void columns(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                         final String[] names, final int[] offsets) {
        for (int i = 0; i < names.length && i < offsets.length; i++) {
            Texts.small(g, font, names[i], x + offsets[i], y, skin.dim());
        }
        g.fill(x, y + 8, x + w, y + 9, skin.edge());
    }

    /**
     * The process rows themselves. {@code detailed} adds the kind and the load beside the memory; the older
     * managers' task list shows only the name and whether it is running.
     */
    private void drawRows(final GuiGraphics g, final Font font, final List<RamUse> list, final int x,
                          final int y, final int w, final int h, final boolean detailed) {
        final int fit = Math.max(1, h / ROW_H);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, list.size() - fit)));
        for (int i = 0; i < fit && scroll + i < list.size(); i++) {
            final int index = scroll + i;
            final RamUse use = list.get(index);
            final int ry = y + i * ROW_H;
            if (index == selected) {
                g.fill(x, ry, x + w, ry + ROW_H, skin.accent());
            } else if (lastMouseY >= ry && lastMouseY < ry + ROW_H && lastMouseX >= x && lastMouseX < x + w) {
                g.fill(x, ry, x + w, ry + ROW_H, skin.listHover());
            }
            final int text = index == selected ? 0xFFFFFFFF : skin.text();
            final int dim = index == selected ? 0xFFFFFFFF : skin.dim();
            if (detailed) {
                final String mb = JsTechTheme.fmt(use.mb()) + " MB";
                final int mbW = Texts.smallWidth(font, mb);
                final String cpu = cpuOf(use) + "%";
                final int cpuW = Texts.smallWidth(font, cpu);
                final String kind = kindLabel(use.kind());
                Texts.small(g, font, Texts.clip(font, use.label(), w - mbW - cpuW - 62), x + 1, ry + 2, text);
                Texts.small(g, font, kind, x + w - mbW - cpuW - 52, ry + 2, dim);
                Texts.small(g, font, cpu, x + w - mbW - cpuW - 6, ry + 2, dim);
                Texts.small(g, font, mb, x + w - mbW - 1, ry + 2, dim);
            } else {
                // The task list has only the two columns its own manager had, at the header's own offsets.
                Texts.small(g, font, Texts.clip(font, use.label(), w - 54), x + 1, ry + 2, text);
                Texts.small(g, font, "Running", x + w - 48, ry + 2, dim);
            }
        }
    }

    /** A button in the skin's own shape, greyed when the action behind it cannot run. */
    private void button(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                        final int h, final String label, final boolean enabled) {
        final boolean hot = enabled && lastMouseX >= x && lastMouseX < x + w
                && lastMouseY >= y && lastMouseY < y + h;
        skin.button(g, font, x, y, w, h, "", hot, false, false);
        final int tw = Texts.smallWidth(font, label);
        Texts.small(g, font, label, x + (w - tw) / 2, y + (h - 7) / 2, enabled ? skin.text() : skin.dim());
    }

    private static String kindLabel(final String kind) {
        return switch (kind) {
            case KIND_SYSTEM -> "system";
            case KIND_DESKTOP -> "desktop";
            case KIND_SERVICE -> "service";
            case KIND_WINDOW -> "program";
            default -> kind.toLowerCase(Locale.ROOT);
        };
    }

    private String clock() {
        final int mhz = data == null ? 0 : data.cpuMhz();
        if (mhz <= 0) {
            return "-";
        }
        return mhz >= 1000 ? String.format(Locale.ROOT, "%.2f GHz", mhz / 1000.0) : mhz + " MHz";
    }

    private static int usageColor(final double frac) {
        if (frac >= 0.9) {
            return 0xFFD1495B;
        }
        if (frac >= 0.7) {
            return 0xFFE0A020;
        }
        return 0xFF2EA043;
    }

    // ---- input --------------------------------------------------------------------------------------

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY,
                            final int button) {
        final int x = cx;
        final int y = cy;
        final int w = cw;
        final int h = ch;
        if (data == null || w <= 0) {
            return;
        }
        // The page picker first: a tab strip, a rail, or a segmented control, depending on the form.
        final String[] names = pages();
        switch (form) {
            case LUNA -> {
                if (mouseY >= y + MENU_H && mouseY < y + MENU_H + 12) {
                    int tx = x + 3;
                    for (int i = 0; i < names.length; i++) {
                        final int tw = Texts.smallWidth(font(), names[i]) + 8;
                        if (mouseX >= tx && mouseX < tx + tw) {
                            page = i;
                            selected = -1;
                            scroll = 0;
                            return;
                        }
                        tx += tw + 1;
                    }
                }
            }
            case MODERN, PLASMA -> {
                final int railW = form == Form.MODERN ? 74 : 62;
                if (mouseX < x + railW) {
                    final int row = (int) ((mouseY - (y + 4)) / 13);
                    if (row >= 0 && row < names.length) {
                        page = row;
                        selected = -1;
                        scroll = 0;
                    }
                    return;
                }
            }
            case GNOME -> {
                if (mouseY < y + 14) {
                    int segW = 0;
                    for (final String name : names) {
                        segW += Texts.smallWidth(font(), name) + 10;
                    }
                    int sx = x + (w - segW) / 2;
                    for (int i = 0; i < names.length; i++) {
                        final int tw = Texts.smallWidth(font(), names[i]) + 10;
                        if (mouseX >= sx && mouseX < sx + tw) {
                            page = i;
                            selected = -1;
                            scroll = 0;
                            return;
                        }
                        sx += tw;
                    }
                }
            }
            default -> {
            }
        }
        // Then the buttons, then the rows: whichever the click actually landed on.
        if (endButtonHit(mouseX, mouseY, x, y, w, h)) {
            endSelected();
            return;
        }
        final int[] list = listRect(x, y, w, h);
        if (list != null && mouseX >= list[0] && mouseX < list[0] + list[2]
                && mouseY >= list[1] && mouseY < list[1] + list[3]) {
            final int row = scroll + (int) ((mouseY - list[1]) / ROW_H);
            selected = row >= 0 && row < rows().size() ? row : -1;
        }
    }

    /** The vanilla font, which the hit-tests measure with exactly as the drawing does. */
    private static Font font() {
        return net.minecraft.client.Minecraft.getInstance().font;
    }

    /** Where the End button sits on the page in front, or null when this page has none. */
    private boolean endButtonHit(final double mx, final double my, final int x, final int y,
                                 final int w, final int h) {
        return switch (form) {
            case CLOSE_BOX -> {
                final int btnH = 13;
                final int bw = (w - 12 - 8) / 3;
                final int by = y + h - 6 - btnH;
                yield my >= by && my < by + btnH && mx >= x + 6 && mx < x + 6 + bw;
            }
            case LUNA -> {
                if (page > 1) {
                    yield false;
                }
                final int py = y + MENU_H + 14;
                final int ph = y + h - STATUS_H - py - 4;
                final int by = py + ph - 17 + 3;
                if (my < by || my >= by + 13) {
                    yield false;
                }
                final int pw = w - 10;
                yield page == 1 ? mx >= x + 5 + pw - 60
                        : mx >= x + 5 + pw - 3 * ((pw - 8) / 3) - 8 && mx < x + 5 + pw - 2 * ((pw - 8) / 3) - 4;
            }
            case MODERN -> page == 0 && my >= y + 3 && my < y + 15 && mx >= x + w - 48;
            case PLASMA -> my >= y + (page == 0 ? 29 : 3) && my < y + (page == 0 ? 40 : 14) && mx >= x + w - 46;
            case GNOME -> page == 0 && my >= y + 2 && my < y + 13 && mx >= x + w - 42;
        };
    }

    /** The rectangle the rows of the page in front occupy, or null when this page shows no list. */
    @Nullable
    private int[] listRect(final int x, final int y, final int w, final int h) {
        return switch (form) {
            case CLOSE_BOX -> new int[] {x + 8, y + 8, w - 16, h - 12 - 13 - 24};
            case LUNA -> page > 1 ? null
                    : new int[] {x + 7, y + MENU_H + 24, w - 14, y + h - STATUS_H - (y + MENU_H + 24) - 21};
            case MODERN -> page == 0 || page == 2
                    ? new int[] {x + 78, y + 27, w - 86, h - 31} : null;
            case PLASMA -> page == 3 ? null
                    : new int[] {x + 66, y + (page == 0 ? 51 : 25), w - 74, h - (page == 0 ? 55 : 29)};
            case GNOME -> page == 0 ? new int[] {x + 4, y + 25, w - 8, h - 29} : null;
        };
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        scroll = Math.max(0, scroll - (int) Math.signum(delta));
        return true;
    }
}
