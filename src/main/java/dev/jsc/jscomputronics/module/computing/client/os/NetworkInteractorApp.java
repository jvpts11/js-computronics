/*
 * SPDX-License-Identifier: LGPL-3.0-only
 *
 * Copyright (C) 2026 jvpts11
 *
 * This file is part of J's Computronics.
 */
package dev.jsc.jscomputronics.module.computing.client.os;

import dev.jsc.jscomputronics.module.computing.gui.layout.NetworkInteractorLayout;
import dev.jsc.jscomputronics.module.computing.operation.payload.CraftCatalogPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.DesktopShellOutputPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.DesktopShellRunPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.NetworkInteractorPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.NetworkItemEntry;
import dev.jsc.jscomputronics.module.computing.operation.payload.NiDepositPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.NiGridClickPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.NiSelectPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestNetworkInteractorPayload;
import dev.jsc.jscomputronics.module.computing.operation.payload.RequestNiServersPayload;
import dev.jsc.jscomputronics.module.computing.program.cli.CliStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Network Interactor desktop window: the graphical face of the data network for a Frames computer,
 * with the same capabilities as the MC-NET terminal — extract from the network into local storage,
 * withdraw local storage into the inventory, deposit/insert from the player's hotbar, and request
 * crafts — addressing the host by position. The player's full inventory is shown in the window as real
 * container slots inside a fixed, framed band pinned just above the footer: the desktop menu owns the 36
 * slots and the desktop screen positions them over that band, so the vanilla container drives the cursor,
 * drag, and shift-click. This app paints the inventory frame and slot backgrounds; the screen renders the
 * items and cursor on top. The item grid above the band scrolls its items when there are more than fit. An
 * embedded console still runs ad-hoc operations through the same path as the Shell.
 */
public final class NetworkInteractorApp implements DesktopApp {

    // Labels kept short so all five tabs fit the strip; "Local"/"Network" abbreviate the longer mock names.
    private static final String[] TABS = {"Status", "Local", "Network", "Crafting", "Operations"};
    private static final int TAB_STATUS = 0;
    private static final int TAB_LOCAL = 1;
    private static final int TAB_NETWORK = 2;
    private static final int TAB_CRAFTING = 3;
    private static final int TAB_OPS = 4;

    // Layout constants and zone math live in the pure NetworkInteractorLayout, shared with the desktop
    // screen so the drawn cells, the real container slots, and the hit-tests all agree at every size.
    private static final int TAB_H = NetworkInteractorLayout.TAB_H;
    private static final int SEARCH_H = NetworkInteractorLayout.SEARCH_H;
    private static final int CELL = NetworkInteractorLayout.CELL;
    private static final int INV_COLS = NetworkInteractorLayout.INV_COLS;
    private static final int INV_ROWS = NetworkInteractorLayout.INV_ROWS;
    private static final int INV_PAD = NetworkInteractorLayout.INV_PAD;

    // The skin of the OS this program runs on, handed in each frame by the window manager. Every colour below
    // is re-derived from it in applySkin, so the whole program follows the installed OS (Frames 95/XP/11).
    private OsSkin skin = OsSkin.fallback();
    private int PANEL = 0xFFFFFFFF;
    private int FIELD = 0xFFF1F4F9;
    private int EDGE = 0xFFB7C0CE;
    private int TEXT = 0xFF1A2230;
    private int DIM = 0xFF6A7280;
    // The framed inventory band: a slightly raised panel with a bevel, so it reads as a distinct surface.
    private int BAND_FILL = 0xFFE7ECF3;
    private int BAND_HI = 0xFFFFFFFF;
    private int BAND_LO = 0xFF9AA4B4;

    // The last tab the player viewed, kept across reopens (reopening the computer or the monitor) so the NI
    // comes back to where they left it instead of snapping to Network every time.
    private static int lastTab = TAB_NETWORK;

    private final BlockPos host;
    private final BlockPos monitorPos;
    private int tab = lastTab;

    private final List<NetworkItemEntry> networkItems = new ArrayList<>();
    private final List<NetworkItemEntry> localItems = new ArrayList<>();
    private final List<CraftCatalogPayload.Entry> crafts = new ArrayList<>();
    private boolean online;
    private long usedItems;
    private int serverCount;

    private final StringBuilder search = new StringBuilder();
    /** How the grid is ordered: 0 by name, 1 most stored first, 2 least stored first. */
    private int sortMode;
    private static final int SORT_MODES = 3;
    private static final String[] SORT_LABELS = {"A-Z", "MOST", "LEAST"};
    private boolean searchFocus;
    /** Top visible item row of the grid; the grid scrolls its items (not its pixels) when more rows exist. */
    private int gridScroll;

    private final Deque<Line> console = new ArrayDeque<>();
    private final StringBuilder input = new StringBuilder();
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;
    private int contentW = 280;
    private int contentH = 188;

    // Request/storage popup (MC-NET style): clicking an item with an empty cursor opens a quantity dialog
    // instead of extracting a fixed amount. popupEntry == null means no popup is open.
    private NetworkItemEntry popupEntry;
    private long popupQty;
    private boolean popupStorage; // true: Storage-tab popup (TO INVENTORY / TO NETWORK); false: Network REQUEST
    private static final int[] POPUP_STEPS = {-1000, -100, -10, -1, 1, 10, 100, 1000};
    private static final int POPUP_W = 188;
    private static final int POPUP_H = 86;
    private static final int POPUP_H_ADV = 158;
    private static final int ADV_ROWS = 4; // visible PULL-FROM rows before scrolling

    // Advanced request: choose which Servers to pull from and where to send the result.
    private boolean popupAdvanced;
    private final List<dev.jsc.jscomputronics.module.computing.operation.payload.NetworkServersPayload.ServerEntry>
            servers = new ArrayList<>();
    private final Set<String> popupDeselected = new java.util.HashSet<>(); // source server keys turned OFF
    private int popupDestIndex; // 0 = this computer; 1.. = servers.get(i-1)
    private int popupSrcScroll;

    // Craft popup (MC-NET style): clicking a craft opens a quantity dialog + a live plan (need/have) before
    // crafting, instead of crafting a fixed amount immediately. craftPopup == null means no craft popup is open.
    private dev.jsc.jscomputronics.module.computing.operation.payload.CraftCatalogPayload.Entry craftPopup;
    private long craftQty = 1;
    // When the item in the craft popup can be made BOTH by a multi-stage pipeline and by composing its flat
    // patterns, this toggle chooses: true runs the pipeline, false lets the recursive planner build the tree.
    // Only shown (and only meaningful) when the popup's entry has a multi-stage recipe.
    private boolean craftMulti = true;
    // Frames since the last live refresh: the Network Interactor re-asks the server for the storage grid and,
    // on the Operations tab, the live operations a few times a second, so stock and craft progress move on their
    // own instead of only when a command is run.
    private int refreshFrames;
    private dev.jsc.jscomputronics.module.computing.operation.payload.CraftPlanPayload craftPlan;
    private static final int CRAFT_W = 196;
    private static final int CRAFT_H = 150;
    private static final int[] CRAFT_STEPS = {-64, -1, 1, 64};

    /** Delivers a craft plan (need/have rows) to the open NI's craft popup. */
    public static void acceptCraftPlan(
            final dev.jsc.jscomputronics.module.computing.operation.payload.CraftPlanPayload plan) {
        if (active != null && active.craftPopup != null
                && ItemStack.isSameItemSameComponents(active.craftPopup.result(), plan.result())) {
            active.craftPlan = plan;
        }
    }

    /** Delivers the network's computer list (for the advanced popup) to the open NI. */
    public static void acceptServers(final List<dev.jsc.jscomputronics.module.computing.operation
            .payload.NetworkServersPayload.ServerEntry> list) {
        if (active != null) {
            active.servers.clear();
            active.servers.addAll(list);
        }
    }

    // Operations tab (network task manager): the network's recent log and live in-flight Operations.
    private final List<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord>
            recentOps = new ArrayList<>();
    private final List<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord>
            activeOps = new ArrayList<>();
    private int opScroll;
    private int opSelected = -1;
    // Parallel craft-slot capacity from the network's online supercomputers (used / total), shown in this tab.
    private int scSlotsUsed;
    private int scSlotsTotal;

    /** Delivers the network's recent Operations log to the open NI's Operations tab. */
    public static void acceptOps(final List<dev.jsc.jscomputronics.module.computing.operation
            .payload.OperationRecord> ops) {
        if (active != null) {
            active.recentOps.clear();
            active.recentOps.addAll(ops);
        }
    }

    /** Delivers the network's live (in-flight) Operations and supercomputer slot capacity to the NI's Operations tab. */
    public static void acceptActiveOps(final List<dev.jsc.jscomputronics.module.computing.operation
            .payload.OperationRecord> ops, final int scSlotsUsed, final int scSlotsTotal) {
        if (active != null) {
            active.activeOps.clear();
            active.activeOps.addAll(ops);
            active.scSlotsUsed = scSlotsUsed;
            active.scSlotsTotal = scSlotsTotal;
        }
    }

    private static NetworkInteractorApp active;

    private record Line(String text, int color) {
    }

    public NetworkInteractorApp(final BlockPos host, final BlockPos monitorPos) {
        this.host = host;
        this.monitorPos = monitorPos;
        active = this;
        request();
    }

    /**
     * Marks this window as the active Network Interactor — the one that receives network snapshots and
     * console output. The desktop screen calls this whenever this window becomes the focused one, so the
     * static routing follows focus instead of pointing at the most recently constructed instance.
     */
    public void markActive() {
        active = this;
    }

    @Override
    public void applySkin(final OsSkin osSkin) {
        this.skin = osSkin;
        // Re-derive the whole palette from the installed OS so the program follows its skin (95/XP/11).
        this.PANEL = osSkin.windowBg();
        this.FIELD = osSkin.fieldBg();
        this.EDGE = osSkin.edge();
        this.TEXT = osSkin.text();
        this.DIM = osSkin.dim();
        this.BAND_FILL = osSkin.panelBg();
        this.BAND_HI = 0xFFFFFFFF;
        this.BAND_LO = osSkin.edge();
    }

    private void request() {
        PacketDistributor.sendToServer(new RequestNetworkInteractorPayload(host, monitorPos));
        if (tab == TAB_OPS) {
            requestOps();
        }
    }

