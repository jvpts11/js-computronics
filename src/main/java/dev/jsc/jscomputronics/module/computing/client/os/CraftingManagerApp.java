/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import dev.jsc.jscomputronics.module.computing.operation.payload.CraftManagerStatePayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.DownloadToMediaPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.LoadFromMediaPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RemoveRomCraftPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestCraftManagerPayload;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Crafting Manager desktop app: moves {@code .craft} recipe files between a removable medium in a
 * linked drive and the Crafting Computer's recipe store.
 *
 * <p>The left pane lists the {@code .craft} files on the medium; the right pane lists the recipes
 * loaded on the computer (its Recipe ROM, mirrored as files under {@code crafts/} on its disk). The
 * action bar loads a selection (or every missing file) from the medium, downloads loaded recipes back
 * onto the medium, or removes loaded recipes. Every action needs a Crafting Card installed; without
 * one the app shows a banner and disables the buttons.
 */
public final class CraftingManagerApp implements DesktopApp {

    // Palette re-derived from the installed OS skin in applySkin; the warning colours stay fixed (semantic).
    private OsSkin skin = OsSkin.fallback();
    private int PANEL = 0xFFFFFFFF;
    private int EDGE = 0xFF6E7686;
    private int TEXT = 0xFF1A2230;
    private int SUB = 0xFF60687A;
    private int SEL_BG = 0xFF000080;
    private static final int SEL_TEXT = 0xFFFFFFFF;
    private int HEADER_BG = 0xFFE6E8EF;
    private static final int WARN_BG = 0xFFFCE3A1;
    private static final int WARN_TEXT = 0xFF6B4E00;

    private static final int PAD = 5;
    private static final int HEADER_H = 12;
    private static final int ROW_H = 11;
    private static final int BTN_H = 13;
    private static final int BAR_H = BTN_H + PAD * 2;
    private static final int TAB_H = 13;
    private static final int M_ROW_H = 15;
    private static final int MAX_JOBS = 16;

    private static CraftingManagerApp active;

    private final BlockPos host;

    private String mediaVolumeKey = "";
    private String mediaLabel = "";
    private List<String> mediaFiles = List.of();
    private List<CraftManagerStatePayload.WireRomEntry> romEntries = List.of();
    private boolean hasCard;
    private boolean loaded;
    private String status = "";
    private List<CraftManagerStatePayload.WireMachine> machines = List.of();
    private int tab; // 0 = Recipes, 1 = Machines

    private final Set<Integer> selectedMedia = new HashSet<>();
    private final Set<Integer> selectedRom = new HashSet<>();

    /**
     * The wire indices of the selected ROM rows. List positions are NOT the payload indices: machine recipes
     * carry offset indices (MACHINE_ROM_BASE + i), so every action must send {@code entry.index()} — sending
     * the raw list position would remove or export a different (bench) recipe.
     */
    private List<Integer> selectedRomIndices() {
        final List<Integer> out = new ArrayList<>();
        for (final int pos : selectedRom) {
            if (pos >= 0 && pos < romEntries.size()) {
                out.add(romEntries.get(pos).index());
            }
        }
        return out;
    }
    private int mediaScroll;
    private int romScroll;
    private int machineScroll;

    // Geometry captured on the last render so click math matches exactly.
    private int lastX;
    private int lastY;
    private int lastW;
    private int lastH;

    public CraftingManagerApp(final BlockPos host) {
        this.host = host;
        active = this;
        PacketDistributor.sendToServer(new RequestCraftManagerPayload(host));
    }

    /** Delivers a state refresh from the server to the open window. */
    public static void accept(final CraftManagerStatePayload payload) {
        if (active == null) {
            return;
        }
        active.mediaVolumeKey = payload.mediaVolumeKey();
        active.mediaLabel = payload.mediaLabel();
        active.mediaFiles = payload.mediaFiles();
        active.romEntries = payload.romEntries();
        active.machines = payload.machines();
        active.hasCard = payload.hasCard();
        if (!payload.status().isEmpty()) {
            active.status = payload.status();
        }
        active.loaded = true;
        active.selectedMedia.removeIf(i -> i >= active.mediaFiles.size());
        active.selectedRom.removeIf(i -> i >= active.romEntries.size());
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
        this.PANEL = osSkin.windowBg();
        this.EDGE = osSkin.edge();
        this.TEXT = osSkin.text();
        this.SUB = osSkin.dim();
        this.SEL_BG = osSkin.accent();
        this.HEADER_BG = osSkin.panelBg();
    }

