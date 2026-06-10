/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client;

import dev.jsc.jscomputronics.module.computing.menu.ComputerTerminalMenu;
import dev.jsc.jscomputronics.module.computing.operation.payload.NetworkItemEntry;
import dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestServerBreakdownPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.ServerBreakdownPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.TerminalInsertPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.TerminalLocalDepositPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.TerminalLocalUploadPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.TerminalLocalWithdrawPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.NetworkServersPayload;
import dev.jsc.jscomputronics.module.computing.storage.StorageKey;
import dev.jsc.jscomputronics.module.computing.operation.payload.TerminalSelectPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Screen for the Monitor terminal: a left tab rail (icon over name) and a content area, drawn as a flat dark "computer OS" with square edges and a cyan accent, with the player inventory pinned along the bottom so every tab is usable.
 */
public class ComputerTerminalScreen extends AbstractContainerScreen<ComputerTerminalMenu> {

    // Flat palette (ARGB), sourced from the shared OS theme so the Monitor terminal never drifts from
    // the other computing GUIs. No rounded corners anywhere.
    private static final int OUTER = JscOsTheme.OUTER;
    private static final int SCREEN = JscOsTheme.SCREEN;
    private static final int RAIL = JscOsTheme.RAIL;
    private static final int PANEL = JscOsTheme.PANEL;
    private static final int LINE = JscOsTheme.LINE;
    private static final int TRACK = JscOsTheme.TRACK;
    private static final int SLOT_BG = JscOsTheme.SLOT_BG;
    private static final int SLOT_EDGE = JscOsTheme.SLOT_EDGE;
    private static final int ACCENT = JscOsTheme.ACCENT;
    private static final int ACCENT2 = JscOsTheme.ACCENT2;
    private static final int GREEN = JscOsTheme.GREEN;
    private static final int AMBER = JscOsTheme.AMBER;
    private static final int RED = JscOsTheme.RED;
    private static final int TEXT = JscOsTheme.TEXT;
    private static final int DIM = JscOsTheme.DIM;
    private static final int TAB_ON = JscOsTheme.TAB_ON;

    private static final int RAIL_X = 4;
    private static final int RAIL_W = 56;
    private static final int TAB_Y0 = 6;
    private static final int TAB_H = 28;
    private static final int CONTENT_X = 63;

    // Network item grid (a virtual grid — not real slots; rendered from the snapshot).
    private static final int NET_X = 68;
    private static final int NET_COLS = 9;
    private static final int NET_ROWS = 4;
    private static final int NET_Y = 52;

    // Toolbar above the grid: a search field on the left, a sort toggle on the right.
    private static final int TOOLBAR_Y = 36;
    private static final int TOOLBAR_H = 12;
    private static final int TOOLBAR_W = NET_COLS * 18 - 2;
    private static final int SORT_W = 46;
    private static final int SORT_X = NET_X + TOOLBAR_W - SORT_W;
    private static final int SEARCH_W = TOOLBAR_W - SORT_W - 4;

    // Deposit bar — the explicit "insert held items into the network" target, sitting
    // just below the item grid and above the player inventory.
    private static final int DEPOSIT_Y = NET_Y + NET_ROWS * 18 + 2;
    private static final int DEPOSIT_W = NET_COLS * 18 - 2;
    private static final int DEPOSIT_H = 14;

    private static final String[] TAB_NAMES = {"Local", "Storage", "Network", "Operations", "Tasks"};

    private int netScrollRow;
    private int selectedOp;
    private int opScroll;
    private int taskSubTab;

    @org.jetbrains.annotations.Nullable
    private EditBox searchBox;
    private boolean sortByQuantity = true;

    // Operations tab layout (content-relative).
    private static final int OPS_ROWS = 4;

    private static final String[] TASK_SUBTABS = {"Processes", "Hardware", "Devices"};

    // Request popup state (open only while popupEntry != null).
    private static final int POPUP_W = 204;
    private static final int POPUP_H = 178;
    private static final int[] STEP_AMOUNTS = {-1000, -100, -10, -1, 1, 10, 100, 1000};
    private static final String[] STEP_LABELS = {"----", "---", "--", "-", "+", "++", "+++", "++++"};
    private EditBox qtyBox;
    private boolean syncingQty;
    @org.jetbrains.annotations.Nullable
    private NetworkItemEntry popupEntry;
    private int popupQty = 1;
    private boolean popupFromStorage;
    private final Set<String> deselectedServers = new HashSet<>();
    private boolean advancedMode;
    private int destServerIndex;
    private static final int REQ_H_SIMPLE = 102;
    private static final int REQ_H_ADVANCED = 200;

    @org.jetbrains.annotations.Nullable
    private OperationRecord popupOp;
    private int opPopupScroll;
    private static final int OP_POPUP_ROWS = 7;
    private static final int TASK_OP_ROWS = 4;

    public ComputerTerminalScreen(final ComputerTerminalMenu menu, final Inventory inventory,
                                  final Component title) {
        super(menu, inventory, title);
        this.imageWidth = 244;
        this.imageHeight = 230;
        // The terminal draws all of its own labels.
        this.titleLabelX = -10000;
        this.inventoryLabelY = -10000;
    }

    @Override
    protected void init() {
        super.init();
        final EditBox box = new EditBox(font, leftPos + NET_X + 4, topPos + TOOLBAR_Y + 2,
                SEARCH_W - 8, TOOLBAR_H - 3, Component.literal("Search"));
        box.setBordered(false);
        box.setTextColor(TEXT);
        box.setMaxLength(48);
        box.setHint(Component.literal("Search items...").withStyle(ChatFormatting.DARK_GRAY));
        box.setResponder(s -> netScrollRow = 0);
        addRenderableWidget(box);
        searchBox = box;

        // The request popup's editable quantity field (digits only); visible only while the popup is up.
        final EditBox qty = new EditBox(font, popupX() + 8, popupY() + 30, 118, 14, Component.literal("Qty"));
        qty.setTextColor(TEXT);
        qty.setMaxLength(12);
        qty.setFilter(s -> s.isEmpty() || s.chars().allMatch(Character::isDigit));
        qty.setResponder(this::onQtyTyped);
        qty.visible = false;
        qty.active = false;
        addRenderableWidget(qty);
        qtyBox = qty;

        syncSearchBoxVisibility();
    }

    private void syncSearchBoxVisibility() {
        if (searchBox == null) {
            return;
        }
        final boolean show = isGridTab() && popupEntry == null && popupOp == null;
        searchBox.visible = show;
        searchBox.active = show;
        if (!show) {
            searchBox.setFocused(false);
        }
        if (qtyBox != null) {
            final boolean p = popupEntry != null;
            qtyBox.visible = p;
            qtyBox.active = p;
            if (!p) {
                qtyBox.setFocused(false);
            }
        }
    }

    private void onQtyTyped(final String s) {
        if (popupEntry == null || syncingQty) {
            return;
        }
        try {
            final long v = s.isEmpty() ? 0L : Long.parseLong(s);
            popupQty = (int) Math.max(0L, Math.min((long) Integer.MAX_VALUE, Math.min(popupEntry.total(), v)));
        } catch (final NumberFormatException ignored) {
            // Over-long input: leave the last valid quantity in place.
        }
    }

    private void setPopupQty(final int value) {
        final int max = (int) Math.max(1L, Math.min((long) Integer.MAX_VALUE,
                popupEntry == null ? 1L : popupEntry.total()));
        popupQty = Math.max(1, Math.min(max, value));
        if (qtyBox != null) {
            syncingQty = true;
            qtyBox.setValue(String.valueOf(popupQty));
            syncingQty = false;
        }
    }

    private int tabCount() {
        return menu.mainframeHost() ? 5 : 4;
    }

    private int contentW() {
        return imageWidth - CONTENT_X - 6;
    }

    // Background

    @Override
    protected void renderBg(final GuiGraphics g, final float partialTick, final int mouseX, final int mouseY) {
        final int x = leftPos;
        final int y = topPos;

        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, OUTER);
        g.fill(x, y, x + imageWidth, y + imageHeight, SCREEN);

        // Tab rail.
        g.fill(x + RAIL_X, y + TAB_Y0, x + RAIL_X + RAIL_W, y + TAB_Y0 + 140, RAIL);
        g.fill(x + RAIL_X + RAIL_W, y + TAB_Y0, x + RAIL_X + RAIL_W + 1, y + TAB_Y0 + 140, LINE);
        for (int i = 0; i < tabCount(); i++) {
            final int tx = x + RAIL_X;
            final int ty = y + TAB_Y0 + i * TAB_H;
            final boolean on = i == menu.activeTab();
            if (on) {
                g.fill(tx, ty, tx + RAIL_W, ty + TAB_H, TAB_ON);
                g.fill(tx, ty, tx + 2, ty + TAB_H, ACCENT);
            }
            icon(g, i, tx + (RAIL_W - 16) / 2, ty + 3, on ? ACCENT : DIM);
        }