    /** Asks the server for the network's recent + active Operations (for the Operations tab). */
    private void requestOps() {
        PacketDistributor.sendToServer(
                new dev.jsc.jscomputronics.module.computing.operation.payload
                        .RequestNiOperationsPayload(host, monitorPos));
    }

    /** Routes a storage snapshot to the open Network Interactor window. */
    public static void accept(final NetworkInteractorPayload payload) {
        if (active == null) {
            return;
        }
        active.networkItems.clear();
        active.networkItems.addAll(payload.networkItems());
        active.localItems.clear();
        active.localItems.addAll(payload.localItems());
        active.crafts.clear();
        active.crafts.addAll(payload.crafts());
        active.online = payload.mainframeOnline();
        active.usedItems = payload.usedItems();
        active.serverCount = payload.serverCount();
    }

    /** Routes an embedded-console output reply to the open Network Interactor window. */
    public static void acceptConsole(final DesktopShellOutputPayload payload) {
        if (active == null) {
            return;
        }
        if (payload.clear()) {
            active.console.clear();
        }
        for (final DesktopShellOutputPayload.WireLine line : payload.lines()) {
            active.pushConsole(line.text(), colorOf(line.style()));
        }
        active.request(); // an operation may have changed the network; refresh the grid
    }

    private void pushConsole(final String text, final int color) {
        console.addLast(new Line(text, color));
        while (console.size() > 64) {
            console.removeFirst();
        }
    }

    @Override
    public String title() {
        return "Network Interactor";
    }

    @Override
    public int defaultWidth() {
        // Left column (grid + framed inventory) + details panel + gaps — compact, just above minWidth() so the
        // window opens tidy and never below its own minimum (which squashes the content and clips the panel).
        return 330;
    }

    @Override
    public int defaultHeight() {
        return 226;
    }

    @Override
    public int minWidth() {
        return NetworkInteractorLayout.minContentWidth() + 8;
    }

    @Override
    public int minHeight() {
        return NetworkInteractorLayout.minContentHeight() + DesktopWindow.TITLE_H + 8;
    }

    // Layout — every zone comes from the pure NetworkInteractorLayout, derived from the LIVE content size, so
    // the drawn cells, the real container slots, and the hit-tests agree at any size. The inventory band is a
    // fixed-height panel pinned above the footer; only the grid scrolls (its items, not its pixels).
    private NetworkInteractorLayout.Zones zones() {
        return NetworkInteractorLayout.resolve(contentW, contentH);
    }

    private int bodyTopLocal() {
        return TAB_H + 2;
    }

    // --- Inventory zone, in content-local coordinates (relative to the app content's top-left). The desktop
    // screen reads these to place the menu's 36 inventory slots over this window each frame. The band frame
    // padding and 18px cell pitch match the backgrounds renderInventory() paints; the slots never scroll. ---

    /** The content-local x of a slot cell's top-left, where the vanilla item is drawn. */
    public int invCellContentX(final int col) {
        return zones().invX() + col * CELL;
    }

    /**
     * The content-local y of a slot cell's top-left for the given content height. The inventory band is a
     * fixed-height panel pinned just above the footer, so the row position is derived from the live height,
     * never from a cached field — the item lines up with its slot background from the very first frame.
     */
    public int invCellContentY(final int row, final int contentHeight) {
        return NetworkInteractorLayout.resolve(contentW, contentHeight).invY()
                + NetworkInteractorLayout.rowYOffset(row);
    }

    /**
     * The content-local y just past the bottom row of inventory slots, for the given content height — the
     * desktop screen uses it as the band's lower visibility bound so all 36 slots are always counted visible.
     */
    public int invBandBottom(final int contentHeight) {
        return NetworkInteractorLayout.resolve(contentW, contentHeight).invY()
                + NetworkInteractorLayout.rowYOffset(INV_ROWS - 1) + CELL;
    }

    /** Whether a content-local point falls within an inventory slot cell (the framed, always-visible band). */
    private boolean inInventoryZone(final double lx, final double ly) {
        return NetworkInteractorLayout.inventorySlotAt((int) lx, (int) ly, zones()) >= 0;
    }

    @Override
    public void renderContent(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY,
                              final float partialTick) {
        this.contentW = width;
        this.contentH = height;
        // Keep the view live: a few times a second, re-ask for the storage grid (and the live operations on the
        // Operations tab) so stock counts and craft progress update on their own, without a manual refresh.
        if (++refreshFrames >= 20) {
            refreshFrames = 0;
            request();
        }
        final NetworkInteractorLayout.Zones z = NetworkInteractorLayout.resolve(width, height);
        clampGridScroll(z);
        g.fill(x, y, x + width, y + height, PANEL);

        // Tab strip (fixed header), drawn in the OS skin's tab shape (95 bevel / XP Luna / 11 underline).
        int tx = x;
        for (int i = 0; i < TABS.length; i++) {
            final int tw = font.width(TABS[i]) + 12;
            skin.tab(g, font, tx, y, tw, TAB_H, TABS[i], i == tab);
            tx += tw;
        }
        g.fill(x, y + TAB_H, x + width, y + TAB_H + 1, EDGE);

        // Search + sort (fixed header, grid tabs only).
        if (tab == TAB_NETWORK || tab == TAB_LOCAL || tab == TAB_CRAFTING) {
            renderSearchSort(g, font, x, y, z);
        }

        // Grid zone: the tab body, clipped to the band between the fixed header and the inventory band. Only
        // this region scrolls, and it scrolls its ITEMS (gridScroll), not its pixels — the rows shown change.
        final int gridTop = y + z.gridY();
        final int gridBottom = y + z.gridY() + z.gridH();
        if (z.gridH() > 0) {
            pushScissor(g, x, gridTop, x + width, gridBottom);
            switch (tab) {
                case TAB_STATUS -> renderStatus(g, font, x + 4, gridTop + 2, width - 8, z.gridH());
                case TAB_LOCAL -> renderGridCells(g, font, x, gridTop, width, z, localItems, mouseX, mouseY);
                case TAB_NETWORK -> renderGridCells(g, font, x, gridTop, width, z, networkItems, mouseX, mouseY);
                case TAB_CRAFTING -> renderCrafting(g, font, x, gridTop, width, z, mouseX, mouseY);
                case TAB_OPS -> renderOps(g, font, x, gridTop, width, z, mouseX, mouseY);
                default -> { }
            }
            g.disableScissor();
        }

        // The framed inventory band — a pinned panel with the 36 slot backgrounds; the desktop screen draws
        // the real container items and the cursor over it. Always fully visible, never clipped.
        renderInventoryBand(g, font, x, y, z);

        // Item details panel (right column): the hovered grid item, or the selected Operation on the Ops tab.
        if (tab == TAB_OPS) {
            renderOpDetails(g, font, x, y, z);
        } else {
            renderDetails(g, font, x, y, z, mouseX, mouseY);
        }

        // Status line (fixed footer) — usage right-aligned, the left label trimmed so it never overruns.
        final int statusY = y + z.statusY();
        final String usage = dataLabel(usedItems) + " stored";
        final int usageX = x + width - font.width(usage) - 3;
        g.drawString(font, usage, usageX, statusY + 1, DIM, false);
        final String left = (online ? "● Mainframe online" : "○ Mainframe offline")
                + "  ·  " + networkItems.size() + " types  ·  " + serverCount + " servers";
        g.drawString(font, trimTo(font, left, usageX - (x + 3) - 4), x + 3, statusY + 1,
                online ? 0xFF2E8B45 : 0xFF9A4A4A, false);

        // Embedded console (fixed footer).
        final int cy = y + z.consoleY();
        g.fill(x, cy - 1, x + width, y + height, 0xFF101820);
        final String last = console.isEmpty() ? "" : console.peekLast().text();
        if (!last.isEmpty() && input.isEmpty() && !searchFocus) {
            g.drawString(font, trimTo(font, last, width - 6), x + 3, cy + 1, console.peekLast().color(), false);
        } else {
            g.drawString(font, "> " + input + (searchFocus ? "" : "_"), x + 3, cy + 1, 0xFF40C060, false);
        }

        // Thin scrollbar down the right of the grid zone when the items overflow the rows that fit.
        final int maxScroll = maxGridScroll(z);
        if (maxScroll > 0 && z.gridH() > 0) {
            renderGridScrollbar(g, x + width - 3, gridTop, z.gridH(), maxScroll);
        }

    }

    @Override
    public void renderModal(final GuiGraphics g, final Font font, final int x, final int y,
                            final int width, final int height, final int mouseX, final int mouseY) {
        // The desktop draws this in a late pass above every item icon, so the dialog's own dim covers and
        // darkens the grid/craft/inventory icons instead of them piercing through at their blit depth.
        if (popupEntry != null) {
            renderPopup(g, font, x, y, width, height, mouseX, mouseY);
        }
        if (craftPopup != null) {
            renderCraftPopup(g, font, x, y, width, height, mouseX, mouseY);
        }
    }

    private int popupOx;
    private int popupOy;

    /** Whether the advanced sections are shown (only the Network request popup has them). */
    private boolean advancedShown() {
        return popupAdvanced && !popupStorage;
    }

    private int popupHeight() {
        return advancedShown() ? POPUP_H_ADV : POPUP_H;
    }