    @Override
    public String title() {
        return "Crafting Manager";
    }

    @Override
    public int defaultWidth() {
        return 280;
    }

    @Override
    public int defaultHeight() {
        return 196;
    }

    @Override
    public int minWidth() {
        return 240;
    }

    @Override
    public int minHeight() {
        return 150;
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        lastX = x;
        lastY = y;
        lastW = width;
        lastH = height;

        g.fill(x, y, x + width, y + height, PANEL);
        outline(g, x, y, width, height);
        drawTabs(g, font, x, y, width);
        if (tab == 1) {
            renderMachines(g, font, x, y + TAB_H, width, height - TAB_H, mouseX, mouseY);
            return;
        }
        renderRecipes(g, font, x, y + TAB_H, width, height - TAB_H, mouseX, mouseY);
    }

    private void drawTabs(final GuiGraphics g, final Font font, final int x, final int y, final int width) {
        final String[] labels = {"Recipes", "Machines"};
        final int tw = width / 2;
        for (int i = 0; i < labels.length; i++) {
            final int tx = x + i * tw;
            final boolean on = tab == i;
            g.fill(tx, y, tx + tw, y + TAB_H, on ? PANEL : HEADER_BG);
            if (on) {
                g.fill(tx, y, tx + tw, y + 2, SEL_BG);
            }
            g.drawString(font, labels[i], tx + (tw - font.width(labels[i])) / 2, y + 3, on ? TEXT : SUB, false);
        }
        g.fill(x, y + TAB_H - 1, x + width, y + TAB_H, EDGE);
    }

    private void renderRecipes(final GuiGraphics g, final Font font, final int x, final int y,
                               final int width, final int height, final int mouseX, final int mouseY) {
        // Card-required banner, when no Crafting Card is installed.
        int top = y;
        if (loaded && !hasCard) {
            g.fill(x + 1, y + 1, x + width - 1, y + 1 + HEADER_H, WARN_BG);
            g.drawString(font, "A Crafting Card is required to manage recipes.",
                    x + 4, y + 3, WARN_TEXT, false);
            top = y + 1 + HEADER_H;
        }

        final int barY = y + height - BAR_H;
        final int listTop = top + HEADER_H;
        final int listBottom = barY - 1;
        final int listH = listBottom - listTop;

        final int gap = PAD;
        final int paneW = (width - PAD * 2 - gap) / 2;
        final int leftX = x + PAD;
        final int rightX = leftX + paneW + gap;

        // Pane headers.
        drawPaneHeader(g, font, leftX, top, paneW,
                mediaVolumeKey.isEmpty() ? "Removable media: none" : "Media: " + mediaLabel);
        drawPaneHeader(g, font, rightX, top, paneW, "This computer  (" + romEntries.size() + "/50)");

        // Media file list.
        mediaScroll = clampScroll(mediaScroll, mediaFiles.size(), listH);
        drawList(g, font, leftX, listTop, paneW, listH, mediaFiles.size(), mediaScroll, mouseX, mouseY,
                i -> mediaFiles.get(i), selectedMedia,
                mediaVolumeKey.isEmpty() ? "Insert a disc into a linked drive" : "No .craft files");

        // ROM list (loaded recipes).
        romScroll = clampScroll(romScroll, romEntries.size(), listH);
        drawList(g, font, rightX, listTop, paneW, listH, romEntries.size(), romScroll, mouseX, mouseY,
                i -> (romEntries.get(i).inMedia() ? "= " : "") + romEntries.get(i).name(), selectedRom,
                "No recipes loaded");

        // Load result line (e.g. "Loaded 3 crafts" / "ROM full"), drawn just above the action bar.
        if (!status.isEmpty()) {
            g.fill(x + 1, barY - 10, x + width - 1, barY, HEADER_BG);
            g.drawString(font, trim(font, status, width - 8), x + 4, barY - 9,
                    status.contains("full") ? WARN_TEXT : SUB, false);
        }

        // Action bar.
        g.fill(x + 1, barY, x + width - 1, y + height - 1, HEADER_BG);
        g.fill(x + 1, barY, x + width - 1, barY + 1, 0xFFC2C7D4);
        layoutButtons(g, font, x, barY, width, mouseX, mouseY, false);
    }