        // Content header bar.
        final int cx = x + CONTENT_X;
        final int cy = y + 6;
        final int cw = contentW();
        g.fill(cx, cy, cx + cw, cy + 16, PANEL);
        g.fill(cx, cy + 16, cx + cw, cy + 17, LINE);

        switch (menu.activeTab()) {
            case ComputerTerminalMenu.TAB_LOCAL -> localBg(g, cx, cy, cw);
            case ComputerTerminalMenu.TAB_STORAGE -> storageBg(g, x, y, cx, cy, cw);
            case ComputerTerminalMenu.TAB_NETWORK -> networkBg(g, x, y);
            case ComputerTerminalMenu.TAB_OPS -> opsBg(g, cx, cy, cw);
            case ComputerTerminalMenu.TAB_TASKS -> tasksBg(g, cx, cy, cw);
            default -> { /* nothing */ }
        }

        // Player inventory backgrounds (always visible).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                slotBg(g, x + ComputerTerminalMenu.INV_X + col * 18, y + ComputerTerminalMenu.INV_Y + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            slotBg(g, x + ComputerTerminalMenu.INV_X + col * 18, y + ComputerTerminalMenu.HOTBAR_Y);
        }
    }

    private void localBg(final GuiGraphics g, final int cx, final int cy, final int cw) {
        final int tileW = (cw - 8) / 3;
        for (int i = 0; i < 3; i++) {
            final int tx = cx + i * (tileW + 4);
            g.fill(tx, cy + 26, tx + tileW, cy + 52, PANEL);
            g.fill(tx, cy + 26, tx + tileW, cy + 27, LINE);
        }
        // Hardware-population bars — inline (label · track · value on one row).
        inlineTrack(g, cx, cy + 70, cw, frac(menu.installedCpus(), menu.cpuSlots()), ACCENT2);
        inlineTrack(g, cx, cy + 82, cw, frac(menu.installedRam(), menu.ramSlots()), ACCENT2);
        inlineTrack(g, cx, cy + 94, cw, frac(menu.installedGpus(), menu.gpuSlots()), ACCENT);
        inlineTrack(g, cx, cy + 106, cw, frac(menu.installedDisks(), menu.diskSlots()), ACCENT);
        final long cap = menu.storageCapacity();
        final double sf = cap <= 0 ? 0 : Math.min(1.0, (double) menu.storageUsed() / cap);
        inlineTrack(g, cx, cy + 122, cw, sf, cap <= 0 ? DIM : (sf > 0.9 ? RED : GREEN));
    }

    private void inlineTrack(final GuiGraphics g, final int x, final int y, final int w,
                             final double f, final int color) {
        final int tx = x + 36;
        final int tw = w - 36 - 44;
        g.fill(tx, y + 1, tx + tw, y + 8, TRACK);
        g.fill(tx, y + 1, tx + tw, y + 2, LINE);
        final int fw = (int) Math.round((tw - 2) * Math.max(0, Math.min(1, f)));
        if (fw > 0) {
            g.fill(tx + 1, y + 2, tx + 1 + fw, y + 7, color);
        }
    }

    private void storageBg(final GuiGraphics g, final int x, final int y,
                           final int cx, final int cy, final int cw) {
        // The Storage tab is a disk-backed quantity view: the same item grid + deposit bar as the
        // Network tab, drawn from the local-storage snapshot (visibleItems() sources it by tab).
        networkBg(g, x, y);
    }

    private static void slotBg(final GuiGraphics g, final int x, final int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_EDGE);
        g.fill(x, y, x + 16, y + 16, SLOT_BG);
    }

    private static double frac(final int a, final int b) {
        return b <= 0 ? 0 : Math.min(1.0, (double) a / b);
    }

    private void track(final GuiGraphics g, final int x, final int y, final int w,
                       final double f, final int color) {
        g.fill(x, y, x + w, y + 8, TRACK);
        g.fill(x, y, x + w, y + 1, LINE);
        final int fw = (int) Math.round((w - 2) * Math.max(0, Math.min(1, f)));
        if (fw > 0) {
            g.fill(x + 1, y + 1, x + 1 + fw, y + 7, color);
        }
    }

    // Labels / text

    @Override
    protected void renderLabels(final GuiGraphics g, final int mouseX, final int mouseY) {
        final int cx = CONTENT_X;
        final int cy = 6;
        final int cw = contentW();

        // Tab names.
        for (int i = 0; i < tabCount(); i++) {
            final int ty = TAB_Y0 + i * TAB_H;
            g.drawCenteredString(font, TAB_NAMES[i], RAIL_X + RAIL_W / 2, ty + 19,
                    i == menu.activeTab() ? ACCENT : DIM);
        }

        // Header: computer name + status pill.
        g.drawString(font, this.title, cx + 6, cy + 5, TEXT, false);
        final int netState = menu.networkLinkState();
        final String status;
        final int statusColor;
        if (netState == 2) {
            status = "CONFLICT";
            statusColor = RED;
        } else if (!menu.buildValid()) {
            status = "OFFLINE";
            statusColor = RED;
        } else if (menu.running()) {
            status = "ONLINE";
            statusColor = GREEN;
        } else {
            status = "READY";
            statusColor = AMBER;
        }
        g.drawString(font, status, cx + cw - font.width(status) - 6, cy + 5, statusColor, false);

        switch (menu.activeTab()) {
            case ComputerTerminalMenu.TAB_LOCAL -> localLabels(g, cx, cy, cw);
            case ComputerTerminalMenu.TAB_STORAGE -> storageLabels(g, cx, cy, cw);
            case ComputerTerminalMenu.TAB_NETWORK -> networkLabels(g, cx, cy, cw);
            case ComputerTerminalMenu.TAB_OPS -> opsLabels(g, cx, cy, cw);
            case ComputerTerminalMenu.TAB_TASKS -> tasksLabels(g, cx, cy, cw);
            default -> placeholder(g, cx, cy, "Not available yet");
        }
    }

    private void placeholder(final GuiGraphics g, final int cx, final int cy, final String text) {
        g.drawString(font, text, cx + 6, cy + 28, DIM, false);
    }

    private void localLabels(final GuiGraphics g, final int cx, final int cy, final int cw) {
        final int tileW = (cw - 8) / 3;
        tile(g, cx + 0 * (tileW + 4), cy + 26, "CAPACITY", fmt(menu.capacity()), "it/t");
        tile(g, cx + 1 * (tileW + 4), cy + 26, "QUEUES", String.valueOf(menu.queues()), "");
        tile(g, cx + 2 * (tileW + 4), cy + 26, "RAM BUF", fmt(menu.ramBuffer()), "it");

        g.drawString(font, "HARDWARE", cx, cy + 56, DIM, false);
        final int net = menu.networkLinkState();
        final String netStr = net == 2 ? "conflict"
                : net == 1 ? menu.serverCount() + " servers" : "offline";
        g.drawString(font, netStr, cx + cw - font.width(netStr), cy + 56, net == 2 ? RED : DIM, false);

        barLabel(g, cx, cy + 70, cw, "CPU", menu.installedCpus() + "/" + menu.cpuSlots());
        barLabel(g, cx, cy + 82, cw, "RAM", menu.installedRam() + "/" + menu.ramSlots());
        barLabel(g, cx, cy + 94, cw, "GPU", menu.installedGpus() + "/" + menu.gpuSlots());
        barLabel(g, cx, cy + 106, cw, "Disk", menu.installedDisks() + "/" + menu.diskSlots());

        final String store = menu.storageCapacity() <= 0 ? "no disk"
                : fmt(menu.storageUsed()) + "/" + fmt(menu.storageCapacity());
        barLabel(g, cx, cy + 122, cw, "Storage", store);
    }

    private void storageLabels(final GuiGraphics g, final int cx, final int cy, final int cw) {
        final String cap = menu.storageCapacity() <= 0 ? "no disk"
                : fmt(menu.storageUsed()) + "/" + fmt(menu.storageCapacity());
        g.drawString(font, "LOCAL STORAGE  " + cap, cx, cy + 20, DIM, false);
        final int shown = visibleItems().size();
        final String t = shown + (shown == 1 ? " type" : " types");
        g.drawString(font, t, cx + cw - font.width(t), cy + 20, DIM, false);
        g.drawCenteredString(font, sortByQuantity ? "Qty" : "Name", SORT_X + SORT_W / 2,
                TOOLBAR_Y + 3, ACCENT);
        final boolean holding = !menu.getCarried().isEmpty();
        g.drawCenteredString(font, "DEPOSIT TO STORAGE", NET_X + DEPOSIT_W / 2, DEPOSIT_Y + 3,
                holding ? ACCENT : DIM);
    }

    // Network tab — a virtual item grid drawn from the snapshot

    private void networkBg(final GuiGraphics g, final int x, final int y) {
        // Toolbar: search field box + sort toggle.
        final int tbx = x + NET_X;
        final int tby = y + TOOLBAR_Y;
        g.fill(tbx, tby, tbx + SEARCH_W, tby + TOOLBAR_H, TRACK);
        g.fill(tbx, tby, tbx + SEARCH_W, tby + 1, LINE);
        final int sbx = x + SORT_X;
        g.fill(sbx, tby, sbx + SORT_W, tby + TOOLBAR_H, PANEL);
        g.fill(sbx, tby, sbx + SORT_W, tby + 1, LINE);

        final List<NetworkItemEntry> items = visibleItems();
        final int start = clampScroll(items.size()) * NET_COLS;
        for (int row = 0; row < NET_ROWS; row++) {
            for (int col = 0; col < NET_COLS; col++) {
                final int sx = x + NET_X + col * 18;
                final int sy = y + NET_Y + row * 18;
                slotBg(g, sx, sy);
                final int idx = start + row * NET_COLS + col;
                if (idx < items.size()) {
                    final NetworkItemEntry e = items.get(idx);
                    drawDataIcon(g, e.key(), e.total(), sx, sy);
                }
            }
        }
        depositBar(g, x, y);
    }

    private List<NetworkItemEntry> visibleItems() {
        final String q = searchBox == null ? "" : searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        final List<NetworkItemEntry> out = new ArrayList<>();
        final List<NetworkItemEntry> source = menu.activeTab() == ComputerTerminalMenu.TAB_STORAGE
                ? menu.localItems() : menu.networkItems();
        for (final NetworkItemEntry e : source) {
            if (q.isEmpty()
                    || e.name().getString().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(e);
            }
        }
        if (sortByQuantity) {
            out.sort((a, b) -> Long.compare(b.total(), a.total()));
        } else {
            out.sort((a, b) -> a.name().getString()
                    .compareToIgnoreCase(b.name().getString()));
        }
        return out;
    }

    private void depositBar(final GuiGraphics g, final int x, final int y) {
        final int dx = x + NET_X;
        final int dy = y + DEPOSIT_Y;
        final boolean holding = !menu.getCarried().isEmpty();
        g.fill(dx - 1, dy - 1, dx + DEPOSIT_W + 1, dy + DEPOSIT_H + 1, holding ? ACCENT : SLOT_EDGE);
        g.fill(dx, dy, dx + DEPOSIT_W, dy + DEPOSIT_H, holding ? 0xFF123038 : TRACK);
        // Down-arrow glyph (deposit into the network).
        final int gx = dx + 6;
        final int gy = dy + 3;
        final int gc = holding ? ACCENT : DIM;
        g.fill(gx + 2, gy, gx + 4, gy + 5, gc);
        g.fill(gx, gy + 4, gx + 6, gy + 5, gc);
        g.fill(gx + 1, gy + 5, gx + 5, gy + 6, gc);
        g.fill(gx + 2, gy + 6, gx + 4, gy + 7, gc);
    }

    private void networkLabels(final GuiGraphics g, final int cx, final int cy, final int cw) {
        g.drawString(font, "NETWORK", cx, cy + 20, DIM, false);
        final int shown = visibleItems().size();
        final String t = shown + (shown == 1 ? " item" : " items");
        g.drawString(font, t, cx + cw - font.width(t), cy + 20, DIM, false);
        // Sort toggle caption.
        g.drawCenteredString(font, sortByQuantity ? "Qty" : "Name", SORT_X + SORT_W / 2,
                TOOLBAR_Y + 3, ACCENT);
        // Deposit-bar caption (centred in the strip; brightens while holding an item).
        final boolean holding = !menu.getCarried().isEmpty();
        g.drawCenteredString(font, "DEPOSIT TO NETWORK", NET_X + DEPOSIT_W / 2, DEPOSIT_Y + 3,
                holding ? ACCENT : DIM);
    }

    private int clampScroll(final int count) {
        final int rows = (count + NET_COLS - 1) / NET_COLS;
        final int max = Math.max(0, rows - NET_ROWS);
        netScrollRow = Math.max(0, Math.min(max, netScrollRow));
        return netScrollRow;
    }

    @org.jetbrains.annotations.Nullable
    private NetworkItemEntry networkItemAt(final int mx, final int my) {
        final int relX = mx - (leftPos + NET_X);
        final int relY = my - (topPos + NET_Y);
        if (relX < 0 || relY < 0 || relX % 18 > 16 || relY % 18 > 16) {
            return null;
        }
        final int col = relX / 18;
        final int row = relY / 18;
        if (col >= NET_COLS || row >= NET_ROWS) {
            return null;
        }
        final List<NetworkItemEntry> items = visibleItems();
        final int idx = (netScrollRow + row) * NET_COLS + col;
        return idx >= 0 && idx < items.size() ? items.get(idx) : null;
    }

    // Operations tab — recent network Operations + provenance detail

    private void opsBg(final GuiGraphics g, final int cx, final int cy, final int cw) {
        final List<OperationRecord> ops = menu.operationsLog();
        final int start = clampOpScroll(ops.size());
        for (int i = 0; i < OPS_ROWS && start + i < ops.size(); i++) {
            final int ry = cy + 32 + i * 12;
            final boolean sel = (start + i) == selectedOp;
            g.fill(cx, ry, cx + cw, ry + 11, sel ? TAB_ON : PANEL);
            if (sel) {
                g.fill(cx, ry, cx + 2, ry + 11, ACCENT);
            }
        }
        // Scrollbar on the list's right edge whenever the history overflows the visible rows.
        if (ops.size() > OPS_ROWS) {
            final int trackTop = cy + 32;
            final int trackH = OPS_ROWS * 12 - 1;
            final int maxOff = ops.size() - OPS_ROWS;
            final int thumbH = Math.max(8, trackH * OPS_ROWS / ops.size());
            final int thumbY = trackTop + (trackH - thumbH) * start / maxOff;
            g.fill(cx + cw - 2, trackTop, cx + cw, trackTop + trackH, LINE);
            g.fill(cx + cw - 2, thumbY, cx + cw, thumbY + thumbH, ACCENT);
        }
        g.fill(cx, cy + 90, cx + cw, cy + 140, PANEL);
        g.fill(cx, cy + 90, cx + cw, cy + 91, LINE);
        if (selectedOp >= 0 && selectedOp < ops.size()) {
            drawDataIcon(g, ops.get(selectedOp).key(), -1L, cx + 5, cy + 96);
        }
    }

    private void opsLabels(final GuiGraphics g, final int cx, final int cy, final int cw) {
        g.drawString(font, "OPERATIONS", cx, cy + 20, DIM, false);
        final List<OperationRecord> ops = menu.operationsLog();
        final String n = ops.size() + (ops.size() == 1 ? " op" : " ops");
        g.drawString(font, n, cx + cw - font.width(n), cy + 20, DIM, false);
        if (ops.isEmpty()) {
            g.drawString(font, "No operations yet.", cx, cy + 40, DIM, false);
            return;
        }
        final int start = clampOpScroll(ops.size());
        for (int i = 0; i < OPS_ROWS && start + i < ops.size(); i++) {
            opListRow(g, cx, cy + 34 + i * 12, cw, ops.get(start + i));
        }
        if (selectedOp >= 0 && selectedOp < ops.size()) {
            final OperationRecord op = ops.get(selectedOp);
            g.drawString(font, op.name().getString(), cx + 24, cy + 96, TEXT, false);
            final String sub = fmt(op.moved()) + " of " + fmt(op.requested()) + "  " + statusLabel(op.status());
            g.drawString(font, sub, cx + 24, cy + 106, statusColor(op.status()), false);
            // At most two provenance rows fit in the box; if there are more sources,
            // the second row is replaced by a one-line summary so nothing overflows.
            final List<OperationRecord.MoveRow> mv = op.moves();
            if (!mv.isEmpty()) {
                moveRow(g, cx, cy + 118, mv.get(0));
                if (mv.size() == 2) {
                    moveRow(g, cx, cy + 128, mv.get(1));
                } else if (mv.size() > 2) {
                    g.drawString(font, "+" + (mv.size() - 1) + " more sources", cx + 6, cy + 128, DIM, false);
                }
            }
        }
    }

    private void moveRow(final GuiGraphics g, final int cx, final int my, final OperationRecord.MoveRow mv) {
        g.drawString(font, font.plainSubstrByWidth(mv.from(), 62), cx + 6, my, DIM, false);
        g.drawString(font, ">", cx + 72, my, ACCENT, false);
        g.drawString(font, font.plainSubstrByWidth(fmt(mv.qty()) + " " + mv.to(), POPUP_W - 88),
                cx + 82, my, TEXT, false);
    }

    private static int statusColor(final byte status) {
        return switch (status) {
            case OperationRecord.STATUS_COMPLETED -> GREEN;
            case OperationRecord.STATUS_PARTIAL -> AMBER;
            case OperationRecord.STATUS_PROCESSING -> ACCENT2;
            default -> RED;
        };
    }

    private static String statusLabel(final byte status) {
        return switch (status) {
            case OperationRecord.STATUS_COMPLETED -> "COMPLETED";
            case OperationRecord.STATUS_PARTIAL -> "PARTIAL";
            case OperationRecord.STATUS_PROCESSING -> "PROCESSING";
            default -> "FAILED";
        };
    }

    private static String opTypeLabel(final byte type) {
        return switch (type) {
            case OperationRecord.TYPE_INSERT -> "INSERT";
            case OperationRecord.TYPE_DELETE -> "DELETE";
            case OperationRecord.TYPE_MOVE -> "MOVE";
            default -> "SELECT";
        };
    }

    private static int opTypeColor(final byte type) {
        return switch (type) {
            case OperationRecord.TYPE_INSERT -> AMBER;
            case OperationRecord.TYPE_DELETE -> RED;
            case OperationRecord.TYPE_MOVE -> GREEN;
            default -> ACCENT2;
        };
    }

    // Task Manager tab (Mainframe only): Processes / Hardware / Devices

    private void tasksBg(final GuiGraphics g, final int cx, final int cy, final int cw) {
        final int sw = cw / 3;
        g.fill(cx + taskSubTab * sw + 4, cy + 36, cx + (taskSubTab + 1) * sw - 4, cy + 37, ACCENT);
        g.fill(cx, cy + 38, cx + cw, cy + 39, LINE);
        if (taskSubTab == 0 || taskSubTab == 1) {
            tilesBg(g, cx, cy + 44, cw);
        }
        if (taskSubTab == 1) {
            inlineTrack(g, cx, cy + 90, cw, frac(menu.installedCpus(), menu.cpuSlots()), ACCENT2);
            inlineTrack(g, cx, cy + 102, cw, frac(menu.installedRam(), menu.ramSlots()), ACCENT2);
            inlineTrack(g, cx, cy + 114, cw, frac(menu.installedGpus(), menu.gpuSlots()), ACCENT);
            inlineTrack(g, cx, cy + 126, cw, frac(menu.installedDisks(), menu.diskSlots()), ACCENT);
        }
    }

    private void tilesBg(final GuiGraphics g, final int x, final int y, final int cw) {
        final int tileW = (cw - 8) / 3;
        for (int i = 0; i < 3; i++) {
            final int tx = x + i * (tileW + 4);
            g.fill(tx, y, tx + tileW, y + 28, PANEL);
            g.fill(tx, y, tx + tileW, y + 1, LINE);
        }
    }

    private void tasksLabels(final GuiGraphics g, final int cx, final int cy, final int cw) {
        final int sw = cw / 3;
        for (int i = 0; i < 3; i++) {
            g.drawCenteredString(font, TASK_SUBTABS[i], cx + i * sw + sw / 2, cy + 28,
                    i == taskSubTab ? ACCENT : DIM);
        }
        switch (taskSubTab) {
            case 0 -> tasksProcesses(g, cx, cy, cw);
            case 1 -> tasksHardware(g, cx, cy, cw);
            default -> tasksDevices(g, cx, cy, cw);
        }
    }

    private void tasksProcesses(final GuiGraphics g, final int cx, final int cy, final int cw) {
        final int tileW = (cw - 8) / 3;
        final List<OperationRecord> active = menu.activeOps();
        tile(g, cx, cy + 44, "IN FLIGHT", String.valueOf(active.size()), "");
        tile(g, cx + tileW + 4, cy + 44, "PENDING", String.valueOf(menu.pendingOps()), "");
        tile(g, cx + 2 * (tileW + 4), cy + 44, "DONE", fmt(menu.completedOps()), "");
        g.drawString(font, "IN PROGRESS", cx, cy + 78, DIM, false);
        if (active.isEmpty()) {
            g.drawString(font, "Idle - no Operations running.", cx, cy + 90, DIM, false);
            return;
        }
        for (int i = 0; i < TASK_OP_ROWS && i < active.size(); i++) {
            taskOpRow(g, cx, cy + 90 + i * 14, cw, active.get(i));
        }
        if (active.size() > TASK_OP_ROWS) {
            g.drawString(font, "+" + (active.size() - TASK_OP_ROWS) + " more",
                    cx, cy + 90 + TASK_OP_ROWS * 14, DIM, false);
        }
    }

    private void taskOpRow(final GuiGraphics g, final int cx, final int ry, final int cw,
                           final OperationRecord op) {
        final byte type = op.type();
        g.drawString(font, opTypeLabel(type), cx + 4, ry, opTypeColor(type), false);
        final double f = op.requested() <= 0 ? 0 : Math.min(1.0, (double) op.moved() / op.requested());
        final String pct = (int) Math.round(f * 100) + "%";
        final int nameW = Math.max(0, cw - 44 - font.width(pct) - 8);
        g.drawString(font, font.plainSubstrByWidth(op.name().getString(), nameW),
                cx + 44, ry, TEXT, false);
        g.drawString(font, pct, cx + cw - font.width(pct) - 4, ry, ACCENT, false);
        track(g, cx + 4, ry + 9, cw - 8, f, ACCENT2);
    }

    private void opListRow(final GuiGraphics g, final int cx, final int ry, final int cw,
                           final OperationRecord op) {
        final byte type = op.type();
        g.drawString(font, opTypeLabel(type), cx + 4, ry, opTypeColor(type), false);
        final String q = fmt(op.moved());
        final int nameW = Math.max(0, cw - 44 - font.width(q) - 8);
        final String name = font.plainSubstrByWidth(op.name().getString(), nameW);
        g.drawString(font, name, cx + 44, ry, TEXT, false);
        g.drawString(font, q, cx + cw - font.width(q) - 4, ry, statusColor(op.status()), false);
    }

    private void tasksHardware(final GuiGraphics g, final int cx, final int cy, final int cw) {
        final int tileW = (cw - 8) / 3;
        tile(g, cx, cy + 44, "CAPACITY", fmt(menu.capacity()), "it/t");
        tile(g, cx + tileW + 4, cy + 44, "QUEUES", String.valueOf(menu.queues()), "");
        tile(g, cx + 2 * (tileW + 4), cy + 44, "RAM BUF", fmt(menu.ramBuffer()), "it");
        g.drawString(font, "HARDWARE", cx, cy + 78, DIM, false);
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
        g.drawString(font, "Network storage", cx, cy + 110, DIM, false);
        final String st = fmt(menu.storageUsed()) + " / " + fmt(menu.storageCapacity());
        g.drawString(font, st, cx + cw - font.width(st), cy + 110, TEXT, false);
    }

    private void deviceRow(final GuiGraphics g, final int cx, final int y, final int cw,
                           final String name, final int count) {
        g.drawString(font, name, cx + 4, y, TEXT, false);
        final String c = String.valueOf(count);
        g.drawString(font, c, cx + cw - font.width(c) - 4, y, count > 0 ? GREEN : DIM, false);
    }

    private int taskSubTabAt(final int mx, final int my) {
        final int barY = topPos + 6 + 26;
        if (my < barY || my >= barY + 14) {
            return -1;
        }
        final int sw = contentW() / 3;
        final int rel = mx - (leftPos + CONTENT_X);
        if (rel < 0) {
            return -1;
        }
        final int sub = rel / sw;
        return sub >= 0 && sub < 3 ? sub : -1;
    }

    private int clampOpScroll(final int size) {
        final int max = Math.max(0, size - OPS_ROWS);
        opScroll = Math.max(0, Math.min(max, opScroll));
        return opScroll;
    }

    private int opsRowAt(final int mx, final int my) {
        if (mx < leftPos + CONTENT_X || mx >= leftPos + imageWidth - 6) {
            return -1;
        }
        final int rel = my - (topPos + 6 + 32);
        if (rel < 0) {
            return -1;
        }
        final int row = rel / 12;
        if (row < 0 || row >= OPS_ROWS) {
            return -1;
        }
        final int idx = opScroll + row;
        return idx < menu.operationsLog().size() ? idx : -1;
    }

    private void tile(final GuiGraphics g, final int x, final int y, final String key,
                      final String value, final String unit) {
        g.drawString(font, key, x + 4, y + 4, DIM, false);
        g.drawString(font, value, x + 4, y + 14, TEXT, false);
        if (!unit.isEmpty()) {
            g.drawString(font, unit, x + 5 + font.width(value), y + 16, DIM, false);
        }
    }

    private void barLabel(final GuiGraphics g, final int x, final int y, final int w,
                          final String label, final String value) {
        g.drawString(font, label, x, y, TEXT, false);
        g.drawString(font, value, x + w - font.width(value), y, DIM, false);
    }

    // Tab icons (drawn as flat pixel glyphs so they stay crisp at any GUI scale)

    private void icon(final GuiGraphics g, final int tab, final int x, final int y, final int c) {
        switch (tab) {
            case 0 -> { // Local — ascending stat bars
                g.fill(x + 2, y + 9, x + 5, y + 14, c);
                g.fill(x + 6, y + 6, x + 9, y + 14, c);
                g.fill(x + 10, y + 3, x + 13, y + 14, c);
            }
            case 1 -> { // Storage — stacked drive bays
                g.fill(x + 2, y + 3, x + 14, y + 6, c);
                g.fill(x + 2, y + 7, x + 14, y + 10, c);
                g.fill(x + 2, y + 11, x + 14, y + 14, c);
            }
            case 2 -> { // Network — hub and spokes
                g.fill(x + 3, y + 7, x + 13, y + 9, c);
                g.fill(x + 7, y + 3, x + 9, y + 13, c);
                g.fill(x + 6, y + 6, x + 10, y + 10, c);
                g.fill(x + 6, y + 2, x + 10, y + 4, c);
                g.fill(x + 6, y + 12, x + 10, y + 14, c);
                g.fill(x + 2, y + 6, x + 4, y + 10, c);
                g.fill(x + 12, y + 6, x + 14, y + 10, c);
            }
            case 3 -> { // Operations — bulleted list
                for (int r = 0; r < 3; r++) {
                    final int ly = y + 3 + r * 4;
                    g.fill(x + 2, ly, x + 4, ly + 2, c);
                    g.fill(x + 5, ly, x + 14, ly + 2, c);
                }
            }
            default -> { // Tasks — CPU chip
                g.fill(x + 4, y + 4, x + 12, y + 5, c);
                g.fill(x + 4, y + 11, x + 12, y + 12, c);
                g.fill(x + 4, y + 4, x + 5, y + 12, c);
                g.fill(x + 11, y + 4, x + 12, y + 12, c);
                g.fill(x + 7, y + 7, x + 9, y + 9, c);
                g.fill(x + 6, y + 2, x + 7, y + 4, c);
                g.fill(x + 9, y + 2, x + 10, y + 4, c);
                g.fill(x + 6, y + 12, x + 7, y + 14, c);
                g.fill(x + 9, y + 12, x + 10, y + 14, c);
                g.fill(x + 2, y + 6, x + 4, y + 7, c);
                g.fill(x + 2, y + 9, x + 4, y + 10, c);
                g.fill(x + 12, y + 6, x + 14, y + 7, c);
                g.fill(x + 12, y + 9, x + 14, y + 10, c);
            }
        }
    }

    // Interaction

    @Override
    public boolean keyPressed(final int key, final int scan, final int mods) {
        // The request popup is modal: ESC closes it, Enter submits, typing goes to the quantity field,
        // and every other key is swallowed so the inventory key ('E') never closes the GUI mid-edit.
        if (popupEntry != null) {
            if (key == 256) {
                closeRequest();
                return true;
            }
            if (key == 257 || key == 335) {
                // Enter submits: a Storage popup withdraws to the inventory; a Network popup requests.
                if (popupFromStorage) {
                    sendStorageAction(false);
                } else {
                    sendRequest();
                }
                return true;
            }
            if (qtyBox != null && qtyBox.isFocused()) {
                qtyBox.keyPressed(key, scan, mods);
            }
            return true;
        }
        // While the search field has focus, route typing to it; ESC unfocuses it; never let a letter
        // key fall through and close the GUI.
        if (searchBox != null && searchBox.isFocused()) {
            if (key == 256) {
                searchBox.setFocused(false);
                setFocused(null);
                return true;
            }
            searchBox.keyPressed(key, scan, mods);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean charTyped(final char c, final int mods) {
        if (popupEntry != null && qtyBox != null && qtyBox.isFocused()) {
            return qtyBox.charTyped(c, mods);
        }
        if (searchBox != null && searchBox.isFocused()) {
            return searchBox.charTyped(c, mods);
        }
        return super.charTyped(c, mods);
    }

    @Override
    public boolean mouseClicked(final double mouseX, final double mouseY, final int button) {
        if (popupEntry != null) {
            return handlePopupClick(mouseX, mouseY, button);
        }
        if (popupOp != null) {
            return handleOpPopupClick(mouseX, mouseY, button);
        }
        // Clicking the search field selects it for typing; clicking elsewhere deselects it. Container
        // screens don't reliably route focus to widgets, so do it explicitly.
        if (searchBox != null && searchBox.visible) {
            if (searchBox.isMouseOver(mouseX, mouseY)) {
                setFocused(searchBox);
                searchBox.setFocused(true);
                return searchBox.mouseClicked(mouseX, mouseY, button);
            }
            searchBox.setFocused(false);
        }
        // Deposit: holding a stack and clicking the grid or deposit bar inserts it into the network
        // (Network tab) or the computer's local storage (Storage tab) — left = whole stack, right = one.
        if (isGridTab() && !menu.getCarried().isEmpty()
                && (button == 0 || button == 1)
                && (overDepositBar(mouseX, mouseY) || overNetworkGrid(mouseX, mouseY))) {
            if (menu.activeTab() == ComputerTerminalMenu.TAB_STORAGE) {
                PacketDistributor.sendToServer(new TerminalLocalDepositPayload(menu.monitorPos(), menu.hostPos(),
                        button == 1 ? TerminalLocalDepositPayload.CURSOR_ONE : TerminalLocalDepositPayload.CURSOR));
            } else {
                PacketDistributor.sendToServer(new TerminalInsertPayload(menu.monitorPos(), menu.hostPos(),
                        button == 1 ? TerminalInsertPayload.CURSOR_ONE : TerminalInsertPayload.CURSOR));
            }
            return true;
        }
        // Storage tab: clicking an item with an empty cursor opens the actions popup, where the player
        // sets a quantity and sends it to their inventory or up into the network.
        if (menu.activeTab() == ComputerTerminalMenu.TAB_STORAGE && menu.getCarried().isEmpty()
                && button == 0) {
            final NetworkItemEntry e = networkItemAt((int) mouseX, (int) mouseY);
            if (e != null) {
                openStorageRequest(e);
                return true;
            }
        }
        if (button == 0) {
            for (int i = 0; i < tabCount(); i++) {
                final int tx = leftPos + RAIL_X;
                final int ty = topPos + TAB_Y0 + i * TAB_H;
                if (mouseX >= tx && mouseX < tx + RAIL_W && mouseY >= ty && mouseY < ty + TAB_H) {
                    if (i != menu.activeTab()) {
                        menu.setActiveTab(i);
                        if (minecraft != null && minecraft.gameMode != null) {
                            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, i);
                        }
                        syncSearchBoxVisibility();
                    }
                    return true;
                }
            }
            if (isGridTab()) {
                if (inRect(mouseX, mouseY, leftPos + SORT_X, topPos + TOOLBAR_Y, SORT_W, TOOLBAR_H)) {
                    sortByQuantity = !sortByQuantity;
                    netScrollRow = 0;
                    return true;
                }
                // The Network tab opens a request popup; the Storage tab withdraws directly (above).
                if (menu.activeTab() == ComputerTerminalMenu.TAB_NETWORK) {
                    final NetworkItemEntry e = networkItemAt((int) mouseX, (int) mouseY);
                    if (e != null) {
                        openRequest(e);
                        return true;
                    }
                }
            }
            if (menu.activeTab() == ComputerTerminalMenu.TAB_OPS) {
                final int row = opsRowAt((int) mouseX, (int) mouseY);
                if (row >= 0) {
                    selectedOp = row;
                    openOpPopup(menu.operationsLog().get(row)); // click a logged op -> SubOperations popup
                    return true;
                }
            }
            if (menu.activeTab() == ComputerTerminalMenu.TAB_TASKS) {
                final int sub = taskSubTabAt((int) mouseX, (int) mouseY);
                if (sub >= 0) {
                    taskSubTab = sub;
                    return true;
                }
                final int opRow = taskOpRowAt((int) mouseX, (int) mouseY);
                if (opRow >= 0) {
                    openOpPopup(menu.activeOps().get(opRow)); // click an in-flight op -> SubOperations popup
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected void slotClicked(final Slot slot, final int slotId, final int button, final ClickType type) {
        // On the Network/Storage tabs, shift-clicking an inventory stack deposits it into the network
        // or local storage respectively, instead of a (no-op) quick-move.
        if (isGridTab() && type == ClickType.QUICK_MOVE
                && slot != null && slot.hasItem() && slot.index >= menu.storageSlotCount()) {
            if (menu.activeTab() == ComputerTerminalMenu.TAB_STORAGE) {
                PacketDistributor.sendToServer(new TerminalLocalDepositPayload(
                        menu.monitorPos(), menu.hostPos(), slot.index));
            } else {
                PacketDistributor.sendToServer(new TerminalInsertPayload(
                        menu.monitorPos(), menu.hostPos(), slot.index));
            }
            return;
        }
        super.slotClicked(slot, slotId, button, type);
    }

    private boolean overNetworkGrid(final double mx, final double my) {
        return mx >= leftPos + NET_X && mx < leftPos + NET_X + NET_COLS * 18
                && my >= topPos + NET_Y && my < topPos + NET_Y + NET_ROWS * 18;
    }

    private boolean isGridTab() {
        return menu.activeTab() == ComputerTerminalMenu.TAB_NETWORK
                || menu.activeTab() == ComputerTerminalMenu.TAB_STORAGE;
    }

    private boolean overDepositBar(final double mx, final double my) {
        final int dx = leftPos + NET_X;
        final int dy = topPos + DEPOSIT_Y;
        return mx >= dx && mx < dx + DEPOSIT_W && my >= dy && my < dy + DEPOSIT_H;
    }

    // Request popup (Network SELECT)

    private int popupX() {
        return leftPos + (imageWidth - POPUP_W) / 2;
    }

    private int popupY() {
        return topPos + (imageHeight - POPUP_H) / 2;
    }

    private static final int POPUP_SERVER_ROWS = 4;

    private void openRequest(final NetworkItemEntry e) {
        popupEntry = e;
        popupFromStorage = false;
        deselectedServers.clear();
        advancedMode = false;
        destServerIndex = 0;
        menu.setServerBreakdown(List.of());  // clear stale rows; the reply repopulates
        menu.setNetworkServers(List.of());   // ditto the destination picker's computer list
        syncSearchBoxVisibility(); // hide the search field, reveal the quantity field
        setPopupQty((int) Math.min(64L, Math.max(1L, e.total())));
        setFocused(qtyBox);
        if (qtyBox != null) {
            qtyBox.setFocused(true);
        }
        // The reply carries BOTH the per-server breakdown (advanced sources) and the full computer list
        // (the advanced destination picker).
        PacketDistributor.sendToServer(new RequestServerBreakdownPayload(
                menu.monitorPos(), menu.hostPos(), e.key()));
    }

    private void openStorageRequest(final NetworkItemEntry e) {
        popupEntry = e;
        popupFromStorage = true;
        advancedMode = false;
        syncSearchBoxVisibility();
        setPopupQty((int) Math.min(64L, Math.max(1L, e.total())));
        setFocused(qtyBox);
        if (qtyBox != null) {
            qtyBox.setFocused(true);
        }
    }

    private void closeRequest() {
        popupEntry = null;
        popupFromStorage = false;
        deselectedServers.clear();
        advancedMode = false;
        syncSearchBoxVisibility();
    }

    private int reqPopupH() {
        return !popupFromStorage && advancedMode ? REQ_H_ADVANCED : REQ_H_SIMPLE;
    }

    private void toggleAdvanced() {
        advancedMode = !advancedMode;
    }

    // Operation-detail popup (SubOperations of a clicked Operation)

    private void openOpPopup(final OperationRecord op) {
        popupOp = op;
        opPopupScroll = 0;
        syncSearchBoxVisibility();
    }

    private void closeOpPopup() {
        popupOp = null;
        syncSearchBoxVisibility();
    }

    private boolean handleOpPopupClick(final double mouseX, final double mouseY, final int button) {
        if (button == 1) {
            closeOpPopup();
            return true;
        }
        if (button == 0) {
            final int px = popupX();
            final int py = popupY();
            if (mouseX < px || mouseX >= px + POPUP_W || mouseY < py || mouseY >= py + POPUP_H) {
                closeOpPopup();
            }
        }
        return true; // swallow clicks while the popup is up
    }

    private int clampOpPopupScroll(final int size) {
        final int max = Math.max(0, size - OP_POPUP_ROWS);
        opPopupScroll = Math.max(0, Math.min(max, opPopupScroll));
        return opPopupScroll;
    }

    private void renderOpPopup(final GuiGraphics g) {
        if (popupOp == null) {
            return;
        }
        // Push above the item grid and dim the screen, so the tab rail and header behind never show
        // through the panel (flat fills draw below items otherwise).
        g.pose().pushPose();
        g.pose().translate(0, 0, 350);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0070A0F);
        final int px = popupX();
        final int py = popupY();
        g.fill(px - 2, py - 2, px + POPUP_W + 2, py + POPUP_H + 2, 0xFF0A1A1F);
        g.fill(px, py, px + POPUP_W, py + POPUP_H, PANEL);
        g.fill(px, py, px + POPUP_W, py + 1, ACCENT);
        drawDataIcon(g, popupOp.key(), -1L, px + 6, py + 5);
        final String label = opTypeLabel(popupOp.type());
        g.drawString(font, label + "  " + popupOp.name().getString(), px + 28, py + 6, TEXT, false);
        g.drawString(font, fmt(popupOp.moved()) + " of " + fmt(popupOp.requested()) + "  "
                + statusLabel(popupOp.status()), px + 28, py + 17, statusColor(popupOp.status()), false);
        final double f = popupOp.requested() <= 0
                ? (popupOp.status() == OperationRecord.STATUS_PROCESSING ? 0 : 1)
                : Math.min(1.0, (double) popupOp.moved() / popupOp.requested());
        track(g, px + 6, py + 30, POPUP_W - 12, f,
                popupOp.status() == OperationRecord.STATUS_PROCESSING ? ACCENT2 : statusColor(popupOp.status()));
        g.drawString(font, "SUBOPERATIONS", px + 6, py + 42, DIM, false);
        final List<OperationRecord.MoveRow> moves = popupOp.moves();
        if (moves.isEmpty()) {
            g.drawString(font, "No movement yet.", px + 6, py + 56, DIM, false);
        } else {
            final int start = clampOpPopupScroll(moves.size());
            for (int i = 0; i < OP_POPUP_ROWS && start + i < moves.size(); i++) {
                moveRow(g, px, py + 56 + i * 12, moves.get(start + i));
            }
        }
        final String hint = "right-click to close";
        g.drawString(font, hint, px + POPUP_W - font.width(hint) - 6, py + POPUP_H - 10, DIM, false);
        g.pose().popPose();
    }

    private int taskOpRowAt(final int mx, final int my) {
        if (menu.activeTab() != ComputerTerminalMenu.TAB_TASKS || taskSubTab != 0) {
            return -1;
        }
        if (mx < leftPos + CONTENT_X || mx >= leftPos + imageWidth - 6) {
            return -1;
        }
        final int rel = my - (topPos + 6 + 90);
        if (rel < 0) {
            return -1;
        }
        final int row = rel / 14;
        return row >= 0 && row < Math.min(TASK_OP_ROWS, menu.activeOps().size()) ? row : -1;
    }

    private boolean handlePopupClick(final double mouseX, final double mouseY, final int button) {
        if (button == 1) {
            closeRequest(); // right-click closes
            return true;
        }
        if (button != 0) {
            return true;
        }
        final int px = popupX();
        final int py = popupY();
        final int ph = reqPopupH();
        // Outside the box closes.
        if (mouseX < px || mouseX >= px + POPUP_W || mouseY < py || mouseY >= py + ph) {
            closeRequest();
            return true;
        }
        // Advanced toggle (Network tab only; never on the Storage popup).
        if (!popupFromStorage && inRect(mouseX, mouseY, px + POPUP_W - 44, py + 5, 36, 12)) {
            toggleAdvanced();
            return true;
        }
        // Quantity field: focus it so the player can type, and let the EditBox place its cursor.
        if (inRect(mouseX, mouseY, px + 7, py + 29, 120, 16)) {
            setFocused(qtyBox);
            qtyBox.setFocused(true);
            qtyBox.mouseClicked(mouseX, mouseY, button);
            return true;
        }
        // Max: request everything available.
        if (inRect(mouseX, mouseY, px + POPUP_W - 58, py + 30, 50, 14)) {
            setPopupQty((int) Math.min(Integer.MAX_VALUE, popupEntry.total()));
            return true;
        }
        // Steppers: -1000/-100/-10/-1 then +1/+10/+100/+1000.
        for (int i = 0; i < STEP_AMOUNTS.length; i++) {
            if (inRect(mouseX, mouseY, px + 8 + i * 23, py + 48, 22, 14)) {
                setPopupQty(popupQty + STEP_AMOUNTS[i]);
                return true;
            }
        }
        // Storage popup: two action buttons (to inventory / to network); no sources or destination.
        if (popupFromStorage) {
            final int by = py + ph - 22;
            if (inRect(mouseX, mouseY, px + 8, by, 90, 16)) {
                sendStorageAction(false);
            } else if (inRect(mouseX, mouseY, px + 104, by, 90, 16)) {
                sendStorageAction(true);
            }
            return true;
        }
        // Advanced mode: source-server checkboxes (PULL FROM) and the destination computer cycle.
        if (advancedMode) {
            final List<ServerBreakdownPayload.ServerHolding> servers = menu.serverBreakdown();
            for (int i = 0; i < Math.min(POPUP_SERVER_ROWS, servers.size()); i++) {
                if (inRect(mouseX, mouseY, px + 8, py + 78 + i * 12, POPUP_W - 16, 11)) {
                    final String key = servers.get(i).key();
                    if (!deselectedServers.remove(key)) {
                        deselectedServers.add(key);
                    }
                    return true;
                }
            }
            final int count = menu.networkServers().size();
            if (count > 0) {
                if (inRect(mouseX, mouseY, px + 8, py + 152, 14, 14)) {
                    destServerIndex = Math.floorMod(destServerIndex - 1, count);
                    return true;
                }
                if (inRect(mouseX, mouseY, px + POPUP_W - 22, py + 152, 14, 14)) {
                    destServerIndex = Math.floorMod(destServerIndex + 1, count);
                    return true;
                }
            }
        }
        // Action button (REQUEST in simple mode, SEND to a computer in advanced mode).
        if (inRect(mouseX, mouseY, px + 8, py + ph - 22, POPUP_W - 16, 16)) {
            sendRequest();
            return true;
        }
        return true; // clicks inside the box are consumed
    }

    private void sendRequest() {
        if (popupEntry == null || popupQty <= 0) {
            return;
        }
        final List<String> keys = new ArrayList<>();
        boolean anyDeselected = false;
        int kind = TerminalSelectPayload.DEST_AUTO;
        String destServer = "";
        // Sources and destination are advanced-only; a simple request pulls from everywhere to the
        // auto destination (local storage, else inventory).
        if (advancedMode) {
            for (final ServerBreakdownPayload.ServerHolding s : menu.serverBreakdown()) {
                if (deselectedServers.contains(s.key())) {
                    anyDeselected = true;
                } else {
                    keys.add(s.key());
                }
            }
            if (anyDeselected && keys.isEmpty() && !menu.serverBreakdown().isEmpty()) {
                closeRequest(); // every source unchecked — nothing to pull from
                return;
            }
            final List<NetworkServersPayload.ServerEntry> comp = menu.networkServers();
            if (comp.isEmpty()) {
                return; // no destination computer yet — keep the popup open
            }
            kind = TerminalSelectPayload.DEST_SERVER;
            destServer = comp.get(Math.floorMod(destServerIndex, comp.size())).key();
        }
        PacketDistributor.sendToServer(new TerminalSelectPayload(
                menu.monitorPos(), menu.hostPos(), popupEntry.key(), popupQty,
                anyDeselected ? keys : List.of(), kind, destServer));
        closeRequest();
    }

    private void sendStorageAction(final boolean toNetwork) {
        if (popupEntry == null || popupQty <= 0) {
            return;
        }
        if (toNetwork) {
            PacketDistributor.sendToServer(new TerminalLocalUploadPayload(
                    menu.monitorPos(), menu.hostPos(), popupEntry.key(), popupQty));
        } else {
            PacketDistributor.sendToServer(new TerminalLocalWithdrawPayload(
                    menu.monitorPos(), menu.hostPos(), popupEntry.key(), popupQty));
        }
        closeRequest();
    }

    private static boolean inRect(final double mx, final double my, final int x, final int y,
                                  final int w, final int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void renderPopup(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        if (popupEntry == null) {
            return;
        }
        // Items (the grid + inventory) render at a higher z than flat fills, so the popup must sit
        // above them or they show through. Push the whole popup — and the quantity field — forward.
        g.pose().pushPose();
        g.pose().translate(0, 0, 350);
        g.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0070A0F);
        final int px = popupX();
        final int py = popupY();
        final int ph = reqPopupH();
        g.fill(px - 1, py - 1, px + POPUP_W + 1, py + ph + 1, ACCENT);
        g.fill(px, py, px + POPUP_W, py + ph, 0xFF0F151C);

        // Header: icon + name + available, plus the advanced-mode toggle (Network tab only).
        drawDataIcon(g, popupEntry.key(), -1L, px + 8, py + 5);
        g.drawString(font, font.plainSubstrByWidth(popupEntry.name().getString(), POPUP_W - 78),
                px + 28, py + 6, TEXT, false);
        g.drawString(font, fmt(popupEntry.total()) + (popupEntry.isFluid() ? " mB" : "")
                        + (popupFromStorage ? " in local" : " available"),
                px + 28, py + 17, DIM, false);
        if (!popupFromStorage) {
            final boolean advHover = inRect(mouseX, mouseY, px + POPUP_W - 44, py + 5, 36, 12);
            g.fill(px + POPUP_W - 44, py + 5, px + POPUP_W - 8, py + 17,
                    advancedMode ? ACCENT : (advHover ? 0xFF24323C : 0xFF1A222B));
            g.drawCenteredString(font, "ADV", px + POPUP_W - 26, py + 7, advancedMode ? 0xFF0F151C : DIM);
        }

        // Quantity: editable field (re-rendered here so it sits ON the popup) + a Max button.
        g.fill(px + 7, py + 29, px + 127, py + 45, TRACK);
        qtyBox.render(g, mouseX, mouseY, partialTick);
        final boolean maxHover = inRect(mouseX, mouseY, px + POPUP_W - 58, py + 30, 50, 14);
        g.fill(px + POPUP_W - 58, py + 30, px + POPUP_W - 8, py + 44, maxHover ? 0xFF2BB3A4 : 0xFF1F9488);
        g.drawCenteredString(font, "MAX", px + POPUP_W - 33, py + 33, 0xFFFFFFFF);

        // Stepper row: ---- --- -- - (1000/100/10/1 down) then + ++ +++ ++++ (up).
        for (int i = 0; i < STEP_LABELS.length; i++) {
            final int bx = px + 8 + i * 23;
            final boolean hover = inRect(mouseX, mouseY, bx, py + 48, 22, 14);
            g.fill(bx, py + 48, bx + 22, py + 62, hover ? 0xFF24323C : 0xFF1A222B);
            g.drawCenteredString(font, STEP_LABELS[i], bx + 11, py + 51, STEP_AMOUNTS[i] > 0 ? ACCENT : AMBER);
        }

        if (popupFromStorage) {
            renderStorageActions(g, mouseX, mouseY, px, py, ph);
        } else if (advancedMode) {
            renderAdvanced(g, mouseX, mouseY, px, py, ph);
        } else {
            renderSimple(g, mouseX, mouseY, px, py, ph);
        }
        g.pose().popPose();
    }

    private void renderSimple(final GuiGraphics g, final int mouseX, final int mouseY,
                              final int px, final int py, final int ph) {
        final boolean hasStorage = menu.usableStorageSlots() > 0;
        g.drawString(font, hasStorage ? "Lands in this computer's storage" : "Needs internal storage (no disk)",
                px + 8, py + 70, hasStorage ? DIM : AMBER, false);
        actionButton(g, mouseX, mouseY, px + 8, py + ph - 22, POPUP_W - 16, "REQUEST " + fmt(popupQty));
    }

    private void renderStorageActions(final GuiGraphics g, final int mouseX, final int mouseY,
                                      final int px, final int py, final int ph) {
        g.drawString(font, "Send local items to:", px + 8, py + 70, DIM, false);
        final int by = py + ph - 22;
        actionButton(g, mouseX, mouseY, px + 8, by, 90, "TO INVENTORY");
        actionButton(g, mouseX, mouseY, px + 104, by, 90, "TO NETWORK");
    }

    private void renderAdvanced(final GuiGraphics g, final int mouseX, final int mouseY,
                                final int px, final int py, final int ph) {
        g.drawString(font, "PULL FROM", px + 8, py + 66, DIM, false);
        final List<ServerBreakdownPayload.ServerHolding> servers = menu.serverBreakdown();
        if (servers.isEmpty()) {
            g.drawString(font, "all servers", px + POPUP_W - 8 - font.width("all servers"), py + 66, DIM, false);
        }
        for (int i = 0; i < Math.min(POPUP_SERVER_ROWS, servers.size()); i++) {
            final ServerBreakdownPayload.ServerHolding s = servers.get(i);
            final int ry = py + 78 + i * 12;
            final boolean on = !deselectedServers.contains(s.key());
            g.fill(px + 8, ry, px + 18, ry + 10, on ? ACCENT : 0xFF2A3340);
            g.fill(px + 9, ry + 1, px + 17, ry + 9, on ? ACCENT : 0xFF11161D);
            g.drawString(font, font.plainSubstrByWidth(s.label(), 120), px + 22, ry + 1, on ? TEXT : DIM, false);
            final String c = fmt(s.count());
            g.drawString(font, c, px + POPUP_W - 8 - font.width(c), ry + 1, DIM, false);
        }
        if (servers.size() > POPUP_SERVER_ROWS) {
            g.drawString(font, "+" + (servers.size() - POPUP_SERVER_ROWS) + " more (included)",
                    px + 22, py + 78 + POPUP_SERVER_ROWS * 12, DIM, false);
        }

        // SEND TO: cycle through every computer that can receive items.
        g.drawString(font, "SEND TO", px + 8, py + 140, DIM, false);
        final List<NetworkServersPayload.ServerEntry> comp = menu.networkServers();
        if (comp.isEmpty()) {
            g.drawString(font, "No computers available", px + 8, py + 154, AMBER, false);
        } else {
            final NetworkServersPayload.ServerEntry target = comp.get(Math.floorMod(destServerIndex, comp.size()));
            final boolean lh = inRect(mouseX, mouseY, px + 8, py + 152, 14, 14);
            final boolean rh = inRect(mouseX, mouseY, px + POPUP_W - 22, py + 152, 14, 14);
            g.fill(px + 8, py + 152, px + 22, py + 166, lh ? 0xFF24323C : 0xFF1A222B);
            g.drawCenteredString(font, "<", px + 15, py + 155, ACCENT);
            g.fill(px + POPUP_W - 22, py + 152, px + POPUP_W - 8, py + 166, rh ? 0xFF24323C : 0xFF1A222B);
            g.drawCenteredString(font, ">", px + POPUP_W - 15, py + 155, ACCENT);
            final String text = font.plainSubstrByWidth(
                    target.name() + "  (" + fmt(target.free()) + " free)", POPUP_W - 52);
            g.drawString(font, text, px + 26, py + 155, TEXT, false);
        }
        actionButton(g, mouseX, mouseY, px + 8, py + ph - 22, POPUP_W - 16, "SEND " + fmt(popupQty));
    }

    private void actionButton(final GuiGraphics g, final int mouseX, final int mouseY,
                              final int x, final int y, final int w, final String label) {
        final boolean hover = inRect(mouseX, mouseY, x, y, w, 16);
        g.fill(x, y, x + w, y + 16, hover ? 0xFF2BB3A4 : 0xFF1F9488);
        g.drawCenteredString(font, label, x + w / 2, y + 4, 0xFFFFFFFF);
    }

    private void drawDataIcon(final GuiGraphics g, final StorageKey key, final long count,
                              final int x, final int y) {
        if (key.isFluid()) {
            FluidSprite.draw(g, key.fluidPrototype(), x, y);
            if (count >= 0L) {
                final String c = fmt(count);
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.drawString(font, c, x + 17 - font.width(c), y + 9, 0xFFFFFFFF, true);
                g.pose().popPose();
            }
        } else {
            g.renderItem(key.stack(1), x, y);
            if (count >= 0L) {
                g.renderItemDecorations(font, key.stack(1), x, y, fmt(count));
            }
        }
    }

    private static String fmt(final long n) {
        if (n < 10_000) {
            return String.format("%,d", n);
        }
        if (n < 1_000_000) {
            return String.format("%.1fk", n / 1_000.0);
        }
        return String.format("%.1fM", n / 1_000_000.0);
    }

    @Override
    public boolean mouseScrolled(final double mx, final double my, final double dx, final double dy) {
        if (popupOp != null && dy != 0) {
            opPopupScroll = Math.max(0, opPopupScroll - (int) Math.signum(dy));
            return true;
        }
        if (isGridTab() && dy != 0) {
            final int rows = (visibleItems().size() + NET_COLS - 1) / NET_COLS;
            final int max = Math.max(0, rows - NET_ROWS);
            netScrollRow = Math.max(0, Math.min(max, netScrollRow - (int) Math.signum(dy)));
            return true;
        }
        if (menu.activeTab() == ComputerTerminalMenu.TAB_OPS && dy != 0) {
            final int max = Math.max(0, menu.operationsLog().size() - OPS_ROWS);
            opScroll = Math.max(0, Math.min(max, opScroll - (int) Math.signum(dy)));
            return true;
        }
        return super.mouseScrolled(mx, my, dx, dy);
    }

    @Override
    public void render(final GuiGraphics g, final int mouseX, final int mouseY, final float partialTick) {
        syncSearchBoxVisibility();
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (popupEntry != null) {
            renderPopup(g, mouseX, mouseY, partialTick);
        } else if (popupOp != null) {
            renderOpPopup(g);
        } else if (isGridTab()) {
            final boolean local = menu.activeTab() == ComputerTerminalMenu.TAB_STORAGE;
            final String where = local ? "in storage" : "on the network";
            renderNetworkHover(g, mouseX, mouseY);
            if (overDepositBar(mouseX, mouseY)) {
                g.renderComponentTooltip(font, List.of(
                        Component.literal(local ? "Deposit into local storage" : "Deposit into the network"),
                        Component.literal("Click: deposit held stack").withStyle(ChatFormatting.GRAY),
                        Component.literal("Right-click: deposit one").withStyle(ChatFormatting.GRAY),
                        Component.literal("Shift-click an inventory item").withStyle(ChatFormatting.GRAY)),
                        mouseX, mouseY);
                return;
            }
            final NetworkItemEntry e = networkItemAt(mouseX, mouseY);
            if (e != null) {
                final List<Component> lines = new ArrayList<>();
                lines.add(e.name());
                lines.add(Component.literal(String.format("%,d", e.total()) + " " + where)
                        .withStyle(ChatFormatting.GRAY));
                if (local) {
                    lines.add(Component.literal("Click: take a stack").withStyle(ChatFormatting.DARK_GRAY));
                    lines.add(Component.literal("Shift-click: take all  -  Right-click: take one")
                            .withStyle(ChatFormatting.DARK_GRAY));
                }
                g.renderComponentTooltip(font, lines, mouseX, mouseY);
            }
        }
    }

    private void renderNetworkHover(final GuiGraphics g, final int mx, final int my) {
        final int gx = leftPos + NET_X;
        final int gy = topPos + NET_Y;
        final int relX = mx - gx;
        final int relY = my - gy;
        if (relX < 0 || relY < 0 || relX % 18 > 15 || relY % 18 > 15) {
            return;
        }
        final int col = relX / 18;
        final int row = relY / 18;
        if (col >= NET_COLS || row >= NET_ROWS) {
            return;
        }
        final int sx = gx + col * 18;
        final int sy = gy + row * 18;
        // Items render above flat fills, so push the highlight forward to sit over them.
        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        g.fill(sx, sy, sx + 16, sy + 16, 0x80FFFFFF);
        g.pose().popPose();
    }
}