    private void renderPopup(final GuiGraphics g, final Font font, final int x, final int y,
                             final int width, final int height, final int mouseX, final int mouseY) {
        g.fill(x, y, x + width, y + height, 0x99000000); // dim the window behind the dialog
        final int ph = popupHeight();
        final int px = x + (width - POPUP_W) / 2;
        final int py = y + (height - ph) / 2;
        popupOx = px;
        popupOy = py;
        g.fill(px, py, px + POPUP_W, py + ph, PANEL);
        outline(g, px, py, POPUP_W, ph);
        g.fill(px, py, px + POPUP_W, py + 1, BAND_HI);

        // Header: icon + name + availability, with an ADV toggle (Network popup only).
        DesktopItems.data(g, font, popupEntry.key(), px + 4, py + 4, null);
        final int nameW = popupStorage ? POPUP_W - 30 : POPUP_W - 64;
        g.drawString(font, trimTo(font, popupEntry.name().getString(), nameW),
                px + 24, py + 5, TEXT, false);
        g.drawString(font, amount(popupEntry.key(), popupEntry.total()) + " available", px + 24, py + 15, DIM, false);
        if (!popupStorage) {
            final int advX = px + POPUP_W - 34;
            drawButton(g, font, advX, py + 4, 30, 11, "Adv",
                    popupAdvanced || hovered(mouseX, mouseY, advX, py + 4, 30, 11));
        }

        // Quantity readout + Max.
        g.fill(px + 4, py + 27, px + POPUP_W - 40, py + 39, FIELD);
        outline(g, px + 4, py + 27, POPUP_W - 44, 12);
        g.drawString(font, "x" + popupQty, px + 8, py + 29, TEXT, false);
        final int maxX = px + POPUP_W - 36;
        drawButton(g, font, maxX, py + 27, 32, 12, "Max", hovered(mouseX, mouseY, maxX, py + 27, 32, 12));

        // Stepper row: -1k -100 -10 -1 +1 +10 +100 +1k.
        for (int i = 0; i < POPUP_STEPS.length; i++) {
            final int sx = px + 4 + i * 23;
            drawButton(g, font, sx, py + 44, 22, 12, stepLabel(POPUP_STEPS[i]),
                    hovered(mouseX, mouseY, sx, py + 44, 22, 12));
        }

        if (advancedShown()) {
            renderAdvanced(g, font, px, py, mouseX, mouseY);
            final int by = py + ph - 20;
            drawButton(g, font, px + 4, by, POPUP_W - 8, 16,
                    popupDestIndex == 0 ? "Request" : "Send",
                    hovered(mouseX, mouseY, px + 4, by, POPUP_W - 8, 16));
        } else if (popupStorage) {
            final int bw = (POPUP_W - 12) / 2;
            final int b1 = px + 4;
            final int b2 = b1 + bw + 4;
            drawButton(g, font, b1, py + 62, bw, 18, "To Inventory", hovered(mouseX, mouseY, b1, py + 62, bw, 18));
            drawButton(g, font, b2, py + 62, bw, 18, "To Network", hovered(mouseX, mouseY, b2, py + 62, bw, 18));
        } else {
            drawButton(g, font, px + 4, py + 62, POPUP_W - 8, 18, "Request",
                    hovered(mouseX, mouseY, px + 4, py + 62, POPUP_W - 8, 18));
        }
    }

    /** Renders the PULL FROM checkbox list and the SEND TO destination cycle of the advanced popup. */
    private void renderAdvanced(final GuiGraphics g, final Font font, final int px, final int py,
                                final int mouseX, final int mouseY) {
        smallText(g, font, "PULL FROM (servers)", px + 4, py + 60, DIM);
        final int listY = py + 69;
        for (int row = 0; row < ADV_ROWS; row++) {
            final int i = popupSrcScroll + row;
            if (i >= servers.size()) {
                break;
            }
            final var srv = servers.get(i);
            final int ry = listY + row * 10;
            final boolean on = !popupDeselected.contains(srv.key());
            g.fill(px + 6, ry, px + 13, ry + 7, on ? 0xFF2F6AC6 : FIELD);
            outline(g, px + 6, ry, 7, 7);
            if (on) {
                g.drawString(font, "x", px + 7, ry - 1, 0xFFFFFFFF, false);
            }
            smallText(g, font, trimTo(font, srv.name(), scaledWidth(POPUP_W - 24)), px + 17, ry, TEXT);
        }
        if (servers.isEmpty()) {
            smallText(g, font, "all sources", px + 17, listY, DIM);
        }

        // SEND TO destination cycle: index 0 = this computer, then each server/computer.
        final int destY = listY + ADV_ROWS * 10 + 2;
        smallText(g, font, "SEND TO", px + 4, destY, DIM);
        final String destLabel = popupDestIndex == 0 ? "This computer"
                : (popupDestIndex - 1 < servers.size() ? servers.get(popupDestIndex - 1).name() : "This computer");
        drawButton(g, font, px + 4, destY + 9, 12, 12, "<", hovered(mouseX, mouseY, px + 4, destY + 9, 12, 12));
        g.fill(px + 18, destY + 9, px + POPUP_W - 18, destY + 21, FIELD);
        outline(g, px + 18, destY + 9, POPUP_W - 36, 12);
        smallText(g, font, trimTo(font, destLabel, scaledWidth(POPUP_W - 44)), px + 21, destY + 11, TEXT);
        drawButton(g, font, px + POPUP_W - 16, destY + 9, 12, 12, ">",
                hovered(mouseX, mouseY, px + POPUP_W - 16, destY + 9, 12, 12));
    }

    private static String stepLabel(final int step) {
        final String sign = step > 0 ? "+" : "-";
        final int mag = Math.abs(step);
        return sign + (mag >= 1000 ? mag / 1000 + "k" : Integer.toString(mag));
    }

    private void drawButton(final GuiGraphics g, final Font font, final int bx, final int by,
                            final int bw, final int bh, final String label, final boolean hover) {
        // The button face is drawn in the OS skin's shape; the label stays at the dense small scale so it fits.
        skin.button(g, font, bx, by, bw, bh, "", hover, false, false);
        smallText(g, font, label, bx + (bw - scaledWidth(fontWidth(label))) / 2, by + (bh - 6) / 2,
                hover && skin.form() == OsSkin.Form.FLAT ? 0xFFFFFFFF : TEXT);
    }

    private static boolean hovered(final int mx, final int my, final int bx, final int by,
                                   final int bw, final int bh) {
        return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
    }

    private void renderSearchSort(final GuiGraphics g, final Font font, final int x, final int y,
                                  final NetworkInteractorLayout.Zones z) {
        final int sx = x + z.searchX();
        final int sy = y + z.searchY();
        skin.field(g, sx, sy, z.searchW(), SEARCH_H, searchFocus);
        final String shown = search.length() == 0 && !searchFocus ? "Search" : search.toString();
        g.drawString(font, shown + (searchFocus ? "_" : ""), sx + 3, sy + 3,
                search.length() == 0 && !searchFocus ? DIM : TEXT, false);
        final int qx = x + z.sortX();
        skin.field(g, qx, sy, z.sortW(), SEARCH_H, false);
        // The button always names the order it is in, so the player can see the mode without clicking it.
        final String label = SORT_LABELS[sortMode];
        g.drawString(font, label, qx + (z.sortW() - font.width(label)) / 2, sy + 3, TEXT, false);
    }

    private void renderGridScrollbar(final GuiGraphics g, final int sbX, final int sbY, final int sbH,
                                     final int maxScroll) {
        g.fill(sbX, sbY, sbX + 3, sbY + sbH, EDGE);
        final int totalSteps = maxScroll + 1;
        final int thumbH = Math.max(8, sbH / totalSteps);
        final int thumbY = sbY + (sbH - thumbH) * gridScroll / maxScroll;
        skin.scrollThumb(g, sbX, thumbY, 3, thumbH);
    }