    /** Lays out (and optionally hit-tests) the four action buttons. Returns the clicked action, or -1. */
    private int layoutButtons(final GuiGraphics g, final Font font, final int x, final int barY,
                              final int width, final int mouseX, final int mouseY, final boolean hitTest) {
        // Short labels: four buttons share the bar, and the narrowest default window leaves ~55px each.
        final String[] labels = {"Load →", "Load all", "← Download", "Remove"};
        final boolean[] enabled = {
                hasCard && !selectedMedia.isEmpty(),
                hasCard && !mediaVolumeKey.isEmpty(),
                hasCard && !selectedRom.isEmpty() && !mediaVolumeKey.isEmpty(),
                hasCard && !selectedRom.isEmpty(),
        };
        final int n = labels.length;
        final int btnW = (width - PAD * (n + 1)) / n;
        final int by = barY + PAD;
        for (int i = 0; i < n; i++) {
            final int bx = x + PAD + i * (btnW + PAD);
            if (hitTest) {
                if (enabled[i] && mouseX >= bx && mouseX < bx + btnW && mouseY >= by && mouseY < by + BTN_H) {
                    return i;
                }
            } else {
                drawButton(g, font, bx, by, btnW, labels[i], enabled[i], mouseX, mouseY);
            }
        }
        return -1;
    }

    private interface RowText {
        String at(int index);
    }

    private void drawList(final GuiGraphics g, final Font font, final int x, final int y, final int w,
                          final int h, final int count, final int scroll, final int mouseX, final int mouseY,
                          final RowText rows, final Set<Integer> selected, final String emptyText) {
        g.fill(x, y, x + w, y + h, 0xFFF7F8FB);
        outlineColor(g, x, y, w, h, 0xFFC2C7D4);
        if (count == 0) {
            g.drawString(font, emptyText, x + 4, y + 4, SUB, false);
            return;
        }
        final int visible = Math.max(1, (h - 2) / ROW_H);
        for (int row = 0; row < visible; row++) {
            final int i = scroll + row;
            if (i >= count) {
                break;
            }
            final int ry = y + 1 + row * ROW_H;
            final boolean sel = selected.contains(i);
            if (sel) {
                g.fill(x + 1, ry, x + w - 1, ry + ROW_H, SEL_BG);
            }
            g.drawString(font, trim(font, rows.at(i), w - 8), x + 4, ry + 2,
                    sel ? SEL_TEXT : TEXT, false);
        }
    }

    private void drawPaneHeader(final GuiGraphics g, final Font font, final int x, final int y,
                                final int w, final String label) {
        g.fill(x, y, x + w, y + HEADER_H, HEADER_BG);
        g.drawString(font, trim(font, label, w - 6), x + 3, y + 2, 0xFF3A4256, false);
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY,
                             final int button) {
        if (button != 0 || !loaded) {
            return;
        }
        final int x = lastX;
        final int y = lastY;
        final int width = lastW;
        final int height = lastH;

        // Tab bar.
        if (mouseY >= y && mouseY < y + TAB_H) {
            tab = mouseX < x + width / 2 ? 0 : 1;
            return;
        }
        final int cy = y + TAB_H;
        final int ch = height - TAB_H;
        if (tab == 1) {
            machinesClicked(x, cy, width, ch, mouseX, mouseY);
            return;
        }

        // Action bar first.
        final Geometry geo = geometry();
        final int action = layoutButtons(null, null, x, geo.barY(), width, (int) mouseX, (int) mouseY, true);
        if (action >= 0) {
            onAction(action);
            return;
        }

        // Then the two list panes.
        if (mouseX >= geo.leftX() && mouseX < geo.leftX() + geo.paneW()
                && mouseY >= geo.listTop() && mouseY < geo.listTop() + geo.listH()) {
            toggle(selectedMedia, mediaScroll + (int) ((mouseY - geo.listTop() - 1) / ROW_H), mediaFiles.size());
        } else if (mouseX >= geo.rightX() && mouseX < geo.rightX() + geo.paneW()
                && mouseY >= geo.listTop() && mouseY < geo.listTop() + geo.listH()) {
            toggle(selectedRom, romScroll + (int) ((mouseY - geo.listTop() - 1) / ROW_H), romEntries.size());
        }
    }

    /** The Recipes tab's hit geometry for the last rendered content rectangle; the click code and the
     *  inspection helpers below share it so a test clicks exactly where the player would. */
    private record Geometry(int barY, int listTop, int listH, int paneW, int leftX, int rightX) {
    }

    private Geometry geometry() {
        final int cy = lastY + TAB_H;
        final int ch = lastH - TAB_H;
        final int barY = cy + ch - BAR_H;
        final int top = (loaded && !hasCard) ? cy + 1 + HEADER_H : cy;
        final int listTop = top + HEADER_H;
        final int listH = barY - 1 - listTop;
        final int paneW = (lastW - PAD * 2 - PAD) / 2;
        final int leftX = lastX + PAD;
        return new Geometry(barY, listTop, listH, paneW, leftX, leftX + paneW + PAD);
    }

    // --- inspection (client tests) ---

    public boolean isLoaded() {
        return loaded;
    }

    public boolean hasCard() {
        return hasCard;
    }

    public int activeTab() {
        return tab;
    }

    public String status() {
        return status;
    }

    public List<String> mediaFiles() {
        return mediaFiles;
    }

    /** The ROM entries' display names, as listed. */
    public List<String> romNames() {
        final List<String> out = new ArrayList<>();
        for (final CraftManagerStatePayload.WireRomEntry e : romEntries) {
            out.add(e.name());
        }
        return out;
    }

    public List<CraftManagerStatePayload.WireMachine> machines() {
        return machines;
    }

    /** Screen centre of the Recipes/Machines tab {@code index} (0 or 1). */
    public int[] tabCenter(final int index) {
        return new int[]{lastX + lastW / 4 + index * (lastW / 2), lastY + TAB_H / 2};
    }

    /** Screen centre of action button {@code index}: 0 Load, 1 Load missing, 2 Download, 3 Remove. */
    public int[] actionButtonCenter(final int index) {
        final int btnW = (lastW - PAD * 5) / 4;
        return new int[]{lastX + PAD + index * (btnW + PAD) + btnW / 2, geometry().barY() + PAD + BTN_H / 2};
    }

    /** Screen centre of the {@code index}-th visible row of the media (left) list. */
    public int[] mediaRowCenter(final int index) {
        final Geometry geo = geometry();
        return new int[]{geo.leftX() + geo.paneW() / 2, geo.listTop() + 1 + index * ROW_H + ROW_H / 2};
    }

    /** Screen centre of the {@code index}-th visible row of the ROM (right) list. */
    public int[] romRowCenter(final int index) {
        final Geometry geo = geometry();
        return new int[]{geo.rightX() + geo.paneW() / 2, geo.listTop() + 1 + index * ROW_H + ROW_H / 2};
    }

    private void onAction(final int action) {
        switch (action) {
            case 0 -> { // Load selected media files
                final List<String> files = new ArrayList<>();
                for (final int i : selectedMedia) {
                    if (i < mediaFiles.size()) {
                        files.add(mediaFiles.get(i));
                    }
                }
                if (!files.isEmpty()) {
                    PacketDistributor.sendToServer(
                            new LoadFromMediaPayload(host, mediaVolumeKey, files, false));
                }
            }
            case 1 -> // Load every missing file
                    PacketDistributor.sendToServer(
                            new LoadFromMediaPayload(host, mediaVolumeKey, List.of(), true));
            case 2 -> { // Download selected ROM recipes onto the medium
                if (!selectedRom.isEmpty()) {
                    PacketDistributor.sendToServer(
                            new DownloadToMediaPayload(host, mediaVolumeKey, selectedRomIndices()));
                }
            }
            case 3 -> { // Remove selected ROM recipes (and their disk mirrors)
                if (!selectedRom.isEmpty()) {
                    PacketDistributor.sendToServer(
                            new RemoveRomCraftPayload(host, selectedRomIndices()));
                    selectedRom.clear();
                }
            }
            default -> { }
        }
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        if (tab == 1) {
            machineScroll -= (int) Math.signum(delta);
            return true;
        }
        // Scroll the pane under the cursor is overkill here; scroll the ROM pane (the longer of the two)
        // when it overflows, otherwise the media pane.
        if (romEntries.size() > mediaFiles.size()) {
            romScroll -= (int) Math.signum(delta);
        } else {
            mediaScroll -= (int) Math.signum(delta);
        }
        return true;
    }

    private static void toggle(final Set<Integer> set, final int index, final int size) {
        if (index < 0 || index >= size) {
            return;
        }
        if (!set.remove(index)) {
            set.add(index);
        }
    }

    private static int clampScroll(final int scroll, final int count, final int listH) {
        final int visible = Math.max(1, (listH - 2) / ROW_H);
        final int max = Math.max(0, count - visible);
        return Math.max(0, Math.min(scroll, max));
    }