    private void renderInventoryBand(final GuiGraphics g, final Font font, final int x, final int y,
                                     final NetworkInteractorLayout.Zones z) {
        // The band panel: a raised surface with a bevel, so the inventory reads as a distinct, bounded area.
        final int bx = x + z.invBandX();
        final int by = y + z.invBandY();
        final int bw = z.invBandW();
        final int bh = z.invBandH();
        g.fill(bx, by, bx + bw, by + bh, BAND_FILL);
        g.fill(bx, by, bx + bw, by + 1, BAND_HI);
        g.fill(bx, by, bx + 1, by + bh, BAND_HI);
        g.fill(bx, by + bh - 1, bx + bw, by + bh, BAND_LO);
        g.fill(bx + bw - 1, by, bx + bw, by + bh, BAND_LO);

        // "Inventory" label tucked into the band's top frame, so the panel is clearly the player inventory.
        g.drawString(font, "Inventory", bx + 3, by - 9, DIM, false);

        // Slot backgrounds inside the frame (the desktop screen draws the real items and cursor over these),
        // using the shared rowYOffset so the 3-rows + gap + hotbar lines up exactly with the real slots.
        for (int r = 0; r < INV_ROWS; r++) {
            for (int c = 0; c < INV_COLS; c++) {
                final int cx = x + z.invX() + c * CELL;
                final int cy = y + z.invY() + NetworkInteractorLayout.rowYOffset(r);
                g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, FIELD);
                outline(g, cx, cy, CELL - 2, CELL - 2);
            }
        }
    }

    /**
     * Enables a scissor given in window-local coordinates, compensating for the desktop's pose translation
     * (enableScissor ignores the pose in 1.21.1), so the clip lines up with the drawn content instead of being
     * offset by the desktop origin — the bug that made the details text and the grid cells clip in the wrong
     * place and the panel borders not meet up.
     */
    private static void pushScissor(final GuiGraphics g, final int x1, final int y1, final int x2, final int y2) {
        final org.joml.Matrix4f m = g.pose().last().pose();
        final dev.jsc.jscomputronics.common.gui.layout.WindowGeometry.Rect r =
                dev.jsc.jscomputronics.common.gui.layout.WindowGeometry.scissor(
                        (int) m.m30(), (int) m.m31(), x1, y1, x2, y2);
        g.enableScissor(r.x(), r.y(), r.x() + r.w(), r.y() + r.h());
    }

    /**
     * The right-hand item details panel: the hovered grid item's icon, name, mod, id, weight, durability and
     * network total, filling what used to be empty space. Per-server breakdown and tags/components are layered
     * in later; this draws everything resolvable client-side from the item itself.
     */
    private void renderDetails(final GuiGraphics g, final Font font, final int x, final int y,
                               final NetworkInteractorLayout.Zones z, final int mouseX, final int mouseY) {
        final int dx = x + z.detailsX();
        final int dy = y + z.detailsY();
        final int dw = z.detailsW();
        final int dh = z.detailsH();
        if (dw <= 6 || dh <= 6) {
            return;
        }
        g.fill(dx, dy, dx + dw, dy + dh, BAND_FILL);
        g.fill(dx, dy, dx + dw, dy + 1, BAND_HI);
        g.fill(dx, dy, dx + 1, dy + dh, BAND_HI);
        g.fill(dx, dy + dh - 1, dx + dw, dy + dh, BAND_LO);
        g.fill(dx + dw - 1, dy, dx + dw, dy + dh, BAND_LO);

        pushScissor(g, dx + 1, dy + 1, dx + dw - 1, dy + dh - 1);
        final NetworkItemEntry e = hoveredGridEntry(mouseX - x, mouseY - y);
        final int px = dx + 5;
        int py = dy + 5;
        if (e == null) {
            smallText(g, font, "Hover an item to", px, py, DIM);
            smallText(g, font, "see its details.", px, py + 9, DIM);
            g.disableScissor();
            return;
        }
        final dev.jsc.jscomputronics.module.computing.storage.StorageKey key = e.key();
        final net.minecraft.world.item.ItemStack stack = e.icon(); // empty for a fluid or a chemical
        final net.minecraft.resources.ResourceLocation id = dataId(key);
        DesktopItems.data(g, font, key, px, py, null);
        smallText(g, font, trimTo(font, e.name().getString(), scaledWidth(dw - 28)), px + 20, py, TEXT);
        smallText(g, font, trimTo(font, modName(id.getNamespace()), scaledWidth(dw - 28)), px + 20, py + 9, 0xFF2F6AC6);
        py += 22;
        py = detail(g, font, px, py, dw, "ID", id.toString());
        py = detail(g, font, px, py, dw, "KIND", key.isItem() ? "Item" : key.isFluid() ? "Fluid" : "Chemical");
        py = detail(g, font, px, py, dw, "WEIGHT", dataLabel(key.weight(e.total())));
        if (key.isItem() && stack.isDamageableItem()) {
            py = detail(g, font, px, py, dw, "DURABILITY",
                    (stack.getMaxDamage() - stack.getDamageValue()) + " / " + stack.getMaxDamage());
        }
        py = detailList(g, font, px, py, dw, "STORED", storedLines(e));
        if (key.isItem()) {
            py = detailList(g, font, px, py, dw, "TAGS", itemTags(stack));
            detailList(g, font, px, py, dw, "COMPONENTS", componentNames(stack));
        }
        g.disableScissor();
    }

    /** The STORED section: one line per server/storage that holds the type (label + amount), or a single
     *  network-total line when no per-server breakdown was sent (e.g. the Local Storage tab). */
    private java.util.List<String> storedLines(final NetworkItemEntry e) {
        if (e.shares().isEmpty()) {
            return java.util.List.of(amount(e.key(), e.total()) + " on the network");
        }
        final java.util.List<String> out = new java.util.ArrayList<>();
        for (final NetworkItemEntry.StorageShare s : e.shares()) {
            out.add(s.label() + ": " + amount(e.key(), s.qty()));
        }
        return out;
    }

    /** Draws a labelled list section (a dim caption, then each entry on its own line; "(none)" if empty). */
    private int detailList(final GuiGraphics g, final Font font, final int px, final int py, final int dw,
                           final String label, final java.util.List<String> values) {
        smallText(g, font, label, px, py, DIM);
        int vy = py + 8;
        if (values.isEmpty()) {
            smallText(g, font, "(none)", px + 2, vy, DIM);
            return vy + 10;
        }
        for (final String v : values) {
            smallText(g, font, trimTo(font, v, scaledWidth(dw - 8)), px + 2, vy, TEXT);
            vy += 8;
        }
        return vy + 2;
    }

    /** The item's tags as namespaced ids (sorted), for the details panel TAGS section. */
    private static java.util.List<String> itemTags(final net.minecraft.world.item.ItemStack stack) {
        return stack.getItemHolder().tags()
                .map(t -> t.location().toString())
                .sorted()
                .toList();
    }

    /** The data components present on the stack as namespaced ids (sorted), for the COMPONENTS section. */
    private static java.util.List<String> componentNames(final net.minecraft.world.item.ItemStack stack) {
        final java.util.List<String> out = new java.util.ArrayList<>();
        for (final net.minecraft.core.component.TypedDataComponent<?> c : stack.getComponents()) {
            final net.minecraft.resources.ResourceLocation key =
                    net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(c.type());
            if (key != null) {
                out.add(key.toString());
            }
        }
        java.util.Collections.sort(out);
        return out;
    }

    /** Draws a labelled detail line (a dim caption, then the value wrapped to the panel width). Returns new y. */
    private int detail(final GuiGraphics g, final Font font, final int px, final int py, final int dw,
                       final String label, final String value) {
        smallText(g, font, label, px, py, DIM);
        int vy = py + 8;
        for (final String line : wrap(font, value, scaledWidth(dw - 12))) {
            smallText(g, font, line, px + 2, vy, TEXT);
            vy += 8;
        }
        return vy + 2;
    }

    /** Greedy width-based wrap, for ids/values too long for the narrow details panel. */
    private static java.util.List<String> wrap(final Font font, final String s, final int maxW) {
        final java.util.List<String> out = new java.util.ArrayList<>();
        String rest = s;
        while (!rest.isEmpty() && out.size() < 6) {
            int n = rest.length();
            while (n > 1 && font.width(rest.substring(0, n)) > maxW) {
                n--;
            }
            out.add(rest.substring(0, n));
            rest = rest.substring(n);
        }
        return out;
    }

    /** A readable mod name for a namespace (Minecraft for vanilla, otherwise the title-cased namespace). */
    private static String modName(final String ns) {
        if (ns.equals("minecraft")) {
            return "Minecraft";
        }
        return Character.toUpperCase(ns.charAt(0)) + ns.substring(1).replace('_', ' ');
    }

    /** The grid entry under a content-local point, or null when the cursor isn't over a grid item. */
    @org.jetbrains.annotations.Nullable
    private NetworkItemEntry hoveredGridEntry(final double lx, final double ly) {
        if (tab != TAB_NETWORK && tab != TAB_LOCAL) {
            return null;
        }
        final int idx = gridIndexAt(lx, ly);
        if (idx < 0) {
            return null;
        }
        final java.util.List<NetworkItemEntry> items = filtered(tab == TAB_NETWORK ? networkItems : localItems);
        return idx < items.size() ? items.get(idx) : null;
    }

    private void renderStatus(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height) {
        int ry = y + 2;
        smallText(g, font, online ? "Mainframe: online" : "Mainframe: offline",
                x, ry, online ? 0xFF2E8B45 : 0xFF9A4A4A);
        ry += 10;
        smallText(g, font, "Item types on network: " + networkItems.size(), x, ry, TEXT);
        ry += 10;
        smallText(g, font, "Local item types: " + localItems.size(), x, ry, TEXT);
        ry += 10;
        smallText(g, font, "Network stored: " + dataLabel(usedItems), x, ry, TEXT);
        ry += 10;
        smallText(g, font, "Servers: " + serverCount, x, ry, TEXT);
        ry += 10;
        smallText(g, font, "Craftable: " + crafts.size(), x, ry, TEXT);
    }

    /** Draws compact (0.85x) text anchored at the top-left of (x, y); used to pack the dense status and
     *  details panels so their text reads smaller and fits without colliding. */
    private static void smallText(final GuiGraphics g, final Font font, final String text,
                                  final int x, final int y, final int color) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(0.85f, 0.85f, 1f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    /** The font-unit width that fits in {@code px} real pixels when drawn through {@link #smallText} (0.85x). */
    private static int scaledWidth(final int px) {
        return (int) (px / 0.85f);
    }

    /** The crafts shown after the search filter (by result name); the full list when the search is empty. */
    private java.util.List<CraftCatalogPayload.Entry> filteredCrafts() {
        if (search.length() == 0) {
            return crafts;
        }
        final String q = search.toString().toLowerCase(java.util.Locale.ROOT);
        final java.util.List<CraftCatalogPayload.Entry> out = new ArrayList<>();
        for (final CraftCatalogPayload.Entry e : crafts) {
            if (e.result().getHoverName().getString().toLowerCase(java.util.Locale.ROOT).contains(q)) {
                out.add(e);
            }
        }
        return out;
    }

    private void renderCrafting(final GuiGraphics g, final Font font, final int x, final int gridTop,
                                final int width, final NetworkInteractorLayout.Zones z,
                                final int mouseX, final int mouseY) {
        final java.util.List<CraftCatalogPayload.Entry> list = filteredCrafts();
        final int cols = Math.max(1, (width - 8) / CELL);
        final int rows = z.gridRows();
        int idx = gridScroll * cols;
        for (int r = 0; r < rows && idx < list.size(); r++) {
            for (int c = 0; c < cols && idx < list.size(); c++) {
                final CraftCatalogPayload.Entry e = list.get(idx);
                final int cx = x + 4 + c * CELL;
                final int cy = gridTop + r * CELL;
                g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, FIELD);
                outline(g, cx, cy, CELL - 2, CELL - 2);
                DesktopItems.item(g, e.result(), cx + 1, cy + 1);
                // Availability dot (green/amber/red), top-right.
                final int dot = switch (e.availability()) {
                    case CraftCatalogPayload.DOT_GREEN -> 0xFF3CC75A;
                    case CraftCatalogPayload.DOT_AMBER -> 0xFFE6A93A;
                    default -> 0xFFD05050;
                };
                g.fill(cx + CELL - 6, cy + 1, cx + CELL - 2, cy + 5, dot);
                if (mouseX >= cx && mouseX < cx + CELL - 2 && mouseY >= cy && mouseY < cy + CELL - 2) {
                    g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, 0x80FFFFFF);
                }
                idx++;
            }
        }
        if (list.isEmpty() && rows > 0) {
            // Two lines, each wrapped to the grid width so the hint never truncates mid-word.
            final String msg = search.length() > 0
                    ? "No crafts match \"" + search + "\"."
                    : "No patterns on the network. Load .craft files on a Crafting Computer.";
            drawWrapped(g, font, msg, x + 6, gridTop + 2, width - 12, DIM);
        }
    }

    private void renderGridCells(final GuiGraphics g, final Font font, final int x, final int gridTop,
                                 final int width, final NetworkInteractorLayout.Zones z,
                                 final List<NetworkItemEntry> source, final int mouseX, final int mouseY) {
        // The search/sort row is drawn separately in the fixed header; here we paint just the item cells, which
        // the grid-zone scissor clips. The grid scrolls its items (gridScroll) within the rows that fit.
        final List<NetworkItemEntry> items = filtered(source);
        final int cols = z.gridCols();
        final int rows = z.gridRows();
        int idx = gridScroll * cols;
        for (int r = 0; r < rows && idx < items.size(); r++) {
            for (int c = 0; c < cols && idx < items.size(); c++) {
                final NetworkItemEntry e = items.get(idx);
                final int cx = x + z.gridX() + c * CELL;
                final int cy = gridTop + r * CELL;
                g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, FIELD);
                outline(g, cx, cy, CELL - 2, CELL - 2);
                // The count rides just in front of the model, both inside this window's depth band.
                DesktopItems.data(g, font, e.key(), cx + 1, cy + 1, formatCount(e.total()));
                // Same hover highlight as a real inventory slot — every item cell gets it.
                if (mouseX >= cx && mouseX < cx + CELL - 2 && mouseY >= cy && mouseY < cy + CELL - 2) {
                    g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, 0x80FFFFFF);
                }
                idx++;
            }
        }
        if (items.isEmpty() && rows > 0) {
            g.drawString(font, "(empty)", x + 6, gridTop + 2, DIM, false);
        }
    }

    // ---- Operations tab (network task manager) ----

    private List<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord> allOps() {
        final List<dev.jsc.jscomputronics.module.computing.operation.payload.OperationRecord> all =
                new ArrayList<>(activeOps.size() + recentOps.size());
        all.addAll(activeOps); // live ops first, then the recent log
        all.addAll(recentOps);
        return all;
    }

    private void renderOps(final GuiGraphics g, final Font font, final int x, final int gridTop,
                           final int width, final NetworkInteractorLayout.Zones z,
                           final int mouseX, final int mouseY) {
        final var all = allOps();
        final int listLeft = x + z.gridX();
        final int listRight = x + z.detailsX() - 2;
        // Supercomputer parallel craft-slot capacity, in the free strip above the list (amber when saturated).
        if (scSlotsTotal > 0) {
            smallText(g, font, "Supercomputer: " + scSlotsUsed + " / " + scSlotsTotal + " parallel crafts",
                    listLeft + 2, gridTop - 9, scSlotsUsed >= scSlotsTotal ? 0xFFE6A93A : DIM);
        }
        if (all.isEmpty()) {
            smallText(g, font, "No operations on the network.", listLeft + 2, gridTop + 4, DIM);
            return;
        }
        final int rowH = 12;
        final int rows = Math.max(1, z.gridH() / rowH);
        opScroll = Math.max(0, Math.min(opScroll, Math.max(0, all.size() - rows)));
        for (int r = 0; r < rows; r++) {
            final int i = opScroll + r;
            if (i >= all.size()) {
                break;
            }
            final var op = all.get(i);
            final int ry = gridTop + r * rowH;
            final boolean live = i < activeOps.size();
            if (i == opSelected) {
                g.fill(listLeft, ry, listRight, ry + rowH, 0x552F6AC6);
            } else if (mouseX >= listLeft && mouseX < listRight && mouseY >= ry && mouseY < ry + rowH) {
                g.fill(listLeft, ry, listRight, ry + rowH, 0x22000000);
            }
            g.fill(listLeft + 1, ry + 4, listLeft + 4, ry + 7, live ? 0xFF49E07A : 0xFF8A93A4);
            final String type = opTypeLabel(op.type());
            g.drawString(font, type, listLeft + 7, ry + 2, opTypeColor(op.type()), false);
            final int nameX = listLeft + 8 + font.width(type) + 3;
            final String st = opStatusShort(op.status());
            final int stW = font.width(st);
            g.drawString(font, trimTo(font, op.name().getString(), listRight - nameX - stW - 6),
                    nameX, ry + 2, TEXT, false);
            g.drawString(font, st, listRight - stW - 2, ry + 2, opStatusColor(op.status()), false);
        }
    }

    /** The selected Operation's detail in the right panel: amounts, status, and per-source/sub rows. */
    private void renderOpDetails(final GuiGraphics g, final Font font, final int x, final int y,
                                 final NetworkInteractorLayout.Zones z) {
        final int dx = x + z.detailsX();
        final int dy = y + z.detailsY();
        final int dw = z.detailsW();
        final int dh = z.detailsH();
        if (dw <= 6 || dh <= 6) {
            return;
        }
        g.fill(dx, dy, dx + dw, dy + dh, BAND_FILL);
        g.fill(dx, dy, dx + dw, dy + 1, BAND_HI);
        g.fill(dx, dy, dx + 1, dy + dh, BAND_HI);
        g.fill(dx, dy + dh - 1, dx + dw, dy + dh, BAND_LO);
        g.fill(dx + dw - 1, dy, dx + dw, dy + dh, BAND_LO);

        pushScissor(g, dx + 1, dy + 1, dx + dw - 1, dy + dh - 1);
        final var all = allOps();
        final int px = dx + 5;
        int py = dy + 5;
        if (opSelected < 0 || opSelected >= all.size()) {
            smallText(g, font, "Select an operation", px, py, DIM);
            smallText(g, font, "to see its details.", px, py + 9, DIM);
            g.disableScissor();
            return;
        }
        final var op = all.get(opSelected);
        DesktopItems.data(g, font, op.key(), px, py, null);
        smallText(g, font, trimTo(font, op.name().getString(), scaledWidth(dw - 28)), px + 20, py + 1, TEXT);
        smallText(g, font, opTypeLabel(op.type()), px + 20, py + 10, opTypeColor(op.type()));
        py += 22;
        smallText(g, font, "moved " + formatCount(op.moved()) + " / " + formatCount(op.requested()),
                px, py, TEXT);
        py += 10;
        smallText(g, font, opStatusLong(op.status()), px, py, opStatusColor(op.status()));
        py += 12;
        if (!op.subs().isEmpty()) {
            smallText(g, font, "SUBOPERATIONS", px, py, DIM);
            py += 10;
            for (final var sub : op.subs()) {
                if (py > dy + dh - 9) {
                    break;
                }
                smallText(g, font, trimTo(font, sub.server() + ": " + sub.moved() + "/" + sub.planned()
                        + " " + subStateLabel(sub.state()), scaledWidth(dw - 10)), px, py, TEXT);
                py += 9;
            }
        } else if (!op.moves().isEmpty()) {
            smallText(g, font, "SOURCES", px, py, DIM);
            py += 10;
            for (final var mv : op.moves()) {
                if (py > dy + dh - 9) {
                    break;
                }
                smallText(g, font, trimTo(font, mv.from() + " " + mv.qty() + " -> " + mv.to(),
                        scaledWidth(dw - 10)), px, py, TEXT);
                py += 9;
            }
        }
        g.disableScissor();
    }

    private void opsBodyClick(final double lx, final double ly) {
        final NetworkInteractorLayout.Zones z = zones();
        final int gridTop = z.gridY();
        final int listLeft = z.gridX();
        final int listRight = z.detailsX() - 2;
        if (lx < listLeft || lx >= listRight || ly < gridTop || ly >= gridTop + z.gridH()) {
            return;
        }
        final int row = (int) ((ly - gridTop) / 12);
        final int i = opScroll + row;
        if (i >= 0 && i < allOps().size()) {
            opSelected = (opSelected == i) ? -1 : i;
        }
    }

    private static String opTypeLabel(final byte type) {
        return dev.jsc.jscomputronics.module.computing.program.OperationPalette.labelFor(type);
    }

    private static int opTypeColor(final byte type) {
        // Shared with the Network Manager and the network terminal, so an Operation type reads the same colour
        // everywhere.
        return dev.jsc.jscomputronics.module.computing.program.OperationPalette.colorFor(type);
    }

    private static String opStatusShort(final byte status) {
        return switch (status) {
            case 0 -> "done";
            case 1 -> "part";
            case 2 -> "fail";
            case 3 -> "run";
            case 4 -> "wait";
            case 5 -> "lock";
            case 6 -> "pend";
            default -> "drop";
        };
    }

    private static String opStatusLong(final byte status) {
        return switch (status) {
            case 0 -> "Completed";
            case 1 -> "Completed (partial)";
            case 2 -> "Failed";
            case 3 -> "Processing";
            case 4 -> "Waiting";
            case 5 -> "Resource locked";
            case 6 -> "Pending";
            default -> "Discarded";
        };
    }

    private static int opStatusColor(final byte status) {
        return switch (status) {
            case 0 -> 0xFF2E8B45;       // completed
            case 1, 3, 4, 6 -> 0xFFB8860B; // partial / processing / waiting / pending — amber
            case 2, 5, 7 -> 0xFFB23A3A; // failed / locked / discarded — red
            default -> 0xFF6A7280;
        };
    }

    private static String subStateLabel(final byte state) {
        return switch (state) {
            case 1 -> "reading";
            case 2 -> "streaming";
            case 3 -> "done";
            default -> "queued";
        };
    }

    /** The number of item columns currently shown in the grid (matches the rendering). */
    private int gridColumns() {
        return INV_COLS;
    }

    /** The number of item rows the visible (and scrollable) source has, for the active grid tab. */
    private int totalItemRows(final NetworkInteractorLayout.Zones z) {
        final int cols = gridColumns();
        final int count = switch (tab) {
            case TAB_NETWORK -> filtered(networkItems).size();
            case TAB_LOCAL -> filtered(localItems).size();
            case TAB_CRAFTING -> filteredCrafts().size();
            default -> 0;
        };
        return (count + cols - 1) / cols;
    }

    /** The largest grid-scroll (top row) that still keeps the last item row inside the visible rows. */
    private int maxGridScroll(final NetworkInteractorLayout.Zones z) {
        if (tab == TAB_STATUS) {
            return 0;
        }
        return Math.max(0, totalItemRows(z) - z.gridRows());
    }

    private void clampGridScroll(final NetworkInteractorLayout.Zones z) {
        final int max = maxGridScroll(z);
        if (gridScroll > max) {
            gridScroll = max;
        }
        if (gridScroll < 0) {
            gridScroll = 0;
        }
    }

    private List<NetworkItemEntry> filtered(final List<NetworkItemEntry> source) {
        final String q = search.toString().trim().toLowerCase(Locale.ROOT);
        final List<NetworkItemEntry> out = new ArrayList<>();
        for (final NetworkItemEntry e : source) {
            if (q.isEmpty() || e.name().getString().toLowerCase(Locale.ROOT).contains(q)) {
                out.add(e);
            }
        }
        switch (sortMode) {
            case 1 -> out.sort((a, b) -> Long.compare(b.total(), a.total()));
            case 2 -> out.sort((a, b) -> Long.compare(a.total(), b.total()));
            default -> out.sort((a, b) -> a.name().getString().compareToIgnoreCase(b.name().getString()));
        }
        return out;
    }

    @Override
    public void mouseClicked(final DesktopWindow window, final double mouseX, final double mouseY,
                             final int button) {
        final int ox = window.x() + 4;
        final int oy = window.y() + 18;
        final double lx = mouseX - ox;
        final double ly = mouseY - oy;

        // A modal dialog, when open, takes the click before anything else.
        if (popupEntry != null) {
            popupClick(lx, ly, button);
            return;
        }
        if (craftPopup != null) {
            craftPopupClick(lx, ly);
            return;
        }

        // Tabs.
        if (ly >= 0 && ly < TAB_H) {
            int tabX = 0;
            for (int i = 0; i < TABS.length; i++) {
                final int tw = fontWidth(TABS[i]) + 12;
                if (lx >= tabX && lx < tabX + tw) {
                    tab = i;
                    lastTab = i; // remember it so the next reopen lands here
                    searchFocus = false;
                    gridScroll = 0;
                    if (i == TAB_OPS) {
                        opSelected = -1;
                        opScroll = 0;
                        requestOps();
                    }
                    return;
                }
                tabX += tw;
            }
        }

        // Inventory band — real container slots handled by the desktop screen (cursor, drag, shift-click);
        // the app simply ignores clicks that land there so it never misreads them as grid/console input.
        if (inInventoryZone(lx, ly)) {
            return;
        }

        // Search field / sort button (grid + crafting tabs). Both hit boxes come from the same zones the
        // header draws them at: measured against the whole content width instead, the sort button sat out
        // over the details panel and clicking the button itself did nothing.
        if ((tab == TAB_NETWORK || tab == TAB_LOCAL || tab == TAB_CRAFTING)
                && ly >= bodyTopLocal() && ly < bodyTopLocal() + SEARCH_H) {
            final NetworkInteractorLayout.Zones z = zones();
            if (lx >= z.sortX() && lx < z.sortX() + z.sortW()) {
                sortMode = (sortMode + 1) % SORT_MODES;
                searchFocus = false;
            } else if (lx >= z.searchX() && lx < z.searchX() + z.searchW()) {
                searchFocus = true;
            }
            return;
        }
        searchFocus = false;

        // Grid / craft / operations body clicks.
        if (tab == TAB_NETWORK || tab == TAB_LOCAL) {
            gridBodyClick(lx, ly, button);
        } else if (tab == TAB_CRAFTING) {
            craftBodyClick(lx, ly, button);
        } else if (tab == TAB_OPS) {
            opsBodyClick(lx, ly);
        }
    }

    /**
     * The deposit target when the player clicks the grid zone of a grid tab while holding a stack, or
     * {@code -1} when the click is not over a depositable grid. The desktop screen (which owns the
     * cursor) calls this to route a cursor deposit to the network or to local storage.
     */
    public int cursorDepositTarget(final double lx, final double ly) {
        if (tab != TAB_NETWORK && tab != TAB_LOCAL) {
            return -1;
        }
        final NetworkInteractorLayout.Zones z = zones();
        if (ly >= z.gridY() && ly < z.gridY() + z.gridH() && lx >= 0 && lx < contentW) {
            return tab == TAB_NETWORK
                    ? NiDepositPayload.TARGET_NETWORK : NiDepositPayload.TARGET_STORAGE;
        }
        return -1;
    }

    /**
     * The grid entry under the cursor on a grid tab — the data a held empty container would fill with on a
     * right-click — or empty when the click is not on an entry.
     */
    public java.util.Optional<dev.jsc.jscomputronics.module.computing.storage.StorageKey> cursorDepositEntry(
            final double lx, final double ly) {
        if (tab != TAB_NETWORK && tab != TAB_LOCAL) {
            return java.util.Optional.empty();
        }
        final int idx = gridIndexAt(lx, ly);
        final List<NetworkItemEntry> items = filtered(tab == TAB_NETWORK ? networkItems : localItems);
        return idx >= 0 && idx < items.size() ? java.util.Optional.of(items.get(idx).key()) : java.util.Optional.empty();
    }

    private void gridBodyClick(final double lx, final double ly, final int button) {
        final int idx = gridIndexAt(lx, ly);
        if (idx < 0) {
            return;
        }
        final List<NetworkItemEntry> items = filtered(tab == TAB_NETWORK ? networkItems : localItems);
        if (idx >= items.size()) {
            return;
        }
        // Open the request/storage quantity dialog (MC-NET style) instead of pulling a fixed amount.
        openPopup(items.get(idx), tab == TAB_LOCAL);
    }

    private void openPopup(final NetworkItemEntry e, final boolean storage) {
        popupEntry = e;
        popupStorage = storage;
        // Default to one stack, clamped to what is available.
        popupQty = Math.max(1L, Math.min(e.total(), e.key().batch()));
        popupAdvanced = false;
        popupDeselected.clear();
        popupDestIndex = 0;
        popupSrcScroll = 0;
        searchFocus = false;
        if (!storage) {
            // Fetch the network's computers so advanced mode can list sources and destinations.
            PacketDistributor.sendToServer(new RequestNiServersPayload(host, monitorPos));
        }
    }

    private void closePopup() {
        popupEntry = null;
        popupQty = 0;
    }

    /** Whether any modal dialog (request/storage or craft) is open — the desktop routes all clicks to the app then. */
    public boolean hasPopup() {
        return popupEntry != null || craftPopup != null;
    }

    @Override
    public boolean modalActive() {
        return hasPopup();
    }

    /**
     * Where a shift-click on an inventory slot inserts: the network on the Network tab, local storage on the
     * Local tab, or -1 (not applicable) on the other tabs. Matches the deposit targets the cursor drop uses.
     */
    public int shiftInsertTarget() {
        if (tab == TAB_NETWORK) {
            return dev.jsc.jscomputronics.module.computing.operation.payload
                    .NiShiftInsertPayload.TARGET_NETWORK;
        }
        if (tab == TAB_LOCAL) {
            return dev.jsc.jscomputronics.module.computing.operation.payload
                    .NiShiftInsertPayload.TARGET_STORAGE;
        }
        return -1;
    }

    /** Routes a click inside the open dialog. Coordinates are window-content-local (same space as the render). */
    private void popupClick(final double lx, final double ly, final int button) {
        final int ph = popupHeight();
        final int px = (contentW - POPUP_W) / 2;
        final int py = (contentH - ph) / 2;
        // A right-click, or a click outside the dialog box, dismisses it.
        if (button == 1 || lx < px || lx >= px + POPUP_W || ly < py || ly >= py + ph) {
            closePopup();
            return;
        }
        // ADV toggle (Network popup only) — grows the dialog to reveal the source/destination controls.
        if (!popupStorage && inRect(lx, ly, px + POPUP_W - 34, py + 4, 30, 11)) {
            popupAdvanced = !popupAdvanced;
            return;
        }
        final long avail = popupEntry.total();
        if (inRect(lx, ly, px + POPUP_W - 36, py + 27, 32, 12)) {
            popupQty = Math.max(1L, avail);
            return;
        }
        for (int i = 0; i < POPUP_STEPS.length; i++) {
            final int sx = px + 4 + i * 23;
            if (inRect(lx, ly, sx, py + 44, 22, 12)) {
                popupQty = Math.max(1L, Math.min(avail, popupQty + POPUP_STEPS[i]));
                return;
            }
        }
        if (advancedShown()) {
            advancedClick(lx, ly, px, py, ph);
        } else if (popupStorage) {
            final int bw = (POPUP_W - 12) / 2;
            final int b1 = px + 4;
            final int b2 = b1 + bw + 4;
            if (inRect(lx, ly, b1, py + 62, bw, 18)) {
                popupAction(NiGridClickPayload.MODE_LOCAL_TO_INV);
            } else if (inRect(lx, ly, b2, py + 62, bw, 18)) {
                popupAction(NiGridClickPayload.MODE_LOCAL_TO_NET);
            }
        } else if (inRect(lx, ly, px + 4, py + 62, POPUP_W - 8, 18)) {
            popupAction(NiGridClickPayload.MODE_NET_TO_LOCAL);
        }
    }

    /** Click handling for the advanced popup's source checkboxes, destination cycle, and action button. */
    private void advancedClick(final double lx, final double ly, final int px, final int py, final int ph) {
        final int listY = py + 69;
        for (int row = 0; row < ADV_ROWS; row++) {
            final int i = popupSrcScroll + row;
            if (i >= servers.size()) {
                break;
            }
            if (inRect(lx, ly, px + 6, listY + row * 10, POPUP_W - 12, 9)) {
                final String key = servers.get(i).key();
                if (!popupDeselected.remove(key)) {
                    popupDeselected.add(key);
                }
                return;
            }
        }
        final int destY = listY + ADV_ROWS * 10 + 2;
        final int destCount = servers.size() + 1; // index 0 = this computer
        if (inRect(lx, ly, px + 4, destY + 9, 12, 12)) {
            popupDestIndex = (popupDestIndex - 1 + destCount) % destCount;
            return;
        }
        if (inRect(lx, ly, px + POPUP_W - 16, destY + 9, 12, 12)) {
            popupDestIndex = (popupDestIndex + 1) % destCount;
            return;
        }
        // Action button.
        if (inRect(lx, ly, px + 4, py + ph - 20, POPUP_W - 8, 16)) {
            sendAdvancedRequest();
        }
    }

    private void popupAction(final int mode) {
        if (popupEntry != null && popupQty > 0) {
            PacketDistributor.sendToServer(
                    new NiGridClickPayload(host, monitorPos, popupEntry.key(), popupQty, mode));
        }
        closePopup();
    }

    /** Sends the advanced request: the selected source Servers and the chosen destination. */
    private void sendAdvancedRequest() {
        if (popupEntry == null || popupQty <= 0) {
            closePopup();
            return;
        }
        // Sources: the servers NOT deselected. An empty list means "all sources" server-side.
        final List<String> sources = new ArrayList<>();
        for (final var srv : servers) {
            if (!popupDeselected.contains(srv.key())) {
                sources.add(srv.key());
            }
        }
        final boolean allSelected = sources.size() == servers.size();
        final String destKey = (popupDestIndex > 0 && popupDestIndex - 1 < servers.size())
                ? servers.get(popupDestIndex - 1).key() : "";
        PacketDistributor.sendToServer(new NiSelectPayload(host, monitorPos, popupEntry.key(), popupQty,
                allSelected ? List.of() : sources, destKey));
        closePopup();
    }

    private static boolean inRect(final double mx, final double my, final int x, final int y,
                                  final int w, final int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private void craftBodyClick(final double lx, final double ly, final int button) {
        final int idx = gridIndexAt(lx, ly);
        final java.util.List<CraftCatalogPayload.Entry> list = filteredCrafts();
        if (idx < 0 || idx >= list.size()) {
            return;
        }
        // Open the MC-NET-style craft popup (quantity + live plan), instead of crafting a fixed amount at once.
        craftPopup = list.get(idx);
        craftQty = 1;
        craftMulti = true; // default to the pipeline when the item has one; the toggle lets the player switch
        craftPlan = null;
        closePopup();
        requestCraftPlan();
    }

    private void closeCraftPopup() {
        craftPopup = null;
        craftPlan = null;
    }

    /** Asks the server to plan the current craft at the current quantity (need vs have rows). */
    private void requestCraftPlan() {
        if (craftPopup != null) {
            PacketDistributor.sendToServer(
                    new dev.jsc.jscomputronics.module.computing.operation.payload.CraftPlanRequestPayload(
                            monitorPos, host, craftPopup.result(), craftQty));
        }
    }

    private void setCraftQty(final long value) {
        craftQty = Math.max(1, Math.min(99_999, value));
        requestCraftPlan();
    }

    /** Submits the craft (full or partial up to what is currently feasible) and closes the popup. */
    private void submitCraft(final boolean partial) {
        if (craftPopup != null) {
            // A multi-stage choice only bites when the entry actually has a pipeline; otherwise it is ignored.
            final boolean multi = !craftPopup.multiStage() || craftMulti;
            PacketDistributor.sendToServer(
                    new dev.jsc.jscomputronics.module.computing.operation.payload.CraftSubmitPayload(
                            monitorPos, host, craftPopup.result(), craftQty, partial, multi));
        }
        closeCraftPopup();
    }

    /** The MC-NET-style craft popup: result + quantity steppers + a live plan (need vs have) + Craft/Partial/Close. */
    private void renderCraftPopup(final GuiGraphics g, final Font font, final int x, final int y,
                                  final int width, final int height, final int mouseX, final int mouseY) {
        g.fill(x, y, x + width, y + height, 0xB0000000); // dim behind the modal
        final int px = x + (width - CRAFT_W) / 2;
        final int py = y + (height - CRAFT_H) / 2;
        g.fill(px - 1, py - 1, px + CRAFT_W + 1, py + CRAFT_H + 1, BAND_LO);
        g.fill(px, py, px + CRAFT_W, py + CRAFT_H, PANEL);
        g.fill(px, py, px + CRAFT_W, py + 1, skin.accent());

        DesktopItems.item(g, craftPopup.result(), px + 4, py + 3);
        g.drawString(font, "Craft " + trimTo(font, craftPopup.result().getHoverName().getString(), CRAFT_W - 40),
                px + 24, py + 6, TEXT, false);

        // Quantity field + steppers (-64 / -1 / +1 / +64).
        g.fill(px + 5, py + 22, px + 66, py + 36, FIELD);
        outline(g, px + 5, py + 22, 61, 14);
        g.drawString(font, Long.toString(craftQty), px + 9, py + 25, TEXT, false);
        for (int i = 0; i < CRAFT_STEPS.length; i++) {
            final int bx = px + 70 + i * 31;
            drawButton(g, font, bx, py + 22, 29, 14, (CRAFT_STEPS[i] > 0 ? "+" : "") + CRAFT_STEPS[i],
                    hovered(mouseX, mouseY, bx, py + 22, 29, 14));
        }

        // Plan rows (need vs have): green when the network has enough, red otherwise.
        smallText(g, font, "PLAN - raw ingredients", px + 5, py + 41, DIM);
        int ry = py + 51;
        if (craftPlan == null) {
            smallText(g, font, "planning...", px + 5, ry, DIM);
        } else {
            final int shown = Math.min(5, craftPlan.rows().size());
            for (int i = 0; i < shown; i++) {
                final var row = craftPlan.rows().get(i);
                DesktopItems.item(g, row.item(), px + 4, ry - 2);
                smallText(g, font, trimTo(font, row.item().getHoverName().getString(), scaledWidth(96)),
                        px + 22, ry, TEXT);
                final String counts = formatCount(row.have()) + " / " + formatCount(row.need());
                smallText(g, font, counts, px + CRAFT_W - 6 - scaledWidth(fontWidth(counts)), ry,
                        row.satisfied() ? 0xFF2E8B45 : 0xFFB23A3A);
                ry += 12;
            }
            if (craftPlan.rows().size() > shown) {
                smallText(g, font, "+" + (craftPlan.rows().size() - shown) + " more", px + 22, ry, DIM);
            }
            final String est = craftPlan.estimateTicks() > 0
                    ? "EST ~" + Math.max(1, craftPlan.estimateTicks() / 20) + "s" : "EST --";
            smallText(g, font, est, px + 5, py + CRAFT_H - 32, DIM);
            if (!craftPlan.feasible()) {
                smallText(g, font, "max " + formatCount(craftPlan.maxFeasible()), px + 70, py + CRAFT_H - 32,
                        0xFFB8860B);
            }
        }

        // Recipe toggle: only when the item can be made as a multi-stage pipeline (and thus also flat). It picks
        // which recipe the craft runs — the whole pipeline, or the flat patterns composed by the planner.
        if (craftPopup.multiStage()) {
            final int tx = px + 118;
            final int tw = CRAFT_W - 118 - 5;
            drawButton(g, font, tx, py + CRAFT_H - 33, tw, 12, craftMulti ? "Multi-stage" : "Flat",
                    hovered(mouseX, mouseY, tx, py + CRAFT_H - 33, tw, 12));
        }

        // Buttons: Craft (full) / Partial (up to feasible) / Close.
        final int by = py + CRAFT_H - 19;
        drawButton(g, font, px + 5, by, 58, 16, "Craft", hovered(mouseX, mouseY, px + 5, by, 58, 16));
        drawButton(g, font, px + 67, by, 66, 16, "Partial", hovered(mouseX, mouseY, px + 67, by, 66, 16));
        drawButton(g, font, px + 137, by, 54, 16, "Close", hovered(mouseX, mouseY, px + 137, by, 54, 16));
    }

    private void craftPopupClick(final double lx, final double ly) {
        final int px = (contentW - CRAFT_W) / 2;
        final int py = (contentH - CRAFT_H) / 2;
        if (lx < px || lx >= px + CRAFT_W || ly < py || ly >= py + CRAFT_H) {
            closeCraftPopup(); // click outside dismisses
            return;
        }
        for (int i = 0; i < CRAFT_STEPS.length; i++) {
            final int bx = px + 70 + i * 31;
            if (inRect(lx, ly, bx, py + 22, 29, 14)) {
                setCraftQty(craftQty + CRAFT_STEPS[i]);
                return;
            }
        }
        if (craftPopup.multiStage()) {
            final int tx = px + 118;
            final int tw = CRAFT_W - 118 - 5;
            if (inRect(lx, ly, tx, py + CRAFT_H - 33, tw, 12)) {
                craftMulti = !craftMulti;
                return;
            }
        }
        final int by = py + CRAFT_H - 19;
        if (inRect(lx, ly, px + 5, by, 58, 16)) {
            submitCraft(false);
        } else if (inRect(lx, ly, px + 67, by, 66, 16)) {
            submitCraft(true);
        } else if (inRect(lx, ly, px + 137, by, 54, 16)) {
            closeCraftPopup();
        }
    }

    /**
     * The item index under a content-local point inside the grid zone, accounting for the current item scroll,
     * or {@code -1} when the point is outside the grid. The cell math matches what the grid draws.
     */
    private int gridIndexAt(final double lx, final double ly) {
        // Delegate to the unit-tested pure hit-test so the hover/click always matches the rendered cells
        // (including rejecting the scrollbar strip) — render and hit-test share one source of truth.
        return NetworkInteractorLayout.gridIndexAt((int) lx, (int) ly, gridScroll, zones());
    }

    @Override
    public void renderTooltip(final GuiGraphics g, final Font font, final int x, final int y,
                              final int width, final int height, final int mouseX, final int mouseY) {
        final double lx = mouseX - x;
        final double ly = mouseY - y;

        // The inventory band is real container slots; the desktop screen renders their item tooltips, so the
        // app stays out of that area to avoid a double tooltip.
        if (inInventoryZone(lx, ly)) {
            return;
        }
        // Network/local grid cell: name + the true on-network total (which a count badge can't show fully).
        if (tab == TAB_NETWORK || tab == TAB_LOCAL) {
            final int idx = gridIndexAt(lx, ly);
            final List<NetworkItemEntry> items = filtered(tab == TAB_NETWORK ? networkItems : localItems);
            if (idx >= 0 && idx < items.size()) {
                final NetworkItemEntry e = items.get(idx);
                g.renderComponentTooltip(font, java.util.List.of(
                        e.name(),
                        net.minecraft.network.chat.Component.literal(amountLabel(e.key(), e.total()))
                                .withStyle(net.minecraft.ChatFormatting.GRAY)),
                        mouseX, mouseY);
            }
        } else if (tab == TAB_CRAFTING) {
            final int idx = gridIndexAt(lx, ly);
            if (idx >= 0 && idx < crafts.size()) {
                g.renderTooltip(font, crafts.get(idx).result(), mouseX, mouseY);
            }
        }
    }

    @Override
    public boolean mouseScrolled(final double delta) {
        // The Operations tab scrolls its own list; the grid tabs scroll their items; nothing on Status.
        if (tab == TAB_OPS) {
            final int rows = Math.max(1, zones().gridH() / 12);
            final int max = Math.max(0, allOps().size() - rows);
            opScroll = Math.max(0, Math.min(opScroll + (delta > 0 ? -1 : 1), max));
            return true;
        }
        final int max = maxGridScroll(zones());
        if (max <= 0) {
            return false;
        }
        gridScroll = Math.max(0, Math.min(gridScroll + (delta > 0 ? -1 : 1), max));
        return true;
    }

    @Override
    public boolean charTyped(final char c) {
        if (c < 32 || c == 127) {
            return false;
        }
        if (popupEntry != null) {
            if (c >= '0' && c <= '9') {
                final long v = popupQty * 10 + (c - '0');
                if (v <= 999_999_999L) {
                    popupQty = v;
                }
            }
            return true; // the dialog captures all typing while it is open
        }
        if (craftPopup != null) {
            if (c >= '0' && c <= '9') {
                setCraftQty(craftQty * 10 + (c - '0'));
            }
            return true;
        }
        if (searchFocus) {
            if (search.length() < 48) {
                search.append(c);
                gridScroll = 0;
            }
            return true;
        }
        if (input.length() < DesktopShellRunPayload.MAX_LEN - 1) {
            input.append(c);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(final int key, final int scanCode, final int modifiers) {
        if (popupEntry != null) {
            switch (key) {
                case 259 -> popupQty /= 10; // Backspace
                case 257, 335 -> popupAction(popupStorage // Enter = primary action
                        ? NiGridClickPayload.MODE_LOCAL_TO_INV : NiGridClickPayload.MODE_NET_TO_LOCAL);
                case 256 -> closePopup(); // Escape
                default -> { }
            }
            return true;
        }
        if (craftPopup != null) {
            switch (key) {
                case 259 -> setCraftQty(craftQty / 10);   // Backspace
                case 257, 335 -> submitCraft(false);      // Enter = Craft
                case 256 -> closeCraftPopup();            // Escape
                default -> { }
            }
            return true;
        }
        if (searchFocus) {
            switch (key) {
                case 259 -> {
                    if (search.length() > 0) {
                        search.deleteCharAt(search.length() - 1);
                    }
                }
                case 257, 335, 256 -> searchFocus = false;
                default -> {
                    return false;
                }
            }
            return true;
        }
        switch (key) {
            case 257, 335 -> submit();
            case 259 -> {
                if (input.length() > 0) {
                    input.deleteCharAt(input.length() - 1);
                }
            }
            case 265 -> recall(-1);
            case 264 -> recall(1);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void submit() {
        final String line = input.toString().trim();
        input.setLength(0);
        historyIndex = -1;
        if (line.isEmpty()) {
            return;
        }
        pushConsole("> " + line, 0xFF40C060);
        if (history.isEmpty() || !history.get(history.size() - 1).equals(line)) {
            history.add(line);
        }
        PacketDistributor.sendToServer(new DesktopShellRunPayload(host, line));
    }

    private void recall(final int direction) {
        if (history.isEmpty()) {
            return;
        }
        if (historyIndex == -1) {
            historyIndex = history.size();
        }
        historyIndex = Math.max(0, Math.min(history.size(), historyIndex + direction));
        input.setLength(0);
        if (historyIndex >= history.size()) {
            historyIndex = -1;
        } else {
            input.append(history.get(historyIndex));
        }
    }

    private static String formatCount(final long n) {
        if (n < 1000) {
            return Long.toString(n);
        }
        if (n < 1_000_000) {
            return String.format(Locale.ROOT, "%.1fk", n / 1000.0);
        }
        return String.format(Locale.ROOT, "%.1fM", n / 1_000_000.0);
    }

    /** One item-equivalent is 4 MB of data; show the network usage in those data units. */
    /** A short amount with its unit where the unit is not obvious: items by the count, data by the millibucket. */
    private static String amount(final dev.jsc.jscomputronics.module.computing.storage.StorageKey key, final long n) {
        return key.isItem() ? formatCount(n) : formatCount(n) + " mB";
    }

    /** The exact amount with its unit, for a tooltip. */
    private static String amountLabel(final dev.jsc.jscomputronics.module.computing.storage.StorageKey key, final long n) {
        return key.isItem()
                ? String.format(Locale.ROOT, "%,d item%s", n, n == 1L ? "" : "s")
                : String.format(Locale.ROOT, "%,d mB", n);
    }

    /** The registry id of the data behind a key: the item's, the fluid's, or the chemical's. */
    private static net.minecraft.resources.ResourceLocation dataId(
            final dev.jsc.jscomputronics.module.computing.storage.StorageKey key) {
        if (key.isFluid()) {
            return net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(key.fluidPrototype().getFluid());
        }
        if (key.isChemical()) {
            return java.util.Objects.requireNonNull(key.chemicalId());
        }
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(key.item());
    }

    /** The data a weight amounts to, at 4 MB the item (1 000 mB-eq): a bucket of fluid weighs as much as an item. */
    private static String dataLabel(final long weight) {
        final long mb = weight * 4L / dev.jsc.jscomputronics.module.computing.storage.StorageKey.MB_EQ_PER_ITEM;
        if (mb < 1024L) {
            return mb + " MB";
        }
        if (mb < 1024L * 1024L) {
            return String.format(Locale.ROOT, "%.1f GB", mb / 1024.0);
        }
        return String.format(Locale.ROOT, "%.1f TB", mb / (1024.0 * 1024.0));
    }

    private static String trimTo(final Font font, final String s, final int maxWidth) {
        String out = s;
        while (out.length() > 1 && font.width(out) > maxWidth) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    /**
     * Draws {@code text} word-wrapped to {@code maxWidth}, one line per row, so a hint never truncates in the
     * middle of a word. A single word longer than the line is left as-is on its own row.
     */
    private static void drawWrapped(final GuiGraphics g, final Font font, final String text,
                                    final int x, final int y, final int maxWidth, final int color) {
        int ry = y;
        final StringBuilder line = new StringBuilder();
        for (final String word : text.split(" ")) {
            final String candidate = line.length() == 0 ? word : line + " " + word;
            if (font.width(candidate) > maxWidth && line.length() > 0) {
                g.drawString(font, line.toString(), x, ry, color, false);
                ry += 11;
                line.setLength(0);
                line.append(word);
            } else {
                line.setLength(0);
                line.append(candidate);
            }
        }
        if (line.length() > 0) {
            g.drawString(font, line.toString(), x, ry, color, false);
        }
    }

    private static int fontWidth(final String s) {
        return Minecraft.getInstance().font.width(s);
    }

    // --- inspection (client tests drive the app through the same hit areas the player clicks; all points are
    // content-local, i.e. relative to the window's content origin) ---

    public int activeTab() {
        return tab;
    }

    public boolean isCraftPopupOpen() {
        return craftPopup != null;
    }

    public long craftQuantity() {
        return craftQty;
    }

    /** The Crafting tab's entries as listed (after the search filter), by display name. */
    public java.util.List<String> craftableNames() {
        final java.util.List<String> out = new ArrayList<>();
        for (final CraftCatalogPayload.Entry e : filteredCrafts()) {
            out.add(e.result().getHoverName().getString());
        }
        return out;
    }

    public int[] craftingTabCenter() {
        int tabX = 0;
        for (int i = 0; i < TAB_CRAFTING; i++) {
            tabX += fontWidth(TABS[i]) + 12;
        }
        return new int[]{tabX + (fontWidth(TABS[TAB_CRAFTING]) + 12) / 2, TAB_H / 2};
    }

    /** The centre of the grid cell showing craftable {@code index} (must be scrolled into view). */
    public int[] craftableCellCenter(final int index) {
        final NetworkInteractorLayout.Zones z = zones();
        final int col = index % z.gridCols();
        final int row = index / z.gridCols() - gridScroll;
        return new int[]{z.gridX() + col * CELL + CELL / 2, z.gridY() + row * CELL + CELL / 2};
    }

    /** The centre of quantity stepper {@code index} of the craft popup (0: -64, 1: -1, 2: +1, 3: +64). */
    public int[] craftPopupStepCenter(final int index) {
        final int px = (contentW - CRAFT_W) / 2;
        final int py = (contentH - CRAFT_H) / 2;
        return new int[]{px + 70 + index * 31 + 14, py + 22 + 7};
    }

    /** Content-local centre of inventory band slot {@code index} (rows 0-2 main inventory, row 3 hotbar). */
    public int[] inventoryBandSlotCenter(final int index) {
        final NetworkInteractorLayout.Zones z = zones();
        final int col = index % NetworkInteractorLayout.INV_COLS;
        final int row = index / NetworkInteractorLayout.INV_COLS;
        return new int[]{z.invX() + col * CELL + CELL / 2,
                z.invY() + NetworkInteractorLayout.rowYOffset(row) + CELL / 2};
    }

    /** Content-local centre of the first grid cell (where a carried stack is deposited on a grid tab). */
    public int[] gridFirstCellCenter() {
        final NetworkInteractorLayout.Zones z = zones();
        return new int[]{z.gridX() + CELL / 2, z.gridY() + CELL / 2};
    }

    /** The centre of the craft popup's full-request button. */
    public int[] craftPopupSubmitCenter() {
        final int px = (contentW - CRAFT_W) / 2;
        final int py = (contentH - CRAFT_H) / 2;
        return new int[]{px + 5 + 29, py + CRAFT_H - 19 + 8};
    }

    private void outline(final GuiGraphics g, final int x, final int y, final int w, final int h) {
        g.fill(x, y, x + w, y + 1, EDGE);
        g.fill(x, y + h - 1, x + w, y + h, EDGE);
        g.fill(x, y, x + 1, y + h, EDGE);
        g.fill(x + w - 1, y, x + w, y + h, EDGE);
    }

    private static int colorOf(final int ordinal) {
        final CliStyle[] values = CliStyle.values();
        final CliStyle style = ordinal >= 0 && ordinal < values.length ? values[ordinal] : CliStyle.PLAIN;
        return switch (style) {
            case ACCENT, HEADER -> 0xFF39D6C4;
            case OK -> 0xFF5FE07A;
            case ERROR -> 0xFFEF6A5A;
            case WARN -> 0xFFF0B23A;
            case INFO -> 0xFF2AA7E0;
            case DIM -> 0xFF7D8A9C;
            // The extended palette: brand-tinted terminal colors (screenfetch logos and the like).
            case ORANGE -> 0xFFE95420;
            case MAGENTA -> 0xFFE0447C;
            case BLUE -> 0xFF5A8FD6;
            case CYAN -> 0xFF2FA6E8;
            case PURPLE -> 0xFF9E8FD6;
            default -> 0xFFCDD6E2;
        };
    }
}