    private void drawButton(final GuiGraphics g, final Font font, final int x, final int y,
                            final int w, final String label, final boolean enabled,
                            final int mouseX, final int mouseY) {
        final boolean hover = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + BTN_H;
        final int bg = !enabled ? 0xFFECEEF3 : hover ? 0xFF3F77C8 : 0xFFDDE2EC;
        g.fill(x, y, x + w, y + BTN_H, bg);
        outlineColor(g, x, y, w, BTN_H, EDGE);
        final int fg = !enabled ? 0xFFA8AEBC : hover ? 0xFFFFFFFF : TEXT;
        g.drawString(font, trim(font, label, w - 4), x + (w - Math.min(w - 4, font.width(label))) / 2,
                y + 3, fg, false);
    }

    // --- Machines tab: a row per routed machine with its concurrency controls ---

    private int machineButtonW(final int width) {
        return Math.max(20, (width / 2 - PAD * 2) / 3);
    }

    private int machineButtonX(final int x, final int width, final int idx) {
        return x + width / 2 + PAD + idx * (machineButtonW(width) + 2);
    }

    private void renderMachines(final GuiGraphics g, final Font font, final int x, final int y,
                                final int width, final int height, final int mouseX, final int mouseY) {
        if (!hasCard) {
            g.drawString(font, "A Crafting Card is required.", x + PAD, y + PAD, SUB, false);
            return;
        }
        if (machines.isEmpty()) {
            g.drawString(font, "No machines on the crafting network.", x + PAD, y + PAD, SUB, false);
            return;
        }
        final int listH = height - PAD;
        final int visible = Math.max(1, listH / M_ROW_H);
        machineScroll = Math.max(0, Math.min(machineScroll, Math.max(0, machines.size() - visible)));
        final int bw = machineButtonW(width);
        for (int row = 0; row < visible; row++) {
            final int i = machineScroll + row;
            if (i >= machines.size()) {
                break;
            }
            final CraftManagerStatePayload.WireMachine m = machines.get(i);
            final int ry = y + PAD / 2 + row * M_ROW_H;
            g.drawString(font, trim(font, m.label(), width / 2 - PAD * 2), x + PAD, ry + 3,
                    m.active() ? TEXT : SUB, false);
            drawButton(g, font, machineButtonX(x, width, 0), ry, bw, "Jobs " + m.maxJobs(), true, mouseX, mouseY);
            drawButton(g, font, machineButtonX(x, width, 1), ry, bw, m.locked() ? "Paused" : "Run",
                    true, mouseX, mouseY);
            drawButton(g, font, machineButtonX(x, width, 2), ry, bw, m.feedMax() ? "Fill" : "One",
                    true, mouseX, mouseY);
        }
    }

    private void machinesClicked(final int x, final int y, final int width, final int height,
                                 final double mouseX, final double mouseY) {
        if (!hasCard || machines.isEmpty()) {
            return;
        }
        final int listH = height - PAD;
        final int visible = Math.max(1, listH / M_ROW_H);
        final int bw = machineButtonW(width);
        for (int row = 0; row < visible; row++) {
            final int i = machineScroll + row;
            if (i >= machines.size()) {
                break;
            }
            final CraftManagerStatePayload.WireMachine m = machines.get(i);
            final int ry = y + PAD / 2 + row * M_ROW_H;
            if (mouseY < ry || mouseY >= ry + BTN_H) {
                continue;
            }
            for (int b = 0; b < 3; b++) {
                final int bx = machineButtonX(x, width, b);
                if (mouseX >= bx && mouseX < bx + bw) {
                    int jobs = m.maxJobs();
                    boolean locked = m.locked();
                    boolean fill = m.feedMax();
                    switch (b) {
                        case 0 -> jobs = jobs >= MAX_JOBS ? 1 : jobs + 1;
                        case 1 -> locked = !locked;
                        case 2 -> fill = !fill;
                        default -> { }
                    }
                    PacketDistributor.sendToServer(
                            new dev.jsc.jscomputronics.module.computing.operation.payload.SetMachineConfigPayload(
                                    host, m.key(), jobs, locked, fill));
                    return;
                }
            }
        }
    }

    private static String trim(final Font font, final String s, final int maxW) {
        String out = s;
        while (out.length() > 2 && font.width(out) > maxW) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private void outline(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        outlineColor(g, x, y, w, h, EDGE);
    }

    private static void outlineColor(final GuiGraphics g, final int x, final int y, final int w, final int h,
                                     final int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }
}
